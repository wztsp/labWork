<template>
  <div class="bacteria-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="代码/名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="编码/中文名/拉丁名"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="革兰氏">
          <el-select v-model="searchForm.gramType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="g in gramTypeOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="菌属分类">
          <el-select v-model="searchForm.category" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增细菌</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="bacteriaCode" label="编码" width="100" />
        <el-table-column prop="bacteriaName" label="中文名称" min-width="140" />
        <el-table-column prop="bacteriaNameLatin" label="拉丁学名" min-width="180" />
        <el-table-column label="革兰氏" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.gramType === 'G+'" type="danger" size="small">G+</el-tag>
            <el-tag v-else-if="row.gramType === 'G-'" type="primary" size="small">G-</el-tag>
            <el-tag v-else-if="row.gramType === '真菌'" type="warning" size="small">真菌</el-tag>
            <el-tag v-else-if="row.gramType === '抗酸菌'" type="success" size="small">抗酸</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="菌属分类" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.bacteriaCategory" type="info" size="small" effect="plain">{{ row.bacteriaCategory }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="morphology" label="形态" width="100" align="center" />
        <el-table-column label="常见菌" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isCommon === 1" type="success" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[20, 50, 100, 500, 1000]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑细菌' : '新增细菌'" width="680px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="细菌编码" prop="bacteriaCode">
              <el-input v-model="editForm.bacteriaCode" :disabled="!!editForm.id" placeholder="如 ECO" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中文名称" prop="bacteriaName">
              <el-input v-model="editForm.bacteriaName" placeholder="如 大肠埃希菌" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="拉丁学名">
              <el-input v-model="editForm.bacteriaNameLatin" placeholder="如 Escherichia coli" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="革兰氏属性">
              <el-select v-model="editForm.gramType" placeholder="请选择" style="width: 100%">
                <el-option v-for="g in gramTypeOptions" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菌属分类">
              <el-select v-model="editForm.bacteriaCategory" placeholder="请选择" filterable allow-create style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="形态">
              <el-select v-model="editForm.morphology" placeholder="请选择" clearable filterable allow-create style="width: 100%">
                <el-option v-for="m in morphologyOptions" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="常见菌">
              <el-switch
                v-model="editForm.isCommon"
                :active-value="1"
                :inactive-value="0"
                active-text="是"
                inactive-text="否"
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getBacteriaPage,
  saveBacteria,
  deleteBacteria,
  type Bacteria,
  type BacteriaQuery
} from '@/api/micro'

const gramTypeOptions = ['G+', 'G-', '真菌', '抗酸菌']
const categoryOptions = [
  '肠杆菌科',
  '非发酵菌',
  '葡萄球菌属',
  '链球菌属',
  '肠球菌属',
  '奈瑟菌科',
  '弧菌科',
  '弯曲菌',
  '嗜血杆菌',
  '其他G-杆菌',
  '芽孢杆菌属',
  '梭菌属',
  '其他G+杆菌',
  '放线菌',
  '分枝杆菌',
  '厌氧菌',
  '真菌'
]
const morphologyOptions = ['球菌', '杆菌', '球杆菌', '弯曲杆菌', '弧菌', '酵母样', '丝状真菌']

const loading = ref(false)
const tableData = ref<Bacteria[]>([])
const searchForm = reactive({
  keyword: '',
  gramType: '',
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
const editForm = reactive<Bacteria>({
  id: 0,
  bacteriaCode: '',
  bacteriaName: '',
  bacteriaNameLatin: '',
  gramType: '',
  bacteriaCategory: '',
  morphology: '',
  isCommon: 0,
  status: 1
})
const formRules: FormRules = {
  bacteriaCode: [{ required: true, message: '请输入细菌编码', trigger: 'blur' }],
  bacteriaName: [{ required: true, message: '请输入中文名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: BacteriaQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.gramType) params.gramType = searchForm.gramType
    if (searchForm.category) params.category = searchForm.category
    const res = await getBacteriaPage(params)
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
  searchForm.gramType = ''
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
    bacteriaCode: '',
    bacteriaName: '',
    bacteriaNameLatin: '',
    gramType: '',
    bacteriaCategory: '',
    morphology: '',
    isCommon: 0,
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: Bacteria) {
  Object.assign(editForm, {
    id: row.id,
    bacteriaCode: row.bacteriaCode,
    bacteriaName: row.bacteriaName,
    bacteriaNameLatin: row.bacteriaNameLatin ?? '',
    gramType: row.gramType ?? '',
    bacteriaCategory: row.bacteriaCategory ?? '',
    morphology: row.morphology ?? '',
    isCommon: row.isCommon ?? 0,
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
      await saveBacteria({ ...editForm })
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

async function handleDelete(row: Bacteria) {
  try {
    await ElMessageBox.confirm(
      `确认删除细菌 "${row.bacteriaName}" 吗?`,
      '提示',
      { type: 'warning' }
    )
    await deleteBacteria(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.bacteria-page {
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
