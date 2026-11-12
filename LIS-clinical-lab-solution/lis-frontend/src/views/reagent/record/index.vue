<template>
  <div class="reagent-record-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="试剂名称">
          <el-input
            v-model="searchForm.reagentName"
            placeholder="请输入试剂名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="记录类型">
          <el-select v-model="searchForm.recordType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, key) in RECORD_TYPE_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            @change="handleDateRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <!-- 【缺陷 O】「新增记录」按钮已删除：流水为只读审计数据，只能由业务动作追加写入 -->
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" />
        <el-table-column prop="batchNo" label="批号" width="140" />
        <el-table-column label="记录类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="recordTypeTag(row.recordType)" size="small">
              {{ recordTypeText(row.recordType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column label="操作时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.recordTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
        <!-- 【缺陷 O】「操作」列（编辑/删除）已删除：
             lis_reagent_stock_record 是库存变动的审计流水，手工改删会使
             「SUM(流水.quantity) + 初始值 = 当前 quantity」对账断言失效。
             后端 POST/PUT/DELETE /api/reagent/records 已随 T-06 下线。 -->
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

    <!-- 【缺陷 O】新增/编辑弹窗已整体删除：出入库流水不可手工增改删。
         需要修正账目请走「盘点调账」(/reagent/stock-check)，该流程会在同一事务内
         同时更新库存与流水，保持对账链条完整。 -->
  </div>
</template>

<script setup lang="ts">
// 【缺陷 O】本页面已收敛为【只读】：不再 import saveRecord / deleteRecord
// （后端对应端点已随 T-06 下线），随之不再需要 element-plus 的
// ElMessage / ElMessageBox / FormInstance / FormRules 等写操作依赖。
import { ref, reactive, onMounted } from 'vue'
import dayjs from 'dayjs'
import {
  getRecordPage,
  type ReagentStockRecord,
  type ReagentRecordQuery
} from '@/api/reagent'

const loading = ref(false)
const tableData = ref<ReagentStockRecord[]>([])
const searchForm = reactive<ReagentRecordQuery>({
  pageNum: 1,
  pageSize: 20,
  reagentName: '',
  recordType: undefined as number | undefined,
  beginTime: '',
  endTime: ''
})

// 记录类型枚举（与后端 RecordType 一致）：1入库 2出库 3报损 4退库 5盘点 6消耗 7调拨 8借用 9归还
const RECORD_TYPE_MAP: Record<number, string> = {
  1: '入库',
  2: '出库',
  3: '报损',
  4: '退库',
  5: '盘点',
  6: '消耗',
  7: '调拨',
  8: '借用',
  9: '归还'
}
function recordTypeText(t?: number): string {
  return t === undefined || t === null ? '-' : RECORD_TYPE_MAP[t] || `未知(${t})`
}
function recordTypeTag(t?: number): 'success' | 'warning' | 'info' {
  if (t === 1) return 'success'
  if (t === 2 || t === 3) return 'warning'
  return 'info'
}

// 日期范围
const dateRange = ref<[string, string] | null>(null)

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 【缺陷 O】formDialogVisible / formLoading / formRef / editForm / formRules
// 五个写表单相关的响应式状态已删除，本页只保留查询与分页状态。

// 格式化时间
function formatTime(time?: string): string {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 日期范围变化
function handleDateRangeChange(val: [string, string] | null) {
  if (val) {
    searchForm.beginTime = val[0] + 'T00:00:00'
    searchForm.endTime = val[1] + 'T23:59:59'
  } else {
    searchForm.beginTime = ''
    searchForm.endTime = ''
  }
}

// 拉取列表
async function fetchList() {
  loading.value = true
  try {
    const params: ReagentRecordQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.reagentName) params.reagentName = searchForm.reagentName
    if (searchForm.recordType) params.recordType = searchForm.recordType
    if (searchForm.beginTime) params.beginTime = searchForm.beginTime
    if (searchForm.endTime) params.endTime = searchForm.endTime
    const res = await getRecordPage(params)
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
  searchForm.reagentName = ''
  searchForm.recordType = undefined
  searchForm.beginTime = ''
  searchForm.endTime = ''
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

// 【缺陷 O】handleAdd / handleEdit / handleSave / handleDelete 四个函数已删除。
// 出入库流水只能由入库验收、出库、盘点调账等业务动作在同一事务内追加写入，
// 页面不提供任何手工增改删入口。

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.reagent-record-page {
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
