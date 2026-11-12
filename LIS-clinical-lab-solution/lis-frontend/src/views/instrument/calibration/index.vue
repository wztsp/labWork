<template>
  <div class="calibration-page">
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
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增校准</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="calibrationNo" label="校准编号" width="150" />
        <el-table-column label="仪器" min-width="180">
          <template #default="{ row }">{{ getInstrumentName(row.instrumentId) }}</template>
        </el-table-column>
        <el-table-column label="校准类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.calibrationType" :type="getTypeTagType(row.calibrationType)" size="small">
              {{ row.calibrationType }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="校准时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.calibrationTime) }}</template>
        </el-table-column>
        <el-table-column label="下次校准时间" width="160" align="center">
          <template #default="{ row }">
            <span :class="{ 'warn-text': isExpiringSoon(row.nextCalibrationTime) }">
              {{ formatTime(row.nextCalibrationTime) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" align="center" />
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getResultTagType(row.result)" size="small">
              {{ getResultText(row.result) }}
            </el-tag>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑校准记录' : '新增校准记录'" width="680px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="校准编号" prop="calibrationNo">
              <el-input v-model="editForm.calibrationNo" :disabled="!!editForm.id" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仪器" prop="instrumentId">
              <el-select
                v-model="editForm.instrumentId"
                placeholder="请选择仪器"
                filterable
                style="width: 100%"
              >
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
            <el-form-item label="校准类型">
              <el-select v-model="editForm.calibrationType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in calibrationTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结果">
              <el-select v-model="editForm.result" placeholder="请选择" style="width: 100%">
                <el-option value="PASS" label="PASS" />
                <el-option value="FAIL" label="FAIL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="校准时间">
              <el-date-picker
                v-model="editForm.calibrationTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次校准时间">
              <el-date-picker
                v-model="editForm.nextCalibrationTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作人">
              <el-input v-model="editForm.operatorName" placeholder="操作人员姓名" />
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
import { getAllInstruments, type Instrument } from '@/api/instrument'
import {
  getCalibrationPage,
  saveCalibration,
  deleteCalibration,
  type InstrumentCalibration,
  type CalibrationQuery
} from '@/api/instrument/calibration'

// 校准类型预设: 日常/月度/季度/年度
const calibrationTypes = ['日常', '月度', '季度', '年度']

// 校准类型 el-tag
const typeTagTypeMap: Record<string, string> = {
  日常: 'info',
  月度: 'primary',
  季度: 'warning',
  年度: 'danger'
}

function getTypeTagType(type: string): string {
  return typeTagTypeMap[type] || 'info'
}

// 结果 el-tag: PASS=成功, FAIL=危险
function getResultTagType(result?: string): string {
  if (result === 'PASS') return 'success'
  if (result === 'FAIL') return 'danger'
  return 'info'
}

function getResultText(result?: string): string {
  if (result === 'PASS') return 'PASS'
  if (result === 'FAIL') return 'FAIL'
  return '-'
}

function formatTime(time?: string): string {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 下次校准时间临近(30天内)高亮
function isExpiringSoon(time?: string): boolean {
  if (!time) return false
  const diff = dayjs(time).diff(dayjs(), 'day')
  return diff <= 30 && diff >= 0
}

// 仪器列表(下拉)
const instrumentList = ref<Instrument[]>([])

function getInstrumentName(instrumentId?: number): string {
  if (!instrumentId) return '-'
  const ins = instrumentList.value.find((i) => i.id === instrumentId)
  return ins ? ins.instrumentName : '-'
}

const loading = ref(false)
const tableData = ref<InstrumentCalibration[]>([])
const searchForm = reactive({
  instrumentId: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<InstrumentCalibration>({
  id: 0,
  instrumentId: undefined as number | undefined,
  calibrationNo: '',
  calibrationType: '',
  calibrationTime: '',
  nextCalibrationTime: '',
  operatorId: undefined,
  operatorName: '',
  result: 'PASS',
  remark: '',
  createTime: ''
})
const formRules: FormRules = {
  calibrationNo: [{ required: true, message: '请输入校准编号', trigger: 'blur' }],
  instrumentId: [{ required: true, message: '请选择仪器', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: CalibrationQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.instrumentId) params.instrumentId = searchForm.instrumentId
    const res = await getCalibrationPage(params)
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

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.instrumentId = undefined
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
    calibrationNo: '',
    calibrationType: '',
    calibrationTime: '',
    nextCalibrationTime: '',
    operatorId: undefined,
    operatorName: '',
    result: 'PASS',
    remark: '',
    createTime: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: InstrumentCalibration) {
  Object.assign(editForm, {
    id: row.id,
    instrumentId: row.instrumentId,
    calibrationNo: row.calibrationNo,
    calibrationType: row.calibrationType ?? '',
    calibrationTime: row.calibrationTime ?? '',
    nextCalibrationTime: row.nextCalibrationTime ?? '',
    operatorId: row.operatorId,
    operatorName: row.operatorName ?? '',
    result: row.result ?? 'PASS',
    remark: row.remark ?? '',
    createTime: row.createTime ?? ''
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveCalibration({ ...editForm })
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

async function handleDelete(row: InstrumentCalibration) {
  try {
    await ElMessageBox.confirm(
      `确认删除校准记录 "${row.calibrationNo}" 吗?`,
      '提示',
      { type: 'warning' }
    )
    await deleteCalibration(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
  loadInstruments()
})
</script>

<style scoped lang="scss">
.calibration-page {
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

    .warn-text {
      color: #e6a23c;
      font-weight: 600;
    }
  }
}
</style>
