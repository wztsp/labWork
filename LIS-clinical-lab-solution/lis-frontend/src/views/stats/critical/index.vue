<template>
  <div class="critical-stats-page">
    <!-- 顶部: 日期范围选择 + 查询按钮 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
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
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-total">
            <div class="stat-icon">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">危急值总数</div>
              <div class="stat-value">{{ summary.totalCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-confirmed">
            <div class="stat-icon">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">已确认数</div>
              <div class="stat-value">{{ summary.confirmedCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-rate">
            <div class="stat-icon">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">确认率</div>
              <div class="stat-value">{{ summary.confirmRate }}%</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-duration">
            <div class="stat-icon">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">平均确认时长(分)</div>
              <div class="stat-value">{{ summary.avgDuration }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <el-row :gutter="20" class="chart-row">
        <el-col :span="16">
          <el-card shadow="never" class="chart-card">
            <template #header>按日危急值数量</template>
            <v-chart class="chart" :option="dailyChartOption" autoresize />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="chart-card">
            <template #header>按项目分布</template>
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
import { Warning, CircleCheck, DataLine, Timer } from '@element-plus/icons-vue'
import { getCriticalStats, getCriticalByItem, getCriticalByDay } from '@/api/stats'

use([CanvasRenderer, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

interface DailyItem {
  date: string
  count: number
}

interface ItemDist {
  itemName: string
  count: number
}

const loading = ref(false)
const dateRange = ref<[string, string] | null>([null, null])

const summary = reactive({
  totalCount: 0,
  confirmedCount: 0,
  confirmRate: 0,
  avgDuration: 0
})

const dailyList = ref<DailyItem[]>([])
const itemList = ref<ItemDist[]>([])

async function fetchData() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择日期范围')
    return
  }
  loading.value = true
  const params = {
    startDate: dateRange.value[0],
    endDate: dateRange.value[1]
  }
  try {
    const [summaryRes, dayRes, itemRes] = await Promise.all([
      getCriticalStats(params),
      getCriticalByDay(params),
      getCriticalByItem(params)
    ])
    summary.totalCount = Number((summaryRes as any)?.total ?? 0)
    summary.confirmedCount = Number((summaryRes as any)?.confirmed ?? 0)
    summary.confirmRate = Number((summaryRes as any)?.confirmRate ?? 0)
    summary.avgDuration = Math.round(Number((summaryRes as any)?.avgConfirmSeconds ?? 0) / 60)
    dailyList.value = ((dayRes as any) || []).map((r: any) => ({
      date: String(r.day ?? ''),
      count: Number(r.count ?? 0)
    }))
    itemList.value = ((itemRes as any) || []).map((r: any) => ({
      itemName: String(r.item_name ?? '未知'),
      count: Number(r.count ?? 0)
    }))
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
      axisPointer: { type: 'shadow' }
    },
    grid: { left: 50, right: 30, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: dailyList.value.map(d => d.date),
      axisLabel: { rotate: 30 }
    },
    yAxis: { type: 'value', name: '数量' },
    series: [
      {
        name: '危急值数量',
        type: 'bar',
        data: dailyList.value.map(d => d.count),
        itemStyle: { color: '#F56C6C' },
        barMaxWidth: 32
      }
    ]
  }
})

const itemChartOption = computed(() => {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', type: 'scroll' },
    series: [
      {
        name: '项目分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['55%', '50%'],
        avoidLabelOverlap: true,
        data: itemList.value.map(d => ({ name: d.itemName, value: d.count })),
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
.critical-stats-page {
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
        font-size: 28px;
        font-weight: bold;
        color: var(--el-text-color-primary);
        line-height: 1.2;
      }
    }

    &.stat-total .stat-icon { background: var(--el-color-danger); }
    &.stat-total .stat-value { color: var(--el-color-danger); }
    &.stat-confirmed {
      .stat-icon { background: var(--el-color-success); }
      .stat-value { color: var(--el-color-success); }
    }
    &.stat-rate {
      .stat-icon { background: var(--el-color-primary); }
      .stat-value { color: var(--el-color-primary); }
    }
    &.stat-duration {
      .stat-icon { background: var(--el-color-warning); }
      .stat-value { color: var(--el-color-warning); }
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
