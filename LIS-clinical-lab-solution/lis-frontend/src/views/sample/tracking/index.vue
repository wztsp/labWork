<template>
  <div class="sample-tracking-page">
    <!-- 顶部查询 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="条码/ID">
          <el-input
            v-model="keyword"
            placeholder="请输入标本条码或ID"
            clearable
            style="width: 280px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <el-empty v-if="!sampleInfo" description="请输入条码或ID查询标本追踪信息" />

      <template v-else>
        <!-- 标本信息 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>标本信息</span>
              <div class="header-actions">
                <el-button
                  v-if="canRefundOrVoid"
                  type="warning"
                  size="small"
                  @click="handleRefund"
                >退费</el-button>
                <el-button
                  v-if="canRefundOrVoid"
                  type="danger"
                  size="small"
                  plain
                  @click="handleVoid"
                >作废</el-button>
                <el-button type="primary" size="small" plain @click="openMergeDialog">合并标本</el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="条码">{{ sampleInfo.sampleBarcode }}</el-descriptions-item>
            <el-descriptions-item label="患者姓名">{{ sampleInfo.patientName }}</el-descriptions-item>
            <el-descriptions-item label="标本类型">{{ sampleInfo.sampleType }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small">{{ sampleInfo.statusText || '未知' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="科室">{{ sampleInfo.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="急诊">
              <el-tag v-if="sampleInfo.isUrgent === 1" type="danger" size="small">急诊</el-tag>
              <span v-else>普通</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 时间线 -->
        <el-card class="timeline-card" shadow="never">
          <template #header>
            <span>处理时间线</span>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(node, idx) in timelineNodes"
              :key="idx"
              :timestamp="node.time"
              :type="node.time ? 'success' : 'info'"
              :hollow="!node.time"
            >
              <h4>{{ node.title }}</h4>
              <p v-if="node.time" class="node-time">{{ formatTime(node.time) }}</p>
              <p v-else class="muted">未完成</p>
              <p v-if="node.person" class="node-person">处理人: {{ node.person }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </template>
    </div>

    <!-- 合并标本对话框 -->
    <el-dialog v-model="mergeDialogVisible" title="合并标本" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="目标标本">
          <el-input :model-value="mergeTargetLabel" disabled />
          <div class="merge-tip">当前查询的标本将作为合并目标, 源标本的结果/项目将归并到此标本。</div>
        </el-form-item>
        <el-form-item label="源标本条码">
          <el-input
            v-model="mergeSourceInput"
            placeholder="输入源标本条码/ID, 多个用逗号分隔"
            clearable
            type="textarea"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="mergeLoading" @click="handleMerge">确认合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  trackSample,
  getSampleByBarcode,
  getSampleById,
  refundSample,
  voidSample,
  type SampleVO
} from '@/api/sample'
import { mergeReports } from '@/api/report'

const loading = ref(false)
const keyword = ref('')
const sampleInfo = ref<SampleVO | null>(null)

interface TimelineNode {
  title: string
  time: string | undefined
  person?: string
}

const timelineNodes = ref<TimelineNode[]>([])

// 退费/作废按钮仅对已报告(6)/已签收(3)状态显示
const canRefundOrVoid = computed(() => {
  if (!sampleInfo.value) return false
  return sampleInfo.value.status === 6 || sampleInfo.value.status === 3
})

// 合并标本对话框
const mergeDialogVisible = ref(false)
const mergeLoading = ref(false)
const mergeSourceInput = ref('')
const mergeTargetLabel = computed(() => {
  if (!sampleInfo.value) return ''
  return `${sampleInfo.value.sampleBarcode} (${sampleInfo.value.patientName || '-'})`
})

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function buildTimeline(s: SampleVO) {
  timelineNodes.value = [
    { title: '申请时间', time: s.createTime },
    { title: '采集时间', time: s.collectTime, person: s.collectorName },
    { title: '送检时间', time: s.deliverTime, person: s.deliverPerson },
    { title: '签收时间', time: s.signOffTime, person: s.signOffPerson },
    { title: '核收时间', time: s.receiveTime },
    { title: '检测开始', time: s.testStartTime },
    { title: '报告时间', time: s.reportTime }
  ]
}

async function handleQuery() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入条码或ID')
    return
  }
  loading.value = true
  try {
    let sample: SampleVO | null = null
    // 优先按条码查询 (条码可能是纯数字如260731000001, 也可能含字母)
    try {
      sample = await getSampleByBarcode(kw)
    } catch (e) {
      // 条码查询失败, 继续尝试按ID查询
    }
    // 条码查不到且输入是纯数字, 尝试按标本ID查询 (雪花ID为19位数字)
    if (!sample && /^\d+$/.test(kw)) {
      try {
        const trackRes = await trackSample(Number(kw))
        if (trackRes) {
          sample = trackRes as SampleVO
        }
      } catch (e) {
        // 回退到 getSampleById
      }
      if (!sample) {
        try {
          sample = await getSampleById(Number(kw))
        } catch (e) {
          // ID也查不到
        }
      }
    }
    if (!sample) {
      ElMessage.warning('未找到对应标本')
      sampleInfo.value = null
      timelineNodes.value = []
      return
    }
    sampleInfo.value = sample
    buildTimeline(sample)
  } catch (e) {
    sampleInfo.value = null
    timelineNodes.value = []
  } finally {
    loading.value = false
  }
}

async function handleRefund() {
  if (!sampleInfo.value) return
  const s = sampleInfo.value
  try {
    await ElMessageBox.confirm(
      `确认对标本 ${s.sampleBarcode} (${s.patientName || '-'}) 进行退费操作？`,
      '退费确认',
      { type: 'warning', confirmButtonText: '确认退费', cancelButtonText: '取消' }
    )
    await refundSample(s.id)
    ElMessage.success('标本退费成功')
    handleQuery()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误提示已由请求拦截器统一处理
    }
  }
}

async function handleVoid() {
  if (!sampleInfo.value) return
  const s = sampleInfo.value
  try {
    await ElMessageBox.confirm(
      `确认对标本 ${s.sampleBarcode} (${s.patientName || '-'}) 进行作废操作？作废后不可恢复。`,
      '作废确认',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
    await voidSample(s.id)
    ElMessage.success('标本作废成功')
    handleQuery()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误提示已由请求拦截器统一处理
    }
  }
}

function openMergeDialog() {
  if (!sampleInfo.value) {
    ElMessage.warning('请先查询标本')
    return
  }
  mergeSourceInput.value = ''
  mergeDialogVisible.value = true
}

async function handleMerge() {
  if (!sampleInfo.value) return
  const raw = mergeSourceInput.value.trim()
  if (!raw) {
    ElMessage.warning('请输入源标本条码/ID')
    return
  }
  // 解析源标本输入 (逗号/空格/换行分隔)
  const tokens = raw.split(/[,，\s\n]+/).map(s => s.trim()).filter(Boolean)
  if (tokens.length === 0) {
    ElMessage.warning('请输入有效的源标本条码/ID')
    return
  }
  // 数字直接作为标本ID, 非数字按条码查询转ID
  const sourceIds: number[] = []
  for (const tk of tokens) {
    if (/^\d+$/.test(tk)) {
      sourceIds.push(Number(tk))
    } else {
      try {
        const s = await getSampleByBarcode(tk)
        if (s && s.id) {
          sourceIds.push(s.id)
        } else {
          ElMessage.warning(`源标本 ${tk} 不存在`)
          return
        }
      } catch (e) {
        ElMessage.warning(`源标本 ${tk} 查询失败`)
        return
      }
    }
  }
  const targetId = sampleInfo.value.id
  if (sourceIds.includes(targetId)) {
    ElMessage.warning('源标本不能包含目标标本')
    return
  }
  mergeLoading.value = true
  try {
    await mergeReports(targetId, sourceIds)
    ElMessage.success(`合并成功, 共合并 ${sourceIds.length} 个源标本`)
    mergeDialogVisible.value = false
    handleQuery()
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    mergeLoading.value = false
  }
}

onMounted(() => {
  // 初始不加载, 等待用户输入
})
</script>

<style scoped lang="scss">
.sample-tracking-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;
  }

  .info-card {
    margin-bottom: 12px;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .merge-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }

  .timeline-card {
    h4 {
      margin: 0 0 4px 0;
      font-size: 14px;
      color: #303133;
    }

    p {
      margin: 0;
      font-size: 13px;
      color: #606266;
    }

    .node-time {
      font-weight: 500;
    }

    .node-person {
      color: #1a73e8;
      font-size: 12px;
    }

    .muted {
      color: #c0c4cc;
    }
  }
}
</style>
