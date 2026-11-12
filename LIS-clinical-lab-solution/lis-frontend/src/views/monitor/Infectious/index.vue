<template>
  <div class="infectious-page">
    <el-card shadow="never">
      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="检验项目名称"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增传染病项目</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="testItemName" label="检验项目" min-width="180" />
        <el-table-column prop="positiveText" label="阳性判定文本" width="140" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该配置?" @confirm="handleDelete(row)">
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
      :title="editForm.id ? '编辑传染病项目' : '新增传染病项目'"
      width="520px"
    >
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="110px">
        <el-form-item label="检验项目" prop="testItemId">
          <el-select
            v-model="editForm.testItemId"
            placeholder="请选择检验项目"
            filterable
            style="width: 100%"
            @change="onItemChange"
          >
            <el-option v-for="p in panels" :key="p.id" :label="p.itemName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="阳性判定文本" prop="positiveText">
          <el-input v-model="editForm.positiveText" placeholder="如: 阳性 / 1+" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
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
  getInfectiousPage,
  saveInfectious,
  deleteInfectious,
  type InfectiousItem,
  type InfectiousQuery
} from '@/api/monitor'
import { getAllTestItems, type TestItem } from '@/api/base'

const loading = ref(false)
const saving = ref(false)
const tableData = ref<InfectiousItem[]>([])
const total = ref(0)
const panels = ref<TestItem[]>([])
const queryParams = reactive<InfectiousQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<InfectiousItem>({
  id: 0,
  testItemId: undefined,
  testItemName: '',
  positiveText: '阳性',
  enabled: 1,
  remark: ''
})

const rules = {
  testItemId: [{ required: true, message: '请选择检验项目', trigger: 'change' }],
  positiveText: [{ required: true, message: '请输入阳性判定文本', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getInfectiousPage(queryParams)
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

function onItemChange(id: number) {
  const p = panels.value.find(x => x.id === id)
  editForm.testItemName = p ? p.itemName : ''
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0, testItemId: undefined, testItemName: '', positiveText: '阳性', enabled: 1, remark: ''
  })
  dialogVisible.value = true
}

function handleEdit(row: InfectiousItem) {
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveInfectious({ ...editForm })
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

async function handleDelete(row: InfectiousItem) {
  try {
    await deleteInfectious(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // ignore
  }
}

async function loadDicts() {
  try {
    const items = (await getAllTestItems()) || []
    panels.value = items.filter(i => i.isPanel === 1)
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  loadDicts()
  fetchList()
})
</script>

<style scoped lang="scss">
.infectious-page {
  padding: 16px;

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>