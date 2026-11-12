<template>
  <div class="reagent-stock-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="试剂名称">
          <el-input
            v-model="searchForm.reagentName"
            placeholder="请输入试剂名称"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="批号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入批号"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="条码">
          <el-input
            v-model="searchForm.barcode"
            placeholder="请输入条码(完整或前缀)"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="可用" :value="1" />
            <el-option label="已耗尽" :value="2" />
            <el-option label="过期锁定" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <div class="table-toolbar">
        <span class="toolbar-tip">已有条码的批次可补打标签</span>
        <span style="flex: 1"></span>
        <el-tooltip content="打印设置（纸张尺寸、缩放比例等）">
          <el-button
            plain
            size="small"
            :icon="Setting"
            @click="openPrintConfigDialog"
          >打印设置</el-button>
        </el-tooltip>
        <el-button
          type="primary"
          plain
          size="small"
          :disabled="selectedRows.length === 0"
          @click="printSelected"
        >批量补打条码（{{ selectedRows.length }}）</el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        @selection-change="selectedRows = $event"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="150" />
        <el-table-column label="批号" width="130">
          <template #default="{ row }">{{ row.batchNo || row.lotNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="条码" min-width="200">
          <template #default="{ row }">
            <el-tooltip v-if="row.barcode" :content="'复制条码: ' + row.barcode" placement="top">
              <span
                class="barcode-text"
                @click="copyBarcode(row.barcode!)"
                title="点击复制"
              >{{ row.barcode }}</span>
            </el-tooltip>
            <span v-else class="barcode-missing">未生成</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="库存数量" width="100" align="right" />
        <el-table-column prop="location" label="存放位置" width="120" />
        <el-table-column prop="expiryDate" label="到期日期" width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'expiry-warn': isExpiringSoon(row.expiryDate) }">
              {{ row.expiryDate || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.barcode"
              type="primary"
              link
              size="small"
              @click="printOne(row)"
            >补打条码</el-button>
            <span v-else class="barcode-missing">-</span>
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

    <!-- 【缺陷 O】新增/编辑弹窗已整体删除。库存变动请走：
         入库 /reagent/inbound、出库 /reagent/stock/out/**、盘点调账 /reagent/stock-check，
         三者均在同一事务内同步写出 lis_reagent_stock_record 流水。 -->
    <PrintConfigDialog
      v-model:visible="printConfigVisible"
      scene="reagent-barcode"
      @saved="onPrintConfigSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getStockPage,
  type ReagentStock,
  type ReagentStockQuery
} from '@/api/reagent'
import { printReagentBarcodeLabels, type ReagentBarcodeLabel } from '@/utils/reagentPrint'
import {
  getPrintConfig,
  type PrintConfig,
} from '@/utils/printConfig'
import { Setting } from '@element-plus/icons-vue'
import PrintConfigDialog from '@/components/PrintConfigDialog.vue'

const loading = ref(false)
const tableData = ref<ReagentStock[]>([])
const selectedRows = ref<ReagentStock[]>([])

// ========== 打印设置 ==========
const printConfigVisible = ref(false)
const currentPrintConfig = ref<PrintConfig>(getPrintConfig('reagent-barcode'))
function openPrintConfigDialog() {
  printConfigVisible.value = true
}
function onPrintConfigSaved(cfg: PrintConfig) {
  currentPrintConfig.value = cfg
  ElMessage.success('打印设置已保存，下次打印时生效')
}
const searchForm = reactive({
  reagentName: '',
  batchNo: '',
  barcode: '',
  status: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 【缺陷 O】formDialogVisible / formLoading / formRef / editForm / formRules
// 五个写表单相关状态已删除，本页只保留查询与分页状态。

// 批次状态映射: 0过期锁定 1可用 2已耗尽
const STOCK_STATUS_MAP: Record<number, string> = { 0: '过期锁定', 1: '可用', 2: '已耗尽' }
function statusText(s?: number): string {
  return s === undefined || s === null ? '-' : STOCK_STATUS_MAP[s] || `未知(${s})`
}
function statusTag(s?: number): 'info' | 'success' | 'warning' | 'danger' {
  if (s === 0) return 'danger'
  if (s === 2) return 'warning'
  return 'success'
}

// 判断是否临近过期(30天内)
function isExpiringSoon(expiryDate?: string): boolean {
  if (!expiryDate) return false
  const diff = dayjs(expiryDate).diff(dayjs(), 'day')
  return diff >= 0 && diff <= 30
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
    const params: ReagentStockQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.reagentName) params.reagentName = searchForm.reagentName
    if (searchForm.batchNo) params.batchNo = searchForm.batchNo
    if (searchForm.barcode) params.barcode = searchForm.barcode
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getStockPage(params)
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
  searchForm.reagentName = ''
  searchForm.batchNo = ''
  searchForm.barcode = ''
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

// 单行补打条码
function printOne(row: ReagentStock) {
  if (!row.barcode) {
    ElMessage.warning('该批次尚未生成条码')
    return
  }
  doPrint([toLabel(row)])
}

// 批量补打条码（勾选的行）
function printSelected() {
  const labels = selectedRows.value.map(toLabel).filter((l) => l.barcode)
  if (labels.length === 0) {
    ElMessage.warning('所选批次均未生成条码，无法补打')
    return
  }
  doPrint(labels)
}

function toLabel(row: ReagentStock): ReagentBarcodeLabel {
  return {
    barcode: row.barcode || '',
    reagentName: row.reagentName,
    lotNo: row.batchNo || row.lotNo,
    expiryDate: row.expiryDate,
    location: row.location
  }
}

function doPrint(labels: ReagentBarcodeLabel[]) {
  const ok = printReagentBarcodeLabels('试剂条码补打', labels)
  if (ok) {
    ElMessage.success(`正在补打 ${labels.length} 张条码标签`)
  } else {
    ElMessage.error('无法打开打印窗口，请检查浏览器弹窗拦截设置')
  }
}

// 【缺陷 O】handleAdd / handleEdit / handleSave / handleDelete 四个函数已删除。
// 库存写入口唯一化：只允许 ReagentStockServiceImpl 与入库/领用 Service 在事务内写入。

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.reagent-stock-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .table-toolbar {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;

      .toolbar-tip {
        font-size: 12px;
        color: #909399;
      }
    }

    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .expiry-warn {
    color: #e6a23c;
    font-weight: 600;
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
}
</style>
