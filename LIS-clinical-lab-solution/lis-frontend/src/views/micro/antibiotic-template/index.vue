<template>
  <div class="antibiotic-template-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="编码/名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="模板编码或名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="全部" clearable filterable style="width: 150px">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="code" label="模板编码" width="160" />
        <el-table-column prop="name" label="模板名称" min-width="180" />
        <el-table-column label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.templateCategory" size="small" type="warning">{{ row.templateCategory }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="检测方法" width="110" align="center">
          <template #default="{ row }">{{ row.defaultMethod || '-' }}</template>
        </el-table-column>
        <el-table-column label="抗生素数" width="90" align="center">
          <template #default="{ row }">{{ row.itemCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="绑定细菌" min-width="200">
          <template #default="{ row }">
            <template v-if="row.bacteriaNames && row.bacteriaNames.length">
              <el-tag v-for="n in row.bacteriaNames" :key="n" size="small" style="margin-right:4px">{{ n }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isSystem === 1" size="small" type="success">系统</el-tag>
            <el-tag v-else size="small" type="info">自建</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" :disabled="row.isSystem === 1" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑抗生素模板' : '新增抗生素模板'" width="860px" top="4vh" append-to-body>
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板编码" prop="code">
              <el-input v-model="editForm.code" :disabled="!!editForm.id && editForm.isSystem === 1" placeholder="如 ABX_GP_ROUTINE" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称" prop="name">
              <el-input v-model="editForm.name" placeholder="如 G+ 常规药敏板" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="editForm.templateCategory" placeholder="请选择" clearable filterable allow-create style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认检测方法">
              <el-select v-model="editForm.defaultMethod" placeholder="不预设" clearable style="width: 100%">
                <el-option label="MIC（最小抑菌浓度）" value="MIC" />
                <el-option label="K-B纸片法" value="K-B纸片法" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="适用仪器、板子货号、适用场景等" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">绑定细菌（选择细菌时可匹配该模板）</el-divider>
        <el-form-item label="适用细菌">
          <el-select
            v-model="editForm.bacteriaIds"
            multiple
            filterable
            placeholder="选择该模板适用的细菌（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="b in bacteriaOptions"
              :key="b.id"
              :label="`${b.bacteriaName} (${b.bacteriaCode})`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">抗生素明细</el-divider>
        <el-form-item>
          <el-button type="primary" plain size="small" :disabled="!!editForm.id && editForm.isSystem === 1" @click="addItemRow">+ 添加抗生素</el-button>
        </el-form-item>
        <el-table :data="editForm.items" border stripe size="small">
          <el-table-column label="抗生素" min-width="260">
            <template #default="{ row }">
              <el-select
                v-model="row.antibioticId"
                filterable
                placeholder="搜索或选择抗生素"
                style="width: 100%"
                @change="(val:number)=>onItemAntibioticChange(row, val)"
              >
                <el-option
                  v-for="a in antibioticOptions"
                  :key="a.id"
                  :label="`${a.antibioticName} (${a.antibioticCode})${a.category ? ' · ' + a.category : ''}`"
                  :value="a.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="检测方法" width="180">
            <template #default="{ row }">
              <el-select v-model="row.defaultMethod" placeholder="继承模板" clearable style="width: 100%">
                <el-option label="MIC" value="MIC" />
                <el-option label="K-B纸片法" value="K-B纸片法" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="排序" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.sortNo" :min="0" :controls="false" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" :disabled="!!editForm.id && editForm.isSystem === 1" @click="removeItemRow($index)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="尚未添加抗生素，保存前请至少添加一条" :image-size="40" />
          </template>
        </el-table>
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
import {
  getAntibioticTemplatePage,
  getAntibioticTemplateDetail,
  saveAntibioticTemplate,
  deleteAntibioticTemplate,
  getAllBacteria,
  getAllAntibiotics,
  type AntibioticTemplateItem,
  type AntibioticTemplateSaveRequest,
  type Bacteria,
  type Antibiotic
} from '@/api/micro'

const categoryOptions = ['G+', 'G-', '非发酵菌', '苛养菌', '厌氧菌', '真菌']

const loading = ref(false)
const tableData = ref<any[]>([])
const searchForm = reactive({ keyword: '', category: '', status: undefined as number | undefined })

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<AntibioticTemplateSaveRequest & { isSystem?: number; items: AntibioticTemplateItem[] }>({
  id: 0,
  code: '',
  name: '',
  templateCategory: '',
  defaultMethod: '',
  description: '',
  status: 1,
  sortNo: 0,
  bacteriaIds: [],
  isSystem: 0,
  items: []
})
const formRules: FormRules = {
  code: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }]
}

const bacteriaOptions = ref<Bacteria[]>([])
const antibioticOptions = ref<Antibiotic[]>([])

async function loadDicts() {
  try {
    bacteriaOptions.value = (await getAllBacteria()) || []
  } catch {
    bacteriaOptions.value = []
  }
  try {
    antibioticOptions.value = (await getAllAntibiotics()) || []
  } catch {
    antibioticOptions.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.category) params.category = searchForm.category
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getAntibioticTemplatePage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}
function handleReset() {
  searchForm.keyword = ''
  searchForm.category = ''
  searchForm.status = undefined
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

function resetForm() {
  Object.assign(editForm, {
    id: 0,
    code: '',
    name: '',
    templateCategory: '',
    defaultMethod: '',
    description: '',
    status: 1,
    sortNo: 0,
    bacteriaIds: [],
    isSystem: 0,
    items: []
  })
}

function handleAdd() {
  resetForm()
  loadDicts()
  formDialogVisible.value = true
}

function addItemRow() {
  editForm.items!.push({ antibioticId: 0, defaultMethod: '', sortNo: editForm.items!.length, remark: '' })
}

function removeItemRow(index: number) {
  editForm.items!.splice(index, 1)
}

function onItemAntibioticChange(row: AntibioticTemplateItem, val: number) {
  const ab = antibioticOptions.value.find((a) => a.id === val)
  if (ab) {
    row.antibioticCode = ab.antibioticCode
    row.antibioticName = ab.antibioticName
    row.category = ab.category
  }
}

async function handleEdit(row: any) {
  resetForm()
  loadDicts()
  try {
    const detail = await getAntibioticTemplateDetail(row.id)
    if (detail) {
      Object.assign(editForm, {
        id: detail.id,
        code: detail.code,
        name: detail.name,
        templateCategory: detail.templateCategory ?? '',
        defaultMethod: detail.defaultMethod ?? '',
        description: detail.description ?? '',
        status: detail.status ?? 1,
        sortNo: detail.sortNo ?? 0,
        bacteriaIds: detail.bacteriaIds || [],
        isSystem: detail.isSystem ?? 0,
        items: (detail.items || []).map((it) => ({
          id: it.id,
          antibioticId: it.antibioticId,
          antibioticCode: it.antibioticCode,
          antibioticName: it.antibioticName,
          category: it.category,
          defaultMethod: it.defaultMethod ?? '',
          sortNo: it.sortNo ?? 0,
          remark: it.remark ?? ''
        }))
      })
    }
  } catch (e) {
    // ignore
  }
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!editForm.items || editForm.items.length === 0) {
      ElMessage.warning('请至少添加一条抗生素')
      return
    }
    if (editForm.items.some((it) => !it.antibioticId)) {
      ElMessage.warning('请为每条明细选择抗生素')
      return
    }
    formLoading.value = true
    try {
      await saveAntibioticTemplate({
        id: editForm.id,
        code: editForm.code,
        name: editForm.name,
        templateCategory: editForm.templateCategory,
        defaultMethod: editForm.defaultMethod,
        description: editForm.description,
        status: editForm.status,
        sortNo: editForm.sortNo,
        bacteriaIds: editForm.bacteriaIds,
        items: editForm.items.map((it) => ({
          antibioticId: it.antibioticId,
          defaultMethod: it.defaultMethod || '',
          sortNo: it.sortNo ?? 0,
          remark: it.remark ?? ''
        }))
      })
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

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除模板 "${row.name}" 吗?（仅客户自建模板可删除）`, '提示', { type: 'warning' })
    await deleteAntibioticTemplate(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
  loadDicts()
})
</script>

<style scoped lang="scss">
.antibiotic-template-page {
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

  :deep(.el-divider--horizontal) {
    margin: 4px 0 16px;
  }
}
</style>