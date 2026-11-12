<template>
  <div class="reagent-consumption-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="试剂">
          <el-select
            v-model="searchForm.reagentId"
            filterable
            clearable
            placeholder="选择试剂"
            style="width: 200px"
          >
            <el-option
              v-for="r in reagentOptions"
              :key="r.id!"
              :label="`${r.reagentName} (${r.reagentCode})`"
              :value="r.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="消耗类型">
          <el-select v-model="searchForm.consumeType" placeholder="全部" clearable style="width: 140px">
            <el-option label="手工" :value="1" />
            <el-option label="自动" :value="2" />
            <el-option label="质控" :value="3" />
            <el-option label="校准" :value="4" />
            <el-option label="维护" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联单号">
          <el-input
            v-model="searchForm.relatedNo"
            placeholder="请输入关联单号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="扫码">
          <ReagentScanBar placeholder="扫描条码快速登记" @scanned="onQuickScan" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增消耗</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="consumptionNo" label="消耗单号" width="160" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="150" />
        <el-table-column prop="lotNo" label="批号" width="130" />
        <el-table-column prop="quantity" label="消耗数量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="消耗类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="consumeTypeTag(row.consumeType)" size="small">
              {{ consumeTypeText(row.consumeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="relatedNo" label="关联单号" width="140" />
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column label="消耗时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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

    <!-- 新增消耗弹窗 -->
    <el-dialog v-model="formDialogVisible" title="新增消耗" width="640px" @closed="resetForm">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="扫码">
              <ReagentScanBar placeholder="扫描或输入条码，自动带出试剂/批次" @scanned="onDialogScan" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="试剂" prop="reagentId">
              <el-select
                v-model="editForm.reagentId"
                filterable
                clearable
                placeholder="选择试剂"
                style="width: 100%"
              >
                <el-option
                  v-for="r in reagentOptions"
                  :key="r.id!"
                  :label="`${r.reagentName} (${r.reagentCode})`"
                  :value="r.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次库存">
              <el-select
                v-model="editForm.stockId"
                filterable
                clearable
                placeholder="留空走FIFO"
                style="width: 100%"
              >
                <el-option
                  v-for="s in stockOptions"
                  :key="s.id!"
                  :label="`${s.reagentName || '-'} (${s.batchNo || '无批号'})`"
                  :value="s.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="消耗数量" prop="quantity">
              <el-input-number
                v-model="editForm.quantity"
                :min="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="editForm.unit" placeholder="请输入单位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="消耗类型">
              <el-select v-model="editForm.consumeType" style="width: 100%">
                <el-option label="手工" :value="1" />
                <el-option label="自动" :value="2" />
                <el-option label="质控" :value="3" />
                <el-option label="校准" :value="4" />
                <el-option label="维护" :value="5" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联单号">
              <el-input v-model="editForm.relatedNo" placeholder="请输入关联单号" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input
                v-model="editForm.remark"
                type="textarea"
                :rows="3"
                placeholder="请输入备注"
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import {
  getConsumptionPage,
  createConsumption,
  getReagentPage,
  getStockPage,
  type Reagent,
  type ReagentStock,
  type ReagentConsumption,
  type ReagentConsumptionQuery
} from '@/api/reagent'
import { useUserStore } from '@/stores/user'
import ReagentScanBar from '@/components/ReagentScanBar.vue'
import type { ScannedReagent } from '@/utils/reagentScan'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<ReagentConsumption[]>([])
const searchForm = reactive({
  reagentId: undefined as number | undefined,
  consumeType: undefined as number | undefined,
  relatedNo: ''
})

// 试剂 / 库存批次选项（供搜索与表单选择器使用）
const reagentOptions = ref<Reagent[]>([])
const stockOptions = ref<ReagentStock[]>([])
async function loadReagents() {
  try {
    const res = await getReagentPage({ pageNum: 1, pageSize: 100 })
    reagentOptions.value = res.records || []
  } catch (e) {
    reagentOptions.value = []
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

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<ReagentConsumption>({
  reagentId: 0,
  stockId: undefined,
  quantity: 0,
  unit: '',
  consumeType: 1,
  relatedNo: '',
  remark: ''
})
const formRules: FormRules = {
  reagentId: [{ required: true, message: '请选择试剂', trigger: 'change' }],
  quantity: [
    { required: true, message: '请输入消耗数量', trigger: 'blur' },
    { validator: (_r, v, cb) => { if (v == null || v <= 0) cb(new Error('消耗数量必须大于0')); else cb() }, trigger: 'blur' }
  ]
}

function consumeTypeText(type?: number): string {
  switch (type) {
    case 1: return '手工'
    case 2: return '自动'
    case 3: return '质控'
    case 4: return '校准'
    case 5: return '维护'
    default: return '-'
  }
}

function consumeTypeTag(type?: number): 'info' | 'success' | 'warning' | 'primary' | 'danger' {
  switch (type) {
    case 1: return 'info'
    case 2: return 'success'
    case 3: return 'warning'
    case 4: return 'primary'
    case 5: return 'danger'
    default: return 'info'
  }
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

// 拉取列表
async function fetchList() {
  loading.value = true
  try {
    const params: ReagentConsumptionQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.reagentId !== undefined) params.reagentId = searchForm.reagentId
    if (searchForm.consumeType !== undefined) params.consumeType = searchForm.consumeType
    if (searchForm.relatedNo) params.relatedNo = searchForm.relatedNo
    const res = await getConsumptionPage(params)
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
  searchForm.reagentId = undefined
  searchForm.consumeType = undefined
  searchForm.relatedNo = ''
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

// 将扫码结果填入编辑表单（带出试剂/批次/单位）
function applyScanToForm(payload: ScannedReagent) {
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
  editForm.reagentId = payload.reagentId
  editForm.stockId = payload.fromStock ? payload.stockId : undefined
  if (!editForm.unit) editForm.unit = payload.unit || ''
}

// 弹窗内扫码
function onDialogScan(payload: ScannedReagent) {
  applyScanToForm(payload)
}

// 列表快捷扫码：打开预填的登记弹窗，便于连续登记
function onQuickScan(payload: ScannedReagent) {
  resetForm()
  if (!formDialogVisible.value) formDialogVisible.value = true
  applyScanToForm(payload)
}

function resetForm() {
  editForm.reagentId = 0
  editForm.stockId = undefined
  editForm.quantity = 0
  editForm.unit = ''
  editForm.consumeType = 1
  editForm.relatedNo = ''
  editForm.remark = ''
  formRef.value?.clearValidate?.()
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      const payload: ReagentConsumption = {
        reagentId: editForm.reagentId,
        quantity: editForm.quantity,
        unit: editForm.unit,
        consumeType: editForm.consumeType,
        relatedNo: editForm.relatedNo,
        remark: editForm.remark,
        operatorId: userStore.userId,
        operatorName: userStore.realName || userStore.username
      }
      if (editForm.stockId) payload.stockId = editForm.stockId
      await createConsumption(payload)
      ElMessage.success('消耗登记成功')
      formDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

onMounted(() => {
  fetchList()
  loadReagents()
  loadStocks()
})
</script>

<style scoped lang="scss">
.reagent-consumption-page {
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
}
</style>
