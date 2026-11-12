<template>
  <div class="reagent-batches-page">
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
        <el-form-item label="批次号">
          <el-input v-model="query.batchNo" placeholder="请输入批号" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="可用" :value="1" />
            <el-option label="已过期" :value="0" />
            <el-option label="已耗尽" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.includeZero">含零库存</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="试剂" min-width="220">
          <template #default="{ row }">
            <div class="reagent-cell">
              <span>{{ row.reagentName || '-' }}</span>
              <span v-if="row.reagentCode" class="sub-text">编码：{{ row.reagentCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="160" align="center">
          <template #default="{ row }">{{ row.batchNo || row.lotNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="数量" width="120" align="right">
          <template #default="{ row }">
            <span>{{ formatNumber(row.quantity) }}</span>
            <span v-if="row.unit" class="unit">{{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="效期" width="130" align="center">
          <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="barcode" label="条码" min-width="170" show-overflow-tooltip />
        <el-table-column prop="location" label="库位" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getStockBatchPage, getReagentPage, type Reagent, type ReagentBatchQuery, type ReagentStock } from '@/api/reagent'

const loading = ref(false)
const list = ref<ReagentStock[]>([])
const total = ref(0)
const reagentOptions = ref<Reagent[]>([])

const query = reactive<ReagentBatchQuery>({
  pageNum: 1,
  pageSize: 10,
  includeZero: true
})

function formatNumber(value?: number) {
  if (value === undefined || value === null) return '0'
  return String(Number(value))
}
function statusText(status?: number) {
  const map: Record<number, string> = { 0: '已过期', 1: '可用', 2: '已耗尽' }
  return map[status ?? 1] || '可用'
}
function statusTag(status?: number) {
  const map: Record<number, string> = { 0: 'danger', 1: 'success', 2: 'info' }
  return (map[status ?? 1] || 'success') as any
}

async function loadReagentOptions() {
  try {
    const page = await getReagentPage({ pageNum: 1, pageSize: 1000 })
    reagentOptions.value = page.records || []
  } catch (e) {
    reagentOptions.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getStockBatchPage({ ...query })
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
  query.batchNo = ''
  query.status = undefined
  query.includeZero = true
  query.pageNum = 1
  query.pageSize = 10
  fetchList()
}

onMounted(() => {
  loadReagentOptions()
  fetchList()
})
</script>

<style scoped lang="scss">
.reagent-batches-page {
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

  .unit {
    margin-left: 4px;
    color: #909399;
    font-size: 12px;
  }

  .pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>
