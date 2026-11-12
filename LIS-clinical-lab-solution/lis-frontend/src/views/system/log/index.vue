<template>
  <div class="log-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="searchForm.module" placeholder="如 标本管理" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="module" label="模块" width="130" />
        <el-table-column prop="operation" label="操作" min-width="160" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="method-text">{{ row.method || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.costTime != null" :type="costTimeTag(row.costTime)" size="small">
              {{ row.costTime }} ms
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="640px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="日志ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail.operation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costTime != null ? `${detail.costTime} ms` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">
          <span class="method-text">{{ detail.method || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="json-block">{{ formatJson(detail.params) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="返回结果" :span="2">
          <pre class="json-block">{{ formatJson(detail.result) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  getLogList,
  getLogDetail,
  type AuditLog,
  type LogQuery
} from '@/api/system'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<AuditLog[]>([])
const dateRange = ref<[string, string] | null>(null)
const searchForm = reactive<LogQuery>({
  username: '',
  module: '',
  beginTime: undefined,
  endTime: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 详情
const detailDialogVisible = ref(false)
const detail = ref<AuditLog | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await getLogList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      username: searchForm.username || undefined,
      module: searchForm.module || undefined,
      beginTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1]
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.username = ''
  searchForm.module = ''
  searchForm.beginTime = undefined
  searchForm.endTime = undefined
  dateRange.value = null
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

async function handleDetail(row: AuditLog) {
  try {
    detail.value = await getLogDetail(row.id)
    detailDialogVisible.value = true
  } catch (e) {
    // ignore
  }
}

function costTimeTag(cost: number): 'success' | 'warning' | 'danger' {
  if (cost < 500) return 'success'
  if (cost < 2000) return 'warning'
  return 'danger'
}

function formatJson(str?: string): string {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch (e) {
    return str
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.log-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .method-text {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 12px;
      color: var(--el-text-color-regular);
    }

    .text-muted {
      color: var(--el-text-color-placeholder);
    }

    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .json-block {
    margin: 0;
    max-height: 200px;
    overflow: auto;
    background: var(--el-fill-color-light);
    padding: 8px 12px;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
