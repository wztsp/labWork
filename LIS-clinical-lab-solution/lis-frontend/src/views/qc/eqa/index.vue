<template>
  <div class="eqa-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="EQA批号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="项目">
          <el-input
            v-model="searchForm.itemName"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="是否合格">
          <el-select v-model="searchForm.qualified" placeholder="全部" clearable style="width: 120px">
            <el-option label="合格" :value="1" />
            <el-option label="不合格" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleSubmit">提交EQA数据</el-button>
          <el-button type="warning" :icon="MagicStick" @click="handleGenerate">生成数据</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 合格率统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-total">
          <div class="stat-icon">
            <el-icon><DataLine /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">EQA总数</div>
            <div class="stat-value">{{ stats.totalCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-pass">
          <div class="stat-icon">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">合格数</div>
            <div class="stat-value">{{ stats.qualifiedCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card stat-rate">
          <div class="stat-icon">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">合格率</div>
            <div class="stat-value">{{ stats.passRate }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="batchNo" label="EQA批号" width="140" />
        <el-table-column prop="itemName" label="项目" min-width="140" />
        <el-table-column prop="instrumentName" label="仪器" min-width="140" />
        <el-table-column prop="targetValue" label="靶值" width="100" align="center" />
        <el-table-column prop="measureValue" label="实测值" width="100" align="center" />
        <el-table-column label="得分" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.score != null" :class="{ 'score-fail': row.score < 80 }">{{ row.score }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.grade" :type="gradeTagType(row.grade)" size="small">{{ row.grade }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="是否合格" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.qualified != null" :type="row.qualified === 1 ? 'success' : 'danger'" size="small">
              {{ row.qualified === 1 ? '合格' : '不合格' }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.score == null"
              type="primary"
              link
              size="small"
              @click="handleCalculate(row)"
            >计算得分</el-button>
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

    <!-- 提交EQA数据弹窗 -->
    <el-dialog v-model="submitDialogVisible" title="提交EQA数据" width="560px">
      <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="EQA批号" prop="batchNo">
              <el-input v-model="submitForm.batchNo" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目" prop="itemName">
              <el-select
                v-model="submitForm.itemName"
                filterable
                allow-create
                default-first-option
                placeholder="请选择或输入项目"
                style="width: 100%"
              >
                <el-option v-for="item in testItemOptions" :key="item.id" :label="item.itemName" :value="item.itemName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仪器" prop="instrumentName">
              <el-select
                v-model="submitForm.instrumentName"
                filterable
                allow-create
                default-first-option
                placeholder="请选择或输入仪器"
                style="width: 100%"
              >
                <el-option v-for="item in instrumentOptions" :key="item.id" :label="item.instrumentName" :value="item.instrumentName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="靶值" prop="targetValue">
              <el-input-number v-model="submitForm.targetValue" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实测值" prop="measureValue">
              <el-input-number v-model="submitForm.measureValue" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 生成EQA数据弹窗 -->
    <el-dialog v-model="generateDialogVisible" title="生成EQA测试数据" width="620px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        根据设定参数批量生成EQA记录，靶值和实测值在指定范围内随机生成，合格率控制实测值与靶值的偏差。
      </el-alert>
      <el-form :model="generateForm" label-width="110px" size="default">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生成条数">
              <el-input-number v-model="generateForm.count" :min="1" :max="100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批号前缀">
              <el-input v-model="generateForm.batchPrefix" placeholder="如 EQA2026">
                <template #append>
                  <span style="font-size:12px;color:#909399">+序号</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="靶值范围">
              <el-input-number v-model="generateForm.targetMin" :precision="2" controls-position="right" style="width: 45%" />
              <span style="margin: 0 4px">~</span>
              <el-input-number v-model="generateForm.targetMax" :precision="2" controls-position="right" style="width: 45%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合格率(%)">
              <el-input-number v-model="generateForm.passRate" :min="0" :max="100" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="偏差范围(%)">
              <el-input-number v-model="generateForm.deviation" :min="1" :max="50" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="检验项目">
              <el-select
                v-model="generateForm.itemNames"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择或输入检验项目(可多选, 留空则随机)"
                style="width: 100%"
              >
                <el-option v-for="item in testItemOptions" :key="item.id" :label="item.itemName" :value="item.itemName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="仪器名称">
              <el-select
                v-model="generateForm.instrumentNames"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择或输入仪器名称(可多选, 留空则随机)"
                style="width: 100%"
              >
                <el-option v-for="item in instrumentOptions" :key="item.id" :label="item.instrumentName" :value="item.instrumentName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="generate-rows">
        <div class="generate-ctrl">
          <span class="preview-label">生成预览 (可直接修改靶值/实测值):</span>
          <el-button size="small" :icon="Refresh" @click="regenerateRows">重新生成</el-button>
        </div>
        <el-table :data="generatedRows" border stripe size="small" max-height="260">
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="batchNo" label="批号" width="110" />
          <el-table-column prop="itemName" label="项目" min-width="110" show-overflow-tooltip />
          <el-table-column prop="instrumentName" label="仪器" min-width="130" show-overflow-tooltip />
          <el-table-column label="靶值" width="140" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.targetValue" :precision="2" :step="0.1" controls-position="right" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="实测值" width="140" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.measureValue" :precision="2" :step="0.1" controls-position="right" size="small" style="width: 100%" />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="generating" @click="confirmGenerate">生成 {{ generatedRows.length }} 条</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getEqaPage,
  submitEqa,
  batchSubmitEqa,
  calculateEqaScore,
  getEqaStats
} from '@/api/qc/eqa'
import { getAllTestItems, getAllInstrumentDicts, type TestItem, type InstrumentDict } from '@/api/base'
import { DataLine, CircleCheck, TrendCharts, MagicStick, Refresh } from '@element-plus/icons-vue'

interface EqaRecord {
  id: number
  batchNo: string
  itemName: string
  instrumentName: string
  targetValue: number
  measureValue: number
  score?: number
  grade?: string
  qualified?: number
}

/** 生成预览中的一行 (靶值/实测值可编辑) */
interface GeneratedRow {
  batchNo: string
  itemName: string
  instrumentName: string
  targetValue: number
  measureValue: number
}

const loading = ref(false)
const tableData = ref<EqaRecord[]>([])
const searchForm = reactive({
  batchNo: '',
  itemName: '',
  qualified: undefined as number | undefined
})

const stats = reactive({
  totalCount: 0,
  qualifiedCount: 0,
  passRate: 0
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 提交弹窗
const submitDialogVisible = ref(false)
const submitLoading = ref(false)
const submitFormRef = ref<FormInstance>()
const submitForm = reactive({
  batchNo: '',
  itemName: '',
  instrumentName: '',
  targetValue: 0,
  measureValue: 0
})
const submitRules: FormRules = {
  batchNo: [{ required: true, message: '请输入EQA批号', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入项目', trigger: 'blur' }],
  instrumentName: [{ required: true, message: '请输入仪器', trigger: 'blur' }],
  targetValue: [{ required: true, message: '请输入靶值', trigger: 'blur' }],
  measureValue: [{ required: true, message: '请输入实测值', trigger: 'blur' }]
}

function gradeTagType(grade: string): string {
  const map: Record<string, string> = { A: 'success', B: 'primary', C: 'warning', D: 'danger' }
  return map[grade] || 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await getEqaPage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      batchNo: searchForm.batchNo || undefined,
      itemName: searchForm.itemName || undefined,
      qualified: searchForm.qualified
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const res: any = await getEqaStats({
      batchNo: searchForm.batchNo || undefined,
      itemName: searchForm.itemName || undefined
    })
    stats.totalCount = res?.totalCount || 0
    stats.qualifiedCount = res?.qualifiedCount || 0
    stats.passRate = stats.totalCount > 0
      ? Math.round((stats.qualifiedCount / stats.totalCount) * 1000) / 10
      : 0
  } catch (e) {
    // ignore
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
  fetchStats()
}

function handleReset() {
  searchForm.batchNo = ''
  searchForm.itemName = ''
  searchForm.qualified = undefined
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

function handleSubmit() {
  Object.assign(submitForm, {
    batchNo: '',
    itemName: '',
    instrumentName: '',
    targetValue: 0,
    measureValue: 0
  })
  loadOptions()
  submitDialogVisible.value = true
}

async function handleSaveSubmit() {
  if (!submitFormRef.value) return
  await submitFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await submitEqa({ ...submitForm })
      ElMessage.success('提交成功')
      submitDialogVisible.value = false
      fetchList()
      fetchStats()
    } catch (e: any) {
      // 提交失败时明确提示, 避免用户误以为字段被清空 / 无响应
      ElMessage.error(e?.message || '提交EQA数据失败，请检查必填项后重试')
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleCalculate(row: EqaRecord) {
  try {
    await ElMessageBox.confirm(`确认计算项目 ${row.itemName} 的EQA得分吗?`, '提示', { type: 'info' })
    await calculateEqaScore(row.id)
    ElMessage.success('计算完成')
    fetchList()
    fetchStats()
  } catch (e) {
    // 取消或失败
  }
}

// ============ 手工生成EQA数据 ============
const generateDialogVisible = ref(false)
const generating = ref(false)
const testItemOptions = ref<TestItem[]>([])
const instrumentOptions = ref<InstrumentDict[]>([])

const defaultItems = ['白细胞计数', '红细胞计数', '血红蛋白', '血小板计数', '血糖', '尿素', '肌酐', 'ALT', 'AST', '总蛋白']
const defaultInstruments = ['迈瑞 BC-7500', '罗氏 cobas 8000', '希森美康 XN-1000', '雅培 Architect']

const generateForm = reactive({
  count: 10,
  batchPrefix: `EQA${new Date().getFullYear()}`,
  targetMin: 5,
  targetMax: 200,
  passRate: 80,
  deviation: 10,
  itemNames: [] as string[],
  instrumentNames: [] as string[]
})

/** 随机数工具 */
function randFloat(min: number, max: number, decimals = 2): number {
  return Number((Math.random() * (max - min) + min).toFixed(decimals))
}
function pick(arr: string[]): string {
  return arr[Math.floor(Math.random() * arr.length)] || ''
}

/** 生成单条EQA记录数据 */
function generateOne(idx: number): { batchNo: string; itemName: string; instrumentName: string; targetValue: number; measureValue: number; qualified: boolean } {
  const batchNo = `${generateForm.batchPrefix || 'EQA'}${String(idx).padStart(4, '0')}`
  const items = generateForm.itemNames.length ? generateForm.itemNames : defaultItems
  const instruments = generateForm.instrumentNames.length ? generateForm.instrumentNames : defaultInstruments
  const itemName = pick(items) || defaultItems[0]
  const instrumentName = pick(instruments) || defaultInstruments[0]
  const targetValue = randFloat(generateForm.targetMin, generateForm.targetMax)

  // 根据合格率决定本条是否合格
  const shouldBeQualified = Math.random() * 100 < generateForm.passRate
  const dev = generateForm.deviation / 100
  let measureValue: number
  if (shouldBeQualified) {
    // 合格: 偏差在 deviation 以内
    const offset = (Math.random() * 2 - 1) * dev * targetValue
    measureValue = Number((targetValue + offset).toFixed(2))
  } else {
    // 不合格: 偏差超过 deviation
    const sign = Math.random() < 0.5 ? -1 : 1
    const offset = sign * (dev + Math.random() * dev) * targetValue
    measureValue = Number((targetValue + offset).toFixed(2))
  }
  const qualified = Math.abs(measureValue - targetValue) / targetValue <= dev
  return { batchNo, itemName, instrumentName, targetValue, measureValue, qualified }
}

/** 预览刷新计数: 用于"重新生成"重新随机 */
const generatedRows = ref<GeneratedRow[]>([])

/** 按当前参数重新生成全部预览行 */
function regenerateRows() {
  generatedRows.value = []
  for (let i = 0; i < generateForm.count; i++) {
    const r = generateOne(i + 1)
    generatedRows.value.push({
      batchNo: r.batchNo,
      itemName: r.itemName,
      instrumentName: r.instrumentName,
      targetValue: r.targetValue,
      measureValue: r.measureValue
    })
  }
}

function handleGenerate() {
  generateForm.batchPrefix = `EQA${new Date().getFullYear()}`
  generateForm.count = 10
  generateForm.itemNames = []
  generateForm.instrumentNames = []
  generateDialogVisible.value = true
  loadOptions()
  regenerateRows()
}

async function loadOptions() {
  if (!testItemOptions.value.length) {
    try {
      const items = await getAllTestItems()
      testItemOptions.value = (items || []).filter(t => t.isPanel !== 1)
    } catch {
      testItemOptions.value = []
    }
  }
  if (!instrumentOptions.value.length) {
    try {
      instrumentOptions.value = await getAllInstrumentDicts()
    } catch {
      instrumentOptions.value = []
    }
  }
}

async function confirmGenerate() {
  if (!generatedRows.value.length) {
    ElMessage.warning('请先生成数据')
    return
  }
  generating.value = true
  try {
    // 使用当前(可编辑)的生成行数据一次批量提交
    const list = generatedRows.value.map(r => ({
      batchNo: r.batchNo,
      itemName: r.itemName,
      instrumentName: r.instrumentName,
      targetValue: r.targetValue,
      measureValue: r.measureValue
    }))
    const success = await batchSubmitEqa(list)
    if (success > 0) {
      ElMessage.success(`成功生成 ${success} 条EQA数据`)
      generateDialogVisible.value = false
      fetchList()
      fetchStats()
    } else {
      ElMessage.error('生成失败，请检查后端服务是否正常')
    }
  } catch (e) {
    ElMessage.error('生成失败，请检查后端服务是否正常')
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  fetchList()
  fetchStats()
})
</script>

<style scoped lang="scss">
.eqa-page {
  padding: 16px;

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
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
    }

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      color: #fff;
      flex-shrink: 0;
    }

    .stat-info {
      flex: 1;
      min-width: 0;

      .stat-label {
        font-size: 14px;
        color: var(--el-text-color-secondary);
        margin-bottom: 6px;
      }

      .stat-value {
        font-size: 28px;
        font-weight: bold;
        color: var(--el-text-color-primary);
        line-height: 1.2;
      }
    }

    &.stat-total .stat-icon { background: var(--el-color-primary); }
    &.stat-pass {
      .stat-icon { background: var(--el-color-success); }
      .stat-value { color: var(--el-color-success); }
    }
    &.stat-rate {
      .stat-icon { background: var(--el-color-warning); }
      .stat-value { color: var(--el-color-warning); }
    }
  }

  .table-card {
    .score-fail {
      color: var(--el-color-danger);
      font-weight: bold;
    }

    .text-muted {
      color: var(--el-text-color-placeholder);
    }

    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }
}

.generate-rows {
  margin-top: 16px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px dashed #dcdfe6;

  .generate-ctrl {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .preview-label {
    font-size: 12px;
    font-weight: 600;
    color: #909399;
  }
}
</style>
