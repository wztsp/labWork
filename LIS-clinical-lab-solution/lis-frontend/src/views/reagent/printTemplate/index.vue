<template>
  <div class="pt-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="业务类型">
          <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 170px" @change="handleSearch">
            <el-option v-for="b in bizTypes" :key="b.code" :label="b.name" :value="b.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px" @change="handleSearch">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="模板编码/名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="handleCreate">新建模板</el-button>
        <span class="toolbar-tip">模板内容支持拖拽设计，打印时按业务类型取默认模板</span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="templateCode" label="模板编码" width="190" show-overflow-tooltip />
        <el-table-column prop="templateName" label="模板名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="业务类型" width="140">
          <template #default="{ row }">{{ bizName(row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="纸张(mm)" width="120" align="center">
          <template #default="{ row }">{{ paperText(row) }}</template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'primary' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="165" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">设计</el-button>
            <el-button type="primary" link size="small" :disabled="row.isDefault === 1 || row.status !== 1" @click="handleSetDefault(row)">
              设为默认
            </el-button>
            <el-button type="primary" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button type="primary" link size="small" :disabled="row.isDefault === 1" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" :disabled="row.isDefault === 1" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <PrintTemplateDesigner
      v-model:visible="designerVisible"
      :template="editingTemplate"
      :biz-type="defaultBizType"
      @save="handleSave"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import PrintTemplateDesigner from '@/components/PrintTemplateDesigner.vue'
import {
  createPrintTemplate,
  deletePrintTemplate,
  getPrintBizTypes,
  getPrintTemplatePage,
  setDefaultPrintTemplate,
  updatePrintTemplate,
  type PrintBizType,
  type PrintTemplate
} from '@/api/reagent/printTemplate'

const loading = ref(false)
const bizTypes = ref<PrintBizType[]>([])
const tableData = ref<PrintTemplate[]>([])
const designerVisible = ref(false)
const editingTemplate = ref<PrintTemplate | null>(null)
const defaultBizType = ref('REAGENT_LABEL')

const query = reactive({
  bizType: '',
  status: undefined as number | undefined,
  keyword: ''
})

const pagination = reactive({ current: 1, size: 10, total: 0 })

function bizName(code?: string) {
  if (!code) return '-'
  return bizTypes.value.find(b => b.code === code)?.name || code
}

function paperText(row: PrintTemplate) {
  return `${Number(row.paperWidth)}×${Number(row.paperHeight)}`
}

async function fetchBizTypes() {
  try {
    bizTypes.value = await getPrintBizTypes()
  } catch {
    bizTypes.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getPrintTemplatePage({
      current: pagination.current,
      size: pagination.size,
      bizType: query.bizType || undefined,
      status: query.status,
      keyword: query.keyword || undefined
    })
    tableData.value = (res.records || []).map(item => ({
      ...item,
      updateTime: item.updateTime ? dayjs(item.updateTime).format('YYYY-MM-DD HH:mm:ss') : ''
    }))
    pagination.total = res.total || 0
  } catch {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchList()
}

function handleReset() {
  query.bizType = ''
  query.status = undefined
  query.keyword = ''
  handleSearch()
}

function handleCreate() {
  editingTemplate.value = null
  defaultBizType.value = query.bizType || 'REAGENT_LABEL'
  designerVisible.value = true
}

function handleEdit(row: PrintTemplate) {
  editingTemplate.value = { ...row }
  defaultBizType.value = row.bizType
  designerVisible.value = true
}

function handleCopy(row: PrintTemplate) {
  editingTemplate.value = {
    ...row,
    id: undefined,
    templateCode: `${row.templateCode}_COPY_${Math.floor(Math.random() * 900 + 100)}`,
    templateName: `${row.templateName}（副本）`,
    isDefault: 0,
    status: 1
  }
  defaultBizType.value = row.bizType
  designerVisible.value = true
}

async function handleSave(payload: PrintTemplate) {
  try {
    if (payload.id) {
      await updatePrintTemplate(payload)
    } else {
      await createPrintTemplate(payload)
    }
    ElMessage.success('保存成功')
    designerVisible.value = false
    fetchList()
  } catch {
    // 请求层已提示错误
  }
}

async function handleSetDefault(row: PrintTemplate) {
  try {
    await setDefaultPrintTemplate(row.id as number)
    ElMessage.success('已设为默认模板')
    fetchList()
  } catch {
    // 请求层已提示错误
  }
}

async function handleToggleStatus(row: PrintTemplate) {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updatePrintTemplate({ ...row, status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '已启用' : '已停用')
    fetchList()
  } catch {
    // 请求层已提示错误
  }
}

async function handleDelete(row: PrintTemplate) {
  try {
    await ElMessageBox.confirm(`确认删除模板「${row.templateName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePrintTemplate(row.id as number)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 请求层已提示错误
  }
}

onMounted(() => {
  fetchBizTypes()
  fetchList()
})
</script>

<style scoped>
.pt-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.toolbar-tip {
  font-size: 12px;
  color: #909399;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
