<template>
  <div class="qc-lot-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="批号">
          <el-input v-model="searchForm.lotNo" placeholder="批号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="检验指标">
          <el-select v-model="searchForm.testItemId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="item in filteredSearchTestItemList" :key="item.id" :label="item.itemName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="仪器">
          <el-select v-model="searchForm.instrumentId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="item in instrumentList" :key="item.id" :label="item.instrumentName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openDialog()">新增批号</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 批号列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredList" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="lotNo" label="批号" width="140" />
        <el-table-column label="检验指标" min-width="120">
          <template #default="{ row }">{{ row.testItemName || getTestItemName(row.testItemId) }}</template>
        </el-table-column>
        <el-table-column label="仪器" min-width="140">
          <template #default="{ row }">{{ row.instrumentName || getInstrumentName(row.instrumentId) }}</template>
        </el-table-column>
        <el-table-column prop="targetValue" label="靶值" width="100" align="center" />
        <el-table-column prop="sd" label="标准差" width="100" align="center" />
        <el-table-column prop="cv" label="CV(%)" width="90" align="center" />
        <el-table-column label="浓度级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)" size="small">{{ QC_LEVEL_MAP[row.level] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="220" align="center">
          <template #default="{ row }">{{ row.startDate || '?' }} ~ {{ row.endDate || '?' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingLot.id ? '编辑批号' : '新增批号'" width="600px">
      <el-form ref="formRef" :model="editingLot" :rules="formRules" label-width="100px">
        <el-form-item label="批号" prop="lotNo">
          <el-input v-model="editingLot.lotNo" placeholder="如 QC2026W001" />
        </el-form-item>
        <el-form-item label="仪器" prop="instrumentId">
          <el-select v-model="editingLot.instrumentId" placeholder="请先选择仪器（必填）" filterable clearable style="width: 100%">
            <el-option v-for="item in instrumentList" :key="item.id" :label="item.instrumentName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验指标" prop="testItemId">
          <el-select
            v-model="editingLot.testItemId"
            :disabled="!editingLot.instrumentId"
            placeholder="请先选择仪器"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option v-for="item in dialogTestItemList" :key="item.id" :label="item.itemName" :value="item.id" />
          </el-select>
          <div v-if="!editingLot.instrumentId" style="font-size:12px;color:#909399;margin-top:2px;">请先选择仪器，系统将仅列出该仪器绑定的检验指标。</div>
        </el-form-item>
        <el-form-item label="靶值" prop="targetValue">
          <el-input-number v-model="editingLot.targetValue" :precision="4" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="标准差(SD)" prop="sd">
          <el-input-number v-model="editingLot.sd" :precision="4" :step="0.1" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="变异系数(%)" prop="cv">
          <el-input-number v-model="editingLot.cv" :precision="2" :step="0.1" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="浓度级别" prop="level">
          <el-select v-model="editingLot.level" placeholder="选择级别" style="width: 100%">
            <el-option label="低浓度" :value="1" />
            <el-option label="中浓度" :value="2" />
            <el-option label="高浓度" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用日期" prop="startDate">
          <el-date-picker v-model="editingLot.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期" prop="endDate">
          <el-date-picker v-model="editingLot.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editingLot.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  getQcLotList,
  createQcLot,
  updateQcLot,
  deleteQcLot,
  QC_LEVEL_MAP,
  type QcLot
} from '@/api/qc'
import { getAllTestItems, type TestItem } from '@/api/base'
import { getAllInstruments, getInstrumentItemMaps, type Instrument } from '@/api/instrument'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const lotList = ref<QcLot[]>([])
const testItemList = ref<TestItem[]>([])
const instrumentList = ref<Instrument[]>([])

const searchForm = reactive({
  lotNo: '',
  testItemId: undefined as number | undefined,
  instrumentId: undefined as number | undefined
})

const editingLot = reactive({
  id: undefined as number | undefined,
  lotNo: '',
  testItemId: undefined as number | undefined,
  instrumentId: undefined as number | undefined,
  targetValue: undefined as number | undefined,
  sd: undefined as number | undefined,
  cv: undefined as number | undefined,
  level: 2,
  startDate: '',
  endDate: '',
  status: 1
})

const formRules = {
  lotNo: [{ required: true, message: '请输入批号', trigger: 'blur' }],
  instrumentId: [{ required: true, message: '请先选择仪器', trigger: 'change' }],
  testItemId: [{ required: true, message: '请选择检验指标', trigger: 'change' }],
  targetValue: [{ required: true, message: '请输入靶值', trigger: 'blur' }],
  sd: [{ required: true, message: '请输入标准差', trigger: 'blur' }],
  level: [{ required: true, message: '请选择浓度级别', trigger: 'change' }]
}

const filteredList = computed(() => {
  return lotList.value.filter(item => {
    if (searchForm.lotNo && !item.lotNo.toLowerCase().includes(searchForm.lotNo.toLowerCase())) return false
    if (searchForm.testItemId && item.testItemId !== searchForm.testItemId) return false
    if (searchForm.instrumentId && item.instrumentId !== searchForm.instrumentId) return false
    return true
  })
})

function levelTagType(level: number): string {
  if (level === 1) return 'info'
  if (level === 3) return 'danger'
  return 'primary'
}

function getTestItemName(id: number | undefined): string {
  if (!id) return '-'
  return testItemList.value.find(t => t.id === id)?.itemName || '-'
}

function getInstrumentName(id: number | undefined): string {
  if (!id) return '-'
  return instrumentList.value.find(i => i.id === id)?.instrumentName || '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getQcLotList()
    lotList.value = res.records || []
  } catch (e) {
    ElMessage.error('加载批号列表失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [items, instrs] = await Promise.all([
      getAllTestItems(),
      getAllInstruments()
    ])
    testItemList.value = items || []
    instrumentList.value = instrs || []
  } catch (e) {
    // ignore
  }
}

// 所选仪器绑定的检验指标 id 集合 (新增/编辑弹窗)
const dialogBoundTestItemIds = ref<Set<number>>(new Set())
// 搜索区所选仪器绑定的检验指标 id 集合
const searchBoundTestItemIds = ref<Set<number>>(new Set())

/** 弹窗指标下拉: 强制按所选仪器过滤。未选仪器时禁用/置空, 仅显示该仪器绑定的指标；
 *  编辑模式下，若当前已选指标不在绑定集合中，保留显示以便回显并给出变更提示。
 */
const dialogTestItemList = computed(() => {
  // 未选仪器：显示空列表（配合 disabled）
  if (!editingLot.instrumentId) return []
  const bound = testItemList.value.filter(t => dialogBoundTestItemIds.value.has(Number(t.id)))
  // 编辑回显：若数据库里 testItemId 不属于该仪器绑定，也保留用于识别异常
  if (editingLot.testItemId && !bound.some(t => Number(t.id) === Number(editingLot.testItemId))) {
    const cur = testItemList.value.find(t => Number(t.id) === Number(editingLot.testItemId))
    if (cur) bound.push({ ...cur, itemName: (cur.itemName || '') + '（未关联到当前仪器，建议更换）' } as any)
  }
  return bound
})

/** 搜索区指标下拉: 按所选仪器过滤, 未选仪器时显示全部 */
const filteredSearchTestItemList = computed(() => {
  if (!searchForm.instrumentId) return testItemList.value
  return testItemList.value.filter(t => searchBoundTestItemIds.value.has(Number(t.id)))
})

// 弹窗选择仪器时, 加载该仪器绑定的指标。
// 切换仪器时清空原选中的 testItemId，避免错配到其他仪器指标下；
// 若新绑定集合中仍包含旧指标值，则保留（仅当值本身在新集合中时）。
watch(() => editingLot.instrumentId, async (id, oldId) => {
  dialogBoundTestItemIds.value = new Set()
  if (id) {
    try {
      const maps = await getInstrumentItemMaps(Number(id))
      dialogBoundTestItemIds.value = new Set((maps || []).map(m => Number(m.testItemId)))
    } catch (e) {
      // ignore
    }
  }
  // 切换仪器（包括从 null→id、id→id、id→null）时，若当前选中的 testItemId 不在新绑定集合中，清空
  if (oldId !== id && editingLot.testItemId != null) {
    if (!dialogBoundTestItemIds.value.has(Number(editingLot.testItemId))) {
      editingLot.testItemId = undefined
    }
  }
})

// 搜索区选择仪器时, 加载该仪器绑定的指标并校验当前所选指标
watch(() => searchForm.instrumentId, async (id) => {
  searchBoundTestItemIds.value = new Set()
  if (id) {
    try {
      const maps = await getInstrumentItemMaps(Number(id))
      searchBoundTestItemIds.value = new Set((maps || []).map(m => Number(m.testItemId)))
    } catch (e) {
      // ignore
    }
  }
  if (searchForm.testItemId && !searchBoundTestItemIds.value.has(Number(searchForm.testItemId))) {
    searchForm.testItemId = undefined
  }
})

function handleSearch() {
  // filteredList 是 computed, 自动响应
}

function handleReset() {
  searchForm.lotNo = ''
  searchForm.testItemId = undefined
  searchForm.instrumentId = undefined
}

function openDialog(row?: QcLot) {
  if (row) {
    Object.assign(editingLot, {
      id: row.id,
      lotNo: row.lotNo,
      testItemId: row.testItemId,
      instrumentId: row.instrumentId,
      targetValue: Number(row.targetValue),
      sd: Number(row.sd),
      cv: row.cv ? Number(row.cv) : undefined,
      level: row.level,
      startDate: row.startDate || '',
      endDate: row.endDate || '',
      status: row.status
    })
  } else {
    Object.assign(editingLot, {
      id: undefined,
      lotNo: '',
      testItemId: undefined,
      instrumentId: undefined,
      targetValue: undefined,
      sd: undefined,
      cv: undefined,
      level: 2,
      startDate: '',
      endDate: '',
      status: 1
    })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (editingLot.id) {
        await updateQcLot(editingLot as QcLot)
        ElMessage.success('修改成功')
      } else {
        await createQcLot(editingLot)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {
      ElMessage.error('保存失败')
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row: QcLot) {
  try {
    await ElMessageBox.confirm(`确认删除批号「${row.lotNo}」？`, '删除确认', { type: 'warning' })
    await deleteQcLot(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped lang="scss">
.qc-lot-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;
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
