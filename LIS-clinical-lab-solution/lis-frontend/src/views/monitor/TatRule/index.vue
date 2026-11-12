<template>
  <div class="tat-rule-page">
    <el-card shadow="never">
      <!-- 启用开关 -->
      <div class="enable-bar">
        <el-switch
          v-model="enabled"
          active-text="启用 TAT 超时规则监控"
          inactive-text="停用 TAT 超时规则监控"
          @change="handleToggleEnabled"
        />
        <span class="enable-tip">启用后系统将按以下规则扫描标本并发送 TAT 超时提醒</span>
      </div>

      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="标本类型">
          <el-select
            v-model="queryParams.specimenType"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="s in specimenTypes"
              :key="String(s.id)"
              :label="s.typeName"
              :value="s.typeCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="检验项目名称"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增TAT规则</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="标本类型" width="150">
          <template #default="{ row }">
            <el-tag v-if="!row.specimenType" type="warning" size="small">全部标本类型</el-tag>
            <span v-else>{{ row.specimenType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="testItemName" label="检验项目" min-width="180" />
        <el-table-column label="急查超时(分钟)" width="140" align="center">
          <template #default="{ row }">{{ row.urgentTimeout }}</template>
        </el-table-column>
        <el-table-column label="普通超时(分钟)" width="140" align="center">
          <template #default="{ row }">{{ row.normalTimeout }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该TAT规则?" @confirm="handleDelete(row)">
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
          :page-sizes="[10, 20, 50, 100]"
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
      :title="editForm.id ? '编辑TAT规则' : '新增TAT规则'"
      width="520px"
    >
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="110px">
        <el-form-item label="标本类型" prop="specimenType">
          <el-select
            v-model="editForm.specimenType"
            placeholder="不选默认匹配全部标本类型"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option label="【全部标本类型】(不指定)" value="" />
            <el-option
              v-for="s in specimenTypes"
              :key="String(s.id)"
              :label="s.typeName"
              :value="s.typeCode"
            />
          </el-select>
          <div class="form-tip">不选择则匹配该检验项目下的全部标本类型</div>
        </el-form-item>
        <el-form-item label="检验项目" prop="testItemId">
          <el-select
            v-model="editForm.testItemId"
            placeholder="请选择检验项目"
            filterable
            style="width: 100%"
            @change="onItemChange"
          >
            <el-option
              v-for="p in allTestItems"
              :key="String(p.id)"
              :label="buildItemLabel(p)"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="急查超时(分钟)" prop="urgentTimeout">
          <el-input-number v-model="editForm.urgentTimeout" :min="1" :max="1440" style="width: 100%" />
        </el-form-item>
        <el-form-item label="普通超时(分钟)" prop="normalTimeout">
          <el-input-number v-model="editForm.normalTimeout" :min="1" :max="1440" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getTatRulePage,
  saveTatRule,
  deleteTatRule,
  getTatEnabled,
  setTatEnabled,
  type TatRule,
  type TatRuleQuery
} from '@/api/monitor'
import { getAllSpecimenTypes, getAllTestItems, type SpecimenType, type TestItem } from '@/api/base'

const loading = ref(false)
const saving = ref(false)
const enabled = ref(false)
const tableData = ref<TatRule[]>([])
const total = ref(0)
const specimenTypes = ref<SpecimenType[]>([])
/** 全部启用的检验项目 (不再只取 isPanel=1, 避免显示不全) */
const allTestItems = ref<TestItem[]>([])
const queryParams = reactive<TatRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  specimenType: '',
  keyword: ''
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<TatRule>({
  id: 0,
  specimenType: '',
  testItemId: undefined,
  testItemName: '',
  urgentTimeout: 60,
  normalTimeout: 120,
  enabled: 1,
  remark: ''
})

const rules = {
  testItemId: [{ required: true, message: '请选择检验项目', trigger: 'change' }],
  urgentTimeout: [{ required: true, message: '请输入急查超时时间', trigger: 'blur' }],
  normalTimeout: [{ required: true, message: '请输入普通超时时间', trigger: 'blur' }]
}

/** 格式化下拉显示的项目名: [编码] 名称 */
function buildItemLabel(p: TestItem) {
  return p.itemCode ? `[${p.itemCode}] ${p.itemName}` : p.itemName
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getTatRulePage(queryParams)
    tableData.value = res.records || []
    total.value = res.total || 0
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
  queryParams.specimenType = ''
  queryParams.keyword = ''
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

/** 按 String 统一比较 ID, 避免 Long/String 精度不匹配 */
function onItemChange(id: number | string) {
  const p = allTestItems.value.find(x => String(x.id) === String(id))
  editForm.testItemName = p ? p.itemName : ''
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0, specimenType: '', testItemId: undefined, testItemName: '',
    urgentTimeout: 60, normalTimeout: 120, enabled: 1, remark: ''
  })
  dialogVisible.value = true
}

function handleEdit(row: TatRule) {
  Object.assign(editForm, {
    ...row,
    specimenType: row.specimenType || ''
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveTatRule({ ...editForm, specimenType: editForm.specimenType || undefined })
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

async function handleDelete(row: TatRule) {
  try {
    await deleteTatRule(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // ignore
  }
}

async function handleToggleEnabled(val: boolean) {
  try {
    await setTatEnabled(val)
    ElMessage.success(val ? '已启用 TAT 规则' : '已停用 TAT 规则')
  } catch (e) {
    enabled.value = !val
  }
}

async function loadDicts() {
  try {
    specimenTypes.value = (await getAllSpecimenTypes()) || []
  } catch (e) {
    // ignore
  }
  try {
    // 全部启用的检验项目: 不再过滤 isPanel, 避免"显示不全"
    allTestItems.value = (await getAllTestItems()) || []
  } catch (e) {
    // ignore
  }
}

onMounted(async () => {
  await loadDicts()
  try {
    enabled.value = await getTatEnabled()
  } catch (e) {
    // ignore
  }
  fetchList()
})
</script>

<style scoped lang="scss">
.tat-rule-page {
  padding: 16px;

  .enable-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    background: #f5f7fa;
    border-radius: 6px;
    margin-bottom: 16px;

    .enable-tip {
      font-size: 12px;
      color: #909399;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }
}
</style>
