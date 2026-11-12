<template>
  <div class="report-page">
    <el-card shadow="never" class="tab-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 采购统计 -->
        <el-tab-pane label="采购统计" name="purchase">
          <div class="pane-toolbar">
            <el-form inline @submit.prevent>
              <el-form-item label="统计维度">
                <el-select v-model="purchaseForm.groupBy" style="width: 140px" @change="fetchPurchaseStat">
                  <el-option label="按供应商" value="supplier" />
                  <el-option label="按科室" value="dept" />
                  <el-option label="按分类" value="category" />
                </el-select>
              </el-form-item>
              <el-form-item label="日期区间">
                <el-date-picker
                  v-model="purchaseForm.range"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  style="width: 240px"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" plain @click="fetchPurchaseStat">查询</el-button>
                <el-button type="success" plain @click="handleExportPurchase">导出 Excel</el-button>
              </el-form-item>
            </el-form>
          </div>
          <el-table v-loading="purchase.loading" :data="purchase.rows" border stripe style="width: 100%">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="维度值" min-width="180">
              <template #default="{ row }">{{ purchase.dimShow(row) }}</template>
            </el-table-column>
            <el-table-column label="订单数" align="center">
              <template #default="{ row }">{{ row.order_count ?? row.orderCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="总金额" align="center">
              <template #default="{ row }">{{ fmtMoney(row.total_amount ?? row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="已到货订单数" align="center">
              <template #default="{ row }">{{ row.arrived_order_count ?? row.arrivedOrderCount ?? 0 }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 领用趋势 -->
        <el-tab-pane label="领用趋势" name="trend">
          <div class="pane-toolbar">
            <el-form inline @submit.prevent>
              <el-form-item label="粒度">
                <el-radio-group v-model="trendForm.granularity" @change="fetchTrend">
                  <el-radio-button value="day">按日</el-radio-button>
                  <el-radio-button value="week">按周</el-radio-button>
                  <el-radio-button value="month">按月</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="日期区间">
                <el-date-picker
                  v-model="trendForm.range"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  style="width: 240px"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" plain @click="fetchTrend">查询</el-button>
                <el-button type="success" plain :disabled="!trend.option" @click="saveTrendImage">保存图片</el-button>
              </el-form-item>
            </el-form>
          </div>
          <div v-loading="trend.loading" class="chart-box">
            <VChart v-if="trend.option" ref="trendChartRef" :option="trend.option" autoresize class="chart" />
            <el-empty v-else description="暂无趋势数据" />
          </div>
        </el-tab-pane>

        <!-- 库存结构 -->
        <el-tab-pane label="库存结构分析" name="structure">
          <div class="pane-toolbar">
            <el-button type="primary" plain @click="fetchStructure">查询</el-button>
          </div>
          <el-table v-loading="structure.loading" :data="structure.rows" border stripe style="width: 100%">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="分类" min-width="160">
              <template #default="{ row }">{{ row.category_name ?? row.categoryName ?? '未分类' }}</template>
            </el-table-column>
            <el-table-column label="试剂种数" align="center">
              <template #default="{ row }">{{ row.reagent_count ?? row.reagentCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="库存总数量" align="center">
              <template #default="{ row }">{{ row.total_qty ?? row.totalQty ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="库存总金额" align="center">
              <template #default="{ row }">{{ fmtMoney(row.total_amount ?? row.totalAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 台账 / 应付 / 各类汇总 -->
        <el-tab-pane v-for="tab in statTabs" :key="tab.name" :label="tab.label" :name="tab.name">
          <div class="pane-toolbar">
            <el-form inline @submit.prevent>
              <el-form-item label="日期区间">
                <el-date-picker
                  v-model="statForms[tab.name].range"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  style="width: 240px"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" plain @click="loadStat(tab.name)">查询</el-button>
                <el-button type="success" plain @click="handleExportStat(tab)">导出CSV</el-button>
                <el-button plain @click="handlePrintStat(tab)">打印</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 台账四类汇总 -->
          <el-descriptions v-if="tab.name === 'ledger'" :column="4" border class="summary-cards">
            <el-descriptions-item label="正常入库">
              {{ statSummary.normalInQty ?? 0 }} / ¥{{ fmtMoney(statSummary.normalInAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="偿还入库">
              {{ statSummary.repayInQty ?? 0 }} / ¥{{ fmtMoney(statSummary.repayInAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="正常出库">
              {{ statSummary.normalOutQty ?? 0 }} / ¥{{ fmtMoney(statSummary.normalOutAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="借用出库">
              {{ statSummary.borrowOutQty ?? 0 }} / ¥{{ fmtMoney(statSummary.borrowOutAmount) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-table
            v-loading="statLoading[tab.name]"
            :data="statRows[tab.name] || []"
            border
            stripe
            max-height="560"
            style="width: 100%"
          >
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column
              v-for="col in tab.columns"
              :key="col.prop"
              :prop="col.prop"
              :label="col.label"
              :min-width="col.minWidth || 120"
              :align="col.align || 'left'"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ formatStatCell(row, col) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import {
  getReagentPurchaseStat,
  getReagentTrend,
  getReagentStockStructure,
  exportReagentPurchaseStat,
  getStatLedger,
  getStatLedgerSummary,
  getStatSupplierPayable,
  getStatDeptIssue,
  getStatInvoiceSummary,
  getStatInboundByDept,
  getStatInboundBySupplier,
  getStatOutboundByDept,
  getStatConsumptionStat,
  exportStatCsv,
  type StatQuery
} from '@/api/reagent'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const activeTab = ref('purchase')

const purchaseForm = reactive({ groupBy: 'supplier', range: [] as string[] })
const purchase = reactive({
  loading: false,
  rows: [] as Array<Record<string, any>>,
  dimShow: (r: Record<string, any>) => String(r.supplier_name ?? r.dept_name ?? r.category_name ?? '-')
})

const trendForm = reactive({ granularity: 'day', range: [] as string[] })
const trend = reactive({ loading: false, option: null as any })
const trendChartRef = ref<any>()

const structure = reactive({ loading: false, rows: [] as Array<Record<string, any>> })

// ===== 台账 / 汇总页签 =====
interface StatColumn {
  prop: string
  label: string
  minWidth?: number
  align?: 'left' | 'center' | 'right'
  type?: 'text' | 'money' | 'number' | 'datetime'
}

interface StatTab {
  name: string
  label: string
  columns: StatColumn[]
}

const statTabs: StatTab[] = [
  {
    name: 'ledger',
    label: '台账',
    columns: [
      { prop: 'createTime', label: '时间', minWidth: 165, type: 'datetime' },
      { prop: 'reagentCode', label: '试剂编码', minWidth: 120 },
      { prop: 'reagentName', label: '试剂名称', minWidth: 160 },
      { prop: 'recordTypeName', label: '类型', minWidth: 110 },
      { prop: 'quantity', label: '数量', minWidth: 90, align: 'right', type: 'number' },
      { prop: 'unitPrice', label: '单价', minWidth: 100, align: 'right', type: 'money' },
      { prop: 'amount', label: '金额', minWidth: 110, align: 'right', type: 'money' },
      { prop: 'deptName', label: '科室', minWidth: 130 },
      { prop: 'operatorName', label: '操作人', minWidth: 100 },
      { prop: 'sourceNo', label: '来源单号', minWidth: 150 },
      { prop: 'remark', label: '备注', minWidth: 140 }
    ]
  },
  {
    name: 'supplierPayable',
    label: '供应商应付',
    columns: [
      { prop: 'supplierName', label: '供应商', minWidth: 200 },
      { prop: 'inboundCount', label: '入库单数', minWidth: 110, align: 'center', type: 'number' },
      { prop: 'totalAmount', label: '入库金额', minWidth: 130, align: 'right', type: 'money' }
    ]
  },
  {
    name: 'deptIssue',
    label: '领用小组汇总',
    columns: [
      { prop: 'deptName', label: '科室/小组', minWidth: 180 },
      { prop: 'totalQty', label: '出库数量', minWidth: 110, align: 'right', type: 'number' },
      { prop: 'totalAmount', label: '出库金额', minWidth: 130, align: 'right', type: 'money' },
      { prop: 'recordCount', label: '流水条数', minWidth: 110, align: 'center', type: 'number' }
    ]
  },
  {
    name: 'invoiceSummary',
    label: '发票汇总',
    columns: [
      { prop: 'supplierName', label: '供应商', minWidth: 200 },
      { prop: 'invoiceCount', label: '发票张数', minWidth: 110, align: 'center', type: 'number' },
      { prop: 'totalAmount', label: '价税合计', minWidth: 130, align: 'right', type: 'money' },
      { prop: 'totalTaxAmount', label: '税额', minWidth: 120, align: 'right', type: 'money' }
    ]
  },
  {
    name: 'inboundByDept',
    label: '科室入库汇总',
    columns: [
      { prop: 'deptName', label: '科室', minWidth: 180 },
      { prop: 'inboundCount', label: '入库单数', minWidth: 110, align: 'center', type: 'number' },
      { prop: 'totalQty', label: '入库数量', minWidth: 110, align: 'right', type: 'number' },
      { prop: 'totalAmount', label: '入库金额', minWidth: 130, align: 'right', type: 'money' }
    ]
  },
  {
    name: 'inboundBySupplier',
    label: '入库汇总(按供应商)',
    columns: [
      { prop: 'supplierName', label: '供应商', minWidth: 200 },
      { prop: 'inboundCount', label: '入库单数', minWidth: 110, align: 'center', type: 'number' },
      { prop: 'totalQty', label: '入库数量', minWidth: 110, align: 'right', type: 'number' },
      { prop: 'totalAmount', label: '入库金额', minWidth: 130, align: 'right', type: 'money' }
    ]
  },
  {
    name: 'outboundByDept',
    label: '出库汇总(按小组)',
    columns: [
      { prop: 'deptName', label: '科室/小组', minWidth: 180 },
      { prop: 'requisitionCount', label: '出库单数', minWidth: 110, align: 'center', type: 'number' },
      { prop: 'totalQty', label: '出库数量', minWidth: 110, align: 'right', type: 'number' },
      { prop: 'totalAmount', label: '出库金额', minWidth: 130, align: 'right', type: 'money' }
    ]
  },
  {
    name: 'consumptionStat',
    label: '实际消耗量',
    columns: [
      { prop: 'instrumentName', label: '仪器', minWidth: 200 },
      { prop: 'totalQty', label: '消耗数量', minWidth: 110, align: 'right', type: 'number' },
      { prop: 'totalTestCount', label: '测试项目数', minWidth: 120, align: 'right', type: 'number' },
      { prop: 'recordCount', label: '记录条数', minWidth: 110, align: 'center', type: 'number' }
    ]
  }
]

const statForms = reactive<Record<string, { range: string[] }>>({})
const statRows = ref<Record<string, any[]>>({})
const statLoading = ref<Record<string, boolean>>({})
const statSummary = ref<Record<string, any>>({})

statTabs.forEach((tab) => {
  statForms[tab.name] = { range: [] }
  statRows.value[tab.name] = []
  statLoading.value[tab.name] = false
})

const statLoaders: Record<string, (params: StatQuery) => Promise<any>> = {
  ledger: getStatLedger,
  supplierPayable: getStatSupplierPayable,
  deptIssue: getStatDeptIssue,
  invoiceSummary: getStatInvoiceSummary,
  inboundByDept: getStatInboundByDept,
  inboundBySupplier: getStatInboundBySupplier,
  outboundByDept: getStatOutboundByDept,
  consumptionStat: getStatConsumptionStat
}

const backendExportTypes: Record<string, string> = {
  ledger: 'ledger',
  supplierPayable: 'supplier-payable',
  deptIssue: 'dept-issue',
  invoiceSummary: 'invoice-summary',
  inboundBySupplier: 'inbound-by-supplier',
  outboundByDept: 'outbound-by-dept'
}

function fmtMoney(v: any): string {
  if (v === undefined || v === null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function buildStatParams(tabName: string): StatQuery {
  const range = statForms[tabName]?.range || []
  const params: StatQuery = {}
  if (range.length === 2) {
    params.startDate = range[0]
    params.endDate = range[1]
  }
  return params
}

function formatStatCell(row: Record<string, any>, col: StatColumn): string {
  const value = row[col.prop]
  if (col.type === 'money') return fmtMoney(value)
  if (col.type === 'number') return value === undefined || value === null ? '0' : String(value)
  if (col.type === 'datetime') return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

async function loadStat(tabName: string) {
  const loader = statLoaders[tabName]
  if (!loader) return
  statLoading.value[tabName] = true
  try {
    const params = buildStatParams(tabName)
    const rows = await loader(params)
    statRows.value[tabName] = rows || []
    // 台账页签同步刷新四类汇总
    if (tabName === 'ledger') {
      statSummary.value = (await getStatLedgerSummary(params)) || {}
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载统计数据失败')
    statRows.value[tabName] = []
  } finally {
    statLoading.value[tabName] = false
  }
}

function handleTabChange(name: string | number) {
  const tabName = String(name)
  if (statLoaders[tabName] && !(statRows.value[tabName] || []).length) {
    loadStat(tabName)
  }
}

/** 客户端 CSV 导出（后端未支持的页签使用） */
function exportStatByClient(tab: StatTab) {
  const rows = statRows.value[tab.name] || []
  const headers = tab.columns.map((c) => c.label)
  const escape = (val: any) => `"${String(val ?? '').replace(/"/g, '""')}"`
  const lines = [headers.map(escape).join(',')]
  rows.forEach((row) => {
    lines.push(tab.columns.map((col) => escape(formatStatCell(row, col))).join(','))
  })
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  downloadBlob(blob, `reagent-stat-${tab.name}.csv`)
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

async function handleExportStat(tab: StatTab) {
  try {
    if (!(statRows.value[tab.name] || []).length) {
      await loadStat(tab.name)
    }
    const backendType = backendExportTypes[tab.name]
    if (backendType) {
      await exportStatCsv(backendType, buildStatParams(tab.name))
    } else {
      exportStatByClient(tab)
    }
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

/** 打印当前页签表格 */
function handlePrintStat(tab: StatTab) {
  const rows = statRows.value[tab.name] || []
  if (!rows.length) {
    ElMessage.warning('暂无可打印数据')
    return
  }
  const range = statForms[tab.name]?.range || []
  const rangeText = range.length === 2 ? `${range[0]} 至 ${range[1]}` : '全部日期'
  const head = `<tr>${tab.columns.map((c) => `<th>${c.label}</th>`).join('')}</tr>`
  const body = rows
    .map((row) => `<tr>${tab.columns.map((c) => `<td>${formatStatCell(row, c)}</td>`).join('')}</tr>`)
    .join('')
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${tab.label}</title>
    <style>
      body { font-family: "Microsoft YaHei", Arial, sans-serif; padding: 20px; color: #000; }
      h2 { text-align: center; }
      .meta { text-align: center; font-size: 13px; margin-bottom: 12px; }
      table { width: 100%; border-collapse: collapse; }
      th, td { border: 1px solid #333; padding: 5px 6px; font-size: 12px; }
      th { background: #f2f2f2; }
    </style></head><body>
    <h2>${tab.label}</h2>
    <div class="meta">日期区间：${rangeText}　打印时间：${dayjs().format('YYYY-MM-DD HH:mm:ss')}</div>
    <table><thead>${head}</thead><tbody>${body}</tbody></table>
    </body></html>`
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.error('无法打开打印窗口，请检查浏览器弹窗拦截设置')
    return
  }
  win.document.write(html)
  win.document.close()
  win.focus()
  win.print()
}

/** 保存领用趋势图片（ECharts getDataURL → PNG） */
function saveTrendImage() {
  const instance = trendChartRef.value?.getEchartsInstance?.()
  const chart = instance || trendChartRef.value
  if (!chart?.getDataURL) {
    ElMessage.error('图表尚未渲染完成')
    return
  }
  const dataUrl = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' })
  const link = document.createElement('a')
  link.href = dataUrl
  link.download = `领用趋势-${dayjs().format('YYYYMMDDHHmmss')}.png`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

async function fetchPurchaseStat() {
  purchase.loading = true
  try {
    const params: Record<string, any> = { groupBy: purchaseForm.groupBy }
    if (purchaseForm.range && purchaseForm.range.length === 2) {
      params.start = purchaseForm.range[0]
      params.end = purchaseForm.range[1]
    }
    purchase.rows = await getReagentPurchaseStat(params)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载采购统计失败')
  } finally {
    purchase.loading = false
  }
}

async function handleExportPurchase() {
  try {
    const params: Record<string, any> = { groupBy: purchaseForm.groupBy }
    if (purchaseForm.range && purchaseForm.range.length === 2) {
      params.start = purchaseForm.range[0]
      params.end = purchaseForm.range[1]
    }
    await exportReagentPurchaseStat(params)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

async function fetchTrend() {
  trend.loading = true
  try {
    const params: Record<string, any> = { granularity: trendForm.granularity }
    if (trendForm.range && trendForm.range.length === 2) {
      params.start = trendForm.range[0]
      params.end = trendForm.range[1]
    }
    const rows = await getReagentTrend(params)
    const periods = rows.map((r) => r.period ?? '')
    const values = rows.map((r) => Number(r.totalQty ?? 0))
    trend.option = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['领用量'] },
      grid: { left: 40, right: 20, bottom: 40, top: 40 },
      xAxis: { type: 'category', data: periods, axisLabel: { rotate: periods.length > 12 ? 45 : 0 } },
      yAxis: { type: 'value', name: '领用量' },
      series: [
        {
          name: '领用量',
          type: 'line',
          smooth: true,
          areaStyle: { opacity: 0.15 },
          data: values
        }
      ]
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载领用趋势失败')
  } finally {
    trend.loading = false
  }
}

async function fetchStructure() {
  structure.loading = true
  try {
    structure.rows = await getReagentStockStructure()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载库存结构失败')
  } finally {
    structure.loading = false
  }
}

onMounted(() => {
  fetchPurchaseStat()
  fetchTrend()
  fetchStructure()
})
</script>

<style scoped lang="scss">
.tab-card .pane-toolbar {
  margin-bottom: 16px;
}

.summary-cards {
  margin-bottom: 16px;
}

.chart-box {
  height: 420px;

  .chart {
    width: 100%;
    height: 100%;
  }
}
</style>
