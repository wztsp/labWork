<template>
  <div class="ref-range-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="检验指标">
          <el-select
            v-model="searchForm.testItemId"
            placeholder="请选择指标"
            filterable
            clearable
            style="width: 240px"
          >
            <el-option
              v-for="item in testItemList"
              :key="item.id"
              :label="`${item.itemCode} - ${item.itemName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标本类型">
          <el-select v-model="searchForm.specimenType" placeholder="全部" clearable filterable style="width: 140px">
            <el-option
              v-for="t in specimenTypes"
              :key="t.typeCode"
              :label="t.typeName"
              :value="t.typeName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="仪器">
          <el-select v-model="searchForm.instrumentId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option
              v-for="ins in instrumentList"
              :key="ins.id"
              :label="ins.instrumentName"
              :value="ins.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增参考范围</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="testItemName" label="指标名称" min-width="140" />
        <el-table-column label="标本类型" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.specimenType">{{ row.specimenType }}</span>
            <el-tag v-else type="info" size="small">全部</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仪器" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.instrumentId">{{ getInstrumentName(row.instrumentId) }}</span>
            <el-tag v-else type="info" size="small">全部</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="80" align="center">
          <template #default="{ row }">{{ getGenderText(row.gender) }}</template>
        </el-table-column>
        <el-table-column label="年龄范围" width="120" align="center">
          <template #default="{ row }">
            {{ row.ageMin != null ? row.ageMin : 0 }} - {{ row.ageMax != null ? row.ageMax : 999 }}
          </template>
        </el-table-column>
        <el-table-column label="参考范围" width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.refLow != null && row.refHigh != null">
              {{ row.refLow }} - {{ row.refHigh }}
            </span>
            <span v-else-if="row.refText">{{ row.refText }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="危急值范围" width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.criticalLow != null && row.criticalHigh != null" class="critical-text">
              {{ row.criticalLow }} - {{ row.criticalHigh }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" align="center" />
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
    <el-dialog
      v-model="formDialogVisible"
      :title="editForm.id ? '编辑参考范围' : '新增参考范围'"
      width="640px"
    >
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="检验指标" prop="testItemId">
              <el-select
                v-model="editForm.testItemId"
                placeholder="请选择"
                filterable
                style="width: 100%"
                @change="onTestItemChange"
              >
                <el-option
                  v-for="item in testItemList"
                  :key="item.id"
                  :label="`${item.itemCode} - ${item.itemName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标本类型">
              <el-select v-model="editForm.specimenType" placeholder="全部适用" clearable filterable style="width: 100%">
                <el-option
                  v-for="t in specimenTypes"
                  :key="t.typeCode"
                  :label="t.typeName"
                  :value="t.typeName"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仪器">
              <el-select v-model="editForm.instrumentId" placeholder="全部适用" clearable filterable style="width: 100%">
                <el-option
                  v-for="ins in instrumentList"
                  :key="ins.id"
                  :label="ins.instrumentName"
                  :value="ins.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="editForm.gender" style="width: 100%">
                <el-option label="全部" :value="null" />
                <el-option label="男" :value="1" />
                <el-option label="女" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="editForm.unit" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小年龄">
              <el-input-number
                v-model="editForm.ageMin"
                :min="0"
                :precision="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大年龄">
              <el-input-number
                v-model="editForm.ageMax"
                :min="0"
                :precision="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考下限">
              <el-input v-model="editForm.refLow" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考上限">
              <el-input v-model="editForm.refHigh" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="文字参考范围">
              <el-input v-model="editForm.refText" placeholder="如: 阴性" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="危急值下限">
              <el-input v-model="editForm.criticalLow" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="危急值上限">
              <el-input v-model="editForm.criticalHigh" placeholder="请输入" />
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
import {
  getRefRangeList,
  saveRefRange,
  deleteRefRange,
  getAllTestItems,
  getAllSpecimenTypes,
  getInstrumentDictList,
  type ReferenceRange,
  type RefRangeQuery,
  type TestItem,
  type SpecimenType,
  type InstrumentDict
} from '@/api/base'

const loading = ref(false)
const tableData = ref<ReferenceRange[]>([])
const testItemList = ref<TestItem[]>([])
const specimenTypes = ref<SpecimenType[]>([])
const instrumentList = ref<InstrumentDict[]>([])

const searchForm = reactive<{
  testItemId?: number
  specimenType?: string
  instrumentId?: number
  gender: number | null
}>({
  testItemId: undefined,
  specimenType: undefined,
  instrumentId: undefined,
  gender: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<ReferenceRange>({
  id: 0,
  testItemId: 0,
  testItemName: '',
  gender: null,
  ageMin: 0,
  ageMax: 999,
  refLow: '',
  refHigh: '',
  refText: '',
  criticalLow: '',
  criticalHigh: '',
  unit: '',
  specimenType: undefined,
  instrumentId: undefined
})
const formRules: FormRules = {
  testItemId: [{ required: true, message: '请选择检验指标', trigger: 'change' }]
}

function getGenderText(gender?: number | null): string {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '全部'
}

function getInstrumentName(instrumentId?: number): string {
  if (!instrumentId) return ''
  const ins = instrumentList.value.find(i => i.id === instrumentId)
  return ins ? ins.instrumentName : `ID:${instrumentId}`
}

async function fetchList() {
  loading.value = true
  try {
    const params: RefRangeQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.testItemId) {
      params.testItemId = searchForm.testItemId
    }
    if (searchForm.specimenType) {
      params.specimenType = searchForm.specimenType
    }
    if (searchForm.instrumentId) {
      params.instrumentId = searchForm.instrumentId
    }
    const res = await getRefRangeList(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function loadTestItems() {
  try {
    testItemList.value = await getAllTestItems()
  } catch (e) {
    // ignore
  }
}

async function loadSpecimenTypes() {
  try {
    specimenTypes.value = await getAllSpecimenTypes()
  } catch (e) {
    specimenTypes.value = []
  }
}

async function loadInstruments() {
  try {
    const res = await getInstrumentDictList({ pageNum: 1, pageSize: 100, status: 1 })
    instrumentList.value = res.records || []
  } catch (e) {
    instrumentList.value = []
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.testItemId = undefined
  searchForm.specimenType = undefined
  searchForm.instrumentId = undefined
  searchForm.gender = null
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
    testItemId: 0,
    testItemName: '',
    gender: null,
    ageMin: 0,
    ageMax: 999,
    refLow: '',
    refHigh: '',
    refText: '',
    criticalLow: '',
    criticalHigh: '',
    unit: '',
    specimenType: undefined,
    instrumentId: undefined
  })
  formDialogVisible.value = true
}

function handleEdit(row: ReferenceRange) {
  Object.assign(editForm, {
    id: row.id,
    testItemId: row.testItemId,
    testItemName: row.testItemName ?? '',
    gender: row.gender ?? null,
    ageMin: row.ageMin ?? 0,
    ageMax: row.ageMax ?? 999,
    refLow: row.refLow ?? '',
    refHigh: row.refHigh ?? '',
    refText: row.refText ?? '',
    criticalLow: row.criticalLow ?? '',
    criticalHigh: row.criticalHigh ?? '',
    unit: row.unit ?? '',
    specimenType: row.specimenType ?? undefined,
    instrumentId: row.instrumentId ?? undefined
  })
  formDialogVisible.value = true
}

function onTestItemChange(val: number) {
  const item = testItemList.value.find(t => t.id === val)
  if (item) {
    editForm.testItemName = item.itemName
    if (!editForm.unit && item.unit) {
      editForm.unit = item.unit
    }
  }
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveRefRange({ ...editForm })
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

async function handleDelete(row: ReferenceRange) {
  try {
    await ElMessageBox.confirm(
      `确认删除该参考范围吗?`,
      '提示',
      { type: 'warning' }
    )
    await deleteRefRange(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

function formatTime(time: string): string {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(() => {
  loadTestItems()
  loadSpecimenTypes()
  loadInstruments()
  fetchList()
})
</script>

<style scoped lang="scss">
.ref-range-page {
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

  .critical-text {
    color: var(--el-color-danger);
    font-weight: bold;
  }
}
</style>
