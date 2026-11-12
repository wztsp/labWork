<template>
  <div class="staff-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="姓名/工号/电话"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="所属小组">
          <el-select v-model="searchForm.groupId" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在职" :value="1" />
            <el-option label="离职" :value="0" />
            <el-option label="试用期" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增员工</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="staffNo" label="工号" width="120" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column label="所属小组" min-width="140">
          <template #default="{ row }">
            {{ groupNameOf(row.groupId) }}
          </template>
        </el-table-column>
        <el-table-column prop="education" label="学历" width="100" />
        <el-table-column prop="major" label="专业" width="140" />
        <el-table-column prop="entryDate" label="入职日期" width="120" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑员工' : '新增员工'" width="760px" @open="onDialogOpen">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" prop="staffNo">
              <el-input v-model="editForm.staffNo" :disabled="!!editForm.id" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="editForm.name" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="editForm.phone" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="editForm.email" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证号">
              <el-input v-model="editForm.idCard" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属小组">
              <el-select v-model="editForm.groupId" placeholder="请选择" filterable clearable style="width: 100%">
                <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学历">
              <el-select v-model="editForm.education" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="e in educationOptions" :key="e" :label="e" :value="e" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专业">
              <el-input v-model="editForm.major" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker
                v-model="editForm.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="在职" :value="1" />
                <el-option label="离职" :value="0" />
                <el-option label="试用期" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 电子签名区域 -->
        <el-divider content-position="left">电子签名</el-divider>
        <el-form-item label="电子签名">
          <div class="signature-area">
            <div class="signature-preview">
              <img v-if="editForm.signatureImage" :src="editForm.signatureImage" alt="签名" class="signature-img" />
              <span v-else class="signature-placeholder">暂无签名</span>
            </div>
            <div class="signature-actions">
              <el-radio-group v-model="signatureMode" size="small">
                <el-radio-button label="upload">上传图片</el-radio-button>
                <el-radio-button label="canvas">手写签名</el-radio-button>
              </el-radio-group>
              <div class="signature-tools">
                <upload-btn v-if="signatureMode === 'upload'" :on-change="onSignatureUpload" />
                <template v-else>
                  <el-button size="small" @click="openCanvasDialog">打开手写板</el-button>
                  <el-button size="small" type="danger" plain @click="clearSignature">清除</el-button>
                </template>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 手写签名对话框 -->
    <el-dialog v-model="canvasDialogVisible" title="手写签名" width="640px" @opened="initCanvas">
      <div class="canvas-wrapper">
        <canvas ref="canvasRef" width="560" height="240" class="signature-canvas" />
      </div>
      <template #footer>
        <el-button @click="clearCanvas">清空</el-button>
        <el-button @click="canvasDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCanvas">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, ElButton } from 'element-plus'
import {
  getStaffPage,
  saveStaff,
  deleteStaff,
  getGroupList,
  type Staff,
  type StaffQuery,
  type LabGroup
} from '@/api/hr'

// 上传按钮 (内联组件, 避免新增文件)
const UploadBtn = defineComponent({
  props: { onChange: { type: Function as PropType<(file: File) => void>, required: true } },
  setup(props) {
    const inputRef = ref<HTMLInputElement | null>(null)
    function pick() { inputRef.value?.click() }
    function onInput(e: Event) {
      const target = e.target as HTMLInputElement
      const file = target.files?.[0]
      if (file) props.onChange(file)
      target.value = ''
    }
    return () => h('div', { style: { display: 'inline-block' } }, [
      h(ElButton, { size: 'small', onClick: pick }, () => '选择图片'),
      h('input', {
        ref: inputRef,
        type: 'file',
        accept: 'image/png,image/jpeg',
        style: { display: 'none' },
        onChange: onInput
      })
    ])
  }
})
import { defineComponent, type PropType } from 'vue'

const loading = ref(false)
const tableData = ref<Staff[]>([])
const groupOptions = ref<LabGroup[]>([])
const groupMap = ref(new Map<number, LabGroup>())
const searchForm = reactive({
  keyword: '',
  groupId: undefined as number | undefined,
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
const educationOptions = ['博士', '硕士', '本科', '大专', '中专', '高中', '其他']
const editForm = reactive<Staff>({
  id: 0,
  staffNo: '',
  name: '',
  phone: '',
  email: '',
  groupId: undefined,
  education: '',
  major: '',
  entryDate: '',
  idCard: '',
  signatureImage: '',
  status: 1
})
const formRules: FormRules = {
  staffNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

// ===== 电子签名 =====
const signatureMode = ref<'upload' | 'canvas'>('upload')
const canvasDialogVisible = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let canvasCtx: CanvasRenderingContext2D | null = null
let drawing = false
let lastPoint: { x: number; y: number } | null = null

function onSignatureUpload(file: File) {
  if (!/image\/(png|jpe?g)/.test(file.type)) {
    ElMessage.warning('仅支持 PNG/JPG 图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    editForm.signatureImage = reader.result as string
    ElMessage.success('签名图片已加载, 保存后生效')
  }
  reader.readAsDataURL(file)
}

function openCanvasDialog() {
  canvasDialogVisible.value = true
}

function initCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  canvasCtx = canvas.getContext('2d')
  if (canvasCtx) {
    canvasCtx.fillStyle = '#fff'
    canvasCtx.fillRect(0, 0, canvas.width, canvas.height)
    canvasCtx.strokeStyle = '#000'
    canvasCtx.lineWidth = 2
    canvasCtx.lineCap = 'round'
    canvasCtx.lineJoin = 'round'
  }
  canvas.onmousedown = (e) => startDraw(getPos(e, canvas))
  canvas.onmousemove = (e) => moveDraw(getPos(e, canvas))
  window.onmouseup = () => endDraw()
  // 触摸支持
  canvas.ontouchstart = (e) => {
    e.preventDefault()
    const t = e.touches[0]
    startDraw(getPos(t, canvas))
  }
  canvas.ontouchmove = (e) => {
    e.preventDefault()
    const t = e.touches[0]
    moveDraw(getPos(t, canvas))
  }
  canvas.ontouchend = (e) => { e.preventDefault(); endDraw() }
}

function getPos(evt: MouseEvent | Touch, canvas: HTMLCanvasElement) {
  const rect = canvas.getBoundingClientRect()
  return {
    x: (evt.clientX - rect.left) * (canvas.width / rect.width),
    y: (evt.clientY - rect.top) * (canvas.height / rect.height)
  }
}

function startDraw(p: { x: number; y: number }) {
  drawing = true
  lastPoint = p
}

function moveDraw(p: { x: number; y: number }) {
  if (!drawing || !canvasCtx || !lastPoint) return
  canvasCtx.beginPath()
  canvasCtx.moveTo(lastPoint.x, lastPoint.y)
  canvasCtx.lineTo(p.x, p.y)
  canvasCtx.stroke()
  lastPoint = p
}

function endDraw() {
  drawing = false
  lastPoint = null
}

function clearCanvas() {
  const canvas = canvasRef.value
  if (canvas && canvasCtx) {
    canvasCtx.fillStyle = '#fff'
    canvasCtx.fillRect(0, 0, canvas.width, canvas.height)
  }
}

function confirmCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  // 校验是否为空白画布
  editForm.signatureImage = canvas.toDataURL('image/png')
  canvasDialogVisible.value = false
  ElMessage.success('手写签名已生成, 保存后生效')
}

function clearSignature() {
  editForm.signatureImage = ''
}

function onDialogOpen() {
  // 弹窗打开时重置画布模式
  signatureMode.value = editForm.signatureImage ? 'upload' : 'upload'
}

// ===== 字典/辅助 =====
function groupNameOf(groupId?: number): string {
  if (groupId === undefined || groupId === null) return '-'
  return groupMap.value.get(groupId)?.groupName ?? '-'
}

function statusLabel(status?: number): string {
  if (status === 1) return '在职'
  if (status === 2) return '试用期'
  return '离职'
}

function statusTagType(status?: number): 'success' | 'info' | 'warning' {
  if (status === 1) return 'success'
  if (status === 2) return 'warning'
  return 'info'
}

// ===== 列表 =====
async function fetchList() {
  loading.value = true
  try {
    const params: StaffQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.groupId !== undefined) params.groupId = searchForm.groupId
    if (searchForm.status !== undefined) params.status = searchForm.status
    const res = await getStaffPage(params)
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
    const list = await getGroupList()
    groupOptions.value = list || []
    groupMap.value = new Map(groupOptions.value.map((g) => [g.id, g]))
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
  searchForm.groupId = undefined
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
    staffNo: '',
    name: '',
    phone: '',
    email: '',
    groupId: undefined,
    education: '',
    major: '',
    entryDate: '',
    idCard: '',
    signatureImage: '',
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: Staff) {
  Object.assign(editForm, {
    id: row.id,
    staffNo: row.staffNo ?? '',
    name: row.name ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    groupId: row.groupId,
    education: row.education ?? '',
    major: row.major ?? '',
    entryDate: row.entryDate ?? '',
    idCard: row.idCard ?? '',
    signatureImage: row.signatureImage ?? '',
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
      await saveStaff({ ...editForm })
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

async function handleDelete(row: Staff) {
  try {
    await ElMessageBox.confirm(`确认删除员工 "${row.name}" 吗?`, '提示', { type: 'warning' })
    await deleteStaff(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  loadGroups()
  fetchList()
})
</script>

<style scoped lang="scss">
.staff-page {
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
}

.signature-area {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  width: 100%;
}

.signature-preview {
  width: 220px;
  height: 110px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  flex-shrink: 0;

  .signature-img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }

  .signature-placeholder {
    color: #c0c4cc;
    font-size: 13px;
  }
}

.signature-actions {
  flex: 1;

  .signature-tools {
    margin-top: 10px;
    display: flex;
    gap: 8px;
  }
}

.canvas-wrapper {
  display: flex;
  justify-content: center;

  .signature-canvas {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    background: #fff;
    cursor: crosshair;
    touch-action: none;
  }
}
</style>
