<template>
  <div class="document-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="文档名称/编号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="searchForm.docType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in docTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属小组">
          <el-select v-model="searchForm.groupId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增文档</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="docCode" label="文档编号" width="140" align="center">
          <template #default="{ row }">{{ row.docCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="docName" label="文档名称" min-width="200" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="docTypeTagType(row.docType)">
              {{ docTypeLabel(row.docType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" align="center">
          <template #default="{ row }">{{ row.version || '-' }}</template>
        </el-table-column>
        <el-table-column label="所属小组" width="140" align="center">
          <template #default="{ row }">
            {{ row.groupName || groupNameById(row.groupId) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="上传时间" width="160" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="docStatusType(row.status)" size="small">
              {{ docStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :disabled="!row.fileUrl" @click="handlePreview(row)">预览</el-button>
            <el-button type="primary" link size="small" :disabled="!row.fileUrl" @click="handleDownload(row)">下载</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" type="warning" link size="small" @click="handleAbolish(row)">作废</el-button>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑文档' : '新增文档'" width="640px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="文档编号">
              <el-input v-model="editForm.docCode" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文档名称" prop="docName">
              <el-input v-model="editForm.docName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文档类型">
              <el-select v-model="editForm.docType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in docTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="editForm.version" placeholder="如 V1.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属小组">
              <el-select v-model="editForm.groupId" placeholder="请选择" clearable filterable style="width: 100%">
                <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="现行" :value="1" />
                <el-option label="草稿" :value="2" />
                <el-option label="作废" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="文档文件">
              <div class="upload-area">
                <input ref="fileInputRef" type="file" style="display: none" @change="onFileChange" />
                <el-button type="primary" plain @click="pickFile">
                  {{ editForm.fileUrl ? '重新选择文件' : '选择文件' }}
                </el-button>
                <span v-if="selectedFileName" class="file-name">{{ selectedFileName }}</span>
                <el-button v-if="editForm.fileUrl" link type="danger" @click="clearFile">清除</el-button>
              </div>
              <div class="upload-tip">支持 PDF/Word/Excel/图片, 单文件不超过 10MB (以 Base64 存储)</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="文档预览" width="80%" top="5vh">
      <div class="preview-wrapper">
        <img v-if="previewIsImage" :src="previewUrl" alt="预览" class="preview-img" />
        <iframe v-else :src="previewUrl" class="preview-iframe" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getDocumentPage,
  saveDocument,
  abolishDocument,
  deleteDocument,
  getGroupList,
  type LabDocument,
  type LabGroup,
  type DocumentQuery
} from '@/api/hr'

const docTypeOptions = [
  { value: 'SOP', label: 'SOP标准操作规程' },
  { value: 'MANUAL', label: '手册' },
  { value: 'PROCEDURE', label: '程序文件' },
  { value: 'FORM', label: '表单' }
]

const docTypeMap: Record<string, { label: string; type: string }> = {
  SOP: { label: 'SOP', type: 'primary' },
  MANUAL: { label: '手册', type: 'success' },
  PROCEDURE: { label: '程序文件', type: 'warning' },
  FORM: { label: '表单', type: 'info' }
}

function docTypeLabel(code?: string): string {
  return (code && docTypeMap[code]?.label) || code || '-'
}
function docTypeTagType(code?: string): any {
  return (code && docTypeMap[code]?.type) || 'info'
}
function docStatusLabel(status?: number): string {
  if (status === 1) return '现行'
  if (status === 2) return '草稿'
  return '作废'
}
function docStatusType(status?: number): 'success' | 'warning' | 'info' {
  if (status === 1) return 'success'
  if (status === 2) return 'warning'
  return 'info'
}

const loading = ref(false)
const tableData = ref<LabDocument[]>([])
const groupOptions = ref<LabGroup[]>([])
const searchForm = reactive({
  keyword: '',
  docType: '',
  groupId: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const fileInputRef = ref<HTMLInputElement>()
const selectedFileName = ref('')
const editForm = reactive<LabDocument>({
  id: 0,
  docCode: '',
  docName: '',
  docType: '',
  version: '',
  groupId: undefined,
  fileUrl: '',
  status: 1
})
const formRules: FormRules = {
  docName: [{ required: true, message: '请输入文档名称', trigger: 'blur' }]
}

const previewVisible = ref(false)
const previewUrl = ref('')
const previewIsImage = computed(() => previewUrl.value.startsWith('data:image/'))

function groupNameById(id?: number): string {
  if (!id) return ''
  return groupOptions.value.find((g) => g.id === id)?.groupName || ''
}

async function fetchList() {
  loading.value = true
  try {
    const params: DocumentQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.docType) params.docType = searchForm.docType
    if (searchForm.groupId !== undefined) params.groupId = searchForm.groupId
    const res = await getDocumentPage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  try {
    groupOptions.value = (await getGroupList()) || []
  } catch (e) {
    groupOptions.value = []
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.docType = ''
  searchForm.groupId = undefined
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

function pickFile() {
  fileInputRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 10MB')
    input.value = ''
    return
  }
  selectedFileName.value = file.name
  const reader = new FileReader()
  reader.onload = () => {
    editForm.fileUrl = reader.result as string
  }
  reader.readAsDataURL(file)
  input.value = ''
}

function clearFile() {
  editForm.fileUrl = ''
  selectedFileName.value = ''
}

function handleAdd() {
  Object.assign(editForm, {
    id: 0,
    docCode: '',
    docName: '',
    docType: '',
    version: '',
    groupId: undefined,
    fileUrl: '',
    status: 1
  })
  selectedFileName.value = ''
  formDialogVisible.value = true
}

function handleEdit(row: LabDocument) {
  Object.assign(editForm, {
    id: row.id,
    docCode: row.docCode ?? '',
    docName: row.docName ?? '',
    docType: row.docType ?? '',
    version: row.version ?? '',
    groupId: row.groupId || undefined,
    fileUrl: row.fileUrl ?? '',
    status: row.status ?? 1
  })
  selectedFileName.value = row.fileUrl ? '已上传文件' : ''
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveDocument({ ...editForm })
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

async function handleAbolish(row: LabDocument) {
  try {
    await ElMessageBox.confirm(`确认作废文档 "${row.docName}" 吗?`, '提示', { type: 'warning' })
    await abolishDocument(row.id)
    ElMessage.success('已作废')
    fetchList()
  } catch (e) {
    // ignore
  }
}

async function handleDelete(row: LabDocument) {
  try {
    await ElMessageBox.confirm(`确认删除文档 "${row.docName}" 吗?`, '提示', { type: 'warning' })
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // ignore
  }
}

function handlePreview(row: LabDocument) {
  if (!row.fileUrl) return
  previewUrl.value = row.fileUrl
  previewVisible.value = true
}

function handleDownload(row: LabDocument) {
  if (!row.fileUrl) return
  const isDataUrl = row.fileUrl.startsWith('data:')
  if (isDataUrl) {
    const link = document.createElement('a')
    link.href = row.fileUrl
    link.download = row.docName || 'document'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } else {
    window.open(row.fileUrl, '_blank')
  }
}

onMounted(() => {
  loadGroups()
  fetchList()
})
</script>

<style scoped lang="scss">
.document-page {
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

  .upload-area {
    display: flex;
    align-items: center;
    gap: 12px;

    .file-name {
      color: var(--el-text-color-secondary);
      font-size: 13px;
    }
  }

  .upload-tip {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  .preview-wrapper {
    .preview-img {
      max-width: 100%;
      display: block;
      margin: 0 auto;
    }

    .preview-iframe {
      width: 100%;
      height: 70vh;
      border: none;
    }
  }
}
</style>
