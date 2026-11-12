<template>
  <div class="cost-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item label="维度">
          <el-select v-model="searchForm.dimension" style="width: 160px">
            <el-option label="按试剂" value="reagent" />
            <el-option label="按仪器" value="instrument" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="fetchSummary">查询</el-button>
          <el-button type="success" plain @click="fetchDetail">查看明细</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 成本汇总 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>成本汇总</span>
          <span class="total-tip">合计金额: <b>¥{{ totalCost.toFixed(2) }}</b></span>
        </div>
      </template>
      <el-table v-loading="summaryLoading" :data="summaryData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column v-if="searchForm.dimension === 'reagent'" label="试剂编码" prop="reagentCode" width="140" />
        <el-table-column v-if="searchForm.dimension === 'reagent'" label="试剂名称" prop="reagentName" min-width="180" />
        <el-table-column v-else label="仪器名称" min-width="160">
          <template #default="{ row }">{{ row.instrumentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="消耗数量" prop="totalQty" width="130" align="right">
          <template #default="{ row }">{{ row.totalQty ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="消耗成本(元)" prop="totalCost" width="160" align="right">
          <template #default="{ row }">¥{{ Number(row.totalCost || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="占比" width="200">
          <template #default="{ row }">
            <el-progress :percentage="getPercent(row.totalCost)" :stroke-width="12" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 成本明细抽屉 -->
    <el-drawer v-model="detailVisible" title="成本明细" size="70%">
      <el-table v-loading="detailLoading" :data="detailData" border stripe size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="consumptionNo" label="消耗单号" width="160" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="140" />
        <el-table-column prop="lotNo" label="批号" width="130" />
        <el-table-column label="数量" width="100" align="right">
          <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="消耗类型" width="100" align="center">
          <template #default="{ row }">{{ consumeTypeText(row.consumeType) }}</template>
        </el-table-column>
        <el-table-column prop="relatedNo" label="关联单号" width="130" />
        <el-table-column prop="operatorName" label="操作人" width="100" align="center" />
        <el-table-column label="成本(元)" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.cost || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import {
  getCostSummary,
  getCostDetail,
  type ReagentCostSummary,
  type ReagentCostDetail
} from '@/api/reagent'

const searchForm = reactive({ dimension: 'reagent' })
const dateRange = ref<[string, string] | []>([])

function getRange() {
  if (dateRange.value && dateRange.value.length === 2) {
    return { start: dateRange.value[0], end: dateRange.value[1] }
  }
  return {}
}

const summaryLoading = ref(false)
const summaryData = ref<ReagentCostSummary[]>([])

const totalCost = computed(() => {
  return summaryData.value.reduce((sum, it) => sum + Number(it.totalCost || 0), 0)
})

function getPercent(cost?: number): number {
  const total = totalCost.value
  if (!total || !cost) return 0
  return Number(((Number(cost) / total) * 100).toFixed(1))
}

async function fetchSummary() {
  summaryLoading.value = true
  try {
    summaryData.value = await getCostSummary({ ...getRange(), dimension: searchForm.dimension })
  } catch (e) {
    summaryData.value = []
  } finally {
    summaryLoading.value = false
  }
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<ReagentCostDetail[]>([])

function consumeTypeText(type?: number): string {
  const map: Record<number, string> = { 1: '手工', 2: '自动', 3: '质控', 4: '校准', 5: '维护' }
  return type ? (map[type] || '-') : '-'
}

function formatTime(time?: string): string {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function fetchDetail() {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await getCostDetail(getRange())
  } catch (e) {
    detailData.value = []
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  // 默认查询最近一个月
  const end = dayjs().format('YYYY-MM-DD')
  const start = dayjs().subtract(1, 'month').format('YYYY-MM-DD')
  dateRange.value = [start, end]
  fetchSummary()
})
</script>

<style scoped lang="scss">
.cost-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .total-tip {
        font-size: 14px;
        color: #606266;

        b {
          color: #f56c6c;
          font-size: 16px;
        }
      }
    }
  }
}
</style>
