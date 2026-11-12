<template>
  <div class="certificate-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="员工姓名">
          <el-input
            v-model="searchForm.staffName"
            placeholder="请输入员工姓名"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="证书名称">
          <el-input
            v-model="searchForm.certName"
            placeholder="请输入证书名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="有效" :value="1" />
            <el-option label="过期" :value="0" />
            <el-option label="作废" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增证书</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 即将到期预警提示 -->
    <el-alert
      v-if="expiringIds.length > 0"
      :title="`检测到 ${expiringIds.length} 条证书即将在 30 天内到期，请及时处理`"
      type="warning"
      show-icon
      :closable="false"
      class="expire-alert"
    />

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%" :row-class-name="rowClassName">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="员工" width="140">
          <template #default="{ row }">
            {{ row.staffName || (staffMap.get(row.staffId) && staffMap.get(row.staffId)!.name) || row.staffId }}
          </template>
        </el-table-column>
        <el-table-column prop="certType" label="资质类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.certType" size="small" type="info">{{ row.certType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="certName" label="证书名称" min-width="160" />
        <el-table-column prop="certNo" label="证书编号" width="150" />
        <el-table-column prop="issueOrg" label="颁发机构" width="160" />
        <el-table-column prop="issueDate" label="颁发日期" width="120" align="center" />
        <el-table-column prop="expiryDate" label="到期日期" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="isExpiringSoon(row.expiryDate)" type="danger" effect="dark" size="small">
              {{ row.expiryDate || '-' }} (即将到期)
            </el-tag>
            <span v-else :class="{ 'expiry-past': isExpired(row.expiryDate) }">
              {{ row.expiryDate || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleManageAttachments(row)">
              <el-icon><Document /></el-icon>
              <span>{{ row.attachmentCount || 0 }}</span>
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="certStatusType(row.status)" size="small">
              {{ certStatusLabel(row.status) }}
            </el-tag>
          </template>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑证书' : '新增证书'" width="640px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="员工" prop="staffId">
              <el-select
                v-model="editForm.staffId"
                filterable
                placeholder="请选择员工"
                style="width: 100%"
              >
                <el-option
                  v-for="s in staffOptions"
                  :key="s.id"
                  :label="`${s.name} (${s.staffNo})`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资质类型">
              <el-select v-model="editForm.certType" placeholder="请选择" style="width: 100%" clearable>
                <el-option v-for="t in certTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证书名称" prop="certName">
              <el-input v-model="editForm.certName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证书编号">
              <el-input v-model="editForm.certNo" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="颁发机构">
              <el-input v-model="editForm.issueOrg" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="有效" :value="1" />
                <el-option label="过期" :value="0" />
                <el-option label="作废" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="颁发日期">
              <el-date-picker
                v-model="editForm.issueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日期">
              <el-date-picker
                v-model="editForm.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警天数">
              <el-input-number v-model="editForm.alertDays" :min="1" :max="365" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 附件管理弹窗 -->
    <el-dialog v-model="attachmentDialogVisible" :title="`附件管理 - ${currentCert?.certName || ''}`" width="720px">
      <div class="attachment-toolbar">
        <el-upload
          :show-file-list="false"
          :before-upload="handleBeforeUpload"
          :http-request="handleUpload"
          accept=".pdf,.jpg,.jpeg,.png,.gif,.doc,.docx,.xls,.xlsx"
        >
          <el-button type="primary" :loading="uploading">
            <el-icon><Upload /></el-icon>
            上传附件
          </el-button>
        </el-upload>
        <span class="upload-tip">支持 PDF、图片、Word、Excel 文档，单个文件不超过 10MB</span>
      </div>

      <el-divider />

      <div v-if="attachments.length === 0" class="empty-attachment">
        <el-icon :size="48" color="#c0c4cc"><FolderOpened /></el-icon>
        <p>暂无附件，点击上方按钮上传</p>
      </div>

      <el-table v-else :data="attachments" border style="width: 100%">
        <el-table-column label="文件名" min-width="200">
          <template #default="{ row }">
            <div class="file-name">
              <el-icon :size="20" :color="getFileIconColor(row.fileType)">
                <component :is="getFileIcon(row.fileType)" />
              </el-icon>
              <span :title="row.fileName">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ (row.fileType || '').toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100" align="center">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="100" align="center" />
        <el-table-column label="上传时间" width="160" align="center">
          <template #default="{ row }">
            {{ formatDate(row.uploadTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handlePreviewAttachment(row)">预览</el-button>
            <el-button type="success" link size="small" @click="handleDownloadAttachment(row)">下载</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteAttachment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="attachmentDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览弹窗 -->
    <el-dialog v-model="previewVisible" title="文件预览" width="800px" top="5vh">
      <div class="preview-container" v-if="previewUrl">
        <img v-if="isImageFile(previewFile?.fileType)" :src="previewUrl" class="image-preview" />
        <iframe v-else-if="isPdfFile(previewFile?.fileType)" :src="previewUrl" class="pdf-preview" />
        <div v-else class="unsupported-preview">
          <el-icon :size="48" color="#909399"><Document /></el-icon>
          <p>该文件类型不支持在线预览，请下载后查看</p>
          <el-button type="primary" @click="handleDownloadAttachment(previewFile)">下载文件</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, markRaw } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import {
  getCertificatePage,
  saveCertificate,
  deleteCertificate,
  listExpiringCertificates,
  getAllActiveStaff,
  uploadCertificateAttachment,
  getCertificateAttachments,
  deleteCertificateAttachment,
  downloadCertificateAttachment,
  type StaffCertificate,
  type CertificateQuery,
  type Staff,
  type CertificateAttachment
} from '@/api/hr'
import { Document, Upload, FolderOpened, Picture, Reading, Grid, Download, Delete } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref<StaffCertificate[]>([])
const searchForm = reactive({
  staffName: '',
  certName: '',
  status: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const staffOptions = ref<Staff[]>([])
const staffMap = ref(new Map<number, Staff>())
const expiringIds = ref<number[]>([])
const certTypeOptions = ['PCR', 'HIV', '输血', '执业资格', '生物安全', '其他']
const editForm = reactive<StaffCertificate>({
  id: 0,
  staffId: 0,
  certType: '',
  certName: '',
  certNo: '',
  issueDate: '',
  expiryDate: '',
  issueOrg: '',
  alertDays: 30,
  status: 1
})
const formRules: FormRules = {
  staffId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  certName: [{ required: true, message: '请输入证书名称', trigger: 'blur' }]
}

// 附件相关状态
const attachmentDialogVisible = ref(false)
const currentCert = ref<StaffCertificate | null>(null)
const attachments = ref<CertificateAttachment[]>([])
const uploading = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewFile = ref<CertificateAttachment | null>(null)

// 判断证书是否临近过期(30天内)
function isExpiringSoon(expiryDate?: string): boolean {
  if (!expiryDate) return false
  const diff = dayjs(expiryDate).diff(dayjs(), 'day')
  return diff >= 0 && diff <= 30
}

// 判断证书是否已过期
function isExpired(expiryDate?: string): boolean {
  if (!expiryDate) return false
  return dayjs(expiryDate).isBefore(dayjs(), 'day')
}

function certStatusLabel(status?: number): string {
  if (status === 1) return '有效'
  if (status === 2) return '作废'
  return '过期'
}

function certStatusType(status?: number): 'success' | 'danger' | 'info' {
  if (status === 1) return 'success'
  if (status === 2) return 'info'
  return 'danger'
}

// 行高亮: 即将到期红色背景
function rowClassName({ row }: { row: StaffCertificate }): string {
  if (isExpiringSoon(row.expiryDate)) return 'row-expiring-soon'
  if (isExpired(row.expiryDate)) return 'row-expired'
  return ''
}

// 文件图标映射
function getFileIcon(fileType?: string) {
  if (!fileType) return markRaw(Document)
  const type = fileType.toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(type)) return markRaw(Picture)
  if (type === 'pdf') return markRaw(Reading)
  if (['doc', 'docx'].includes(type)) return markRaw(Document)
  if (['xls', 'xlsx'].includes(type)) return markRaw(Grid)
  return markRaw(Document)
}

function getFileIconColor(fileType?: string): string {
  if (!fileType) return '#909399'
  const type = fileType.toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif'].includes(type)) return '#409eff'
  if (type === 'pdf') return '#f56c6c'
  if (['doc', 'docx'].includes(type)) return '#409eff'
  if (['xls', 'xlsx'].includes(type)) return '#67c23a'
  return '#909399'
}

function isImageFile(fileType?: string): boolean {
  if (!fileType) return false
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(fileType.toLowerCase())
}

function isPdfFile(fileType?: string): boolean {
  if (!fileType) return false
  return fileType.toLowerCase() === 'pdf'
}

function formatFileSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  return dayjs(dateStr).format('YYYY-MM-DD HH:mm')
}

// 拉取列表
async function fetchList() {
  loading.value = true
  try {
    const params: CertificateQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.staffName) params.staffName = searchForm.staffName
    if (searchForm.certName) params.certName = searchForm.certName
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getCertificatePage(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
    // 并行拉取预警证书列表
    loadExpiring()
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function loadExpiring() {
  try {
    const list = await listExpiringCertificates(30)
    expiringIds.value = (list || []).map((c) => c.id)
  } catch (e) {
    expiringIds.value = []
  }
}

async function loadStaffOptions() {
  try {
    staffOptions.value = await getAllActiveStaff()
    staffMap.value = new Map(staffOptions.value.map((s) => [s.id, s]))
  } catch (e) {
    staffOptions.value = []
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.staffName = ''
  searchForm.certName = ''
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

function handleAdd() {
  Object.assign(editForm, {
    id: 0,
    staffId: 0,
    certType: '',
    certName: '',
    certNo: '',
    issueDate: '',
    expiryDate: '',
    issueOrg: '',
    alertDays: 30,
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: StaffCertificate) {
  Object.assign(editForm, {
    id: row.id,
    staffId: row.staffId,
    certType: row.certType ?? '',
    certName: row.certName,
    certNo: row.certNo ?? '',
    issueDate: row.issueDate ?? '',
    expiryDate: row.expiryDate ?? '',
    issueOrg: row.issueOrg ?? '',
    alertDays: row.alertDays ?? 30,
    status: row.status ?? 1
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveCertificate({ ...editForm })
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

async function handleDelete(row: StaffCertificate) {
  try {
    await ElMessageBox.confirm(`确认删除证书 "${row.certName}" 吗?`, '提示', { type: 'warning' })
    await deleteCertificate(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

// 附件管理相关函数
async function handleManageAttachments(row: StaffCertificate) {
  currentCert.value = row
  attachmentDialogVisible.value = true
  await loadAttachments(row.id)
}

async function loadAttachments(certificateId: number) {
  try {
    attachments.value = await getCertificateAttachments(certificateId)
  } catch (e) {
    attachments.value = []
  }
}

function handleBeforeUpload(file: File): boolean {
  // 验证文件类型
  const allowedTypes = ['pdf', 'jpg', 'jpeg', 'png', 'gif', 'doc', 'docx', 'xls', 'xlsx']
  const fileType = file.name.split('.').pop()?.toLowerCase() || ''
  
  if (!allowedTypes.includes(fileType)) {
    ElMessage.error('不支持的文件类型，仅支持 PDF、图片、Word、Excel 文档')
    return false
  }
  
  // 验证文件大小
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  
  return true
}

async function handleUpload(options: any) {
  if (!currentCert.value) return
  
  uploading.value = true
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    await uploadCertificateAttachment(
      currentCert.value.id,
      options.file,
      userInfo.id,
      userInfo.realName || userInfo.username
    )
    ElMessage.success('上传成功')
    await loadAttachments(currentCert.value.id)
    // 更新证书附件数量
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handlePreviewAttachment(attachment: CertificateAttachment) {
  try {
    previewFile.value = attachment
    const res = await downloadCertificateAttachment(attachment.id)
    const blob = new Blob([res], { type: attachment.contentType || 'application/octet-stream' })
    previewUrl.value = URL.createObjectURL(blob)
    previewVisible.value = true
  } catch (e) {
    ElMessage.error('预览失败')
  }
}

async function handleDownloadAttachment(attachment: CertificateAttachment) {
  try {
    const res = await downloadCertificateAttachment(attachment.id)
    const blob = new Blob([res], { type: attachment.contentType || 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = attachment.fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

async function handleDeleteAttachment(attachment: CertificateAttachment) {
  try {
    await ElMessageBox.confirm(`确认删除附件 "${attachment.fileName}" 吗?`, '提示', { type: 'warning' })
    await deleteCertificateAttachment(attachment.id)
    ElMessage.success('删除成功')
    if (currentCert.value) {
      await loadAttachments(currentCert.value.id)
      // 更新证书附件数量
      await fetchList()
    }
  } catch (e) {
    // 取消或失败
  }
}

// 清理预览URL
function cleanupPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

onMounted(() => {
  loadStaffOptions()
  fetchList()
})
</script>

<style scoped lang="scss">
.certificate-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .expire-alert {
    margin-bottom: 12px;
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }

    // 即将到期行: 红色高亮
    :deep(.row-expiring-soon) {
      background-color: #fef0f0 !important;
    }

    // 已过期行: 浅灰红
    :deep(.row-expired) {
      background-color: #fde2e2 !important;
    }
  }

  // 已过期文字标红
  .expiry-past {
    color: #f56c6c;
    font-weight: 600;
  }

  // 临近过期高亮 (兼容旧样式)
  .expiry-warn {
    color: #e6a23c;
    font-weight: 600;
  }
}

// 附件管理相关样式
.attachment-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;

  .upload-tip {
    color: #909399;
    font-size: 12px;
  }
}

.empty-attachment {
  text-align: center;
  padding: 40px 0;
  color: #909399;

  p {
    margin-top: 12px;
  }
}

.file-name {
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.preview-container {
  text-align: center;
  min-height: 400px;

  .image-preview {
    max-width: 100%;
    max-height: 600px;
  }

  .pdf-preview {
    width: 100%;
    height: 600px;
    border: none;
  }

  .unsupported-preview {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px;

    p {
      margin: 16px 0;
      color: #606266;
    }
  }
}
</style>
