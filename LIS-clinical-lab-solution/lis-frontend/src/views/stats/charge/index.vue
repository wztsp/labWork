<template>
  <div class="charge-stats-page">
    <!-- 顶部: 日期范围 + 科室选择 + 查询 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item label="科室">
          <el-select
            v-model="searchForm.department"
            placeholder="全部科室"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="12">
          <el-card shadow="hover" class="stat-card stat-total">
            <div class="stat-icon">
              <el-icon><Wallet /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">总费用</div>
              <div class="stat-value">¥{{ formatMoney(summary.totalAmount) }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" class="stat-card stat-today">
            <div class="stat-icon">
              <el-icon><Calendar /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">今日费用</div>
              <div class="stat-value">¥{{ formatMoney(summary.todayAmount) }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <el-row :gutter="20" class="chart-row">
        <el-col :span="16">
          <el-card shadow="never" class="chart-card">
            <template #header>按日费用趋势</template>
            <v-chart class="chart" :option="dailyChartOption" autoresize />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="chart-card">
            <template #header>按项目费用分布</template>
            <v-chart class="chart" :option="itemChartOption" autoresize />
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { Wallet, Calendar } from '@element-plus/icons-vue'
import { getChargeStats, getChargeByDay, getChargeByItem, getChargeByDept } from '@/api/stats'

use([CanvasRenderer, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

interface DailyCharge {
  date: string
  amount: number
}

interface ItemCharge {
  itemName: string
  amount: number
}

const deptOptions = ref<string[]>([])

const loading = ref(false)
const dateRange = ref<[string, string] | null>([null, null])

const searchForm = reactive({
  department: ''
})

const summary = reactive({
  totalAmount: 0,
  todayAmount: 0
})

const dailyList = ref<DailyCharge[]>([])
const itemList = ref<ItemCharge[]>([])

function formatMoney(val: number): string {
  return (val || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchData() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择日期范围')
    return
  }
  loading.value = true
  const params: any = {
    startDate: dateRange.value[0],
    endDate: dateRange.value[1]
  }
  if (searchForm.department) params.department = searchForm.department
  try {
    const [summaryRes, dayRes, itemRes, deptRes] = await Promise.all([
      getChargeStats(params),
      getChargeByDay(params),
      getChargeByItem(params),
      getChargeByDept({ startDate: dateRange.value[0], endDate: dateRange.value[1] })
    ])
    summary.totalAmount = Number((summaryRes as any)?.total_fee ?? 0)
    summary.todayAmount = Number((summaryRes as any)?.today_fee ?? 0)
    dailyList.value = ((dayRes as any) || []).map((r: any) => ({
      date: String(r.day ?? ''),
      amount: Number(r.total_fee ?? 0)
    }))
    itemList.value = ((itemRes as any) || []).map((r: any) => ({
      itemName: String(r.item_name ?? '未知'),
      amount: Number(r.total_fee ?? 0)
    }))
    const depts = ((deptRes as any) || [])
      .map((d: any) => String(d.department ?? ''))
      .filter((d: string) => d)
    deptOptions.value = depts.length ? depts : deptOptions.value
  } catch (e) {
    dailyList.value = []
    itemList.value = []
  } finally {
    loading.value = false
  }
}

const dailyChartOption = computed(() => {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex ?? 0
        const row = dailyList.value[idx]
        if (!row) return ''
        return `${row.date}<br/>费用: ¥${formatMoney(row.amount)}`
      }
    },
    grid: { left: 70, right: 30, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: dailyList.value.map(d => d.date),
      axisLabel: { rotate: 30 }
    },
    yAxis: {
      type: 'value',
      name: '费用(元)',
      axisLabel: { formatter: (val: number) => (val >= 10000 ? `${(val / 10000).toFixed(1)}万` : `${val}`) }
    },
    series: [
      {
        name: '费用',
        type: 'bar',
        data: dailyList.value.map(d => d.amount),
        itemStyle: { color: '#1A73E8' },
        barMaxWidth: 32
      }
    ]
  }
})

const itemChartOption = computed(() => {
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: ¥{c} ({d}%)'
    },
    legend: { orient: 'vertical', left: 'left', type: 'scroll' },
    series: [
      {
        name: '项目费用分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['55%', '50%'],
        avoidLabelOverlap: true,
        data: itemList.value.map(d => ({ name: d.itemName, value: d.amount })),
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0,0,0,0.5)'
          }
        }
      }
    ]
  }
})

function handleQuery() {
  fetchData()
}

function initDefaultRange() {
  const end = dayjs()
  const start = end.subtract(6, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

onMounted(() => {
  initDefaultRange()
  fetchData()
})
</script>

<style scoped lang="scss">
.charge-stats-page {
  padding: 16px;

  .search-card {
    margin-bottom: 16px;
  }

  .stats-row {
    margin-bottom: 16px;
  }

  .stat-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
    }

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      color: #fff;
      flex-shrink: 0;
    }

    .stat-info {
      flex: 1;
      min-width: 0;

      .stat-label {
        font-size: 14px;
        color: var(--el-text-color-secondary);
        margin-bottom: 6px;
      }

      .stat-value {
        font-size: 26px;
        font-weight: bold;
        color: var(--el-text-color-primary);
        line-height: 1.2;
      }
    }

    &.stat-total {
      .stat-icon { background: var(--el-color-primary); }
      .stat-value { color: var(--el-color-primary); }
    }
    &.stat-today {
      .stat-icon { background: var(--el-color-success); }
      .stat-value { color: var(--el-color-success); }
    }
  }

  .chart-row {
    .chart-card {
      margin-bottom: 12px;

      .chart {
        height: 380px;
        width: 100%;
      }
    }
  }
}
</style>
