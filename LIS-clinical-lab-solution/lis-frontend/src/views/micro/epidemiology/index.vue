<template>
  <div class="epi-stat-page">
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
          <el-button @click="handleResetRange">近30天</el-button>
          <el-button plain @click="handleAllTime">全部</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <!-- 统计卡片 -->
      <el-row :gutter="12" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-report">
            <div class="stat-info">
              <div class="stat-label">报告总数</div>
              <div class="stat-value">{{ stat.totalReportCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-organism">
            <div class="stat-info">
              <div class="stat-label">细菌鉴定数</div>
              <div class="stat-value">{{ stat.totalOrganismCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-sensitivity">
            <div class="stat-info">
              <div class="stat-label">药敏结果数</div>
              <div class="stat-value">{{ stat.totalSensitivityCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-alert">
            <div class="stat-info">
              <div class="stat-label">耐药预警数</div>
              <div class="stat-value">{{ stat.totalAlertCount }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 第1行: 细菌分布 + 药敏结果分布 -->
      <el-row :gutter="12" class="chart-row">
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header>细菌分布统计</template>
            <v-chart v-if="stat.organismDistribution.length" class="chart" :option="organismChartOption" autoresize />
            <el-empty v-else description="暂无数据" :image-size="80" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header>药敏结果分布 (S/I/R)</template>
            <v-chart v-if="hasResultDist" class="chart" :option="resultDistChartOption" autoresize />
            <el-empty v-else description="暂无数据" :image-size="80" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 第2行: 月度趋势 + 特殊耐药预警 -->
      <el-row :gutter="12" class="chart-row">
        <el-col :span="14">
          <el-card shadow="never" class="chart-card">
            <template #header>月度检出趋势</template>
            <v-chart v-if="stat.monthlyTrend.length" class="chart" :option="monthlyTrendChartOption" autoresize />
            <el-empty v-else description="暂无数据" :image-size="80" />
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never" class="chart-card">
            <template #header>特殊耐药预警分布</template>
            <v-chart v-if="stat.alertTypeDistribution.length" class="chart" :option="alertChartOption" autoresize />
            <el-empty v-else description="暂无数据" :image-size="80" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 第3行: 抗生素耐药率 -->
      <el-card shadow="never" class="chart-card resistance-chart-card">
        <template #header>抗生素耐药率统计 (%)</template>
        <v-chart v-if="stat.resistanceRates.length" class="chart-tall" :option="resistanceRateChartOption" autoresize />
        <el-empty v-else description="暂无药敏数据" :image-size="80" />
      </el-card>

      <!-- 标本类型分布 + 培养部位 -->
      <el-row :gutter="12" class="chart-row">
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header>标本类型分布</template>
            <el-table :data="stat.specimenTypeDistribution" border stripe size="small" max-height="320">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="name" label="标本类型" min-width="160" />
              <el-table-column label="数量" width="100" align="center">
                <template #default="{ row }">
                  <el-tag type="primary" size="small">{{ row.count }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="占比" min-width="160">
                <template #default="{ row }">
                  <el-progress :percentage="percentOf(row.count, specimenTotal)" :stroke-width="14" :text-inside="true" />
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无数据" :image-size="60" />
              </template>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header>培养部位分布 (前10)</template>
            <el-table :data="stat.cultureSiteDistribution" border stripe size="small" max-height="320">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="name" label="培养部位" min-width="160" />
              <el-table-column label="数量" width="100" align="center">
                <template #default="{ row }">
                  <el-tag type="success" size="small">{{ row.count }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="占比" min-width="160">
                <template #default="{ row }">
                  <el-progress :percentage="percentOf(row.count, siteTotal)" :stroke-width="14" :text-inside="true" status="success" />
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无数据" :image-size="60" />
              </template>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- 抗生素耐药率明细表 -->
      <el-card shadow="never" class="chart-card">
        <template #header>抗生素耐药率明细</template>
        <el-table :data="stat.resistanceRates" border stripe size="small" max-height="400">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="antibioticName" label="抗生素" min-width="160" />
          <el-table-column label="检测总数" width="100" align="center">
            <template #default="{ row }">{{ row.totalCount }}</template>
          </el-table-column>
          <el-table-column label="敏感(S)" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="success" size="small">{{ row.susceptibleCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="中介(I)" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="warning" size="small">{{ row.intermediateCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耐药(R)" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="danger" size="small">{{ row.resistantCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耐药率" min-width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="row.resistanceRate"
                :stroke-width="14"
                :text-inside="true"
                :status="resistanceStatus(row.resistanceRate)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无药敏数据" :image-size="60" />
          </template>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { getMicroEpiStat, type MicroEpiStat } from '@/api/report/micro'

use([CanvasRenderer, BarChart, PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const loading = ref(false)
const dateRange = ref<[string, string] | null>([null, null])

const stat = reactive<MicroEpiStat>({
  totalReportCount: 0,
  totalOrganismCount: 0,
  totalSensitivityCount: 0,
  totalAlertCount: 0,
  organismDistribution: [],
  specimenTypeDistribution: [],
  cultureSiteDistribution: [],
  resistanceRates: [],
  monthlyTrend: [],
  alertTypeDistribution: [],
  resultDistribution: {}
})

const hasResultDist = computed(() => {
  const d = stat.resultDistribution || {}
  return Object.values(d).some((v) => v > 0)
})

const specimenTotal = computed(() =>
  stat.specimenTypeDistribution.reduce((s, i) => s + i.count, 0)
)
const siteTotal = computed(() =>
  stat.cultureSiteDistribution.reduce((s, i) => s + i.count, 0)
)

function percentOf(count: number, total: number): number {
  if (!total) return 0
  return Math.round((count / total) * 1000) / 10
}

function resistanceStatus(rate: number): string {
  if (rate >= 50) return 'exception'
  if (rate >= 25) return 'warning'
  return 'success'
}

// 细菌分布饼图
const organismChartOption = computed(() => {
  const data = stat.organismDistribution.slice(0, 15).map((d) => ({ name: d.name, value: d.count }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', left: 'left', top: 'middle' },
    series: [
      {
        name: '细菌分布',
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['60%', '50%'],
        avoidLabelOverlap: true,
        data,
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' }
        }
      }
    ]
  }
})

// 药敏结果分布饼图
const resultDistChartOption = computed(() => {
  const d = stat.resultDistribution || {}
  const data = [
    { name: '敏感 (S)', value: d.S || 0, itemStyle: { color: '#67c23a' } },
    { name: '中介 (I)', value: d.I || 0, itemStyle: { color: '#e6a23c' } },
    { name: '耐药 (R)', value: d.R || 0, itemStyle: { color: '#f56c6c' } }
  ].filter((i) => i.value > 0)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        name: '药敏结果',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        data,
        label: { formatter: '{b}\n{d}%' },
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' }
        }
      }
    ]
  }
})

// 月度趋势折线图
const monthlyTrendChartOption = computed(() => {
  const data = stat.monthlyTrend
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 30, bottom: 50 },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.name),
      axisLabel: { rotate: 30 }
    },
    yAxis: { type: 'value', name: '报告数' },
    series: [
      {
        name: '检出报告数',
        type: 'line',
        smooth: true,
        data: data.map((d) => d.count),
        itemStyle: { color: '#1a73e8' },
        areaStyle: { opacity: 0.2 },
        label: { show: true, position: 'top' }
      }
    ]
  }
})

// 特殊耐药预警分布柱状图
const alertChartOption = computed(() => {
  const data = stat.alertTypeDistribution
  const alertNameMap: Record<string, string> = {
    MRSA: 'MRSA',
    VRE: 'VRE',
    CRE: 'CRE',
    ESBL: 'ESBL',
    CRAB: 'CRAB',
    MDR: 'MDR',
    CRITICAL_VALUE: '危急值'
  }
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 60, right: 30, top: 30, bottom: 40 },
    xAxis: {
      type: 'category',
      data: data.map((d) => alertNameMap[d.name] || d.name),
      axisLabel: { rotate: 25 }
    },
    yAxis: { type: 'value', name: '数量' },
    series: [
      {
        name: '预警数',
        type: 'bar',
        data: data.map((d) => d.count),
        itemStyle: { color: '#f56c6c' },
        barMaxWidth: 36,
        label: { show: true, position: 'top' }
      }
    ]
  }
})

// 抗生素耐药率柱状图 (横向)
const resistanceRateChartOption = computed(() => {
  const data = stat.resistanceRates.slice(0, 20)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const idx = params[0].dataIndex
        const item = data[idx]
        if (!item) return ''
        return `${item.antibioticName}<br/>` +
          `耐药率: ${item.resistanceRate}%<br/>` +
          `检测总数: ${item.totalCount} (S:${item.susceptibleCount} I:${item.intermediateCount} R:${item.resistantCount})`
      }
    },
    grid: { left: 130, right: 40, top: 30, bottom: 30 },
    xAxis: { type: 'value', name: '耐药率 %', max: 100 },
    yAxis: {
      type: 'category',
      data: data.map((d) => d.antibioticName).reverse(),
      axisLabel: { interval: 0 }
    },
    series: [
      {
        name: '耐药率',
        type: 'bar',
        data: data.map((d) => d.resistanceRate).reverse(),
        barMaxWidth: 20,
        itemStyle: {
          color: (params: any) => {
            const v = params.value
            if (v >= 50) return '#f56c6c'
            if (v >= 25) return '#e6a23c'
            return '#67c23a'
          }
        },
        label: { show: true, position: 'right', formatter: '{c}%' }
      }
    ]
  }
})

async function fetchStat() {
  loading.value = true
  try {
    const [begin, end] = dateRange.value || [undefined, undefined]
    const res = await getMicroEpiStat(begin, end)
    Object.assign(stat, res || {})
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleQuery() {
  await fetchStat()
}

function handleResetRange() {
  const end = dayjs()
  const start = end.subtract(29, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
  fetchStat()
}

function handleAllTime() {
  dateRange.value = null
  fetchStat()
}

function initDefaultRange() {
  const end = dayjs()
  const start = end.subtract(29, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

onMounted(() => {
  initDefaultRange()
  fetchStat()
})
</script>

<style scoped lang="scss">
.epi-stat-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;
  }

  .stats-row {
    margin-bottom: 12px;
  }

  .stat-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 18px 20px;
    }

    .stat-info {
      .stat-label {
        font-size: 13px;
        color: #909399;
        margin-bottom: 6px;
      }

      .stat-value {
        font-size: 28px;
        font-weight: 600;
      }
    }

    &.stat-report .stat-value { color: #1a73e8; }
    &.stat-organism .stat-value { color: #67c23a; }
    &.stat-sensitivity .stat-value { color: #e6a23c; }
    &.stat-alert .stat-value { color: #f56c6c; }
  }

  .chart-row {
    margin-bottom: 12px;
  }

  .chart-card {
    margin-bottom: 12px;

    .chart {
      height: 340px;
      width: 100%;
    }

    .chart-tall {
      height: 480px;
      width: 100%;
    }
  }

  .resistance-chart-card {
    margin-bottom: 12px;
  }
}
</style>
