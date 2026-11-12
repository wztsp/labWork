<template>
  <div class="micro-qc-page">
    <el-card class="type-card" shadow="never">
      <el-tabs v-model="activeType" @tab-change="onTypeChange">
        <el-tab-pane v-for="t in typeTabs" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>
    </el-card>

    <div v-loading="loading">
      <!-- 搜索栏 -->
      <el-card class="search-card" shadow="never">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="名称/批号/菌株">
            <el-input
              v-model="searchForm.keyword"
              placeholder="物品名称/批号/质控菌株"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="判定">
            <el-select v-model="searchForm.result" placeholder="全部" clearable style="width: 120px">
              <el-option label="合格" value="PASS" />
              <el-option label="不合格" value="FAIL" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button type="primary" @click="handleAdd">新增质控记录</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-total">
            <div class="stat-label">质控总数</div>
            <div class="stat-value">{{ stats.total }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-pass">
            <div class="stat-label">合格数</div>
            <div class="stat-value">{{ stats.pass }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-fail">
            <div class="stat-label">不合格数</div>
            <div class="stat-value">{{ stats.fail }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-rate">
            <div class="stat-label">合格率</div>
            <div class="stat-value">{{ stats.passRate }}%</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 表格 -->
      <el-card shadow="never" class="table-card">
        <el-table :data="tableData" border stripe style="width: 100%">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="itemName" label="物品名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="itemCode" label="编码" width="100" align="center" />
          <el-table-column prop="qcLotNo" label="批号" width="120" align="center" />
          <el-table-column prop="controlStrain" label="质控菌株" min-width="140" show-overflow-tooltip />
          <el-table-column prop="expectedResult" label="预期结果" width="100" align="center" />
          <el-table-column prop="actualResult" label="实际结果" width="100" align="center" />
          <el-table-column label="判定" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">
                {{ row.result === 'PASS' ? '合格' : '不合格' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="100" align="center" />
          <el-table-column label="操作时间" width="160" align="center">
            <template #default="{ row }">{{ row.measureTime ? formatTime(row.measureTime) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="130" align="center" fixed="right">
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
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑质控记录' : '新增质控记录'" width="720px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        当前类别：{{ currentTypeLabel }}。填写预期结果与实际结果，系统自动判定合格/不合格。
      </el-alert>
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物品名称" prop="itemName">
              <el-select
                v-if="dictionaryMode"
                v-model="editForm.itemName"
                placeholder="请选择"
                filterable
                clearable
                style="width: 100%"
                @change="onItemNameChange"
              >
                <el-option v-for="d in dictOptions" :key="d.value" :label="d.label" :value="d.label" />
              </el-select>
              <el-input v-else v-model="editForm.itemName" placeholder="请输入物品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批号/质控编号">
              <el-input v-model="editForm.qcLotNo" placeholder="如 20260801" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质控菌株">
              <el-input v-model="editForm.controlStrain" placeholder="如 ATCC25922 / 大肠埃希菌" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预期结果">
              <el-select v-model="editForm.expectedResult" filterable allow-create default-first-option placeholder="选择或输入" style="width: 100%">
                <el-option v-for="o in resultOptions" :key="o" :label="o" :value="o" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际结果">
              <el-select v-model="editForm.actualResult" filterable allow-create default-first-option placeholder="选择或输入" style="width: 100%">
                <el-option v-for="o in resultOptions" :key="o" :label="o" :value="o" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作时间">
              <el-date-picker
                v-model="editForm.measureTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="备注信息" />
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
  getMicroQcPage,
  getMicroQcStats,
  saveMicroQc,
  deleteMicroQc,
  MICRO_QC_TYPES,
  type MicroQcRecord,
  type MicroQcQuery
} from '@/api/micro/qc'
import { getAllAntibiotics, getAllBacteria } from '@/api/micro'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const typeTabs = Object.entries(MICRO_QC_TYPES).map(([key, label]) => ({
  value: Number(key),
  label
}))

const activeType = ref(1)
const currentTypeLabel = computed(() => MICRO_QC_TYPES[activeType.value] || '')

const loading = ref(false)
const tableData = ref<MicroQcRecord[]>([])
const searchForm = reactive({
  keyword: '',
  result: ''
})
const stats = reactive({
  total: 0,
  pass: 0,
  fail: 0,
  passRate: 0
})
const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 字典选项 (抗菌药物/标准菌株)
const dictOptions = ref<{ label: string; value: string }[]>([])
const dictionaryMode = computed(() => activeType.value === 2 || activeType.value === 7)

// 结果选项 (各类别常用)
const resultOptions = computed(() => {
  switch (activeType.value) {
    case 2:
    case 3:
      return ['S', 'I', 'R', 'S（敏感）', 'I（中介）', 'R（耐药）']
    case 5:
      return ['生长', '不生长', '阳性', '阴性']
    case 6:
      return ['正常', '异常', '合格', '不合格']
    case 7:
      return ['阳性', '阴性', '符合', '不符合']
    default:
      return ['阳性', '阴性', '+++', '++', '+', '±', '-']
  }
})

async function loadDictOptions() {
  if (activeType.value === 2) {
    const list = (await getAllAntibiotics()) || []
    dictOptions.value = list.map(a => ({ label: a.antibioticName, value: a.antibioticCode }))
  } else if (activeType.value === 7) {
    const list = (await getAllBacteria()) || []
    dictOptions.value = list.map(b => ({ label: b.bacteriaName, value: b.bacteriaCode }))
  } else {
    dictOptions.value = []
  }
}

function onItemNameChange(label: string) {
  const hit = dictOptions.value.find(d => d.label === label)
  editForm.itemCode = hit ? hit.value : ''
}

async function fetchList() {
  loading.value = true
  try {
    const params: MicroQcQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      itemType: activeType.value
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.result) params.result = searchForm.result
    const res = await getMicroQcPage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const res = await getMicroQcStats(activeType.value)
    stats.total = res?.total || 0
    stats.pass = res?.pass || 0
    stats.fail = res?.fail || 0
    stats.passRate = res?.passRate ?? 0
  } catch (e) {
    // ignore
  }
}

function onTypeChange() {
  pagination.pageNum = 1
  searchForm.keyword = ''
  searchForm.result = ''
  if (dictionaryMode.value) loadDictOptions()
  fetchList()
  fetchStats()
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
  fetchStats()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.result = ''
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

function formatTime(s: string): string {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 19)
}

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<MicroQcRecord>({
  id: 0,
  itemType: 1,
  itemCode: '',
  itemName: '',
  qcLotNo: '',
  controlStrain: '',
  expectedResult: '',
  actualResult: '',
  result: '',
  operatorId: 0,
  operatorName: '',
  measureTime: '',
  remark: ''
})
const formRules: FormRules = {
  itemName: [{ required: true, message: '请输入物品名称', trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0,
    itemType: activeType.value,
    itemCode: '',
    itemName: '',
    qcLotNo: '',
    controlStrain: '',
    expectedResult: '',
    actualResult: '',
    result: '',
    operatorId: userStore.userId || 0,
    operatorName: userStore.realName || userStore.username || '',
    measureTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    remark: ''
  })
  if (dictionaryMode.value) loadDictOptions()
  formDialogVisible.value = true
}

function handleEdit(row: MicroQcRecord) {
  Object.assign(editForm, {
    id: row.id,
    itemType: row.itemType,
    itemCode: row.itemCode ?? '',
    itemName: row.itemName ?? '',
    qcLotNo: row.qcLotNo ?? '',
    controlStrain: row.controlStrain ?? '',
    expectedResult: row.expectedResult ?? '',
    actualResult: row.actualResult ?? '',
    result: row.result ?? '',
    operatorId: row.operatorId ?? (userStore.userId || 0),
    operatorName: row.operatorName ?? (userStore.realName || ''),
    measureTime: row.measureTime ?? '',
    remark: row.remark ?? ''
  })
  if (dictionaryMode.value) loadDictOptions()
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveMicroQc({ ...editForm })
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchList()
      fetchStats()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: MicroQcRecord) {
  try {
    await ElMessageBox.confirm(
      `确认删除质控记录 "${row.itemName}" 吗?`,
      '提示',
      { type: 'warning' }
    )
    await deleteMicroQc(row.id!)
    ElMessage.success('删除成功')
    fetchList()
    fetchStats()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
  fetchStats()
})
</script>

<style scoped lang="scss">
.micro-qc-page {
  padding: 16px;

  .type-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 0 16px;
    }
  }

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .stats-row {
    margin-bottom: 16px;
  }

  .stat-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 20px;
    }

    .stat-label {
      font-size: 13px;
      color: var(--el-text-color-secondary);
      margin-bottom: 6px;
    }

    .stat-value {
      font-size: 26px;
      font-weight: bold;
      line-height: 1.2;
    }

    &.stat-total .stat-value { color: var(--el-color-primary); }
    &.stat-pass .stat-value { color: var(--el-color-success); }
    &.stat-fail .stat-value { color: var(--el-color-danger); }
    &.stat-rate .stat-value { color: var(--el-color-warning); }
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