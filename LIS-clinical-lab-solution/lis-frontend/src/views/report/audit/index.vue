<template>
  <div class="report-audit-page">
    <el-row :gutter="20">
      <!-- 左侧: 待审核报告列表 -->
      <el-col :span="8">
        <el-card shadow="never" class="left-panel">
          <template #header>
            <div class="panel-header">
              <span>待审核报告</span>
              <el-button type="primary" link size="small" @click="fetchList">刷新</el-button>
            </div>
          </template>
          <el-table
            v-loading="loading"
            :data="reportList"
            border
            stripe
            highlight-current-row
            height="calc(100vh - 220px)"
            @row-click="handleRowClick"
            style="width: 100%"
          >
            <el-table-column prop="reportNo" label="报告编号" min-width="140" />
            <el-table-column prop="patientName" label="患者" width="90" />
            <el-table-column label="时间" width="150" align="center">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="queryParams.current"
              v-model:page-size="queryParams.size"
              :total="total"
              :page-sizes="[20, 50, 100]"
              layout="prev, pager, next"
              small
              background
              @current-change="fetchList"
              @size-change="fetchList"
            />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧: 详情与终审 -->
      <el-col :span="16">
        <el-card shadow="never" class="right-panel">
          <template #header>
            <div class="panel-header">
              <span>报告详情</span>
              <div v-if="currentReport">
                <el-button type="info" plain @click="openPreview">预览报告</el-button>
                <el-button type="success" :loading="auditLoading" @click="handlePass">终审通过</el-button>
                <el-button type="danger" @click="openReject">驳回</el-button>
              </div>
            </div>
          </template>

          <div v-loading="detailLoading" class="detail-content">
            <el-empty v-if="!currentReport" description="请选择左侧报告" />
            <template v-else-if="reportDetail">
              <!-- 患者信息 -->
              <el-card shadow="never" class="info-card">
                <template #header>患者信息</template>
                <el-descriptions :column="3" border size="small">
                  <el-descriptions-item label="姓名">{{ reportDetail.patientName }}</el-descriptions-item>
                  <el-descriptions-item label="条码">{{ sampleBarcode }}</el-descriptions-item>
                  <el-descriptions-item label="类型">{{ reportTypeText(reportDetail.reportType) }}</el-descriptions-item>
                </el-descriptions>
              </el-card>

              <!-- 结果列表 -->
              <el-table :data="reportDetail.results" border stripe size="small" style="margin-top: 12px">
                <el-table-column prop="testItemName" label="项目名称" min-width="140" />
                <el-table-column label="结果" width="140">
                  <template #default="{ row }">
                    <span :class="getResultClass(row)">
                      {{ row.resultValue }} {{ row.unit }}
                      <el-tag v-if="row.abnormalFlag === 'H'" type="danger" size="small">↑</el-tag>
                      <el-tag v-else-if="row.abnormalFlag === 'L'" type="primary" size="small">↓</el-tag>
                      <el-tag v-else-if="row.abnormalFlag === 'N'" type="success" size="small">N</el-tag>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="refRange" label="参考范围" min-width="120" />
                <el-table-column label="危急值" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isCritical === 1" type="danger" effect="dark" size="small">危急值</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="500px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="auditLoading" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 报告预览弹窗(基于模板渲染) -->
    <el-dialog v-model="previewVisible" title="报告预览" width="900px" class="preview-dialog">
      <div v-if="reportDetail" class="preview-container">
        <ReportRenderer :data="previewData" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getReportPage,
  getReportById,
  auditReport,
  type ReportVO,
  type ResultVO
} from '@/api/report'
import ReportRenderer from '@/views/report/components/ReportRenderer.vue'

const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const auditLoading = ref(false)
const reportList = ref<ReportVO[]>([])
const currentReport = ref<ReportVO | null>(null)
const reportDetail = ref<ReportVO | null>(null)
const sampleBarcode = ref('')

const queryParams = reactive({ current: 1, size: 20 })
const total = ref(0)

const rejectDialogVisible = ref(false)
const rejectForm = reactive({ reason: '' })
const previewVisible = ref(false)

/** 预览用的报告数据(适配 ReportRenderer 所需格式) */
const previewData = computed<Record<string, any>>(() => {
  if (!reportDetail.value) return { results: [] }
  const d = reportDetail.value as any
  return {
    patientName: d.patientName || '-',
    gender: d.patientGender,
    age: d.patientAge,
    visitNo: d.visitNo,
    department: d.applyDept || d.department,
    bedNo: d.bedNo,
    doctor: d.applyDoctor,
    clinicalDiag: d.clinicalDiag,
    sampleBarcode: d.sampleBarcode || String(d.sampleId || ''),
    sampleType: d.sampleType,
    containerType: d.containerType,
    collectTime: d.collectTime,
    deliverTime: d.deliverTime,
    signOffTime: d.signOffTime,
    receiveTime: d.receiveTime,
    specimenNo: d.sampleNo,
    checker: d.checker,
    auditor: d.auditorName,
    reportDoctor: d.reportDoctor,
    reportTime: d.auditTime || d.publishTime || d.createTime,
    results: (d.results || []).map((r: ResultVO) => ({
      testItemName: r.testItemName,
      resultValue: r.resultValue,
      unit: r.unit,
      refRange: r.refRange,
      abnormalFlag: r.abnormalFlag,
      isCritical: r.isCritical,
      testTime: r.testTime,
      instrumentName: r.instrumentName
    })),
    sampleImages: (d.sampleImages || []).map((img: any) => ({
      imageUrl: img.imageUrl || img.url,
      imageName: img.imageName || img.title || ''
    }))
  }
})

function openPreview() {
  previewVisible.value = true
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function reportTypeText(type: number): string {
  const map: Record<number, string> = { 1: '常规', 2: '急诊', 3: '微生物' }
  return map[type] || '-'
}

function getResultClass(row: ResultVO) {
  return {
    'critical-value': row.isCritical === 1,
    'abnormal-high': row.abnormalFlag === 'H',
    'abnormal-low': row.abnormalFlag === 'L'
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getReportPage({
      current: queryParams.current,
      size: queryParams.size,
      status: 0
    })
    reportList.value = res.records || []
    total.value = res.total || 0
    // URL 携带 id 时优先选中
    const queryId = route.query.id ? Number(route.query.id) : null
    if (queryId) {
      const matched = reportList.value.find(r => r.id === queryId)
      if (matched) {
        handleRowClick(matched)
        return
      }
      // 列表中没找到, 直接加载详情
      currentReport.value = { id: queryId } as ReportVO
      await loadDetail(queryId)
      return
    }
    if (reportList.value.length && !currentReport.value) {
      handleRowClick(reportList.value[0])
    }
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleRowClick(row: ReportVO) {
  currentReport.value = row
  await loadDetail(row.id)
}

async function loadDetail(id: number) {
  detailLoading.value = true
  try {
    const detail = await getReportById(id)
    reportDetail.value = detail
    // 报告中无 sampleBarcode 字段, 用 sampleId 兜底显示
    sampleBarcode.value = (detail as any).sampleBarcode || String(detail.sampleId || '')
  } catch (e) {
    reportDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function handlePass() {
  if (!currentReport.value) return
  auditLoading.value = true
  try {
    await auditReport({ reportId: currentReport.value.id, pass: true })
    ElMessage.success('终审通过，报告已完成')
    currentReport.value = null
    reportDetail.value = null
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    auditLoading.value = false
  }
}

function openReject() {
  if (!currentReport.value) return
  rejectForm.reason = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  if (!rejectForm.reason.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  if (!currentReport.value) return
  auditLoading.value = true
  try {
    await auditReport({ reportId: currentReport.value.id, pass: false, reason: rejectForm.reason })
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    currentReport.value = null
    reportDetail.value = null
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    auditLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.report-audit-page {
  padding: 16px;

  .left-panel {
    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    :deep(.el-table__row) {
      cursor: pointer;
    }

    .pagination-wrapper {
      margin-top: 12px;
      display: flex;
      justify-content: center;
    }
  }

  .right-panel {
    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .detail-content {
      min-height: 400px;
    }

    .info-card {
      :deep(.el-card__header) {
        padding: 10px 16px;
        font-weight: 600;
      }
    }
  }

  .critical-value {
    color: var(--el-color-danger);
    font-weight: bold;
  }

  .abnormal-high {
    color: var(--el-color-danger);
    font-weight: bold;
  }

  .abnormal-low {
    color: var(--el-color-primary);
    font-weight: bold;
  }
}

.preview-dialog {
  :deep(.el-dialog__body) {
    max-height: 70vh;
    overflow-y: auto;
    padding: 0;
  }
}

.preview-container {
  display: flex;
  justify-content: center;
}
</style>
