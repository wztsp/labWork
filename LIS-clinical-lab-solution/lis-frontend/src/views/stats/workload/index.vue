<template>
  <div class="workload-page">
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
          <el-button @click="handleReset">近7天</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-total">
            <div class="stat-icon">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">总标本量</div>
              <div class="stat-value">{{ summary.totalCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-today">
            <div class="stat-icon">
              <el-icon><Calendar /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">今日标本量</div>
              <div class="stat-value">{{ summary.todayCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-urgent">
            <div class="stat-icon">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">急诊标本量</div>
              <div class="stat-value">{{ summary.urgentCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-reported">
            <div class="stat-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">已报告数</div>
              <div class="stat-value">{{ summary.reportedCount }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 图表区域 -->
      <el-row :gutter="20" class="chart-row">
        <el-col :span="16">
          <el-card shadow="never" class="chart-card">
            <template #header>按日标本量统计</template>
            <v-chart class="chart" :option="dailyChartOption" autoresize />
            <el-empty v-if="dailyList.length === 0" description="所选区间暂无数据" :image-size="80" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="chart-card">
            <template #header>科室标本分布</template>
            <v-chart class="chart" :option="deptChartOption" autoresize />
            <el-empty v-if="deptList.length === 0" description="暂无数据" :image-size="80" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 按仪器统计表格 -->
      <el-card shadow="never" class="instrument-card">
        <template #header>按仪器工作量明细</template>
        <el-table :data="instrumentList" border stripe style="width: 100%" max-height="320">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="instrument_name" label="仪器名称" min-width="200" />
          <el-table-column label="检测标本数" width="160" align="center">
            <template #default="{ row }">
              <el-tag type="primary" size="small">{{ row.count }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="占比" min-width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="instrumentPercent(row.count)"
                :stroke-width="14"
                :text-inside="true"
              />
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="instrumentList.length === 0" description="暂无仪器统计数据" :image-size="80" />
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
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { DataLine, Calendar, Warning, Document } from '@element-plus/icons-vue'
import { getWorkloadStats, getWorkloadByDay, getWorkloadByDept, getWorkloadByInstrument } from '@/api/stats'

use([CanvasRenderer, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

interface DailyItem {
  day: string
  count: number
}

interface DeptItem {
  department: string
  count: number
}

interface InstrumentItem {
  instrument_name: string
  count: number
}

const loading = ref(false)
const dateRange = ref<[string, string] | null>([null, null])

const summary = reactive({
  totalCount: 0,
  todayCount: 0,
  urgentCount: 0,
  reportedCount: 0
})

const dailyList = ref<DailyItem[]>([])
const deptList = ref<DeptItem[]>([])
const instrumentList = ref<InstrumentItem[]>([])

// 汇总数据 (不依赖日期范围, 反映全局实时状态)
async function fetchSummary() {
  try {
    const res: any = await getWorkloadStats({})
    summary.totalCount = Number(res?.total ?? 0)
    summary.todayCount = Number(res?.today ?? 0)
    summary.urgentCount = Number(res?.urgent ?? 0)
    summary.reportedCount = Number(res?.reported ?? 0)
  } catch (e) {
    // 接口异常时保持 0
  }
}

async function fetchByDay() {
  if (!dateRange.value) return
  try {
    const res: any = await getWorkloadByDay({ startDate: dateRange.value[0], endDate: dateRange.value[1] })
    dailyList.value = (res || []).map((r: any) => ({
      day: String(r.day ?? ''),
      count: Number(r.count ?? 0)
    }))
  } catch (e) {
    dailyList.value = []
  }
}

async function fetchByDept() {
  if (!dateRange.value) return
  try {
    const res: any = await getWorkloadByDept({ startDate: dateRange.value[0], endDate: dateRange.value[1] })
    deptList.value = (res || []).map((r: any) => ({
      department: String(r.department ?? '未知'),
      count: Number(r.count ?? 0)
    }))
  } catch (e) {
    deptList.value = []
  }
}

async function fetchByInstrument() {
  if (!dateRange.value) return
  try {
    const res: any = await getWorkloadByInstrument({
      startDate: dateRange.value[0],
      endDate: dateRange.value[1]
    })
    instrumentList.value = (res || []).map((r: any) => ({
      instrument_name: String(r.instrument_name ?? '未指定'),
      count: Number(r.count ?? 0)
    }))
  } catch (e) {
    instrumentList.value = []
  }
}

const dailyChartOption = computed(() => {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { left: 50, right: 30, top: 30, bottom: 50 },
    xAxis: {
      type: 'category',
      data: dailyList.value.map((d) => d.day),
      axisLabel: { rotate: 30 }
    },
    yAxis: { type: 'value', name: '数量' },
    series: [
      {
        name: '标本量',
        type: 'bar',
        data: dailyList.value.map((d) => d.count),
        itemStyle: { color: '#1A73E8' },
        barMaxWidth: 36,
        label: { show: true, position: 'top' }
      }
    ]
  }
})

const deptChartOption = computed(() => {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', type: 'scroll' },
    series: [
      {
        name: '科室标本分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['55%', '50%'],
        avoidLabelOverlap: true,
        data: deptList.value.map((d) => ({ name: d.department, value: d.count })),
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

const instrumentTotal = computed(() =>
  instrumentList.value.reduce((sum, i) => sum + i.count, 0)
)

function instrumentPercent(count: number): number {
  if (instrumentTotal.value === 0) return 0
  return Math.round((count / instrumentTotal.value) * 1000) / 10
}

async function handleQuery() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择日期范围')
    return
  }
  loading.value = true
  try {
    await Promise.all([fetchSummary(), fetchByDay(), fetchByDept(), fetchByInstrument()])
  } finally {
    loading.value = false
  }
}

function handleReset() {
  const end = dayjs()
  const start = end.subtract(6, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
  handleQuery()
}

function initDefaultRange() {
  const end = dayjs()
  const start = end.subtract(6, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

onMounted(() => {
  initDefaultRange()
  handleQuery()
})
</script>

<style scoped lang="scss">
.workload-page {
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

    &.stat-total .stat-icon { background: var(--el-color-primary); }
    &.stat-today .stat-icon { background: var(--el-color-primary); opacity: 0.85; }
    &.stat-urgent {
      .stat-icon { background: var(--el-color-danger); }
      .stat-value { color: var(--el-color-danger); }
    }
    &.stat-reported {
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

  .instrument-card {
    margin-bottom: 12px;
  }
}
</style>
