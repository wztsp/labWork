<template>
  <div class="deliver-page">
    <div class="top-bar">
      <div class="filter-area">
        <el-form :inline="true" :model="searchForm" size="default">
          <el-form-item label="关键字">
            <el-input v-model="searchForm.keyword" placeholder="条码号/患者/就诊号" clearable style="width: 220px" @keyup.enter="loadData" />
          </el-form-item>
          <el-form-item label="科室">
            <el-input v-model="searchForm.department" placeholder="科室" clearable style="width: 140px" @keyup.enter="loadData" />
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="loadData"
            />
          </el-form-item>
          <el-form-item label="标签页">
            <el-radio-group v-model="activeTab" @change="loadData">
              <el-radio-button :value="0">待送检</el-radio-button>
              <el-radio-button :value="1">已送检</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
            <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 扫码送检条 -->
    <div class="scan-deliver-bar">
      <el-icon :size="18" color="#67c23a"><Promotion /></el-icon>
      <span class="scan-label">扫码送检:</span>
      <el-radio-group v-model="scanMode" size="small" class="scan-mode">
        <el-radio-button :value="'single'">单条送检</el-radio-button>
        <el-radio-button :value="'batch'">批量连续扫码</el-radio-button>
      </el-radio-group>
      <el-input
        ref="deliverScanRef"
        v-model="deliverScanBarcode"
        :placeholder="scanMode === 'batch' ? '连续扫描标本条码 (自动加入下方列表)' : '扫描标本条码直接送检'"
        clearable
        size="large"
        :style="{ width: scanMode === 'batch' ? '420px' : '360px' }"
        @keyup.enter="handleScanDeliver"
      />
      <el-button v-if="scanMode === 'single'" type="success" size="large" @click="handleScanDeliver">
        <el-icon><Promotion /></el-icon>&nbsp;送检
      </el-button>
      <el-button v-else type="success" size="large" :disabled="batchList.length === 0" :loading="batchDelivering" @click="confirmBatchDeliver">
        <el-icon><Promotion /></el-icon>&nbsp;确认送检 ({{ batchList.length }})
      </el-button>
      <el-button v-if="scanMode === 'batch'" size="large" :disabled="batchList.length === 0" @click="clearBatch">
        <el-icon><Delete /></el-icon>&nbsp;清除
      </el-button>
      <el-divider direction="vertical" />
      <span class="scan-label">送检人:</span>
      <el-tag type="info" effect="plain" size="default">{{ userStore.realName || userStore.username || '当前用户' }}</el-tag>
      <el-tag v-if="scanDeliverMsg" :type="scanDeliverMsgType" effect="plain" size="default" style="margin-left: 8px">
        {{ scanDeliverMsg }}
      </el-tag>
    </div>

    <!-- 批量扫码列表 -->
    <div v-if="scanMode === 'batch'" class="batch-scan-bar"
      :class="{ 'batch-has-list': batchList.length > 0 }">
      <div class="batch-header">
        <span class="batch-title">
          <el-icon><Collection /></el-icon>&nbsp;已扫描标本 ({{ batchList.length }})
        </span>
        <span class="batch-hint">重复扫描自动忽略，可点击移除</span>
      </div>
      <div class="batch-list">
        <el-tag
          v-for="bc in batchList"
          :key="bc.barcode"
          closable
          size="large"
          :type="bc.status === 'duplicate' ? 'info' : 'success'"
          effect="plain"
          class="batch-item"
          @close="removeFromBatch(bc.barcode)"
        >
          <span v-if="bc.status === 'duplicate'">⚠️ 重复</span>
          <el-icon v-else><CircleCheckFilled /></el-icon>
          <span class="batch-barcode">{{ bc.barcode }}</span>
        </el-tag>
        <span v-if="batchList.length === 0" class="batch-empty">等待扫描... 扫描后自动加入列表</span>
      </div>
    </div>

    <div class="stat-cards">
      <div class="stat-card" :class="{ active: activeTab === 0 }" @click="switchTab(0)">
        <div class="stat-title">待送检</div>
        <div class="stat-value text-orange">{{ pendingCount }}</div>
      </div>
      <div class="stat-card" :class="{ active: activeTab === 1 }" @click="switchTab(1)">
        <div class="stat-title">已送检</div>
        <div class="stat-value text-blue">{{ deliveredCount }}</div>
      </div>
      <div class="stat-card" @click="loadData">
        <div class="stat-title">急诊</div>
        <div class="stat-value text-red">{{ urgentCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">送检人</div>
        <div class="stat-value text-primary" style="font-size:14px">
          {{ userStore.realName || userStore.username || '-' }}
        </div>
      </div>
    </div>

    <div class="table-container">
      <div class="table-header">
        <span class="table-title">
          {{ activeTab === 0 ? '待送检标本 (已采集未送检)' : '已送检标本' }}
        </span>
        <div class="table-actions">
          <el-button v-if="activeTab === 0" type="success" :icon="Promotion" :disabled="selectedRows.length === 0" @click="batchDeliver">
            批量送检 ({{ selectedRows.length }})
          </el-button>
          <el-button v-if="activeTab === 1" type="warning" :icon="RefreshLeft" :disabled="selectedRows.length === 0" @click="batchCancelDeliver">
            批量取消送检 ({{ selectedRows.length }})
          </el-button>
          <el-button :icon="Printer" :disabled="selectedRows.length === 0" @click="printSelected">打印送检单</el-button>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        height="calc(100vh - 380px)"
        size="small"
        @selection-change="handleSelectionChange"
        :row-class-name="rowClassName"
      >
        <el-table-column type="selection" width="40" :selectable="isRowSelectable" />
        <el-table-column prop="sampleBarcode" label="标本条码" width="150" fixed />
        <el-table-column prop="patientName" label="患者姓名" width="90" fixed />
        <el-table-column prop="visitNo" label="就诊号" width="130" show-overflow-tooltip />
        <el-table-column label="检验项目" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.orderItems?.map((i: any) => i.testItemName).join('、') || row.sampleType || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sampleType" label="标本类型" width="90" show-overflow-tooltip />
        <el-table-column prop="containerType" label="容器类型" width="110" show-overflow-tooltip />
        <el-table-column label="急诊" width="50" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isUrgent" type="danger" size="small">急</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="科室" width="100" show-overflow-tooltip />
        <el-table-column prop="collectorName" label="采集人" width="80" />
        <el-table-column prop="collectTime" label="采集时间" width="150">
          <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
        </el-table-column>
        <el-table-column prop="deliverPerson" label="送检人" width="80" v-if="activeTab === 1" />
        <el-table-column prop="deliverTime" label="送检时间" width="150" v-if="activeTab === 1">
          <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" type="success" size="small">已送检</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">待送检</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="activeTab === 0" type="success" link size="small" @click="deliverOne(row)">送检</el-button>
            <el-button v-if="activeTab === 1 && canCancelDeliverRow(row)" type="warning" link size="small" @click="cancelDeliverOne(row)">取消送检</el-button>
            <span v-if="activeTab === 1 && !canCancelDeliverRow(row)" style="font-size:12px;color:#999">不可取消</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="activeTab === 0 ? '暂无待送检标本 (需先完成采集)' : '暂无已送检标本'" />
        </template>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 送检单打印预览 -->
    <el-dialog v-model="printVisible" title="送检单打印预览" width="800px" top="5vh" :close-on-click-modal="false">
      <div class="print-format-bar">
        <span class="format-label">显示字段:</span>
        <el-checkbox-group v-model="printFields" size="small">
          <el-checkbox value="sampleBarcode">条码号</el-checkbox>
          <el-checkbox value="patientName">姓名</el-checkbox>
          <el-checkbox value="visitNo">就诊号</el-checkbox>
          <el-checkbox value="testItems">检验项目</el-checkbox>
          <el-checkbox value="containerType">容器类型</el-checkbox>
          <el-checkbox value="collectTime">采集时间</el-checkbox>
          <el-checkbox value="isUrgent">急诊</el-checkbox>
        </el-checkbox-group>
        <el-button type="primary" size="small" :icon="Printer" @click="doPrint">打印</el-button>
      </div>
      <div class="print-area" id="deliverPrintArea">
        <div class="print-doc">
          <div class="print-title">标本送检单</div>
          <div class="print-meta">
            <span>送检人: {{ printDeliverPerson }}</span>
            <span>送检时间: {{ printDeliverTime }}</span>
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
                <th v-if="printFields.includes('containerType')">容器类型</th>
                <th v-if="printFields.includes('collectTime')">采集时间</th>
                <th v-if="printFields.includes('isUrgent')">急诊</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in printData" :key="idx">
                <td>{{ idx + 1 }}</td>
                <td v-if="printFields.includes('sampleBarcode')">{{ row.sampleBarcode }}</td>
                <td v-if="printFields.includes('patientName')">{{ row.patientName }}</td>
                <td v-if="printFields.includes('visitNo')">{{ row.visitNo || '-' }}</td>
                <td v-if="printFields.includes('testItems')">{{ row.orderItems?.map((i: any) => i.testItemName).join('、') || row.sampleType || '-' }}</td>
                <td v-if="printFields.includes('containerType')">{{ row.containerType || '-' }}</td>
                <td v-if="printFields.includes('collectTime')">{{ formatTime(row.collectTime) }}</td>
                <td v-if="printFields.includes('isUrgent')">{{ row.isUrgent ? '急' : '' }}</td>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Promotion, Printer, RefreshLeft, Delete, Collection, CircleCheckFilled } from '@element-plus/icons-vue'
import { getSamplePage, deliverSamples, cancelDeliverSamples, type SampleVO, type SampleQuery } from '@/api/sample'
import { useUserStore } from '@/stores/user'
import { openPrintWindow } from '@/utils/print'
import { sanitizeHtml } from '@/utils/sanitize'

const loading = ref(false)
const tableData = ref<SampleVO[]>([])
const selectedRows = ref<SampleVO[]>([])
const activeTab = ref(0) // 0=待送检 1=已送检
const userStore = useUserStore()

// 扫码送检相关
const deliverScanRef = ref<any>(null)
const deliverScanBarcode = ref('')
const scanDeliverMsg = ref('')
const scanDeliverMsgType = ref<'info' | 'success' | 'error' | 'warning'>('info')

// 批量连续扫码
const scanMode = ref<'single' | 'batch'>('single')
const batchDelivering = ref(false)
interface BatchItem { barcode: string; status: 'new' | 'duplicate' }
const batchList = ref<BatchItem[]>([])

/** 返回当天日期字符串 YYYY-MM-DD */
function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const searchForm = reactive({
  keyword: '',
  department: '',
  // 默认显示当天, 数据按所选日期查询
  dateRange: [todayStr(), todayStr()] as string[]
})

const pagination = reactive({ current: 1, size: 100, total: 0 })

// 打印相关
const printVisible = ref(false)
const printData = ref<SampleVO[]>([])
const printFields = ref(['sampleBarcode', 'patientName', 'visitNo', 'testItems', 'isUrgent'])

/** 打印单送检人: 优先取所选标本实际送检记录, 未送检时回退当前登录用户 */
const printDeliverPerson = computed(() => {
  if (printData.value.length === 0) return '-'
  const first = printData.value[0]
  if (first.deliverPerson) return first.deliverPerson
  return userStore.realName || userStore.username || '-'
})

/** 打印单送检时间: 优先取所选标本实际送检时间, 未送检时取当前时间 */
const printDeliverTime = computed(() => {
  if (printData.value.length === 0) return '-'
  const first = printData.value[0]
  if (first.deliverTime) return formatTime(first.deliverTime)
  return new Date().toLocaleString('zh-CN', { hour12: false })
})

const pendingCount = computed(() => activeTab.value === 0 ? tableData.value.length : 0)
const deliveredCount = computed(() => activeTab.value === 1 ? tableData.value.length : 0)
const urgentCount = computed(() => tableData.value.filter(s => s.isUrgent).length)

function loadData() {
  loading.value = true
  // 待送检: status=1(已采集), 已送检: status=2(已送检)
  const params: SampleQuery = {
    current: pagination.current,
    size: pagination.size,
    status: activeTab.value === 0 ? 1 : 2
  }
  if (searchForm.keyword) {
    const kw = searchForm.keyword.trim()
    if (/^V\d+$/i.test(kw) || /^MZ\d+/i.test(kw) || /^ZY\d+/i.test(kw)) {
      params.visitNo = kw
    } else if (/^[A-Z]{0,2}\d{6,}$/i.test(kw)) {
      params.sampleBarcode = kw
    } else {
      params.patientName = kw
    }
  }
  if (searchForm.department) params.department = searchForm.department
  // 日期范围查询: 待送检按采集日期, 已送检按送检日期
  if (searchForm.dateRange && searchForm.dateRange.length === 2) {
    if (activeTab.value === 1) {
      params.deliverStartDate = searchForm.dateRange[0]
      params.deliverEndDate = searchForm.dateRange[1]
    } else {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }
  }

  getSamplePage(params).then(res => {
    const all = res.records || []
    tableData.value = all
    pagination.total = res.total || 0
  }).catch(() => {
    tableData.value = []
    pagination.total = 0
  }).finally(() => {
    loading.value = false
  })
}

/** 急查行高亮 */
function rowClassName({ row }: { row: SampleVO }) {
  return row.isUrgent === 1 ? 'urgent-row' : ''
}

/** 扫码送检: 单条直接送检, 批量则加入待送队列 */
async function handleScanDeliver() {
  const barcode = deliverScanBarcode.value.trim()
  if (!barcode) {
    scanDeliverMsg.value = '请输入或扫描标本条码'
    scanDeliverMsgType.value = 'warning'
    return
  }
  deliverScanBarcode.value = ''
  if (scanMode.value === 'batch') {
    addToBatch(barcode)
    return
  }
  try {
    await deliverSamples({ barcodes: [barcode] })
    scanDeliverMsg.value = `送检成功: ${barcode} / 送检人: ${userStore.realName || userStore.username}`
    scanDeliverMsgType.value = 'success'
    loadData()
  } catch (e: any) {
    scanDeliverMsg.value = `送检失败: ${barcode}`
    scanDeliverMsgType.value = 'error'
  }
}

/** 批量扫码: 加入待送队列, 重复扫码自动忽略 */
function addToBatch(barcode: string) {
  if (batchList.value.some(i => i.barcode === barcode)) {
    // 已存在则标记为重复(短暂提示后移除标记)
    const target = batchList.value.find(i => i.barcode === barcode)
    if (target && target.status === 'new') {
      target.status = 'duplicate'
      setTimeout(() => { target.status = 'new' }, 1200)
    }
    scanDeliverMsg.value = `重复扫描已忽略: ${barcode}`
    scanDeliverMsgType.value = 'warning'
    return
  }
  batchList.value.push({ barcode, status: 'new' })
  scanDeliverMsg.value = `已加入 (${batchList.value.length}): ${barcode}`
  scanDeliverMsgType.value = 'success'
}

/** 移除单个待送条码 */
function removeFromBatch(barcode: string) {
  batchList.value = batchList.value.filter(i => i.barcode !== barcode)
}

/** 清空待送队列 */
function clearBatch() {
  batchList.value = []
  scanDeliverMsg.value = '已清空待送列表'
  scanDeliverMsgType.value = 'info'
}

/** 批量连续扫码一键送检 */
async function confirmBatchDeliver() {
  const barcodes = batchList.value.map(i => i.barcode)
  if (barcodes.length === 0) {
    ElMessage.warning('请先扫描待送检的标本')
    return
  }
  batchDelivering.value = true
  try {
    await deliverSamples({ barcodes })
    scanDeliverMsg.value = `批量送检成功: ${barcodes.length} 个标本 / ${userStore.realName || userStore.username}`
    scanDeliverMsgType.value = 'success'
    ElMessage.success(`已送检 ${barcodes.length} 个标本`)
    batchList.value = []
    loadData()
  } catch (e: any) {
    scanDeliverMsg.value = '批量送检失败, 请检查无法送检的条码'
    scanDeliverMsgType.value = 'error'
    ElMessage.error('批量送检失败')
  } finally {
    batchDelivering.value = false
  }
}

function switchTab(tab: number) {
  activeTab.value = tab
  pagination.current = 1
  loadData()
}

function resetSearch() {
  searchForm.keyword = ''
  searchForm.department = ''
  searchForm.dateRange = [todayStr(), todayStr()]
  pagination.current = 1
  loadData()
}

function handleSelectionChange(rows: SampleVO[]) {
  selectedRows.value = rows
}

function formatTime(time?: string) {
  if (!time) return '-'
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

async function deliverOne(row: SampleVO) {
  try {
    await ElMessageBox.confirm(
      `确认送检标本 ${row.sampleBarcode} (${row.patientName})？\n送检人: ${userStore.realName || userStore.username}`,
      '送检确认',
      { type: 'success' }
    )
    await deliverSamples({ barcodes: [row.sampleBarcode] })
    ElMessage.success(`标本 ${row.sampleBarcode} 送检成功`)
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('送检失败')
  }
}

async function batchDeliver() {
  const validRows = selectedRows.value.filter(r => r.status === 1)
  if (validRows.length === 0) {
    ElMessage.warning('请选择待送检的标本')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量送检 ${validRows.length} 个标本？\n送检人: ${userStore.realName || userStore.username}`,
      '批量送检',
      { type: 'success' }
    )
    await deliverSamples({
      barcodes: validRows.map(r => r.sampleBarcode)
    })
    ElMessage.success(`已送检 ${validRows.length} 个标本`)
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('批量送检失败')
  }
}

/** 判断行是否可选: 待送检 tab 全可选, 已送检 tab 仅可选可取消的 */
function isRowSelectable(row: SampleVO) {
  if (activeTab.value === 0) return row.status === 1  // 待送检tab: 已采集可选
  return canCancelDeliverRow(row)  // 已送检tab: 可取消的可选
}

/** 判断已送检行是否可取消: 状态为已送检(status=2) */
function canCancelDeliverRow(row: SampleVO) {
  return row.status === 2
}

/** 单个取消送检 */
async function cancelDeliverOne(row: SampleVO) {
  if (!canCancelDeliverRow(row)) {
    ElMessage.warning('该标本已签收/已核收/已报告, 不可取消送检')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认取消标本 ${row.sampleBarcode} (${row.patientName}) 的送检？\n取消后标本将回到"已采集"状态。`,
      '取消送检确认',
      { type: 'warning' }
    )
    await cancelDeliverSamples({ barcodes: [row.sampleBarcode] })
    ElMessage.success(`标本 ${row.sampleBarcode} 取消送检成功`)
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('取消送检失败')
  }
}

/** 批量取消送检 */
async function batchCancelDeliver() {
  const validRows = selectedRows.value.filter(r => canCancelDeliverRow(r))
  if (validRows.length === 0) {
    ElMessage.warning('请选择可取消送检的标本(需未签收、未核收、未报告)')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量取消 ${validRows.length} 个标本的送检？\n取消后标本将回到"已采集"状态。`,
      '批量取消送检确认',
      { type: 'warning' }
    )
    await cancelDeliverSamples({
      barcodes: validRows.map(r => r.sampleBarcode)
    })
    ElMessage.success(`已取消 ${validRows.length} 个标本的送检`)
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('批量取消送检失败')
  }
}

function printSelected() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择标本')
    return
  }
  printData.value = [...selectedRows.value]
  printVisible.value = true
}

/** 执行浏览器打印 */
function doPrint() {
  const printArea = document.getElementById('deliverPrintArea')
  if (!printArea) return
  // 仅对动态内容消毒, 模板 (含 <style>) 由 openPrintWindow 原样写入
  const safeHtml = sanitizeHtml(printArea.innerHTML)
  const win = openPrintWindow(`
    <html><head><title>标本送检单</title>
    <style>
      body { font-family: 'SimSun', serif; padding: 20px; }
      .print-title { text-align: center; font-size: 20px; font-weight: bold; margin-bottom: 10px; }
      .print-meta { display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 10px; color: #666; flex-wrap: wrap; gap: 6px; }
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

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.deliver-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  padding: 8px;
  overflow: hidden;
}

.top-bar {
  background: #fff;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.scan-deliver-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);

  .scan-label {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    white-space: nowrap;
  }
}

.scan-mode {
  margin-right: 4px;
}

.batch-scan-bar {
  background: #fff;
  border-radius: 6px;
  margin-bottom: 8px;
  padding: 8px 12px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  border: 1px dashed #c0c4cc;

  &.batch-has-list {
    border-color: #67c23a;
    border-style: solid;
  }

  .batch-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;

    .batch-title {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      display: flex;
      align-items: center;
    }

    .batch-hint {
      font-size: 12px;
      color: #909399;
    }
  }

  .batch-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    max-height: 120px;
    overflow-y: auto;
  }

  .batch-item {
    .batch-barcode {
      font-family: 'Consolas', monospace;
      margin-left: 4px;
    }
  }

  .batch-empty {
    font-size: 13px;
    color: #c0c4cc;
    padding: 4px 0;
  }
}

:deep(.urgent-row) {
  background-color: #fef0f0 !important;
}

.stat-cards {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.stat-card {
  flex: 1;
  background: #fff;
  padding: 12px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  text-align: center;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 21, 41, 0.1);
  }

  &.active {
    border-color: #1a73e8;
    background: #ecf5ff;
  }
}

.stat-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.text-blue { color: #1a73e8; }
.text-green { color: #67c23a; }
.text-orange { color: #e6a23c; }
.text-red { color: #f56c6c; }
.text-primary { color: #1a73e8; }

.table-container {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #ebeef5;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.table-actions {
  display: flex;
  gap: 8px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid #ebeef5;
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
    flex-wrap: wrap;
    gap: 6px;
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
