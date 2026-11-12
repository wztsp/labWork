<template>
  <div class="qc-alert-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" size="small" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已读" value="READ" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="testItemName" label="检验项目" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.testItemName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" width="120" align="center">
          <template #default="{ row }">{{ row.lotNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="instrumentName" label="仪器" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.instrumentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="预警类型" width="160" align="center">
          <template #default="{ row }">
            <el-tag :type="alertTypeTag(row.alertType)" size="small">{{ alertTypeText(row.alertType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="规则描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="最近测量值" width="110" align="center">
          <template #default="{ row }">{{ row.lastMeasureValue ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="最近Z分数" width="100" align="center">
          <template #default="{ row }">{{ row.lastZScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.alertTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'READ' ? 'info' : 'danger'" size="small">
              {{ row.status === 'READ' ? '已读' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'READ'"
              type="primary"
              link
              size="small"
              @click="handleMarkRead(row)"
            >标记已读</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
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
import { getQcAlertPage, markQcAlertRead, type QcAlert } from '@/api/qc'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<QcAlert[]>([])
const searchForm = reactive({ status: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

function alertTypeText(type?: string): string {
  const map: Record<string, string> = {
    SAME_SIDE_BIAS: '连续同侧偏移',
    TREND: '连续单调趋势',
    TREND_AND_SAME_SIDE: '趋势+同侧偏移'
  }
  return map[type || ''] || type || '-'
}

function alertTypeTag(type?: string): string {
  return type === 'TREND_AND_SAME_SIDE' ? 'danger' : 'warning'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getQcAlertPage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      status: searchForm.status || undefined
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.status = ''
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

async function handleMarkRead(row: QcAlert) {
  try {
    await markQcAlertRead(row.id)
    ElMessage.success('已标记为已读')
    fetchList()
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.qc-alert-page {
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
</style>
