<template>
  <div class="collection-method-page">
    <el-card shadow="never">
      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="编码/名称"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增采集方式</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="methodCode" label="方式编码" width="180" />
        <el-table-column prop="methodName" label="方式名称" width="140" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该采集方式?" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editForm.id ? '编辑采集方式' : '新增采集方式'"
      width="480px"
    >
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="90px">
        <el-form-item label="方式编码" prop="methodCode">
          <el-input v-model="editForm.methodCode" placeholder="如: OUTPATIENT" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="方式名称" prop="methodName">
          <el-input v-model="editForm.methodName" placeholder="如: 门诊采集" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="如: 门诊患者标本采集" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getCollectionMethodList,
  saveCollectionMethod,
  deleteCollectionMethod,
  type CollectionMethod,
  type CollectionMethodQuery
} from '@/api/base'

const loading = ref(false)
const saving = ref(false)
const tableData = ref<CollectionMethod[]>([])
const total = ref(0)
const queryParams = reactive<CollectionMethodQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: undefined
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<CollectionMethod>({
  id: 0,
  methodCode: '',
  methodName: '',
  description: '',
  status: 1,
  sortOrder: 0
})

const rules = {
  methodCode: [{ required: true, message: '请输入方式编码', trigger: 'blur' }],
  methodName: [{ required: true, message: '请输入方式名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getCollectionMethodList(queryParams)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function resetAndFetch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  fetchList()
}

function handleSizeChange(size: number) {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  fetchList()
}

function handlePageChange(page: number) {
  queryParams.pageNum = page
  fetchList()
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0, methodCode: '', methodName: '', description: '', status: 1, sortOrder: 0
  })
  dialogVisible.value = true
}

function handleEdit(row: CollectionMethod) {
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveCollectionMethod({ ...editForm })
      ElMessage.success(editForm.id ? '更新成功' : '新增成功')
      dialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: CollectionMethod) {
  try {
    await deleteCollectionMethod(Number(row.id))
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // ignore
  }
}

function formatTime(time?: string) {
  if (!time) return ''
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.collection-method-page {
  padding: 16px;

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
