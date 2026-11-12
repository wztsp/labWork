<template>
  <div class="reagent-inbound-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="入库单号">
          <el-input
            v-model="searchForm.inboundNo"
            placeholder="请输入入库单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="验收状态">
          <el-select v-model="searchForm.verifyStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="待验收" :value="0" />
            <el-option label="已验收" :value="1" />
            <el-option label="拒收" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新建入库单</el-button>
          <el-button type="success" plain @click="openScanDialog">扫码入库</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="inboundNo" label="入库单号" width="160" />
        <el-table-column prop="supplier" label="供应商" min-width="160" />
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }">
            {{ row.totalAmount != null ? `¥${Number(row.totalAmount).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="验收状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="verifyStatusType(row.verifyStatus)" size="small">
              {{ verifyStatusText(row.verifyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="verifierName" label="验收人" width="110" />
        <el-table-column label="验收时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.verifyTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">查看详情</el-button>
            <el-button type="primary" link size="small" @click="handlePrint(row)">打印</el-button>
            <el-button
              v-if="row.verifyStatus === 0"
              type="success"
              link
              size="small"
              :loading="verifyingIds.has(row.id!)"
              :disabled="verifyingIds.has(row.id!) || rejectingIds.has(row.id!)"
              @click="handleVerify(row)"
            >验收</el-button>
            <el-button
              v-if="row.verifyStatus === 0"
              type="danger"
              link
              size="small"
              :loading="rejectingIds.has(row.id!)"
              :disabled="verifyingIds.has(row.id!) || rejectingIds.has(row.id!)"
              @click="handleReject(row)"
            >拒收</el-button>
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

    <!-- 新建入库单弹窗 -->
    <el-dialog v-model="formDialogVisible" title="新建入库单" width="960px" @closed="resetForm">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplier">
              <el-select
                v-model="editForm.supplier"
                filterable
                clearable
                placeholder="请选择供应商"
                style="width: 100%"
              >
                <el-option v-for="s in supplierOptions" :key="s.supplierName" :label="s.supplierName" :value="s.supplierName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-header">
          <span class="items-title">入库明细</span>
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
          <el-table-column label="试剂" width="200">
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
          <el-table-column label="批号" width="140">
            <template #default="{ row }">
              <el-input v-model="row.lotNo" placeholder="批号" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="1" controls-position="right" style="width: 100%" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="90">
            <template #default="{ row }">
              <el-input v-model="row.unit" placeholder="单位" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="采购单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.purchasePrice" :min="0" :precision="2" controls-position="right" style="width: 100%" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">
              {{ row.amount != null ? `¥${row.amount.toFixed(2)}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="效期" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="存放位置" width="130">
            <template #default="{ row }">
              <el-input v-model="row.location" placeholder="位置" size="small" />
            </template>
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
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 扫码入库弹窗 -->
    <el-dialog v-model="scanDialogVisible" title="扫码入库" width="860px" @closed="resetScanForm">
      <el-tabs v-model="scanMode">
        <el-tab-pane label="规则扫码" name="rule">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="按前缀 + 起始序号 + 数量批量生成条码"
            description="序号按 4 位补零（如 PZ0001），生成后可预览并提交入库。"
            style="margin-bottom: 12px"
          />
          <el-form :inline="true" label-width="100px">
            <el-form-item label="条码前缀">
              <el-input v-model="scanForm.prefix" placeholder="如 PZ" style="width: 160px" />
            </el-form-item>
            <el-form-item label="起始序号">
              <el-input-number v-model="scanForm.startSeq" :min="1" :controls="false" style="width: 130px" />
            </el-form-item>
            <el-form-item label="数量">
              <el-input-number v-model="scanForm.count" :min="1" :max="500" style="width: 130px" />
            </el-form-item>
          </el-form>
          <div class="scan-preview">
            <span class="preview-label">条码预览（{{ ruleBarcodes.length }}）：</span>
            <el-tag v-for="code in ruleBarcodes" :key="code" size="small" class="code-tag">{{ code }}</el-tag>
          </div>
        </el-tab-pane>
        <el-tab-pane label="逐个扫码" name="single">
          <div class="scan-input-row">
            <el-input
              v-model="barcodeInput"
              placeholder="扫描或输入条码后回车"
              clearable
              style="width: 320px"
              @keyup.enter="addBarcode"
            />
            <el-button type="primary" plain @click="addBarcode">添加条码</el-button>
          </div>
          <div class="scan-preview">
            <span class="preview-label">已录入（{{ singleBarcodes.length }}）：</span>
            <el-tag
              v-for="(code, index) in singleBarcodes"
              :key="code"
              size="small"
              closable
              class="code-tag"
              @close="singleBarcodes.splice(index, 1)"
            >{{ code }}</el-tag>
          </div>
        </el-tab-pane>
        <el-tab-pane label="扫码选择" name="alias">
          <div class="scan-input-row">
            <el-input
              v-model="barcodeInput"
              placeholder="扫描或输入条码后回车，自动解析试剂"
              clearable
              style="width: 320px"
              @keyup.enter="addAliasCode"
            />
            <el-button type="primary" plain :loading="resolving" @click="addAliasCode">解析并添加</el-button>
          </div>
          <el-table :data="aliasRows" border size="small" style="width: 100%; margin-top: 12px">
            <el-table-column prop="code" label="扫描条码" width="180" />
            <el-table-column label="解析试剂" min-width="180">
              <template #default="{ row }">{{ row.resolved?.reagentName || '未匹配' }}</template>
            </el-table-column>
            <el-table-column label="批号" width="120">
              <template #default="{ row }">{{ row.resolved?.batchNo || row.resolved?.lotNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="aliasRows.splice($index, 1)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <el-divider content-position="left">入库信息</el-divider>
      <el-form :inline="true" label-width="90px">
        <el-form-item label="试剂">
          <el-select v-model="scanForm.reagentId" placeholder="扫码选择可自动带出" clearable filterable style="width: 240px">
            <el-option
              v-for="r in reagentOptions"
              :key="r.id"
              :label="`${r.reagentName}${r.reagentCode ? '（' + r.reagentCode + '）' : ''}`"
              :value="r.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="批号">
          <el-input v-model="scanForm.batchNo" placeholder="批号" style="width: 160px" />
        </el-form-item>
        <el-form-item label="效期">
          <el-date-picker v-model="scanForm.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="scanForm.quantity" :min="1" :controls="false" style="width: 120px" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="scanForm.unit" placeholder="单位" style="width: 100px" />
        </el-form-item>
        <el-form-item label="采购单价">
          <el-input-number v-model="scanForm.purchasePrice" :min="0" :precision="2" :controls="false" style="width: 120px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="scanForm.remark" placeholder="备注" style="width: 220px" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="scanDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="scanSubmitting" @click="submitScan">提交入库</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="入库单详情" width="900px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="入库单号">{{ detailData.inboundNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailData.supplier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          {{ detailData.totalAmount != null ? `¥${Number(detailData.totalAmount).toFixed(2)}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="验收状态">
          <el-tag :type="verifyStatusType(detailData.verifyStatus)" size="small">
            {{ verifyStatusText(detailData.verifyStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="验收人">{{ detailData.verifierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="验收时间">{{ formatTime(detailData.verifyTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detailData.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="items-header" style="margin-top: 16px">
        <span class="items-title">入库明细</span>
      </div>
      <el-table :data="detailData.items || []" border stripe style="width: 100%" size="small">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="140">
          <template #default="{ row }">{{ row.reagentName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" width="110" />
        <el-table-column label="条码" min-width="210">
          <template #default="{ row }">
            <span v-if="row.barcode" class="barcode-text" @click="copyBarcode(row.barcode!)" title="点击复制">
              {{ row.barcode }}
            </span>
            <el-tag v-else-if="detailData.verifyStatus === 0" type="info" size="small" effect="plain">验收后自动生成</el-tag>
            <span v-else class="barcode-missing">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="采购单价" width="100" align="right">
          <template #default="{ row }">
            {{ row.purchasePrice != null ? `¥${Number(row.purchasePrice).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            {{ row.amount != null ? `¥${Number(row.amount).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="expiryDate" label="效期" width="110" align="center" />
        <el-table-column prop="location" label="存放位置" width="110" />
      </el-table>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-tooltip content="打印设置（纸张尺寸、缩放比例等）">
          <el-button
            v-if="detailData.verifyStatus === 1 && printedBarcodes.length > 0"
            :icon="Setting"
            @click="openReagentPrintConfigDialog"
          >打印设置</el-button>
        </el-tooltip>
        <el-button
          v-if="detailData.verifyStatus === 1 && printedBarcodes.length > 0"
          type="primary"
          :icon="Printer"
          @click="printBarcodes"
        >打印条码（{{ printedBarcodes.length }}）</el-button>
      </template>
    </el-dialog>
    <PrintConfigDialog
      v-model:visible="reagentPrintConfigVisible"
      scene="reagent-barcode"
      @saved="onReagentPrintConfigSaved"
    />
    <PrintTemplateChooseDialog
      v-model:visible="docPrintVisible"
      biz-type="INBOUND"
      title="试剂入库单"
      :doc-ids="docPrintIds"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import {
  getInboundPage,
  getInboundDetail,
  createInbound,
  verifyInbound,
  rejectInbound,
  getReagentPage,
  getSelectableSuppliers,
  downloadInboundTemplate,
  importInboundItems,
  resolveInboundScanCode,
  scanInboundByRule,
  scanInboundBySingle,
  scanInboundByAlias,
  type Reagent,
  type ReagentInbound,
  type ReagentInboundItem,
  type ReagentInboundQuery,
  type Supplier,
  type InboundScanResolve
} from '@/api/reagent'
import { useUserStore } from '@/stores/user'
import ReagentScanBar from '@/components/ReagentScanBar.vue'
import type { ScannedReagent } from '@/utils/reagentScan'
import { openPrintWindow } from '@/utils/print'
import { escapeHtml } from '@/utils/sanitize'
import { printReagentBarcodeLabels } from '@/utils/reagentPrint'
import {
  getPrintConfig,
  type PrintConfig,
} from '@/utils/printConfig'
import { Setting, Printer } from '@element-plus/icons-vue'
import PrintConfigDialog from '@/components/PrintConfigDialog.vue'
import PrintTemplateChooseDialog from '@/components/PrintTemplateChooseDialog.vue'
const userStore = useUserStore()
/** 入库单打印（走后台打印模板） */
const docPrintVisible = ref(false)
const docPrintIds = ref<number[]>([])

function handlePrint(row: ReagentInbound) {
  if (!row.id) return
  docPrintIds.value = [row.id]
  docPrintVisible.value = true
}
const loading = ref(false)
const verifyingIds = ref<Set<number>>(new Set())
const rejectingIds = ref<Set<number>>(new Set())
const tableData = ref<ReagentInbound[]>([])
const searchForm = reactive({
  inboundNo: '',
  verifyStatus: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<ReagentInbound>({
  supplier: '',
  remark: '',
  items: []
})
const formRules: FormRules = {
  supplier: [{ required: true, message: '请输入供应商', trigger: 'blur' }]
}

const detailDialogVisible = ref(false)
const detailData = ref<ReagentInbound>({})

// 已生成条码的明细（用于批量打印）
const printedBarcodes = computed(() => (detailData.value.items || []).filter((it) => it.barcode))

// ========== 扫码入库 ==========
const scanDialogVisible = ref(false)
const scanMode = ref<'rule' | 'single' | 'alias'>('rule')
const scanSubmitting = ref(false)
const resolving = ref(false)
const barcodeInput = ref('')
const singleBarcodes = ref<string[]>([])
const aliasRows = ref<Array<{ code: string; resolved?: InboundScanResolve }>>([])
const scanForm = reactive({
  prefix: '',
  startSeq: 1,
  count: 1,
  reagentId: undefined as number | undefined,
  batchNo: '',
  expiryDate: '',
  quantity: 1,
  unit: '',
  purchasePrice: 0,
  remark: ''
})

/** 规则扫码条码预览：前缀 + 4 位序号 */
const ruleBarcodes = computed(() => {
  const prefix = scanForm.prefix.trim()
  if (!prefix) return []
  const count = Math.max(1, Number(scanForm.count) || 1)
  return Array.from({ length: count }, (_, i) => `${prefix}${String((scanForm.startSeq || 1) + i).padStart(4, '0')}`)
})

function openScanDialog() {
  resetScanForm()
  scanDialogVisible.value = true
}

function resetScanForm() {
  scanMode.value = 'rule'
  barcodeInput.value = ''
  singleBarcodes.value = []
  aliasRows.value = []
  scanForm.prefix = ''
  scanForm.startSeq = 1
  scanForm.count = 1
  scanForm.reagentId = undefined
  scanForm.batchNo = ''
  scanForm.expiryDate = ''
  scanForm.quantity = 1
  scanForm.unit = ''
  scanForm.purchasePrice = 0
  scanForm.remark = ''
}

function addBarcode() {
  const code = barcodeInput.value.trim()
  if (!code) return
  if (!singleBarcodes.value.includes(code)) {
    singleBarcodes.value.push(code)
  }
  barcodeInput.value = ''
}

async function addAliasCode() {
  const code = barcodeInput.value.trim()
  if (!code) return
  if (aliasRows.value.some((r) => r.code === code)) {
    ElMessage.info('该条码已添加')
    barcodeInput.value = ''
    return
  }
  resolving.value = true
  try {
    const resolved = await resolveInboundScanCode(code)
    aliasRows.value.push({ code, resolved })
    // 解析出试剂后自动带出入库信息
    if (resolved?.reagentId) {
      if (!scanForm.reagentId) scanForm.reagentId = resolved.reagentId
      if (!scanForm.batchNo && (resolved.batchNo || resolved.lotNo)) {
        scanForm.batchNo = resolved.batchNo || resolved.lotNo || ''
      }
      if (!scanForm.unit) {
        const reagent = reagentOptions.value.find((r) => r.id === resolved.reagentId)
        scanForm.unit = reagent?.unit || ''
        if (!scanForm.purchasePrice) scanForm.purchasePrice = reagent?.price ?? 0
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || `条码 ${code} 解析失败`)
  } finally {
    resolving.value = false
    barcodeInput.value = ''
  }
}

async function submitScan() {
  // 基础校验：试剂/批号/效期/数量（别名扫码可由条码解析出试剂与批号）
  if (scanMode.value !== 'alias' && !scanForm.reagentId) {
    ElMessage.warning('请选择试剂')
    return
  }
  if (!scanForm.expiryDate) {
    ElMessage.warning('请选择效期')
    return
  }
  if (!scanForm.quantity || scanForm.quantity <= 0) {
    ElMessage.warning('入库数量必须大于 0')
    return
  }
  if (scanMode.value === 'rule' && !scanForm.prefix.trim()) {
    ElMessage.warning('请输入条码前缀')
    return
  }
  if (scanMode.value === 'single' && !singleBarcodes.value.length) {
    ElMessage.warning('请先录入条码')
    return
  }
  if (scanMode.value === 'alias' && !aliasRows.value.length) {
    ElMessage.warning('请先扫描并解析条码')
    return
  }

  const base = {
    reagentId: scanForm.reagentId,
    batchNo: scanForm.batchNo || undefined,
    expiryDate: scanForm.expiryDate,
    quantity: scanForm.quantity,
    unit: scanForm.unit || undefined,
    purchasePrice: scanForm.purchasePrice || undefined,
    operatorId: userStore.userId,
    operatorName: userStore.realName || userStore.username,
    remark: scanForm.remark || undefined
  }

  scanSubmitting.value = true
  try {
    let result
    if (scanMode.value === 'rule') {
      result = await scanInboundByRule({
        ...base,
        prefix: scanForm.prefix.trim(),
        startSeq: scanForm.startSeq || 1,
        count: scanForm.count
      })
    } else if (scanMode.value === 'single') {
      result = await scanInboundBySingle({ ...base, barcodes: [...singleBarcodes.value] })
    } else {
      result = await scanInboundByAlias({ ...base, codes: aliasRows.value.map((r) => r.code) })
    }
    ElMessage.success(`扫码入库成功：${result?.inboundNo || ''}（待验收）`)
    scanDialogVisible.value = false
    // 创建后进入待验收列表
    searchForm.verifyStatus = 0
    pagination.pageNum = 1
    await fetchList()
  } catch (e) {
    // 统一提示
  } finally {
    scanSubmitting.value = false
  }
}

// ========== 打印设置 ==========
const reagentPrintConfigVisible = ref(false)
const currentReagentPrintConfig = ref<PrintConfig>(getPrintConfig('reagent-barcode'))
function openReagentPrintConfigDialog() {
  reagentPrintConfigVisible.value = true
}
function onReagentPrintConfigSaved(cfg: PrintConfig) {
  currentReagentPrintConfig.value = cfg
  ElMessage.success('打印设置已保存，下次打印时生效')
}

/** 打印入库存量条码：验收后自动生成的批次条码 (使用统一打印配置) */
async function printBarcodes() {
  const labels = printedBarcodes.value
  if (labels.length === 0) {
    ElMessage.warning('暂无已生成的条码，验收后自动生成后方可打印')
    return
  }
  const no = detailData.value.inboundNo || ''
  const ok = await printReagentBarcodeLabels(`入库条码-${no}`, labels.map(it => ({
    barcode: it.barcode!,
    reagentName: it.reagentName,
    lotNo: it.lotNo,
    expiryDate: it.expiryDate,
    location: it.location,
    extra: [{ label: '入库单', value: no }],
  })))
  if (!ok) {
    ElMessage.error('无法打开打印窗口，请检查浏览器弹窗拦截设置')
    return
  }
  ElMessage.success(`正在打印 ${labels.length} 个条码标签`)
}

// 试剂选项（供入库明细选择器使用）
const reagentOptions = ref<Reagent[]>([])
async function loadReagents() {
  try {
    const res = await getReagentPage({ pageNum: 1, pageSize: 100 })
    reagentOptions.value = res.records || []
  } catch (e) {
    reagentOptions.value = []
  }
}

// 供应商选项（供入库单选择）
const supplierOptions = ref<Supplier[]>([])
async function loadSuppliers() {
  try {
    supplierOptions.value = (await getSelectableSuppliers()) || []
  } catch (e) {
    supplierOptions.value = []
  }
}

function onReagentChange(row: ReagentInboundItem, reagentId: number) {
  const reagent = reagentOptions.value.find((r) => r.id === reagentId)
  if (reagent) {
    if (!row.unit) row.unit = reagent.unit ?? ''
    if (!row.purchasePrice && reagent.price) row.purchasePrice = reagent.price
  }
}

const totalAmount = computed(() => {
  return (editForm.items || []).reduce((sum, item) => {
    const qty = Number(item.quantity || 0)
    const price = Number(item.purchasePrice || 0)
    item.amount = Number((qty * price).toFixed(2))
    return sum + item.amount
  }, 0)
})

function verifyStatusType(status?: number): 'warning' | 'success' | 'danger' {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

function verifyStatusText(status?: number): string {
  if (status === 1) return '已验收'
  if (status === 2) return '拒收'
  return '待验收'
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

async function copyBarcode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('条码已复制: ' + code)
  } catch (e) {
    const textarea = document.createElement('textarea')
    textarea.value = code
    document.body.appendChild(textarea)
    textarea.select()
    try { document.execCommand('copy'); ElMessage.success('条码已复制: ' + code) } catch { ElMessage.warning('复制失败, 请手动复制') }
    document.body.removeChild(textarea)
  }
}

// 拉取列表
async function fetchList() {
  loading.value = true
  try {
    const params: ReagentInboundQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.inboundNo) params.inboundNo = searchForm.inboundNo
    if (searchForm.verifyStatus !== undefined) params.verifyStatus = searchForm.verifyStatus
    const res = await getInboundPage(params)
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
  searchForm.inboundNo = ''
  searchForm.verifyStatus = undefined
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

function handleAdd() {
  resetForm()
  formDialogVisible.value = true
}

function resetForm() {
  editForm.supplier = ''
  editForm.remark = ''
  editForm.items = []
  formRef.value?.clearValidate?.()
}

function addItem() {
  const item: ReagentInboundItem = {
    reagentId: 0,
    lotNo: '',
    quantity: 0,
    unit: '',
    purchasePrice: 0,
    amount: 0,
    expiryDate: '',
    location: ''
  }
  editForm.items!.push(item)
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
  const item: ReagentInboundItem = {
    reagentId: payload.reagentId,
    reagentName: payload.reagentName,
    lotNo: '',
    quantity: 1,
    unit: payload.unit || '',
    purchasePrice: payload.price,
    amount: 0,
    expiryDate: '',
    location: payload.location || ''
  }
  editForm.items!.push(item)
  ElMessage.success(`已新增：${payload.reagentName}${payload.batchNo ? `（批号 ${payload.batchNo}）` : ''}`)
}

// ===== 导入明细 =====
const importLoading = ref(false)
function downloadTemplate() {
  downloadInboundTemplate().then(() => {
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
    const res = await importInboundItems(options.file)
    if (res.failed > 0) {
      const first = res.errors?.[0]
      ElMessage.warning(`明细导入完成：成功 ${res.success} 条，失败 ${res.failed} 条${first ? `（第${first.row}行：${first.reason}）` : ''}`)
    } else {
      ElMessage.success(`明细导入完成：成功 ${res.success} 条`)
    }
    const rows: ReagentInboundItem[] = res.items || []
    for (const it of rows) {
      // 补充到试剂下拉选项，保证选择器能显示名称
      if (!reagentOptions.value.some((r) => r.id === it.reagentId)) {
        reagentOptions.value.push({ id: it.reagentId!, name: it.reagentName || '', reagentCode: it.reagentCode, unit: it.unit, price: it.purchasePrice } as unknown as Reagent)
      }
      editForm.items.push({
        reagentId: it.reagentId!,
        reagentName: it.reagentName || '',
        lotNo: it.lotNo || '',
        quantity: Number(it.quantity || 0),
        unit: it.unit || '',
        purchasePrice: it.purchasePrice ? Number(it.purchasePrice) : undefined,
        amount: 0,
        expiryDate: it.expiryDate || '',
        location: it.location || ''
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!editForm.items || editForm.items.length === 0) {
      ElMessage.warning('请至少添加一条入库明细')
      return
    }
    // 校验每条明细完整性: 试剂必选、数量>0、批号与效期必填
    for (const item of editForm.items) {
      if (!item.reagentId) {
        ElMessage.warning('入库明细存在未选择试剂的行, 请检查')
        return
      }
      if (!item.lotNo || !item.lotNo.trim()) {
        ElMessage.warning(`明细 "${item.reagentName || ''}" 未填写批号`)
        return
      }
      if (item.quantity == null || item.quantity <= 0) {
        ElMessage.warning('入库数量必须大于0')
        return
      }
      if (!item.expiryDate) {
        ElMessage.warning('请为每条入库明细填写效期')
        return
      }
    }
    formLoading.value = true
    try {
      const payload: ReagentInbound = {
        supplier: editForm.supplier,
        remark: editForm.remark,
        totalAmount: Number(totalAmount.value.toFixed(2)),
        items: editForm.items!.map((item) => ({
          reagentId: item.reagentId,
          lotNo: item.lotNo,
          quantity: item.quantity,
          unit: item.unit,
          purchasePrice: item.purchasePrice,
          amount: item.amount,
          expiryDate: item.expiryDate,
          location: item.location
        }))
      }
      await createInbound(payload)
      ElMessage.success('入库单创建成功')
      formDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDetail(row: ReagentInbound) {
  try {
    const res = await getInboundDetail(row.id!)
    detailData.value = res
    detailDialogVisible.value = true
  } catch (e) {
    // ignore
  }
}

async function handleVerify(row: ReagentInbound) {
  if (verifyingIds.value.has(row.id!) || rejectingIds.value.has(row.id!)) return
  try {
    await ElMessageBox.confirm(`确认验收入库单 "${row.inboundNo}" 吗?`, '验收确认', { type: 'warning' })
  } catch {
    return
  }
  verifyingIds.value.add(row.id!)
  try {
    await verifyInbound(row.id!, userStore.userId, userStore.realName || userStore.username)
    ElMessage.success('验收成功')
    await fetchList()
  } catch (e: any) {
    // 区分：业务正常 500(空响应/容器级异常)/网络错误 其实验收有可能已成功(事务已提交)，必须提示用户刷新，禁止重复点
    const msg = e?.message || ''
    const status = e?.response?.status
    const isServerBusy =
      status === 500 ||
      /status code 500/i.test(msg) ||
      /Network Error/i.test(msg) ||
      /timeout/i.test(msg) ||
      (!!e && !msg)
    if (isServerBusy) {
      ElMessageBox.alert(
        '验收接口响应异常（服务器繁忙或无响应），单据状态可能已成功变更为【已验收】，请立即点击【查询/刷新】核对列表状态，切勿重复点击【验收】按钮！',
        '验收结果不明确提示',
        { type: 'warning', confirmButtonText: '我知道了，去刷新' }
      ).catch(() => {})
    } else if (msg && !/cancel/i.test(msg)) {
      ElMessage.error(msg)
    }
  } finally {
    verifyingIds.value.delete(row.id!)
  }
}

async function handleReject(row: ReagentInbound) {
  if (verifyingIds.value.has(row.id!) || rejectingIds.value.has(row.id!)) return
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入拒收原因', '拒收确认', {
      type: 'warning',
      inputPlaceholder: '请输入拒收原因',
      inputValidator: (val) => !!val?.trim() || '拒收原因不能为空'
    })
    reason = value
  } catch {
    return
  }
  rejectingIds.value.add(row.id!)
  try {
    await rejectInbound(row.id!, userStore.userId, userStore.realName || userStore.username, reason)
    ElMessage.success('已拒收')
    await fetchList()
  } catch (e: any) {
    const msg = e?.message || ''
    if (msg && !/cancel/i.test(msg)) ElMessage.error(msg)
  } finally {
    rejectingIds.value.delete(row.id!)
  }
}

onMounted(() => {
  fetchList()
  loadReagents()
  loadSuppliers()
})
</script>

<style scoped lang="scss">
.reagent-inbound-page {
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

  .barcode-text {
    font-family: Consolas, Monaco, monospace;
    color: #1a73e8;
    cursor: pointer;
    user-select: all;
    text-decoration: underline dotted;
    &:hover { color: #0d47a1; }
  }
  .barcode-missing {
    color: #c0c4cc;
    font-size: 12px;
  }

  .scan-input-row {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .scan-preview {
    margin-top: 12px;
    line-height: 2;

    .preview-label {
      color: #606266;
      font-size: 13px;
      margin-right: 8px;
    }

    .code-tag {
      margin: 0 6px 6px 0;
      font-family: Consolas, Monaco, monospace;
    }
  }
}
</style>
