<template>
  <div class="iqc-page">
    <!-- 运行概览条 KPI ×4 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :span="6"><div class="kpi-card mono"><div class="kpi-label">监控项目</div><div class="kpi-value">{{ summary.total ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="kpi-card ok"><div class="kpi-label">在控</div><div class="kpi-value">{{ summary.inControl ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="kpi-card warn"><div class="kpi-label">警告</div><div class="kpi-value">{{ summary.warning ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="kpi-card danger"><div class="kpi-label">失控</div><div class="kpi-value">{{ summary.outOfControl ?? '-' }}</div></div></el-col>
    </el-row>

    <!-- 顶部筛选 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="仪器">
          <el-select
            v-model="filters.instrumentId"
            placeholder="全部仪器"
            clearable
            style="width:180px"
            @change="onInstrumentChange"
          >
            <el-option
              v-for="ins in instruments"
              :key="ins.id"
              :label="ins.instrumentName + (ins.model ? ' (' + ins.model + ')' : '')"
              :value="ins.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:110px" @change="loadProjects">
            <el-option label="在控" value="在控" /><el-option label="警告" value="警告" /><el-option label="失控" value="失控" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:240px"
          />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.keyword" placeholder="项目/仪器 关键字" clearable style="width:220px" @keyup.enter="loadProjects" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadProjects">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-button type="warning" plain :disabled="!selectedCode" :loading="recalcing" @click="doRecalculate">
            计算失控
          </el-button>
        </el-form-item>
        <el-form-item style="float:right">
          <el-button type="success" :disabled="!selectedCode" @click="entryDialogVisible = true">
            录入测量值
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 主体 左右可拖动分栏 -->
    <div
      class="main-grid"
      :style="leftWidthPx ? { gridTemplateColumns: leftWidthPx + 'px 6px 1fr' } : {}"
    >
      <!-- 左侧: 质控项目列表(全宽表格) -->
      <el-card shadow="never" class="project-card">
        <template #header>
          <div class="panel-header"><span>质控项目</span><el-tag size="small" type="info">共 {{ projects.length }} 项</el-tag></div>
        </template>
        <el-table
          v-loading="listLoading"
          :data="projects"
          border
          stripe
          size="small"
          highlight-current-row
          :row-class-name="rowClass"
          @current-change="onSelectProject"
          style="width:100%"
        >
          <el-table-column label="项目" min-width="160">
            <template #default="{ row }">{{ row.itemCode }} {{ row.itemName }}</template>
          </el-table-column>
          <el-table-column prop="method" label="方法学" min-width="130" show-overflow-tooltip />
          <el-table-column prop="instrumentName" label="仪器" min-width="140" show-overflow-tooltip />
          <el-table-column label="水平" width="80" align="center">
            <template #default="{ row }">{{ levelText(row.level) }}</template>
          </el-table-column>
          <el-table-column label="靶值" width="80" align="right">
            <template #default="{ row }">{{ fmt(row.target) }}</template>
          </el-table-column>
          <el-table-column label="最近值" width="90" align="right">
            <template #default="{ row }">{{ fmt(row.lastValue) }}<span v-if="row.lastValue!=null" class="unit">{{ row.unit }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无质控项目" :image-size="50" /></template>
        </el-table>
      </el-card>

      <!-- 可拖动分隔条 -->
      <div
        ref="resizerRef"
        class="col-resizer"
        :title="'拖动调整左右宽度'"
        @mousedown="onResizeMouseDown"
      />

      <!-- 右侧: 联动详情 -->
      <div class="right-col">
        <!-- 质控可视化 + 数据明细 Tab 切换卡 (分页显示 3 种视图, 避免垂直堆叠过长) -->
        <el-card shadow="never" class="card viz-card">
          <template #header>
            <div class="panel-header">
              <el-tabs v-model="vizTab" class="viz-tabs" @tab-change="onVizTabChange">
                <el-tab-pane label="L-J 质控图" name="lj" />
                <el-tab-pane label="Z 分数图" name="z" />
                <el-tab-pane label="质控数据明细" name="detail" />
              </el-tabs>
              <span v-if="detail && detail.lot" class="sub">{{ dtTitle }}</span>
            </div>
          </template>
          <!-- Tab 1: Levey-Jennings 质控图 -->
          <div v-show="vizTab === 'lj'" v-loading="chartLoading" class="chart-box">
            <v-chart v-if="chartOption" ref="chartRef" :option="chartOption" autoresize class="chart" />
            <el-empty v-else-if="!chartLoading" description="请选择左侧质控项目" :image-size="56" />
          </div>
          <!-- Tab 2: Z 分数图 -->
          <div v-show="vizTab === 'z'" v-loading="chartLoading" class="chart-box">
            <v-chart v-if="zOption" ref="zChartRef" :option="zOption" autoresize class="chart" />
            <el-empty v-else-if="!chartLoading" description="请选择左侧质控项目" :image-size="56" />
          </div>
          <!-- Tab 3: 质控数据明细 (分页表格, 代替原独立 records-card 堆叠) -->
          <div v-show="vizTab === 'detail'" class="records-pane">
            <div class="panel-header records-header">
              <span class="sub">共 {{ recTotal }} 条质控记录</span>
            </div>
            <el-table
              v-loading="recLoading"
              :data="recList"
              border
              stripe
              size="small"
              style="width:100%"
              max-height="380"
            >
              <el-table-column label="序号" type="index" width="60" align="center" />
              <el-table-column label="测量时间" width="150" align="center" prop="measureTime" show-overflow-tooltip />
              <el-table-column label="测量值" width="100" align="right">
                <template #default="{ row }">{{ Number(row.measureValue).toFixed(3) }}<span v-if="sel?.unit" class="unit">{{ sel.unit }}</span></template>
              </el-table-column>
              <el-table-column label="Z分数" width="100" align="right">
                <template #default="{ row }">{{ row.zScore != null ? Number(row.zScore).toFixed(3) : '-' }}</template>
              </el-table-column>
              <el-table-column label="靶值偏差" width="120" align="right">
                <template #default="{ row }">
                  <span v-if="sel?.target != null && detail">
                    {{ (Number(row.measureValue) - Number(detail.lot?.targetValue ?? detail.target ?? 0)).toFixed(3) }}
                  </span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="判定" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.qcResult === 0 ? 'danger' : 'success'" size="small">
                    {{ row.qcResult === 0 ? '失控' : '在控' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="触发规则" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.ruleViolation">{{ row.ruleViolation }}</span>
                  <span v-else class="unit">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作人" width="90" align="center" prop="operatorName" show-overflow-tooltip />
              <el-table-column label="创建时间" width="150" align="center" prop="createTime" show-overflow-tooltip />
              <template #empty>
                <el-empty v-if="!selectedCode" description="请先选择左侧质控项目" :image-size="50" />
                <el-empty v-else description="该项目暂无质控记录" :image-size="50" />
              </template>
            </el-table>
            <div v-if="recTotal > 0" class="pager-row">
              <el-pagination
                background
                size="small"
                layout="total, sizes, prev, pager, next, jumper"
                :current-page="recPage"
                :page-sizes="[10, 20, 50, 100]"
                :page-size="recSize"
                :total="recTotal"
                @current-change="onRecPageChange"
                @size-change="onRecSizeChange"
              />
            </div>
          </div>
        </el-card>

        <!-- Westgard 规则判读卡 -->
        <el-card shadow="never" class="card">
          <template #header>
            <div class="panel-header">
              <span>Westgard 多规则判读</span>
              <el-tag :type="wgVerdict==='失控' ? 'danger' : 'success'" size="small">
                判读：{{ detail ? wgVerdict : '-' }}
              </el-tag>
            </div>
          </template>
          <el-table :data="wgRules" size="small" border style="width:100%">
            <el-table-column prop="code" label="规则" width="70" align="center" />
            <el-table-column prop="name" label="含义" min-width="150" />
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.state==='trig' ? 'danger' : row.state==='warn' ? 'warning' : 'success'" size="small">
                  {{ row.state==='trig' ? '触发' : row.state==='warn' ? '警告' : '未触发' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 靶值与质控参数卡 + 基础数据四页签 -->
        <el-card shadow="never" class="card param-card">
          <template #header>
            <div class="panel-header">
              <span>靶值与基础数据</span>
              <span>
                <el-button size="small" type="warning" plain :disabled="!detail" @click="openReestimate">重估靶值</el-button>
                <el-button size="small" type="primary" plain :disabled="!detail" @click="openRuleDialog">调整规则</el-button>
              </span>
            </div>
          </template>
          <el-tabs v-model="baseTab">
            <el-tab-pane label="项目信息" name="info">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="项目名称">{{ sel?.itemName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="检测方法">{{ sel?.method || '-' }}</el-descriptions-item>
                <el-descriptions-item label="仪器">{{ sel?.instrumentName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="单位">{{ sel?.unit || '-' }}</el-descriptions-item>
                <el-descriptions-item label="批号">{{ sel?.lotNo || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
            <el-tab-pane label="靶值/SD" name="target">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="靶值">{{ fmt(detail?.target) || '-' }}</el-descriptions-item>
                <el-descriptions-item label="SD">{{ fmt(detail?.sd) || '-' }}</el-descriptions-item>
                <el-descriptions-item label="CV">{{ cvText((detail?.lot) ) }}</el-descriptions-item>
                <el-descriptions-item label="数据点数">{{ detail?.points?.length ?? '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
            <el-tab-pane label="质控品" name="material">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="批号">{{ sel?.lotNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="水平">{{ levelText(sel?.level) }}</el-descriptions-item>
                <el-descriptions-item label="靶值来源">常规靶值</el-descriptions-item>
                <el-descriptions-item label="使用状态">{{ sel?.status === '失控' ? '失控' : '在控' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
            <el-tab-pane label="规则" name="rules">
              <el-table :data="detail?.rules || []" size="small" border style="width:100%">
                <el-table-column prop="ruleCode" label="编码" width="80" align="center" />
                <el-table-column prop="ruleName" label="规则名称" min-width="120" />
                <el-table-column label="类型" width="80" align="center">
                  <template #default="{ row }">{{ row.ruleType === 'WARNING' ? '警告' : '拒绝' }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </div>
    </div>

    <!-- 测量录入弹窗 (保留原有测量功能) -->
    <el-dialog v-model="entryDialogVisible" title="录入测量值" width="420px">
      <el-form label-width="90px">
        <el-form-item label="批号"><el-input :model-value="sel?.lotNo" disabled /></el-form-item>
        <el-form-item label="测量值"><el-input-number v-model="entry.measureValue" :precision="4" :step="0.1" style="width:100%" /></el-form-item>
        <el-form-item label="测量时间">
          <el-date-picker v-model="entry.measureTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
      </el-form>
      <div v-if="lastJudge" class="judge-box" :class="lastJudge.qcResult === 0 ? 'danger' : 'ok'">
        判定：{{ lastJudge.qcResult === 0 ? '失控 · ' + (lastJudge.ruleViolation || '') : '在控' }}
      </div>
      <template #footer>
        <el-button @click="entryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="entering" @click="submitEntry">提交</el-button>
      </template>
    </el-dialog>

    <!-- 重估靶值弹窗 -->
    <el-dialog v-model="reestimateVisible" title="重估靶值与SD" width="520px">
      <el-form label-width="110px">
        <el-form-item label="项目">
          <el-input :model-value="sel?.itemCode + ' / ' + (sel?.itemName || '')" disabled />
        </el-form-item>
        <el-form-item label="批号">
          <el-input :model-value="sel?.lotNo" disabled />
        </el-form-item>
        <el-form-item label="当前靶值">
          <el-input :model-value="fmt(detail?.target)" disabled />
        </el-form-item>
        <el-form-item label="当前SD">
          <el-input :model-value="fmt(detail?.sd)" disabled />
        </el-form-item>
        <el-form-item label="最小样本数" required>
          <el-input-number v-model="reestimateMin" :min="2" :max="200" :step="1" style="width:100%" />
          <div style="font-size:12px;color:#909399;margin-top:4px">默认20条；仅统计在控(ACCEPT)记录。样本数不足将拒绝重估。</div>
        </el-form-item>
      </el-form>
      <div v-if="reestimateResult" style="margin: 10px 0; padding: 10px; background: var(--el-color-success-light-9); border-radius: 6px; font-size: 13px;">
        <div><b>重估成功</b> · 使用 {{ reestimateResult.sampleCount }} 条在控样本</div>
        <div>靶值: {{ fmt(reestimateResult.oldTarget) }} → <span style="color:#67c23a">{{ fmt(reestimateResult.newTarget) }}</span></div>
        <div>SD: {{ fmt(reestimateResult.oldSd) }} → <span style="color:#67c23a">{{ fmt(reestimateResult.newSd) }}</span></div>
        <div>CV: <span style="color:#67c23a">{{ (reestimateResult.newCv ?? 0).toFixed(2) }}%</span> · 时间 {{ reestimateResult.estimatedAt }}</div>
      </div>
      <template #footer>
        <el-button @click="reestimateVisible = false">取消</el-button>
        <el-button type="warning" :loading="reestimating" @click="doReestimate">确认重估靶值</el-button>
      </template>
    </el-dialog>

    <!-- 调整规则弹窗 -->
    <el-dialog v-model="ruleDialogVisible" title="调整Westgard规则启用状态" width="640px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
        title="调整后立即对后续质控判定生效。对已有的失控/在控判定结果不做回溯。" />
      <el-table v-loading="ruleSaving" :data="ruleList" size="small" border style="width:100%">
        <el-table-column prop="ruleCode" label="编码" width="90" align="center" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.ruleType === 1" type="warning" size="small">警告</el-tag>
            <el-tag v-else type="danger" size="small">拒绝</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-switch v-model="row._enabled" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="saveRules">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, ScatterChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, MarkLineComponent } from 'echarts/components'
import {
  getIqcSummary,
  getIqcProjects,
  getIqcProjectDetail,
  submitQcRecord,
  recalculateQcRecords,
  reestimateLot,
  getQcRuleList,
  updateQcRule,
  getQcRecordList,
  type IqcProject,
  type IqcProjectDetail,
  type QcRule,
  type QcRecord
} from '@/api/qc'
import { getAllInstruments, type Instrument } from '@/api/instrument'

use([CanvasRenderer, LineChart, ScatterChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent])

const summary = reactive<{ total?: number; inControl?: number; warning?: number; outOfControl?: number }>({})
const instruments = ref<Instrument[]>([])
const projects = ref<IqcProject[]>([])
const listLoading = ref(false)
const recalcing = ref(false)
const filters = reactive({ instrumentId: undefined as number | undefined, status: '', keyword: '', dateRange: [] as string[] })
const selCode = ref<number | null>(null)
const selected = ref<IqcProject | null>(null)
const detail = ref<IqcProjectDetail | null>(null)
const baseTab = ref('info')
// 右侧大 Tab: L-J 图 / Z分数图 / 质控数据明细 (分页显示, 替代原垂直堆叠)
const vizTab = ref<'lj' | 'z' | 'detail'>('lj')
const chartLoading = ref(false)
const chartRef = ref()
const zChartRef = ref()
const chartOption = ref<any>(null)
const zOption = ref<any>(null)
function onVizTabChange(name: string) {
  nextTick(() => {
    if (name === 'lj') chartRef.value?.chart?.resize()
    if (name === 'z')  zChartRef.value?.chart?.resize()
  })
}
const entryDialogVisible = ref(false)
const entering = ref(false)
const lastJudge = ref<any>(null)
const entry = reactive({ measureValue: undefined as number | undefined, measureTime: dayjs().format('YYYY-MM-DD HH:mm:ss') })

// ===== 重估靶值 & 调整规则 弹窗 =====
const reestimateVisible = ref(false)
const reestimating = ref(false)
const reestimateMin = ref(20)
const reestimateResult = ref<any>(null)

const ruleDialogVisible = ref(false)
const ruleList = ref<(QcRule & { _enabled: number })[]>([])
const ruleSaving = ref(false)

// ===== 左右分栏可拖动 =====
const leftWidthPx = ref<number>(0) // 0 表示使用默认比例
const resizerRef = ref<HTMLElement | null>(null)
let resizeStartX = 0
let resizeStartLeft = 0
function onResizeMouseDown(e: MouseEvent) {
  resizeStartX = e.clientX
  const gridEl = document.querySelector('.main-grid') as HTMLElement | null
  const leftEl = document.querySelector('.main-grid > .project-card') as HTMLElement | null
  if (!gridEl || !leftEl) return
  resizeStartLeft = leftEl.getBoundingClientRect().width
  leftWidthPx.value = resizeStartLeft
  document.addEventListener('mousemove', onResizeMouseMove)
  document.addEventListener('mouseup', onResizeMouseUp)
  e.preventDefault()
}
function onResizeMouseMove(e: MouseEvent) {
  const dx = e.clientX - resizeStartX
  const gridEl = document.querySelector('.main-grid') as HTMLElement | null
  const totalW = gridEl ? gridEl.getBoundingClientRect().width : 1200
  const minLeft = Math.max(320, totalW * 0.35)
  const maxLeft = Math.max(minLeft, totalW - 380)
  const newLeft = Math.min(Math.max(resizeStartLeft + dx, minLeft), maxLeft)
  leftWidthPx.value = newLeft
}
function onResizeMouseUp() {
  document.removeEventListener('mousemove', onResizeMouseMove)
  document.removeEventListener('mouseup', onResizeMouseUp)
  nextTick(() => chartRef.value?.chart?.resize())
}

// ===== 质控记录明细表格 =====
const recList = ref<QcRecord[]>([])
const recTotal = ref(0)
const recPage = ref(1)
const recSize = ref(10)
const recLoading = ref(false)
async function loadRecordList(keepPage = false) {
  if (!keepPage) recPage.value = 1
  if (!selectedCode.value) { recList.value = []; recTotal.value = 0; return }
  recLoading.value = true
  try {
    const lotId = selectedCode.value
    const begin = filters.dateRange?.[0]
    const end = filters.dateRange?.[1]
    const res = await getQcRecordList({ pageNum: recPage.value, pageSize: recSize.value, qcLotId: lotId, beginDate: begin, endDate: end })
    recList.value = res.records || []
    recTotal.value = Number(res.total) || 0
  } catch (e) { recList.value = []; recTotal.value = 0 } finally { recLoading.value = false }
}
function onRecPageChange(p: number) { recPage.value = p; loadRecordList(true) }
function onRecSizeChange(s: number) { recSize.value = s; recPage.value = 1; loadRecordList(true) }

const sel = computed(() => selected.value)
const selectedCode = computed(() => sel.value?.lotId ?? null)

const dtTitle = computed(() => {
  if (!sel.value || !detail.value) return ''
  const d = detail.value
  const t = d.lot?.targetValue ?? d.target
  const s = d.lot?.sd ?? d.sd
  return `${sel.value.itemName} · ${sel.value.instrumentName} · 靶值 ${fmt(t)} / SD ${fmt(s)}`
})

const LEVEL_MAP: Record<number, string> = { 1: 'L1', 2: 'L2', 3: 'L3' }
function levelText(l?: number) { return l != null ? (LEVEL_MAP[l] || l) : '-' }
function fmt(v?: number) { return v === undefined || v === null || v === '' ? '-' : Number(v).toFixed(2) }
function cvText(lot: any) {
  if (!lot) return '-'
  if (lot.cv != null) return Number(lot.cv).toFixed(2) + '%'
  if (lot.sd && lot.targetValue) return ((lot.sd / lot.targetValue) * 100).toFixed(2) + '%'
  return '-'
}
function statusTag(s?: string): any {
  if (s === '失控') return 'danger'
  if (s === '警告') return 'warning'
  return 'success'
}
function rowClass({ row }: { row: IqcProject }) { return row.status === '失控' ? 'row-out' : '' }

/** 仪器切换: 清空旧选中, 重新加载该仪器下的质控项目 */
function onInstrumentChange() {
  selected.value = null
  selCode.value = null
  detail.value = null
  chartOption.value = null
  loadProjects()
}

function resetFilter() {
  filters.instrumentId = undefined; filters.status = ''; filters.keyword = ''; filters.dateRange = []
  loadProjects()
}

async function loadSummary() {
  try { Object.assign(summary, await getIqcSummary()) } catch (e) { /* ignore */ }
}
async function loadProjects() {
  listLoading.value = true
  try {
    projects.value = await getIqcProjects({
      instrumentId: filters.instrumentId,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })
    loadSummary()
    if (selectedCode.value) {
      const keep = projects.value.find(p => p.lotId === selectedCode.value)
      if (keep) selected.value = keep
      else selectProject(projects.value[0] || null)
    } else if (projects.value.length) {
      selectProject(projects.value[0])
    } else {
      selectProject(null)
    }
  } finally { listLoading.value = false }
}

function onSelectProject(row: IqcProject) { if (row) selectProject(row) }
async function selectProject(p: IqcProject | null) {
  selected.value = p
  selCode.value = p?.lotId ?? null
  if (!p) { detail.value = null; chartOption.value = null; await loadRecordList(); return }
  chartLoading.value = true
  try {
    detail.value = await getIqcProjectDetail(p.lotId)
    buildChart()
    await loadRecordList()
  } finally { chartLoading.value = false }
}

// ===== 失控计算 (批量重算, 时间范围内) =====
async function doRecalculate() {
  if (!selectedCode.value) { ElMessage.warning('请先选择质控项目'); return }
  const lotId = selectedCode.value
  const begin = filters.dateRange?.[0]
  const end = filters.dateRange?.[1]
  recalcing.value = true
  try {
    const res = await recalculateQcRecords({ lotId, beginDate: begin, endDate: end })
    const rangeTxt = begin && end ? ` (${begin} ~ ${end})` : '(全部时间)'
    if (res && res.rejectCount > 0) {
      ElMessage.warning(`计算失控完成${rangeTxt}：共 ${res.total} 条，命中失控 ${res.rejectCount} 条，已自动生成/更新失控报告`)
    } else {
      ElMessage.success(`计算完成${rangeTxt}：共 ${res.total} 条，未命中失控`)
    }
    await loadProjects()
    await loadRecordList()
  } catch (e: any) {
    ElMessage.error(e?.message || '计算失控失败')
  } finally {
    recalcing.value = false
  }
}

// ===== Westgard 规则判读(前端实时计算, 与后端同算法) =====
const wgMeta = [
  { code: '1₂s', name: '单点超出±2SD (警告)' },
  { code: '1₃s', name: '单点超出±3SD (失控)' },
  { code: '2₂s', name: '连续2点同侧超±2SD (失控)' },
  { code: 'R₄s', name: '极差超出4SD (失控)' },
  { code: '4₁s', name: '连续4点同侧超±1SD (失控)' },
  { code: '10x̄', name: '连续10点偏向均值一侧 (失控)' }
]
function zsOf(points: IqcProjectDetail['points'], target: number, sd: number): number[] {
  if (sd <= 0) return []
  return points.map(p => (Number(p.measureValue) - target) / sd)
}
function evalWg() {
  const d = detail.value
  if (!d || !d.points?.length) return { rules: wgMeta.map(m => ({ ...m, state: 'pass' })), verdict: '暂无' }
  const target = Number(d.lot?.targetValue ?? d.target ?? 0)
  const sd = Number(d.lot?.sd ?? d.sd ?? 1) || 1
  const zs = zsOf(d.points, target, sd)
  const states: Record<string, string> = { '1₂s': 'pass', '1₃s': 'pass', '2₂s': 'pass', 'R₄s': 'pass', '4₁s': 'pass', '10x̄': 'pass' }
  // 1₂s / 1₃s
  if (zs.some(z => Math.abs(z) >= 2)) states['1₂s'] = 'warn'
  if (zs.some(z => Math.abs(z) >= 3)) states['1₃s'] = 'trig'
  // 2₂s: 连续2点同侧 |z|∈[2,3)
  for (let i = 1; i < zs.length; i++) {
    if (zs[i] * zs[i - 1] > 0 && Math.abs(zs[i]) >= 2 && Math.abs(zs[i]) < 3 && Math.abs(zs[i - 1]) >= 2 && Math.abs(zs[i - 1]) < 3) { states['2₂s'] = 'trig'; break }
  }
  // R₄s: 相邻两点相反方向且都超±2SD (极差>4SD)
  for (let i = 1; i < zs.length; i++) {
    if (zs[i] * zs[i - 1] < 0 && Math.abs(zs[i]) >= 2 && Math.abs(zs[i - 1]) >= 2) { states['R₄s'] = 'trig'; break }
  }
  // 4₁s
  for (let i = 3; i < zs.length; i++) {
    const seg = zs.slice(i - 3, i + 1)
    const same = seg.every(z => z > 0) || seg.every(z => z < 0)
    if (same && seg.every(z => Math.abs(z) >= 1)) { states['4₁s'] = 'trig'; break }
  }
  // 10x̄
  for (let i = 9; i < zs.length; i++) {
    const seg = zs.slice(i - 9, i + 1)
    const same = seg.every(z => z > 0) || seg.every(z => z < 0)
    if (same) { states['10x̄'] = 'trig'; break }
  }
  const verdict = (states['1₃s'] || states['2₂s'] || states['R₄s'] || states['4₁s'] || states['10x̄']) === 'trig' ? '失控' : states['1₂s'] === 'warn' ? '警告' : '在控'
  return { rules: wgMeta.map(m => ({ ...m, state: states[m.code] })), verdict }
}
const wg = computed(() => evalWg())
const wgRules = computed(() => wg.value.rules)
const wgVerdict = computed(() => wg.value.verdict)

// ===== L-J 图 =====
function buildChart() {
  const d = detail.value
  if (!d) { chartOption.value = null; return }
  const points = d.points || []
  const target = Number(d.lot?.targetValue ?? d.target ?? 0)
  const sd = Number(d.lot?.sd ?? d.sd ?? 1) || 1
  const min = target - 4 * sd, max = target + 4 * sd
  const yVals: number[] = []
  const reject: any[] = []
  const x = points.map((p, i) => {
    const v = Math.min(Math.max(Number(p.measureValue), min), max)
    yVals.push(Number(v.toFixed(4)))
    if (p.qcResult === 0) reject.push({ value: [i, Number(v.toFixed(4))] })
    return dayjs(p.measureTime).format('MM-DD HH:mm')
  })
  const markLines = [
    { yAxis: target + 3 * sd, lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: '+3SD', position: 'end', color: '#F56C6C' } },
    { yAxis: target + 2 * sd, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '+2SD', position: 'end', color: '#E6A23C' } },
    { yAxis: target, lineStyle: { color: '#67C23A', type: 'solid', width: 2 }, label: { formatter: '靶值', position: 'end', color: '#67C23A' } },
    { yAxis: target - 2 * sd, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '-2SD', position: 'end', color: '#E6A23C' } },
    { yAxis: target - 3 * sd, lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: '-3SD', position: 'end', color: '#F56C6C' } }
  ]
  chartOption.value = {
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = points[ps[0].dataIndex]
        const st = p.qcResult === 0 ? '失控' : '在控'
        return `<b>${dayjs(p.measureTime).format('YYYY-MM-DD HH:mm')}</b><br/>测量值: ${p.measureValue}<br/>Z: ${p.zScore != null ? Number(p.zScore).toFixed(4) : '-'}<br/>判定: <b style="color:${p.qcResult === 0 ? '#F56C6C' : '#67C23A'}">${st}</b>`
      }
    },
    legend: { data: ['测量值', '失控点'], top: 0 },
    grid: { left: 60, right: 40, top: 30, bottom: 60 },
    xAxis: { type: 'category', data: x, axisLabel: { rotate: 45, fontSize: 10 } },
    yAxis: { type: 'value', min, max, name: sel.value?.unit || '', axisLabel: { formatter: (v: number) => v.toFixed(1) } },
    series: [
      { name: '测量值', type: 'line', data: yVals, symbol: 'circle', symbolSize: 7, itemStyle: { color: '#1A73E8' }, lineStyle: { color: '#1A73E8', width: 2 }, markLine: { symbol: 'none', silent: true, data: markLines } },
      { name: '失控点', type: 'scatter', data: reject, symbol: 'circle', symbolSize: 16, itemStyle: { color: '#F56C6C', borderColor: '#fff', borderWidth: 2 }, z: 10 }
    ]
  }
  // 同步构建 Z 分数图
  buildZScoreChart()
}

// ===== Z 分数图 (y = (value - target) / SD, 以 0 为中心, ±1/±2/±3 四条参考线) =====
function buildZScoreChart() {
  const d = detail.value
  if (!d) { zOption.value = null; return }
  const points = d.points || []
  const target = Number(d.lot?.targetValue ?? d.target ?? 0)
  const sd = Number(d.lot?.sd ?? d.sd ?? 1) || 1
  const clampMin = -4, clampMax = 4
  const zVals: number[] = []
  const reject: any[] = []
  const x = points.map((p, i) => {
    let z = p.zScore != null ? Number(p.zScore) : (Number(p.measureValue) - target) / sd
    if (!isFinite(z)) z = 0
    const clamped = Math.min(Math.max(z, clampMin), clampMax)
    zVals.push(Number(clamped.toFixed(4)))
    if (p.qcResult === 0) reject.push({ value: [i, Number(clamped.toFixed(4))] })
    return dayjs(p.measureTime).format('MM-DD HH:mm')
  })
  const markLines = [
    { yAxis: 3,  lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: '+3', position: 'end', color: '#F56C6C' } },
    { yAxis: 2,  lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '+2', position: 'end', color: '#E6A23C' } },
    { yAxis: 1,  lineStyle: { color: '#409EFF', type: 'dotted' }, label: { formatter: '+1', position: 'end', color: '#409EFF' } },
    { yAxis: 0,  lineStyle: { color: '#67C23A', type: 'solid', width: 2 }, label: { formatter: '0', position: 'end', color: '#67C23A' } },
    { yAxis: -1, lineStyle: { color: '#409EFF', type: 'dotted' }, label: { formatter: '-1', position: 'end', color: '#409EFF' } },
    { yAxis: -2, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '-2', position: 'end', color: '#E6A23C' } },
    { yAxis: -3, lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: '-3', position: 'end', color: '#F56C6C' } }
  ]
  zOption.value = {
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = points[ps[0].dataIndex]
        const z = p.zScore != null ? Number(p.zScore) : (Number(p.measureValue) - target) / sd
        const st = p.qcResult === 0 ? '失控' : '在控'
        return `<b>${dayjs(p.measureTime).format('YYYY-MM-DD HH:mm')}</b><br/>测量值: ${p.measureValue}<br/>Z分数: ${isFinite(z) ? z.toFixed(4) : '-'}<br/>判定: <b style="color:${p.qcResult === 0 ? '#F56C6C' : '#67C23A'}">${st}</b>`
      }
    },
    legend: { data: ['Z分数', '失控点'], top: 0 },
    grid: { left: 60, right: 40, top: 30, bottom: 60 },
    xAxis: { type: 'category', data: x, axisLabel: { rotate: 45, fontSize: 10 } },
    yAxis: { type: 'value', min: clampMin, max: clampMax, name: 'Z', interval: 1, axisLabel: { formatter: (v: number) => v.toFixed(0) } },
    series: [
      { name: 'Z分数', type: 'line', data: zVals, symbol: 'circle', symbolSize: 7, itemStyle: { color: '#8E44AD' }, lineStyle: { color: '#8E44AD', width: 2 }, markLine: { symbol: 'none', silent: true, data: markLines } },
      { name: '失控点', type: 'scatter', data: reject, symbol: 'circle', symbolSize: 16, itemStyle: { color: '#F56C6C', borderColor: '#fff', borderWidth: 2 }, z: 10 }
    ]
  }
}

watch([detail], () => {
  if (detail.value) nextTick(() => {
    chartRef.value?.chart?.resize()
    zChartRef.value?.chart?.resize()
  })
})

// ===== 测量录入 =====
async function submitEntry() {
  if (entry.measureValue === undefined) { ElMessage.warning('请输入测量值'); return }
  if (!selectedCode.value) return
  entering.value = true
  try {
    const res = await submitQcRecord({ qcLotId: selectedCode.value, measureValue: entry.measureValue, measureTime: entry.measureTime })
    lastJudge.value = res
    if (res.qcResult === 1) ElMessage.success('质控在控')
    else ElMessage.error('质控失控: ' + (res.ruleViolation || ''))
    entry.measureValue = undefined
    await loadProjects()
    await loadRecordList()
  } finally { entering.value = false }
}

// ===== 重估靶值 (真实接口: POST /qc/lots/{id}/reestimate) =====
async function openReestimate() {
  if (!selectedCode.value) { ElMessage.warning('请先选择质控项目'); return }
  reestimateResult.value = null
  reestimateMin.value = 20
  reestimateVisible.value = true
}
async function doReestimate() {
  if (!selectedCode.value) return
  const n = Math.max(2, Number(reestimateMin.value || 20))
  reestimating.value = true
  try {
    const res = await reestimateLot({ id: selectedCode.value, minSamples: n })
    reestimateResult.value = res
    ElMessage.success(`靶值重估成功：共${res.sampleCount}条在控样本，靶值 ${fmt(res.oldTarget)} → ${fmt(res.newTarget)}，SD ${fmt(res.oldSd)} → ${fmt(res.newSd)}，CV ${(res.newCv ?? 0).toFixed(2)}%`)
    // 刷新列表和详情, 重绘质控图
    await loadProjects()
    if (selectedCode.value) {
      const p = projects.value.find(x => x.lotId === selectedCode.value)
      if (p) await selectProject(p)
    }
    await loadRecordList()
  } catch (e: any) {
    ElMessage.error(e?.message || '重估失败')
  } finally {
    reestimating.value = false
  }
}

// ===== 调整规则 (真实接口: GET /qc/rules + PUT /qc/rules/{id}/enabled) =====
async function openRuleDialog() {
  if (!detail.value) { ElMessage.warning('请先选择质控项目'); return }
  ruleSaving.value = true
  try {
    const list = await getQcRuleList()
    ruleList.value = list.map(r => ({ ...r, _enabled: Number(r.enabled ?? 1) }))
    ruleDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载规则失败')
  } finally {
    ruleSaving.value = false
  }
}
async function saveRules() {
  ruleSaving.value = true
  try {
    let ok = 0
    for (const r of ruleList.value) {
      const target = Number(r._enabled ?? 0) > 0 ? 1 : 0
      if (Number(r.enabled) !== target) {
        await updateQcRule({ ...r, id: Number(r.id), enabled: target })
        r.enabled = target
        ok++
      }
    }
    ElMessage.success(ok > 0 ? `已保存 ${ok} 条规则启停状态` : '规则未发生变化')
    ruleDialogVisible.value = false
    // 刷新详情(规则页签)与质控图
    if (selectedCode.value) {
      const p = projects.value.find(x => x.lotId === selectedCode.value)
      if (p) await selectProject(p)
    }
    await loadRecordList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    ruleSaving.value = false
  }
}

onMounted(async () => {
  // 1. 先加载质控项目(不带仪器过滤), 从项目中提取有质控品的 instrumentId
  let allProjects: IqcProject[] = []
  try {
    allProjects = await getIqcProjects()
  } catch (_) { allProjects = [] }

  // 2. 收集所有被质控项目引用的 instrumentId (兼容 Long→String 序列化)
  const relatedInstIds = new Set<number>()
  allProjects.forEach(p => {
    const id = Number(p.instrumentId)
    if (!Number.isNaN(id) && id > 0) relatedInstIds.add(id)
  })

  // 3. 加载全部仪器, 过滤出有质控品关联的
  try {
    const all = await getAllInstruments()
    instruments.value = all.filter(ins => relatedInstIds.has(Number(ins.id)))
    // 按 ID 排序, 保持稳定顺序
    instruments.value.sort((a, b) => Number(a.id) - Number(b.id))
  } catch (_) {
    instruments.value = []
  }

  // 4. 首次加载全部项目
  await loadProjects()
})
</script>

<style scoped lang="scss">
.iqc-page { padding: 12px; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { border-radius: 8px; padding: 14px; text-align: center; background: #f7f8fa;
  .kpi-label { color: var(--el-text-color-secondary); font-size: 13px; }
  .kpi-value { font-size: 30px; font-weight: 700; margin-top: 4px; }
  &.ok .kpi-value { color: #67c23a; } &.warn .kpi-value { color: #e6a23c; } &.danger .kpi-value { color: #f56c6c; } &.mono .kpi-value { color: #1a73e8; }
}
.search-card { margin-bottom: 12px; :deep(.el-card__body){ padding: 12px 16px 0 16px; } }

/* 主体：左列表 | 分隔条 | 右详情 */
.main-grid { display: grid; grid-template-columns: 1.7fr 6px 1.3fr; gap: 0; align-items: start; }
.main-grid > .project-card { border-right: 0; border-top-right-radius: 0; border-bottom-right-radius: 0; }
.main-grid > .right-col > .card:first-child { border-top-left-radius: 0; border-bottom-left-radius: 0; }
.col-resizer {
  width: 6px; min-width: 6px; align-self: stretch;
  cursor: col-resize;
  background: transparent;
  position: relative;
  transition: background .15s ease;
  &:hover, &:active { background: var(--el-color-primary-light-5); }
  &::after {
    content: '';
    position: absolute; left: 2px; top: 50%; transform: translateY(-50%);
    width: 2px; height: 32px; border-radius: 2px;
    background: var(--el-border-color);
  }
  &:hover::after, &:active::after { background: var(--el-color-primary); }
}

.project-card { :deep(.el-card__body){ padding: 8px; } }
.right-col { display: flex; flex-direction: column; gap: 12px; min-width: 0; }

/* 可视化 Tab 大卡 (L-J / Z-分数 / 明细 三张) */
.viz-card {
  :deep(.el-card__header) {
    padding-top: 8px; padding-bottom: 0;
    border-bottom: 0;
  }
  .panel-header {
    gap: 12px;
    align-items: flex-end;
  }
  .viz-tabs {
    :deep(.el-tabs__header) { margin-bottom: 4px; }
    :deep(.el-tabs__item) { height: 36px; line-height: 36px; font-weight: 500; }
  }
  .chart-box { height: 340px; .chart { width: 100%; height: 100%; } }
  .records-pane { padding: 4px 4px 8px 4px; }
  .records-header { padding: 2px 2px 8px 2px; }
  .pager-row {
    display: flex; justify-content: flex-end; align-items: center;
    padding: 10px 4px 0 4px;
  }
}
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.unit { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 3px; }
:deep(.row-out) { background-color: var(--el-color-danger-light-9); }
.sub { color: var(--el-text-color-secondary); font-size: 12px; }
.judge-box { padding: 8px 12px; border-radius: 4px; margin-bottom: 8px; font-size: 13px; &.ok { background: var(--el-color-success-light-9); color: #67c23a; } &.danger { background: var(--el-color-danger-light-9); color: #f56c6c; } }
</style>