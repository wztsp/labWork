<template>
  <div class="rule-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <span>Westgard 多规则配置</span>
          <el-button type="primary" link size="small" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="ruleCode" label="规则" width="120" align="center">
          <template #default="{ row }">
            <span class="rule-code">{{ row.ruleCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="QC_RULE_TYPE_MAP[row.ruleType]?.type">
              {{ QC_RULE_TYPE_MAP[row.ruleType]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="280">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="启用" width="120" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              :loading="row._loading"
              @change="handleToggleEnabled(row)"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getQcRuleList,
  updateQcRule,
  QC_RULE_TYPE_MAP,
  type QcRule
} from '@/api/qc'

type QcRuleWithLoading = QcRule & { _loading?: boolean }

const loading = ref(false)
const tableData = ref<QcRuleWithLoading[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res = await getQcRuleList()
    tableData.value = (res || []).map(r => ({ ...r, _loading: false }))
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleToggleEnabled(row: QcRuleWithLoading) {
  row._loading = true
  const prevValue = row.enabled === 1 ? 0 : 1
  try {
    await updateQcRule({ ...row, _loading: undefined })
    ElMessage.success(`${row.enabled === 1 ? '启用' : '禁用'}规则 ${row.ruleCode} 成功`)
  } catch (e) {
    // 失败时回滚
    row.enabled = prevValue
  } finally {
    row._loading = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.rule-config-page {
  padding: 16px;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .rule-code {
    font-weight: bold;
    font-family: 'Consolas', 'Monaco', monospace;
    color: var(--el-color-primary);
  }
}
</style>
