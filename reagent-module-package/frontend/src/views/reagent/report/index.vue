<template>
  <div class="report-page">
    <el-card shadow="never" class="tab-card">
      <el-tabs v-model="activeTab">
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
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import {
  getReagentPurchaseStat,
  getReagentTrend,
  getReagentStockStructure,
  exportReagentPurchaseStat
} from '@/api/reagent'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const activeTab = ref('purchase')

const purchaseForm = reactive({ groupBy: 'supplier', range: [] as string[] })
const purchase = reactive({ loading: false, rows: [] as Array<Record<string, any>>, dimShow: (r: Record<string, any>) => String(r.supplier_name ?? r.dept_name ?? r.category_name ?? '-') })

const trendForm = reactive({ granularity: 'day', range: [] as string[] })
const trend = reactive({ loading: false, option: null as any })

const structure = reactive({ loading: false, rows: [] as Array<Record<string, any>> })

function fmtMoney(v: any): string {
  if (v === undefined || v === null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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

.chart-box {
  height: 420px;

  .chart {
    width: 100%;
    height: 100%;
  }
}
</style>