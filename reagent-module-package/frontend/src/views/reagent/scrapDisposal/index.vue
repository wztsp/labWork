<template>
  <div class="scrap-disposal-page">
    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-tag type="warning" effect="plain">危险品报废环保处置台账</el-tag>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="handleAdd">新增处置登记</el-button>
          <el-button type="success" plain @click="handleExport">导出台账</el-button>
          <el-button type="primary" plain @click="fetchData">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 台账列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reqNo" label="报废单号" width="140" />
        <el-table-column label="危险品" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hazardFlag === 1 ? 'danger' : 'info'" size="small">
              {{ row.hazardFlag === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hazardClass" label="危险类别" width="120" />
        <el-table-column prop="disposalMethod" label="处置方式" min-width="120" />
        <el-table-column prop="disposalOrg" label="处置单位" min-width="160" />
        <el-table-column prop="disposalCert" label="凭证号" width="140" />
        <el-table-column prop="disposalDate" label="处置日期" width="120" align="center" />
        <el-table-column prop="safetyApproverName" label="安全员" width="100" align="center" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createTime" label="登记时间" width="170" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
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

    <!-- 新增/编辑处置登记弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑处置登记' : '新增处置登记'" width="600px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-form-item label="报废单" prop="reqId">
          <el-select v-model="editForm.reqId" filterable placeholder="请选择报废单" style="width: 100%">
            <el-option
              v-for="r in scrapReqs"
              :key="r.id!"
              :label="`${r.reqNo}（${hazardText(r.hazardFlag)}）`"
              :value="r.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="危险品">
          <el-radio-group v-model="editForm.hazardFlag">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="危险类别">
          <el-select v-model="editForm.hazardClass" clearable placeholder="易燃/易爆/剧毒/腐蚀/放射性" style="width: 100%">
            <el-option v-for="c in ['易燃', '易爆', '剧毒', '腐蚀', '放射性']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置方式">
          <el-select v-model="editForm.disposalMethod" clearable placeholder="请选择处置方式" style="width: 100%">
            <el-option v-for="m in ['焚烧', '中和', '委托回收', '填埋']" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置单位">
          <el-input v-model="editForm.disposalOrg" placeholder="请输入处置单位" />
        </el-form-item>
        <el-form-item label="处置凭证号">
          <el-input v-model="editForm.disposalCert" placeholder="请输入凭证号" />
        </el-form-item>
        <el-form-item label="处置日期">
          <el-date-picker v-model="editForm.disposalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="安全员">
          <el-input v-model="editForm.safetyApproverName" placeholder="请输入安全员姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getScrapDisposalPage,
  saveScrapDisposal,
  deleteScrapDisposal,
  getRequisitionPage,
  type ReagentScrapDisposal,
  type ReagentRequisition
} from '@/api/reagent'

function hazardText(flag?: number): string {
  return flag === 1 ? '危险品' : '普通'
}

const loading = ref(false)
const tableData = ref<ReagentScrapDisposal[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

// 可登记处置的报废单（reqType=3）
const scrapReqs = ref<ReagentRequisition[]>([])
async function loadScrapReqs() {
  try {
    const res = await getRequisitionPage({ pageNum: 1, pageSize: 100, reqType: 3 })
    scrapReqs.value = res.records || []
  } catch (e) {
    scrapReqs.value = []
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getScrapDisposalPage({ pageNum: pagination.pageNum, pageSize: pagination.pageSize })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载处置台账失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  // 通过 fetch 下载 Excel（复用后端导出端点）
  try {
    const { useUserStore } = await import('@/stores/user')
    const token = useUserStore().token
    const url = '/api/reagent/scrap-disposal/export'
    const resp = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    if (!resp.ok) throw new Error('导出失败')
    const blob = await resp.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'scrap_disposal_ledger.xls'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

function handleSizeChange() {
  pagination.pageNum = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

// ===== 新增/编辑 =====
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = (): ReagentScrapDisposal => ({
  reqId: undefined,
  hazardFlag: 0,
  hazardClass: '',
  disposalMethod: '',
  disposalOrg: '',
  disposalCert: '',
  disposalDate: '',
  safetyApproverName: '',
  remark: ''
})
const editForm = reactive<ReagentScrapDisposal>(emptyForm())
const formRules: FormRules = {
  reqId: [{ required: true, message: '请选择报废单', trigger: 'change' }]
}

function handleAdd() {
  Object.assign(editForm, emptyForm(), { id: undefined })
  formDialogVisible.value = true
}

function handleEdit(row: ReagentScrapDisposal) {
  Object.assign(editForm, {
    id: row.id,
    reqId: row.reqId,
    hazardFlag: row.hazardFlag ?? 0,
    hazardClass: row.hazardClass ?? '',
    disposalMethod: row.disposalMethod ?? '',
    disposalOrg: row.disposalOrg ?? '',
    disposalCert: row.disposalCert ?? '',
    disposalDate: row.disposalDate ? String(row.disposalDate).slice(0, 10) : '',
    safetyApproverName: row.safetyApproverName ?? '',
    remark: row.remark ?? ''
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveScrapDisposal({ ...editForm })
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchData()
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: ReagentScrapDisposal) {
  try {
    await ElMessageBox.confirm('确认删除该处置登记吗？', '提示', { type: 'warning' })
    await deleteScrapDisposal(row.id!)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchData()
  loadScrapReqs()
})
</script>

<style scoped lang="scss">
.toolbar-card {
  margin-bottom: 16px;

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}

.table-card .pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>