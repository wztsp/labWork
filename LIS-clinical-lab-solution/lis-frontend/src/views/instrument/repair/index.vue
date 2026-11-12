<template>
  <div class="repair-page">
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
          <el-button type="primary" @click="handleAdd">新增维修</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="repairNo" label="维修编号" width="150" />
        <el-table-column label="仪器" min-width="180">
          <template #default="{ row }">{{ getInstrumentName(row.instrumentId) }}</template>
        </el-table-column>
        <el-table-column label="故障时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.faultTime) }}</template>
        </el-table-column>
        <el-table-column prop="faultDesc" label="故障描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="维修人" width="110" align="center">
          <template #default="{ row }">{{ row.repairman || '-' }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.finishTime) }}</template>
        </el-table-column>
        <el-table-column label="停机时长" width="110" align="center">
          <template #default="{ row }">
            {{ row.downtimeHours != null ? `${row.downtimeHours}h` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="partsReplaced" label="更换配件" min-width="160" show-overflow-tooltip />
        <el-table-column label="费用" width="110" align="right">
          <template #default="{ row }">
            {{ row.cost != null ? `¥${Number(row.cost).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getResultTagType(row.result)" size="small">
              {{ getResultText(row.result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑维修记录' : '新增维修记录'" width="760px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="维修编号" prop="repairNo">
              <el-input v-model="editForm.repairNo" :disabled="!!editForm.id" placeholder="请输入" />
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
            <el-form-item label="故障时间">
              <el-date-picker
                v-model="editForm.faultTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修开始时间">
              <el-date-picker
                v-model="editForm.repairTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="完成时间">
              <el-date-picker
                v-model="editForm.finishTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修人">
              <el-input v-model="editForm.repairman" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="停机时长(小时)">
              <el-input-number
                v-model="editForm.downtimeHours"
                :min="0"
                :precision="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修结果">
              <el-select v-model="editForm.result" placeholder="请选择" style="width: 100%">
                <el-option value="FIXED" label="已修复 (FIXED)" />
                <el-option value="PENDING" label="待处理 (PENDING)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="费用">
              <el-input-number
                v-model="editForm.cost"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="故障描述">
              <el-input v-model="editForm.faultDesc" type="textarea" :rows="2" placeholder="请输入故障描述" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="更换配件">
              <el-input v-model="editForm.partsReplaced" type="textarea" :rows="2" placeholder="请输入更换配件信息" />
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
  getRepairPage,
  saveRepair,
  deleteRepair,
  type InstrumentRepair,
  type RepairQuery
} from '@/api/instrument/calibration'

// 结果 el-tag: FIXED=成功, PENDING=警告
function getResultTagType(result?: string): string {
  if (result === 'FIXED') return 'success'
  if (result === 'PENDING') return 'warning'
  return 'info'
}

function getResultText(result?: string): string {
  if (result === 'FIXED') return '已修复'
  if (result === 'PENDING') return '待处理'
  return '-'
}

function formatTime(time?: string): string {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 仪器列表(下拉)
const instrumentList = ref<Instrument[]>([])

function getInstrumentName(instrumentId?: number): string {
  if (!instrumentId) return '-'
  const ins = instrumentList.value.find((i) => i.id === instrumentId)
  return ins ? ins.instrumentName : '-'
}

const loading = ref(false)
const tableData = ref<InstrumentRepair[]>([])
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
const editForm = reactive<InstrumentRepair>({
  id: 0,
  instrumentId: undefined as number | undefined,
  repairNo: '',
  faultTime: '',
  faultDesc: '',
  repairTime: '',
  finishTime: '',
  repairman: '',
  downtimeHours: undefined as number | undefined,
  result: 'PENDING',
  partsReplaced: '',
  cost: undefined as number | undefined,
  remark: '',
  createTime: ''
})
const formRules: FormRules = {
  repairNo: [{ required: true, message: '请输入维修编号', trigger: 'blur' }],
  instrumentId: [{ required: true, message: '请选择仪器', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: RepairQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.instrumentId) params.instrumentId = searchForm.instrumentId
    const res = await getRepairPage(params)
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
    repairNo: '',
    faultTime: '',
    faultDesc: '',
    repairTime: '',
    finishTime: '',
    repairman: '',
    downtimeHours: undefined,
    result: 'PENDING',
    partsReplaced: '',
    cost: undefined,
    remark: '',
    createTime: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: InstrumentRepair) {
  Object.assign(editForm, {
    id: row.id,
    instrumentId: row.instrumentId,
    repairNo: row.repairNo,
    faultTime: row.faultTime ?? '',
    faultDesc: row.faultDesc ?? '',
    repairTime: row.repairTime ?? '',
    finishTime: row.finishTime ?? '',
    repairman: row.repairman ?? '',
    downtimeHours: row.downtimeHours,
    result: row.result ?? 'PENDING',
    partsReplaced: row.partsReplaced ?? '',
    cost: row.cost,
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
      await saveRepair({ ...editForm })
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

async function handleDelete(row: InstrumentRepair) {
  try {
    await ElMessageBox.confirm(
      `确认删除维修记录 "${row.repairNo}" 吗?`,
      '提示',
      { type: 'warning' }
    )
    await deleteRepair(row.id)
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
.repair-page {
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
