<template>
  <div class="sign-off-page">
    <!-- 顶部扫码区 -->
    <div class="top-bar">
      <div class="scan-area">
        <el-input
          ref="scanInputRef"
          v-model="scanBarcode"
          placeholder="扫描标本条码 - 首次扫码加载信息, 再次扫码签收"
          clearable
          size="large"
          style="width: 480px"
          @keyup.enter="handleScan"
        >
          <template #prefix>
            <el-icon :size="18" :color="currentSample ? '#67c23a' : '#1a73e8'">
              <View />
            </el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" @click="handleScan">
          <el-icon><VideoPlay /></el-icon>&nbsp;确认
        </el-button>
        <el-divider direction="vertical" />
        <el-tag v-if="currentSample" type="warning" effect="dark" size="large">
          待签收: {{ currentSample.sampleBarcode }}
        </el-tag>
        <el-tag v-else type="info" effect="plain" size="large">请扫描标本条码</el-tag>
      </div>
      <div class="top-right">
        <span class="summary-item">签收人:</span>
        <el-tag type="info" effect="plain" size="default">{{ userStore.realName || userStore.username || '当前用户' }}</el-tag>
        <el-button type="danger" :icon="CircleClose" :disabled="!currentSample || (currentSample.status !== 2 && currentSample.status !== 3)" @click="openRejectDialog">拒收标本</el-button>
        <el-button :icon="Refresh" @click="loadHistory">刷新</el-button>
      </div>
    </div>

    <!-- 状态提示条 (扫码即时视觉反馈) -->
    <div class="status-banner" :class="[bannerClass, bannerFlashClass]">
      <el-icon v-if="bannerType === 'success'"><CircleCheckFilled /></el-icon>
      <el-icon v-else-if="bannerType === 'error'"><CircleCloseFilled /></el-icon>
      <el-icon v-else-if="bannerType === 'warning'"><WarningFilled /></el-icon>
      <el-icon v-else><InfoFilled /></el-icon>
      <span>{{ bannerText }}</span>
    </div>

    <!-- 急诊优先看板 -->
    <div v-if="urgentPendingList.length > 0" class="urgent-board">
      <div class="urgent-board-header">
        <el-icon color="#f56c6c"><AlarmClock /></el-icon>
        <span class="urgent-board-title">急诊标本优先处理</span>
        <el-tag type="danger" size="small" effect="dark">{{ urgentPendingList.length }}</el-tag>
      </div>
      <div class="urgent-board-list">
        <div
          v-for="u in urgentPendingList"
          :key="u.sampleBarcode"
          class="urgent-board-item"
          :class="{ 'urgent-overdue': u.overdue }"
          @click="scanUrgent(u.sampleBarcode)"
        >
          <span class="urgent-barcode">{{ u.sampleBarcode }}</span>
          <span class="urgent-name">{{ u.patientName }}</span>
          <span class="urgent-time">{{ u.waitText }}</span>
          <el-button size="small" type="danger" link @click.stop="scanUrgent(u.sampleBarcode)">立即处理</el-button>
        </div>
      </div>
    </div>

    <div class="main-content">
      <!-- 左侧: 当前扫码加载的标本信息卡片 -->
      <div class="left-panel">
        <div class="panel-header">
          <span class="panel-title">
            <el-icon><Document /></el-icon>&nbsp;当前标本信息
          </span>
          <el-tag v-if="currentSample" type="success" size="small">已加载</el-tag>
          <el-tag v-else type="info" size="small">空</el-tag>
        </div>

        <!-- 空状态 -->
        <div v-if="!currentSample" class="empty-state">
          <el-empty description="请扫描标本条码加载患者信息" :image-size="80">
            <template #description>
              <p style="color: #909399; font-size: 13px">
                首次扫码: 加载标本与患者信息<br />
                再次扫码: 确认并完成签收
              </p>
            </template>
          </el-empty>
        </div>

        <!-- 标本信息卡片 -->
        <div v-else class="sample-detail">
          <!-- 患者信息头部 -->
          <div class="patient-header">
            <el-avatar :size="56" :style="{ background: currentSample.patientGender === '男' ? '#1a73e8' : (currentSample.patientGender === '女' ? '#f56c6c' : '#909399') }">
              {{ currentSample.patientName?.charAt(0) }}
            </el-avatar>
            <div class="patient-main">
              <div class="patient-name-row">
                <span class="patient-name">{{ currentSample.patientName }}</span>
                <el-tag v-if="currentSample.patientGender" :type="currentSample.patientGender === '男' ? 'primary' : 'danger'" size="small">
                  {{ currentSample.patientGender }}
                </el-tag>
                <el-tag v-if="currentSample.isUrgent" type="danger" effect="dark" size="small">急诊</el-tag>
              </div>
              <div class="patient-meta">
            <span>就诊号: {{ currentSample.visitNo || '-' }}</span>
            <span>科室: {{ currentSample.department || '-' }}</span>
          </div>
          <div class="patient-meta" v-if="currentSample.orderItems?.length">
            <span>检验项目: {{ currentSample.orderItems.map(i => i.testItemName).join('、') }}</span>
          </div>
            </div>
          </div>

          <el-divider style="margin: 12px 0" />

          <!-- 标本信息 -->
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">条码号:</span>
              <span class="info-value barcode-value">{{ currentSample.sampleBarcode }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">标本类型:</span>
              <span class="info-value">{{ currentSample.sampleType || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">容器:</span>
              <span class="info-value">{{ currentSample.containerType || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">送检人:</span>
              <span class="info-value">{{ currentSample.deliverPerson || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">送达时间:</span>
              <span class="info-value">{{ formatTime(currentSample.deliverTime) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">当前状态:</span>
              <el-tag v-if="currentSample.status === 9" type="danger" size="small">已拒收</el-tag>
              <el-tag v-else-if="currentSample.status === 3" type="success" size="small">已签收</el-tag>
              <el-tag v-else-if="currentSample.status === 2" type="warning" size="small">待签收</el-tag>
              <el-tag v-else type="info" size="small">{{ currentSample.statusText || '未送检' }}</el-tag>
            </div>
          </div>

          <el-divider style="margin: 12px 0" />

          <!-- 检验项目 -->
          <div class="items-section">
            <div class="section-label">检验项目 ({{ currentSample.orderItems?.length || 0 }}项)</div>
            <div class="items-list">
              <el-tag
                v-for="item in (currentSample.orderItems || [])"
                :key="item.id"
                size="default"
                effect="plain"
                style="margin: 3px"
              >
                {{ item.testItemName }}
              </el-tag>
              <span v-if="!currentSample.orderItems?.length" style="color: #909399; font-size: 12px">无项目信息</span>
            </div>
          </div>

          <el-divider style="margin: 12px 0" />

          <!-- 操作提示 -->
          <div class="action-hint">
            <el-alert
              v-if="currentSample.status === 2"
              type="warning"
              :closable="false"
              show-icon
            >
              <template #title>
                再次扫描该条码或点击下方按钮完成签收
              </template>
            </el-alert>
            <el-alert
              v-else-if="currentSample.status === 3"
              type="success"
              :closable="false"
              show-icon
            >
              <template #title>该标本已签收</template>
            </el-alert>
            <el-alert
              v-else
              type="error"
              :closable="false"
              show-icon
            >
              <template #title>该标本尚未送检, 无法签收</template>
            </el-alert>
          </div>

          <!-- 签收按钮 -->
          <div class="action-buttons" v-if="currentSample && currentSample.status === 2">
            <el-button type="success" size="large" :icon="Check" @click="confirmSignOff" style="width: 100%">
              确认签收 ({{ currentSample.sampleBarcode }})
            </el-button>
            <el-button size="default" @click="cancelCurrent">取消</el-button>
          </div>
          <div class="action-buttons" v-else-if="currentSample">
            <el-button size="default" @click="cancelCurrent" style="width: 100%">清空, 扫下一个</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧: 今日签收记录 -->
      <div class="right-panel">
        <div class="right-header">
          <div class="header-left">
            <el-radio-group v-model="activeTab" size="default" @change="loadHistory">
              <el-radio-button :value="0">待签收 ({{ pendingCount }})</el-radio-button>
              <el-radio-button :value="1">已签收 ({{ signedCount }})</el-radio-button>
              <el-radio-button :value="2">已拒收 ({{ rejectedCount }})</el-radio-button>
            </el-radio-group>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 220px; margin-left: 8px"
              @change="loadHistory"
            />
          </div>
          <div class="header-right">
            <span class="summary-item">急诊: <b class="text-red">{{ urgentCount }}</b></span>
            <el-button v-if="activeTab === 0" type="success" :icon="Check" :disabled="selectedRows.length === 0" @click="batchSignOff">批量签收 ({{ selectedRows.length }})</el-button>
            <el-button v-if="activeTab === 1" type="warning" :icon="RefreshLeft" :disabled="selectedRows.length === 0" @click="batchCancelSignOff">批量取消签收 ({{ selectedRows.length }})</el-button>
            <el-button :icon="Printer" :disabled="historyData.length === 0" @click="openPrint">打印签收单</el-button>
          </div>
        </div>

        <div class="table-wrapper">
          <el-table
            v-loading="loading"
            :data="historyData"
            border
            stripe
            height="100%"
            size="small"
            :row-class-name="rowClassName"
            @selection-change="handleSelectionChange"
          >
            <el-table-column v-if="activeTab === 0 || activeTab === 1" type="selection" width="40" :selectable="(row: any) => (activeTab === 0 ? row.status === 2 : row.status === 3)" />
            <el-table-column prop="sampleBarcode" label="标本条码" width="160" fixed />
            <el-table-column prop="patientName" label="姓名" width="80" fixed />
            <el-table-column prop="visitNo" label="就诊号" width="140" show-overflow-tooltip />
            <el-table-column label="检验项目" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.orderItems?.map((i: any) => i.testItemName).join('、') || row.sampleType || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="急诊" width="55" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isUrgent" type="danger" size="small">急</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="department" label="科室" width="100" show-overflow-tooltip />
            <el-table-column prop="deliverPerson" label="送检人" width="80" />
            <el-table-column prop="deliverTime" label="送达时间" width="150">
              <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
            </el-table-column>
            <el-table-column prop="signOffPerson" label="签收人" width="80" v-if="activeTab === 1" />
            <el-table-column prop="signOffTime" label="签收时间" width="150" v-if="activeTab === 1">
              <template #default="{ row }">{{ formatTime(row.signOffTime) }}</template>
            </el-table-column>
            <el-table-column prop="rejectType" label="不合格类型" width="100" show-overflow-tooltip v-if="activeTab === 2" />
            <el-table-column prop="rejectPerson" label="拒收人" width="80" v-if="activeTab === 2" />
            <el-table-column prop="rejectReason" label="拒收原因" min-width="120" show-overflow-tooltip v-if="activeTab === 2" />
            <el-table-column prop="rejectTime" label="拒收时间" width="150" v-if="activeTab === 2">
              <template #default="{ row }">{{ formatTime(row.rejectTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-tag v-if="row.status === 9" type="danger" size="small">已拒收</el-tag>
                <el-tag v-else-if="row.status === 3" type="success" size="small">已签收</el-tag>
                <el-tag v-else type="warning" size="small">待签收</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="activeTab === 1" label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 3" type="warning" link size="small" @click="cancelSignOffRow(row)">取消签收</el-button>
                <span v-else class="text-gray">-</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :description="activeTab === 0 ? '暂无待签收标本 (需先送检)' : (activeTab === 1 ? '今日暂无签收记录' : '今日暂无拒收记录')" />
            </template>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 签收单打印预览 -->
    <el-dialog v-model="printVisible" title="签收单打印预览" width="800px" top="5vh" :close-on-click-modal="false">
      <div class="print-format-bar">
        <span class="format-label">显示字段:</span>
        <el-checkbox-group v-model="printFields" size="small">
          <el-checkbox value="sampleBarcode">条码号</el-checkbox>
          <el-checkbox value="patientName">姓名</el-checkbox>
          <el-checkbox value="visitNo">就诊号</el-checkbox>
          <el-checkbox value="testItems">检验项目</el-checkbox>
          <el-checkbox value="deliverTime">送达时间</el-checkbox>
          <el-checkbox value="signOffTime">签收时间</el-checkbox>
          <el-checkbox value="signOffPerson">签收人</el-checkbox>
        </el-checkbox-group>
        <el-button type="primary" size="small" :icon="Printer" @click="doPrint">打印</el-button>
      </div>
      <div class="print-area" id="signoffPrintArea">
        <div class="print-doc">
          <div class="print-title">标本签收单</div>
          <div class="print-meta">
            <span>签收人: {{ printSignOffPerson }}</span>
            <span>签收时间: {{ printSignOffTime }}</span>
            <span>标本数: {{ printData.length }}</span>
          </div>
          <table class="print-table">
            <thead>
              <tr>
                <th>序号</th>
                <th v-if="printFields.includes('sampleBarcode')">条码号</th>
                <th v-if="printFields.includes('patientName')">姓名</th>
                <th v-if="printFields.includes('visitNo')">就诊号</th>
                <th v-if="printFields.includes('testItems')">检验项目</th>
                <th v-if="printFields.includes('deliverTime')">送达时间</th>
                <th v-if="printFields.includes('signOffTime')">签收时间</th>
                <th v-if="printFields.includes('signOffPerson')">签收人</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in printData" :key="idx">
                <td>{{ idx + 1 }}</td>
                <td v-if="printFields.includes('sampleBarcode')">{{ row.sampleBarcode }}</td>
                <td v-if="printFields.includes('patientName')">{{ row.patientName }}</td>
                <td v-if="printFields.includes('visitNo')">{{ row.visitNo || '-' }}</td>
                <td v-if="printFields.includes('testItems')">{{ row.orderItems?.map((i: any) => i.testItemName).join('、') || row.sampleType || '-' }}</td>
                <td v-if="printFields.includes('deliverTime')">{{ formatTime(row.deliverTime) }}</td>
                <td v-if="printFields.includes('signOffTime')">{{ formatTime(row.signOffTime) }}</td>
                <td v-if="printFields.includes('signOffPerson')">{{ row.signOffPerson || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <div class="print-footer">
            <span>送检人: ___________</span>
            <span>签收人: ___________</span>
            <span>共 {{ printData.length }} 份标本</span>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 拒收标本弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="拒收标本 - 选择不合格类型" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
        <template #title>
          拒收后标本状态将变为"已拒收", 不可继续后续流程。请确认操作。
        </template>
      </el-alert>
      <el-form :model="rejectForm" label-width="90px" size="default">
        <el-form-item label="标本条码:">
          <el-input :model-value="rejectForm.barcode" disabled />
        </el-form-item>
        <el-form-item label="不合格类型:" required>
          <el-select
            v-model="rejectForm.rejectType"
            placeholder="请选择不合格类型"
            style="width: 100%"
            size="default"
            filterable
          >
            <el-option
              v-for="t in rejectTypeOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="拒收原因:">
          <el-input
            v-model="rejectForm.rejectReason"
            type="textarea"
            :rows="3"
            placeholder="请输入拒收原因(可选)"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="拒收人:">
          <el-tag type="info" effect="plain" size="default">{{ userStore.realName || userStore.username || '当前用户' }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" :disabled="!rejectForm.rejectType" @click="confirmReject">
          确认拒收
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  View, VideoPlay, Refresh, Check, Printer, Document,
  CircleCheckFilled, CircleCloseFilled, WarningFilled, InfoFilled, CircleClose, RefreshLeft, AlarmClock
} from '@element-plus/icons-vue'
import {
  getSamplePage, getSampleByBarcode, signOffSamples, cancelSignOffSamples, rejectSample, SAMPLE_REJECT_TYPES,
  type SampleVO, type SampleQuery, type SampleRejectDTO
} from '@/api/sample'
import { useUserStore } from '@/stores/user'
import { openPrintWindow } from '@/utils/print'
import { sanitizeHtml } from '@/utils/sanitize'

const scanInputRef = ref<any>(null)
const scanBarcode = ref('')
const userStore = useUserStore()
const currentSample = ref<SampleVO | null>(null)
const activeTab = ref(0) // 0=待签收 1=已签收 2=已拒收
const loading = ref(false)
const historyData = ref<SampleVO[]>([])
const selectedRows = ref<SampleVO[]>([])

/** 返回当天日期字符串 YYYY-MM-DD */
function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

// 默认显示当天, 数据按所选日期查询
const dateRange = ref<string[]>([todayStr(), todayStr()])

// 状态提示条 (扫码即时反馈)
const bannerText = ref('请扫描标本条码开始签收')
const bannerType = ref<'info' | 'success' | 'error' | 'warning'>('info')
const bannerClass = computed(() => `banner-${bannerType.value}`)
// 视觉反馈: 扫码后状态条短暂闪烁颜色, 让操作员快速感知成败
const bannerFlash = ref<'none' | 'flash-success' | 'flash-error' | 'flash-warning'>('none')
const bannerFlashClass = computed(() => bannerFlash.value)
let bannerFlashTimer: any = null

/** 扫码听觉反馈: 成功单声"滴", 失败双声"嘟嘟" (Web Audio 生成, 无需音频文件) */
function playScanSound(type: 'success' | 'error') {
  try {
    const Ctx = window.AudioContext || (window as any).webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const now = ctx.currentTime
    const beep = (freq: number, start: number, dur: number, vol = 0.15) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(vol, start)
      gain.gain.exponentialRampToValueAtTime(0.001, start + dur)
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start(start)
      osc.stop(start + dur)
    }
    if (type === 'success') {
      beep(880, now, 0.12)
    } else {
      beep(330, now, 0.15)
      beep(330, now + 0.18, 0.15)
    }
    setTimeout(() => ctx.close(), 800)
  } catch { /* 音频不可用时静默忽略 */ }
}

/** 触发视觉闪烁反馈 */
function triggerFlash(type: 'success' | 'error' | 'warning') {
  bannerFlash.value = type === 'success' ? 'flash-success' : type === 'error' ? 'flash-error' : 'flash-warning'
  if (bannerFlashTimer) clearTimeout(bannerFlashTimer)
  bannerFlashTimer = setTimeout(() => { bannerFlash.value = 'none' }, 900)
}

// 打印相关
const printVisible = ref(false)
const printData = ref<SampleVO[]>([])
const printFields = ref(['sampleBarcode', 'patientName', 'visitNo', 'testItems', 'signOffTime', 'signOffPerson'])

/** 打印单签收人: 优先取所选标本实际签收记录, 未签收时回退当前登录用户 */
const printSignOffPerson = computed(() => {
  if (printData.value.length === 0) return '-'
  const first = printData.value[0]
  if (first.signOffPerson) return first.signOffPerson
  return userStore.realName || userStore.username || '-'
})

/** 打印单签收时间: 优先取所选标本实际签收时间, 未签收时取当前时间 */
const printSignOffTime = computed(() => {
  if (printData.value.length === 0) return '-'
  const first = printData.value[0]
  if (first.signOffTime) return formatTime(first.signOffTime)
  return new Date().toLocaleString('zh-CN', { hour12: false })
})

const pendingCount = computed(() => activeTab.value === 0 ? historyData.value.length : 0)
const signedCount = computed(() => activeTab.value === 1 ? historyData.value.length : 0)
const rejectedCount = computed(() => activeTab.value === 2 ? historyData.value.length : 0)
const urgentCount = computed(() => historyData.value.filter(s => s.isUrgent).length)

/** 急诊优先看板: 待签收的急诊标本, 计算等待时长, 超30分钟标红闪烁 */
const urgentPendingList = computed(() => {
  const now = Date.now()
  return historyData.value
    .filter(s => s.isUrgent && s.status === 2)
    .map(s => {
      const ts = s.deliverTime ? new Date(s.deliverTime).getTime() : (s.collectTime ? new Date(s.collectTime).getTime() : now)
      const waitMin = Math.max(0, Math.floor((now - ts) / 60000))
      return {
        ...s,
        overdue: waitMin > 30,
        waitText: waitMin < 1 ? '刚刚' : `${waitMin} 分钟`
      }
    })
    .sort((a, b) => new Date(a.deliverTime || a.collectTime).getTime() - new Date(b.deliverTime || b.collectTime).getTime())
})

/** 点击急诊看板立即定位并扫描该标本 */
function scanUrgent(barcode: string) {
  scanBarcode.value = barcode
  handleScan()
}

function formatTime(time?: string) {
  if (!time) return '-'
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

function setBanner(text: string, type: 'info' | 'success' | 'error' | 'warning' = 'info') {
  bannerText.value = text
  bannerType.value = type
  // 扫码即时反馈: 成功/失败/警告触发视觉闪烁 + 听觉提示
  if (type === 'success') {
    triggerFlash('success')
    playScanSound('success')
  } else if (type === 'error') {
    triggerFlash('error')
    playScanSound('error')
  } else if (type === 'warning') {
    triggerFlash('warning')
  }
}

function focusScanInput() {
  nextTick(() => {
    scanInputRef.value?.focus?.()
  })
}

/** 加载右侧历史列表 */
function loadHistory() {
  loading.value = true
  const params: SampleQuery = {
    current: 1,
    size: 200
  }
  // 0=待签收(status=2), 1=已签收(status=3), 2=已拒收(status=9)
  params.status = activeTab.value === 0 ? 2 : (activeTab.value === 1 ? 3 : 9)
  // 日期范围查询 (已签收用签收时间, 待签收用采集时间)
  if (dateRange.value && dateRange.value.length === 2) {
    if (activeTab.value === 1) {
      // 已签收: 按签收时间过滤
      ;(params as any).signOffStartDate = dateRange.value[0]
      ;(params as any).signOffEndDate = dateRange.value[1]
    } else {
      // 待签收/已拒收: 按采集时间过滤
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
  }
  getSamplePage(params).then(res => {
    historyData.value = res.records || []
  }).catch(() => {
    historyData.value = []
  }).finally(() => {
    loading.value = false
  })
}

/** 多选变更 */
function handleSelectionChange(rows: SampleVO[]) {
  selectedRows.value = rows
}

/** 批量签收 */
async function batchSignOff() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请选择待签收的标本')
    return
  }
  try {
    const barcodes = selectedRows.value.map(r => r.sampleBarcode)
    await signOffSamples({ barcodes })
    const signTime = new Date().toLocaleString('zh-CN', { hour12: false })
    setBanner(`批量签收成功: ${barcodes.length} 个标本 / ${userStore.realName || userStore.username} / ${signTime}`, 'success')
    selectedRows.value = []
    loadHistory()
  } catch (e: any) {
    // 错误已由拦截器提示
  }
}

/** 批量取消签收 */
async function batchCancelSignOff() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请选择已签收的标本')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认取消 ${selectedRows.value.length} 个标本的签收? 取消后状态将回退到"已送检"`,
      '确认取消签收',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const barcodes = selectedRows.value.map(r => r.sampleBarcode)
    await cancelSignOffSamples({ barcodes })
    const signTime = new Date().toLocaleString('zh-CN', { hour12: false })
    setBanner(`批量取消签收成功: ${barcodes.length} 个标本 / ${userStore.realName || userStore.username} / ${signTime}`, 'success')
    selectedRows.value = []
    loadHistory()
  } catch (e: any) {
    // 错误已由拦截器提示
  }
}

/** 单行取消签收 */
async function cancelSignOffRow(row: SampleVO) {
  try {
    await ElMessageBox.confirm(
      `确认取消标本 ${row.sampleBarcode} 的签收? 取消后状态将回退到"已送检"`,
      '确认取消签收',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await cancelSignOffSamples({ barcodes: [row.sampleBarcode] })
    // 清除当前标本缓存(避免重新扫码时使用过期数据)
    if (currentSample.value?.sampleBarcode === row.sampleBarcode) {
      currentSample.value = null
    }
    const signTime = new Date().toLocaleString('zh-CN', { hour12: false })
    setBanner(`取消签收成功: ${row.sampleBarcode} / ${userStore.realName || userStore.username} / ${signTime}`, 'success')
    loadHistory()
  } catch (e: any) {
    // 错误已由拦截器提示
  }
}

/** 扫码处理: 首次扫码加载信息, 再次扫同一标本条码签收 */
async function handleScan() {
  if (!scanBarcode.value) {
    setBanner('请输入或扫描标本条码', 'warning')
    return
  }
  const barcode = scanBarcode.value.trim()
  scanBarcode.value = ''
  focusScanInput()

  // 情况1: 当前已加载标本, 且本次扫码与当前标本条码一致 -> 重新查询最新状态后签收
  //   (避免 currentSample 缓存过期: 取消签收后 currentSample.status 仍为3, 需重新查询)
  if (currentSample.value && currentSample.value.sampleBarcode === barcode) {
    try {
      const fresh = await getSampleByBarcode(barcode)
      if (!fresh) {
        setBanner(`未找到条码 ${barcode} 对应的标本`, 'error')
        currentSample.value = null
        return
      }
      currentSample.value = fresh
      await doSignOff(fresh)
    } catch {
      setBanner(`查询条码 ${barcode} 失败`, 'error')
      currentSample.value = null
    }
    return
  }

  // 情况2: 查询条码对应的标本信息
  try {
    const sample = await getSampleByBarcode(barcode)
    if (!sample) {
      setBanner(`未找到条码 ${barcode} 对应的标本`, 'error')
      currentSample.value = null
      return
    }
    currentSample.value = sample
    // 根据状态显示不同提示
    if (sample.status === 3) {
      setBanner(`条码 ${barcode} 已签收, 请扫描其他标本`, 'success')
    } else if (sample.status !== 2) {
      setBanner(`条码 ${barcode} 尚未送检, 无法签收`, 'error')
    } else {
      setBanner(`已加载 ${sample.patientName} 的标本, 再次扫码确认签收`, 'warning')
    }
  } catch {
    setBanner(`查询条码 ${barcode} 失败`, 'error')
    currentSample.value = null
  }
}

/** 执行签收 */
async function doSignOff(sample: SampleVO) {
  if (!sample || sample.status === 3) {
    setBanner('该标本已签收', 'info')
    return
  }
  if (sample.status !== 2) {
    setBanner('该标本尚未送检, 无法签收', 'error')
    return
  }
  try {
    await signOffSamples({ barcodes: [sample.sampleBarcode] })
    const signTime = new Date().toLocaleString('zh-CN', { hour12: false })
    setBanner(`签收成功: ${sample.patientName} / ${sample.sampleBarcode} / 签收人:${userStore.realName || userStore.username} / ${signTime}`, 'success')
    currentSample.value = null
    loadHistory()
  } catch {
    setBanner(`签收失败: ${sample.sampleBarcode}`, 'error')
  }
}

/** 点击按钮确认签收 */
async function confirmSignOff() {
  if (currentSample.value) {
    await doSignOff(currentSample.value)
  }
}

/** 取消当前标本 */
function cancelCurrent() {
  currentSample.value = null
  setBanner('请扫描标本条码开始签收', 'info')
  focusScanInput()
}

/** 高亮当前标本所在行 */
function rowClassName({ row }: { row: SampleVO }) {
  if (currentSample.value && row.sampleBarcode === currentSample.value.sampleBarcode) {
    return 'current-row-highlight'
  }
  return ''
}

/** 打开打印预览 */
function openPrint() {
  printData.value = [...historyData.value]
  printVisible.value = true
}

/** 执行浏览器打印 */
function doPrint() {
  const printArea = document.getElementById('signoffPrintArea')
  if (!printArea) return
  // 仅对动态内容消毒, 模板 (含 <style>) 由 openPrintWindow 原样写入
  const safeHtml = sanitizeHtml(printArea.innerHTML)
  const win = openPrintWindow(`
    <html><head><title>标本签收单</title>
    <style>
      body { font-family: 'SimSun', serif; padding: 20px; }
      .print-title { text-align: center; font-size: 20px; font-weight: bold; margin-bottom: 10px; }
      .print-meta { display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 10px; color: #666; }
      table { width: 100%; border-collapse: collapse; font-size: 12px; }
      th, td { border: 1px solid #333; padding: 4px 6px; text-align: center; }
      th { background: #f0f0f0; }
      .print-footer { display: flex; justify-content: space-between; margin-top: 20px; font-size: 13px; }
      @media print { body { padding: 0; } }
    </style></head><body>${safeHtml}</body></html>
  `, { width: 900, height: 700 })
  if (!win) {
    ElMessage.warning('浏览器阻止了弹窗，请允许后重试')
  }
}

// ========== 标本拒收 ==========
const rejectDialogVisible = ref(false)
const rejecting = ref(false)
const rejectTypeOptions = SAMPLE_REJECT_TYPES
const rejectForm = ref<SampleRejectDTO>({
  barcode: '',
  rejectType: '',
  rejectReason: '',
  operator: ''
})

/** 打开拒收弹窗 */
function openRejectDialog() {
  if (!currentSample.value) {
    ElMessage.warning('请先扫描标本条码加载标本信息')
    return
  }
  if (currentSample.value.status === 3) {
    ElMessage.warning('该标本已签收, 不可拒收')
    return
  }
  if (currentSample.value.status !== 2) {
    ElMessage.warning('该标本尚未送检, 不可拒收')
    return
  }
  rejectForm.value = {
    barcode: currentSample.value.sampleBarcode,
    rejectType: '',
    rejectReason: '',
    operator: ''
  }
  rejectDialogVisible.value = true
}

/** 确认拒收 */
async function confirmReject() {
  if (!rejectForm.value.barcode) {
    ElMessage.warning('标本条码不能为空')
    return
  }
  if (!rejectForm.value.rejectType) {
    ElMessage.warning('请选择不合格类型')
    return
  }
  rejecting.value = true
  try {
    await rejectSample({ ...rejectForm.value })
    const signTime = new Date().toLocaleString('zh-CN', { hour12: false })
    setBanner(`拒收成功: ${currentSample.value?.patientName} / ${rejectForm.value.barcode} / ${rejectForm.value.rejectType} / ${signTime}`, 'success')
    currentSample.value = null
    rejectDialogVisible.value = false
    loadHistory()
  } catch (e: any) {
    // 错误已由拦截器提示
  } finally {
    rejecting.value = false
  }
}

onMounted(() => {
  loadHistory()
  focusScanInput()
})
</script>

<style scoped lang="scss">
.sign-off-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  padding: 8px;
  overflow: hidden;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.scan-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-item {
  font-size: 13px;
  color: #606266;
}

.text-green { color: #67c23a; }
.text-red { color: #f56c6c; }
.text-orange { color: #e6a23c; }

.status-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 6px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s;

  .el-icon { font-size: 16px; }

  &.banner-info {
    background: #ecf5ff;
    color: #1a73e8;
    border: 1px solid #d9ecff;
  }
  &.banner-success {
    background: #f0f9eb;
    color: #67c23a;
    border: 1px solid #e1f3d8;
  }
  &.banner-warning {
    background: #fdf6ec;
    color: #e6a23c;
    border: 1px solid #faecd8;
  }
  &.banner-error {
    background: #fef0f0;
    color: #f56c6c;
    border: 1px solid #fde2e2;
  }

  // 扫码即时视觉反馈: 成功/失败/警告闪烁
  &.flash-success { animation: flashSuccess 0.9s ease; }
  &.flash-error { animation: flashError 0.9s ease; }
  &.flash-warning { animation: flashWarning 0.9s ease; }
}

@keyframes flashSuccess {
  0%, 100% { background: #f0f9eb; color: #67c23a; border-color: #e1f3d8; }
  30% { background: #67c23a; color: #fff; border-color: #67c23a; }
  70% { background: #f0f9eb; color: #67c23a; border-color: #e1f3d8; }
}

@keyframes flashError {
  0%, 100% { background: #fef0f0; color: #f56c6c; border-color: #fde2e2; }
  30% { background: #f56c6c; color: #fff; border-color: #f56c6c; }
  70% { background: #fef0f0; color: #f56c6c; border-color: #fde2e2; }
}

@keyframes flashWarning {
  0%, 100% { background: #fdf6ec; color: #e6a23c; border-color: #faecd8; }
  30% { background: #e6a23c; color: #fff; border-color: #e6a23c; }
  70% { background: #fdf6ec; color: #e6a23c; border-color: #faecd8; }
}

/* 急诊优先看板 */
.urgent-board {
  background: #fff;
  border-radius: 6px;
  margin-bottom: 8px;
  padding: 8px 12px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  border: 1px solid #fde2e2;

  .urgent-board-header {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;

    .urgent-board-title {
      font-size: 13px;
      font-weight: 600;
      color: #f56c6c;
    }
  }

  .urgent-board-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .urgent-board-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 10px;
    background: #fef0f0;
    border: 1px solid #fde2e2;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;

    &:hover { background: #fde2e2; }

    .urgent-barcode {
      font-family: 'Consolas', monospace;
      font-weight: 600;
      color: #f56c6c;
    }
    .urgent-name { color: #303133; }
    .urgent-time { color: #909399; }

    &.urgent-overdue {
      background: #f56c6c;
      border-color: #f56c6c;
      animation: urgentBlink 1s infinite;

      .urgent-barcode, .urgent-name, .urgent-time, .el-button { color: #fff; }
    }
  }
}

@keyframes urgentBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.main-content {
  flex: 1;
  display: flex;
  gap: 8px;
  overflow: hidden;
}

.left-panel {
  width: 460px;
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sample-detail {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.patient-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.patient-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.patient-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.patient-name {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
}

.patient-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-label {
  font-size: 12px;
  color: #909399;
}

.info-value {
  font-size: 13px;
  color: #303133;
}

.barcode-value {
  font-family: 'Consolas', monospace;
  font-weight: bold;
  color: #1a73e8;
}

.items-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.items-list {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.action-hint {
  margin-bottom: 12px;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.right-panel {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.right-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;

  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
  padding: 8px;
}

:deep(.current-row-highlight) {
  background-color: #ecf5ff !important;
}

.print-format-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  flex-wrap: wrap;

  .format-label {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }

  .el-checkbox-group {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
}

.print-area {
  max-height: 60vh;
  overflow-y: auto;
}

.print-doc {
  font-family: 'SimSun', serif;
  padding: 10px;

  .print-title {
    text-align: center;
    font-size: 20px;
    font-weight: bold;
    margin-bottom: 10px;
  }

  .print-meta {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    margin-bottom: 10px;
    color: #666;
  }

  .print-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;

    th, td {
      border: 1px solid #333;
      padding: 4px 6px;
      text-align: center;
    }

    th {
      background: #f0f0f0;
    }
  }

  .print-footer {
    display: flex;
    justify-content: space-between;
    margin-top: 20px;
    font-size: 13px;
  }
}
</style>
