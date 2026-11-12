<template>
  <div class="budget-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="年度">
          <el-input-number v-model="searchForm.budgetYear" :min="2000" :max="2100" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item label="预算编号">
          <el-input
            v-model="searchForm.budgetNo"
            placeholder="请输入编号"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="仅看预警">
          <el-switch v-model="searchForm.onlyWarning" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">创建预算</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="budgetNo" label="预算编号" width="150" />
        <el-table-column prop="budgetYear" label="年度" width="80" align="center" />
        <el-table-column prop="deptName" label="科室" width="120" />
        <el-table-column prop="categoryName" label="类别" width="120">
          <template #default="{ row }">{{ row.categoryName || '不限类别' }}</template>
        </el-table-column>
        <el-table-column label="总额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="已占用" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.frozenAmount) }}</template>
        </el-table-column>
        <el-table-column label="已实扣" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.usedAmount) }}</template>
        </el-table-column>
        <el-table-column label="可用" width="110" align="right">
          <template #default="{ row }">
            <span :class="availableColor(row)">{{ formatMoney(availableAmount(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="占用率" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="usageTag(row)" size="small">{{ usageRatio(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button type="warning" link size="small" @click="handleAdjust(row, 4)">调增</el-button>
            <el-button type="danger" link size="small" @click="handleAdjust(row, 5)">调减</el-button>
            <el-button type="success" link size="small" @click="handleReconcile(row)">对账</el-button>
            <el-button type="info" link size="small" @click="handleJudge(row)">判定</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 创建预算弹窗 -->
    <el-dialog v-model="createDialogVisible" title="创建预算" width="560px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="预算年度" prop="budgetYear">
          <el-input-number v-model="createForm.budgetYear" :min="2000" :max="2100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="科室" prop="deptId">
          <el-select v-model="createForm.deptId" placeholder="请选择科室" filterable style="width: 100%" @change="handleDeptChange">
            <el-option v-for="d in deptList" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别名称">
          <el-input v-model="createForm.categoryName" placeholder="留空为不限类别" />
        </el-form-item>
        <el-form-item label="预算总额" prop="totalAmount">
          <el-input-number v-model="createForm.totalAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预警阈值">
          <el-input-number v-model="createForm.warnRatio" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="createForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateSave">创建</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="预算详情" width="600px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="预算编号">{{ detailData.budgetNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年度">{{ detailData.budgetYear ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="科室">{{ detailData.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类别">{{ detailData.categoryName || '不限类别' }}</el-descriptions-item>
        <el-descriptions-item label="预算总额">{{ formatMoney(detailData.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已占用">{{ formatMoney(detailData.frozenAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已实扣">{{ formatMoney(detailData.usedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="可用额度">{{ formatMoney(availableAmount(detailData)) }}</el-descriptions-item>
        <el-descriptions-item label="预警阈值">{{ detailData.warnRatio != null ? `${(Number(detailData.warnRatio) * 100).toFixed(0)}%` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailData.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 调增/调减弹窗 -->
    <el-dialog v-model="adjustDialogVisible" :title="adjustForm.changeType === 4 ? '预算调增' : '预算调减'" width="460px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="110px">
        <el-form-item label="预算">
          <span>{{ adjustTarget?.budgetNo }} ({{ adjustTarget?.deptName }})</span>
        </el-form-item>
        <el-form-item label="当前总额">
          <span>{{ formatMoney(adjustTarget?.totalAmount) }}</span>
        </el-form-item>
        <el-form-item label="调整金额" prop="amount">
          <el-input-number v-model="adjustForm.amount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustLoading" @click="handleAdjustSave">确认</el-button>
      </template>
    </el-dialog>

    <!-- 对账结果弹窗 -->
    <el-dialog v-model="reconcileDialogVisible" title="预算对账结果" width="600px">
      <template v-if="reconcileData">
        <el-alert
          :title="reconcileData.consistent ? '对账一致：流水推算值与主表余额一致' : '对账不一致：存在主表与流水不一致，请立即排查! ' + (reconcileData.message || '')"
          :type="reconcileData.consistent ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="主表占用">{{ formatMoney(reconcileData.masterFrozen) }}</el-descriptions-item>
          <el-descriptions-item label="流水推算占用">{{ formatMoney(reconcileData.recordFrozen) }}</el-descriptions-item>
          <el-descriptions-item label="占用差额">{{ formatMoney(reconcileData.frozenDiff) }}</el-descriptions-item>
          <el-descriptions-item label="实扣差额">{{ formatMoney(reconcileData.usedDiff) }}</el-descriptions-item>
          <el-descriptions-item label="主表实扣">{{ formatMoney(reconcileData.masterUsed) }}</el-descriptions-item>
          <el-descriptions-item label="流水推算实扣">{{ formatMoney(reconcileData.recordUsed) }}</el-descriptions-item>
          <el-descriptions-item label="参与流水条数">{{ reconcileData.recordCount ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="reconcileDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 预算判定弹窗 -->
    <el-dialog v-model="judgeDialogVisible" title="预算占用判定" width="460px">
      <el-form label-width="110px">
        <el-form-item label="预算">
          <span>{{ judgeTarget?.budgetNo }} ({{ judgeTarget?.deptName }})</span>
        </el-form-item>
        <el-form-item label="拟占用金额">
          <el-input-number v-model="judgeAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="判定结果">
          <el-tag v-if="judgeResult" :type="judgeVerdictTag(judgeResult.code)" size="large">
            {{ judgeVerdictText(judgeResult.code) }}
          </el-tag>
          <span v-else>-</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="judgeDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="judgeLoading" @click="handleJudgeRun">判定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import {
  getBudgetPage,
  getBudgetDetail,
  reconcileBudget,
  createBudget,
  adjustBudget,
  judgeBudget,
  getReagentDeptOptions,
  type ReagentBudget,
  type BudgetQuery,
  type BudgetReconcileVO,
  type BudgetVerdictVO
} from '@/api/reagent'

// 科室下拉(用于创建预算时提供 deptId, 后端要求科室必填)
const deptList = ref<{ id: number; deptName: string }[]>([])

function flattenDepts(list: any[]): { id: number; deptName: string }[] {
  const out: { id: number; deptName: string }[] = []
  for (const item of list || []) {
    out.push({ id: item.id, deptName: item.deptName })
    if (item.children && item.children.length) out.push(...flattenDepts(item.children))
  }
  return out
}

async function loadDepts() {
  try {
    const res: any = await getReagentDeptOptions()
    deptList.value = flattenDepts(res || [])
  } catch (e) {
    deptList.value = []
  }
}

function handleDeptChange(deptId: number) {
  const d = deptList.value.find((i) => i.id === deptId)
  createForm.deptName = d ? d.deptName : ''
}

// 预算判定三态：PASS 通过 / WARN 预警 / EXCEED 超预算
const VERDICT_MAP: Record<string, string> = {
  PASS: '通过',
  WARN: '预警',
  EXCEED: '超预算'
}
function judgeVerdictText(code?: string): string {
  return code ? VERDICT_MAP[code] || code : '-'
}
function judgeVerdictTag(code?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (code === 'PASS') return 'success'
  if (code === 'WARN') return 'warning'
  if (code === 'EXCEED') return 'danger'
  return 'info'
}

function formatMoney(v?: number): string {
  return v != null ? `¥${Number(v).toFixed(2)}` : '-'
}
function availableAmount(row: ReagentBudget): number {
  return Number(row.totalAmount || 0) - Number(row.frozenAmount || 0) - Number(row.usedAmount || 0)
}
function usageRatio(row: ReagentBudget): string {
  const total = Number(row.totalAmount || 0)
  if (total <= 0) return '-'
  const used = ((Number(row.frozenAmount || 0) + Number(row.usedAmount || 0)) / total) * 100
  return `${used.toFixed(1)}%`
}
function usageTag(row: ReagentBudget): 'success' | 'warning' | 'danger' {
  const total = Number(row.totalAmount || 0)
  if (total <= 0) return 'danger'
  const ratio = (Number(row.frozenAmount || 0) + Number(row.usedAmount || 0)) / total
  const warn = Number(row.warnRatio || 0.8)
  // 超预算(>=100%)优先显示危险, 否则按预警阈值显示预警
  if (ratio >= 1) return 'danger'
  if (ratio >= warn) return 'warning'
  return 'success'
}
function availableColor(row: ReagentBudget): string {
  return availableAmount(row) < 0 ? 'text-danger' : ''
}

const loading = ref(false)
const tableData = ref<ReagentBudget[]>([])
const searchForm = reactive({
  budgetYear: dayjs().year(),
  budgetNo: '',
  status: undefined as number | undefined,
  onlyWarning: false
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const params: BudgetQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.budgetYear) params.budgetYear = searchForm.budgetYear
    if (searchForm.budgetNo) params.budgetNo = searchForm.budgetNo
    if (searchForm.status !== undefined) params.status = searchForm.status
    if (searchForm.onlyWarning) params.onlyWarning = true
    const res = await getBudgetPage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}
function handleReset() {
  searchForm.budgetYear = dayjs().year()
  searchForm.budgetNo = ''
  searchForm.status = undefined
  searchForm.onlyWarning = false
  handleSearch()
}
function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}
function handlePageChange(page: number) {
  pagination.pageNum = page
  fetchList()
}

// 创建
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<ReagentBudget>({
  budgetYear: dayjs().year(),
  deptId: undefined,
  deptName: '',
  categoryName: '',
  totalAmount: 0,
  warnRatio: 0.8,
  status: 1,
  remark: ''
})
const createRules: FormRules = {
  budgetYear: [{ required: true, message: '请输入预算年度', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择科室', trigger: 'change' }],
  totalAmount: [{ required: true, message: '请输入预算总额', trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(createForm, {
    budgetYear: dayjs().year(),
    deptId: undefined,
    deptName: '',
    categoryName: '',
    totalAmount: 0,
    warnRatio: 0.8,
    status: 1,
    remark: ''
  })
  createDialogVisible.value = true
}

async function handleCreateSave() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    createLoading.value = true
    try {
      await createBudget({ ...createForm })
      ElMessage.success('预算创建成功')
      createDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      createLoading.value = false
    }
  })
}

// 详情
const detailDialogVisible = ref(false)
const detailData = ref<ReagentBudget>({})

async function handleDetail(row: ReagentBudget) {
  try {
    detailData.value = await getBudgetDetail(row.id!)
    detailDialogVisible.value = true
  } catch (e) {
    // ignore
  }
}

// 调增/调减
const adjustDialogVisible = ref(false)
const adjustLoading = ref(false)
const adjustFormRef = ref<FormInstance>()
const adjustTarget = ref<ReagentBudget | null>(null)
const adjustForm = reactive({ changeType: 4, amount: 0 })
const adjustRules: FormRules = {
  amount: [{ required: true, message: '请输入调整金额', trigger: 'blur' }]
}

function handleAdjust(row: ReagentBudget, changeType: number) {
  adjustTarget.value = row
  adjustForm.changeType = changeType
  adjustForm.amount = 0
  adjustDialogVisible.value = true
}

async function handleAdjustSave() {
  if (!adjustFormRef.value) return
  await adjustFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!adjustTarget.value?.id) return
    adjustLoading.value = true
    try {
      await adjustBudget(adjustTarget.value.id, adjustForm.changeType, adjustForm.amount)
      ElMessage.success(adjustForm.changeType === 4 ? '调增成功' : '调减成功')
      adjustDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      adjustLoading.value = false
    }
  })
}

// 对账
const reconcileDialogVisible = ref(false)
const reconcileData = ref<BudgetReconcileVO | null>(null)

async function handleReconcile(row: ReagentBudget) {
  try {
    reconcileData.value = await reconcileBudget(row.id!)
    reconcileDialogVisible.value = true
  } catch (e) {
    // ignore
  }
}

// 判定
const judgeDialogVisible = ref(false)
const judgeLoading = ref(false)
const judgeTarget = ref<ReagentBudget | null>(null)
const judgeAmount = ref(0)
const judgeResult = ref<BudgetVerdictVO | null>(null)

function handleJudge(row: ReagentBudget) {
  judgeTarget.value = row
  judgeAmount.value = 0
  judgeResult.value = null
  judgeDialogVisible.value = true
}

async function handleJudgeRun() {
  if (!judgeTarget.value?.id) return
  judgeLoading.value = true
  try {
    judgeResult.value = await judgeBudget(judgeTarget.value.id, judgeAmount.value)
  } catch (e) {
    // ignore
  } finally {
    judgeLoading.value = false
  }
}

onMounted(() => {
  fetchList()
  loadDepts()
})
</script>

<style scoped lang="scss">
.budget-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .text-danger {
    color: #f56c6c;
    font-weight: 600;
  }
}
</style>
