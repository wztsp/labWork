<template>
  <div class="tat-monitor-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value">{{ stats.total || 0 }}</div>
            <div class="stat-label">监控标本总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value overdue">{{ stats.overdue || 0 }}</div>
            <div class="stat-label">超时标本数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value normal">{{ stats.notOverdue || 0 }}</div>
            <div class="stat-label">未超时标本数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-value notified">{{ stats.notified || 0 }}</div>
            <div class="stat-label">已通知次数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="时间范围">
          <el-radio-group v-model="rangePreset" size="default" @change="onRangePresetChange">
            <el-radio-button value="today">今天</el-radio-button>
            <el-radio-button value="yesterday">昨天</el-radio-button>
            <el-radio-button value="7d">近7天</el-radio-button>
            <el-radio-button value="30d">近30天</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="rangePreset === 'custom'">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :shortcuts="dateShortcuts"
            style="width: 280px"
            @change="onCustomRangeChange"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.isOverdue" placeholder="全部" clearable style="width: 140px">
            <el-option label="已超时" :value="1" />
            <el-option label="未超时" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="条码/患者/项目"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="warning" :loading="refreshing" @click="handleRefresh">立即扫描</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="sampleBarcode" label="标本条码" width="140" />
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column prop="testItemName" label="检验项目" min-width="160" />
        <el-table-column prop="specimenType" label="标本类型" width="110" />
        <el-table-column label="急查" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isUrgent === 1" type="danger" size="small">急查</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="时限(分钟)" width="100" align="center">
          <template #default="{ row }">{{ row.timeoutMinutes }}</template>
        </el-table-column>
        <el-table-column label="已耗时(分钟)" width="110" align="center">
          <template #default="{ row }">{{ row.elapsedMinutes }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isOverdue === 1 ? 'danger' : 'success'" size="small">
              {{ row.isOverdue === 1 ? '已超时' : '未超时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column label="通知" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.notified === 1 ? 'warning' : 'info'" size="small">
              {{ row.notified === 1 ? '已通知' : '未通知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签收时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.signOffTime) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getTatMonitorPage,
  getTatMonitorStats,
  refreshTatMonitor,
  type TatMonitor,
  type TatMonitorQuery
} from '@/api/monitor'

const loading = ref(false)
const refreshing = ref(false)
const tableData = ref<TatMonitor[]>([])
const total = ref(0)
const stats = ref<Record<string, any>>({})

/** yyyy-MM-dd 格式化 */
function fmtDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function addDays(base: Date, days: number): Date {
  const d = new Date(base)
  d.setDate(d.getDate() + days)
  return d
}

/** 今天的日期字符串 */
const today = (): string => fmtDate(new Date())

/** 时间预设 */
const rangePreset = ref<'today' | 'yesterday' | '7d' | '30d' | 'custom'>('today')
const dateRange = ref<[string, string] | null>(null)

/** 日期快捷项 */
const dateShortcuts = [
  {
    text: '今天',
    value: () => {
      const d = new Date()
      return [d, d]
    }
  },
  {
    text: '昨天',
    value: () => {
      const d = new Date()
      const y = addDays(d, -1)
      return [y, y]
    }
  },
  {
    text: '最近一周',
    value: () => {
      const d = new Date()
      return [addDays(d, -6), d]
    }
  },
  {
    text: '最近一个月',
    value: () => {
      const d = new Date()
      return [addDays(d, -29), d]
    }
  }
]

/** 根据预设计算开始/结束日期 */
function resolveRangeFromPreset(): { start: string; end: string } {
  const now = new Date()
  switch (rangePreset.value) {
    case 'today':
      return { start: today(), end: today() }
    case 'yesterday': {
      const y = fmtDate(addDays(now, -1))
      return { start: y, end: y }
    }
    case '7d':
      return { start: fmtDate(addDays(now, -6)), end: today() }
    case '30d':
      return { start: fmtDate(addDays(now, -29)), end: today() }
    case 'custom':
      if (dateRange.value && dateRange.value[0] && dateRange.value[1]) {
        return { start: dateRange.value[0], end: dateRange.value[1] }
      }
      return { start: today(), end: today() }
    default:
      return { start: today(), end: today() }
  }
}

const queryParams = reactive<TatMonitorQuery>({
  pageNum: 1,
  pageSize: 10,
  isOverdue: undefined,
  keyword: '',
  startDate: today(),
  endDate: today()
})

/** 预设切换时更新查询时间 */
function onRangePresetChange() {
  if (rangePreset.value !== 'custom') {
    dateRange.value = null
    const { start, end } = resolveRangeFromPreset()
    queryParams.startDate = start
    queryParams.endDate = end
    resetAndFetch()
  }
}

/** 自定义日期范围变化 */
function onCustomRangeChange(val: [string, string] | null) {
  if (val && val[0] && val[1]) {
    queryParams.startDate = val[0]
    queryParams.endDate = val[1]
  }
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
}

async function fetchList() {
  loading.value = true
  try {
    const { start, end } = resolveRangeFromPreset()
    queryParams.startDate = start
    queryParams.endDate = end
    const res = await getTatMonitorPage(queryParams)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const { start, end } = resolveRangeFromPreset()
    stats.value = (await getTatMonitorStats({ startDate: start, endDate: end })) || {}
    // 兼容后端字段: notified = pendingNotify or notified
    if (stats.value.notified === undefined && stats.value.pendingNotify !== undefined) {
      stats.value.notified = stats.value.pendingNotify
    }
  } catch (e) {
    // ignore
  }
}

function resetAndFetch() {
  queryParams.pageNum = 1
  fetchList()
  fetchStats()
}

function handleReset() {
  rangePreset.value = 'today'
  dateRange.value = null
  queryParams.isOverdue = undefined
  queryParams.keyword = ''
  queryParams.pageNum = 1
  const { start, end } = resolveRangeFromPreset()
  queryParams.startDate = start
  queryParams.endDate = end
  fetchList()
  fetchStats()
}

function handleSizeChange(size: number) {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  fetchList()
}

function handlePageChange(page: number) {
  queryParams.pageNum = page
  fetchList()
}

async function handleRefresh() {
  refreshing.value = true
  try {
    const count = await refreshTatMonitor()
    ElMessage.success(`扫描完成，新增 ${count} 条监控记录`)
    fetchList()
    fetchStats()
  } catch (e) {
    // ignore
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  const { start, end } = resolveRangeFromPreset()
  queryParams.startDate = start
  queryParams.endDate = end
  fetchStats()
  fetchList()
})
</script>

<style scoped lang="scss">
.tat-monitor-page {
  padding: 16px;

  .stat-row {
    margin-bottom: 16px;
  }

  .stat-card {
    text-align: center;
    padding: 8px 0;

    .stat-value {
      font-size: 28px;
      font-weight: 700;
      color: #303133;

      &.overdue { color: #f56c6c; }
      &.normal { color: #67c23a; }
      &.notified { color: #e6a23c; }
    }

    .stat-label {
      font-size: 13px;
      color: #909399;
      margin-top: 4px;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
