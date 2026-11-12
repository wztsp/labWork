<template>
  <div class="culture-plan-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="keyword"
            placeholder="方案名称/检验项目"
            clearable
            style="width: 220px"
            @keyup.enter="fetchList"
            @clear="fetchList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchList">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增培养方案</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="planName" label="方案名称" min-width="160" />
        <el-table-column label="绑定检验项目" min-width="180">
          <template #default="{ row }">{{ row.testItemName || '-' }}</template>
        </el-table-column>
        <el-table-column label="标本类型" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.sampleType" size="small" type="warning">{{ specimenTypeName(row.sampleType) }}</el-tag>
            <span v-else class="text-gray">全部</span>
          </template>
        </el-table-column>
        <el-table-column label="培养皿" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="m in row.mediumSummary || []" :key="m" size="small" style="margin: 2px">{{ m }}</el-tag>
            <span v-if="!row.mediumSummary || !row.mediumSummary.length" class="text-gray">未配置培养基</span>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small" effect="dark">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="editForm.id ? '编辑培养方案' : '新增培养方案'" width="720px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="方案名称" prop="planName">
              <el-input v-model="editForm.planName" placeholder="如: 大便微生物培养" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="绑定检验项目" prop="testItemId">
              <el-select
                v-model="editForm.testItemId"
                filterable
                placeholder="选择检验项目"
                style="width: 100%"
                @change="handleTestItemChange"
              >
                <el-option
                  v-for="item in testItemOptions"
                  :key="item.id"
                  :label="item.itemName"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标本类型">
              <el-select
                v-model="editForm.sampleType"
                filterable
                clearable
                default-first-option
                placeholder="选择标本类型(留空=全部标本)"
                style="width: 100%"
              >
                <el-option
                  v-for="t in specimenTypeOptions"
                  :key="t.value"
                  :label="t.label"
                  :value="t.value"
                />
              </el-select>
              <div class="form-tip" style="margin-top:2px">留空表示该方案适用于全部标本类型; 选择后仅在对应标本类型时匹配</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认方案">
              <el-switch v-model="editForm.isDefault" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" placeholder="备注(可选)" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="培养皿配置">
              <div class="plate-item-list">
                <div class="plate-item" v-for="(item, idx) in planItems" :key="idx">
                  <el-select
                    v-model="item.mediumTypeId"
                    filterable
                    placeholder="选择培养基"
                    style="width: 220px"
                    @change="(val: any) => handleMediumChange(item, val)"
                  >
                    <el-option
                      v-for="m in mediumOptions"
                      :key="m.id"
                      :label="m.mediumName"
                      :value="m.id"
                    />
                  </el-select>
                  <span class="qty-label">数量</span>
                  <el-input-number v-model="item.quantity" :min="1" :max="20" style="width: 90px" />
                  <el-button type="danger" link :icon="Delete" @click="removePlateItem(idx)" />
                  <el-tag v-if="item.mediumTypeName" size="small" effect="plain" style="margin-left: 4px">
                    {{ item.mediumTypeName }}
                  </el-tag>
                </div>
                <el-button type="primary" plain :icon="Plus" @click="addPlateItem">添加培养基</el-button>
              </div>
              <div class="form-tip">配置该标本需接种的多个培养皿, 生成条码时按培养基×数量生成对应数量的培养皿条码</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Delete } from '@element-plus/icons-vue'
import {
  getCulturePlanPage,
  getCulturePlanDetail,
  saveCulturePlan,
  deleteCulturePlan,
  getAllMediumTypes,
  type CulturePlan,
  type CulturePlanItem,
  type MediumType
} from '@/api/micro'
import { getAllTestItems, getAllSpecimenTypes, type TestItem, type SpecimenType } from '@/api/base'

interface PlanRow extends CulturePlan {
  mediumSummary?: string[]
}

const loading = ref(false)
const tableData = ref<PlanRow[]>([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const testItemOptions = ref<TestItem[]>([])
const mediumOptions = ref<MediumType[]>([])
/** 基础数据标本类型 (typeCode 存库, typeName 展示) */
const specimenTypeDicts = ref<SpecimenType[]>([])
/** 标本类型下拉首项 + typeCode 映射 typeName 的选项列表 */
const specimenTypeOptions = ref<Array<{ label: string; value: string }>>([])

/** 把 typeCode 转为 typeName (未知 code 时回显原 code) */
function specimenTypeName(code?: string): string {
  if (!code) return ''
  const s = specimenTypeDicts.value.find((x) => x.typeCode === code)
  return s?.typeName || code
}

const formVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<CulturePlan>({
  id: undefined,
  planName: '',
  testItemId: undefined,
  testItemName: '',
  sampleType: '',
  remark: '',
  isDefault: 0,
  status: 1
})
const planItems = ref<CulturePlanItem[]>([])
const formRules: FormRules = {
  planName: [{ required: true, message: '请输入方案名称', trigger: 'blur' }],
  testItemId: [{ required: true, message: '请选择绑定的检验项目', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getCulturePlanPage({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    tableData.value = (res?.records || []).map((p) => {
      const row: PlanRow = { ...p }
      if (p.id) {
        row.mediumSummary = undefined
      }
      return row
    })
    total.value = res?.total || 0
    // 异步加载各方案的培养基明细摘要
    for (const row of tableData.value) {
      if (row.id) {
        try {
          const detail = await getCulturePlanDetail(row.id)
          row.mediumSummary = (detail?.items || [])
            .map((it) => `${it.mediumTypeName || ''}${(it.quantity || 1) > 1 ? `×${it.quantity}` : ''}`)
            .filter(Boolean)
        } catch (e) {
          row.mediumSummary = []
        }
      }
    }
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleTestItemChange(val: number) {
  const item = testItemOptions.value.find((t) => t.id === val)
  editForm.testItemName = item?.itemName || ''
}

function handleMediumChange(item: CulturePlanItem, val: number) {
  const m = mediumOptions.value.find((x) => x.id === val)
  item.mediumTypeName = m?.mediumName || ''
}

function addPlateItem() {
  planItems.value.push({ mediumTypeId: undefined, mediumTypeName: '', quantity: 1 })
}

function removePlateItem(idx: number) {
  planItems.value.splice(idx, 1)
}

function handleAdd() {
  Object.assign(editForm, { id: undefined, planName: '', testItemId: undefined, testItemName: '', sampleType: '', remark: '', isDefault: 0, status: 1 })
  planItems.value = []
  formVisible.value = true
}

async function handleEdit(row: PlanRow) {
  if (!row.id) return
  try {
    const detail = await getCulturePlanDetail(row.id)
    Object.assign(editForm, {
      id: detail.id,
      planName: detail.planName,
      testItemId: detail.testItemId,
      testItemName: detail.testItemName,
      sampleType: detail.sampleType ?? '',
      remark: detail.remark ?? '',
      isDefault: detail.isDefault ?? 0,
      status: detail.status ?? 1
    })
    // 回显检验项目名称(若不在下拉列表中也要显示)
    if (detail.testItemName && !testItemOptions.value.some((t) => t.id === detail.testItemId)) {
      testItemOptions.value.push({
        id: detail.testItemId as number,
        itemCode: '',
        itemName: detail.testItemName,
        status: 1
      })
    }
    planItems.value = (detail.items || []).map((it) => ({ ...it }))
    formVisible.value = true
  } catch (e) {
    // ignore
  }
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (planItems.value.length === 0 || planItems.value.some((it) => !it.mediumTypeId)) {
      ElMessage.warning('请至少配置一个培养基')
      return
    }
    formLoading.value = true
    try {
      await saveCulturePlan({
        plan: { ...editForm },
        items: planItems.value.map((it) => ({ ...it }))
      })
      ElMessage.success('保存成功')
      formVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: PlanRow) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除培养方案 "${row.planName}" 吗?`, '提示', { type: 'warning' })
    await deleteCulturePlan(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(async () => {
  fetchList()
  try {
    testItemOptions.value = (await getAllTestItems()) || []
  } catch (e) {
    testItemOptions.value = []
  }
  try {
    mediumOptions.value = (await getAllMediumTypes()) || []
  } catch (e) {
    mediumOptions.value = []
  }
  try {
    const dicts = (await getAllSpecimenTypes()) || []
    specimenTypeDicts.value = dicts as SpecimenType[]
    // 首项: 不指定/全部 (空 value) + 基础数据中各标本类型 (typeCode存值, typeName显示)
    specimenTypeOptions.value = [
      { label: '【全部标本类型】(不指定)', value: '' },
      ...specimenTypeDicts.value.map((s) => ({ label: s.typeName || s.typeCode, value: s.typeCode || '' }))
    ]
  } catch (e) {
    specimenTypeDicts.value = []
    specimenTypeOptions.value = [{ label: '【全部标本类型】(不指定)', value: '' }]
  }
})
</script>

<style scoped lang="scss">
.culture-plan-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0;
    }
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .plate-item-list {
    width: 100%;

    .plate-item {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }

    .qty-label {
      font-size: 13px;
      color: #606266;
      white-space: nowrap;
    }
  }

  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
    margin-top: 4px;
  }

  .text-gray {
    color: #909399;
  }
}
</style>