<template>
  <div class="maintenance-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="仪器">
          <el-select
            v-model="searchForm.instrumentId"
            placeholder="全部仪器"
            clearable
            filterable
            style="width: 240px"
          >
            <el-option
              v-for="item in instrumentList"
              :key="item.id"
              :label="`${item.instrumentCode} - ${item.instrumentName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="维护类型">
          <el-select v-model="searchForm.maintenanceType" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="(label, key) in PLAN_TYPE_MAP" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增维护记录</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 维护日志列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="维护日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.maintenanceDate) }}</template>
        </el-table-column>
        <el-table-column label="仪器" min-width="180">
          <template #default="{ row }">{{ getInstrumentName(row.instrumentId) }}</template>
        </el-table-column>
        <el-table-column label="维护类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.maintenanceType" size="small">{{ PLAN_TYPE_MAP[row.maintenanceType] || row.maintenanceType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="模板" min-width="140">
          <template #default="{ row }">{{ getTemplateName(row.templateId) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="执行人" width="110" align="center" />
        <el-table-column prop="reviewerName" label="复核人" width="110" align="center" />
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.result !== null && row.result !== undefined" :type="MAINT_RESULT_TAG_TYPE[row.result]" size="small">
              {{ MAINT_RESULT_MAP[row.result] || '-' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑维护记录' : '新增维护记录'" width="640px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="仪器" prop="instrumentId">
              <el-select v-model="editForm.instrumentId" placeholder="请选择仪器" filterable style="width: 100%">
                <el-option
                  v-for="item in instrumentList"
                  :key="item.id"
                  :label="`${item.instrumentCode} - ${item.instrumentName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维护模板">
              <el-select v-model="editForm.templateId" placeholder="可选" clearable filterable style="width: 100%">
                <el-option
                  v-for="t in templateList"
                  :key="t.id"
                  :label="t.templateName"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维护类型" prop="maintenanceType">
              <el-select v-model="editForm.maintenanceType" placeholder="请选择" style="width: 100%">
                <el-option v-for="(label, key) in PLAN_TYPE_MAP" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维护日期" prop="maintenanceDate">
              <el-date-picker
                v-model="editForm.maintenanceDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行人">
              <el-input v-model="editForm.operatorName" placeholder="执行人员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="复核人">
              <el-input v-model="editForm.reviewerName" placeholder="复核人员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结果">
              <el-select v-model="editForm.result" placeholder="请选择" style="width: 100%">
                <el-option v-for="(label, key) in MAINT_RESULT_MAP" :key="key" :label="label" :value="Number(key)" />
              </el-select>
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
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import { getAllInstruments, type Instrument } from '@/api/instrument'
import {
  getMaintenanceLogPage,
  saveMaintenanceLog,
  deleteMaintenanceLog,
  getMaintenanceTemplatePage,
  PLAN_TYPE_MAP,
  MAINT_RESULT_MAP,
  MAINT_RESULT_TAG_TYPE,
  type MaintenanceLog,
  type MaintenanceLogQuery,
  type MaintenanceTemplate
} from '@/api/instrument'

const userStore = useUserStore()

const instrumentList = ref<Instrument[]>([])
const templateList = ref<MaintenanceTemplate[]>([])

function getInstrumentName(instrumentId?: number): string {
  if (!instrumentId) return '-'
  const ins = instrumentList.value.find((i) => i.id === instrumentId)
  return ins ? ins.instrumentName : '-'
}

function getTemplateName(templateId?: number): string {
  if (!templateId) return '-'
  const t = templateList.value.find((i) => i.id === templateId)
  return t ? t.templateName : '-'
}

function formatDate(date?: string): string {
  return date ? dayjs(date).format('YYYY-MM-DD') : '-'
}

const loading = ref(false)
const tableData = ref<MaintenanceLog[]>([])
const searchForm = reactive({
  instrumentId: undefined as number | undefined,
  maintenanceType: '' as string
})
const dateRange = ref<[string, string] | []>([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<MaintenanceLog>({
  id: 0,
  instrumentId: undefined as number | undefined,
  templateId: undefined,
  maintenanceDate: dayjs().format('YYYY-MM-DD'),
  maintenanceType: 'PM',
  operatorName: '',
  reviewerName: '',
  result: 1,
  remark: ''
})
const formRules: FormRules = {
  instrumentId: [{ required: true, message: '请选择仪器', trigger: 'change' }],
  maintenanceType: [{ required: true, message: '请选择维护类型', trigger: 'change' }],
  maintenanceDate: [{ required: true, message: '请选择维护日期', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: MaintenanceLogQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.instrumentId) params.instrumentId = searchForm.instrumentId
    if (searchForm.maintenanceType) params.maintenanceType = searchForm.maintenanceType
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getMaintenanceLogPage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function loadInstruments() {
  try {
    instrumentList.value = await getAllInstruments()
  } catch (e) {
    instrumentList.value = []
  }
}

async function loadTemplates() {
  try {
    const res = await getMaintenanceTemplatePage({ pageSize: 1000 })
    templateList.value = res.records || []
  } catch (e) {
    templateList.value = []
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.instrumentId = undefined
  searchForm.maintenanceType = ''
  dateRange.value = []
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
    instrumentId: undefined,
    templateId: undefined,
    maintenanceDate: dayjs().format('YYYY-MM-DD'),
    maintenanceType: 'PM',
    operatorId: userStore.userId || undefined,
    operatorName: userStore.realName || '',
    reviewerName: '',
    result: 1,
    remark: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: MaintenanceLog) {
  Object.assign(editForm, {
    id: row.id,
    instrumentId: row.instrumentId,
    templateId: row.templateId,
    maintenanceDate: row.maintenanceDate ?? dayjs().format('YYYY-MM-DD'),
    maintenanceType: row.maintenanceType ?? 'PM',
    operatorId: row.operatorId,
    operatorName: row.operatorName ?? '',
    reviewerId: row.reviewerId,
    reviewerName: row.reviewerName ?? '',
    result: row.result ?? 1,
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
      await saveMaintenanceLog({ ...editForm })
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

async function handleDelete(row: MaintenanceLog) {
  try {
    await ElMessageBox.confirm('确认删除该维护记录吗?', '提示', { type: 'warning' })
    await deleteMaintenanceLog(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
  loadInstruments()
  loadTemplates()
})
</script>

<style scoped lang="scss">
.maintenance-page {
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
