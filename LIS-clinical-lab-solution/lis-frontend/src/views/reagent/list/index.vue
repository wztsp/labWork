<template>
  <div class="reagent-list-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="试剂名称">
          <el-input
            v-model="searchForm.reagentName"
            placeholder="请输入试剂名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="试剂编码">
          <el-input
            v-model="searchForm.reagentCode"
            placeholder="请输入编码"
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
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option
              v-for="c in categoryOptions"
              :key="c.id!"
              :label="c.categoryName"
              :value="c.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="危险品">
          <el-select v-model="searchForm.hazardFlag" placeholder="全部" clearable style="width: 120px">
            <el-option label="普通" :value="0" />
            <el-option label="危险品" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增试剂</el-button>
          <el-button type="success" plain @click="handleExport">导出</el-button>
          <el-button type="warning" plain @click="importVisible = true">导入</el-button>
          <el-button link type="primary" @click="advancedVisible = !advancedVisible">
            {{ advancedVisible ? '收起高级筛选' : '高级筛选' }}
          </el-button>
        </el-form-item>
      </el-form>
      <!-- 高级筛选（T-19 / R-14：库存量/效期/仓库/位置 组合） -->
      <el-form v-if="advancedVisible" :inline="true" :model="searchForm" @submit.prevent class="advanced-form">
        <el-form-item label="库存量(≥)">
          <el-input-number v-model="searchForm.minQuantity" :min="0" controls-position="right" style="width: 120px" placeholder="下限" />
        </el-form-item>
        <el-form-item label="库存量(≤)">
          <el-input-number v-model="searchForm.maxQuantity" :min="0" controls-position="right" style="width: 120px" placeholder="上限" />
        </el-form-item>
        <el-form-item label="效期起">
          <el-date-picker v-model="searchForm.expiryStart" type="date" value-format="YYYY-MM-DD" placeholder="开始" style="width: 140px" />
        </el-form-item>
        <el-form-item label="效期止">
          <el-date-picker v-model="searchForm.expiryEnd" type="date" value-format="YYYY-MM-DD" placeholder="结束" style="width: 140px" />
        </el-form-item>
        <el-form-item label="仓库/冰箱">
          <el-select v-model="searchForm.warehouseId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="w in warehouseOptions" :key="w.id!" :label="w.whName" :value="w.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="searchForm.location" placeholder="如 A-01-03" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">高级查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reagentCode" label="试剂编码" width="130" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" />
        <el-table-column prop="specification" label="规格" width="120" />
        <el-table-column prop="manufacturer" label="厂家" width="140" />
        <el-table-column prop="supplier" label="供应商" width="140" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="单价" width="100" align="right">
          <template #default="{ row }">
            {{ row.price != null ? `¥${Number(row.price).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="storageCondition" label="存储条件" width="120" />
        <el-table-column prop="expiryDays" label="有效期(天)" width="100" align="center" />
        <el-table-column label="最佳库存" width="100" align="right">
          <template #default="{ row }">{{ row.optimalStock != null ? row.optimalStock : '-' }}</template>
        </el-table-column>
        <el-table-column label="采购周期(天)" width="115" align="center">
          <template #default="{ row }">{{ row.purchaseCycleDays != null ? row.purchaseCycleDays : '-' }}</template>
        </el-table-column>
        <el-table-column prop="barcodeAlias" label="条码别名" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.barcodeAlias || '-' }}</template>
        </el-table-column>
        <el-table-column label="质控品" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.qcFlag === 1 ? 'warning' : 'info'" size="small">
              {{ row.qcFlag === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分包份数" width="90" align="center">
          <template #default="{ row }">{{ row.qcPackCount != null ? row.qcPackCount : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleSuggestion(row)">采购建议</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑试剂' : '新增试剂'" width="680px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="试剂编码" prop="reagentCode">
              <el-input v-model="editForm.reagentCode" :disabled="!!editForm.id" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="试剂名称" prop="reagentName">
              <el-input v-model="editForm.reagentName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="editForm.specification" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="editForm.unit" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厂家">
              <el-input v-model="editForm.manufacturer" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="editForm.supplier" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价">
              <el-input-number
                v-model="editForm.price"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期(天)">
              <el-input-number
                v-model="editForm.expiryDays"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存储条件">
              <el-input v-model="editForm.storageCondition" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch
                v-model="editForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最佳库存">
              <el-input-number
                v-model="editForm.optimalStock"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购周期(天)">
              <el-input-number
                v-model="editForm.purchaseCycleDays"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="条码别名">
              <el-input v-model="editForm.barcodeAlias" placeholder="扫码时用于识别该试剂的别名条码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质控品">
              <el-switch
                v-model="editForm.qcFlag"
                :active-value="1"
                :inactive-value="0"
                active-text="是"
                inactive-text="否"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分包份数">
              <el-input-number
                v-model="editForm.qcPackCount"
                :min="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框（T-19 / R-14） -->
    <el-dialog v-model="importVisible" title="试剂批量导入" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="导入规范"
        description="请先导出获取模板（UTF-8 逗号分隔 CSV）。首行为表头，编码/名称为必填；已存在编码的试剂按编码更新，否则新增。失败的整行跳过，不影响其他行。"
      />
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xls,.txt,.csv"
        :on-change="handleFileChange"
        :on-remove="() => (importFileVal = null)"
        style="margin-top: 16px"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importFileVal" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 采购建议 -->
    <el-dialog v-model="suggestionVisible" title="采购建议" width="480px">
      <el-descriptions v-loading="suggestionLoading" :column="1" border>
        <el-descriptions-item label="试剂">{{ suggestionReagentName }}</el-descriptions-item>
        <el-descriptions-item label="当前库存">{{ suggestion.currentStock ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存下限">{{ suggestion.minStock ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存上限">{{ suggestion.maxStock ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="最佳库存">{{ suggestion.optimalStock ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="采购周期(天)">{{ suggestion.purchaseCycleDays ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="建议采购量">
          <strong class="suggest-qty">{{ suggestion.suggestedQty ?? '-' }}</strong>
        </el-descriptions-item>
        <el-descriptions-item label="建议采购日期">{{ suggestion.suggestedDate || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="suggestionVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getReagentPage,
  saveReagent,
  deleteReagent,
  getCategoryPage,
  getWarehouseTree,
  exportReagentDict,
  importReagentDict,
  getPurchaseSuggestion,
  type Reagent,
  type ReagentQuery,
  type ReagentCategory,
  type ReagentWarehouse,
  type PurchaseSuggestionDTO
} from '@/api/reagent'

const loading = ref(false)
const tableData = ref<Reagent[]>([])
const searchForm = reactive({
  reagentName: '',
  reagentCode: '',
  status: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  hazardFlag: undefined as number | undefined,
  // T-19 / R-14 高级筛选
  minQuantity: undefined as number | undefined,
  maxQuantity: undefined as number | undefined,
  expiryStart: undefined as string | undefined,
  expiryEnd: undefined as string | undefined,
  warehouseId: undefined as number | undefined,
  location: undefined as string | undefined
})
const advancedVisible = ref(false)

// 分类选项（供查询条件使用）
const categoryOptions = ref<ReagentCategory[]>([])
async function loadCategories() {
  try {
    const res = await getCategoryPage({ pageNum: 1, pageSize: 100 })
    categoryOptions.value = res.records || []
  } catch (e) {
    categoryOptions.value = []
  }
}

// 仓库/冰箱选项（三级树拍平供下拉选择）
const warehouseOptions = ref<ReagentWarehouse[]>([])
function flattenWarehouse(list: ReagentWarehouse[]): ReagentWarehouse[] {
  const out: ReagentWarehouse[] = []
  for (const w of list || []) {
    out.push(w)
    if (w.children && w.children.length) out.push(...flattenWarehouse(w.children))
  }
  return out
}
async function loadWarehouses() {
  try {
    const tree = await getWarehouseTree()
    warehouseOptions.value = flattenWarehouse(tree || [])
  } catch (e) {
    warehouseOptions.value = []
  }
}

// 批量导入状态
const importVisible = ref(false)
const importLoading = ref(false)
const importFileVal = ref<File | null>(null)
function handleFileChange(file: any) {
  importFileVal.value = file.raw || null
}

// 采购建议
const suggestionVisible = ref(false)
const suggestionLoading = ref(false)
const suggestionReagentName = ref('')
const suggestion = ref<PurchaseSuggestionDTO>({})

const pagination = reactive({
  pageNum: 1,
  pageSize: 50,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<Reagent>({
  id: 0,
  reagentCode: '',
  reagentName: '',
  specification: '',
  manufacturer: '',
  supplier: '',
  unit: '',
  price: 0,
  storageCondition: '',
  expiryDays: 0,
  optimalStock: 0,
  purchaseCycleDays: 0,
  barcodeAlias: '',
  qcFlag: 0,
  qcPackCount: 1,
  status: 1
})
const formRules: FormRules = {
  reagentCode: [{ required: true, message: '请输入试剂编码', trigger: 'blur' }],
  reagentName: [{ required: true, message: '请输入试剂名称', trigger: 'blur' }]
}

// 拉取列表
async function fetchList() {
  loading.value = true
  try {
    const params: ReagentQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.reagentName) params.reagentName = searchForm.reagentName
    if (searchForm.reagentCode) params.reagentCode = searchForm.reagentCode
    if (searchForm.status !== undefined) params.status = searchForm.status
    if (searchForm.categoryId !== undefined) params.categoryId = searchForm.categoryId
    if (searchForm.hazardFlag !== undefined) params.hazardFlag = searchForm.hazardFlag
    if (searchForm.minQuantity !== undefined) params.minQuantity = searchForm.minQuantity
    if (searchForm.maxQuantity !== undefined) params.maxQuantity = searchForm.maxQuantity
    if (searchForm.expiryStart) params.expiryStart = searchForm.expiryStart
    if (searchForm.expiryEnd) params.expiryEnd = searchForm.expiryEnd
    if (searchForm.warehouseId !== undefined) params.warehouseId = searchForm.warehouseId
    if (searchForm.location) params.location = searchForm.location
    const res = await getReagentPage(params)
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
  searchForm.reagentCode = ''
  searchForm.status = undefined
  searchForm.categoryId = undefined
  searchForm.hazardFlag = undefined
  searchForm.minQuantity = undefined
  searchForm.maxQuantity = undefined
  searchForm.expiryStart = undefined
  searchForm.expiryEnd = undefined
  searchForm.warehouseId = undefined
  searchForm.location = undefined
  handleSearch()
}

// T-19 / R-14 导出
async function handleExport() {
  try {
    await exportReagentDict()
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

// T-19 / R-14 导入
async function handleImport() {
  if (!importFileVal.value) return
  importLoading.value = true
  try {
    const res = await importReagentDict(importFileVal.value)
    if (res.failed > 0) {
      const first = res.errors?.[0]
      ElMessage.warning(
        `导入完成：成功 ${res.success} 条，失败 ${res.failed} 条${first ? `（首行错误：第${first.row}行 ${first.reason}）` : ''}`
      )
    } else {
      ElMessage.success(`导入完成：成功 ${res.success} 条`)
    }
    importVisible.value = false
    importFileVal.value = null
    fetchList()
  } catch (e) {
    ElMessage.error('导入失败，请检查文件编码为 UTF-8')
  } finally {
    importLoading.value = false
  }
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
  Object.assign(editForm, {
    id: 0,
    reagentCode: '',
    reagentName: '',
    specification: '',
    manufacturer: '',
    supplier: '',
    unit: '',
    price: 0,
    storageCondition: '',
    expiryDays: 0,
    optimalStock: 0,
    purchaseCycleDays: 0,
    barcodeAlias: '',
    qcFlag: 0,
    qcPackCount: 1,
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: Reagent) {
  Object.assign(editForm, {
    id: row.id,
    reagentCode: row.reagentCode,
    reagentName: row.reagentName,
    specification: row.specification ?? '',
    manufacturer: row.manufacturer ?? '',
    supplier: row.supplier ?? '',
    unit: row.unit ?? '',
    price: row.price ?? 0,
    storageCondition: row.storageCondition ?? '',
    expiryDays: row.expiryDays ?? 0,
    optimalStock: row.optimalStock ?? 0,
    purchaseCycleDays: row.purchaseCycleDays ?? 0,
    barcodeAlias: row.barcodeAlias ?? '',
    qcFlag: row.qcFlag ?? 0,
    qcPackCount: row.qcPackCount ?? 1,
    status: row.status ?? 1
  })
  formDialogVisible.value = true
}

async function handleSuggestion(row: Reagent) {
  suggestionReagentName.value = row.reagentName
  suggestion.value = {}
  suggestionVisible.value = true
  suggestionLoading.value = true
  try {
    suggestion.value = await getPurchaseSuggestion(row.id)
  } catch (e) {
    suggestion.value = {}
  } finally {
    suggestionLoading.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveReagent({ ...editForm })
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

async function handleDelete(row: Reagent) {
  try {
    await ElMessageBox.confirm(`确认删除试剂 "${row.reagentName}" 吗?`, '提示', { type: 'warning' })
    await deleteReagent(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
  loadCategories()
  loadWarehouses()
})
</script>

<style scoped lang="scss">
.reagent-list-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }

    .advanced-form {
      margin-top: 4px;
      padding-top: 12px;
      border-top: 1px dashed var(--el-border-color);
    }
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .suggest-qty {
    color: var(--el-color-danger);
    font-size: 18px;
  }
}
</style>
