<template>
  <div class="qc-chart-page">
    <!-- 顶部: 仪器选择 + 日期范围 + 模式切换 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="仪器">
          <el-select
            v-model="searchForm.instrumentId"
            placeholder="请选择仪器"
            filterable
            clearable
            style="width: 240px"
            @change="onInstrumentChange"
          >
            <el-option
              v-for="item in instrumentList"
              :key="item.id"
              :label="item.instrumentName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
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
          <el-radio-group v-model="chartMode" @change="handleModeChange">
            <el-radio-button value="lj">Levey-Jennings</el-radio-button>
            <el-radio-button value="zscore">Z-分数</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button
            type="warning"
            :loading="recalculating"
            :disabled="!searchForm.lotId"
            @click="handleRecalculate"
          >
            计算失控
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 主体: 左侧指标批号树 + 右侧图表数据 -->
    <div class="main-body">
      <!-- 左侧: 指标 -> 批号 树形列表 -->
      <el-card shadow="never" class="left-tree-card">
        <template #header>
          <div class="tree-header">
            <span>指标 / 批号</span>
            <el-tag v-if="searchForm.instrumentId" size="small" type="info">
              {{ currentInstrumentName }}
            </el-tag>
          </div>
        </template>
        <el-input
          v-model="treeFilterText"
          placeholder="搜索指标/批号"
          clearable
          size="small"
          style="margin-bottom: 8px"
        />
        <div v-if="!searchForm.instrumentId" class="tree-empty">
          <el-empty description="请先选择仪器" :image-size="60" />
        </div>
        <el-tree
          v-else
          ref="treeRef"
          :data="treeData"
          :props="treeProps"
          node-key="key"
          :default-expand-all="true"
          :expand-on-click-node="true"
          :filter-node-method="filterNode"
          highlight-current
          @node-click="onTreeNodeClick"
        >
          <template #default="{ node, data: nodeData }">
            <span class="tree-node" :class="{ 'is-lot': nodeData.type === 'lot' }">
              <span v-if="nodeData.type === 'item'" class="node-item">
                <span class="node-icon">📊</span>
                <span class="node-label">{{ node.label }}</span>
              </span>
              <span v-else class="node-lot">
                <span class="node-icon">📄</span>
                <span class="node-label">{{ node.label }}</span>
                <el-tag size="small" :type="levelTagType(nodeData.level)" style="margin-left:4px">
                  {{ QC_LEVEL_MAP[nodeData.level] }}
                </el-tag>
              </span>
            </span>
          </template>
          <template #empty>
            <div style="padding:20px;color:#909399;text-align:center">
              暂无批号数据
            </div>
          </template>
        </el-tree>
        <div class="tree-debug" style="padding:8px;font-size:12px;color:#909399;border-top:1px solid var(--el-border-color-lighter);margin-top:8px;">
          <div>仪器ID: {{ searchForm.instrumentId ?? '未选择' }}</div>
          <div>批号数: {{ lotList.length }} | 匹配: {{ instrumentLots.length }} | 树节点: {{ treeData.length }}</div>
          <div>检验指标数: {{ testItemList.length }}</div>
        </div>
      </el-card>

      <!-- 右侧: 质控图 + 数据列表 -->
      <div class="right-content">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="panel-header">
              <span>{{ chartMode === 'lj' ? 'Levey-Jennings 质控图' : 'Z-分数图' }}</span>
              <span v-if="chartData" class="sub-info">
                <span v-if="currentInstrumentName">仪器: {{ currentInstrumentName }} |</span>
                <span v-if="currentTestItemName">指标: {{ currentTestItemName }} |</span>
                批号: {{ chartData.lot.lotNo }} |
                靶值: {{ chartData.lot.targetValue }} |
                实际均值: {{ chartData.mean }} |
                实际SD: {{ chartData.sd }} |
                失控: <b class="text-red">{{ outOfControlCount }}</b> / {{ chartData.points.length }}
              </span>
            </div>
          </template>
          <div v-loading="loading" class="chart-wrapper">
            <v-chart v-if="chartOption" ref="chartRef" class="chart" :option="chartOption" autoresize />
            <el-empty v-else-if="!loading" description="请选择仪器后, 点击左侧批号查看质控图" />
          </div>
        </el-card>

        <!-- 数据列表 -->
        <el-card v-if="chartData && chartData.points.length" shadow="never" class="table-card">
          <template #header>数据列表</template>
          <el-table :data="chartData.points" border stripe size="small" :row-class-name="rowClassName">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="测量时间" width="170" align="center">
              <template #default="{ row }">{{ formatDateTime(row.measureTime) }}</template>
            </el-table-column>
            <el-table-column prop="measureValue" label="测量值" width="120" align="center" />
            <el-table-column label="Z分数" width="120" align="center">
              <template #default="{ row }">
                <span v-if="row.zScore === null || row.zScore === undefined">-</span>
                <span v-else>{{ row.zScore.toFixed(4) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="判定" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="QC_RESULT_MAP[row.qcResult]?.type">
                  {{ QC_RESULT_MAP[row.qcResult]?.label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="违反规则" min-width="120">
              <template #default="{ row }">{{ row.ruleViolation || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, ScatterChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, MarkLineComponent } from 'echarts/components'
import {
  getAllQcLots,
  getQcChartData,
  recalculateQcRecords,
  QC_RESULT_MAP,
  QC_LEVEL_MAP,
  type QcLot,
  type QcChartData,
  type QcChartPoint
} from '@/api/qc'
import { getAllTestItems, type TestItem } from '@/api/base'
import { getAllInstruments, type Instrument } from '@/api/instrument'
import { formatDateTime } from '@/utils/format'

use([
  CanvasRenderer,
  LineChart,
  ScatterChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent
])

const loading = ref(false)
const recalculating = ref(false)
const chartRef = ref()
const lotList = ref<QcLot[]>([])
const testItemList = ref<TestItem[]>([])
const instrumentList = ref<Instrument[]>([])
const chartData = ref<QcChartData | null>(null)
const chartMode = ref<'lj' | 'zscore'>('lj')
const dateRange = ref<[string, string] | null>(null)
const treeRef = ref()
const treeFilterText = ref('')

interface TreeNode {
  key: string
  label: string
  type: 'item' | 'lot'
  itemId?: number
  lotId?: number
  level?: number
  children?: TreeNode[]
}

const treeProps = { label: 'label', children: 'children' }

const searchForm = reactive({
  lotId: undefined as number | undefined,
  testItemId: undefined as number | undefined,
  instrumentId: undefined as number | undefined
})

/** 当前选中仪器下的批号列表 */
const instrumentLots = computed(() => {
  if (!searchForm.instrumentId) return []
  const matched = lotList.value.filter(l => l.instrumentId === searchForm.instrumentId)
  // 如果按 instrumentId 没有匹配的批号，回退显示所有批号（兼容 instrumentId 不匹配的情况）
  if (matched.length === 0) {
    return lotList.value
  }
  return matched
})

/** 左侧树数据: 指标 -> 批号 */
const treeData = computed<TreeNode[]>(() => {
  if (!searchForm.instrumentId) return []
  // 按检验指标分组
  const itemMap = new Map<number, TreeNode>()
  for (const lot of instrumentLots.value) {
    if (!itemMap.has(lot.testItemId)) {
      const item = testItemList.value.find(t => t.id === lot.testItemId)
      itemMap.set(lot.testItemId, {
        key: `item-${lot.testItemId}`,
        label: item ? `${item.itemCode} ${item.itemName}` : `指标${lot.testItemId}`,
        type: 'item',
        itemId: lot.testItemId,
        children: []
      })
    }
    itemMap.get(lot.testItemId)!.children!.push({
      key: `lot-${lot.id}`,
      label: lot.lotNo,
      type: 'lot',
      lotId: lot.id,
      level: lot.level,
      itemId: lot.testItemId
    })
  }
  return Array.from(itemMap.values())
})

/** 当前选中的仪器名称 */
const currentInstrumentName = computed(() => {
  if (!searchForm.instrumentId) return ''
  return instrumentList.value.find(i => i.id === searchForm.instrumentId)?.instrumentName || ''
})

/** 当前选中的检验指标名称 */
const currentTestItemName = computed(() => {
  if (!searchForm.testItemId) return ''
  return testItemList.value.find(t => t.id === searchForm.testItemId)?.itemName || ''
})

/** 失控点数量 */
const outOfControlCount = computed(() => {
  if (!chartData.value) return 0
  return chartData.value.points.filter(p => p.qcResult === 0).length
})

/** 树节点过滤 */
function filterNode(value: string, data: TreeNode) {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

watch(treeFilterText, (val) => {
  treeRef.value?.filter(val)
})

/** 浓度等级标签类型 */
function levelTagType(level?: number): 'success' | 'warning' | 'danger' {
  if (level === 1) return 'success'
  if (level === 2) return 'warning'
  return 'danger'
}

/** 点击树节点 */
function onTreeNodeClick(data: TreeNode) {
  if (data.type === 'lot' && data.lotId) {
    searchForm.lotId = data.lotId
    searchForm.testItemId = data.itemId
    handleQuery()
  } else if (data.type === 'item' && data.children && data.children.length > 0) {
    // 点击指标节点时，自动加载第一个批号的图表
    const firstLot = data.children[0]
    if (firstLot.lotId) {
      searchForm.lotId = firstLot.lotId
      searchForm.testItemId = firstLot.itemId
      handleQuery()
    }
  }
}

/** 选择仪器时清空指标/批号和图表 */
function onInstrumentChange() {
  searchForm.testItemId = undefined
  searchForm.lotId = undefined
  chartData.value = null
  treeFilterText.value = ''
  console.log('[QC Chart] onInstrumentChange, instrumentId:', searchForm.instrumentId, 'lots:', lotList.value.length)
}

/** 切换图表模式 */
function handleModeChange() {
  // chartOption 是 computed, 切换模式自动重算
}

/**
 * 将值 clamp 到 Y 轴范围 [min, max] 内
 * 超出范围的点显示在边界位置
 */
function clampY(value: number, min: number, max: number): number {
  if (value > max) return max
  if (value < min) return min
  return value
}

/**
 * 构建空数据时的图表配置（显示控制线和提示文字）
 */
function buildEmptyChartOption(isZScore: boolean, mean: number, sd: number, yMin: number, yMax: number, data: any) {
  const markLineData: any[] = isZScore
    ? [
        { yAxis: 3, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '+3S', position: 'end', color: '#F56C6C' } },
        { yAxis: 2, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '+2S', position: 'end', color: '#E6A23C' } },
        { yAxis: 1, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '+1S', position: 'end', color: '#909399' } },
        { yAxis: 0, lineStyle: { color: '#67C23A', width: 2 }, label: { formatter: '0', position: 'end', color: '#67C23A' } },
        { yAxis: -1, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '-1S', position: 'end', color: '#909399' } },
        { yAxis: -2, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '-2S', position: 'end', color: '#E6A23C' } },
        { yAxis: -3, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '-3S', position: 'end', color: '#F56C6C' } }
      ]
    : [
        { yAxis: mean + 3 * sd, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '+3SD', position: 'end', color: '#F56C6C' } },
        { yAxis: mean + 2 * sd, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '+2SD', position: 'end', color: '#E6A23C' } },
        { yAxis: mean + sd, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '+1SD', position: 'end', color: '#909399' } },
        { yAxis: mean, lineStyle: { color: '#67C23A', width: 2 }, label: { formatter: 'Mean', position: 'end', color: '#67C23A' } },
        { yAxis: mean - sd, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '-1SD', position: 'end', color: '#909399' } },
        { yAxis: mean - 2 * sd, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '-2SD', position: 'end', color: '#E6A23C' } },
        { yAxis: mean - 3 * sd, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '-3SD', position: 'end', color: '#F56C6C' } }
      ]

  return {
    title: {
      text: `${isZScore ? 'Z-分数图' : 'Levey-Jennings 质控图'} - 暂无数据`,
      subtext: `批号: ${data.lot.lotNo} | 靶值: ${data.lot.targetValue} | SD: ${data.lot.sd}`,
      left: 'center',
      top: 5,
      textStyle: { color: '#909399', fontSize: 14 },
      subtextStyle: { color: '#C0C4CC', fontSize: 12 }
    },
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 50, top: 60, bottom: 60 },
    xAxis: {
      type: 'category',
      data: [],
      name: '时间',
      nameLocation: 'middle',
      nameGap: 30,
      axisLabel: { color: '#909399' },
      splitLine: { show: true, lineStyle: { color: '#EBEEF5' } }
    },
    yAxis: {
      type: 'value',
      name: isZScore ? 'Z-分数' : '测量值',
      min: yMin,
      max: yMax,
      interval: sd,
      axisLabel: { color: '#909399' },
      splitLine: { show: true, lineStyle: { color: '#EBEEF5' } }
    },
    series: [
      {
        name: isZScore ? 'Z-分数' : '测量值',
        type: 'line',
        data: [],
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color: '#1A73E8' },
        lineStyle: { color: '#1A73E8', width: 2 },
        markLine: {
          symbol: 'none',
          silent: true,
          data: markLineData
        },
        label: {
          show: true,
          position: 'top',
          formatter: '暂无质控数据',
          color: '#C0C4CC',
          fontSize: 16,
          fontWeight: 'bold'
        }
      }
    ]
  }
}

/**
 * 判断点是否超出显示范围 (用于标记)
 */
function isOutOfBound(value: number, min: number, max: number): boolean {
  return value > max || value < min
}

/**
 * 获取数据点的 Y 轴值
 * Z分数模式: 优先用 zScore, 为 null 时按 (测量值-靶值)/SD 实时计算
 * LJ模式: 直接用测量值
 */
function getPointY(p: QcChartPoint, isZScore: boolean): number {
  if (!isZScore) return p.measureValue
  if (p.zScore !== null && p.zScore !== undefined) return p.zScore
  // zScore 为 null 时实时计算 (兜底)
  if (chartData.value && chartData.value.sd > 0) {
    return Number(((p.measureValue - chartData.value.mean) / chartData.value.sd).toFixed(4))
  }
  return 0
}

const chartOption = computed(() => {
  if (!chartData.value) return null
  const data = chartData.value
  const isZScore = chartMode.value === 'zscore'

  // 如果 mean 或 sd 为空，使用批号的靶值和SD作为兜底
  const mean = isZScore ? 0 : (data.mean ?? data.lot?.targetValue ?? 0)
  const sd = isZScore ? 1 : (data.sd || data.lot?.sd || 1)

  // Y 轴范围: ±4S
  const yMin = mean - 4 * sd
  const yMax = mean + 4 * sd

  // 如果没有数据点，返回一个基本的图表框架
  if (!data.points || data.points.length === 0) {
    return buildEmptyChartOption(isZScore, mean, sd, yMin, yMax, data)
  }

  const xData = data.points.map(p => dayjs(p.measureTime).format('MM-DD HH:mm'))

  // 简化数据: 用简单数组格式而非对象
  const yValues: number[] = []
  const colors: string[] = []
  const sizes: number[] = []
  const rejectIndexes: number[] = []

  data.points.forEach((p, i) => {
    const rawY = getPointY(p, isZScore)
    const y = clampY(rawY, yMin, yMax)
    yValues.push(Number(y.toFixed(4)))

    const isReject = p.qcResult === 0
    const outOfBound = isOutOfBound(rawY, yMin, yMax)
    colors.push(isReject ? '#F56C6C' : (outOfBound ? '#E6A23C' : '#1A73E8'))
    sizes.push(isReject ? 12 : (outOfBound ? 10 : 7))

    if (isReject) rejectIndexes.push(i)
  })

  // 控制线
  const markLineData: any[] = isZScore
    ? [
        { yAxis: 3, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '+3S', position: 'end', color: '#F56C6C' } },
        { yAxis: 2, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '+2S', position: 'end', color: '#E6A23C' } },
        { yAxis: 1, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '+1S', position: 'end', color: '#909399' } },
        { yAxis: 0, lineStyle: { color: '#67C23A', width: 2 }, label: { formatter: '0', position: 'end', color: '#67C23A' } },
        { yAxis: -1, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '-1S', position: 'end', color: '#909399' } },
        { yAxis: -2, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '-2S', position: 'end', color: '#E6A23C' } },
        { yAxis: -3, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '-3S', position: 'end', color: '#F56C6C' } }
      ]
    : [
        { yAxis: mean + 3 * sd, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '+3SD', position: 'end', color: '#F56C6C' } },
        { yAxis: mean + 2 * sd, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '+2SD', position: 'end', color: '#E6A23C' } },
        { yAxis: mean + sd, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '+1SD', position: 'end', color: '#909399' } },
        { yAxis: mean, lineStyle: { color: '#67C23A', width: 2 }, label: { formatter: 'Mean', position: 'end', color: '#67C23A' } },
        { yAxis: mean - sd, lineStyle: { color: '#C0C4CC', type: 'dotted' }, label: { formatter: '-1SD', position: 'end', color: '#909399' } },
        { yAxis: mean - 2 * sd, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '-2SD', position: 'end', color: '#E6A23C' } },
        { yAxis: mean - 3 * sd, lineStyle: { color: '#F56C6C', type: 'dashed', width: 2 }, label: { formatter: '-3SD', position: 'end', color: '#F56C6C' } }
      ]

  // 为每个点创建独立的 markPoint 数据用于颜色区分
  const markPointData = data.points
    .map((p, i) => {
      const rawY = getPointY(p, isZScore)
      const y = clampY(rawY, yMin, yMax)
      const isReject = p.qcResult === 0
      const outOfBound = isOutOfBound(rawY, yMin, yMax)
      return {
        coord: [i, Number(y.toFixed(4))],
        itemStyle: {
          color: isReject ? '#F56C6C' : (outOfBound ? '#E6A23C' : '#1A73E8')
        },
        symbolSize: isReject ? 12 : (outOfBound ? 10 : 7)
      }
    })

  // 失控点散点
  const rejectPoints = rejectIndexes.map(idx => {
    const p = data.points[idx]
    const rawY = getPointY(p, isZScore)
    const y = clampY(rawY, yMin, yMax)
    return {
      value: [idx, Number(y.toFixed(4))],
      itemStyle: { color: '#F56C6C', borderColor: '#fff', borderWidth: 2, shadowBlur: 6, shadowColor: 'rgba(245,108,108,0.6)' }
    }
  })

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const idx = params[0].dataIndex
        const p = data.points[idx]
        if (!p) return ''
        const rawVal = getPointY(p, isZScore)
        const zScoreVal = p.zScore !== null && p.zScore !== undefined
          ? Number(p.zScore).toFixed(4)
          : '-'
        const status = QC_RESULT_MAP[p.qcResult]?.label || '-'
        const rule = p.ruleViolation ? '<br/>违反: ' + p.ruleViolation : ''
        return `<b>${formatDateTime(p.measureTime)}</b><br/>测量值: ${p.measureValue}<br/>Z分数: ${zScoreVal}<br/>判定: <b style="color:${p.qcResult === 0 ? '#F56C6C' : '#67C23A'}">${status}</b>${rule}`
      }
    },
    legend: {
      data: [isZScore ? 'Z-分数' : '测量值', '失控点'],
      top: 0
    },
    grid: {
      left: 60,
      right: 50,
      top: 40,
      bottom: 60
    },
    xAxis: {
      type: 'category',
      data: xData,
      axisLabel: { rotate: 30, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      name: isZScore ? 'Z-Score' : '测量值',
      min: yMin,
      max: yMax,
      axisLabel: {
        formatter: (val: number) => isZScore ? val.toFixed(1) : val.toFixed(2)
      }
    },
    series: [
      {
        name: isZScore ? 'Z-分数' : '测量值',
        type: 'line',
        data: yValues,
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color: '#1A73E8' },
        lineStyle: { color: '#1A73E8', width: 2 },
        markLine: {
          symbol: 'none',
          silent: true,
          data: markLineData
        },
        markPoint: {
          symbol: 'circle',
          data: markPointData
        }
      },
      {
        name: '失控点',
        type: 'scatter',
        data: rejectPoints,
        symbol: 'circle',
        symbolSize: 16,
        itemStyle: { color: '#F56C6C', borderColor: '#fff', borderWidth: 2, shadowBlur: 6, shadowColor: 'rgba(245,108,108,0.6)' },
        z: 10
      }
    ]
  }
})

async function loadLots() {
  try {
    const data = await getAllQcLots()
    lotList.value = data || []
    console.log('[QC Chart] loadLots success, count:', lotList.value.length)
  } catch (e) {
    console.error('[QC Chart] loadLots failed:', e)
    lotList.value = []
  }
}

async function loadTestItems() {
  try {
    const items = await getAllTestItems()
    testItemList.value = (items || []).filter(t => t.isPanel !== 1)
    console.log('[QC Chart] loadTestItems success, count:', testItemList.value.length)
  } catch (e) {
    console.error('[QC Chart] loadTestItems failed:', e)
    testItemList.value = []
  }
}

async function loadInstruments() {
  try {
    instrumentList.value = await getAllInstruments()
    console.log('[QC Chart] loadInstruments success, count:', instrumentList.value.length)
  } catch (e) {
    console.error('[QC Chart] loadInstruments failed:', e)
    instrumentList.value = []
  }
}

async function handleQuery() {
  if (!searchForm.lotId) {
    ElMessage.warning('请先在左侧树形结构中选择一个质控品批号')
    return
  }
  loading.value = true
  try {
    chartData.value = await getQcChartData({
      lotId: searchForm.lotId,
      beginDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    })
    if (chartData.value && chartData.value.points.length === 0) {
      ElMessage.info('该批号在所选时间范围内没有质控数据')
    }
  } catch (e) {
    chartData.value = null
    ElMessage.error('获取质控图数据失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.lotId = undefined
  searchForm.testItemId = undefined
  searchForm.instrumentId = undefined
  dateRange.value = null
  chartData.value = null
  treeFilterText.value = ''
}

/** 计算失控: 对当前批号+时间范围内的记录批量重跑 Westgard 规则, 失控则更新 */
async function handleRecalculate() {
  if (!searchForm.lotId) {
    ElMessage.warning('请先选择质控品批号')
    return
  }
  const rangeText = dateRange.value
    ? `${dateRange.value[0]} 至 ${dateRange.value[1]}`
    : '全部时间'
  try {
    await ElMessageBox.confirm(
      `将对批号 [${chartData.value?.lot?.lotNo || searchForm.lotId}] 在 ${rangeText} 范围内的质控记录重新计算失控判定，是否继续？`,
      '计算失控确认',
      { type: 'warning', confirmButtonText: '计算', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  recalculating.value = true
  try {
    const res = await recalculateQcRecords({
      lotId: searchForm.lotId,
      beginDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    })
    ElMessage.success(`计算完成：共 ${res.total} 条，失控 ${res.rejectCount} 条，更新 ${res.updatedCount} 条`)
    // 重新加载图表刷新展示
    await handleQuery()
  } catch (e) {
    ElMessage.error('计算失控失败')
  } finally {
    recalculating.value = false
  }
}

function rowClassName({ row }: { row: any }) {
  if (row.qcResult === 0) return 'out-row'
  return ''
}

onMounted(() => {
  loadLots()
  loadTestItems()
  loadInstruments()
})

// 当图表数据或模式变化时，强制刷新图表
watch([chartData, chartMode], () => {
  nextTick(() => {
    if (chartRef.value && chartRef.value.chart) {
      chartRef.value.chart.resize()
    }
  })
})
</script>

<style scoped lang="scss">
.qc-chart-page {
  padding: 16px;
  height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;

  .search-card {
    margin-bottom: 12px;
    flex-shrink: 0;
  }

  .main-body {
    flex: 1;
    display: flex;
    gap: 12px;
    overflow: hidden;
    min-height: 0;
  }

  .left-tree-card {
    width: 280px;
    flex-shrink: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    :deep(.el-card__body) {
      flex: 1;
      overflow: auto;
      padding: 12px;
    }

    .tree-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 600;
    }

    .tree-empty {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 200px;
    }

    .tree-node {
      display: flex;
      align-items: center;
      flex: 1;

      .node-item,
      .node-lot {
        display: flex;
        align-items: center;
        gap: 6px;
        flex: 1;
        overflow: hidden;

        .el-icon {
          color: var(--el-color-primary);
        }

        .node-label {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      &.is-lot {
        .el-icon {
          color: var(--el-color-success);
        }
      }
    }

    :deep(.el-tree-node__content) {
      height: 32px;
    }

    :deep(.el-tree-node.is-current > .el-tree-node__content) {
      background-color: var(--el-color-primary-light-9);
    }
  }

  .right-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 12px;
    overflow: hidden;
    min-width: 0;
  }

  .chart-card {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    :deep(.el-card__body) {
      flex: 1;
      overflow: hidden;
      padding: 12px;
    }

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;

      .sub-info {
        font-size: 13px;
        color: var(--el-text-color-secondary);
        font-weight: normal;
      }
    }

    .chart-wrapper {
      flex: 1;
      min-height: 400px;
      display: flex;
      flex-direction: column;

      .chart {
        flex: 1;
        width: 100%;
        min-height: 400px;
      }
    }
  }

  .table-card {
    flex-shrink: 0;
    max-height: 240px;
    overflow: auto;

    :deep(.out-row) {
      background-color: var(--el-color-danger-light-9) !important;
    }
  }
}
</style>
