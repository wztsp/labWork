<template>
  <div class="title-page">
    <el-tabs v-model="activeTab" class="title-tabs">
      <!-- 人员职称记录 -->
      <el-tab-pane label="人员职称记录" name="record">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="searchForm" @submit.prevent>
            <el-form-item label="员工姓名">
              <el-input
                v-model="searchForm.staffName"
                placeholder="请输入员工姓名"
                clearable
                style="width: 180px"
                @keyup.enter="handleSearchRecord"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" plain @click="handleSearchRecord">查询</el-button>
              <el-button @click="handleResetRecord">重置</el-button>
              <el-button type="primary" @click="handleAddRecord">新增记录</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="loadingRecord" :data="filteredRecords" border stripe style="width: 100%">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="员工姓名" width="140">
              <template #default="{ row }">
                {{ row.staffName || staffNameById(row.staffId) }}
              </template>
            </el-table-column>
            <el-table-column prop="titleName" label="职称名称" min-width="140" />
            <el-table-column prop="certificateNo" label="证书编号" width="150" align="center">
              <template #default="{ row }">{{ row.certificateNo || '-' }}</template>
            </el-table-column>
            <el-table-column prop="acquireDate" label="取得日期" width="120" align="center" />
            <el-table-column prop="expiryDate" label="到期日期" width="120" align="center">
              <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="当前职称" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isCurrent === 1" type="success" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditRecord(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteRecord(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 职称字典 -->
      <el-tab-pane label="职称字典" name="dict">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" @submit.prevent>
            <el-form-item>
              <el-button type="primary" @click="handleAddDict">新增职称</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="loadingDict" :data="dictData" border stripe style="width: 100%">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="titleCode" label="职称编码" width="160" align="center" />
            <el-table-column prop="titleName" label="职称名称" min-width="160" />
            <el-table-column prop="titleLevel" label="级别" width="100" align="center">
              <template #default="{ row }">{{ row.titleLevel ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditDict(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteDict(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 人员职称记录 弹窗 -->
    <el-dialog v-model="recordDialogVisible" :title="recordForm.id ? '编辑职称记录' : '新增职称记录'" width="600px">
      <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="员工" prop="staffId">
              <el-select v-model="recordForm.staffId" filterable placeholder="请选择员工" style="width: 100%">
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
            <el-form-item label="职称" prop="titleName">
              <el-select
                v-model="recordForm.titleName"
                filterable
                allow-create
                placeholder="请选择或输入"
                style="width: 100%"
                @change="onTitleSelect"
              >
                <el-option v-for="t in dictData" :key="t.id" :label="t.titleName" :value="t.titleName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证书编号">
              <el-input v-model="recordForm.certificateNo" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否当前">
              <el-switch v-model="recordForm.isCurrent" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="取得日期">
              <el-date-picker
                v-model="recordForm.acquireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日期">
              <el-date-picker
                v-model="recordForm.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="recordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="recordSaving" @click="handleSaveRecord">保存</el-button>
      </template>
    </el-dialog>

    <!-- 职称字典 弹窗 -->
    <el-dialog v-model="dictDialogVisible" :title="dictForm.id ? '编辑职称' : '新增职称'" width="500px">
      <el-form ref="dictFormRef" :model="dictForm" :rules="dictRules" label-width="100px">
        <el-form-item label="职称编码" prop="titleCode">
          <el-input v-model="dictForm.titleCode" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="职称名称" prop="titleName">
          <el-input v-model="dictForm.titleName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="级别">
          <el-input-number v-model="dictForm.titleLevel" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dictForm.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dictForm.status">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dictSaving" @click="handleSaveDict">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getTitleDictList,
  saveTitleDict,
  deleteTitleDict,
  getAllStaffTitles,
  saveStaffTitle,
  deleteStaffTitle,
  getAllActiveStaff,
  type TitleDict,
  type StaffTitle,
  type Staff
} from '@/api/hr'

const activeTab = ref<'record' | 'dict'>('record')

// ===== 人员职称记录 =====
const loadingRecord = ref(false)
const recordData = ref<StaffTitle[]>([])
const staffOptions = ref<Staff[]>([])
const staffMap = ref(new Map<number, Staff>())
const searchForm = reactive({ staffName: '' })

const recordDialogVisible = ref(false)
const recordSaving = ref(false)
const recordFormRef = ref<FormInstance>()
const recordForm = reactive<StaffTitle>({
  id: 0,
  staffId: 0,
  titleName: '',
  certificateNo: '',
  acquireDate: '',
  expiryDate: '',
  isCurrent: 1
})
const recordRules: FormRules = {
  staffId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  titleName: [{ required: true, message: '请输入职称名称', trigger: 'blur' }]
}

const filteredRecords = computed<StaffTitle[]>(() => {
  const kw = searchForm.staffName.trim()
  if (!kw) return recordData.value
  return recordData.value.filter((r) => {
    const name = r.staffName || staffNameById(r.staffId)
    return (name || '').includes(kw)
  })
})

function staffNameById(id: number): string {
  return staffMap.value.get(id)?.name || ''
}

async function fetchRecords() {
  loadingRecord.value = true
  try {
    recordData.value = (await getAllStaffTitles()) || []
  } catch (e) {
    recordData.value = []
  } finally {
    loadingRecord.value = false
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

function handleSearchRecord() {
  // 前端过滤
}

function handleResetRecord() {
  searchForm.staffName = ''
}

function onTitleSelect(name: string) {
  const t = dictData.value.find((d) => d.titleName === name)
  if (t) recordForm.titleCode = t.titleCode
}

function handleAddRecord() {
  Object.assign(recordForm, {
    id: 0,
    staffId: 0,
    titleCode: '',
    titleName: '',
    certificateNo: '',
    acquireDate: '',
    expiryDate: '',
    isCurrent: 1
  })
  recordDialogVisible.value = true
}

function handleEditRecord(row: StaffTitle) {
  Object.assign(recordForm, {
    id: row.id,
    staffId: row.staffId,
    titleCode: row.titleCode ?? '',
    titleName: row.titleName ?? '',
    certificateNo: row.certificateNo ?? '',
    acquireDate: row.acquireDate ?? '',
    expiryDate: row.expiryDate ?? '',
    isCurrent: row.isCurrent ?? 1
  })
  recordDialogVisible.value = true
}

async function handleSaveRecord() {
  if (!recordFormRef.value) return
  await recordFormRef.value.validate(async (valid) => {
    if (!valid) return
    recordSaving.value = true
    try {
      await saveStaffTitle({ ...recordForm })
      ElMessage.success('保存成功')
      recordDialogVisible.value = false
      fetchRecords()
    } catch (e) {
      // ignore
    } finally {
      recordSaving.value = false
    }
  })
}

async function handleDeleteRecord(row: StaffTitle) {
  try {
    await ElMessageBox.confirm('确认删除该职称记录吗?', '提示', { type: 'warning' })
    await deleteStaffTitle(row.id)
    ElMessage.success('删除成功')
    fetchRecords()
  } catch (e) {
    // ignore
  }
}

// ===== 职称字典 =====
const loadingDict = ref(false)
const dictData = ref<TitleDict[]>([])
const dictDialogVisible = ref(false)
const dictSaving = ref(false)
const dictFormRef = ref<FormInstance>()
const dictForm = reactive<TitleDict>({
  id: 0,
  titleCode: '',
  titleName: '',
  titleLevel: 1,
  sortOrder: 0,
  status: 1
})
const dictRules: FormRules = {
  titleCode: [{ required: true, message: '请输入职称编码', trigger: 'blur' }],
  titleName: [{ required: true, message: '请输入职称名称', trigger: 'blur' }]
}

async function fetchDict() {
  loadingDict.value = true
  try {
    dictData.value = (await getTitleDictList()) || []
  } catch (e) {
    dictData.value = []
  } finally {
    loadingDict.value = false
  }
}

function handleAddDict() {
  Object.assign(dictForm, { id: 0, titleCode: '', titleName: '', titleLevel: 1, sortOrder: 0, status: 1 })
  dictDialogVisible.value = true
}

function handleEditDict(row: TitleDict) {
  Object.assign(dictForm, {
    id: row.id,
    titleCode: row.titleCode || '',
    titleName: row.titleName || '',
    titleLevel: row.titleLevel ?? 1,
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1
  })
  dictDialogVisible.value = true
}

async function handleSaveDict() {
  if (!dictFormRef.value) return
  await dictFormRef.value.validate(async (valid) => {
    if (!valid) return
    dictSaving.value = true
    try {
      await saveTitleDict({ ...dictForm })
      ElMessage.success('保存成功')
      dictDialogVisible.value = false
      fetchDict()
    } catch (e) {
      // ignore
    } finally {
      dictSaving.value = false
    }
  })
}

async function handleDeleteDict(row: TitleDict) {
  try {
    await ElMessageBox.confirm(`确认删除职称 "${row.titleName}" 吗?`, '提示', { type: 'warning' })
    await deleteTitleDict(row.id)
    ElMessage.success('删除成功')
    fetchDict()
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  loadStaffOptions()
  fetchRecords()
  fetchDict()
})
</script>

<style scoped lang="scss">
.title-page {
  padding: 16px;

  .title-tabs {
    :deep(.el-tabs__content) {
      padding-top: 0;
    }
  }

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }
}
</style>
