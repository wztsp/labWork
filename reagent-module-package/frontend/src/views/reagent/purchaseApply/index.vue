<template>
  <div class="purchase-apply-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="申请单号">
          <el-input
            v-model="searchForm.applyNo"
            placeholder="请输入申请单号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, key) in APPLY_STATUS_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新建申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="applyNo" label="申请单号" width="170" />
        <el-table-column prop="applyDeptName" label="申请科室" width="120" />
        <el-table-column prop="applyUserName" label="申请人" width="100" />
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }">
            {{ row.totalAmount != null ? `¥${Number(row.totalAmount).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="applyStatusType(row.status)" size="small">{{ applyStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button v-if="isEditableStatus(row.status)" type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="isSubmittableStatus(row.status)" type="success" link size="small" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="isApprovingStatus(row.status)" type="success" link size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="isApprovingStatus(row.status)" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="isCancellableStatus(row.status)" type="info" link size="small" @click="handleCancel(row)">取消</el-button>
            <el-button v-if="row.status === 5" type="warning" link size="small" @click="handleUnfreeze(row)">解冻</el-button>
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

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑采购申请' : '新建采购申请'" width="960px" @closed="resetForm">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="申请科室">
              <el-select
                v-model="editForm.applyDeptId"
                filterable
                placeholder="请选择申请科室"
                style="width: 100%"
                clearable
                @change="onDeptChange"
              >
                <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算年度">
              <el-input-number v-model="editForm.budgetYear" :min="2000" :max="2100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否加急">
              <el-switch v-model="editForm.urgentFlag" :active-value="1" :inactive-value="0" active-text="紧急" inactive-text="普通" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望到货日期">
              <el-date-picker
                v-model="editForm.expectDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-header">
          <span class="items-title">申请明细</span>
          <ReagentScanBar class="scan-bar" @scanned="onScanAppend" />
          <span style="flex: 1"></span>
          <el-button size="small" plain @click="downloadTemplate">下载模板</el-button>
          <el-upload
            :show-file-list="false"
            :before-upload="beforeImport"
            :http-request="handleImportDetail"
            accept=".xlsx,.xls,.csv,.txt"
            style="display: inline-block; margin-left: 8px"
          >
            <el-button size="small" plain type="primary" :loading="importLoading">导入明细</el-button>
          </el-upload>
          <el-button type="primary" plain size="small" @click="addItem" style="margin-left: 8px">添加明细</el-button>
        </div>
        <el-table :data="editForm.items" border style="width: 100%" size="small">
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column label="试剂" min-width="220">
            <template #default="{ row }">
              <el-select
                v-model="row.reagentId"
                filterable
                placeholder="选择试剂"
                style="width: 100%"
                size="small"
                @change="(val: number) => onReagentChange(row, val)"
              >
                <el-option v-for="r in reagentOptions" :key="r.id" :label="`${r.reagentName} (${r.reagentCode})`" :value="r.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" controls-position="right" style="width: 100%" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="90">
            <template #default="{ row }">
              <el-input v-model="row.unit" placeholder="单位" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="预估单价" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.estimatedPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="total-amount">
          合计金额：<span class="total-value">¥{{ totalAmount.toFixed(2) }}</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="采购申请详情" width="960px">
      <template v-if="detailData.apply">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请单号">{{ detailData.apply.applyNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="applyStatusType(detailData.apply.status)" size="small">{{ applyStatusText(detailData.apply.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请科室">{{ detailData.apply.applyDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detailData.apply.applyUserName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总金额">
            {{ detailData.apply.totalAmount != null ? `¥${Number(detailData.apply.totalAmount).toFixed(2)}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="加急">{{ detailData.apply.urgentFlag === 1 ? '紧急' : '普通' }}</el-descriptions-item>
          <el-descriptions-item label="期望到货日期">{{ detailData.apply.expectDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ detailData.currentNodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="预算总额">{{ detailData.budgetTotalAmount != null ? `¥${Number(detailData.budgetTotalAmount).toFixed(2)}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="预算可用">{{ detailData.budgetAvailable != null ? `¥${Number(detailData.budgetAvailable).toFixed(2)}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detailData.apply.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailData.apply.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <div class="items-header" style="margin-top: 16px">
        <span class="items-title">申请明细</span>
      </div>
      <el-table :data="detailData.items || []" border stripe style="width: 100%" size="small">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160">
          <template #default="{ row }">{{ row.reagentName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="预估单价" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.estimatedPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>

      <div v-if="detailData.approvals && detailData.approvals.length > 0" class="items-header" style="margin-top: 16px">
        <span class="items-title">审批流水</span>
      </div>
      <el-table v-if="detailData.approvals && detailData.approvals.length > 0" :data="detailData.approvals" border stripe size="small" style="margin-top: 8px">
        <el-table-column prop="nodeName" label="节点" width="120" />
        <el-table-column label="动作" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.action === 1 ? 'success' : 'danger'" size="small">{{ row.action === 1 ? '通过' : '驳回' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="110" />
        <el-table-column prop="opinion" label="意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.approveTime) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button v-if="detailData.editable" type="warning" @click="handleEditFromDetail">编辑</el-button>
        <el-button
          v-if="detailData.allowedActions?.includes('SUBMIT')"
          type="success"
          @click="handleSubmitAction(detailData.apply!)"
        >提交</el-button>
        <el-button
          v-if="detailData.allowedActions?.includes('APPROVE')"
          type="success"
          @click="handleApprove(detailData.apply!)"
        >通过</el-button>
        <el-button
          v-if="detailData.allowedActions?.includes('REJECT')"
          type="danger"
          @click="handleReject(detailData.apply!)"
        >驳回</el-button>
        <el-button
          v-if="detailData.allowedActions?.includes('CANCEL')"
          type="info"
          @click="handleCancel(detailData.apply!)"
        >取消</el-button>
        <el-button
          v-if="detailData.allowedActions?.includes('UNFREEZE')"
          type="warning"
          @click="handleUnfreeze(detailData.apply!)"
        >解冻</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import ReagentScanBar from '@/components/ReagentScanBar.vue'
import type { ScannedReagent } from '@/utils/reagentScan'
import { getDepartmentTree } from '@/api/system/department'
import {
  getPurchaseApplyPage,
  getPurchaseApplyDetail,
  savePurchaseApply,
  submitPurchaseApply,
  approvePurchaseApply,
  rejectPurchaseApply,
  cancelPurchaseApply,
  unfreezePurchaseApply,
  getReagentPage,
  downloadPurchaseApplyTemplate,
  importPurchaseApplyItems,
  type Reagent,
  type ReagentPurchaseApply,
  type ApplyItemDTO,
  type ApplyCreateDTO,
  type ApplyDetailVO,
  type ApplyQuery,
  type ApprovalDTO,
  type SubmitResultVO
} from '@/api/reagent'

const userStore = useUserStore()

// 采购申请状态枚举语义映射：0草稿 1待审批 2审批中 3已批准 4已驳回 5已冻结 6已作废
const APPLY_STATUS_MAP: Record<number, string> = {
  0: '草稿',
  1: '待审批',
  2: '审批中',
  3: '已批准',
  4: '已驳回',
  5: '已冻结',
  6: '已作废'
}

function applyStatusText(status?: number): string {
  return status === undefined || status === null ? '-' : APPLY_STATUS_MAP[status] || `未知(${status})`
}
function applyStatusType(status?: number): 'info' | 'warning' | 'success' | 'danger' | 'primary' {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 5) return 'warning'
  if (status === 6) return 'info'
  if (status === 1 || status === 2) return 'warning'
  return 'info'
}
// 可编辑：草稿 / 已驳回
function isEditableStatus(status?: number): boolean {
  return status === 0 || status === 4
}
// 可提交：草稿 / 已驳回
function isSubmittableStatus(status?: number): boolean {
  return status === 0 || status === 4
}
// 审批中：待审批 / 审批中
function isApprovingStatus(status?: number): boolean {
  return status === 1 || status === 2
}
// 可取消：草稿 / 待审批 / 审批中 / 已驳回
function isCancellableStatus(status?: number): boolean {
  return status === 0 || status === 1 || status === 2 || status === 4
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
function formatMoney(v?: number): string {
  return v != null ? `¥${Number(v).toFixed(2)}` : '-'
}

const loading = ref(false)
const tableData = ref<ReagentPurchaseApply[]>([])
const searchForm = reactive({
  applyNo: '',
  status: undefined as number | undefined
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

// 试剂选项（供明细行选择，filterable 本地过滤）
const reagentOptions = ref<Reagent[]>([])
async function loadReagents() {
  try {
    const res = await getReagentPage({ pageNum: 1, pageSize: 100 })
    reagentOptions.value = res.records || []
  } catch (e) {
    reagentOptions.value = []
  }
}

// 科室字典（供"申请科室"下拉选择，联动回填 applyDeptId，避免前端只填名称导致后端 applyDeptId 为空校验失败）
const deptOptions = ref<{ id: number; name: string; children?: any[] }[]>([])
function flattenDepts(nodes: any[]): { id: number; name: string; children?: any[] }[] {
  const flat: { id: number; name: string }[] = []
  const walk = (list: any[]) => {
    (list || []).forEach((n) => {
      flat.push({ id: n.id, name: n.deptName ?? n.name ?? '' })
      if (n.children?.length) walk(n.children)
    })
  }
  walk(nodes)
  return flat
}
async function loadDepts() {
  try {
    const res = await getDepartmentTree()
    deptOptions.value = flattenDepts(Array.isArray(res) ? res : (res?.data || res?.list || []))
  } catch (e) {
    deptOptions.value = []
  }
}
function onDeptChange(deptId?: number) {
  const d = deptOptions.value.find((o) => o.id === deptId)
  editForm.applyDeptName = d?.name || ''
}

async function fetchList() {
  loading.value = true
  try {
    const params: ApplyQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.applyNo) params.applyNo = searchForm.applyNo
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getPurchaseApplyPage(params)
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
  searchForm.applyNo = ''
  searchForm.status = undefined
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

// 新建/编辑
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<ApplyCreateDTO>({ items: [] })
const formRules: FormRules = {
  applyDeptId: [{ required: true, message: '请选择申请科室', trigger: 'change' }]
}

function resetForm() {
  editForm.id = undefined
  editForm.applyDeptId = undefined
  editForm.applyDeptName = ''
  editForm.applyUserId = undefined
  editForm.applyUserName = ''
  editForm.budgetYear = dayjs().year()
  editForm.urgentFlag = 0
  editForm.expectDate = ''
  editForm.remark = ''
  editForm.items = []
  formRef.value?.clearValidate?.()
}

function handleAdd() {
  resetForm()
  formDialogVisible.value = true
}

function handleEdit(row: ReagentPurchaseApply) {
  resetForm()
  editForm.id = row.id
  editForm.applyDeptId = row.applyDeptId
  editForm.applyDeptName = row.applyDeptName ?? ''
  editForm.applyUserId = row.applyUserId ?? (userStore.userId || undefined)
  editForm.applyUserName = row.applyUserName ?? (userStore.realName || '')
  editForm.budgetYear = row.budgetYear ?? dayjs().year()
  editForm.urgentFlag = row.urgentFlag ?? 0
  editForm.expectDate = row.expectDate ?? ''
  editForm.remark = row.remark ?? ''
  editForm.items = (row.items || []).map((it) => ({
    id: it.id,
    reagentId: it.reagentId,
    quantity: it.quantity ?? 0,
    unit: it.unit ?? '',
    estimatedPrice: it.estimatedPrice ?? 0,
    remark: it.remark
  }))
  formDialogVisible.value = true
}

function handleEditFromDetail() {
  if (!detailData.value.apply) return
  detailDialogVisible.value = false
  handleEdit(detailData.value.apply)
}

function addItem() {
  editForm.items!.push({ reagentId: 0, quantity: 0, unit: '', estimatedPrice: 0, remark: '' })
}

function removeItem(index: number) {
  editForm.items!.splice(index, 1)
}

// 扫码追加明细：首次扫描新增一行，重复扫描同一试剂则数量+1
function onScanAppend(payload: ScannedReagent) {
  if (!editForm.items) editForm.items = []
  if (!reagentOptions.value.some((r) => r.id === payload.reagentId)) {
    reagentOptions.value.push({ id: payload.reagentId, reagentCode: '', reagentName: payload.reagentName, unit: payload.unit, price: payload.price, status: 1 } as Reagent)
  }
  const exist = editForm.items.find((it) => it.reagentId === payload.reagentId)
  if (exist) {
    exist.quantity = (Number(exist.quantity || 0) || 0) + 1
    ElMessage.success(`${payload.reagentName} 数量 → ${exist.quantity}`)
    return
  }
  editForm.items!.push({
    reagentId: payload.reagentId,
    quantity: 1,
    unit: payload.unit || '',
    estimatedPrice: payload.price,
    remark: ''
  })
  ElMessage.success(`已新增：${payload.reagentName}${payload.batchNo ? `（批号 ${payload.batchNo}）` : ''}`)
}

// ===== 导入明细 =====
const importLoading = ref(false)
function downloadTemplate() {
  downloadPurchaseApplyTemplate().then(() => {
    ElMessage.success('模板下载成功')
  }).catch(() => ElMessage.error('模板下载失败'))
}
function beforeImport(file: File): boolean {
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!['xlsx', 'xls', 'csv', 'txt'].includes(ext)) {
    ElMessage.error('仅支持 .xlsx/.xls/.csv/.txt 文件')
    return false
  }
  return true
}
async function handleImportDetail(options: any) {
  if (!editForm.items) editForm.items = []
  importLoading.value = true
  try {
    const res = await importPurchaseApplyItems(options.file)
    if (res.failed > 0) {
      const first = res.errors?.[0]
      ElMessage.warning(`明细导入完成：成功 ${res.success} 条，失败 ${res.failed} 条${first ? `（第${first.row}行：${first.reason}）` : ''}`)
    } else {
      ElMessage.success(`明细导入完成：成功 ${res.success} 条`)
    }
    const rows: ApplyItemDTO[] = res.items || []
    for (const it of rows) {
      if (!reagentOptions.value.some((r) => r.id === it.reagentId)) {
        reagentOptions.value.push({ id: it.reagentId!, name: (it as any).reagentName || '', reagentCode: (it as any).reagentCode, unit: it.unit, price: it.estimatedPrice } as unknown as Reagent)
      }
      editForm.items.push({
        reagentId: it.reagentId!,
        reagentName: (it as any).reagentName || '',
        quantity: Number(it.quantity || 0),
        unit: it.unit || '',
        estimatedPrice: it.estimatedPrice ? Number(it.estimatedPrice) : undefined,
        remark: (it as any).remark || ''
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

function onReagentChange(row: ApplyItemDTO, reagentId: number) {
  const reagent = reagentOptions.value.find((r) => r.id === reagentId)
  if (reagent) {
    row.unit = reagent.unit ?? ''
    if (reagent.price && !row.estimatedPrice) row.estimatedPrice = reagent.price
  }
}

const totalAmount = computed(() => {
  return (editForm.items || []).reduce((sum, item) => {
    const qty = Number(item.quantity || 0)
    const price = Number(item.estimatedPrice || 0)
    item.amount = Number((qty * price).toFixed(2))
    return sum + item.amount
  }, 0)
})

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!editForm.items || editForm.items.length === 0) {
      ElMessage.warning('请至少添加一条申请明细')
      return
    }
    if (editForm.items.some((it) => !it.reagentId || it.quantity <= 0)) {
      ElMessage.warning('明细中试剂与数量不能为空且数量必须大于 0')
      return
    }
    formLoading.value = true
    try {
      const payload: ApplyCreateDTO = {
        id: editForm.id,
        applyDeptId: editForm.applyDeptId || undefined,
        applyDeptName: editForm.applyDeptName || undefined,
        applyUserId: userStore.userId || undefined,
        applyUserName: userStore.realName || userStore.username || undefined,
        budgetYear: editForm.budgetYear,
        urgentFlag: editForm.urgentFlag ?? 0,
        expectDate: editForm.expectDate || undefined,
        remark: editForm.remark || undefined,
        items: editForm.items.map((item) => ({
          id: item.id,
          reagentId: item.reagentId,
          quantity: item.quantity,
          unit: item.unit,
          estimatedPrice: item.estimatedPrice,
          remark: item.remark
        }))
      }
      await savePurchaseApply(payload)
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

// 提交（处理 PASS / WARN / EXCEED 三种结局）
function handleSubmitResult(result: SubmitResultVO | void) {
  if (result && (result as SubmitResultVO).verdict === 'WARN') {
    ElMessage.warning((result as SubmitResultVO).message || '已提交审批，但预算占用已达预警线')
  } else if (result && (result as SubmitResultVO).overBudget) {
    const excess = (result as SubmitResultVO).excessAmount ?? 0
    ElMessageBox.alert(
      `单据已冻结。超出预算 ¥${Number(excess).toFixed(2)}，请联系财务调增预算后解冻。`,
      '预算不足',
      { type: 'warning' }
    ).catch(() => {})
  } else {
    ElMessage.success('提交成功')
  }
  fetchList()
}

async function handleSubmit(row: ReagentPurchaseApply) {
  try {
    await ElMessageBox.confirm(`确认提交申请单 "${row.applyNo}" 吗? 提交后将冻结预算并进入审批。`, '提交确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const res = await submitPurchaseApply(row.id!, userStore.userId, userStore.realName || userStore.username)
    handleSubmitResult(res)
  } catch (e) {
    // ignore
  }
}

async function handleSubmitAction(row: ReagentPurchaseApply) {
  await handleSubmit(row)
}

async function handleApprove(row: ReagentPurchaseApply) {
  try {
    await ElMessageBox.confirm(`确认通过申请单 "${row.applyNo}" 吗?`, '审批确认', { type: 'warning' })
    const payload: ApprovalDTO = {
      applyId: row.id,
      action: 1,
      approverId: userStore.userId,
      approverName: userStore.realName || userStore.username,
      opinion: '同意'
    }
    await approvePurchaseApply(row.id!, payload)
    ElMessage.success('审批通过')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleReject(row: ReagentPurchaseApply) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回意见', '驳回确认', {
      type: 'warning',
      inputPlaceholder: '请输入驳回意见',
      inputValidator: (val) => !!val?.trim() || '驳回意见不能为空'
    })
    const payload: ApprovalDTO = {
      applyId: row.id,
      action: 2,
      approverId: userStore.userId,
      approverName: userStore.realName || userStore.username,
      opinion: value
    }
    await rejectPurchaseApply(row.id!, payload)
    ElMessage.success('已驳回')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleCancel(row: ReagentPurchaseApply) {
  try {
    await ElMessageBox.confirm(`确认取消申请单 "${row.applyNo}" 吗? 取消后释放预算占用。`, '取消确认', { type: 'warning' })
    await cancelPurchaseApply(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('已取消')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleUnfreeze(row: ReagentPurchaseApply) {
  try {
    await ElMessageBox.confirm(`确认对申请单 "${row.applyNo}" 执行解冻重判吗?`, '解冻确认', { type: 'warning' })
    const res = await unfreezePurchaseApply(row.id!, userStore.userId, userStore.realName || userStore.username)
    handleSubmitResult(res)
  } catch (e) {
    // 取消或失败
  }
}

// 详情
const detailDialogVisible = ref(false)
const detailData = ref<ApplyDetailVO>({})

async function loadDetail(id: number) {
  try {
    detailData.value = await getPurchaseApplyDetail(id)
  } catch (e) {
    detailData.value = {}
  }
}

async function handleDetail(row: ReagentPurchaseApply) {
  detailDialogVisible.value = true
  await loadDetail(row.id!)
}

onMounted(() => {
  fetchList()
  loadReagents()
  loadDepts()
})
</script>

<style scoped lang="scss">
.purchase-apply-page {
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

  .items-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .items-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }

    .scan-bar {
      margin-left: 12px;
    }
  }

  .total-amount {
    margin-top: 12px;
    text-align: right;
    font-size: 15px;
    color: #303133;

    .total-value {
      color: #f56c6c;
      font-weight: 700;
      margin-left: 4px;
    }
  }
}
</style>
