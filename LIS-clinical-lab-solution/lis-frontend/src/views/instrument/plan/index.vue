<template>
  <div class="plan-page">
    <el-tabs v-model="activeTab" type="card">
      <!-- ============ 保养计划 ============ -->
      <el-tab-pane label="保养计划" name="plan">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="仪器">
              <el-select v-model="planSearch.instrumentId" placeholder="全部仪器" clearable filterable style="width: 220px" @change="fetchPlans">
                <el-option v-for="item in instrumentList" :key="item.id" :label="`${item.instrumentCode} - ${item.instrumentName}`" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" plain @click="fetchPlans">查询</el-button>
              <el-button type="primary" @click="handleAddPlan">新增计划</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="planLoading" :data="planData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="仪器" min-width="160">
              <template #default="{ row }">{{ getInstrumentName(row.instrumentId) }}</template>
            </el-table-column>
            <el-table-column label="计划类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small">{{ PLAN_TYPE_MAP[row.planType] || row.planType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="周期" width="100" align="center">
              <template #default="{ row }">{{ CYCLE_TYPE_MAP[row.cycleType] || row.cycleType }}</template>
            </el-table-column>
            <el-table-column label="首次日期" width="120" align="center">
              <template #default="{ row }">{{ formatDate(row.firstDate) }}</template>
            </el-table-column>
            <el-table-column label="下次日期" width="120" align="center">
              <template #default="{ row }">
                <span :class="{ 'warn-text': isExpiringSoon(row.nextDate) }">{{ formatDate(row.nextDate) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditPlan(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeletePlan(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ 待执行任务 ============ -->
      <el-tab-pane label="任务列表" name="task">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="仪器">
              <el-select v-model="taskSearch.instrumentId" placeholder="全部仪器" clearable filterable style="width: 220px" @change="fetchTasks">
                <el-option v-for="item in instrumentList" :key="item.id" :label="`${item.instrumentCode} - ${item.instrumentName}`" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="taskSearch.status" placeholder="全部" clearable style="width: 140px" @change="fetchTasks">
                <el-option label="待执行" :value="0" />
                <el-option label="已完成" :value="1" />
                <el-option label="逾期" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" plain @click="fetchTasks">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="taskLoading" :data="taskData" border stripe>
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="仪器" min-width="160">
              <template #default="{ row }">{{ getInstrumentName(row.instrumentId) }}</template>
            </el-table-column>
            <el-table-column label="计划类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small">{{ PLAN_TYPE_MAP[row.planType] || row.planType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计划日期" width="120" align="center">
              <template #default="{ row }">{{ formatDate(row.scheduledDate) }}</template>
            </el-table-column>
            <el-table-column label="完成日期" width="120" align="center">
              <template #default="{ row }">{{ formatDate(row.finishDate) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="taskStatusTag(row.status)" size="small">{{ taskStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="执行内容" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" type="success" link size="small" @click="handleExecute(row)">执行</el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 计划新增/编辑弹窗 -->
    <el-dialog v-model="planDialogVisible" :title="planForm.id ? '编辑保养计划' : '新增保养计划'" width="600px">
      <el-form ref="planFormRef" :model="planForm" :rules="planRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="仪器" prop="instrumentId">
              <el-select v-model="planForm.instrumentId" placeholder="请选择仪器" filterable style="width: 100%">
                <el-option v-for="item in instrumentList" :key="item.id" :label="`${item.instrumentCode} - ${item.instrumentName}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划类型" prop="planType">
              <el-select v-model="planForm.planType" placeholder="请选择" style="width: 100%">
                <el-option v-for="(label, key) in PLAN_TYPE_MAP" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="周期" prop="cycleType">
              <el-select v-model="planForm.cycleType" placeholder="请选择" style="width: 100%">
                <el-option v-for="(label, key) in CYCLE_TYPE_MAP" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="首次日期">
              <el-date-picker v-model="planForm.firstDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次日期">
              <el-date-picker v-model="planForm.nextDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="planForm.status" style="width: 100%">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="planForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="planFormLoading" @click="handleSavePlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行任务弹窗 -->
    <el-dialog v-model="taskDialogVisible" title="执行保养任务" width="560px">
      <el-form ref="taskFormRef" :model="taskForm" label-width="100px">
        <el-form-item label="仪器">
          <el-input :value="getInstrumentName(taskForm.instrumentId)" disabled />
        </el-form-item>
        <el-form-item label="计划类型">
          <el-input :value="PLAN_TYPE_MAP[taskForm.planType] || taskForm.planType" disabled />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-input :value="formatDate(taskForm.scheduledDate)" disabled />
        </el-form-item>
        <el-form-item label="执行内容">
          <el-input v-model="taskForm.content" type="textarea" :rows="3" placeholder="请填写执行内容" />
        </el-form-item>
        <el-form-item label="执行人">
          <el-input v-model="taskForm.operatorName" placeholder="执行人姓名" />
        </el-form-item>
        <el-form-item label="费用">
          <el-input-number v-model="taskForm.cost" :min="0" :precision="2" placeholder="费用" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="taskFormLoading" @click="handleFinishTask">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import { getAllInstruments, type Instrument } from '@/api/instrument'
import {
  getMaintenancePlansByInstrument,
  getDueMaintenancePlans,
  saveMaintenancePlan,
  deleteMaintenancePlan,
  getMaintenanceTasks,
  finishMaintenanceTask,
  PLAN_TYPE_MAP,
  CYCLE_TYPE_MAP,
  type MaintenancePlan,
  type MaintenanceTask
} from '@/api/instrument'

const userStore = useUserStore()
const activeTab = ref('plan')

const instrumentList = ref<Instrument[]>([])

function getInstrumentName(instrumentId?: number): string {
  if (!instrumentId) return '-'
  const ins = instrumentList.value.find((i) => i.id === instrumentId)
  return ins ? ins.instrumentName : '-'
}

function formatDate(date?: string): string {
  return date ? dayjs(date).format('YYYY-MM-DD') : '-'
}

function isExpiringSoon(date?: string): boolean {
  if (!date) return false
  const diff = dayjs(date).diff(dayjs(), 'day')
  return diff <= 7 && diff >= 0
}

function taskStatusText(status?: number): string {
  if (status === 0) return '待执行'
  if (status === 1) return '已完成'
  if (status === 2) return '逾期'
  return '-'
}
function taskStatusTag(status?: number): string {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

// ============ 计划 ============
const planLoading = ref(false)
const planData = ref<MaintenancePlan[]>([])
const planSearch = reactive({ instrumentId: undefined as number | undefined })

async function fetchPlans() {
  planLoading.value = true
  try {
    if (planSearch.instrumentId) {
      planData.value = await getMaintenancePlansByInstrument(planSearch.instrumentId)
    } else {
      // 未选仪器时拉取到期计划
      planData.value = await getDueMaintenancePlans()
    }
  } catch (e) {
    planData.value = []
  } finally {
    planLoading.value = false
  }
}

const planDialogVisible = ref(false)
const planFormLoading = ref(false)
const planFormRef = ref<FormInstance>()
const planForm = reactive<MaintenancePlan>({
  id: 0,
  instrumentId: undefined,
  planType: 'PM',
  cycleType: 'MONTHLY',
  firstDate: dayjs().format('YYYY-MM-DD'),
  nextDate: dayjs().format('YYYY-MM-DD'),
  status: 1,
  remark: ''
})
const planRules: FormRules = {
  instrumentId: [{ required: true, message: '请选择仪器', trigger: 'change' }],
  planType: [{ required: true, message: '请选择计划类型', trigger: 'change' }],
  cycleType: [{ required: true, message: '请选择周期', trigger: 'change' }]
}

function handleAddPlan() {
  Object.assign(planForm, {
    id: 0,
    instrumentId: planSearch.instrumentId,
    planType: 'PM',
    cycleType: 'MONTHLY',
    firstDate: dayjs().format('YYYY-MM-DD'),
    nextDate: dayjs().format('YYYY-MM-DD'),
    status: 1,
    remark: ''
  })
  planDialogVisible.value = true
}

function handleEditPlan(row: MaintenancePlan) {
  Object.assign(planForm, {
    id: row.id,
    instrumentId: row.instrumentId,
    planType: row.planType ?? 'PM',
    cycleType: row.cycleType ?? 'MONTHLY',
    firstDate: row.firstDate,
    nextDate: row.nextDate,
    status: row.status ?? 1,
    remark: row.remark ?? ''
  })
  planDialogVisible.value = true
}

async function handleSavePlan() {
  if (!planFormRef.value) return
  await planFormRef.value.validate(async (valid) => {
    if (!valid) return
    planFormLoading.value = true
    try {
      await saveMaintenancePlan({ ...planForm })
      ElMessage.success('保存成功')
      planDialogVisible.value = false
      fetchPlans()
    } catch (e) {
      // ignore
    } finally {
      planFormLoading.value = false
    }
  })
}

async function handleDeletePlan(row: MaintenancePlan) {
  try {
    await ElMessageBox.confirm('确认删除该保养计划吗?', '提示', { type: 'warning' })
    await deleteMaintenancePlan(row.id!)
    ElMessage.success('删除成功')
    fetchPlans()
  } catch (e) {
    // 取消或失败
  }
}

// ============ 任务 ============
const taskLoading = ref(false)
const taskData = ref<MaintenanceTask[]>([])
const taskSearch = reactive({
  instrumentId: undefined as number | undefined,
  status: undefined as number | undefined
})

async function fetchTasks() {
  taskLoading.value = true
  try {
    taskData.value = await getMaintenanceTasks(taskSearch.instrumentId, taskSearch.status)
  } catch (e) {
    taskData.value = []
  } finally {
    taskLoading.value = false
  }
}

const taskDialogVisible = ref(false)
const taskFormLoading = ref(false)
const taskFormRef = ref<FormInstance>()
const taskForm = reactive<MaintenanceTask>({
  id: 0,
  instrumentId: undefined,
  planType: '',
  scheduledDate: '',
  content: '',
  operatorName: '',
  cost: 0
})

function handleExecute(row: MaintenanceTask) {
  Object.assign(taskForm, {
    id: row.id,
    instrumentId: row.instrumentId,
    planType: row.planType,
    scheduledDate: row.scheduledDate,
    content: '',
    operatorName: userStore.realName || '',
    cost: 0
  })
  taskDialogVisible.value = true
}

async function handleFinishTask() {
  taskFormLoading.value = true
  try {
    await finishMaintenanceTask(taskForm.id!, {
      operatorId: userStore.userId || undefined,
      content: taskForm.content,
      cost: taskForm.cost
    })
    ElMessage.success('任务已完成')
    taskDialogVisible.value = false
    fetchTasks()
  } catch (e) {
    // ignore
  } finally {
    taskFormLoading.value = false
  }
}

async function loadInstruments() {
  try {
    instrumentList.value = await getAllInstruments()
  } catch (e) {
    instrumentList.value = []
  }
}

onMounted(async () => {
  await loadInstruments()
  fetchPlans()
  fetchTasks()
})
</script>

<style scoped lang="scss">
.plan-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .warn-text {
      color: #e6a23c;
      font-weight: 600;
    }
  }
}
</style>
