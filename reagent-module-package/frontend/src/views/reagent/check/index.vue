<template>
  <div class="check-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="仓库">
          <el-select v-model="searchForm.warehouse" placeholder="全部仓库" clearable filterable style="width: 200px">
            <el-option v-for="w in warehouseOptions" :key="w.whName" :label="w.whName" :value="w.whName" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="进行中" :value="0" />
            <el-option label="已完成" :value="1" />
            <el-option label="已作废" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleCreate">新建盘点单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 盘点单列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="checkNo" label="盘点单号" width="180" />
        <el-table-column prop="warehouse" label="仓库" min-width="140" />
        <el-table-column label="盘点日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.checkDate) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="盘点人" width="110" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">明细</el-button>
            <el-button v-if="row.status === 0" type="success" link size="small" @click="handleSubmit(row)">录入实盘</el-button>
            <el-button v-if="row.status === 0" type="danger" link size="small" @click="handleVoid(row)">作废</el-button>
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

    <!-- 新建盘点单弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建盘点单" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="仓库">
          <el-select v-model="createForm.warehouse" placeholder="请选择仓库（留空盘点全部）" clearable filterable style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.whName" :label="w.whName" :value="w.whName" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点人">
          <el-input v-model="createForm.operatorName" placeholder="盘点人姓名" />
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

    <!-- 盘点明细弹窗 -->
    <el-dialog v-model="detailDialogVisible" :title="`盘点明细 - ${currentCheck?.checkNo || ''}`" width="900px">
      <el-table :data="checkItems" border stripe size="small">
        <el-table-column label="试剂名称" min-width="140">
          <template #default="{ row }">{{ row.reagentName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" width="140" />
        <el-table-column label="账面数量" width="110" align="center">
          <template #default="{ row }">{{ row.bookQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="实盘数量" width="150" align="center">
          <template #default="{ row }">
            <el-input-number v-if="currentCheck?.status === 0" v-model="row.actualQty" :precision="2" :controls="false" size="small" style="width: 120px" />
            <span v-else>{{ row.actualQty ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异" width="100" align="center">
          <template #default="{ row }">
            <span :class="diffClass(row.diffQty)">{{ formatDiff(row.diffQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-if="currentCheck?.status === 0" v-model="row.remark" size="small" placeholder="备注" />
            <span v-else>{{ row.remark }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button v-if="currentCheck?.status === 0" type="success" :loading="submitLoading" @click="handleSubmitItems">提交盘点</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import {
  getStockCheckPage,
  getStockCheckItems,
  createStockCheck,
  submitStockCheck,
  voidStockCheck,
  getWarehouseTree,
  type ReagentWarehouse,
  type ReagentStockCheck,
  type ReagentStockCheckItem,
  type StockCheckQuery
} from '@/api/reagent'

const userStore = useUserStore()

// 仓库选项（来源：仓库三级树，扁平化取其名称）
const warehouseOptions = ref<ReagentWarehouse[]>([])
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

function formatDate(date?: string): string {
  return date ? dayjs(date).format('YYYY-MM-DD') : '-'
}

function statusText(status?: number): string {
  if (status === 0) return '进行中'
  if (status === 1) return '已完成'
  if (status === 2) return '已作废'
  return '-'
}
function statusTag(status?: number): string {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'info'
  return 'info'
}

function diffClass(diff?: number | null): string {
  if (diff === null || diff === undefined || diff === 0) return ''
  return Number(diff) > 0 ? 'diff-up' : 'diff-down'
}
function formatDiff(diff?: number | null): string {
  if (diff === null || diff === undefined) return '-'
  return Number(diff) > 0 ? `+${diff}` : `${diff}`
}

const loading = ref(false)
const tableData = ref<ReagentStockCheck[]>([])
const searchForm = reactive({ warehouse: '', status: undefined as number | undefined })

const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const params: StockCheckQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.warehouse) params.warehouse = searchForm.warehouse
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getStockCheckPage(params)
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
  searchForm.warehouse = ''
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

// 新建
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ warehouse: '', operatorName: '', remark: '' })
const createRules: FormRules = {}

function handleCreate() {
  Object.assign(createForm, {
    warehouse: '',
    operatorName: userStore.realName || '',
    remark: ''
  })
  createDialogVisible.value = true
}

async function handleCreateSave() {
  createLoading.value = true
  try {
    await createStockCheck({
      warehouse: createForm.warehouse || undefined,
      operatorId: userStore.userId || undefined,
      operatorName: createForm.operatorName || undefined,
      remark: createForm.remark || undefined
    })
    ElMessage.success('盘点单已创建')
    createDialogVisible.value = false
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    createLoading.value = false
  }
}

// 明细
const detailDialogVisible = ref(false)
const currentCheck = ref<ReagentStockCheck | null>(null)
const checkItems = ref<ReagentStockCheckItem[]>([])
const submitLoading = ref(false)

async function handleDetail(row: ReagentStockCheck) {
  currentCheck.value = row
  detailDialogVisible.value = true
  try {
    checkItems.value = await getStockCheckItems(row.id!)
  } catch (e) {
    checkItems.value = []
  }
}

function handleSubmit(row: ReagentStockCheck) {
  currentCheck.value = row
  handleDetail(row)
}

async function handleSubmitItems() {
  if (!currentCheck.value?.id) return
  try {
    await ElMessageBox.confirm('确认提交盘点结果? 提交后盘点单将标记为已完成。', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  submitLoading.value = true
  try {
    const items = checkItems.value.map((it) => ({
      id: it.id!,
      actualQty: it.actualQty ?? 0,
      remark: it.remark
    }))
    await submitStockCheck(currentCheck.value.id, items)
    ElMessage.success('盘点已提交')
    detailDialogVisible.value = false
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    submitLoading.value = false
  }
}

// 作废盘点单（补充：现有页面缺 void 闭环）
async function handleVoid(row: ReagentStockCheck) {
  try {
    await ElMessageBox.confirm(`确认作废盘点单 "${row.checkNo}" 吗?`, '作废确认', { type: 'warning' })
    await voidStockCheck(row.id!)
    ElMessage.success('已作废')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
  loadWarehouses()
})
</script>

<style scoped lang="scss">
.check-page {
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

  .diff-up {
    color: #67c23a;
    font-weight: 600;
  }
  .diff-down {
    color: #f56c6c;
    font-weight: 600;
  }
}
</style>
