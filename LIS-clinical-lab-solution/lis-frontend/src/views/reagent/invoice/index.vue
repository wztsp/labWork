<template>
  <div class="reagent-invoice-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="发票号">
          <el-input v-model="query.invoiceNo" placeholder="请输入发票号" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="全部供应商" clearable filterable style="width: 220px">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" plain @click="openCreate">登记发票</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="invoiceNo" label="发票号" min-width="160" />
        <el-table-column prop="supplierName" label="供应商" min-width="200">
          <template #default="{ row }">{{ row.supplierName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="invoiceDate" label="开票日期" width="120" align="center">
          <template #default="{ row }">{{ row.invoiceDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="价税合计" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column label="关联订单" min-width="150">
          <template #default="{ row }">{{ row.orderNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 0 ? 'info' : 'success'">
              {{ row.status === 0 ? '已作废' : '已登记' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="登记人" width="110" align="center">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="登记时间" width="165" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 0" type="primary" link size="small" @click="openEdit(row)">修改</el-button>
            <el-button v-if="row.status !== 0" type="danger" link size="small" @click="handleCancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '修改发票' : '登记发票'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="发票号" prop="invoiceNo">
          <el-input v-model="form.invoiceNo" placeholder="请输入发票号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票日期" prop="invoiceDate">
          <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="价税合计" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联订单ID">
          <el-input-number v-model="form.orderId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import {
  getInvoicePage,
  registerInvoice,
  updateInvoice,
  cancelInvoice,
  getSelectableSuppliers,
  type InvoiceQuery,
  type ReagentInvoice,
  type Supplier
} from '@/api/reagent'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const list = ref<ReagentInvoice[]>([])
const total = ref(0)
const supplierOptions = ref<Supplier[]>([])
const dateRange = ref<[string, string] | null>(null)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const query = reactive<InvoiceQuery>({ pageNum: 1, pageSize: 10 })
const form = reactive<ReagentInvoice>({ invoiceNo: '', supplierId: undefined, invoiceDate: '', amount: undefined, taxAmount: undefined })

const rules: FormRules = {
  invoiceNo: [{ required: true, message: '请输入发票号', trigger: 'blur' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入价税合计', trigger: 'blur' }]
}

function formatAmount(value?: number) {
  if (value === undefined || value === null) return '0.00'
  return Number(value).toFixed(2)
}
function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function loadSuppliers() {
  try {
    supplierOptions.value = await getSelectableSuppliers()
  } catch (e) {
    supplierOptions.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: InvoiceQuery = { ...query }
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const page = await getInvoicePage(params)
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.invoiceNo = ''
  query.supplierId = undefined
  query.pageNum = 1
  query.pageSize = 10
  dateRange.value = null
  fetchList()
}

function resetForm() {
  form.id = undefined
  form.invoiceNo = ''
  form.supplierId = undefined
  form.invoiceDate = dayjs().format('YYYY-MM-DD')
  form.amount = undefined
  form.taxAmount = undefined
  form.orderId = undefined
  form.remark = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ReagentInvoice) {
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: ReagentInvoice = {
      ...form,
      supplierName: supplierOptions.value.find((s) => s.id === form.supplierId)?.supplierName,
      operatorName: userStore.realName
    }
    if (form.id) {
      await updateInvoice(payload)
      ElMessage.success('发票已修改')
    } else {
      await registerInvoice(payload)
      ElMessage.success('发票已登记')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    // 统一提示
  } finally {
    saving.value = false
  }
}

async function handleCancel(row: ReagentInvoice) {
  try {
    await ElMessageBox.confirm(`确认作废发票 ${row.invoiceNo}？`, '提示', { type: 'warning' })
    await cancelInvoice(row.id!)
    ElMessage.success('已作废')
    fetchList()
  } catch (e) {
    // 取消或接口错误
  }
}

onMounted(() => {
  loadSuppliers()
  fetchList()
})
</script>

<style scoped lang="scss">
.reagent-invoice-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>
