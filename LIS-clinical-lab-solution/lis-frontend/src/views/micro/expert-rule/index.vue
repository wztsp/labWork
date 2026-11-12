<template>
  <div class="expert-rule-page">
    <!-- 说明 -->
    <el-card shadow="never" class="rule-card">
      <template #header>
        <div class="card-header">
          <span>耐药规则引擎 (统一可配置)</span>
          <el-tag type="success" size="small">数据驱动</el-tag>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>规则说明（合并原专家规则解释）</template>
        耐药规则统一为可配置规则引擎（含原「专家规则」定义）：新增时选择<b>细菌</b>和<b>抗生素</b>，可选「任一耐药」或「精确匹配结果」判定。
        规则在以下场景自动对业务数据判定：① 仪器导入报告时；② 新增/修改/删除药敏或细菌时；③ 手动触发评估时。
        命中项会写入「耐药预警」页面，并在微生物<b>鉴定报告</b>详情顶部命中面板标注。
        规则的增删改请到「耐药规则」页面进行；系统内置的 MRSA/CRE/VRE/CRAB/最后防线耐药等规则由种子脚本 08-expert-rules.sql 提供。
      </el-alert>

      <el-table :data="tableData" border stripe style="width: 100%" v-loading="loading" class="mt12">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="ruleName" label="规则名称" min-width="180">
          <template #default="{ row }">
            <span>{{ row.ruleName }}</span>
            <el-tag v-if="row.isBuiltin === 1" size="small" type="info" style="margin-left: 6px">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="适用细菌" min-width="160" prop="bacteriaNames" show-overflow-tooltip />
        <el-table-column label="关联抗生素" min-width="200" prop="antibioticNames" show-overflow-tooltip />
        <el-table-column label="匹配模式" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.matchMode === 'EXACT' ? 'warning' : 'primary'" size="small" effect="plain">
              {{ row.matchMode === 'EXACT' ? '精确匹配' : '任一耐药' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="危急值" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isCritical === 1" type="danger" size="small" effect="dark">危急</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 手动触发评估 -->
    <el-card shadow="never" class="eval-card">
      <template #header>
        <span>手动触发规则评估 / 报告命中标注</span>
      </template>
      <el-form :inline="true" :model="evalForm" @submit.prevent>
        <el-form-item label="微生物报告ID">
          <el-input-number v-model="evalForm.reportId" :min="1" controls-position="right" placeholder="请输入报告ID" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="evalLoading" @click="handleEvaluate">触发评估</el-button>
          <el-button plain @click="evalForm.reportId = undefined">清空</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="evalResult.length > 0 || (hasEvaluated && !evalLoading)"
        :type="evalResult.length > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        class="mb12"
      >
        <template #title>评估结果</template>
        <span v-if="evalResult.length > 0">本次评估命中 {{ evalResult.length }} 条规则，已写入预警列表。</span>
        <span v-else-if="!evalLoading">本次评估未触发任何规则。</span>
      </el-alert>

      <el-table v-if="evalResult.length > 0" :data="evalResult" border size="small" class="result-table">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="规则/预警类型" width="180" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertTypeTagType(row.alertType)" size="small" effect="dark">{{ row.alertType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'" size="small">
              {{ row.alertLevel === 'CRITICAL' ? '危急值' : '警告' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="organismName" label="细菌名称" width="180" />
        <el-table-column prop="message" label="预警描述" min-width="300" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getResistanceRulePage, type ResistanceRule } from '@/api/micro'
import { evaluateExpertRules, type MicroAlert } from '@/api/report/micro'

const loading = ref(false)
const tableData = ref<ResistanceRule[]>([])

// 触发评估
const evalForm = reactive<{ reportId: number | undefined }>({ reportId: undefined })
const evalLoading = ref(false)
const evalResult = ref<MicroAlert[]>([])
const hasEvaluated = ref(false)

const alertTypeMap: Record<string, { text: string; tag: string }> = {
  MRSA: { text: 'MRSA', tag: 'danger' },
  VRE: { text: 'VRE', tag: 'danger' },
  CRE: { text: 'CRE', tag: 'danger' },
  ESBL: { text: 'ESBL', tag: 'warning' },
  CRAB: { text: 'CRAB', tag: 'danger' },
  MDR: { text: 'MDR', tag: 'warning' }
}

function getAlertTypeText(type?: string): string {
  return alertTypeMap[type || '']?.text || type || '-'
}

function getAlertTypeTagType(type?: string): string {
  return alertTypeMap[type || '']?.tag || 'info'
}

async function fetchRules() {
  loading.value = true
  try {
    const res = await getResistanceRulePage({ pageNum: 1, pageSize: 200 })
    tableData.value = res.records || []
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleEvaluate() {
  if (!evalForm.reportId) {
    ElMessage.warning('请输入微生物报告ID')
    return
  }
  evalLoading.value = true
  try {
    evalResult.value = (await evaluateExpertRules(evalForm.reportId)) || []
    hasEvaluated.value = true
    ElMessage.success(evalResult.value.length > 0
      ? `评估完成，命中 ${evalResult.value.length} 条规则`
      : '评估完成，未触发规则')
  } catch (e) {
    // ignore
  } finally {
    evalLoading.value = false
  }
}

onMounted(fetchRules)
</script>

<style scoped lang="scss">
.expert-rule-page {
  padding: 16px;

  .rule-card {
    margin-bottom: 12px;
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .eval-card {
    .result-table { margin-top: 12px; }
  }

  .mb12 { margin-bottom: 12px; }
  .mt12 { margin-top: 12px; }
}
</style>