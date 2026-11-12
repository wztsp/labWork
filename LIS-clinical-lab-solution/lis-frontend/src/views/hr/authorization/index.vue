<template>
  <div class="auth-page">
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
        <el-form-item label="授权类型">
          <el-select v-model="searchForm.authType" placeholder="全部" clearable style="width: 180px">
            <el-option
              v-for="opt in authTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="有效" :value="1" />
            <el-option label="失效" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增授权</el-button>
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
        <el-table-column label="授权类型" width="160" align="center">
          <template #default="{ row }">
            <el-tag :type="authTypeTagType(row.authType)" size="small">
              {{ authTypeLabel(row.authType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authScope" label="授权范围" min-width="180">
          <template #default="{ row }">{{ row.authScope || '-' }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" align="center" />
        <el-table-column prop="endDate" label="结束日期" width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'auth-expired': isExpired(row.endDate) }">{{ row.endDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="授权人" width="140" align="center">
          <template #default="{ row }">
            {{ row.grantorName || (row.grantorId ? `员工${row.grantorId}` : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '有效' : '失效' }}
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
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑授权' : '新增授权'" width="640px">
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
            <el-form-item label="授权类型" prop="authType">
              <el-select v-model="editForm.authType" placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="opt in authTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="授权范围">
              <el-input v-model="editForm.authScope" placeholder="如: 报告审核范围 / 仪器名称 / 项目名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker
                v-model="editForm.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker
                v-model="editForm.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权人">
              <el-select
                v-model="editForm.grantorId"
                filterable
                clearable
                placeholder="请选择授权人"
                style="width: 100%"
                @change="onGrantorChange"
              >
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
            <el-form-item label="状态">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="有效" :value="1" />
                <el-option label="失效" :value="0" />
              </el-select>
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
import dayjs from 'dayjs'
import {
  getAllAuthorizations,
  saveAuthorization,
  deleteAuthorization,
  getAllActiveStaff,
  type StaffAuthorization,
  type Staff
} from '@/api/hr'

const authTypeOptions = [
  { value: 'REPORT_AUDIT', label: '报告审核' },
  { value: 'INSTRUMENT_OP', label: '仪器操作' },
  { value: 'PROJECT_OP', label: '项目检测' }
]

const authTypeMap: Record<string, { label: string; type: string }> = {
  REPORT_AUDIT: { label: '报告审核', type: 'primary' },
  INSTRUMENT_OP: { label: '仪器操作', type: 'success' },
  PROJECT_OP: { label: '项目检测', type: 'warning' }
}

function authTypeLabel(code: string): string {
  return authTypeMap[code]?.label || code || '-'
}

function authTypeTagType(code: string): any {
  return authTypeMap[code]?.type || 'info'
}

function isExpired(endDate?: string): boolean {
  if (!endDate) return false
  return dayjs(endDate).isBefore(dayjs(), 'day')
}

const loading = ref(false)
const tableData = ref<StaffAuthorization[]>([])
const staffOptions = ref<Staff[]>([])
const staffMap = ref(new Map<number, Staff>())
const searchForm = reactive({
  staffName: '',
  authType: '',
  status: undefined as number | undefined
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<StaffAuthorization>({
  id: 0,
  staffId: 0,
  authType: '',
  authScope: '',
  startDate: '',
  endDate: '',
  grantorId: undefined,
  grantorName: '',
  status: 1
})
const formRules: FormRules = {
  staffId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  authType: [{ required: true, message: '请选择授权类型', trigger: 'change' }]
}

const filteredList = computed<StaffAuthorization[]>(() => {
  return tableData.value.filter((r) => {
    const name = r.staffName || staffNameById(r.staffId)
    if (searchForm.staffName && !(name || '').includes(searchForm.staffName.trim())) return false
    if (searchForm.authType && r.authType !== searchForm.authType) return false
    if (searchForm.status !== undefined && r.status !== searchForm.status) return false
    return true
  })
})

function staffNameById(id: number): string {
  return staffMap.value.get(id)?.name || ''
}

async function fetchList() {
  loading.value = true
  try {
    tableData.value = (await getAllAuthorizations()) || []
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
  searchForm.authType = ''
  searchForm.status = undefined
}

function onGrantorChange(staffId: number | undefined) {
  const s = staffOptions.value.find((x) => x.id === staffId)
  editForm.grantorName = s?.name || ''
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0,
    staffId: 0,
    authType: '',
    authScope: '',
    startDate: '',
    endDate: '',
    grantorId: undefined,
    grantorName: '',
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: StaffAuthorization) {
  Object.assign(editForm, {
    id: row.id,
    staffId: row.staffId,
    authType: row.authType || '',
    authScope: row.authScope ?? '',
    startDate: row.startDate ?? '',
    endDate: row.endDate ?? '',
    grantorId: row.grantorId || undefined,
    grantorName: row.grantorName ?? '',
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
      await saveAuthorization({ ...editForm })
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

async function handleDelete(row: StaffAuthorization) {
  try {
    await ElMessageBox.confirm('确认删除该授权记录吗?', '提示', { type: 'warning' })
    await deleteAuthorization(row.id)
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
.auth-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .auth-expired {
    color: var(--el-color-danger);
  }
}
</style>
