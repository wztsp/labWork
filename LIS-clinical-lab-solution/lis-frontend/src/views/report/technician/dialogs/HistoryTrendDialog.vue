<template>
  <el-dialog
    v-model="visible"
    :title="`历史趋势: ${testItemName || ''}`"
    width="720px"
    :close-on-click-modal="false"
    @open="loadTrend"
  >
    <div v-if="patientText" class="trend-patient" style="margin-bottom: 8px">
      <el-tag type="info">{{ patientText }}</el-tag>
      <el-tag v-if="unit" style="margin-left: 8px">单位: {{ unit }}</el-tag>
    </div>

    <template v-if="loading">
      <div style="text-align:center;padding:40px 0">
        <el-icon class="is-loading" :size="28" color="#1a73e8"><Loading /></el-icon>
        <p style="color:#909399;margin-top:8px">加载趋势数据...</p>
      </div>
    </template>

    <el-empty v-else-if="points.length === 0" description="暂无历史结果数据" />

    <template v-else>
      <VChart v-if="chartOption" :option="chartOption" autoresize class="trend-chart" />
      <div class="trend-legend">
        <span class="legend-dot dot-normal"></span>正常
        <span class="legend-dot dot-abnormal"></span>偏高/偏低
        <span class="legend-dot dot-critical"></span>危急值
      </div>
      <el-table :data="points" size="small" max-height="260" style="margin-top:8px">
        <el-table-column label="检验时间" prop="testTime" width="150" />
        <el-table-column label="结果" align="center">
          <template #default="{ row }">
            <span :class="pointCellClass(row)">{{ row.resultValue }}{{ row.resultText ? ' ' + row.resultText : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isCritical === 1 || row.isCritical === true" type="danger" size="small" effect="dark">危急值</el-tag>
            <el-tag v-else-if="row.abnormalFlag === 'H'" type="warning" size="small">偏高</el-tag>
            <el-tag v-else-if="row.abnormalFlag === 'L'" type="warning" size="small">偏低</el-tag>
            <el-tag v-else type="info" size="small">正常</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, MarkLineComponent } from 'echarts/components'
import { getResultTrend, type TrendPoint } from '@/api/report'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent])

const props = defineProps<{
  modelValue: boolean
  patientId?: number | null
  testItemId?: number | null
  testItemName?: string
  patientText?: string
  unit?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })

const loading = ref(false)
const points = ref<TrendPoint[]>([])
const unit = ref(props.unit || '')

const chartOption = computed(() => {
  const list = points.value
  if (list.length === 0) return null
  const times = list.map(p => fmtTime(p.testTime))
  const values = list.map(p => parseFloat(p.resultValue || '') as number)
  const data = values.map((v, i) => ({ value: [times[i], v], itemStyle: pointStyle(list[i]) }))
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        const pt = list[p.dataIndex]
        return `${fmtTime(pt.testTime)}<br/>${pt.resultValue}${pt.resultText ? ' ' + pt.resultText : ''} ${pt.unit ? pt.unit : ''}`
      }
    },
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', scale: true },
    series: [{
      type: 'line',
      data,
      smooth: true,
      symbolSize: 8,
      lineStyle: { color: '#1a73e8', width: 2 },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { type: 'dashed', color: '#c0c4cc' },
        data: [{ type: 'average', name: '均值' }]
      }
    }]
  }
})

function pointStyle(p: TrendPoint) {
  if (p.isCritical === 1 || p.isCritical === true) return { color: '#f5222d', borderColor: '#f5222d' }
  if (p.abnormalFlag === 'H' || p.abnormalFlag === 'L') return { color: '#e6a23c', borderColor: '#e6a23c' }
  return { color: '#1a73e8', borderColor: '#1a73e8' }
}
function pointCellClass(p: TrendPoint) {
  if (p.isCritical === 1 || p.isCritical === true) return 'trend-cell-critical'
  if (p.abnormalFlag === 'H' || p.abnormalFlag === 'L') return 'trend-cell-abnormal'
  return ''
}
function fmtTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t).slice(0, 16)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadTrend() {
  points.value = []
  unit.value = props.unit || ''
  if (!props.patientId || !props.testItemId) return
  loading.value = true
  try {
    const list = await getResultTrend(props.patientId, props.testItemId)
    points.value = list || []
    if (list && list.length > 0 && list[0].unit) unit.value = list[0].unit
  } catch (e) {
    points.value = []
  } finally {
    loading.value = false
  }
}
watch(() => props.modelValue, v => { if (v) loadTrend() })
</script>

<style scoped>
.trend-chart {
  width: 100%;
  height: 300px;
}
.trend-legend {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0 8px;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.dot-normal { background: #1a73e8; }
.dot-abnormal { background: #e6a23c; }
.dot-critical { background: #f5222d; }
.trend-cell-critical { color: #f5222d; font-weight: 700; }
.trend-cell-abnormal { color: #e6a23c; font-weight: 600; }
</style>