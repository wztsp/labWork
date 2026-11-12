<template>
  <div class="config-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="参数Key">
          <el-input
            v-model="searchForm.configKey"
            placeholder="请输入"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="参数名称">
          <el-input
            v-model="searchForm.configName"
            placeholder="请输入"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增参数</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="configKey" label="参数Key" min-width="180" show-overflow-tooltip />
        <el-table-column prop="configValue" label="参数值" min-width="180" show-overflow-tooltip />
        <el-table-column prop="configName" label="参数名称" width="160" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center" fixed="right">
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
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑参数' : '新增参数'" width="560px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="参数Key" prop="configKey">
              <el-input v-model="editForm.configKey" :disabled="!!editForm.id" placeholder="如: system.title" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="参数值" prop="configValue">
              <el-input v-model="editForm.configValue" type="textarea" :rows="2" placeholder="请输入参数值" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="参数名称" prop="configName">
              <el-input v-model="editForm.configName" placeholder="请输入参数名称" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getConfigList,
  createConfig,
  updateConfig,
  deleteConfig
} from '@/api/system/config'

interface SysConfig {
  id: number
  configKey: string
  configValue: string
  configName: string
  remark?: string
}

const loading = ref(false)
const tableData = ref<SysConfig[]>([])
const searchForm = reactive({
  configKey: '',
  configName: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<SysConfig>({
  id: 0,
  configKey: '',
  configValue: '',
  configName: '',
  remark: ''
})
const formRules: FormRules = {
  configKey: [{ required: true, message: '请输入参数Key', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await getConfigList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      configKey: searchForm.configKey || undefined,
      configName: searchForm.configName || undefined
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.configKey = ''
  searchForm.configName = ''
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
    configKey: '',
    configValue: '',
    configName: '',
    remark: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: SysConfig) {
  Object.assign(editForm, {
    id: row.id,
    configKey: row.configKey,
    configValue: row.configValue,
    configName: row.configName,
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
      const data = { ...editForm }
      if (editForm.id) {
        await updateConfig(editForm.id, data)
      } else {
        await createConfig(data)
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

async function handleDelete(row: SysConfig) {
  try {
    await ElMessageBox.confirm(`确认删除参数 "${row.configName}" 吗?`, '提示', { type: 'warning' })
    await deleteConfig(row.id)
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
.config-page {
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
