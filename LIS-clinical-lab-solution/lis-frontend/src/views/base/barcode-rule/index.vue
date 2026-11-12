<template>
  <div class="barcode-rule-page">
    <el-card shadow="never">
      <!-- 查询区 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="规则编码/名称"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item label="日期格式">
          <el-select v-model="queryParams.datePattern" placeholder="全部" clearable style="width: 200px">
            <el-option v-for="o in DATE_PATTERN_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增条码规则</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="ruleCode" label="规则编码" width="140" />
        <el-table-column prop="ruleName" label="规则名称" width="160" />
        <el-table-column prop="prefix" label="前缀" width="100" align="center" />
        <el-table-column label="日期格式" width="200" align="center">
          <template #default="{ row }">
            {{ datePatternLabel(row.datePattern) }}
          </template>
        </el-table-column>
        <el-table-column prop="seqLength" label="序号长度" width="100" align="center" />
        <el-table-column label="序号策略" width="160" align="center">
          <template #default="{ row }">
            {{ seqStrategyLabel(row.seqStrategy) }}
          </template>
        </el-table-column>
        <el-table-column label="条码示例" width="240">
          <template #default="{ row }">
            <span class="barcode-preview">{{ buildBarcodePreview(row, 1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleTest(row)">测试生成</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该条码规则?" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editForm.id ? '编辑条码规则' : '新增条码规则'"
      width="600px"
    >
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="110px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="editForm.ruleCode" placeholder="如: DEFAULT" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="editForm.ruleName" placeholder="如: 默认条码规则" />
        </el-form-item>
        <el-form-item label="前缀" prop="prefix">
          <el-input v-model="editForm.prefix" placeholder="如: LIS 或 留空" maxlength="10" show-word-limit />
        </el-form-item>
        <el-form-item label="日期格式" prop="datePattern">
          <el-select v-model="editForm.datePattern" placeholder="请选择日期格式" style="width: 100%">
            <el-option v-for="o in DATE_PATTERN_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="序号长度" prop="seqLength">
          <el-input-number v-model="editForm.seqLength" :min="1" :max="10" />
          <span style="margin-left: 12px; color: #909399; font-size: 12px">
            序号不足时左侧补0, 范围 1-10
          </span>
        </el-form-item>
        <el-form-item label="序号策略" prop="seqStrategy">
          <el-radio-group v-model="editForm.seqStrategy">
            <el-radio v-for="o in SEQ_STRATEGY_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="条码预览">
          <el-input :model-value="buildBarcodePreview(editForm, 1)" readonly>
            <template #prepend>示例1</template>
          </el-input>
          <div style="height: 8px"></div>
          <el-input :model-value="buildBarcodePreview(editForm, 99)" readonly>
            <template #prepend>示例2</template>
          </el-input>
          <div style="height: 8px"></div>
          <el-input :model-value="buildBarcodePreview(editForm, 1000)" readonly>
            <template #prepend>示例3</template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试生成弹窗 -->
    <el-dialog v-model="testDialogVisible" title="测试生成条码" width="480px">
      <el-form label-width="100px">
        <el-form-item label="当前规则">
          <span class="barcode-preview">{{ testRule?.ruleName }} ({{ testRule?.ruleCode }})</span>
        </el-form-item>
        <el-form-item label="生成数量">
          <el-input-number v-model="testCount" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="生成结果">
          <div v-loading="testLoading" class="test-result-box">
            <div v-for="(b, i) in testBarcodes" :key="i" class="test-result-item">
              {{ i + 1 }}. <span class="barcode-preview">{{ b }}</span>
            </div>
            <div v-if="!testBarcodes.length && !testLoading" style="color: #909399; padding: 12px">
              点击"生成"按钮测试条码生成
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testLoading" @click="doTestGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getBarcodeRuleList,
  updateBarcodeRule,
  buildBarcodePreview,
  DATE_PATTERN_OPTIONS,
  SEQ_STRATEGY_OPTIONS,
  testGenerateBarcode,
  type BarcodeRule
} from '@/api/base'

interface BarcodeRuleQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  datePattern?: string
  enabled?: number
}

const loading = ref(false)
const saving = ref(false)
const tableData = ref<BarcodeRule[]>([])
const total = ref(0)
const allData = ref<BarcodeRule[]>([])
const queryParams = reactive<BarcodeRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  datePattern: '',
  enabled: undefined
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<BarcodeRule>({
  id: '',
  ruleCode: '',
  ruleName: '',
  prefix: '',
  datePattern: 'yyyyMMdd',
  seqLength: 6,
  seqStrategy: 'daily',
  enabled: 1,
  remark: ''
})

const rules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  datePattern: [{ required: true, message: '请选择日期格式', trigger: 'change' }],
  seqLength: [{ required: true, message: '请输入序号长度', trigger: 'blur' }],
  seqStrategy: [{ required: true, message: '请选择序号策略', trigger: 'change' }]
}

const testDialogVisible = ref(false)
const testRule = ref<BarcodeRule | null>(null)
const testCount = ref(5)
const testLoading = ref(false)
const testBarcodes = ref<string[]>([])

function datePatternLabel(pattern: string): string {
  const found = DATE_PATTERN_OPTIONS.find(o => o.value === pattern)
  return found ? found.label : pattern
}

function seqStrategyLabel(strategy: string): string {
  const found = SEQ_STRATEGY_OPTIONS.find(o => o.value === strategy)
  return found ? found.label : strategy
}

function applyFilter() {
  let list = [...allData.value]
  if (queryParams.keyword) {
    const kw = queryParams.keyword.toLowerCase()
    list = list.filter(r =>
      r.ruleCode.toLowerCase().includes(kw) || r.ruleName.toLowerCase().includes(kw)
    )
  }
  if (queryParams.datePattern) {
    list = list.filter(r => r.datePattern === queryParams.datePattern)
  }
  if (queryParams.enabled !== undefined) {
    list = list.filter(r => r.enabled === queryParams.enabled)
  }
  return list
}

function applyPage(list: BarcodeRule[]) {
  const start = (queryParams.pageNum - 1) * queryParams.pageSize
  return list.slice(start, start + queryParams.pageSize)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getBarcodeRuleList()
    allData.value = Array.isArray(res) ? res : []
    const filtered = applyFilter()
    total.value = filtered.length
    tableData.value = applyPage(filtered)
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function resetAndFetch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.datePattern = ''
  queryParams.enabled = undefined
  queryParams.pageNum = 1
  fetchList()
}

function handleSizeChange(size: number) {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  const filtered = applyFilter()
  total.value = filtered.length
  tableData.value = applyPage(filtered)
}

function handlePageChange(page: number) {
  queryParams.pageNum = page
  const filtered = applyFilter()
  tableData.value = applyPage(filtered)
}

function handleAdd() {
  Object.assign(editForm, {
    id: '',
    ruleCode: '',
    ruleName: '',
    prefix: '',
    datePattern: 'yyyyMMdd',
    seqLength: 6,
    seqStrategy: 'daily',
    enabled: 1,
    remark: ''
  })
  dialogVisible.value = true
}

function handleEdit(row: BarcodeRule) {
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await updateBarcodeRule({ ...editForm })
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

async function handleDelete(row: BarcodeRule) {
  try {
    // 后端无单独删除接口, 逻辑上停用即删除
    await updateBarcodeRule({ ...row, enabled: 0 })
    ElMessage.success('已停用该规则')
    fetchList()
  } catch (e) {
    // ignore
  }
}

function handleTest(row: BarcodeRule) {
  testRule.value = row
  testBarcodes.value = []
  testDialogVisible.value = true
}

async function doTestGenerate() {
  if (!testRule.value) return
  testLoading.value = true
  testBarcodes.value = []
  try {
    const results: string[] = []
    for (let i = 0; i < testCount.value; i++) {
      const bc = await testGenerateBarcode(testRule.value.ruleCode)
      results.push(bc)
    }
    testBarcodes.value = results
  } catch (e) {
    // 后端接口不可用时, 前端本地模拟生成示例
    for (let i = 1; i <= testCount.value; i++) {
      testBarcodes.value.push(buildBarcodePreview(testRule.value!, i))
    }
  } finally {
    testLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.barcode-rule-page {
  padding: 16px;

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .barcode-preview {
    font-family: 'Consolas', 'Courier New', monospace;
    font-weight: 600;
    color: #1a73e8;
    letter-spacing: 0.5px;
  }

  .test-result-box {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    padding: 8px 12px;
    min-height: 120px;
    max-height: 300px;
    overflow-y: auto;
    background: #fafafa;

    .test-result-item {
      line-height: 2;
      font-size: 14px;
    }
  }
}
</style>
