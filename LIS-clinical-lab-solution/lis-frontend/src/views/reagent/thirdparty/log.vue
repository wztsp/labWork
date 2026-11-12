<template>
  <div class="thirdparty-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="对接方">
          <el-select v-model="query.partnerCode" placeholder="全部" clearable style="width: 170px">
            <el-option
              v-for="item in configList"
              :key="item.partnerCode"
              :label="item.partnerName"
              :value="item.partnerCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="(label, key) in BIZ_TYPE_MAP" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 160px">
            <el-option label="出站（我们调对方）" :value="1" />
            <el-option label="入站（对方调我们）" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.success" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="业务单号/错误信息" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="partnerCode" label="对接方" width="110" />
        <el-table-column label="业务" width="110" align="center">
          <template #default="{ row }">{{ BIZ_TYPE_MAP[row.bizType] || row.bizType || '-' }}</template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">{{ row.direction === 2 ? '入站' : '出站' }}</template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="70" align="center" />
        <el-table-column prop="url" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="bizNo" label="业务单号" width="150" show-overflow-tooltip />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="90" align="right" />
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="调用详情" size="45%">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="对接方">{{ detailData.partnerCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ BIZ_TYPE_MAP[detailData.bizType || ''] || detailData.bizType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="方向">{{ detailData.direction === 2 ? '入站' : '出站' }}</el-descriptions-item>
        <el-descriptions-item label="HTTP 状态">{{ detailData.httpStatus ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ detailData.bizNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detailData.costMs ?? '-' }} ms</el-descriptions-item>
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
import { getConfigList, getLogPage, type ThirdPartyConfig, type ThirdPartyLog } from '@/api/reagent/thirdparty'

const BIZ_TYPE_MAP: Record<string, string> = {
  ORDER: '采购订单',
  PRODUCT: '商品主数据',
  SUPPLIER: '供应商主数据',
  STOCK: '库存效期',
  DELIVERY: '到货回单',
  TEST: '连接测试'
}

const loading = ref(false)
const tableData = ref<ThirdPartyLog[]>([])
const configList = ref<ThirdPartyConfig[]>([])
const query = reactive({
  partnerCode: '',
  bizType: '',
  direction: undefined as number | undefined,
  success: undefined as number | undefined,
  keyword: ''
})
const pagination = reactive({ current: 1, size: 10, total: 0 })

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getLogPage({
      current: pagination.current,
      size: pagination.size,
      partnerCode: query.partnerCode || undefined,
      bizType: query.bizType || undefined,
      direction: query.direction,
      success: query.success,
      keyword: query.keyword || undefined
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
  query.partnerCode = ''
  query.bizType = ''
  query.direction = undefined
  query.success = undefined
  query.keyword = ''
  handleSearch()
}

const detailVisible = ref(false)
const detailData = ref<ThirdPartyLog>({} as ThirdPartyLog)

function showDetail(row: ThirdPartyLog) {
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

onMounted(async () => {
  configList.value = await getConfigList()
  fetchList()
})
</script>

<style scoped>
.thirdparty-page {
  padding: 16px;
}
.search-card {
  margin-bottom: 12px;
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
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.error-text {
  color: #f56c6c;
}
</style>
