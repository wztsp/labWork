<template>
  <div class="test-item-page">
    <!-- 顶部搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="指标代码/名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="指标代码或名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="searchForm.category"
            placeholder="全部"
            clearable
            filterable
            style="width: 160px"
          >
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标本类型">
          <el-select
            v-model="searchForm.specimenType"
            placeholder="全部"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option
              v-for="t in specimenTypes"
              :key="t.typeCode"
              :label="t.typeName"
              :value="t.typeName"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增指标</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 左右布局 -->
    <div class="split-layout">
      <!-- 左侧: 检验指标列表 -->
      <el-card shadow="never" class="left-panel">
        <template #header>
          <div class="panel-header">
            <span>检验指标</span>
            <el-tag size="small" type="info">共 {{ pagination.total }} 项</el-tag>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          highlight-current-row
          style="width: 100%"
          height="calc(100vh - 260px)"
          @current-change="handleCurrentRowChange"
        >
          <el-table-column type="index" label="序号" width="50" align="center" />
          <el-table-column prop="itemCode" label="指标代码" width="110" />
          <el-table-column prop="itemName" label="指标名称" min-width="130" />
          <el-table-column label="分类" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.category" :type="getCategoryTagType(row.category)" size="small">
                {{ row.category }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="specimenType" label="标本" width="80" align="center" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="warning" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[50, 100, 200, 500]"
            layout="total, prev, pager, next"
            background
            small
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 右侧: 选中指标的参考范围 -->
      <el-card shadow="never" class="right-panel">
        <template #header>
          <div class="panel-header">
            <span>
              参考范围
              <span v-if="currentItem" class="header-subtitle">
                — {{ currentItem.itemName }}
              </span>
            </span>
            <div v-if="currentItem" class="header-actions">
              <el-button type="primary" size="small" @click="handleAddRefRange">新增参考范围</el-button>
            </div>
          </div>
        </template>

        <div v-if="!currentItem" class="empty-tip">
          <el-empty description="请选择左侧检验指标" :image-size="80" />
        </div>

        <div v-else>
          <el-table
            v-loading="refRangeLoading"
            :data="refRangeList"
            border
            stripe
            style="width: 100%"
            height="calc(100vh - 260px)"
          >
            <el-table-column type="index" label="序号" width="50" align="center" />
            <el-table-column label="性别" width="70" align="center">
              <template #default="{ row }">{{ getGenderText(row.gender) }}</template>
            </el-table-column>
            <el-table-column label="年龄范围" width="100" align="center">
              <template #default="{ row }">
                {{ row.ageMin != null ? row.ageMin : 0 }} - {{ row.ageMax != null ? row.ageMax : 999 }}
              </template>
            </el-table-column>
            <el-table-column label="参考范围" width="130" align="center">
              <template #default="{ row }">
                <span v-if="row.refLow != null && row.refHigh != null">
                  {{ row.refLow }} - {{ row.refHigh }}
                </span>
                <span v-else-if="row.refText">{{ row.refText }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="危急值" width="130" align="center">
              <template #default="{ row }">
                <span v-if="row.criticalLow != null && row.criticalHigh != null" class="critical-text">
                  {{ row.criticalLow }} - {{ row.criticalHigh }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column label="条件备注" min-width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.conditionRemark" type="warning" size="small">{{ row.conditionRemark }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="标本类型" width="90" align="center">
              <template #default="{ row }">
                <span v-if="row.specimenType">{{ row.specimenType }}</span>
                <el-tag v-else type="info" size="small">全部</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="仪器" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.instrumentId">{{ getInstrumentName(row.instrumentId) }}</span>
                <el-tag v-else type="info" size="small">全部</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click.stop="handleEditRefRange(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click.stop="handleDeleteRefRange(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="该指标暂无参考范围, 请点击「新增参考范围」" :image-size="80" />
            </template>
          </el-table>
        </div>
      </el-card>
    </div>

    <!-- 检验指标 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑指标' : '新增指标'" width="680px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="指标代码" prop="itemCode">
              <el-input v-model="editForm.itemCode" :disabled="!!editForm.id" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指标名称" prop="itemName">
              <el-input v-model="editForm.itemName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名">
              <el-input v-model="editForm.itemNameEn" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="缩写">
              <el-input v-model="editForm.shortName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="editForm.category" placeholder="请选择" filterable style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标本类型">
              <el-select v-model="editForm.specimenType" placeholder="选择标本类型" clearable filterable style="width: 100%">
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
            <el-form-item label="单位">
              <el-input v-model="editForm.unit" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测方法">
              <el-input v-model="editForm.method" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上级项目">
              <el-select
                v-model="editForm.parentId"
                placeholder="无"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in parentOptions"
                  :key="item.id"
                  :label="`${item.itemCode} - ${item.itemName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格">
              <el-input-number
                v-model="editForm.price"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch
                v-model="editForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 参考范围 新增/编辑弹窗 -->
    <el-dialog
      v-model="refRangeDialogVisible"
      :title="refRangeEditForm.id ? '编辑参考范围' : '新增参考范围'"
      width="640px"
    >
      <el-form ref="refRangeFormRef" :model="refRangeEditForm" :rules="refRangeRules" label-width="120px">
        <el-form-item label="检验指标">
          <el-input :model-value="`${currentItem?.itemCode || ''} - ${currentItem?.itemName || ''}`" disabled />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标本类型">
              <el-select v-model="refRangeEditForm.specimenType" placeholder="全部适用" clearable filterable style="width: 100%">
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
              <el-select v-model="refRangeEditForm.instrumentId" placeholder="全部适用" clearable filterable style="width: 100%">
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
              <el-select v-model="refRangeEditForm.gender" style="width: 100%">
                <el-option label="全部" :value="null" />
                <el-option label="男" :value="1" />
                <el-option label="女" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="refRangeEditForm.unit" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小年龄">
              <el-input-number
                v-model="refRangeEditForm.ageMin"
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
                v-model="refRangeEditForm.ageMax"
                :min="0"
                :precision="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考下限">
              <el-input v-model="refRangeEditForm.refLow" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考上限">
              <el-input v-model="refRangeEditForm.refHigh" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="文字参考范围">
              <el-input v-model="refRangeEditForm.refText" placeholder="如: 阴性" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="危急值下限">
              <el-input v-model="refRangeEditForm.criticalLow" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="危急值上限">
              <el-input v-model="refRangeEditForm.criticalHigh" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="条件备注">
              <el-input v-model="refRangeEditForm.conditionRemark" placeholder="如: 女性-卵泡期 / 立位 / 晨8点 / 空腹 / 孕妇-孕早期" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="refRangeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="refRangeSaving" @click="handleSaveRefRange">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getTestItemList,
  saveTestItem,
  deleteTestItem,
  getAllTestItems,
  getAllSpecimenTypes,
  getRefRangeList,
  saveRefRange,
  deleteRefRange,
  getInstrumentDictList,
  type TestItem,
  type TestItemQuery,
  type SpecimenType,
  type ReferenceRange,
  type InstrumentDict
} from '@/api/base'

// 分类预设
const categoryOptions = ['血常规', '生化', '免疫', '凝血', '微生物', '尿常规', '其他']

const categoryTagTypeMap: Record<string, string> = {
  血常规: 'danger',
  生化: 'primary',
  免疫: 'success',
  凝血: 'warning',
  微生物: 'info',
  尿常规: 'primary',
  其他: 'info'
}

function getCategoryTagType(category: string): string {
  return categoryTagTypeMap[category] || 'info'
}

// 字典数据
const specimenTypes = ref<SpecimenType[]>([])
const instrumentList = ref<InstrumentDict[]>([])
const allTestItems = ref<TestItem[]>([])

// ============ 左侧: 检验指标列表 ============
const loading = ref(false)
const tableData = ref<TestItem[]>([])
const searchForm = reactive({
  keyword: '',
  category: '',
  specimenType: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 200,
  total: 0
})

const parentOptions = computed(() => {
  return allTestItems.value.filter(t => t.isPanel === 1)
})

// 当前选中的检验指标
const currentItem = ref<TestItem | null>(null)

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<TestItem>({
  id: 0,
  itemCode: '',
  itemName: '',
  itemNameEn: '',
  shortName: '',
  category: '',
  specimenType: '',
  unit: '',
  method: '',
  isPanel: 0,
  parentId: undefined,
  price: 0,
  status: 1
})
const formRules: FormRules = {
  itemCode: [{ required: true, message: '请输入指标代码', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入指标名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: TestItemQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      isPanel: 0
    }
    if (searchForm.keyword) {
      params.itemCode = searchForm.keyword
      params.itemName = searchForm.keyword
    }
    if (searchForm.category) params.category = searchForm.category
    if (searchForm.specimenType) params.specimenType = searchForm.specimenType
    const res = await getTestItemList(params)
    tableData.value = (res.records || [])
    pagination.total = res.total || 0
    // 默认选中第一行
    if (tableData.value.length && !currentItem.value) {
      handleCurrentRowChange(tableData.value[0])
    } else if (currentItem.value) {
      const updated = tableData.value.find(t => t.id === currentItem.value!.id)
      if (updated) handleCurrentRowChange(updated)
    }
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function loadAllTestItems() {
  try {
    allTestItems.value = await getAllTestItems()
  } catch (e) {
    // ignore
  }
}

/** 选中指标行: 加载右侧参考范围 */
function handleCurrentRowChange(row: TestItem | null) {
  currentItem.value = row
  if (row) {
    loadRefRanges(row.id)
  } else {
    refRangeList.value = []
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.category = ''
  searchForm.specimenType = ''
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
    id: 0, itemCode: '', itemName: '', itemNameEn: '', shortName: '',
    category: '', specimenType: '', unit: '', method: '',
    isPanel: 0, parentId: undefined, price: 0, status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: TestItem) {
  Object.assign(editForm, {
    id: row.id,
    itemCode: row.itemCode,
    itemName: row.itemName,
    itemNameEn: row.itemNameEn ?? '',
    shortName: row.shortName ?? '',
    category: row.category ?? '',
    specimenType: row.specimenType ?? '',
    unit: row.unit ?? '',
    method: row.method ?? '',
    isPanel: row.isPanel ?? 0,
    parentId: row.parentId ?? undefined,
    price: row.price ?? 0,
    status: row.status ?? 1,
    createTime: row.createTime
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveTestItem({ ...editForm })
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchList()
      loadAllTestItems()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: TestItem) {
  try {
    await ElMessageBox.confirm(`确认删除指标 "${row.itemName}" 吗?`, '提示', { type: 'warning' })
    await deleteTestItem(row.id)
    ElMessage.success('删除成功')
    if (currentItem.value?.id === row.id) {
      currentItem.value = null
      refRangeList.value = []
    }
    fetchList()
    loadAllTestItems()
  } catch (e) {
    // 取消或失败
  }
}

// ============ 右侧: 参考范围 ============
const refRangeLoading = ref(false)
const refRangeList = ref<ReferenceRange[]>([])
const refRangeDialogVisible = ref(false)
const refRangeSaving = ref(false)
const refRangeFormRef = ref<FormInstance>()
const refRangeEditForm = reactive<ReferenceRange>({
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
const refRangeRules: FormRules = {}

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

async function loadRefRanges(testItemId: number) {
  refRangeLoading.value = true
  try {
    const res = await getRefRangeList({ pageNum: 1, pageSize: 200, testItemId })
    refRangeList.value = res.records || []
  } catch (e) {
    refRangeList.value = []
  } finally {
    refRangeLoading.value = false
  }
}

function handleAddRefRange() {
  if (!currentItem.value) return
  Object.assign(refRangeEditForm, {
    id: 0,
    testItemId: currentItem.value.id,
    testItemName: currentItem.value.itemName,
    gender: null,
    ageMin: 0,
    ageMax: 999,
    refLow: '',
    refHigh: '',
    refText: '',
    criticalLow: '',
    criticalHigh: '',
    unit: currentItem.value.unit ?? '',
    specimenType: undefined,
    instrumentId: undefined
  })
  refRangeDialogVisible.value = true
}

function handleEditRefRange(row: ReferenceRange) {
  Object.assign(refRangeEditForm, {
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
    instrumentId: row.instrumentId ?? undefined,
    conditionRemark: row.conditionRemark ?? ''
  })
  refRangeDialogVisible.value = true
}

async function handleSaveRefRange() {
  if (!refRangeFormRef.value || !currentItem.value) return
  await refRangeFormRef.value.validate(async (valid) => {
    if (!valid) return
    refRangeSaving.value = true
    try {
      refRangeEditForm.testItemId = currentItem.value!.id
      refRangeEditForm.testItemName = currentItem.value!.itemName
      await saveRefRange({ ...refRangeEditForm })
      ElMessage.success('保存成功')
      refRangeDialogVisible.value = false
      loadRefRanges(currentItem.value!.id)
    } catch (e) {
      // ignore
    } finally {
      refRangeSaving.value = false
    }
  })
}

async function handleDeleteRefRange(row: ReferenceRange) {
  try {
    await ElMessageBox.confirm('确认删除该参考范围吗?', '提示', { type: 'warning' })
    await deleteRefRange(row.id)
    ElMessage.success('删除成功')
    if (currentItem.value) loadRefRanges(currentItem.value.id)
  } catch (e) {
    // 取消或失败
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

onMounted(async () => {
  // 先加载所有字典, 再 fetchList
  try {
    specimenTypes.value = await getAllSpecimenTypes()
  } catch (e) {
    specimenTypes.value = []
  }
  loadInstruments()
  loadAllTestItems()
  fetchList()
})
</script>

<style scoped lang="scss">
.test-item-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .split-layout {
    display: flex;
    gap: 12px;
    align-items: stretch;

    .left-panel {
      flex: 1.2;
      min-width: 0;
    }

    .right-panel {
      flex: 1;
      min-width: 0;
    }
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-subtitle {
      color: var(--el-color-primary);
      font-weight: 500;
      margin-left: 4px;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }

  .empty-tip {
    display: flex;
    align-items: center;
    justify-content: center;
    height: calc(100vh - 260px);
  }

  .text-muted {
    color: #909399;
    font-size: 12px;
  }

  .critical-text {
    color: var(--el-color-danger);
    font-weight: bold;
  }
}
</style>
