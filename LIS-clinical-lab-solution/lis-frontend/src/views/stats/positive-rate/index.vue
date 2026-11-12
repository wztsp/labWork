<template>
  <div class="positive-rate-page">
    <!-- 顶部: 日期范围 + 项目选择 + 标本类型 + 查询 -->
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
        <el-form-item label="检验项目">
          <el-select
            v-model="searchForm.testItemId"
            placeholder="全部项目"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="t in testItemOptions"
              :key="t.id"
              :label="t.itemName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标本类型">
          <el-select
            v-model="searchForm.specimenType"
            placeholder="全部标本"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option
              v-for="s in specimenOptions"
              :key="s.typeCode"
              :label="s.typeName"
              :value="s.typeCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">近30天</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <!-- 概览卡片 -->
      <el-row :gutter="20" class="summary-row">
        <el-col :span="8">
          <el-card shadow="hover" class="sum-card">
            <div class="sum-label">检验项目数</div>
            <div class="sum-value">{{ tableData.length }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="sum-card">
            <div class="sum-label">检测总次数</div>
            <div class="sum-value">{{ totalTestCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="sum-card">
            <div class="sum-label">阳性总次数</div>
            <div class="sum-value sum-positive">{{ totalPositiveCount }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 阳性率趋势折线图 -->
      <el-card shadow="never" class="chart-card">
        <template #header>阳性率趋势 (按日期)</template>
        <v-chart v-if="trendData.length > 0" class="chart" :option="trendChartOption" autoresize />
        <el-empty v-else description="所选区间暂无趋势数据" :image-size="100" />
      </el-card>

      <!-- 阳性率对比图 -->
      <el-card shadow="never" class="chart-card">
        <template #header>各项目阳性率对比</template>
        <v-chart v-if="tableData.length > 0" class="chart" :option="chartOption" autoresize />
        <el-empty v-else description="所选区间暂无数据" :image-size="100" />
      </el-card>

      <!-- 表格 -->
      <el-card shadow="never" class="table-card">
        <template #header>项目阳性率明细</template>
        <el-table :data="tableData" border stripe style="width: 100%">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="item_name" label="项目名称" min-width="160" />
          <el-table-column v-if="searchForm.specimenType" label="标本类型" width="120" align="center">
            <template #default="{ row }">{{ row.sample_type || '-' }}</template>
          </el-table-column>
          <el-table-column label="检测总数" width="120" align="center">
            <template #default="{ row }">{{ row.total }}</template>
          </el-table-column>
          <el-table-column label="阳性数" width="120" align="center">
            <template #default="{ row }">
              <span :class="{ 'positive-num': row.positive > 0 }">{{ row.positive }}</span>
            </template>
          </el-table-column>
          <el-table-column label="阳性率" width="180" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="Number(row.positive_rate)"
                :color="getRateColor(row.positive_rate)"
                :stroke-width="14"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.positive_rate > 50 ? 'danger' : 'success'" size="small">
                {{ row.positive_rate > 50 ? '偏高' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="tableData.length === 0" description="暂无明细数据" :image-size="80" />
      </el-card>
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
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { getPositiveRate, getPositiveRateTrend } from '@/api/stats'
import { getTestItemList, getAllSpecimenTypes, type SpecimenType } from '@/api/base'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

interface PositiveRow {
  test_item_id: number
  item_name: string
  total: number
  positive: number
  positive_rate: number
  sample_type?: string
}

interface TrendRow {
  stat_date: string
  total: number
  positive: number
  positive_rate: number
}

interface TestItemOption {
  id: number
  itemName: string
}

const loading = ref(false)
const dateRange = ref<[string, string] | null>([null, null])
const tableData = ref<PositiveRow[]>([])
const trendData = ref<TrendRow[]>([])
const testItemOptions = ref<TestItemOption[]>([])
const specimenOptions = ref<SpecimenType[]>([])

const searchForm = reactive({
  testItemId: undefined as number | undefined,
  specimenType: undefined as string | undefined
})

const totalTestCount = computed(() => tableData.value.reduce((sum, r) => sum + r.total, 0))
const totalPositiveCount = computed(() => tableData.value.reduce((sum, r) => sum + r.positive, 0))

// 加载检验项目下拉 (仅 is_panel=0 的检验指标, 用于阳性率筛选)
async function loadTestItemOptions() {
  try {
    // 调用基础字典接口获取检验指标列表 (后端 /api/base/items, isPanel=0)
    const res: any = await getTestItemList({
      pageNum: 1,
      pageSize: 500,
      isPanel: 0,
      status: 1
    })
    const list = Array.isArray(res) ? res : res?.records || []
    testItemOptions.value = list.map((t: any) => ({
      id: Number(t.id),
      itemName: String(t.itemName || t.item_name || '')
    }))
  } catch (e) {
    testItemOptions.value = []
  }
}

// 加载标本类型下拉
async function loadSpecimenOptions() {
  try {
    const list = await getAllSpecimenTypes()
    specimenOptions.value = (list || []).filter((s) => s.status === 1)
  } catch (e) {
    specimenOptions.value = []
  }
}

async function fetchData() {
  if (!dateRange.value) return
  loading.value = true
  try {
    const params: any = {
      startDate: dateRange.value[0],
      endDate: dateRange.value[1]
    }
    if (searchForm.testItemId) params.testItemId = searchForm.testItemId
    if (searchForm.specimenType) params.specimenType = searchForm.specimenType
    // 并行请求明细和趋势
    const [rateRes, trendRes] = await Promise.all([
      getPositiveRate(params),
      getPositiveRateTrend(params)
    ])
    tableData.value = ((rateRes as any) || []).map((r: any) => ({
      test_item_id: Number(r.test_item_id ?? 0),
      item_name: String(r.item_name ?? '未知'),
      total: Number(r.total ?? 0),
      positive: Number(r.positive ?? 0),
      positive_rate: Number(r.positive_rate ?? 0),
      sample_type: r.sample_type ? String(r.sample_type) : undefined
    }))
    trendData.value = ((trendRes as any) || []).map((r: any) => ({
      stat_date: String(r.stat_date ?? ''),
      total: Number(r.total ?? 0),
      positive: Number(r.positive ?? 0),
      positive_rate: Number(r.positive_rate ?? 0)
    }))
  } catch (e) {
    tableData.value = []
    trendData.value = []
  } finally {
    loading.value = false
  }
}

function getRateColor(rate: number): string {
  if (rate > 50) return '#F56C6C'
  if (rate > 20) return '#E6A23C'
  return '#67C23A'
}

// 阳性率对比柱状图
const chartOption = computed(() => {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex ?? 0
        const row = tableData.value[idx]
        if (!row) return ''
        return `${row.item_name}<br/>阳性率: ${row.positive_rate}%<br/>阳性数: ${row.positive}/${row.total}`
      }
    },
    grid: { left: 60, right: 30, top: 40, bottom: 70 },
    xAxis: {
      type: 'category',
      data: tableData.value.map((d) => d.item_name),
      axisLabel: { rotate: 30, interval: 0 }
    },
    yAxis: { type: 'value', name: '阳性率(%)', max: 100 },
    series: [
      {
        name: '阳性率',
        type: 'bar',
        data: tableData.value.map((d) => ({
          value: d.positive_rate,
          itemStyle: { color: getRateColor(d.positive_rate) }
        })),
        barMaxWidth: 40,
        label: {
          show: true,
          position: 'top',
          formatter: '{c}%'
        }
      }
    ]
  }
})

// 阳性率趋势折线图
const trendChartOption = computed(() => {
  const dates = trendData.value.map((d) => dayjs(d.stat_date).format('MM-DD'))
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex ?? 0
        const row = trendData.value[idx]
        if (!row) return ''
        return `${row.stat_date}<br/>阳性率: ${row.positive_rate}%<br/>阳性数: ${row.positive}/${row.total}`
      }
    },
    legend: { data: ['阳性率', '阳性数', '检测总数'], top: 0 },
    grid: { left: 60, right: 60, top: 40, bottom: 60 },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: { rotate: 30, interval: 'auto' }
    },
    yAxis: [
      { type: 'value', name: '阳性率(%)', max: 100, position: 'left' },
      { type: 'value', name: '数量', position: 'right' }
    ],
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 8 }],
    series: [
      {
        name: '阳性率',
        type: 'line',
        data: trendData.value.map((d) => d.positive_rate),
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        yAxisIndex: 0,
        itemStyle: { color: '#1A73E8' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64,158,255,0.3)' },
              { offset: 1, color: 'rgba(64,158,255,0.02)' }
            ]
          }
        },
        lineStyle: { width: 2 }
      },
      {
        name: '阳性数',
        type: 'line',
        data: trendData.value.map((d) => d.positive),
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 1,
        itemStyle: { color: '#F56C6C' }
      },
      {
        name: '检测总数',
        type: 'line',
        data: trendData.value.map((d) => d.total),
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 1,
        itemStyle: { color: '#67C23A' }
      }
    ]
  }
})

async function handleQuery() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择日期范围')
    return
  }
  fetchData()
}

function handleReset() {
  const end = dayjs()
  const start = end.subtract(29, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
  searchForm.testItemId = undefined
  searchForm.specimenType = undefined
  fetchData()
}

function initDefaultRange() {
  const end = dayjs()
  const start = end.subtract(29, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

onMounted(() => {
  initDefaultRange()
  loadTestItemOptions()
  loadSpecimenOptions()
  fetchData()
})
</script>

<style scoped lang="scss">
.positive-rate-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;
  }

  .summary-row {
    margin-bottom: 12px;

    .sum-card {
      margin-bottom: 12px;
      text-align: center;

      :deep(.el-card__body) {
        padding: 20px;
      }

      .sum-label {
        font-size: 14px;
        color: var(--el-text-color-secondary);
        margin-bottom: 8px;
      }

      .sum-value {
        font-size: 28px;
        font-weight: bold;
        color: var(--el-text-color-primary);

        &.sum-positive {
          color: var(--el-color-danger);
        }
      }
    }
  }

  .chart-card {
    margin-bottom: 12px;

    .chart {
      height: 400px;
      width: 100%;
    }
  }

  .positive-num {
    color: var(--el-color-danger);
    font-weight: bold;
  }
}
</style>
