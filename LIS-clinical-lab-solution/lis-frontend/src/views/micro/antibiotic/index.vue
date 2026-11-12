<template>
  <div class="antibiotic-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="代码/名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="抗生素代码或名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="searchForm.category"
            placeholder="全部"
            clearable
            filterable
            style="width: 160px"
          >
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增抗生素</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="antibioticCode" label="抗生素代码" width="140" />
        <el-table-column prop="antibioticName" label="中文名称" min-width="160" />
        <el-table-column prop="antibioticNameEn" label="英文名称" min-width="160" />
        <el-table-column label="分类" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.category" type="primary" size="small">{{ row.category }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="method" label="检测方法" width="130" align="center" />
        <el-table-column prop="breakpointLow" label="折点低" width="100" align="center" />
        <el-table-column prop="breakpointHigh" label="折点高" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link size="small" @click="openBreakpointDialog(row)">折点</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100, 500, 1000]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑抗生素' : '新增抗生素'" width="680px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="抗生素代码" prop="antibioticCode">
              <el-input v-model="editForm.antibioticCode" :disabled="!!editForm.id" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中文名称" prop="antibioticName">
              <el-input v-model="editForm.antibioticName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名称">
              <el-input v-model="editForm.antibioticNameEn" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="editForm.category" placeholder="请选择" filterable style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测方法">
              <el-select v-model="editForm.method" placeholder="请选择" filterable clearable style="width: 100%">
                <el-option value="K-B纸片法" label="K-B纸片法" />
                <el-option value="微量肉汤法" label="微量肉汤法" />
                <el-option value="E-test法" label="E-test法" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="折点低">
              <el-input v-model="editForm.breakpointLow" placeholder="如 ≤2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="折点高">
              <el-input v-model="editForm.breakpointHigh" placeholder="如 ≥8" />
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
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 折点配置弹窗 -->
    <el-dialog
      v-model="breakpointDialogVisible"
      :title="`${currentAntibiotic?.antibioticName || ''} - 折点配置`"
      width="900px"
    >
      <!-- 折点列表表格 -->
      <el-table
        :data="breakpointList"
        border
        stripe
        size="small"
        v-loading="breakpointLoading"
        max-height="300"
      >
        <el-table-column type="index" label="序号" width="50" align="center" />
        <el-table-column label="细菌" min-width="120">
          <template #default="{ row }">
            {{ bacteriaOptions.find(b => String(b.id) === String(row.bacteriaId))?.bacteriaName || row.bacteriaId }}
          </template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="100" />
        <el-table-column prop="standard" label="标准" width="80" />
        <el-table-column prop="standardVersion" label="版本" width="100" />
        <el-table-column prop="sBreakpoint" label="S界值" width="80" />
        <el-table-column prop="rBreakpoint" label="R界值" width="80" />
        <el-table-column prop="iBreakpoint" label="I界值" width="80" />
        <el-table-column prop="sDd" label="S抑菌圈" width="80" />
        <el-table-column prop="rDd" label="R抑菌圈" width="80" />
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDeleteBreakpoint(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分隔线 -->
      <el-divider content-position="left">添加折点</el-divider>

      <!-- 折点表单 -->
      <el-form
        ref="breakpointFormRef"
        :model="breakpointForm"
        :rules="breakpointRules"
        label-width="90px"
      >
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="细菌" prop="bacteriaId">
              <el-select
                v-model="breakpointForm.bacteriaId"
                filterable
                placeholder="选择细菌"
                style="width: 100%"
              >
                <el-option
                  v-for="b in bacteriaOptions"
                  :key="b.id"
                  :label="b.bacteriaName"
                  :value="b.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="方法">
              <el-select v-model="breakpointForm.method" placeholder="选择" style="width: 100%">
                <el-option value="K-B纸片法" label="K-B纸片法" />
                <el-option value="微量肉汤法" label="微量肉汤法" />
                <el-option value="E-test" label="E-test" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标准">
              <el-select v-model="breakpointForm.standard" placeholder="选择" style="width: 100%">
                <el-option value="CLSI" label="CLSI" />
                <el-option value="EUCAST" label="EUCAST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本">
              <el-input v-model="breakpointForm.standardVersion" placeholder="如 M100-2023" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="S界值">
              <el-input v-model="breakpointForm.sBreakpoint" placeholder="如 ≤2" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="R界值">
              <el-input v-model="breakpointForm.rBreakpoint" placeholder="如 ≥8" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="I界值">
              <el-input v-model="breakpointForm.iBreakpoint" placeholder="如 4" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="S抑菌圈">
              <el-input v-model="breakpointForm.sDd" placeholder="如 ≥18" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="R抑菌圈">
              <el-input v-model="breakpointForm.rDd" placeholder="如 ≤14" />
            </el-form-item>
          </el-col>
        </el-row>
        <div style="text-align: right">
          <el-button type="primary" size="small" @click="handleSaveBreakpoint">添加折点</el-button>
        </div>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getAntibioticPage,
  saveAntibiotic,
  deleteAntibiotic,
  getBreakpointPage,
  saveBreakpoint,
  deleteBreakpoint,
  getAllBacteria,
  type Antibiotic,
  type AntibioticQuery,
  type AntibioticBreakpoint,
  type Bacteria
} from '@/api/micro'

// 抗生素分类预设
const categoryOptions = [
  'β-内酰胺类',
  '青霉素类',
  '头孢菌素类',
  '氨基糖苷类',
  '喹诺酮类',
  '大环内酯类',
  '四环素类',
  '糖肽类',
  '林可酰胺类',
  '硝基咪唑类',
  '碳青霉烯类',
  '其他'
]

const loading = ref(false)
const tableData = ref<Antibiotic[]>([])
const searchForm = reactive({
  keyword: '',
  category: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 1000,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<Antibiotic>({
  id: 0,
  antibioticCode: '',
  antibioticName: '',
  antibioticNameEn: '',
  category: '',
  method: '',
  breakpointLow: '',
  breakpointHigh: '',
  status: 1
})
const formRules: FormRules = {
  antibioticCode: [{ required: true, message: '请输入抗生素代码', trigger: 'blur' }],
  antibioticName: [{ required: true, message: '请输入中文名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: AntibioticQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.category) params.category = searchForm.category
    const res = await getAntibioticPage(params)
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
  searchForm.category = ''
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
  Object.assign(editForm, {
    id: 0,
    antibioticCode: '',
    antibioticName: '',
    antibioticNameEn: '',
    category: '',
    method: '',
    breakpointLow: '',
    breakpointHigh: '',
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: Antibiotic) {
  Object.assign(editForm, {
    id: row.id,
    antibioticCode: row.antibioticCode,
    antibioticName: row.antibioticName,
    antibioticNameEn: row.antibioticNameEn ?? '',
    category: row.category ?? '',
    method: row.method ?? '',
    breakpointLow: row.breakpointLow ?? '',
    breakpointHigh: row.breakpointHigh ?? '',
    status: row.status ?? 1
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveAntibiotic({ ...editForm })
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

async function handleDelete(row: Antibiotic) {
  try {
    await ElMessageBox.confirm(
      `确认删除抗生素 "${row.antibioticName}" 吗?`,
      '提示',
      { type: 'warning' }
    )
    await deleteAntibiotic(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

// ==================== 折点配置 ====================
const breakpointDialogVisible = ref(false)
const currentAntibiotic = ref<Antibiotic | null>(null)
const breakpointList = ref<AntibioticBreakpoint[]>([])
const breakpointLoading = ref(false)
const bacteriaOptions = ref<Bacteria[]>([])
const breakpointFormRef = ref<FormInstance>()
const breakpointForm = reactive<AntibioticBreakpoint>({
  antibioticId: 0,
  bacteriaId: 0,
  method: '',
  standard: '',
  standardVersion: '',
  sBreakpoint: '',
  rBreakpoint: '',
  iBreakpoint: '',
  sDd: '',
  rDd: '',
  status: 1
})
const breakpointRules: FormRules = {
  bacteriaId: [{ required: true, message: '请选择细菌', trigger: 'change' }]
}

async function openBreakpointDialog(row: Antibiotic) {
  currentAntibiotic.value = row
  breakpointDialogVisible.value = true
  handleAddBreakpoint()
  // 加载细菌字典
  try {
    bacteriaOptions.value = (await getAllBacteria()) || []
  } catch (e) {
    bacteriaOptions.value = []
  }
  await loadBreakpoints()
}

async function loadBreakpoints() {
  if (!currentAntibiotic.value) return
  breakpointLoading.value = true
  try {
    const res = await getBreakpointPage({
      antibioticId: currentAntibiotic.value.id,
      pageSize: 100
    })
    breakpointList.value = res.records || []
  } catch (e) {
    breakpointList.value = []
  } finally {
    breakpointLoading.value = false
  }
}

function handleAddBreakpoint() {
  if (!currentAntibiotic.value) return
  Object.assign(breakpointForm, {
    id: undefined,
    antibioticId: currentAntibiotic.value.id,
    bacteriaId: 0,
    method: '',
    standard: '',
    standardVersion: '',
    sBreakpoint: '',
    rBreakpoint: '',
    iBreakpoint: '',
    sDd: '',
    rDd: '',
    status: 1
  })
}

async function handleSaveBreakpoint() {
  if (!breakpointFormRef.value) return
  await breakpointFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await saveBreakpoint({ ...breakpointForm })
      ElMessage.success('保存成功')
      handleAddBreakpoint()
      loadBreakpoints()
    } catch (e) {
      // ignore
    }
  })
}

async function handleDeleteBreakpoint(row: AntibioticBreakpoint) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确认删除该折点记录吗?', '提示', { type: 'warning' })
    await deleteBreakpoint(row.id)
    ElMessage.success('删除成功')
    loadBreakpoints()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.antibiotic-page {
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
