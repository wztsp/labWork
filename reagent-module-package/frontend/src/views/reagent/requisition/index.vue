<template>
  <div class="requisition-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="单据号">
          <el-input
            v-model="searchForm.reqNo"
            placeholder="请输入单据号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="单据类型">
          <el-select v-model="searchForm.reqType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, key) in REQ_TYPE_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, key) in REQ_STATUS_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新建业务单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reqNo" label="单据号" width="170" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="reqTypeTag(row.reqType)" size="small">{{ reqTypeText(row.reqType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="科室" width="120" />
        <el-table-column prop="applyUserName" label="申请人" width="100" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            {{ row.totalAmount != null ? `¥${Number(row.totalAmount).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="reqStatusType(row.status)" size="small">{{ reqStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" type="success" link size="small" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 1" type="success" link size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 2" type="success" link size="small" @click="handleExecute(row)">执行</el-button>
            <el-button v-if="[0, 1, 2, 4].includes(row.status!)" type="info" link size="small" @click="handleCancel(row)">作废</el-button>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑业务单' : '新建业务单'" width="960px" @closed="resetForm">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="单据类型" prop="reqType">
              <el-select v-model="editForm.reqType" placeholder="请选择类型" style="width: 100%">
                <el-option v-for="(label, key) in REQ_TYPE_MAP" :key="key" :label="label" :value="Number(key)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科室">
              <el-input v-model="editForm.deptName" placeholder="请输入科室名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="源仓库">
              <el-select
                v-model="editForm.fromWarehouseId"
                filterable
                clearable
                placeholder="留空为默认库房"
                style="width: 100%"
              >
                <el-option
                  v-for="w in warehouseOptions"
                  :key="w.id!"
                  :label="w.whName"
                  :value="w.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标仓库">
              <el-select
                v-model="editForm.toWarehouseId"
                filterable
                clearable
                placeholder="留空为默认库房"
                style="width: 100%"
              >
                <el-option
                  v-for="w in warehouseOptions"
                  :key="w.id!"
                  :label="w.whName"
                  :value="w.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-header">
          <span class="items-title">明细</span>
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
          <el-table-column label="试剂" min-width="200">
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
          <el-table-column label="批次库存" width="180">
            <template #default="{ row }">
              <el-select
                v-model="row.stockId"
                filterable
                clearable
                placeholder="出库类必填"
                style="width: 100%"
                size="small"
              >
                <el-option
                  v-for="s in stockOptions"
                  :key="s.id!"
                  :label="`${s.reagentName || '-'} (${s.batchNo || '无批号'})`"
                  :value="s.id!"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="批号" width="110">
            <template #default="{ row }">
              <el-input v-model="row.lotNo" placeholder="批号" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="效期" width="130">
            <template #default="{ row }">
              <el-input v-model="row.expiryDate" placeholder="效期" size="small" />
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
          <el-table-column label="操作" width="80" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="业务单详情" width="920px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="单据号">{{ detailData.reqNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="reqTypeTag(detailData.reqType)" size="small">{{ reqTypeText(detailData.reqType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="reqStatusType(detailData.status)" size="small">{{ reqStatusText(detailData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="科室">{{ detailData.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detailData.applyUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="危险品">{{ detailData.hazardFlag === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detailData.executorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detailData.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="items-header" style="margin-top: 16px">
        <span class="items-title">明细</span>
      </div>
      <el-table :data="detailData.items || []" border stripe style="width: 100%" size="small">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160">
          <template #default="{ row }">{{ row.reagentName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" width="130" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button v-if="detailData.status === 0" type="success" @click="handleSubmit(detailData)">提交</el-button>
        <el-button v-if="detailData.status === 2" type="success" @click="handleExecute(detailData)">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import ReagentScanBar from '@/components/ReagentScanBar.vue'
import type { ScannedReagent } from '@/utils/reagentScan'
import {
  getRequisitionPage,
  getRequisitionDetail,
  createRequisition,
  submitRequisition,
  approveRequisition,
  rejectRequisition,
  executeRequisition,
  cancelRequisition,
  getReagentPage,
  getStockPage,
  getWarehouseTree,
  downloadRequisitionTemplate,
  importRequisitionItems,
  type Reagent,
  type ReagentStock,
  type ReagentWarehouse,
  type ReagentRequisition,
  type RequisitionCreateDTO,
  type RequisitionItemDTO,
  type RequisitionQuery,
  type ApprovalDTO
} from '@/api/reagent'

const userStore = useUserStore()

// 单据类型枚举语义映射：1领用 2退库 3报废 4调拨 5借用 6归还
const REQ_TYPE_MAP: Record<number, string> = {
  1: '领用',
  2: '退库',
  3: '报废',
  4: '调拨',
  5: '借用',
  6: '归还'
}
// 单据状态：0草稿 1待审 2已审 3已执行 4已驳回 5已作废
const REQ_STATUS_MAP: Record<number, string> = {
  0: '草稿',
  1: '待审',
  2: '已审',
  3: '已执行',
  4: '已驳回',
  5: '已作废'
}

function reqTypeText(t?: number): string {
  return t === undefined || t === null ? '-' : REQ_TYPE_MAP[t] || `未知(${t})`
}
function reqTypeTag(t?: number): 'info' | 'warning' | 'success' | 'danger' {
  if (t === 1 || t === 4) return 'warning'
  if (t === 2 || t === 6) return 'success'
  if (t === 3) return 'danger'
  return 'info'
}
function reqStatusText(s?: number): string {
  return s === undefined || s === null ? '-' : REQ_STATUS_MAP[s] || `未知(${s})`
}
function reqStatusType(s?: number): 'info' | 'warning' | 'success' | 'danger' {
  if (s === 3) return 'success'
  if (s === 1 || s === 2) return 'warning'
  if (s === 4 || s === 5) return 'info'
  return 'info'
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
function formatMoney(v?: number): string {
  return v != null ? `¥${Number(v).toFixed(2)}` : '-'
}

const loading = ref(false)
const tableData = ref<ReagentRequisition[]>([])
const searchForm = reactive({
  reqNo: '',
  reqType: undefined as number | undefined,
  status: undefined as number | undefined
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const reagentOptions = ref<Reagent[]>([])
async function loadReagents() {
  try {
    const res = await getReagentPage({ pageNum: 1, pageSize: 100 })
    reagentOptions.value = res.records || []
  } catch (e) {
    reagentOptions.value = []
  }
}

// 仓库 / 库存批次选项（供表单选择器使用，显示名称不显示 id）
const warehouseOptions = ref<ReagentWarehouse[]>([])
const stockOptions = ref<ReagentStock[]>([])
async function loadWarehouses() {
  try {
    const tree = await getWarehouseTree()
    const flat: ReagentWarehouse[] = []
    const walk = (nodes: ReagentWarehouse[]) => {
      nodes.forEach((n) => {
        flat.push(n)
        if (n.children && n.children.length > 0) walk(n.children)
      })
    }
    walk(tree || [])
    warehouseOptions.value = flat
  } catch (e) {
    warehouseOptions.value = []
  }
}
async function loadStocks() {
  try {
    const res = await getStockPage({ pageNum: 1, pageSize: 100 })
    stockOptions.value = res.records || []
  } catch (e) {
    stockOptions.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: RequisitionQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.reqNo) params.reqNo = searchForm.reqNo
    if (searchForm.reqType !== undefined) params.reqType = searchForm.reqType
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getRequisitionPage(params)
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
  searchForm.reqNo = ''
  searchForm.reqType = undefined
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
const editForm = reactive<RequisitionCreateDTO>({ reqType: 1, items: [] })
const formRules: FormRules = {
  reqType: [{ required: true, message: '请选择单据类型', trigger: 'change' }]
}

function resetForm() {
  editForm.id = undefined
  editForm.reqType = 1
  editForm.deptId = undefined
  editForm.deptName = ''
  editForm.applyUserId = undefined
  editForm.applyUserName = ''
  editForm.fromWarehouseId = 0
  editForm.toWarehouseId = 0
  editForm.remark = ''
  editForm.items = []
  formRef.value?.clearValidate?.()
}

function handleAdd() {
  resetForm()
  formDialogVisible.value = true
}

function handleEdit(row: ReagentRequisition) {
  resetForm()
  editForm.id = row.id
  editForm.reqType = row.reqType ?? 1
  editForm.deptId = row.deptId
  editForm.deptName = row.deptName ?? ''
  editForm.applyUserId = row.applyUserId ?? (userStore.userId || undefined)
  editForm.applyUserName = row.applyUserName ?? (userStore.realName || '')
  editForm.fromWarehouseId = row.fromWarehouseId ?? 0
  editForm.toWarehouseId = row.toWarehouseId ?? 0
  editForm.remark = row.remark ?? ''
  editForm.items = (row.items || []).map((it) => ({
    id: it.id,
    reagentId: it.reagentId,
    stockId: it.stockId,
    lotNo: it.lotNo,
    expiryDate: it.expiryDate,
    quantity: it.quantity ?? 0,
    unit: it.unit ?? '',
    remark: it.remark
  }))
  formDialogVisible.value = true
}

function addItem() {
  editForm.items!.push({ reagentId: 0, stockId: 0, lotNo: '', expiryDate: '', quantity: 0, unit: '', remark: '' })
}

// 扫码追加明细：首次扫描新增一行，重复扫描同一试剂则数量+1
function onScanAppend(payload: ScannedReagent) {
  if (!editForm.items) editForm.items = []
  // 保证下拉能显示试剂名称
  if (!reagentOptions.value.some((r) => r.id === payload.reagentId)) {
    reagentOptions.value.push({ id: payload.reagentId, reagentCode: '', reagentName: payload.reagentName, unit: payload.unit, status: 1 } as Reagent)
  }
  if (payload.fromStock && payload.stockId && !stockOptions.value.some((s) => s.id === payload.stockId)) {
    stockOptions.value.push({
      id: payload.stockId,
      reagentId: payload.reagentId,
      reagentName: payload.reagentName,
      batchNo: payload.batchNo,
      lotNo: payload.lotNo,
      unit: payload.unit
    } as ReagentStock)
  }
  const exist = editForm.items.find((it) => it.reagentId === payload.reagentId)
  if (exist) {
    exist.quantity = (Number(exist.quantity || 0) || 0) + 1
    ElMessage.success(`${payload.reagentName} 数量 → ${exist.quantity}`)
    return
  }
  editForm.items.push({
    reagentId: payload.reagentId,
    stockId: payload.fromStock && payload.stockId ? payload.stockId || 0 : 0,
    lotNo: payload.fromStock ? payload.lotNo || '' : '',
    expiryDate: payload.fromStock ? payload.expiryDate || '' : '',
    quantity: 1,
    unit: payload.unit || '',
    remark: ''
  })
  ElMessage.success(`已新增：${payload.reagentName}${payload.batchNo ? `（批号 ${payload.batchNo}）` : ''}`)
}

function removeItem(index: number) {
  editForm.items!.splice(index, 1)
}

// ===== 导入明细 =====
const importLoading = ref(false)
function downloadTemplate() {
  downloadRequisitionTemplate().then(() => {
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
    const res = await importRequisitionItems(options.file)
    if (res.failed > 0) {
      const first = res.errors?.[0]
      ElMessage.warning(`明细导入完成：成功 ${res.success} 条，失败 ${res.failed} 条${first ? `（第${first.row}行：${first.reason}）` : ''}`)
    } else {
      ElMessage.success(`明细导入完成：成功 ${res.success} 条`)
    }
    const rows: RequisitionItemDTO[] = res.items || []
    for (const it of rows) {
      if (!reagentOptions.value.some((r) => r.id === it.reagentId)) {
        reagentOptions.value.push({ id: it.reagentId!, reagentCode: (it as any).reagentCode, reagentName: (it as any).reagentName || '', specification: (it as any).specification, unit: it.unit } as unknown as Reagent)
      }
      editForm.items.push({
        reagentId: it.reagentId!,
        lotNo: (it as any).lotNo || '',
        expiryDate: (it as any).expiryDate || '',
        quantity: Number(it.quantity || 0),
        unit: it.unit || '',
        remark: (it as any).remark || ''
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

function onReagentChange(row: RequisitionItemDTO, reagentId: number) {
  const reagent = reagentOptions.value.find((r) => r.id === reagentId)
  if (reagent) {
    row.unit = reagent.unit ?? ''
  }
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!editForm.items || editForm.items.length === 0) {
      ElMessage.warning('请至少添加一条明细')
      return
    }
    if (editForm.items.some((it) => !it.reagentId || it.quantity <= 0)) {
      ElMessage.warning('明细中试剂与数量不能为空且数量必须大于 0')
      return
    }
    // 出库类单据(1领用/3报废/5借用)必须选择批次库存
    const isOutbound = [1, 3, 5].includes(editForm.reqType)
    if (isOutbound && editForm.items.some((it) => !it.stockId)) {
      ElMessage.warning('领用/报废/借用单据的明细批次库存不能为空')
      return
    }
    formLoading.value = true
    try {
      const payload: RequisitionCreateDTO = {
        id: editForm.id,
        reqType: editForm.reqType,
        deptId: editForm.deptId || undefined,
        deptName: editForm.deptName || undefined,
        applyUserId: userStore.userId || undefined,
        applyUserName: userStore.realName || userStore.username || undefined,
        fromWarehouseId: editForm.fromWarehouseId || undefined,
        toWarehouseId: editForm.toWarehouseId || undefined,
        remark: editForm.remark || undefined,
        items: editForm.items.map((item) => ({
          id: item.id,
          reagentId: item.reagentId,
          stockId: item.stockId || undefined,
          quantity: item.quantity,
          unit: item.unit,
          remark: item.remark
        }))
      }
      await createRequisition(payload)
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

async function handleSubmit(row: ReagentRequisition) {
  try {
    await ElMessageBox.confirm(`确认提交业务单 "${row.reqNo}" 吗?`, '提交确认', { type: 'warning' })
    await submitRequisition(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('提交成功')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleApprove(row: ReagentRequisition) {
  try {
    await ElMessageBox.confirm(`确认通过业务单 "${row.reqNo}" 吗?`, '审批确认', { type: 'warning' })
    const payload: ApprovalDTO = {
      applyId: row.id,
      action: 1,
      approverId: userStore.userId,
      approverName: userStore.realName || userStore.username,
      opinion: '同意'
    }
    await approveRequisition(row.id!, payload)
    ElMessage.success('审批通过')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleReject(row: ReagentRequisition) {
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
    await rejectRequisition(row.id!, payload)
    ElMessage.success('已驳回')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleExecute(row: ReagentRequisition) {
  try {
    await ElMessageBox.confirm(`确认执行业务单 "${row.reqNo}" 吗? 执行后将实际变动库存并写入流水。`, '执行确认', { type: 'warning' })
    await executeRequisition(row.id!, {
      operatorId: userStore.userId || undefined,
      operatorName: userStore.realName || userStore.username || undefined,
      deptId: row.deptId || undefined,
      deptName: row.deptName || undefined
    })
    ElMessage.success('执行成功')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleCancel(row: ReagentRequisition) {
  try {
    await ElMessageBox.confirm(`确认作废业务单 "${row.reqNo}" 吗?`, '作废确认', { type: 'warning' })
    await cancelRequisition(row.id!, userStore.realName || userStore.username)
    ElMessage.success('已作废')
    fetchList()
    if (detailDialogVisible.value) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

// 详情
const detailDialogVisible = ref(false)
const detailData = ref<ReagentRequisition>({})

async function loadDetail(id: number) {
  try {
    detailData.value = await getRequisitionDetail(id)
  } catch (e) {
    detailData.value = {}
  }
}

async function handleDetail(row: ReagentRequisition) {
  detailDialogVisible.value = true
  await loadDetail(row.id!)
}

onMounted(() => {
  fetchList()
  loadReagents()
  loadWarehouses()
  loadStocks()
})
</script>

<style scoped lang="scss">
.requisition-page {
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
}
</style>
