<template>
  <div class="alert-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="预警级别">
          <el-select v-model="searchForm.alertLevel" placeholder="全部" clearable style="width: 140px">
            <el-option value="WARNING" label="警告 (WARNING)" />
            <el-option value="CRITICAL" label="危急值 (CRITICAL)" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option value="PENDING" label="待处理" />
            <el-option value="READ" label="已读" />
            <el-option value="HANDLED" label="已处理" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button plain @click="fetchList">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计概览 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-total">
          <div class="stat-label">预警总数</div>
          <div class="stat-value">{{ allAlerts.length }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-pending">
          <div class="stat-label">待处理</div>
          <div class="stat-value">{{ pendingCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-critical">
          <div class="stat-label">危急值</div>
          <div class="stat-value">{{ criticalCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-handled">
          <div class="stat-label">已处理</div>
          <div class="stat-value">{{ handledCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredAlerts" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="预警类型" width="160" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertTypeTagType(row.alertType)" size="small" effect="dark">
              {{ getAlertTypeText(row.alertType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'" size="small">
              {{ row.alertLevel === 'CRITICAL' ? '危急值' : '警告' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="organismName" label="细菌名称" width="180" />
        <el-table-column prop="message" label="预警描述" min-width="280" show-overflow-tooltip />
        <el-table-column label="关联报告" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewReport(row)">
              #{{ row.microReportId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="处理人" width="100" align="center">
          <template #default="{ row }">{{ row.handler || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.handleTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="primary" link size="small" @click="handleMarkRead(row)">
              标记已读
            </el-button>
            <el-button v-if="row.status !== 'HANDLED'" type="success" link size="small" @click="openHandleDialog(row)">
              处理
            </el-button>
            <el-button type="info" link size="small" @click="openDetailDialog(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 处理预警弹窗 -->
    <el-dialog v-model="handleDialogVisible" title="处理预警" width="520px">
      <el-descriptions :column="1" border size="small" class="mb12">
        <el-descriptions-item label="预警类型">
          <el-tag :type="getAlertTypeTagType(currentRow?.alertType)" size="small">
            {{ getAlertTypeText(currentRow?.alertType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="细菌名称">{{ currentRow?.organismName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预警描述">{{ currentRow?.message || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="handleForm" label-width="100px">
        <el-form-item label="处理人" required>
          <el-input v-model="handleForm.handler" placeholder="请输入处理人姓名" />
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="handleForm.remark" type="textarea" :rows="3" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleLoading" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="预警详情" width="560px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="预警ID">{{ currentRow?.id }}</el-descriptions-item>
        <el-descriptions-item label="预警类型">
          <el-tag :type="getAlertTypeTagType(currentRow?.alertType)" size="small">
            {{ getAlertTypeText(currentRow?.alertType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预警级别">
          <el-tag :type="currentRow?.alertLevel === 'CRITICAL' ? 'danger' : 'warning'" size="small">
            {{ currentRow?.alertLevel === 'CRITICAL' ? '危急值' : '警告' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="细菌名称">{{ currentRow?.organismName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联报告ID">{{ currentRow?.microReportId }}</el-descriptions-item>
        <el-descriptions-item label="预警描述">{{ currentRow?.message || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag :type="getStatusTagType(currentRow?.status)" size="small">
            {{ getStatusText(currentRow?.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="触发时间">{{ formatTime(currentRow?.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentRow?.handler || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理备注">{{ currentRow?.handleRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatTime(currentRow?.handleTime) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMicroAlertList,
  markAlertRead,
  handleAlert,
  type MicroAlert
} from '@/api/report/micro'

const loading = ref(false)
const allAlerts = ref<MicroAlert[]>([])
const searchForm = reactive({
  status: '',
  alertLevel: ''
})

const filteredAlerts = computed(() => allAlerts.value)

const pendingCount = computed(() => allAlerts.value.filter((a) => a.status === 'PENDING').length)
const criticalCount = computed(
  () => allAlerts.value.filter((a) => a.alertLevel === 'CRITICAL').length
)
const handledCount = computed(() => allAlerts.value.filter((a) => a.status === 'HANDLED').length)

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.alertLevel) params.alertLevel = searchForm.alertLevel
    allAlerts.value = (await getMicroAlertList(params)) || []
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchList()
}

function handleReset() {
  searchForm.status = ''
  searchForm.alertLevel = ''
  fetchList()
}

// 预警类型映射
const alertTypeMap: Record<string, { text: string; tag: string }> = {
  MRSA: { text: 'MRSA', tag: 'danger' },
  VRE: { text: 'VRE', tag: 'danger' },
  CRE: { text: 'CRE', tag: 'danger' },
  ESBL: { text: 'ESBL', tag: 'warning' },
  CRAB: { text: 'CRAB', tag: 'danger' },
  MDR: { text: 'MDR', tag: 'warning' },
  CRITICAL_VALUE: { text: '危急值', tag: 'danger' }
}

function getAlertTypeText(type?: string): string {
  return alertTypeMap[type || '']?.text || type || '-'
}

function getAlertTypeTagType(type?: string): string {
  return alertTypeMap[type || '']?.tag || 'info'
}

function getStatusTagType(status?: string): string {
  if (status === 'PENDING') return 'danger'
  if (status === 'READ') return 'warning'
  if (status === 'HANDLED') return 'success'
  return 'info'
}

function getStatusText(status?: string): string {
  if (status === 'PENDING') return '待处理'
  if (status === 'READ') return '已读'
  if (status === 'HANDLED') return '已处理'
  return '-'
}

function formatTime(time?: string): string {
  if (!time) return '-'
  try {
    return time.replace('T', ' ').substring(0, 19)
  } catch {
    return time
  }
}

// 标记已读
async function handleMarkRead(row: MicroAlert) {
  try {
    await markAlertRead(row.id)
    ElMessage.success('已标记为已读')
    fetchList()
  } catch (e) {
    // ignore
  }
}

// 处理预警
const handleDialogVisible = ref(false)
const handleLoading = ref(false)
const currentRow = ref<MicroAlert | null>(null)
const handleForm = reactive({
  handler: '',
  remark: ''
})

function openHandleDialog(row: MicroAlert) {
  currentRow.value = row
  handleForm.handler = ''
  handleForm.remark = ''
  handleDialogVisible.value = true
}

async function confirmHandle() {
  if (!currentRow.value) return
  if (!handleForm.handler.trim()) {
    ElMessage.warning('请输入处理人姓名')
    return
  }
  handleLoading.value = true
  try {
    await handleAlert(currentRow.value.id, handleForm.handler.trim(), handleForm.remark.trim())
    ElMessage.success('处理成功')
    handleDialogVisible.value = false
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    handleLoading.value = false
  }
}

// 详情弹窗
const detailDialogVisible = ref(false)

function openDetailDialog(row: MicroAlert) {
  currentRow.value = row
  detailDialogVisible.value = true
}

// 查看关联报告
async function handleViewReport(row: MicroAlert) {
  try {
    await ElMessageBox.confirm(
      `跳转到微生物报告 #${row.microReportId} 的详情页查看?`,
      '提示',
      { type: 'info', confirmButtonText: '查看报告', cancelButtonText: '取消' }
    )
    // 跳转到微生物报告页面 (使用 hash 路由)
    window.location.hash = `#/report/micro?id=${row.microReportId}`
  } catch (e) {
    // 取消
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.alert-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .stat-row {
    margin-bottom: 12px;

    .stat-card {
      text-align: center;

      .stat-label {
        font-size: 13px;
        color: #909399;
        margin-bottom: 4px;
      }

      .stat-value {
        font-size: 24px;
        font-weight: 600;
      }
    }

    .stat-total .stat-value {
      color: #1a73e8;
    }
    .stat-pending .stat-value {
      color: #e6a23c;
    }
    .stat-critical .stat-value {
      color: #f56c6c;
    }
    .stat-handled .stat-value {
      color: #67c23a;
    }
  }

  .mb12 {
    margin-bottom: 12px;
  }
}
</style>
