<template>
  <div class="panel-page">
    <!-- 顶部查询 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="编码/名称"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增检验项目</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 左右布局 -->
    <div class="split-layout">
      <!-- 左侧: 检验项目列表 -->
      <el-card shadow="never" class="left-panel">
        <template #header>
          <div class="panel-header">
            <span>检验项目</span>
            <el-tag size="small" type="info">共 {{ total }} 项</el-tag>
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
          <el-table-column prop="itemCode" label="编码" width="100" />
          <el-table-column prop="itemName" label="名称" min-width="120" />
          <el-table-column label="试管" width="110" align="center">
            <template #default="{ row }">
              <el-tag
                v-if="row.tubeId"
                :type="tubeTagType(row.tubeColor)"
                size="small"
                effect="plain"
              >
                {{ row.tubeName || '-' }}
              </el-tag>
              <span v-else class="text-muted">未绑定</span>
            </template>
          </el-table-column>
          <el-table-column prop="specimenType" label="标本" width="80" align="center" />
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="warning" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
              <el-popconfirm title="确认删除该检验项目?" @confirm="handleDelete(row)">
                <template #reference>
                  <el-button type="danger" link size="small" @click.stop>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :total="total"
            :page-sizes="[20, 50, 100, 200]"
            layout="total, prev, pager, next"
            background
            small
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 右侧: 检验项目对应的检验指标 -->
      <el-card shadow="never" class="right-panel">
        <template #header>
          <div class="panel-header">
            <span>
              检验指标
              <span v-if="currentPanel" class="header-subtitle">
                — {{ currentPanel.itemName }}
              </span>
            </span>
            <div v-if="currentPanel" class="header-actions">
              <el-button type="primary" size="small" @click="handleManageItems">管理指标</el-button>
            </div>
          </div>
        </template>

        <div v-if="!currentPanel" class="empty-tip">
          <el-empty description="请选择左侧检验项目" :image-size="80" />
        </div>

        <div v-else>
          <el-table
            v-loading="indicatorsLoading"
            :data="panelIndicators"
            border
            stripe
            style="width: 100%"
            height="calc(100vh - 260px)"
          >
            <el-table-column type="index" label="序号" width="50" align="center" />
            <el-table-column prop="itemCode" label="指标编码" width="110" />
            <el-table-column prop="itemName" label="指标名称" min-width="140" />
            <el-table-column prop="shortName" label="缩写" width="80" />
            <el-table-column prop="specimenType" label="标本类型" width="90" align="center" />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="method" label="检测方法" width="110" />
            <el-table-column label="试管" width="100" align="center">
              <template #default="{ row }">
                <el-tag
                  v-if="row.tubeId"
                  :type="tubeTagType(row.tubeColor)"
                  size="small"
                  effect="plain"
                >
                  {{ row.tubeName || '-' }}
                </el-tag>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="该项目暂未关联检验指标, 请点击「管理指标」" :image-size="80" />
            </template>
          </el-table>
        </div>
      </el-card>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editForm.id ? '编辑检验项目' : '新增检验项目'"
      width="560px"
    >
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目编码" prop="itemCode">
              <el-input v-model="editForm.itemCode" placeholder="如: CBC" :disabled="!!editForm.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称" prop="itemName">
              <el-input v-model="editForm.itemName" placeholder="如: 血常规" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="editForm.category" placeholder="选择分类" clearable filterable allow-create style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标本类型">
              <el-select v-model="editForm.specimenType" placeholder="选择标本类型" clearable style="width: 100%">
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
            <el-form-item label="检验试管">
              <el-select
                v-model="editForm.tubeId"
                placeholder="选择试管(可空)"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="t in testTubes"
                  :key="t.id"
                  :label="`${t.tubeName}(${t.tubeColor || '-'})`"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采集方式">
              <el-select
                v-model="editForm.collectionMethodId"
                placeholder="选择采集方式(可空)"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="m in collectionMethods"
                  :key="m.id"
                  :label="m.methodName"
                  :value="m.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报告模板">
              <el-select
                v-model="editForm.reportTemplateId"
                placeholder="选择报告模板(可空)"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="t in reportTemplates"
                  :key="t.templateId"
                  :label="t.templateName"
                  :value="t.templateId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格">
              <el-input-number v-model="editForm.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="editForm.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 管理指标弹窗 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="`管理指标 - ${currentPanel?.itemName || ''}`"
      width="700px"
    >
      <div class="item-manage">
        <div class="item-manage-header">
          <el-input
            v-model="itemKeyword"
            placeholder="搜索指标编码/名称"
            clearable
            style="width: 240px"
          />
          <el-button type="primary" @click="fetchAllIndicators">刷新指标列表</el-button>
        </div>
        <el-transfer
          v-model="selectedItemIds"
          :data="transferData"
          :titles="['可选指标', '已选指标']"
          :filterable="true"
          :filter-method="transferFilter"
          target-order="push"
          style="margin-top: 12px"
        />
      </div>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingItems" @click="handleSaveItems">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getTestItemList,
  saveTestItem,
  deleteTestItem,
  getPanelItems,
  managePanel,
  getTestItemCategories,
  getAllSpecimenTypes,
  getAllTestTubes,
  getAllCollectionMethods,
  type TestItem,
  type TestItemQuery,
  type TestTube,
  type CollectionMethod
} from '@/api/base'
import { getTemplateList } from '@/api/report/template'

const loading = ref(false)
const saving = ref(false)
const savingItems = ref(false)
const tableData = ref<TestItem[]>([])
const total = ref(0)
const categories = ref<string[]>([])
const specimenTypes = ref<any[]>([])
const testTubes = ref<TestTube[]>([])
const collectionMethods = ref<CollectionMethod[]>([])
const reportTemplates = ref<any[]>([])

const queryParams = reactive<TestItemQuery>({
  pageNum: 1,
  pageSize: 100,
  keyword: '',
  category: '',
  isPanel: 1 // 只查检验项目
})

// 当前选中的检验项目
const currentPanel = ref<TestItem | null>(null)
const indicatorsLoading = ref(false)
const panelIndicators = ref<TestItem[]>([])

// 编辑弹窗
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<any>({
  id: 0,
  itemCode: '',
  itemName: '',
  category: '',
  specimenType: '',
  tubeId: undefined,
  collectionMethodId: undefined,
  reportTemplateId: undefined,
  price: 0,
  status: 1,
  isPanel: 1
})

const rules = {
  itemCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

// 管理指标弹窗
const itemDialogVisible = ref(false)
const allIndicators = ref<TestItem[]>([])
const selectedItemIds = ref<number[]>([])
const itemKeyword = ref('')

const transferData = computed(() => {
  return allIndicators.value.map((item) => ({
    key: item.id,
    label: `${item.itemCode} - ${item.itemName}`,
    itemCode: item.itemCode,
    itemName: item.itemName
  }))
})

function transferFilter(query: string, item: any) {
  return item.label.toLowerCase().includes(query.toLowerCase())
}

/** 试管颜色标签类型 */
function tubeTagType(color?: string): string {
  if (!color) return 'info'
  const c = color.charAt(0)
  const map: Record<string, string> = { '红': 'danger', '紫': 'primary', '蓝': 'primary', '绿': 'success', '黄': 'warning', '黑': 'info', '灰': 'info' }
  return map[c] || 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getTestItemList(queryParams)
    tableData.value = (res.records || []).map(t => enrichTubeInfo(t))
    total.value = res.total || 0
    // 默认选中第一行
    if (tableData.value.length && !currentPanel.value) {
      handleCurrentRowChange(tableData.value[0])
    } else if (currentPanel.value) {
      // 刷新后保持选中
      const updated = tableData.value.find(t => t.id === currentPanel.value!.id)
      if (updated) handleCurrentRowChange(updated)
    }
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

/** 给检验项目附加试管名称/颜色信息 */
function enrichTubeInfo(item: TestItem): TestItem {
  const t: any = item as any
  if (item.tubeId) {
    const tube = testTubes.value.find(x => x.id === item.tubeId)
    if (tube) {
      t.tubeName = tube.tubeName
      t.tubeColor = tube.tubeColor
    }
  }
  return t
}

/** 选中检验项目行: 加载右侧指标 */
function handleCurrentRowChange(row: TestItem | null) {
  currentPanel.value = row
  if (row) {
    loadPanelIndicators(row.id)
  } else {
    panelIndicators.value = []
  }
}

async function loadPanelIndicators(panelId: number) {
  indicatorsLoading.value = true
  try {
    const items = await getPanelItems(panelId)
    panelIndicators.value = (items || []).map(t => enrichTubeInfo(t))
  } catch (e) {
    panelIndicators.value = []
  } finally {
    indicatorsLoading.value = false
  }
}

function resetAndFetch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.category = ''
  queryParams.pageNum = 1
  fetchList()
}

function handleSizeChange(size: number) {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  fetchList()
}

function handlePageChange(page: number) {
  queryParams.pageNum = page
  fetchList()
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0, itemCode: '', itemName: '', category: '', specimenType: '',
    tubeId: undefined, collectionMethodId: undefined, reportTemplateId: undefined,
    price: 0, status: 1, isPanel: 1
  })
  dialogVisible.value = true
}

function handleEdit(row: TestItem) {
  Object.assign(editForm, {
    id: row.id,
    itemCode: row.itemCode,
    itemName: row.itemName,
    category: row.category ?? '',
    specimenType: row.specimenType ?? '',
    tubeId: row.tubeId ?? undefined,
    collectionMethodId: row.collectionMethodId ?? undefined,
    reportTemplateId: row.reportTemplateId ?? undefined,
    price: row.price ?? 0,
    status: row.status ?? 1,
    isPanel: 1
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      editForm.isPanel = 1
      await saveTestItem({ ...editForm })
      ElMessage.success(editForm.id ? '更新成功' : '新增成功')
      dialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: TestItem) {
  try {
    await deleteTestItem(row.id)
    ElMessage.success('删除成功')
    if (currentPanel.value?.id === row.id) {
      currentPanel.value = null
      panelIndicators.value = []
    }
    fetchList()
  } catch (e) {
    // ignore
  }
}

// 管理指标
async function handleManageItems() {
  if (!currentPanel.value) return
  itemDialogVisible.value = true
  selectedItemIds.value = []
  itemKeyword.value = ''
  await Promise.all([fetchAllIndicators(), fetchPanelItems(currentPanel.value.id)])
}

async function fetchAllIndicators() {
  try {
    const res = await getTestItemList({ pageNum: 1, pageSize: 500, isPanel: 0, status: 1 })
    allIndicators.value = res.records || []
  } catch (e) {
    allIndicators.value = []
  }
}

async function fetchPanelItems(panelId: number) {
  try {
    const items = await getPanelItems(panelId)
    selectedItemIds.value = (items || []).map((i) => i.id)
  } catch (e) {
    selectedItemIds.value = []
  }
}

async function handleSaveItems() {
  if (!currentPanel.value) return
  savingItems.value = true
  try {
    await managePanel(currentPanel.value.id, selectedItemIds.value)
    ElMessage.success(`已设置 ${selectedItemIds.value.length} 个指标`)
    itemDialogVisible.value = false
    // 刷新右侧指标列表
    loadPanelIndicators(currentPanel.value.id)
  } catch (e) {
    // ignore
  } finally {
    savingItems.value = false
  }
}

onMounted(async () => {
  categories.value = await getTestItemCategories()
  try {
    specimenTypes.value = await getAllSpecimenTypes()
  } catch (e) {
    specimenTypes.value = []
  }
  try {
    testTubes.value = await getAllTestTubes()
  } catch (e) {
    testTubes.value = []
  }
  try {
    collectionMethods.value = await getAllCollectionMethods()
  } catch (e) {
    collectionMethods.value = []
  }
  try {
    reportTemplates.value = await getTemplateList()
  } catch (e) {
    reportTemplates.value = []
  }
  fetchList()
})
</script>

<style scoped lang="scss">
.panel-page {
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

  .item-manage {
    .item-manage-header {
      display: flex;
      gap: 8px;
      align-items: center;
    }

    :deep(.el-transfer) {
      display: flex;
      justify-content: center;
    }
  }
}
</style>
