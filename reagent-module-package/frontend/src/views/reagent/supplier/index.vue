<template>
  <div class="supplier-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="名称/编码/联系人"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="证照状态">
          <el-select v-model="searchForm.qualStatus" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, key) in QUAL_STATUS_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增供应商</el-button>
          <el-button type="success" plain @click="showSelectable = true">采购可用下拉</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="supplierCode" label="编码" width="120" />
        <el-table-column prop="supplierName" label="供应商名称" min-width="160" />
        <el-table-column prop="shortName" label="简称" width="100" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column label="证照状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="qualStatusType(row.qualStatus)" size="small">{{ qualStatusText(row.qualStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑供应商' : '新增供应商'" width="680px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="editForm.supplierName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="简称">
              <el-input v-model="editForm.shortName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="editForm.contactPerson" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="editForm.phone" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="editForm.email" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地址">
              <el-input v-model="editForm.address" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统一社会信用代码">
              <el-input v-model="editForm.creditCode" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="营业执照号">
              <el-input v-model="editForm.licenseNo" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="营业执照到期日">
              <el-date-picker
                v-model="editForm.licenseExpiry"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="器械经营许可证号">
              <el-input v-model="editForm.medicalCertNo" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="器械证到期日">
              <el-date-picker
                v-model="editForm.medicalCertExpiry"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
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
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="供应商详情" width="680px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="编码">{{ detailData.supplierCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailData.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="简称">{{ detailData.shortName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="信用代码">{{ detailData.creditCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailData.contactPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detailData.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailData.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ detailData.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="证照状态">
          <el-tag :type="qualStatusType(detailData.qualStatus)" size="small">{{ qualStatusText(detailData.qualStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="营业执照到期">{{ detailData.licenseExpiry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="器械证到期">{{ detailData.medicalCertExpiry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailData.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 采购可用下拉弹窗（演示 selectable 接口使用） -->
    <el-dialog v-model="showSelectable" title="采购可用供应商（证照有效且启用）" width="560px">
      <el-select v-model="selectedSupplierId" filterable placeholder="选择供应商" style="width: 100%">
        <el-option v-for="s in selectableSuppliers" :key="s.id!" :label="s.supplierName" :value="s.id!" />
      </el-select>
      <el-table :data="selectableSuppliers" border stripe size="small" style="margin-top: 12px">
        <el-table-column prop="supplierName" label="名称" min-width="160" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="证照" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="qualStatusType(row.qualStatus)" size="small">{{ qualStatusText(row.qualStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showSelectable = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getSupplierPage,
  getSupplierDetail,
  createSupplier,
  updateSupplier,
  deleteSupplier,
  getSelectableSuppliers,
  type Supplier,
  type SupplierQuery
} from '@/api/reagent'

// 证照状态枚举语义映射：0待完善 1有效 2已过期
const QUAL_STATUS_MAP: Record<number, string> = {
  0: '待完善',
  1: '有效',
  2: '已过期'
}

function qualStatusText(s?: number): string {
  return s === undefined || s === null ? '-' : QUAL_STATUS_MAP[s] || `未知(${s})`
}
function qualStatusType(s?: number): 'info' | 'success' | 'danger' | 'warning' {
  if (s === 1) return 'success'
  if (s === 2) return 'danger'
  return 'warning'
}

const loading = ref(false)
const tableData = ref<Supplier[]>([])
const searchForm = reactive({
  keyword: '',
  qualStatus: undefined as number | undefined,
  status: undefined as number | undefined
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const params: SupplierQuery = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.qualStatus !== undefined) params.qualStatus = searchForm.qualStatus
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getSupplierPage(params)
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
  searchForm.keyword = ''
  searchForm.qualStatus = undefined
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

// 新增/编辑
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<Supplier>({
  id: undefined,
  supplierName: '',
  shortName: '',
  contactPerson: '',
  phone: '',
  email: '',
  address: '',
  creditCode: '',
  licenseNo: '',
  licenseExpiry: '',
  medicalCertNo: '',
  medicalCertExpiry: '',
  status: 1,
  remark: ''
})
const formRules: FormRules = {
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(editForm, {
    id: undefined,
    supplierName: '',
    shortName: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    creditCode: '',
    licenseNo: '',
    licenseExpiry: '',
    medicalCertNo: '',
    medicalCertExpiry: '',
    status: 1,
    remark: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: Supplier) {
  Object.assign(editForm, {
    id: row.id,
    supplierName: row.supplierName ?? '',
    shortName: row.shortName ?? '',
    contactPerson: row.contactPerson ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    address: row.address ?? '',
    creditCode: row.creditCode ?? '',
    licenseNo: row.licenseNo ?? '',
    licenseExpiry: row.licenseExpiry ?? '',
    medicalCertNo: row.medicalCertNo ?? '',
    medicalCertExpiry: row.medicalCertExpiry ?? '',
    status: row.status ?? 1,
    remark: row.remark ?? ''
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      if (editForm.id) {
        await updateSupplier({ ...editForm })
      } else {
        await createSupplier({ ...editForm })
      }
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

async function handleDelete(row: Supplier) {
  try {
    await ElMessageBox.confirm(`确认删除供应商 "${row.supplierName}" 吗? 被采购单引用时将拒绝删除。`, '提示', { type: 'warning' })
    await deleteSupplier(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

// 详情
const detailDialogVisible = ref(false)
const detailData = ref<Supplier>({})

async function handleDetail(row: Supplier) {
  try {
    detailData.value = await getSupplierDetail(row.id!)
    detailDialogVisible.value = true
  } catch (e) {
    // ignore
  }
}

// 采购可用下拉
const showSelectable = ref(false)
const selectableSuppliers = ref<Supplier[]>([])
const selectedSupplierId = ref<number | undefined>(undefined)

async function loadSelectable() {
  try {
    selectableSuppliers.value = await getSelectableSuppliers()
  } catch (e) {
    selectableSuppliers.value = []
  }
}

onMounted(() => {
  fetchList()
  loadSelectable()
})
</script>

<style scoped lang="scss">
.supplier-page {
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
