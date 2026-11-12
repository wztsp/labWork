<template>
  <div class="critical-page">
    <!-- 查询条件 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <div class="filter-item">
          <span class="label">日期范围</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
            clearable
          />
        </div>
        <el-button type="primary" :loading="loading" @click="fetchList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :row-class-name="rowClassName"
        style="width: 100%"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="patientName" label="患者姓名" min-width="100" />
        <el-table-column prop="testItemName" label="项目名称" min-width="120" />
        <el-table-column label="结果值" width="120" align="center">
          <template #default="{ row }">
            <span class="critical-value">{{ row.resultValue }} {{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="refRange" label="参考范围" min-width="120" />
        <el-table-column label="危急值范围" width="140" align="center">
          <template #default="{ row }">
            <span class="critical-range">{{ formatCriticalRange(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notifier" label="通知人" min-width="100" align="center">
          <template #default="{ row }">{{ row.notifier || '-' }}</template>
        </el-table-column>
        <el-table-column label="通知时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.notifyTime) }}</template>
        </el-table-column>
        <el-table-column prop="sender" label="发送人" min-width="100" align="center">
          <template #default="{ row }">{{ row.sender || '-' }}</template>
        </el-table-column>
        <el-table-column label="发送时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.sendTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 || row.status === 2" type="warning" link size="small" @click="openNotify(row)">通知</el-button>
            <template v-if="row.status === 1">
              <el-button type="primary" link size="small" @click="openModify(row)">修改</el-button>
              <el-button type="danger" link size="small" @click="handleCancel(row)">取消通知</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 通知 / 修改 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="通知人" prop="notifier">
          <el-input v-model="form.notifier" placeholder="危急值通知人" />
        </el-form-item>
        <el-form-item label="通知时间" prop="notifyTime">
          <el-date-picker v-model="form.notifyTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择通知时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发送人" prop="sender">
          <el-input v-model="form.sender" placeholder="发送人" />
        </el-form-item>
        <el-form-item label="发送时间" prop="sendTime">
          <el-date-picker v-model="form.sendTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择发送时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getCriticalList, notifyCritical, cancelNotifyCritical, updateCriticalRecord } from '@/api/report'
import { useUserStore } from '@/stores/user'

interface CriticalRow {
  id: number
  resultId?: number
  patientName?: string
  testItemName?: string
  resultValue?: string
  unit?: string
  refRange?: string
  criticalLow?: string | number
  criticalHigh?: string | number
  status?: number
  notifier?: string
  notifyTime?: string
  sender?: string
  sendTime?: string
  remark?: string
  createTime?: string
}

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<CriticalRow[]>([])

// 日期范围: 默认当天
const dateRange = ref<string[]>(getTodayRange())
function getTodayRange(): string[] {
  const today = dayjs().format('YYYY-MM-DD')
  return [today, today]
}

const dialogVisible = ref(false)
const submitMode = ref<'notify' | 'modify'>('notify')
const currentId = ref<number>(0)
const submitting = ref(false)
const dialogTitle = computed(() => (submitMode.value === 'notify' ? '危急值通知' : '修改危急值处理记录'))

const form = reactive<{
  notifier: string
  notifyTime: string
  sender: string
  sendTime: string
  remark: string
}>({ notifier: '', notifyTime: '', sender: '', sendTime: '', remark: '' })

const rules = {
  notifier: [{ required: true, message: '请输入通知人', trigger: 'blur' }],
  notifyTime: [{ required: true, message: '请选择通知时间', trigger: 'change' }],
  sender: [{ required: true, message: '请输入发送人', trigger: 'blur' }],
  sendTime: [{ required: true, message: '请选择发送时间', trigger: 'change' }],
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function statusText(status: number): string {
  const map: Record<number, string> = { 0: '待通知', 1: '已通知', 2: '已取消' }
  return map[status] || '未知'
}

function statusTagType(status: number): string {
  const map: Record<number, string> = { 0: 'danger', 1: 'warning', 2: 'info' }
  return map[status] || 'info'
}

function formatCriticalRange(row: CriticalRow): string {
  const low = row.criticalLow ?? row.refRange
  const high = row.criticalHigh ?? ''
  if (low && high) return `${low} ~ ${high}`
  return low || high || '-'
}

function rowClassName({ row }: { row: CriticalRow }): string {
  return row.status === 0 ? 'pending-row' : ''
}

function nowStr(): string {
  return dayjs().format('YYYY-MM-DD HH:mm:ss')
}

async function fetchList() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value || []
    const res = await getCriticalList({ startDate, endDate })
    const list = (res as any) || []
    tableData.value = Array.isArray(list) ? list : (list.records || [])
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  dateRange.value = getTodayRange()
  fetchList()
}

function openNotify(row: CriticalRow) {
  submitMode.value = 'notify'
  currentId.value = row.resultId ?? row.id
  form.notifier = userStore.realName || ''
  form.notifyTime = nowStr()
  form.sender = userStore.realName || ''
  form.sendTime = nowStr()
  form.remark = ''
  dialogVisible.value = true
}

function openModify(row: CriticalRow) {
  submitMode.value = 'modify'
  currentId.value = row.resultId ?? row.id
  form.notifier = row.notifier || userStore.realName || ''
  form.notifyTime = row.notifyTime ? dayjs(row.notifyTime).format('YYYY-MM-DD HH:mm:ss') : nowStr()
  form.sender = row.sender || userStore.realName || ''
  form.sendTime = row.sendTime ? dayjs(row.sendTime).format('YYYY-MM-DD HH:mm:ss') : nowStr()
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.notifier || !form.notifyTime || !form.sender || !form.sendTime) {
    ElMessage.warning('请填写通知人、通知时间、发送人、发送时间')
    return
  }
  submitting.value = true
  try {
    const payload = {
      notifier: form.notifier,
      notifyTime: form.notifyTime,
      sender: form.sender,
      sendTime: form.sendTime,
      remark: form.remark,
    }
    if (submitMode.value === 'notify') {
      await notifyCritical(currentId.value, payload)
      ElMessage.success('已通知临床')
    } else {
      await updateCriticalRecord(currentId.value, payload)
      ElMessage.success('已保存修改')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row: CriticalRow) {
  try {
    await ElMessageBox.confirm(
      `确认取消该危急值的通知 [${row.testItemName}: ${row.resultValue}]?`,
      '取消通知',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '取消' }
    )
    await cancelNotifyCritical(row.resultId ?? row.id)
    ElMessage.success('已取消通知')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.critical-page {
  padding: 16px;

  .filter-card {
    margin-bottom: 16px;

    .filter-bar {
      display: flex;
      align-items: center;
      gap: 12px;

      .filter-item {
        display: flex;
        align-items: center;
        gap: 8px;

        .label {
          color: var(--el-text-color-regular);
          font-size: 14px;
          white-space: nowrap;
        }
      }
    }
  }

  .table-card {
    .critical-value {
      color: var(--el-color-danger);
      font-weight: bold;
      font-size: 15px;
    }

    .critical-range {
      color: var(--el-color-danger);
    }
  }

  :deep(.pending-row) {
    background-color: var(--el-color-danger-light-9) !important;

    &:hover > td {
      background-color: var(--el-color-danger-light-7) !important;
    }
  }
}
</style>