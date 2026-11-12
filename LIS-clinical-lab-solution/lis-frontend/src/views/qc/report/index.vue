<template>
  <div class="qc-report-page">
    <!-- 顶部: 年月选择 + 查询 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="年月">
          <el-date-picker
            v-model="selectedMonth"
            type="month"
            placeholder="请选择年月"
            value-format="YYYY-MM"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button type="warning" :icon="Printer" :disabled="detailData.length === 0" @click="printReport">
            打印质控报告
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card stat-total">
            <div class="stat-icon">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">质控次数</div>
              <div class="stat-value">{{ summary.totalCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card stat-out">
            <div class="stat-icon">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">失控次数</div>
              <div class="stat-value">{{ summary.outCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card stat-rate">
            <div class="stat-icon">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">失控率</div>
              <div class="stat-value">{{ summary.outRate }}%</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 各项目质控情况 -->
      <el-card shadow="never" class="table-card">
        <template #header>
          <span>{{ currentMonthLabel }} 各项目质控情况</span>
        </template>
        <el-table :data="tableData" border stripe style="width: 100%">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="itemName" label="项目名称" min-width="160" />
          <el-table-column prop="instrumentName" label="仪器" min-width="140" />
          <el-table-column prop="totalCount" label="质控次数" width="100" align="center" />
          <el-table-column label="失控次数" width="100" align="center">
            <template #default="{ row }">
              <span :class="{ 'out-text': row.outCount > 0 }">{{ row.outCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="CV(%)" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="cvTagType(Number(row.cv))" size="small">{{ row.cv }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="合格率" width="160" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="Number(row.passRate)"
                :color="row.passRate >= 95 ? '#67C23A' : row.passRate >= 80 ? '#E6A23C' : '#F56C6C'"
                :stroke-width="14"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column label="评估" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passRate >= 95 ? 'success' : 'warning'" size="small">
                {{ row.passRate >= 95 ? '合格' : '需改进' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 质控明细记录 -->
      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="panel-header">
            <span>质控明细记录</span>
            <el-select
              v-model="detailFilter"
              placeholder="筛选项目"
              clearable
              size="small"
              style="width: 200px"
            >
              <el-option
                v-for="item in itemNameOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </div>
        </template>
        <el-table :data="filteredDetails" border stripe style="width: 100%" max-height="500">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="itemName" label="项目名称" min-width="120" />
          <el-table-column prop="instrumentName" label="仪器" min-width="140" />
          <el-table-column prop="measureValue" label="测量值" width="100" align="center" />
          <el-table-column label="Z分数" width="100" align="center">
            <template #default="{ row }">{{ row.zScore ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="判定结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.qcResult === 'ACCEPT' ? 'success' : 'danger'" size="small">
                {{ row.qcResult === 'ACCEPT' ? '在控' : '失控' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="违反规则" width="120" align="center">
            <template #default="{ row }">{{ row.ruleViolation || '-' }}</template>
          </el-table-column>
          <el-table-column label="测量时间" width="170" align="center">
            <template #default="{ row }">{{ formatDateTime(row.measureTime) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { DataLine, Warning, TrendCharts, Printer } from '@element-plus/icons-vue'
import { getQcMonthlyReport, type QcItemReport, type QcReportDetail } from '@/api/qc'
import { formatDateTime } from '@/utils/format'
import { escapeHtml } from '@/utils/sanitize'
import { openPrintWindow } from '@/utils/print'

const loading = ref(false)
const selectedMonth = ref<string>('')
const tableData = ref<QcItemReport[]>([])
const detailData = ref<QcReportDetail[]>([])
const detailFilter = ref<string>('')

const summary = reactive({
  totalCount: 0,
  outCount: 0,
  outRate: 0
})

const currentMonthLabel = computed(() => {
  return selectedMonth.value ? `${selectedMonth.value} 月报` : '质控月报'
})

const itemNameOptions = computed(() => {
  const names = new Set(detailData.value.map(d => d.itemName).filter(Boolean))
  return Array.from(names)
})

const filteredDetails = computed(() => {
  if (!detailFilter.value) return detailData.value
  return detailData.value.filter(d => d.itemName === detailFilter.value)
})

function cvTagType(cv: number): string {
  if (cv < 3) return 'success'
  if (cv < 5) return 'warning'
  return 'danger'
}

async function handleQuery() {
  if (!selectedMonth.value) {
    ElMessage.warning('请选择年月')
    return
  }
  loading.value = true
  try {
    const [year, month] = selectedMonth.value.split('-').map(Number)
    const report = await getQcMonthlyReport(year, month)
    summary.totalCount = report.totalRecords
    summary.outCount = report.outOfControlCount
    summary.outRate = report.outOfControlRate
    tableData.value = report.itemReports || []
    detailData.value = report.details || []
  } catch (e) {
    ElMessage.error('加载月报数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  selectedMonth.value = dayjs().format('YYYY-MM')
  handleQuery()
})

/** 打印质控报告 (以失控报告为主) */
function printReport() {
  if (detailData.value.length === 0) {
    ElMessage.warning('当前无质控明细可打印')
    return
  }
  const outDetails = detailData.value.filter(d => d.qcResult !== 'ACCEPT')
  const rows = (outDetails.length > 0 ? outDetails : detailData.value).map((d, i) => `
    <tr>
      <td class="c">${i + 1}</td>
      <td>${escapeHtml(d.itemName) || '-'}</td>
      <td>${escapeHtml(d.instrumentName) || '-'}</td>
      <td class="c">${escapeHtml(d.measureValue)}</td>
      <td class="c">${escapeHtml(d.zScore ?? '-')}</td>
      <td class="c ${d.qcResult !== 'ACCEPT' ? 'out' : ''}">${d.qcResult === 'ACCEPT' ? '在控' : '失控'}</td>
      <td class="c">${escapeHtml(d.ruleViolation) || '-'}</td>
      <td class="c">${formatDateTime(d.measureTime)}</td>
    </tr>`).join('')

  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>质控失控报告</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: "Microsoft YaHei", SimSun, sans-serif; color: #333; padding: 24px; }
  .title { text-align: center; font-size: 22px; font-weight: bold; margin-bottom: 6px; }
  .subtitle { text-align: center; font-size: 14px; color: #666; margin-bottom: 18px; }
  .summary { display: flex; justify-content: center; gap: 40px; margin-bottom: 20px; }
  .summary div { text-align: center; }
  .summary .label { font-size: 13px; color: #888; }
  .summary .value { font-size: 24px; font-weight: bold; }
  .summary .value.total { color: #1A73E8; }
  .summary .value.out { color: #F56C6C; }
  .summary .value.rate { color: #E6A23C; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; }
  th { background: #f0f0f0; font-weight: bold; }
  td.c, th.c { text-align: center; }
  td.out { color: #F56C6C; font-weight: bold; }
  .section-title { font-size: 15px; font-weight: bold; margin: 18px 0 8px; }
  .footer { margin-top: 30px; display: flex; justify-content: space-between; font-size: 13px; color: #666; }
  @media print {
    body { padding: 0; }
    .no-print { display: none; }
  }
</style>
</head>
<body>
  <div class="title">质控失控报告</div>
  <div class="subtitle">${selectedMonth.value} 月度质控汇总 (${currentMonthLabel.value})</div>
  <div class="summary">
    <div><div class="label">质控次数</div><div class="value total">${summary.totalCount}</div></div>
    <div><div class="label">失控次数</div><div class="value out">${summary.outCount}</div></div>
    <div><div class="label">失控率</div><div class="value rate">${summary.outRate}%</div></div>
  </div>
  <div class="section-title">${outDetails.length > 0 ? '失控记录明细' : '质控记录明细'}</div>
  <table>
    <thead>
      <tr>
        <th class="c">序号</th>
        <th>项目名称</th>
        <th>仪器</th>
        <th class="c">测量值</th>
        <th class="c">Z分数</th>
        <th class="c">判定</th>
        <th class="c">违反规则</th>
        <th class="c">测量时间</th>
      </tr>
    </thead>
    <tbody>${rows}</tbody>
  </table>
  <div class="footer">
    <span>打印时间：${dayjs().format('YYYY-MM-DD HH:mm:ss')}</span>
    <span>检验科质控报告</span>
  </div>
  <div class="no-print" style="text-align:center;margin-top:20px">
    <button onclick="window.print()" style="padding:8px 24px;font-size:14px">打印</button>
  </div>
</body>
</html>`

  const win = openPrintWindow(html, { width: 1000, height: 700 })
  if (!win) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
  }
}
</script>

<style scoped lang="scss">
.qc-report-page {
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
    &.stat-out {
      .stat-icon { background: var(--el-color-danger); }
      .stat-value { color: var(--el-color-danger); }
    }
    &.stat-rate {
      .stat-icon { background: var(--el-color-warning); }
      .stat-value { color: var(--el-color-warning); }
    }
  }

  .table-card {
    margin-bottom: 16px;

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .out-text {
      color: var(--el-color-danger);
      font-weight: bold;
    }
  }
}
</style>
