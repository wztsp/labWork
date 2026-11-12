<template>
  <div class="purchase-order-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="订单号">
          <el-input
            v-model="searchForm.orderNo"
            placeholder="请输入订单号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, key) in ORDER_STATUS_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleCreateFromApply">从申请建单</el-button>
          <el-button type="success" plain @click="handleOpenMerge">汇总生成订单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="orderNo" label="订单号" width="170" />
        <el-table-column prop="applyNo" label="来源申请" width="170" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }">
            {{ row.totalAmount != null ? `¥${Number(row.totalAmount).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" size="small">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="approvalStatusType(row.approvalStatus)" size="small">
              {{ approvalStatusText(row.approvalStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.auditStatus === 1 ? 'success' : 'info'" size="small">
              {{ row.auditStatus === 1 ? '已审核' : '未审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" align="center" />
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.approvalStatus === 0 && row.status !== 4" type="success" link size="small" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.approvalStatus === 0 && row.status !== 4" type="danger" link size="small" @click="handleRejectOrder(row)">驳回</el-button>
            <el-button v-if="row.status === 0 && row.auditStatus !== 1" type="warning" link size="small" @click="handleAudit(row)">审核</el-button>
            <el-button v-if="row.auditStatus === 1" type="info" link size="small" @click="handleUnaudit(row)">取消审核</el-button>
            <el-button v-if="row.status === 0" type="success" link size="small" @click="handlePlace(row)">下单</el-button>
            <el-button v-if="row.status !== 4" type="warning" link size="small" @click="handlePushThirdParty(row)">推送卫材</el-button>
            <el-button v-if="row.status === 0" type="danger" link size="small" @click="handleCancel(row)">取消</el-button>
            <el-button type="primary" link size="small" @click="handlePrint(row)">打印</el-button>
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

    <!-- 从申请建单弹窗 -->
    <el-dialog v-model="createDialogVisible" title="从采购申请创建订单" width="980px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="来源申请" prop="applyId">
              <el-select
                v-model="createForm.applyId"
                filterable
                placeholder="选择已批准的采购申请"
                style="width: 100%"
                @change="loadApplyDetail"
              >
                <el-option
                  v-for="a in approvedApplies"
                  :key="a.id!"
                  :label="`${a.applyNo} (${a.applyDeptName || '-'})`"
                  :value="a.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="createForm.supplierId" filterable placeholder="选择供应商" style="width: 100%">
                <el-option
                  v-for="s in selectableSuppliers"
                  :key="s.id!"
                  :label="s.supplierName"
                  :value="s.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望到货日期">
              <el-date-picker
                v-model="createForm.expectDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-header">
          <span class="items-title">订购明细</span>
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
        </div>
        <el-table :data="createForm.items" border style="width: 100%" size="small">
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="reagentName" label="试剂名称" min-width="180">
            <template #default="{ row }">{{ row.reagentName || '-' }}</template>
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
          <el-table-column label="成交单价" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <div class="total-amount">
          合计金额：<span class="total-value">¥{{ createTotalAmount.toFixed(2) }}</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateSave">创建订单</el-button>
      </template>
    </el-dialog>

    <!-- 汇总生成订单 -->
    <el-dialog v-model="mergeDialogVisible" title="汇总生成订单" width="1020px" @closed="resetMerge">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="按供应商汇总"
        description="勾选已批准的采购申请加入清单，并为每条申请指定供应商；提交时同一供应商的申请合并为一张订单。"
        style="margin-bottom: 12px"
      />
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="期望到货">
          <el-date-picker
            v-model="mergeForm.expectDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="mergeForm.remark" placeholder="合并备注" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleAddMergeRows">加入清单</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="applyTableRef"
        v-loading="mergeLoading"
        :data="approvedApplies"
        border
        stripe
        max-height="220"
        style="width: 100%"
        @selection-change="onApplySelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column prop="applyNo" label="申请单号" width="170" />
        <el-table-column prop="applyDeptName" label="申请科室" min-width="140" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="expectDate" label="期望日期" width="120" align="center" />
      </el-table>

      <div class="items-header" style="margin-top: 16px">
        <span class="items-title">待合并清单</span>
      </div>
      <el-table :data="mergeRows" border stripe size="small" style="width: 100%">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="applyNo" label="申请单号" width="170" />
        <el-table-column prop="applyDeptName" label="申请科室" min-width="140" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="供应商（分组依据）" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.supplierId" placeholder="请选择供应商" filterable size="small" style="width: 100%">
              <el-option v-for="s in selectableSuppliers" :key="s.id!" :label="s.supplierName" :value="s.id!" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="mergeRows.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="mergeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="mergeSubmitting" @click="submitMerge">生成订单</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="采购订单详情" width="960px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="订单号">{{ detailData.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="orderStatusType(detailData.status)" size="small">{{ orderStatusText(detailData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源申请">{{ detailData.applyNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailData.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          {{ detailData.totalAmount != null ? `¥${Number(detailData.totalAmount).toFixed(2)}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="期望到货">{{ detailData.expectDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">
          <el-tag :type="approvalStatusType(detailData.approvalStatus)" size="small">
            {{ approvalStatusText(detailData.approvalStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批意见">{{ detailData.approvalOpinion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="detailData.auditStatus === 1 ? 'success' : 'info'" size="small">
            {{ detailData.auditStatus === 1 ? '已审核' : '未审核' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detailData.auditorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailData.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detailData.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="items-header" style="margin-top: 16px">
        <span class="items-title">订单明细</span>
      </div>
      <el-table :data="detailData.items || []" border stripe style="width: 100%" size="small">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" />
        <el-table-column prop="quantity" label="订购量" width="90" align="right" />
        <el-table-column prop="arrivedQty" label="已到货" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" plain @click="handlePrint(detailData)">打印订单</el-button>
        <el-button v-if="detailData.status === 0" type="success" @click="handlePlace(detailData)">下单</el-button>
        <el-button v-if="detailData.status !== 4" type="warning" @click="handlePushThirdParty(detailData)">推送卫材</el-button>
        <el-button v-if="detailData.status === 0" type="danger" @click="handleCancel(detailData)">取消</el-button>
      </template>
    </el-dialog>

    <!-- 推送三方卫材 -->
    <el-dialog v-model="pushDialogVisible" title="推送订单至卫材系统" width="460px">
      <el-form label-width="90px">
        <el-form-item label="订单号">{{ pushOrder?.orderNo }}</el-form-item>
        <el-form-item label="对接方">
          <el-select v-model="pushPartnerCode" placeholder="请选择对接方" style="width: 100%">
            <el-option
              v-for="item in thirdPartyConfigs"
              :key="item.partnerCode"
              :label="item.partnerName"
              :value="item.partnerCode"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="push-tip">推送成功后，对方返回的单号会回写到订单。</div>
      <template #footer>
        <el-button @click="pushDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pushLoading" @click="confirmPushOrder">推送</el-button>
      </template>
    </el-dialog>

    <PrintTemplateChooseDialog
      v-model:visible="printDialogVisible"
      biz-type="PURCHASE_ORDER"
      title="采购订单"
      :doc-ids="printIds"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import ReagentScanBar from '@/components/ReagentScanBar.vue'
import PrintTemplateChooseDialog from '@/components/PrintTemplateChooseDialog.vue'
import type { ScannedReagent } from '@/utils/reagentScan'
import {
  getPurchaseOrderPage,
  getPurchaseOrderDetail,
  createPurchaseOrder,
  mergePurchaseOrders,
  approvePurchaseOrder,
  rejectPurchaseOrder,
  auditPurchaseOrder,
  unauditPurchaseOrder,
  placePurchaseOrder,
  cancelPurchaseOrder,
  getPurchaseApplyPage,
  getPurchaseApplyDetail,
  getSelectableSuppliers,
  downloadPurchaseOrderTemplate,
  importPurchaseOrderItems,
  type ReagentPurchaseOrder,
  type ReagentPurchaseOrderItem,
  type ReagentPurchaseApply,
  type ApplyItemDTO,
  type OrderItemDTO,
  type OrderCreateDTO,
  type OrderQuery,
  type Supplier
} from '@/api/reagent'
import { getConfigList, pushThirdPartyOrder, type ThirdPartyConfig } from '@/api/reagent/thirdparty'

const userStore = useUserStore()

// 订单状态枚举语义映射：0待下单 1已下单 2部分到货 3已到货 4已取消
const ORDER_STATUS_MAP: Record<number, string> = {
  0: '待下单',
  1: '已下单',
  2: '部分到货',
  3: '已到货',
  4: '已取消'
}

function orderStatusText(status?: number): string {
  return status === undefined || status === null ? '-' : ORDER_STATUS_MAP[status] || `未知(${status})`
}
function orderStatusType(status?: number): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 3) return 'success'
  if (status === 1 || status === 2) return 'warning'
  if (status === 4) return 'info'
  return 'info'
}

// 审批状态：0待审批 1已通过 2已驳回
function approvalStatusText(status?: number): string {
  const map: Record<number, string> = { 0: '待审批', 1: '已通过', 2: '已驳回' }
  return map[status ?? 0] || '待审批'
}
function approvalStatusType(status?: number): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
function formatMoney(v?: number): string {
  return v != null ? `¥${Number(v).toFixed(2)}` : '-'
}

const loading = ref(false)
const tableData = ref<ReagentPurchaseOrder[]>([])
const searchForm = reactive({
  orderNo: '',
  status: undefined as number | undefined
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const params: OrderQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.orderNo) params.orderNo = searchForm.orderNo
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getPurchaseOrderPage(params)
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
  searchForm.orderNo = ''
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

// 从申请建单
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const approvedApplies = ref<ReagentPurchaseApply[]>([])
const selectableSuppliers = ref<Supplier[]>([])
const createForm = reactive<OrderCreateDTO>({ applyId: 0, supplierId: 0, items: [] })
const createRules: FormRules = {
  applyId: [{ required: true, message: '请选择来源申请', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

function resetCreateForm() {
  createForm.id = undefined
  createForm.applyId = 0
  createForm.supplierId = 0
  createForm.supplierName = ''
  createForm.expectDate = ''
  createForm.orderDate = dayjs().format('YYYY-MM-DD')
  createForm.remark = ''
  createForm.items = []
  createFormRef.value?.clearValidate?.()
}

async function handleCreateFromApply() {
  resetCreateForm()
  createDialogVisible.value = true
  try {
    const [applyRes, supplierRes] = await Promise.all([
      getPurchaseApplyPage({ pageNum: 1, pageSize: 50, status: 3 }),
      getSelectableSuppliers()
    ])
    approvedApplies.value = applyRes.records || []
    selectableSuppliers.value = supplierRes || []
  } catch (e) {
    approvedApplies.value = []
    selectableSuppliers.value = []
  }
}

async function loadApplyDetail(applyId: number) {
  if (!applyId) {
    createForm.items = []
    return
  }
  try {
    const detail = await getPurchaseApplyDetail(applyId)
    createForm.items = (detail.items || []).map((it) => ({
      applyItemId: it.id,
      reagentId: it.reagentId,
      reagentName: it.reagentName,
      quantity: it.quantity ?? 0,
      unit: it.unit ?? '',
      unitPrice: it.estimatedPrice ?? 0,
      amount: it.amount ?? 0
    }))
  } catch (e) {
    createForm.items = []
  }
}

const createTotalAmount = computed(() => {
  return (createForm.items || []).reduce((sum, item) => {
    const qty = Number(item.quantity || 0)
    const price = Number(item.unitPrice || 0)
    item.amount = Number((qty * price).toFixed(2))
    return sum + item.amount
  }, 0)
})

// 扫码追加明细：首次扫描新增一行，重复扫描同一试剂则数量+1
function onScanAppend(payload: ScannedReagent) {
  if (!createForm.items) createForm.items = []
  const exist = createForm.items.find((it) => it.reagentId === payload.reagentId)
  if (exist) {
    exist.quantity = (Number(exist.quantity || 0) || 0) + 1
    ElMessage.success(`${payload.reagentName} 数量 → ${exist.quantity}`)
    return
  }
  createForm.items.push({
    reagentId: payload.reagentId,
    reagentName: payload.reagentName,
    quantity: 1,
    unit: payload.unit || '',
    unitPrice: payload.price ?? 0,
    amount: 0
  })
  ElMessage.success(`已新增：${payload.reagentName}${payload.batchNo ? `（批号 ${payload.batchNo}）` : ''}`)
}

// ===== 导入明细 =====
const importLoading = ref(false)
function downloadTemplate() {
  downloadPurchaseOrderTemplate().then(() => {
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
  if (!createForm.items) createForm.items = []
  importLoading.value = true
  try {
    const res = await importPurchaseOrderItems(options.file)
    if (res.failed > 0) {
      const first = res.errors?.[0]
      ElMessage.warning(`明细导入完成：成功 ${res.success} 条，失败 ${res.failed} 条${first ? `（第${first.row}行：${first.reason}）` : ''}`)
    } else {
      ElMessage.success(`明细导入完成：成功 ${res.success} 条`)
    }
    const rows: OrderItemDTO[] = res.items || []
    for (const it of rows) {
      createForm.items.push({
        reagentId: it.reagentId!,
        reagentName: (it as any).reagentName || '',
        quantity: Number(it.quantity || 0),
        unit: it.unit || '',
        unitPrice: it.unitPrice ? Number(it.unitPrice) : 0,
        amount: 0
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

async function handleCreateSave() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!createForm.items || createForm.items.length === 0) {
      ElMessage.warning('请先选择来源申请以载入明细')
      return
    }
    createLoading.value = true
    try {
      const selected = selectableSuppliers.value.find((s) => s.id === createForm.supplierId)
      const payload: OrderCreateDTO = {
        applyId: createForm.applyId,
        supplierId: createForm.supplierId,
        supplierName: selected?.supplierName,
        expectDate: createForm.expectDate || undefined,
        orderDate: dayjs().format('YYYY-MM-DD'),
        operatorId: userStore.userId || undefined,
        operatorName: userStore.realName || userStore.username || undefined,
        remark: createForm.remark || undefined,
        items: createForm.items.map((item) => ({
          applyItemId: item.applyItemId,
          reagentId: item.reagentId,
          quantity: item.quantity,
          unit: item.unit,
          unitPrice: item.unitPrice
        }))
      }
      await createPurchaseOrder(payload)
      ElMessage.success('订单创建成功')
      createDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      createLoading.value = false
    }
  })
}

// ===== 汇总生成订单 =====
type MergeRow = ReagentPurchaseApply & { supplierId?: number }
const mergeDialogVisible = ref(false)
const mergeLoading = ref(false)
const mergeSubmitting = ref(false)
const applyTableRef = ref<any>()
const selectedApplies = ref<ReagentPurchaseApply[]>([])
const mergeRows = ref<MergeRow[]>([])
const mergeForm = reactive({ expectDate: '', remark: '' })

function resetMerge() {
  mergeRows.value = []
  selectedApplies.value = []
  mergeForm.expectDate = ''
  mergeForm.remark = ''
}

async function handleOpenMerge() {
  resetMerge()
  mergeDialogVisible.value = true
  mergeLoading.value = true
  try {
    const [applyRes, supplierRes] = await Promise.all([
      getPurchaseApplyPage({ pageNum: 1, pageSize: 200, status: 3 }),
      getSelectableSuppliers()
    ])
    approvedApplies.value = applyRes.records || []
    selectableSuppliers.value = supplierRes || []
  } catch (e) {
    approvedApplies.value = []
    selectableSuppliers.value = []
  } finally {
    mergeLoading.value = false
  }
}

function onApplySelectionChange(rows: ReagentPurchaseApply[]) {
  selectedApplies.value = rows
}

function handleAddMergeRows() {
  if (!selectedApplies.value.length) {
    ElMessage.warning('请先勾选采购申请')
    return
  }
  const exists = new Set(mergeRows.value.map((r) => r.id))
  const additions = selectedApplies.value
    .filter((a) => !exists.has(a.id))
    .map((a) => ({ ...a, supplierId: undefined as number | undefined }))
  if (!additions.length) {
    ElMessage.info('所选申请已在待合并清单中')
    return
  }
  mergeRows.value.push(...additions)
  applyTableRef.value?.clearSelection?.()
}

async function submitMerge() {
  if (!mergeRows.value.length) {
    ElMessage.warning('待合并清单为空')
    return
  }
  if (mergeRows.value.some((r) => !r.supplierId)) {
    ElMessage.warning('请为每条申请选择供应商')
    return
  }
  // 按供应商分组，同一供应商合并为一张订单
  const groups = new Map<number, MergeRow[]>()
  for (const row of mergeRows.value) {
    const key = row.supplierId!
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(row)
  }
  mergeSubmitting.value = true
  try {
    let success = 0
    for (const [supplierId, rows] of groups) {
      const supplierName = selectableSuppliers.value.find((s) => s.id === supplierId)?.supplierName
      await mergePurchaseOrders({
        applyIds: rows.map((r) => r.id!),
        supplierId,
        supplierName,
        expectDate: mergeForm.expectDate || undefined,
        operatorId: userStore.userId || undefined,
        operatorName: userStore.realName || userStore.username || undefined,
        remark: mergeForm.remark || undefined
      })
      success++
    }
    ElMessage.success(`已按供应商生成 ${success} 张订单`)
    mergeDialogVisible.value = false
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    mergeSubmitting.value = false
  }
}

// ===== 审批 / 审核 =====
async function handleApprove(row: ReagentPurchaseOrder) {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批意见（可留空）', '订单审批', {
      inputPlaceholder: '同意采购',
      confirmButtonText: '通过',
      cancelButtonText: '取消'
    })
    await approvePurchaseOrder(row.id!, value || '同意')
    ElMessage.success('审批通过')
    fetchList()
    if (detailDialogVisible.value && detailData.value.id === row.id) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleRejectOrder(row: ReagentPurchaseOrder) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '订单驳回', {
      inputPlaceholder: '请输入驳回原因',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputValidator: (val) => (val?.trim() ? true : '驳回原因不能为空')
    })
    await rejectPurchaseOrder(row.id!, value)
    ElMessage.success('已驳回')
    fetchList()
    if (detailDialogVisible.value && detailData.value.id === row.id) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleAudit(row: ReagentPurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认审核订单 "${row.orderNo}"？`, '审核确认', { type: 'warning' })
    await auditPurchaseOrder(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('审核通过')
    fetchList()
    if (detailDialogVisible.value && detailData.value.id === row.id) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleUnaudit(row: ReagentPurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认取消订单 "${row.orderNo}" 的审核？`, '取消审核', { type: 'warning' })
    await unauditPurchaseOrder(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('已取消审核')
    fetchList()
    if (detailDialogVisible.value && detailData.value.id === row.id) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

// ===== 打印订单（走后台打印模板） =====
const printDialogVisible = ref(false)
const printIds = ref<number[]>([])

function handlePrint(row: ReagentPurchaseOrder) {
  if (!row.id) return
  printIds.value = [row.id]
  printDialogVisible.value = true
}

async function handlePlace(row: ReagentPurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认对订单 "${row.orderNo}" 下单吗?`, '下单确认', { type: 'warning' })
    await placePurchaseOrder(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('已下单')
    fetchList()
    if (detailDialogVisible.value && detailData.value.id === row.id) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

async function handleCancel(row: ReagentPurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认取消订单 "${row.orderNo}" 吗? 取消后释放预算占用。`, '取消确认', { type: 'warning' })
    await cancelPurchaseOrder(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('已取消')
    fetchList()
    if (detailDialogVisible.value && detailData.value.id === row.id) loadDetail(row.id!)
  } catch (e) {
    // 取消或失败
  }
}

// 详情
const detailDialogVisible = ref(false)
const detailData = ref<ReagentPurchaseOrder>({})

async function loadDetail(id: number) {
  try {
    detailData.value = await getPurchaseOrderDetail(id)
  } catch (e) {
    detailData.value = {}
  }
}

async function handleDetail(row: ReagentPurchaseOrder) {
  detailDialogVisible.value = true
  await loadDetail(row.id!)
}

// 推送三方卫材
const pushDialogVisible = ref(false)
const pushLoading = ref(false)
const pushOrder = ref<ReagentPurchaseOrder>({})
const pushPartnerCode = ref('')
const thirdPartyConfigs = ref<ThirdPartyConfig[]>([])

async function handlePushThirdParty(row: ReagentPurchaseOrder) {
  try {
    const list = await getConfigList()
    thirdPartyConfigs.value = list.filter(item => item.enabled === 1)
  } catch (e) {
    return
  }
  if (!thirdPartyConfigs.value.length) {
    ElMessage.warning('请先在「试剂管理 - 三方卫材对接」中配置并启用对接方')
    return
  }
  pushOrder.value = row
  pushPartnerCode.value = thirdPartyConfigs.value[0].partnerCode
  pushDialogVisible.value = true
}

async function confirmPushOrder() {
  if (!pushPartnerCode.value) {
    ElMessage.warning('请选择对接方')
    return
  }
  pushLoading.value = true
  try {
    const res = await pushThirdPartyOrder(
      pushOrder.value.id!,
      pushPartnerCode.value,
      userStore.realName || userStore.username
    )
    ElMessage.success(res.remoteOrderNo ? `推送成功，对方单号 ${res.remoteOrderNo}` : '推送成功')
    pushDialogVisible.value = false
    fetchList()
    if (detailDialogVisible.value && pushOrder.value.id === detailData.value.id) {
      loadDetail(pushOrder.value.id!)
    }
  } finally {
    pushLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.push-tip {
  font-size: 12px;
  color: #909399;
  padding-left: 90px;
}
.purchase-order-page {
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
