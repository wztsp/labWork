<template>
  <div class="resistance-page">
    <!-- 说明(合并原专家规则解释) -->
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
        系统内置的 MRSA/CRE/VRE/CRAB/最后防线耐药等规则由种子脚本 08-expert-rules.sql 提供。
      </el-alert>
    </el-card>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="规则名/细菌/抗生素"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="searchForm.ruleType" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="t in ruleTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增规则</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="ruleName" label="规则名称" min-width="170">
          <template #default="{ row }">
            <span>{{ row.ruleName }}</span>
            <el-tag v-if="row.isBuiltin === 1" size="small" type="info" style="margin-left: 6px">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.ruleType" size="small" effect="plain">{{ row.ruleType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="bacteriaNames" label="适用细菌" min-width="160" show-overflow-tooltip />
        <el-table-column prop="antibioticNames" label="关联抗生素" min-width="200" show-overflow-tooltip />
        <el-table-column label="匹配模式" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.matchMode === 'EXACT' ? 'warning' : 'primary'" size="small" effect="plain">
              {{ row.matchMode === 'EXACT' ? '精确匹配' : '任一耐药' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="期望结果" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.matchMode === 'EXACT'">{{ resultText(row.result) }}</span>
            <span v-else>-</span>
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
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑规则' : '新增规则'" width="720px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="editForm.ruleName" placeholder="如 MRSA / 自定义规则" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则类型">
              <el-select v-model="editForm.ruleType" placeholder="请选择" filterable allow-create style="width: 100%">
                <el-option v-for="t in ruleTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="适用细菌">
              <el-select
                v-model="editForm.bacteriaCodesArr"
                multiple
                filterable
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="可选择多个细菌，留空=适用于所有细菌"
                style="width: 100%"
              >
                <el-option v-for="b in allBacteria" :key="b.bacteriaCode" :label="b.bacteriaName" :value="b.bacteriaCode" />
              </el-select>
              <div class="field-tip">留空表示该规则适用于所有细菌</div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="关联抗生素">
              <el-select
                v-model="editForm.antibioticCodesArr"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择抗生素(可多个)"
                style="width: 100%"
              >
                <el-option v-for="a in allAntibiotics" :key="a.antibioticCode" :label="`${a.antibioticName} (${a.antibioticCode})`" :value="a.antibioticCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="匹配模式">
              <el-select v-model="editForm.matchMode" placeholder="选择模式" style="width: 100%">
                <el-option value="ANY_RESISTANT" label="任一耐药(对所选抗生素任一耐药即命中)" />
                <el-option value="EXACT" label="精确匹配(所选抗生素结果=期望结果)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="editForm.matchMode === 'EXACT'">
            <el-form-item label="期望结果">
              <el-select v-model="editForm.result" placeholder="选择" style="width: 100%">
                <el-option value="R" label="耐药 (R)" />
                <el-option value="I" label="中介 (I)" />
                <el-option value="S" label="敏感 (S)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否危急值">
              <el-switch v-model="editForm.isCritical" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="预警文案">
              <el-input v-model="editForm.alertMessage" placeholder="命中该规则时展示的预警文案" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="规则说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 手动触发评估 / 报告命中标注 (合并原专家规则页面功能) -->
    <el-card shadow="never" class="eval-card">
      <template #header>
        <span>手动触发耐药规则评估 / 报告命中标注</span>
      </template>
      <el-form :inline="true" :model="evalForm" @submit.prevent>
        <el-form-item label="微生物报告ID">
          <el-input-number v-model="evalForm.reportId" :min="1" controls-position="right" placeholder="请输入报告ID" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="evalLoading" @click="handleEvaluate">触发评估</el-button>
          <el-button @click="refreshList">刷新规则列表</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="evalResultMessage" type="success" :closable="false" show-icon style="margin-top:12px">
        <template #title>评估结果</template>
        {{ evalResultMessage }}
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getResistanceRulePage,
  saveResistanceRule,
  deleteResistanceRule,
  getAllBacteria,
  getAllAntibiotics,
  type ResistanceRule,
  type ResistanceRuleQuery,
  type Bacteria,
  type Antibiotic
} from '@/api/micro'
import { evaluateExpertRules as evaluateResistanceRules, type MicroAlert } from '@/api/report/micro'

// 合并原「专家规则」类型到可配置规则，统一为耐药规则分类
const ruleTypeOptions = ['特殊耐药', '表型推断', '天然耐药', '获得性耐药', '危急值', '可配置规则']

// 字典选项
const allBacteria = ref<Bacteria[]>([])
const allAntibiotics = ref<Antibiotic[]>([])

function resultText(result?: string): string {
  if (result === 'S') return '敏感(S)'
  if (result === 'I') return '中介(I)'
  if (result === 'R') return '耐药(R)'
  return '-'
}

const loading = ref(false)
const tableData = ref<ResistanceRule[]>([])
const searchForm = reactive({ keyword: '', ruleType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = () => ({
  id: 0,
  ruleName: '',
  ruleType: '',
  bacteriaCodesArr: [] as string[],
  antibioticCodesArr: [] as string[],
  matchMode: 'ANY_RESISTANT',
  result: 'R',
  isCritical: 0,
  alertMessage: '',
  description: '',
  status: 1
})
const editForm = reactive<any>(emptyForm())
const formRules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }]
}

/** 把后端存储的 (JSON 数组/逗号分隔) 解析成数组用于多选 */
function parseArr(s?: string): string[] {
  if (!s) return []
  return s.replace(/[[\]"{}]/g, '').split(',').map(x => x.trim()).filter(Boolean)
}

async function fetchList() {
  loading.value = true
  try {
    const params: ResistanceRuleQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.ruleType) params.ruleType = searchForm.ruleType
    const res = await getResistanceRulePage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { searchForm.keyword = ''; searchForm.ruleType = ''; handleSearch() }
function handleSizeChange(size: number) { pagination.pageSize = size; pagination.pageNum = 1; fetchList() }
function handlePageChange(page: number) { pagination.pageNum = page; fetchList() }

function handleAdd() {
  Object.assign(editForm, emptyForm())
  formDialogVisible.value = true
}

function handleEdit(row: ResistanceRule) {
  Object.assign(editForm, {
    id: row.id,
    ruleName: row.ruleName || '',
    ruleType: row.ruleType ?? '',
    bacteriaCodesArr: parseArr(row.bacteriaCodes).concat(row.organismCode ? [row.organismCode] : []),
    antibioticCodesArr: parseArr(row.antibioticCodes),
    matchMode: row.matchMode || 'ANY_RESISTANT',
    result: row.result || 'R',
    isCritical: row.isCritical ?? 0,
    alertMessage: row.alertMessage || '',
    description: row.description || '',
    status: row.status ?? 1
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (editForm.antibioticCodesArr.length === 0) {
      ElMessage.warning('请至少选择一个抗生素')
      return
    }
    formLoading.value = true
    try {
      await saveResistanceRule({
        id: editForm.id,
        ruleName: editForm.ruleName,
        ruleType: editForm.ruleType || undefined,
        bacteriaCodes: JSON.stringify(editForm.bacteriaCodesArr),
        antibioticCodes: JSON.stringify(editForm.antibioticCodesArr),
        matchMode: editForm.matchMode,
        result: editForm.matchMode === 'EXACT' ? editForm.result : undefined,
        isCritical: editForm.isCritical,
        alertMessage: editForm.alertMessage || undefined,
        description: editForm.description || undefined,
        status: editForm.status,
        isBuiltin: 0
      })
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: ResistanceRule) {
  try {
    await ElMessageBox.confirm(`确认删除规则 "${row.ruleName}" 吗?`, '提示', { type: 'warning' })
    await deleteResistanceRule(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

// ========= 手动触发耐药规则评估 (原专家规则页功能) =========
const evalForm = reactive<{ reportId: number | undefined }>({ reportId: undefined })
const evalLoading = ref(false)
const evalResultMessage = ref('')
async function handleEvaluate() {
  if (!evalForm.reportId) { ElMessage.warning('请输入微生物报告ID'); return }
  evalLoading.value = true
  evalResultMessage.value = ''
  try {
    const hits = (await evaluateResistanceRules(evalForm.reportId)) as MicroAlert[] | null
    const n = Array.isArray(hits) ? hits.length : 0
    if (n > 0) {
      const summary = hits.map(h => `· [${h.alertLevel || 'WARN'}] ${h.title || h.ruleName || h.message}`).slice(0, 5).join('\n')
      evalResultMessage.value = `本次共命中 ${n} 条耐药预警：\n${summary}${n > 5 ? `\n... (另有 ${n - 5} 条略)。请前往「微生物报告」详情或「耐药预警」页面查看完整结果。` : ''}`
    } else {
      evalResultMessage.value = '本次评估未命中任何耐药预警。'
    }
    ElMessage.success('评估完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '评估失败')
  } finally {
    evalLoading.value = false
  }
}
function refreshList() { fetchList() }

onMounted(async () => {
  fetchList()
  try {
    allBacteria.value = (await getAllBacteria()) || []
  } catch (e) { /* ignore */ }
  try {
    allAntibiotics.value = (await getAllAntibiotics()) || []
  } catch (e) { /* ignore */ }
})
</script>

<style scoped lang="scss">
.resistance-page {
  padding: 16px;

  .rule-card {
    margin-bottom: 12px;
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }

  .search-card {
    margin-bottom: 12px;
    :deep(.el-card__body) { padding: 16px 16px 0 16px; }
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .eval-card {
    margin-top: 12px;
    white-space: pre-wrap;
  }

  .field-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
  }
}
</style>