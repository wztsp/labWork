<template>
  <div class="report-list-page">
    <div class="split-layout">
      <!-- 左侧：患者/样本列表 -->
      <el-card shadow="never" class="left-panel">
        <div class="left-search">
          <el-input
            v-model="searchKeyword"
            placeholder="请输入关键字"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prepend>
              <el-select v-model="searchField" style="width: 110px">
                <el-option label="患者姓名" value="patientName" />
                <el-option label="样本条码" value="sampleBarcode" />
              </el-select>
            </template>
            <template #append>
              <el-button :icon="Search" @click="handleSearch" />
            </template>
          </el-input>
        </div>

        <div class="table-wrapper">
          <el-table
            v-loading="loading"
            :data="tableData"
            border
            stripe
            size="small"
            highlight-current-row
            style="width: 100%"
            @row-click="handleRowClick"
          >
            <el-table-column type="index" label="#" width="40" align="center" />
            <el-table-column prop="patientName" label="患者姓名" min-width="80" show-overflow-tooltip />
            <el-table-column label="样本条码" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sampleBarcode || row.sampleId || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ row.statusText || statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="报告时间" width="150" align="center">
              <template #default="{ row }">{{ formatTime(row.publishTime || row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="queryParams.current"
            v-model:page-size="queryParams.size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, prev, pager, next"
            small
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 右侧：报告详情 -->
      <el-card shadow="never" class="right-panel">
        <template #header>
          <div class="right-header">
            <span class="right-title">报告详情</span>
            <div v-if="reportDetail" class="right-actions">
              <el-button v-if="reportDetail.status === 0" type="warning" size="small" @click="handleAudit">
                终审
              </el-button>
            </div>
          </div>
        </template>

        <div v-loading="detailLoading" class="right-body">
          <template v-if="reportDetail">
            <el-descriptions :column="3" border size="small" class="info-desc">
              <el-descriptions-item label="报告编号">{{ reportDetail.reportNo }}</el-descriptions-item>
              <el-descriptions-item label="患者姓名">{{ reportDetail.patientName }}</el-descriptions-item>
              <el-descriptions-item label="样本条码">{{ reportDetail.sampleBarcode || reportDetail.sampleId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="报告类型">{{ reportTypeText(reportDetail.reportType) }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="statusTagType(reportDetail.status)" size="small">
                  {{ reportDetail.statusText || statusText(reportDetail.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="报告医生">{{ reportDetail.reportDoctor || '-' }}</el-descriptions-item>
              <el-descriptions-item label="检验者">{{ reportDetail.checker || '-' }}</el-descriptions-item>
              <el-descriptions-item label="终审人">{{ reportDetail.auditorId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="终审时间">{{ formatTime(reportDetail.auditTime) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(reportDetail.createTime) }}</el-descriptions-item>
            </el-descriptions>

            <div class="section-title">检验结果</div>
            <el-table :data="reportDetail.results" border stripe size="small" class="results-table">
              <el-table-column type="index" label="#" width="40" align="center" />
              <el-table-column prop="testItemName" label="项目名称" min-width="140" show-overflow-tooltip />
              <el-table-column label="结果" width="120">
                <template #default="{ row }">
                  <span :class="{ 'critical-value': row.isCritical === 1 }">{{ row.resultValue }}</span>
                  <el-tag v-if="row.abnormalFlag === 'H'" type="danger" size="small" class="flag-tag">↑</el-tag>
                  <el-tag v-else-if="row.abnormalFlag === 'L'" type="warning" size="small" class="flag-tag">↓</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="80" />
              <el-table-column prop="refRange" label="参考范围" min-width="120" show-overflow-tooltip />
              <el-table-column label="异常标志" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.abnormalFlag === 'H'" type="danger" size="small">偏高</el-tag>
                  <el-tag v-else-if="row.abnormalFlag === 'L'" type="warning" size="small">偏低</el-tag>
                  <span v-else>正常</span>
                </template>
              </el-table-column>
              <el-table-column label="危急值" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isCritical === 1" type="danger" effect="dark" size="small">危急</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="检测时间" width="160" align="center">
                <template #default="{ row }">{{ formatTime(row.testTime) }}</template>
              </el-table-column>
            </el-table>
          </template>

          <el-empty
            v-else-if="!detailLoading"
            description="请从左侧选择患者/样本查看报告详情"
            class="placeholder"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { getReportPage, getReportById, type ReportVO } from '@/api/report'

const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const tableData = ref<ReportVO[]>([])
const total = ref(0)

const searchKeyword = ref('')
const searchField = ref<'patientName' | 'sampleBarcode'>('patientName')
const queryParams = reactive({ current: 1, size: 20 })

const reportDetail = ref<ReportVO | null>(null)

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function statusText(status: number): string {
  const map: Record<number, string> = { 0: '草稿', 1: '已审核', 2: '终审', 3: '已修改' }
  return map[status] || '未知'
}

function statusTagType(status: number): string {
  const map: Record<number, string> = { 0: 'info', 1: 'primary', 2: 'success' }
  return map[status] || 'info'
}

function reportTypeText(type: number): string {
  const map: Record<number, string> = { 1: '常规', 2: '急诊', 3: '微生物' }
  return map[type] || '-'
}

async function fetchList() {
  loading.value = true
  try {
    const params: any = {
      current: queryParams.current,
      size: queryParams.size
    }
    if (searchKeyword.value) {
      params[searchField.value] = searchKeyword.value
    }
    const res = await getReportPage(params)
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

function handleSizeChange(size: number) {
  queryParams.size = size
  queryParams.current = 1
  fetchList()
}

function handlePageChange(page: number) {
  queryParams.current = page
  fetchList()
}

async function handleRowClick(row: ReportVO) {
  if (!row) return
  detailLoading.value = true
  try {
    reportDetail.value = await getReportById(row.id)
  } catch (e) {
    reportDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

function handleAudit() {
  if (reportDetail.value) {
    router.push({ path: '/report/audit', query: { id: reportDetail.value.id } })
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.report-list-page {
  height: calc(100vh - 92px);
  overflow: hidden;

  .split-layout {
    display: flex;
    gap: 12px;
    height: 100%;
  }

  .left-panel {
    width: 38%;
    display: flex;
    flex-direction: column;

    :deep(.el-card__body) {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      padding: 12px;
    }

    .left-search {
      margin-bottom: 10px;
      flex-shrink: 0;
    }

    .table-wrapper {
      flex: 1;
      overflow: auto;
    }

    .pagination-wrapper {
      display: flex;
      justify-content: center;
      margin-top: 10px;
      flex-shrink: 0;
    }

    :deep(.el-table__row) {
      cursor: pointer;
    }
  }

  .right-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    :deep(.el-card__body) {
      flex: 1;
      overflow: auto;
      padding: 16px;
    }

    .right-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .right-title {
        font-weight: 600;
        font-size: 15px;
      }
    }

    .right-body {
      min-height: 100%;
    }

    .info-desc {
      margin-bottom: 16px;
    }

    .section-title {
      font-weight: 600;
      margin-bottom: 8px;
      padding-left: 8px;
      border-left: 3px solid var(--el-color-primary);
      font-size: 14px;
    }

    .critical-value {
      color: var(--el-color-danger);
      font-weight: bold;
    }

    .flag-tag {
      margin-left: 4px;
    }

    .placeholder {
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
}
</style>
