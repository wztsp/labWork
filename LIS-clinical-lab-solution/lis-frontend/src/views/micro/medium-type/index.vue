<template>
  <div class="medium-type-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="keyword"
            placeholder="编码/名称/缩写"
            clearable
            style="width: 220px"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchList">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增培养基</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="培养基编码" width="140" align="center">
          <template #default="{ row }">{{ row.mediumCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="mediumName" label="培养基名称" min-width="160" />
        <el-table-column label="缩写" width="120" align="center">
          <template #default="{ row }">{{ row.mediumAbbr || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="editForm.id ? '编辑培养基' : '新增培养基'" width="520px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-form-item label="培养基编码" prop="mediumCode">
          <el-input v-model="editForm.mediumCode" placeholder="如: BAP" />
        </el-form-item>
        <el-form-item label="培养基名称" prop="mediumName">
          <el-input v-model="editForm.mediumName" placeholder="如: 血平板" />
        </el-form-item>
        <el-form-item label="缩写">
          <el-input v-model="editForm.mediumAbbr" placeholder="打印条码用缩写, 如: 血" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" placeholder="备注(可选)" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  getMediumTypePage,
  saveMediumType,
  deleteMediumType,
  type MediumType
} from '@/api/micro'

const loading = ref(false)
const tableData = ref<MediumType[]>([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const formVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<MediumType>({
  id: undefined,
  mediumCode: '',
  mediumName: '',
  mediumAbbr: '',
  remark: '',
  status: 1
})
const formRules: FormRules = {
  mediumCode: [{ required: true, message: '请输入培养基编码', trigger: 'blur' }],
  mediumName: [{ required: true, message: '请输入培养基名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getMediumTypePage({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  Object.assign(editForm, { id: undefined, mediumCode: '', mediumName: '', mediumAbbr: '', remark: '', status: 1 })
  formVisible.value = true
}

function handleEdit(row: MediumType) {
  Object.assign(editForm, {
    id: row.id,
    mediumCode: row.mediumCode ?? '',
    mediumName: row.mediumName ?? '',
    mediumAbbr: row.mediumAbbr ?? '',
    remark: row.remark ?? '',
    status: row.status ?? 1
  })
  formVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveMediumType({ ...editForm })
      ElMessage.success('保存成功')
      formVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: MediumType) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除培养基 "${row.mediumName}" 吗?`, '提示', { type: 'warning' })
    await deleteMediumType(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.medium-type-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0;
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