<template>
  <div class="supplier-eval-page">
    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-checkbox v-model="includeManual" @change="fetchData">计入人工评分</el-checkbox>
          <el-tag type="info" size="small" effect="plain">
            综合分 = 准时率×40% + 拒收率折算×40% + 人工分×20%
          </el-tag>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" plain @click="fetchData">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 评分排行 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="排名" width="70" align="center">
          <template #default="{ $index }">
            <span class="rank-badge" :class="rankClass($index)">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
        <el-table-column label="准时率" width="110" align="center">
          <template #default="{ row }">
            <span>{{ formatRate(row.onTimeRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="拒收率" width="110" align="center">
          <template #default="{ row }">
            <span :class="rejectClass(row.rejectRate)">{{ formatRate(row.rejectRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="自动分" width="100" align="center">
          <template #default="{ row }">
            <span>{{ formatScore(row.autoScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="人工分" width="140" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.manualScore"
              :min="0"
              :max="100"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 110px"
            />
          </template>
        </el-table-column>
        <el-table-column label="综合分" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="scoreType(row.compositeScore)" size="small">{{ formatScore(row.compositeScore) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastOrderDate" label="最近下单" width="120" align="center" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :disabled="!row.manualScore" @click="handleSaveScore(row)">保存评分</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSupplierEvaluateRank, saveSupplierManualScore, type SupplierScoreVO } from '@/api/reagent'

const loading = ref(false)
const includeManual = ref(true)
const tableData = ref<SupplierScoreVO[]>([])

async function fetchData() {
  loading.value = true
  try {
    tableData.value = await getSupplierEvaluateRank(includeManual.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载供应商评分失败')
  } finally {
    loading.value = false
  }
}

function formatRate(v?: number): string {
  if (v === undefined || v === null) return '-'
  // 后端返回的准时率/拒收率已是 0-100 的百分比数值，勿再乘以 100，否则会显示 10000%
  return Number(v).toFixed(1) + '%'
}

function formatScore(v?: number): string {
  if (v === undefined || v === null) return '-'
  return Number(v).toFixed(1)
}

function scoreType(v?: number): 'success' | 'warning' | 'danger' | 'info' {
  if (v === undefined || v === null) return 'info'
  if (v >= 80) return 'success'
  if (v >= 60) return 'warning'
  return 'danger'
}

function rejectClass(v?: number): string {
  if (v === undefined || v === null) return ''
  // 阈值按百分比(0-100)比较：拒收率 > 20% 视为偏高
  return v > 20 ? 'reject-high' : ''
}

function rankClass(index: number): string {
  if (index === 0) return 'rank-top'
  if (index === 1) return 'rank-second'
  if (index === 2) return 'rank-third'
  return ''
}

async function handleSaveScore(row: SupplierScoreVO) {
  if (row.supplierId === undefined) return
  try {
    await saveSupplierManualScore(row.supplierId, Number(row.manualScore || 0))
    ElMessage.success('人工评分已保存')
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.toolbar-card {
  margin-bottom: 16px;

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.table-card {
  .rank-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: #f0f2f5;
    font-weight: 600;
    font-size: 13px;
  }

  .rank-top {
    background: #f56c6c;
    color: #fff;
  }

  .rank-second {
    background: #e6a23c;
    color: #fff;
  }

  .rank-third {
    background: #909399;
    color: #fff;
  }

  .reject-high {
    color: #f56c6c;
    font-weight: 600;
  }
}
</style>