<template>
  <div class="his-log-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="服务">
          <el-select v-model="query.serviceNo" placeholder="全部" clearable filterable style="width: 190px">
            <el-option
              v-for="(label, key) in HIS_SERVICE_MAP"
              :key="key"
              :label="`${key} ${label}`"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 140px">
            <el-option label="出站（LIS调HIS）" :value="1" />
            <el-option label="入站（HIS调LIS）" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.success" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="申请号/条码/流水号" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="时间">
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
          <el-button @click="fetchList">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="服务" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.serviceNo || '-' }}</el-tag>
            <span class="service-name">{{ HIS_SERVICE_MAP[row.serviceNo || ''] || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">{{ row.direction === 2 ? '入站' : '出站' }}</template>
        </el-table-column>
        <el-table-column prop="refNo" label="业务单号" width="180" show-overflow-tooltip />
        <el-table-column prop="httpStatus" label="HTTP" width="80" align="center" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100" align="right">
          <template #default="{ row }">{{ row.costMs ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="接口调用详情" size="50%">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="服务">
          {{ detailData.serviceNo || '-' }} {{ HIS_SERVICE_MAP[detailData.serviceNo || ''] || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="方向">{{ detailData.direction === 2 ? '入站' : '出站' }}</el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ detailData.refNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="HTTP 状态">{{ detailData.httpStatus ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detailData.costMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="调用时间">{{ formatTime(detailData.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="流水号" :span="2">{{ detailData.actionNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ detailData.url || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          <span class="error-text">{{ detailData.errorMsg || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <div class="body-title">请求报文</div>
      <pre class="body-block">{{ pretty(detailData.requestBody) }}</pre>
      <div class="body-title">响应报文</div>
      <pre class="body-block">{{ pretty(detailData.responseBody) }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import dayjs from 'dayjs'
import { getHisLogPage, HIS_SERVICE_MAP, type HisInterfaceLog } from '@/api/integration'

const loading = ref(false)
const tableData = ref<HisInterfaceLog[]>([])
const dateRange = ref<[string, string] | null>(null)
const query = reactive({
  serviceNo: '',
  direction: undefined as number | undefined,
  success: undefined as number | undefined,
  keyword: ''
})
const pagination = reactive({ current: 1, size: 20, total: 0 })

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getHisLogPage({
      current: pagination.current,
      size: pagination.size,
      serviceNo: query.serviceNo || undefined,
      direction: query.direction,
      success: query.success,
      keyword: query.keyword || undefined,
      beginTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1]
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchList()
}

function handleReset() {
  query.serviceNo = ''
  query.direction = undefined
  query.success = undefined
  query.keyword = ''
  dateRange.value = null
  handleSearch()
}

const detailVisible = ref(false)
const detailData = ref<HisInterfaceLog>({} as HisInterfaceLog)

function showDetail(row: HisInterfaceLog) {
  detailData.value = row
  detailVisible.value = true
}

function pretty(text?: string) {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.his-log-page {
  padding: 16px;
}
.search-card {
  margin-bottom: 12px;
}
.service-name {
  margin-left: 6px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.body-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
.body-block {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  font-size: 12px;
  line-height: 18px;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.error-text {
  color: #f56c6c;
}
</style>
