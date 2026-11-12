<template>
  <div class="reagent-opening-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="试剂">
          <el-select v-model="query.reagentId" placeholder="全部试剂" clearable filterable style="width: 220px">
            <el-option
              v-for="item in reagentOptions"
              :key="item.id"
              :label="`${item.reagentName}${item.reagentCode ? '（' + item.reagentCode + '）' : ''}`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="正常" :value="1" />
            <el-option label="已闭封" :value="2" />
            <el-option label="已耗尽" :value="3" />
            <el-option label="已过期" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.openingType" placeholder="全部类型" clearable style="width: 150px">
            <el-option label="首次开封" :value="1" />
            <el-option label="二次开封" :value="2" />
            <el-option label="质控分包" :value="3" />
            <el-option label="危化品" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="开瓶日期">
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
          <el-button type="success" plain @click="subPackageVisible = true">质控分包</el-button>
          <el-button type="warning" plain @click="hazardVisible = true">危化品开封申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="试剂" min-width="200">
          <template #default="{ row }">
            <div class="reagent-cell">
              <span>{{ row.reagentName || '-' }}</span>
              <span v-if="row.lotNo" class="sub-text">批号：{{ row.lotNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="openingTypeTag(row.openingType)">{{ openingTypeText(row.openingType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开瓶时间" width="165" align="center">
          <template #default="{ row }">{{ formatTime(row.openTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="开封效期" width="130" align="center">
          <template #default="{ row }">{{ row.openedExpiry || '-' }}</template>
        </el-table-column>
        <el-table-column label="份数" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.packTotal">{{ row.packNo || 1 }} / {{ row.packTotal }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="110" align="center">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="危化审批" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.openingType === 4" size="small" :type="approvalTag(row.approvalStatus)">
              {{ approvalText(row.approvalStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canReopen(row)"
              type="primary"
              link
              size="small"
              @click="handleReopen(row)"
            >二次开封</el-button>
            <el-button v-if="canSeal(row)" type="warning" link size="small" @click="handleSeal(row)">闭封</el-button>
            <el-button v-if="canExhaust(row)" type="info" link size="small" @click="handleExhaust(row)">耗尽</el-button>
            <template v-if="row.openingType === 4 && row.approvalStatus === 0">
              <el-button type="success" link size="small" @click="handleHazardApprove(row)">审批</el-button>
              <el-button type="danger" link size="small" @click="handleHazardReject(row)">驳回</el-button>
            </template>
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

    <!-- 质控品分包 -->
    <el-dialog v-model="subPackageVisible" title="质控品分包" width="520px" destroy-on-close>
      <el-form ref="subPackageFormRef" :model="subPackageForm" :rules="subPackageRules" label-width="100px">
        <el-form-item label="库存ID" prop="stockId">
          <el-input-number v-model="subPackageForm.stockId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="试剂" prop="reagentId">
          <el-select v-model="subPackageForm.reagentId" placeholder="请选择试剂" filterable style="width: 100%">
            <el-option
              v-for="item in reagentOptions"
              :key="item.id"
              :label="`${item.reagentName}${item.reagentCode ? '（' + item.reagentCode + '）' : ''}`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分包份数" prop="packCount">
          <el-input-number v-model="subPackageForm.packCount" :min="1" :max="999" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subPackageVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSubPackage">确定</el-button>
      </template>
    </el-dialog>

    <!-- 危化品开封申请 -->
    <el-dialog v-model="hazardVisible" title="危化品开封申请" width="520px" destroy-on-close>
      <el-form ref="hazardFormRef" :model="hazardForm" :rules="hazardRules" label-width="100px">
        <el-form-item label="试剂" prop="reagentId">
          <el-select v-model="hazardForm.reagentId" placeholder="请选择试剂" filterable style="width: 100%">
            <el-option
              v-for="item in reagentOptions"
              :key="item.id"
              :label="`${item.reagentName}${item.reagentCode ? '（' + item.reagentCode + '）' : ''}`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库存ID">
          <el-input-number v-model="hazardForm.stockId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="申请用量" prop="demandQty">
          <el-input-number v-model="hazardForm.demandQty" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="申请说明">
          <el-input v-model="hazardForm.remark" type="textarea" :rows="3" placeholder="请填写用途、防护措施等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hazardVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHazard">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import {
  getOpeningPage,
  reopenOpening,
  sealOpening,
  exhaustOpening,
  subPackageOpening,
  applyHazardOpening,
  approveHazardOpening,
  rejectHazardOpening,
  getReagentPage,
  type Reagent,
  type ReagentOpening,
  type ReagentOpeningQuery
} from '@/api/reagent'

const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const list = ref<ReagentOpening[]>([])
const total = ref(0)
const reagentOptions = ref<Reagent[]>([])
const dateRange = ref<[string, string] | null>(null)

const query = reactive<ReagentOpeningQuery>({
  pageNum: 1,
  pageSize: 10
})

const subPackageVisible = ref(false)
const hazardVisible = ref(false)
const subPackageFormRef = ref<FormInstance>()
const hazardFormRef = ref<FormInstance>()

const subPackageForm = reactive({ stockId: undefined as number | undefined, reagentId: undefined as number | undefined, packCount: 10 })
const hazardForm = reactive({
  reagentId: undefined as number | undefined,
  stockId: undefined as number | undefined,
  demandQty: 1,
  remark: ''
})

const subPackageRules: FormRules = {
  stockId: [{ required: true, message: '请输入库存ID', trigger: 'blur' }],
  reagentId: [{ required: true, message: '请选择试剂', trigger: 'change' }],
  packCount: [{ required: true, message: '请输入分包份数', trigger: 'blur' }]
}
const hazardRules: FormRules = {
  reagentId: [{ required: true, message: '请选择试剂', trigger: 'change' }],
  demandQty: [{ required: true, message: '请输入申请用量', trigger: 'blur' }]
}

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}
function openingTypeText(type?: number) {
  const map: Record<number, string> = { 1: '首次开封', 2: '二次开封', 3: '质控分包', 4: '危化品' }
  return map[type || 1] || '首次开封'
}
function openingTypeTag(type?: number) {
  const map: Record<number, string> = { 1: 'primary', 2: 'success', 3: 'info', 4: 'danger' }
  return (map[type || 1] || 'primary') as any
}
function statusText(status?: number) {
  const map: Record<number, string> = { 0: '已过期', 1: '正常', 2: '已闭封', 3: '已耗尽' }
  return map[status ?? 1] || '正常'
}
function statusTag(status?: number) {
  const map: Record<number, string> = { 0: 'danger', 1: 'success', 2: 'info', 3: 'warning' }
  return (map[status ?? 1] || 'success') as any
}
function approvalText(status?: number) {
  const map: Record<number, string> = { 0: '待审批', 1: '已通过', 2: '已驳回' }
  return map[status ?? 0] || '待审批'
}
function approvalTag(status?: number) {
  const map: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger' }
  return (map[status ?? 0] || 'warning') as any
}

function canReopen(row: ReagentOpening) {
  return (row.status === 1 || row.status === 2) && (row.openingType === 1 || row.sealStatus === 1)
}
function canSeal(row: ReagentOpening) {
  return row.status === 1
}
function canExhaust(row: ReagentOpening) {
  return row.status === 1 || row.status === 2
}

async function loadReagentOptions() {
  try {
    const page = await getReagentPage({ pageNum: 1, pageSize: 1000, status: 1 })
    reagentOptions.value = page.records || []
  } catch (e) {
    reagentOptions.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: ReagentOpeningQuery = { ...query }
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const page = await getOpeningPage(params)
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
  query.reagentId = undefined
  query.status = undefined
  query.openingType = undefined
  query.pageNum = 1
  query.pageSize = 10
  dateRange.value = null
  fetchList()
}

async function handleReopen(row: ReagentOpening) {
  try {
    await reopenOpening(row.id!, userStore.userId, userStore.realName)
    ElMessage.success('二次开封成功')
    fetchList()
  } catch (e) {
    // 统一提示
  }
}

async function handleSeal(row: ReagentOpening) {
  try {
    await ElMessageBox.confirm('确认闭封该开封记录？闭封后不可继续使用。', '提示', { type: 'warning' })
    await sealOpening(row.id!, userStore.realName)
    ElMessage.success('已闭封')
    fetchList()
  } catch (e) {
    // 取消或接口错误
  }
}

async function handleExhaust(row: ReagentOpening) {
  try {
    await ElMessageBox.confirm('确认该开封试剂已耗尽？', '提示', { type: 'warning' })
    await exhaustOpening(row.id!)
    ElMessage.success('已标记耗尽')
    fetchList()
  } catch (e) {
    // 取消或接口错误
  }
}

async function handleHazardApprove(row: ReagentOpening) {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批意见（可留空）', '危化品开封审批', {
      inputPlaceholder: '同意开封',
      confirmButtonText: '通过',
      cancelButtonText: '取消'
    })
    await approveHazardOpening(row.id!, {
      approverId: userStore.userId,
      approverName: userStore.realName,
      opinion: value || '同意'
    })
    ElMessage.success('审批通过')
    fetchList()
  } catch (e) {
    // 取消或接口错误
  }
}

async function handleHazardReject(row: ReagentOpening) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '危化品开封审批', {
      inputPlaceholder: '请输入驳回原因',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputValidator: (val) => (val?.trim() ? true : '驳回原因不能为空')
    })
    await rejectHazardOpening(row.id!, {
      approverId: userStore.userId,
      approverName: userStore.realName,
      opinion: value
    })
    ElMessage.success('已驳回')
    fetchList()
  } catch (e) {
    // 取消或接口错误
  }
}

async function submitSubPackage() {
  await subPackageFormRef.value?.validate()
  submitting.value = true
  try {
    await subPackageOpening({
      stockId: subPackageForm.stockId!,
      reagentId: subPackageForm.reagentId!,
      packCount: subPackageForm.packCount,
      operatorId: userStore.userId,
      operatorName: userStore.realName
    })
    ElMessage.success('分包成功')
    subPackageVisible.value = false
    subPackageForm.stockId = undefined
    subPackageForm.reagentId = undefined
    subPackageForm.packCount = 10
    fetchList()
  } catch (e) {
    // 统一提示
  } finally {
    submitting.value = false
  }
}

async function submitHazard() {
  await hazardFormRef.value?.validate()
  submitting.value = true
  try {
    await applyHazardOpening({
      reagentId: hazardForm.reagentId!,
      stockId: hazardForm.stockId,
      demandQty: hazardForm.demandQty,
      operatorId: userStore.userId,
      operatorName: userStore.realName,
      remark: hazardForm.remark
    })
    ElMessage.success('申请已提交，等待审批')
    hazardVisible.value = false
    hazardForm.reagentId = undefined
    hazardForm.stockId = undefined
    hazardForm.demandQty = 1
    hazardForm.remark = ''
    fetchList()
  } catch (e) {
    // 统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadReagentOptions()
  fetchList()
})
</script>

<style scoped lang="scss">
.reagent-opening-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .reagent-cell {
    display: flex;
    flex-direction: column;
    line-height: 1.4;

    .sub-text {
      color: #909399;
      font-size: 12px;
    }
  }

  .pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>
