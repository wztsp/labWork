<template>
  <div class="sample-receive-page">
    <!-- 顶部核收 -->
    <el-card class="receive-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="核收仪器">
          <el-select
            v-model="selectedInstrumentId"
            placeholder="请选择核收仪器"
            filterable
            style="width: 260px"
            @change="handleInstrumentChange"
          >
            <el-option
              v-for="ins in instrumentList"
              :key="ins.id"
              :label="ins.instrumentName"
              :value="ins.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="条码核收">
          <el-input
            v-model="barcodeInput"
            placeholder="扫码/输入条码后回车核收"
            clearable
            style="width: 320px"
            :disabled="!selectedInstrumentId"
            @keyup.enter="handleReceive"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="receiveLoading" :disabled="!selectedInstrumentId" @click="handleReceive">核收</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="条码">
          <el-input v-model="searchForm.sampleBarcode" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="患者姓名">
          <el-input v-model="searchForm.patientName" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待核收" :value="3" />
            <el-option label="已核收" :value="4" />
            <el-option label="检测中" :value="5" />
            <el-option label="已完成" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item label="急诊">
          <el-select v-model="searchForm.isUrgent" placeholder="全部" clearable style="width: 120px">
            <el-option label="急诊" :value="1" />
            <el-option label="普通" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :row-class-name="rowClassName"
        style="width: 100%"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="sampleBarcode" label="条码" min-width="180" />
        <el-table-column prop="patientName" label="患者姓名" min-width="100" />
        <el-table-column prop="sampleType" label="标本类型" width="110" />
        <el-table-column prop="instrumentName" label="核收仪器" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.instrumentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.statusText || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="急诊" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isUrgent === 1" type="danger" size="small">急诊</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="核收时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.receiveTime) }}</template>
        </el-table-column>
        <el-table-column prop="department" label="科室" width="120" />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.current"
          v-model:page-size="queryParams.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getSamplePage, receiveSample, type SampleVO } from '@/api/sample'
import { getMyInstruments, type Instrument } from '@/api/instrument'
import { useUserStore } from '@/stores/user'

const loading = ref(false)
const receiveLoading = ref(false)
const tableData = ref<SampleVO[]>([])
const total = ref(0)
const barcodeInput = ref('')
const userStore = useUserStore()

// 仪器列表
const instrumentList = ref<Instrument[]>([])
const selectedInstrumentId = ref<number | undefined>(undefined)

const searchForm = reactive({
  sampleBarcode: '',
  patientName: '',
  status: undefined as number | undefined,
  isUrgent: undefined as number | undefined
})

const queryParams = reactive({ current: 1, size: 10 })

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function statusTagType(status: number): string {
  const map: Record<number, string> = {
    0: 'info',
    1: 'warning',
    2: 'primary',
    3: 'success',
    4: 'success',
    5: 'primary',
    6: 'success',
    7: 'info',
    8: 'info',
    9: 'danger'
  }
  return map[status] || 'info'
}

function rowClassName({ row }: { row: SampleVO }): string {
  return row.isUrgent === 1 ? 'row-urgent' : ''
}

async function fetchInstruments() {
  try {
    instrumentList.value = await getMyInstruments()
  } catch (e) {
    instrumentList.value = []
  }
}

function handleInstrumentChange() {
  // 切换仪器时聚焦条码输入框
  barcodeInput.value = ''
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getSamplePage({
      current: queryParams.current,
      size: queryParams.size,
      sampleBarcode: searchForm.sampleBarcode || undefined,
      patientName: searchForm.patientName || undefined,
      status: searchForm.status,
      isUrgent: searchForm.isUrgent
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.current = 1
  fetchList()
}

function handleReset() {
  searchForm.sampleBarcode = ''
  searchForm.patientName = ''
  searchForm.status = undefined
  searchForm.isUrgent = undefined
  handleSearch()
}

function handleSizeChange(size: number) {
  queryParams.size = size
  queryParams.current = 1
  fetchList()
}

function handlePageChange(page: number) {
  queryParams.current = page
  fetchList()
}

async function handleReceive() {
  const barcode = barcodeInput.value.trim()
  if (!barcode) {
    ElMessage.warning('请输入或扫码标本条码')
    return
  }
  if (!selectedInstrumentId.value) {
    ElMessage.warning('请先选择核收仪器')
    return
  }
  receiveLoading.value = true
  try {
    await receiveSample({
      sampleBarcode: barcode,
      instrumentId: selectedInstrumentId.value,
      operatorId: userStore.userId || undefined
    })
    ElMessage.success('核收成功')
    barcodeInput.value = ''
    fetchList()
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    receiveLoading.value = false
  }
}

onMounted(() => {
  fetchInstruments()
  fetchList()
})
</script>

<style scoped lang="scss">
.sample-receive-page {
  padding: 16px;

  .receive-card {
    margin-bottom: 12px;
  }

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

  :deep(.row-urgent) {
    background-color: var(--el-color-danger-light-9) !important;

    &:hover > td {
      background-color: var(--el-color-danger-light-7) !important;
    }
  }
}
</style>
