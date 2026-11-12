<template>
  <div class="training-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="员工姓名">
          <el-input
            v-model="searchForm.staffName"
            placeholder="请输入员工姓名"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="培训主题">
          <el-input
            v-model="searchForm.trainingName"
            placeholder="请输入培训主题"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="培训类型">
          <el-select v-model="searchForm.trainingType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in trainingTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增记录</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredList" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="员工姓名" width="140">
          <template #default="{ row }">
            {{ row.staffName || staffNameById(row.staffId) }}
          </template>
        </el-table-column>
        <el-table-column prop="trainingName" label="培训主题" min-width="180" />
        <el-table-column prop="trainingType" label="培训类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.trainingType" size="small" type="info">{{ row.trainingType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="trainingDate" label="培训日期" width="120" align="center" />
        <el-table-column prop="trainer" label="培训人" width="120" align="center">
          <template #default="{ row }">{{ row.trainer || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.result === '合格'" type="success" size="small">合格</el-tag>
            <el-tag v-else-if="row.result === '不合格'" type="danger" size="small">不合格</el-tag>
            <span v-else>{{ row.result || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑培训记录' : '新增培训记录'" width="640px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="员工" prop="staffId">
              <el-select v-model="editForm.staffId" filterable placeholder="请选择员工" style="width: 100%">
                <el-option
                  v-for="s in staffOptions"
                  :key="s.id"
                  :label="`${s.name} (${s.staffNo})`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训主题" prop="trainingName">
              <el-input v-model="editForm.trainingName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训类型">
              <el-select v-model="editForm.trainingType" placeholder="请选择" style="width: 100%" clearable>
                <el-option v-for="t in trainingTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训日期">
              <el-date-picker
                v-model="editForm.trainingDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训人">
              <el-input v-model="editForm.trainer" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结果">
              <el-select v-model="editForm.result" placeholder="请选择" style="width: 100%" clearable>
                <el-option label="合格" value="合格" />
                <el-option label="不合格" value="不合格" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getAllTrainings,
  saveTraining,
  deleteTraining,
  getAllActiveStaff,
  type StaffTraining,
  type Staff
} from '@/api/hr'

const trainingTypeOptions = ['岗前培训', '在岗培训', '继续教育', '外部培训']

const loading = ref(false)
const tableData = ref<StaffTraining[]>([])
const staffOptions = ref<Staff[]>([])
const staffMap = ref(new Map<number, Staff>())
const searchForm = reactive({
  staffName: '',
  trainingName: '',
  trainingType: ''
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<StaffTraining>({
  id: 0,
  staffId: 0,
  trainingName: '',
  trainingType: '',
  trainingDate: '',
  trainer: '',
  result: '',
  remark: ''
})
const formRules: FormRules = {
  staffId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  trainingName: [{ required: true, message: '请输入培训主题', trigger: 'blur' }]
}

const filteredList = computed<StaffTraining[]>(() => {
  return tableData.value.filter((r) => {
    const name = r.staffName || staffNameById(r.staffId)
    if (searchForm.staffName && !(name || '').includes(searchForm.staffName.trim())) return false
    if (searchForm.trainingName && !(r.trainingName || '').includes(searchForm.trainingName.trim())) return false
    if (searchForm.trainingType && r.trainingType !== searchForm.trainingType) return false
    return true
  })
})

function staffNameById(id: number): string {
  return staffMap.value.get(id)?.name || ''
}

async function fetchList() {
  loading.value = true
  try {
    tableData.value = (await getAllTrainings()) || []
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

async function loadStaffOptions() {
  try {
    staffOptions.value = await getAllActiveStaff()
    staffMap.value = new Map(staffOptions.value.map((s) => [s.id, s]))
  } catch (e) {
    staffOptions.value = []
  }
}

function handleSearch() {
  // 前端过滤
}

function handleReset() {
  searchForm.staffName = ''
  searchForm.trainingName = ''
  searchForm.trainingType = ''
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0,
    staffId: 0,
    trainingName: '',
    trainingType: '',
    trainingDate: '',
    trainer: '',
    result: '',
    remark: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: StaffTraining) {
  Object.assign(editForm, {
    id: row.id,
    staffId: row.staffId,
    trainingName: row.trainingName || '',
    trainingType: row.trainingType ?? '',
    trainingDate: row.trainingDate ?? '',
    trainer: row.trainer ?? '',
    result: row.result ?? '',
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
      await saveTraining({ ...editForm })
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

async function handleDelete(row: StaffTraining) {
  try {
    await ElMessageBox.confirm(`确认删除培训记录 "${row.trainingName}" 吗?`, '提示', { type: 'warning' })
    await deleteTraining(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  loadStaffOptions()
  fetchList()
})
</script>

<style scoped lang="scss">
.training-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }
}
</style>
