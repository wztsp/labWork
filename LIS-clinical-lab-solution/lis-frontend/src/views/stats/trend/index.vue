<template>
  <div class="trend-page">
    <!-- 顶部: 患者搜索 + 项目选择 + 查询 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="患者">
          <el-select
            v-model="searchForm.patientId"
            placeholder="输入ID或姓名搜索患者"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="searchPatients"
            :loading="patientLoading"
            style="width: 260px"
          >
            <el-option
              v-for="p in patientOptions"
              :key="p.patientId"
              :label="`${p.patientName} (ID:${p.patientId})${p.gender || ''}${p.age ? ' ' + p.age + '岁' : ''}`"
              :value="p.patientId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="检验项目">
          <el-select
            v-model="searchForm.itemId"
            placeholder="请选择项目"
            filterable
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="item in itemOptions"
              :key="item.id"
              :label="`${item.itemName}${item.unit ? ' [' + item.unit + ']' : ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 趋势图 -->
    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="panel-header">
          <span>历史结果趋势</span>
          <span v-if="trendData" class="sub-info">
            {{ trendData.itemName }} | 参考范围: {{ trendData.refLow }} ~ {{ trendData.refHigh }} {{ trendData.unit }}
          </span>
        </div>
      </template>
      <div v-loading="loading" class="chart-wrapper">
        <v-chart v-if="trendData && trendData.points.length" class="chart" :option="chartOption" autoresize />
        <el-empty v-else description="请选择患者和检验项目后查询" />
      </div>
    </el-card>

    <!-- 数据列表 -->
    <el-card v-if="trendData && trendData.points.length" shadow="never" class="table-card">
      <template #header>数据明细</template>
      <el-table :data="trendData.points" border stripe size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="检测时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.testTime) }}</template>
        </el-table-column>
        <el-table-column label="结果值" width="120" align="center">
          <template #default="{ row }">
            <span :class="pointClass(row)">{{ row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="100" align="center" />
        <el-table-column label="参考范围" min-width="140" align="center">
          <template #default>{{ trendData.refLow }} ~ {{ trendData.refHigh }}</template>
        </el-table-column>
        <el-table-column label="异常标记" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isCritical === 1" type="danger" effect="dark" size="small">危急值</el-tag>
            <el-tag v-else-if="row.abnormalFlag === 'H'" type="warning" size="small">偏高</el-tag>
            <el-tag v-else-if="row.abnormalFlag === 'L'" type="warning" size="small">偏低</el-tag>
            <span v-else class="text-muted">正常</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, MarkAreaComponent } from 'echarts/components'
import { formatDateTime } from '@/utils/format'
import { getAllTestItems, type TestItem, getRefRangeList } from '@/api/base'
import { getReportPage, getResultTrend, type TrendPoint as ApiTrendPoint } from '@/api/report'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, MarkAreaComponent])

interface LocalTrendPoint {
  testTime: string
  value: number
  unit: string
  abnormal: boolean
  abnormalFlag: string
  isCritical: number
}

interface TrendData {
  itemName: string
  unit: string
  refLow: number
  refHigh: number
  points: LocalTrendPoint[]
}

// 检验项目字典(含refLow/refHigh兜底)
const itemOptions = ref<(TestItem & { refLow?: number; refHigh?: number })[]>([])
// 患者搜索结果
interface PatientOption { patientId: number; patientName: string; gender?: string; age?: number }
const patientOptions = ref<PatientOption[]>([])
const patientLoading = ref(false)

const loading = ref(false)
const trendData = ref<TrendData | null>(null)

const searchForm = reactive({
  patientId: undefined as number | undefined,
  itemId: undefined as number | undefined
})

/** 加载检验项目字典 & 其参考范围 */
async function loadItemDict() {
  try {
    const items = (await getAllTestItems()) || []
    // 为每个项目查询参考范围, 取 refLow/refHigh (用默认通用范围)
    const itemsWithRange = await Promise.all(items.map(async (item) => {
      let refLow: number | undefined
      let refHigh: number | undefined
      try {
        const page = await getRefRangeList({ testItemId: item.id, pageNum: 1, pageSize: 10 })
        const r = page?.records?.[0]
        if (r) {
          if (r.refLow != null && !isNaN(Number(r.refLow))) refLow = Number(r.refLow)
          if (r.refHigh != null && !isNaN(Number(r.refHigh))) refHigh = Number(r.refHigh)
        }
      } catch (_) { /* ignore */ }
      return { ...item, refLow, refHigh }
    }))
    itemOptions.value = itemsWithRange.filter(i => i.status === 1)
  } catch (e) {
    console.error('加载检验项目字典失败', e)
  }
}

/** 搜索患者: 通过报告列表按姓名/patientNo模糊查询, 返回去重后的患者集合 */
let searchTimer: any = null
async function searchPatients(keyword: string) {
  patientLoading.value = true
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    try {
      const kw = (keyword || '').trim()
      const set = new Map<number, PatientOption>()
      // 支持 patientName 模糊匹配
      const page = await getReportPage({ size: 100, patientName: kw || undefined })
      const records = page?.records || []
      for (const r of records) {
        if (!r.patientId) continue
        if (!set.has(r.patientId)) {
          set.set(r.patientId, {
            patientId: Number(r.patientId),
            patientName: r.patientName || '未知',
            gender: r.patientGender === 1 ? '男' : r.patientGender === 2 ? '女' : undefined,
            age: r.patientAge as number | undefined
          })
        }
      }
      patientOptions.value = Array.from(set.values())
    } catch (e) {
      console.error('搜索患者失败', e)
      patientOptions.value = []
    } finally {
      patientLoading.value = false
    }
  }, 300)
}

const currentItem = computed(() => itemOptions.value.find(i => i.id === searchForm.itemId))

/** 解析 refRange 字符串 "135~145" 或 "135 - 145" 等 */
function parseRefRange(refRange: string): { low?: number; high?: number } {
  if (!refRange) return {}
  const m = String(refRange).match(/([\-+]?\d+(?:\.\d+)?)\s*[~\-～至到]\s*([\-+]?\d+(?:\.\d+)?)/)
  if (!m) return {}
  const low = Number(m[1]); const high = Number(m[2])
  return { low: isNaN(low) ? undefined : low, high: isNaN(high) ? undefined : high }
}

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const data = trendData.value
  if (!data.points.length) return {}
  const xData = data.points.map(p => dayjs(p.testTime).format('MM-DD HH:mm'))
  const values = data.points.map(p => p.value)

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex ?? 0
        const p = data.points[idx]
        if (!p) return ''
        const tag = p.isCritical === 1 ? '<span style="color:#f5222d"><b>危急值</b></span>'
          : p.abnormalFlag === 'H' || p.abnormalFlag === 'L' ? '<span style="color:#e6a23c">异常</span>'
          : '<span style="color:#67c23a">正常</span>'
        return `时间: ${formatDateTime(p.testTime)}<br/>结果: <b>${p.value}</b> ${data.unit}<br/>状态: ${tag}`
      }
    },
    grid: { left: 60, right: 30, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: xData,
      axisLabel: { rotate: 30 },
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: `${data.itemName}${data.unit ? ' (' + data.unit + ')' : ''}`,
      min: (data.refLow != null ? Math.min(data.refLow, ...values) : Math.min(...values)) * 0.9,
      max: (data.refHigh != null ? Math.max(data.refHigh, ...values) : Math.max(...values)) * 1.1
    },
    series: [
      {
        name: data.itemName,
        type: 'line',
        data: data.points.map(p => ({
          value: p.value,
          itemStyle: p.isCritical === 1 ? { color: '#f5222d', borderColor: '#f5222d' }
            : p.abnormalFlag === 'H' || p.abnormalFlag === 'L' ? { color: '#e6a23c', borderColor: '#e6a23c' }
            : { color: '#1A73E8' }
        })),
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: '#1A73E8', width: 2 },
        markArea: data.refLow != null && data.refHigh != null ? {
          silent: true,
          itemStyle: { color: 'rgba(103, 194, 58, 0.15)' },
          data: [[{ yAxis: data.refLow }, { yAxis: data.refHigh }]]
        } : undefined,
        markLine: data.refLow != null && data.refHigh != null ? {
          symbol: 'none',
          silent: true,
          data: [
            { yAxis: data.refHigh, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: `上限 ${data.refHigh}` } },
            { yAxis: data.refLow, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: `下限 ${data.refLow}` } }
          ]
        } : undefined
      }
    ]
  }
})

function pointClass(p: LocalTrendPoint) {
  if (p.isCritical === 1) return 'trend-critical'
  if (p.abnormalFlag === 'H' || p.abnormalFlag === 'L') return 'trend-abnormal'
  return ''
}

async function handleQuery() {
  if (!searchForm.patientId) {
    ElMessage.warning('请选择患者')
    return
  }
  if (!searchForm.itemId) {
    ElMessage.warning('请选择检验项目')
    return
  }
  loading.value = true
  trendData.value = null
  try {
    const list: ApiTrendPoint[] = (await getResultTrend(searchForm.patientId, searchForm.itemId)) || []
    // 项目信息兜底
    const item = currentItem.value
    let refLow = item?.refLow
    let refHigh = item?.refHigh
    let unit = item?.unit || ''
    // 从结果中推断 refLow/refHigh/unit
    for (const r of list) {
      if (!unit && r.unit) unit = r.unit
      // 结果 refRange 优先
      if ((refLow == null || refHigh == null)) {
        const parsed = parseRefRange(r.resultText || '')
        if (parsed.low != null && parsed.high != null) { refLow = parsed.low; refHigh = parsed.high }
      }
    }
    // 再兜底: 用所有结果的均值 ± 20%
    const values = list.map(r => Number(r.resultValue)).filter(v => !isNaN(v))
    if (values.length > 0 && (refLow == null || refHigh == null)) {
      const avg = values.reduce((a, b) => a + b, 0) / values.length
      if (refLow == null) refLow = Number((avg * 0.8).toFixed(2))
      if (refHigh == null) refHigh = Number((avg * 1.2).toFixed(2))
    }
    const points: LocalTrendPoint[] = list
      .filter(r => r.resultValue != null && r.resultValue !== '' && !isNaN(Number(r.resultValue)))
      .map(r => ({
        testTime: r.testTime,
        value: Number(r.resultValue),
        unit: r.unit || unit || '',
        abnormal: r.abnormalFlag === 'H' || r.abnormalFlag === 'L' || r.isCritical === 1,
        abnormalFlag: r.abnormalFlag || 'N',
        isCritical: Number(r.isCritical) || 0
      }))
      .sort((a, b) => dayjs(a.testTime).valueOf() - dayjs(b.testTime).valueOf())

    if (!points.length) {
      ElMessage.info('该患者该项目暂无历史检验结果')
      return
    }

    trendData.value = {
      itemName: item?.itemName || '检验项目',
      unit: unit || (points[0].unit),
      refLow: refLow as number,
      refHigh: refHigh as number,
      points
    }
  } catch (e: any) {
    console.error(e)
    ElMessage.error('查询失败: ' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadItemDict()
})
</script>

<style scoped lang="scss">
.trend-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;
  }

  .chart-card {
    margin-bottom: 12px;

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .sub-info {
        font-size: 13px;
        color: var(--el-text-color-secondary);
        font-weight: normal;
      }
    }

    .chart-wrapper {
      min-height: 420px;

      .chart {
        height: 420px;
        width: 100%;
      }
    }
  }

  .table-card {
    :deep(.trend-abnormal) {
      color: var(--el-color-warning);
      font-weight: 700;
    }
    :deep(.trend-critical) {
      color: var(--el-color-danger);
      font-weight: 800;
    }

    .text-muted {
      color: var(--el-color-success);
    }
  }
}
</style>
