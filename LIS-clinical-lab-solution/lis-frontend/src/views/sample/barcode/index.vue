<template>
  <div class="collect-page">
    <div class="top-bar">
      <div class="top-left">
        <el-input
          v-model="searchText"
          placeholder="请输入就诊号/申请单号/患者姓名回车搜索"
          clearable
          style="width: 340px"
          :prefix-icon="Search"
          @keyup.enter="searchPatient"
        />
        <el-button type="primary" :icon="Search" @click="searchPatient">查询申请</el-button>
        <el-divider direction="vertical" />
        <el-button
          type="success"
          :icon="Cpu"
          @click="doBatchGenerateBarcode"
          :disabled="tubeGroupsSelection.length === 0 || selectedTubeRowNoBarcodeCount === 0"
          :loading="generating"
        >
          生成条码 ({{ selectedTubeRowNoBarcodeCount }})
        </el-button>
        <el-button
          type="warning"
          @click="doBatchCancelBarcode"
          :disabled="selectedTubeRowBarcodeCount === 0"
        >
          取消条码 ({{ selectedTubeRowBarcodeCount }})
        </el-button>
        <el-button :icon="Printer" @click="printBarcode" :disabled="!hasPrintableBarcode">打印条码</el-button>
        <el-tooltip content="打印设置（纸张尺寸、缩放比例等）">
          <el-button :icon="Setting" @click="openPrintConfigDialog">打印设置</el-button>
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-button type="primary" :icon="Check" @click="completeCollect" :disabled="!canCollect">
          完成采集
        </el-button>
        <div style="display:flex;align-items:center;gap:6px">
          <span class="scan-label">送检人:</span>
          <el-tag type="info" effect="plain" size="default">{{ userStore.realName || userStore.username || '当前用户' }}</el-tag>
        </div>
        <el-button :icon="Promotion" @click="deliverSample" :disabled="!canDeliver">
          送检标本
        </el-button>
        <el-button type="danger" :icon="CircleClose" @click="openRejectDialog" :disabled="!hasRejectable">
          拒收标本
        </el-button>
      </div>
      <div class="top-right">
        <el-tag type="info" effect="plain">合并标本: <b>{{ mergedTubeRows.length }}</b></el-tag>
        <el-tag type="warning" effect="plain">待生成条码: <b>{{ tubeRowNoBarcodeCount }}</b></el-tag>
        <el-tag type="success" effect="plain">已有条码: <b>{{ tubeRowBarcodeCount }}</b></el-tag>
      </div>
    </div>

    <!-- 扫码采集条 (扫码即时反馈 + 患者身份核对) -->
    <div class="scan-collect-bar" :class="scanFlash">
      <el-icon :size="18" color="#1a73e8"><Cpu /></el-icon>
      <span class="scan-label">扫码采集:</span>
      <el-tooltip content="开启后需先扫患者腕带, 再扫标本条码, 系统校验一致后才允许采集, 防止采错人" placement="top">
        <span class="identity-switch">
          <el-switch v-model="identityCheck" size="small" active-text="身份核对" @change="onIdentityCheckChange" />
        </span>
      </el-tooltip>
      <template v-if="identityCheck">
        <el-input
          ref="wristbandRef"
          v-model="wristbandBarcode"
          :placeholder="wristbandPatient ? `腕带已识别: ${wristbandPatient.patientName || ''} ${wristbandPatient.visitNo || ''}` : '先扫描患者腕带(就诊号)'"
          clearable
          size="large"
          style="width: 220px"
          @keyup.enter="handleWristbandScan"
        />
        <el-button size="large" :icon="User" @click="handleWristbandScan">核对腕带</el-button>
      </template>
      <el-input
        ref="collectScanRef"
        v-model="collectScanBarcode"
        :placeholder="identityCheck && !wristbandPatient ? '请先扫描患者腕带' : '扫描标本条码直接采集 (可连续扫描)'"
        clearable
        size="large"
        style="width: 300px"
        @keyup.enter="handleScanCollect"
      />
      <el-button type="primary" size="large" @click="handleScanCollect">
        <el-icon><Check /></el-icon>&nbsp;采集
      </el-button>
      <el-divider direction="vertical" />
      <span class="scan-label">采集人:</span>
      <el-tag type="info" effect="plain" size="default">{{ userStore.realName || userStore.username || '当前用户' }}</el-tag>
      <el-tag v-if="scanCollectMsg" :type="scanCollectMsgType" effect="plain" size="default" style="margin-left: 8px">
        {{ scanCollectMsg }}
      </el-tag>
    </div>

    <div class="main-content">
      <div class="left-panel">
        <div class="patient-info-card">
          <div class="card-header">
            <span class="card-title">患者信息</span>
            <el-tag v-if="currentVisitPatient?.patientGender" :type="currentVisitPatient.patientGender === '男' ? 'primary' : 'danger'" size="small">
              {{ currentVisitPatient.patientGender }}
            </el-tag>
          </div>
          <div class="patient-name-bar" v-if="currentVisitPatient?.patientName">
            <el-avatar :size="40" :style="{ background: currentVisitPatient.patientGender === '男' ? '#1a73e8' : '#f56c6c' }">
              {{ currentVisitPatient.patientName?.charAt(0) }}
            </el-avatar>
            <span class="patient-fullname">{{ currentVisitPatient.patientName }}</span>
            <el-tag v-if="currentVisitPatient?.patientGender" :type="currentVisitPatient.patientGender === '男' ? 'primary' : 'danger'" size="small">
              {{ currentVisitPatient.patientGender }}
            </el-tag>
            <span class="patient-age" v-if="currentVisitPatient?.patientAge">{{ currentVisitPatient.patientAge }}</span>
          </div>
          <el-form :model="patientInfoDisplay" label-width="70px" size="default" :disabled="true">
            <el-form-item label="姓 名:">
              <el-input v-model="patientInfoDisplay.name" />
            </el-form-item>
            <el-form-item label="性 别:">
              <el-input v-model="patientInfoDisplay.gender" />
            </el-form-item>
            <el-form-item label="年 龄:">
              <el-input v-model="patientInfoDisplay.age" />
            </el-form-item>
            <el-form-item label="就诊号:">
              <el-input v-model="patientInfoDisplay.visitNo" />
            </el-form-item>
            <el-form-item label="科 室:">
              <el-input v-model="patientInfoDisplay.department" />
            </el-form-item>
            <el-form-item label="医 生:">
              <el-input v-model="patientInfoDisplay.doctor" />
            </el-form-item>
          </el-form>
          <div class="patient-stats" v-if="currentVisitPatient">
            <div class="stat-item">
              <span class="stat-label">合并标本</span>
              <span class="stat-value">{{ mergedTubeRows.length }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">已生成条码</span>
              <span class="stat-value">{{ tubeRowBarcodeCount }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">申请单数</span>
              <span class="stat-value" style="font-size:12px">{{ tableData.length }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="right-panel">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-radio-group v-model="rightTab" size="default" @change="handleRightTabChange">
              <el-radio-button :value="'specimen'">
                标本显示
                <el-tag size="small" style="margin-left: 4px">{{ mergedTubeRows.length }}</el-tag>
              </el-radio-button>
              <el-radio-button :value="'collected'">
                今日已采集
                <el-tag type="success" size="small" style="margin-left: 4px">{{ collectedTodayCount }}</el-tag>
              </el-radio-button>
            </el-radio-group>
          </div>
          <div class="toolbar-right">
            <el-date-picker
              v-if="rightTab === 'collected'"
              v-model="collectDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 220px; margin-right: 8px"
              @change="loadCollectedToday"
            />
            <el-button size="small" :icon="Refresh" @click="rightTab === 'specimen' ? reloadSpecimenDisplay() : loadCollectedToday()">刷新</el-button>
          </div>
        </div>

        <div class="table-wrapper">
          <!-- 标本显示视图 (按合并维度聚合，一条记录 = 一根管) -->
          <div class="specimen-section" v-if="rightTab === 'specimen'">
            <div class="section-title">
              <el-icon><Cpu /></el-icon>
              <span>待合并标本（生成条码 / 待采集）</span>
              <el-tag size="small" effect="plain" type="info">共 {{ mergedTubeRows.length }} 条</el-tag>
            </div>
            <el-table
              v-loading="tubeLoading"
              :data="mergedTubeRows"
              border
              stripe
              max-height="calc((100vh - 300px) / 2)"
              size="small"
              @selection-change="handleTubeSelectionChange"
              @row-click="handleTubeRowClick"
            >
              <el-table-column type="selection" width="48" />
              <el-table-column label="序号" width="55" align="center" fixed>
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column label="条码号" width="160" fixed>
                <template #default="{ row }">
                  <el-tag v-if="row.sampleBarcode" type="success" effect="plain" style="font-family: Consolas, monospace; font-weight: 600">
                    {{ row.sampleBarcode }}
                  </el-tag>
                  <span v-else class="text-gray">-</span>
                </template>
              </el-table-column>
              <el-table-column label="试管" width="90" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag
                    v-if="row.tubeName"
                    :color="row.tubeColor || undefined"
                    effect="light"
                    size="small"
                    :style="row.tubeColor ? { color: '#fff', borderColor: row.tubeColor } : {}"
                  >
                    {{ row.tubeName }}
                  </el-tag>
                  <span v-else class="text-gray">未绑定</span>
                </template>
              </el-table-column>
              <el-table-column prop="sampleType" label="标本类型" width="80" show-overflow-tooltip />
              <el-table-column label="检验项目" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.testItemNames?.join('、') || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="项目数" width="70" align="center">
                <template #default="{ row }">
                  <el-tag type="info" size="small" effect="plain">{{ row.itemCount || 0 }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="applyDept" label="申请科室" width="90" show-overflow-tooltip />
              <el-table-column label="就诊号" width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.visitNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="患者" width="80" show-overflow-tooltip>
                <template #default="{ row }">{{ row.patientName || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <template v-if="row.sample">
                    <el-tag :type="statusTagType(row.sample.status)" size="small">{{ statusText(row.sample.status) }}</el-tag>
                  </template>
                  <el-tag v-else type="info" size="small" effect="plain">待生成</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="采集时间" width="140">
                <template #default="{ row }">
                  {{ row.sample?.collectTime ? formatTime(row.sample.collectTime) : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="!row.sampleBarcode"
                    type="success"
                    link
                    size="small"
                    @click.stop="doSingleGenerateBarcode(row)"
                  >生成条码</el-button>
                  <el-button
                    v-if="row.sampleBarcode && row.sample?.status === 0"
                    type="danger"
                    link
                    size="small"
                    @click.stop="doSingleCancelBarcode(row)"
                  >取消条码</el-button>
                  <el-button
                    v-if="row.sampleBarcode"
                    type="success"
                    link
                    size="small"
                    @click.stop="generateAndPrintPlates(row.sample)"
                  >培养皿</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="请输入就诊号查询，系统将按合管规则显示标本" :image-size="60" />
              </template>
            </el-table>
          </div>

          <!-- 已采集标本表格 (仅显示该就诊下 status>=1 的标本，完成采集后显示在此) -->
          <div class="specimen-section" v-if="rightTab === 'specimen'" style="margin-top: 16px">
            <div class="section-title collected">
              <el-icon color="#67c23a"><Check /></el-icon>
              <span>已采集标本（完成采集后的数据显示在此）</span>
              <el-tag size="small" effect="plain" type="success">共 {{ collectedSamplesInVisit.length }} 条</el-tag>
              <div class="section-actions" style="margin-left: auto; display:flex; gap: 8px">
                <el-button
                  size="small"
                  type="warning"
                  @click="doSelectedCancelCollect"
                  :disabled="selectedCollectedCancelCount === 0"
                >
                  取消采集 ({{ selectedCollectedCancelCount }})
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  @click="doSelectedDeliver"
                  :disabled="selectedCollectedDeliverCount === 0"
                >
                  送检 ({{ selectedCollectedDeliverCount }})
                </el-button>
              </div>
            </div>
            <el-table
              :data="collectedSamplesInVisit"
              border
              stripe
              max-height="calc((100vh - 300px) / 2)"
              size="small"
              @selection-change="handleCollectedSelectionChange"
            >
              <el-table-column type="selection" width="48" />
              <el-table-column label="序号" width="55" align="center">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="sampleBarcode" label="条码号" width="170" fixed>
                <template #default="{ row }">
                  <span style="font-family: Consolas, monospace; font-weight: 600">{{ row.sampleBarcode }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="sampleType" label="标本类型" width="80" show-overflow-tooltip />
              <el-table-column prop="containerType" label="容器/试管" width="100" show-overflow-tooltip />
              <el-table-column label="检验项目" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ (row as any).orderItems?.map((i: any) => i.testItemName || i.itemName).join('、') || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="就诊号" width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.visitNo || '-' }}</template>
              </el-table-column>
              <el-table-column prop="patientName" label="患者" width="80" show-overflow-tooltip />
              <el-table-column prop="collector" label="采集人" width="80" show-overflow-tooltip />
              <el-table-column label="采集时间" width="160">
                <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="deliverPerson" label="送检人" width="80" show-overflow-tooltip />
              <el-table-column label="送检时间" width="160">
                <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="{ row }">
                  <!-- 已采集未送检: 可取消采集 / 可送检 -->
                  <el-button
                    v-if="row.status === 1"
                    type="warning"
                    link
                    size="small"
                    @click.stop="doSingleCancelCollect(row)"
                  >取消采集</el-button>
                  <el-button
                    v-if="row.status === 1"
                    type="primary"
                    link
                    size="small"
                    @click.stop="doSingleDeliver(row)"
                  >送检</el-button>
                  <!-- 已送检未签收: 可取消送检 -->
                  <el-button
                    v-if="row.status === 2"
                    type="danger"
                    link
                    size="small"
                    @click.stop="doSingleCancelDeliver(row)"
                  >取消送检</el-button>
                  <el-tag v-if="row.status !== 1 && row.status !== 2" type="info" size="small" effect="plain">—</el-tag>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="当前就诊还没有完成采集的标本" :image-size="60" />
              </template>
            </el-table>
          </div>

          <!-- 今日已采集视图 -->
          <el-table
            v-else
            v-loading="collectedLoading"
            :data="collectedTodayData"
            border
            stripe
            height="calc(100vh - 200px)"
            size="small"
            @row-click="handleCollectedRowClick"
          >
            <el-table-column prop="sampleBarcode" label="条码号" width="150" fixed />
            <el-table-column prop="patientName" label="患者姓名" width="90" fixed />
            <el-table-column prop="patientGender" label="性别" width="55" align="center" />
            <el-table-column prop="patientAge" label="年龄" width="60" align="center" />
            <el-table-column prop="visitNo" label="就诊号" width="150" show-overflow-tooltip />
            <el-table-column label="检验项目" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.orderItems?.map((i: any) => i.testItemName).join('、') || row.sampleType || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="containerType" label="容器" width="90" show-overflow-tooltip />
            <el-table-column prop="department" label="科室" width="100" show-overflow-tooltip />
            <el-table-column label="采集时间" width="150">
              <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
            </el-table-column>
            <el-table-column prop="collectorName" label="采集人" width="80" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-tag v-if="row.status === 1" type="primary" size="small" effect="plain">已采集</el-tag>
                <el-tag v-else-if="row.status === 2" type="warning" size="small">已送检</el-tag>
                <el-tag v-else-if="row.status === 3" type="success" size="small">已签收</el-tag>
                <el-tag v-else type="info" size="small">{{ row.statusText }}</el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="今日暂无已采集的标本" />
            </template>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 查看标本弹窗 -->
    <el-dialog v-model="samplesDialogVisible" title="该申请下的标本" width="700px">
      <el-table :data="viewSamples" size="small" border stripe>
        <el-table-column prop="sampleBarcode" label="条码号" width="140" />
        <el-table-column prop="sampleType" label="标本类型" width="90" />
        <el-table-column prop="containerType" label="容器" width="100" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="collectTime" label="采集时间" width="140">
          <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
        </el-table-column>
        <el-table-column prop="deliverTime" label="送检时间" width="140">
          <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无标本" :image-size="60" />
        </template>
      </el-table>
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
          <el-select
            v-model="rejectForm.barcode"
            placeholder="选择要拒收的标本条码"
            style="width: 100%"
            size="default"
          >
            <el-option
              v-for="s in rejectableSamples"
              :key="s.sampleBarcode"
              :label="`${s.sampleBarcode} (${s.sampleType}/${s.containerType})`"
              :value="s.sampleBarcode"
            />
          </el-select>
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
        <el-button type="danger" :loading="rejecting" :disabled="!rejectForm.barcode || !rejectForm.rejectType" @click="confirmReject">
          确认拒收
        </el-button>
      </template>
    </el-dialog>
    <PrintConfigDialog
      v-model:visible="printConfigVisible"
      scene="sample-barcode"
      @saved="onPrintConfigSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Cpu, Printer, Check, Promotion, Refresh, CircleClose, User, Setting } from '@element-plus/icons-vue'
import JsBarcode from 'jsbarcode'
import {
  getOrderPage, getSamplePage, createSample, collectSample, deliverSamples, cancelDeliverSamples, cancelCollectSamples,
  getTubeGroups, batchCreateBarcodes, cancelBarcode, getSampleByBarcode,
  rejectSample, SAMPLE_REJECT_TYPES,
  type OrderVO, type SampleVO, type TubeGroup, type BarcodeGroup, type BatchBarcodeDTO, type SampleRejectDTO
} from '@/api/sample'
import { generateSamplePlates, type SamplePlate } from '@/api/micro'
import { useUserStore } from '@/stores/user'
import { openPrintWindow } from '@/utils/print'
import { escapeHtml } from '@/utils/sanitize'
import {
  buildPrintCss,
  buildJsBarcodeOptions,
  getPrintConfig,
  type PrintConfig,
} from '@/utils/printConfig'
import PrintConfigDialog from '@/components/PrintConfigDialog.vue'

const searchText = ref('')
const loading = ref(false)
const generating = ref(false)
const tableData = ref<OrderVO[]>([])
/** 查看标本弹窗 */
const samplesDialogVisible = ref(false)
const viewSamples = ref<SampleVO[]>([])

// ========== 打印设置 ==========
const printConfigVisible = ref(false)
const currentPrintConfig = ref<PrintConfig>(getPrintConfig('sample-barcode'))
function openPrintConfigDialog() {
  printConfigVisible.value = true
}
function onPrintConfigSaved(cfg: PrintConfig) {
  currentPrintConfig.value = cfg
  ElMessage.success('打印设置已保存，下次打印时生效')
}

// ========== 扫码采集 ==========
const collectScanRef = ref<any>(null)
const collectScanBarcode = ref('')
const userStore = useUserStore()
const scanCollectMsg = ref('')
const scanCollectMsgType = ref<'info' | 'success' | 'error' | 'warning'>('info')

// 患者身份扫码核对 (双向扫码: 腕带 + 标本条码)
const identityCheck = ref(false)
const wristbandRef = ref<any>(null)
const wristbandBarcode = ref('')
const wristbandPatient = ref<{ patientName?: string; visitNo?: string } | null>(null)
const identityMismatch = ref(false)
function onIdentityCheckChange(on: boolean) {
  wristbandBarcode.value = ''
  wristbandPatient.value = null
  identityMismatch.value = false
  if (on) {
    nextTick(() => wristbandRef.value?.focus())
  } else {
    nextTick(() => collectScanRef.value?.focus())
  }
}
/** 识别腕带: 按就诊号/姓名查询申请, 确认对应患者 */
async function handleWristbandScan() {
  const kw = wristbandBarcode.value.trim()
  if (!kw) {
    scanCollectMsg.value = '请扫描患者腕带'
    scanCollectMsgType.value = 'warning'
    triggerScanFlash('warning')
    return
  }
  const params: any = { current: 1, size: 5 }
  if (/^V\d+$/i.test(kw) || /^MZ\d+/i.test(kw) || /^ZY\d+/i.test(kw)) params.visitNo = kw
  else params.patientName = kw
  try {
    const res = await getOrderPage(params)
    const orders = res.records || []
    if (orders.length === 0) {
      scanCollectMsg.value = `未识别到患者: ${kw}`
      scanCollectMsgType.value = 'error'
      triggerScanFlash('error')
      playScanSound('error')
      wristbandPatient.value = null
      return
    }
    const first = orders[0]
    wristbandPatient.value = { patientName: first.patientName, visitNo: first.visitNo }
    identityMismatch.value = false
    scanCollectMsg.value = `腕带已识别: ${first.patientName} ${first.visitNo}`
    scanCollectMsgType.value = 'success'
    triggerScanFlash('success')
    playScanSound('success')
    wristbandBarcode.value = ''
    nextTick(() => collectScanRef.value?.focus())
  } catch (e: any) {
    scanCollectMsg.value = '腕带识别失败: ' + (e?.message || '网络异常')
    scanCollectMsgType.value = 'error'
    triggerScanFlash('error')
    playScanSound('error')
  }
}
// 扫码即时反馈: 视觉闪烁 + 听觉提示
const scanFlash = ref<'flash-success' | 'flash-error' | 'flash-warning' | ''>('')
let scanFlashTimer: any = null
function triggerScanFlash(type: 'success' | 'error' | 'warning') {
  scanFlash.value = type === 'success' ? 'flash-success' : type === 'error' ? 'flash-error' : 'flash-warning'
  if (scanFlashTimer) clearTimeout(scanFlashTimer)
  scanFlashTimer = setTimeout(() => { scanFlash.value = '' }, 900)
}
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

// ========== 合并标本行 (MergedTubeRow) 数据模型 ==========
/** 一条合并标本行 = 一根管，可能未生成条码（TubeGroup）或已生成条码（SampleVO） */
interface MergedTubeRow {
  /** 唯一key (TubeGroup用 groupKey, SampleVO用 barcode) */
  rowKey: string
  /** 后端合管分组序号 */
  groupIndex?: number
  /** 试管ID */
  tubeId?: number | string
  /** 试管名称 */
  tubeName?: string
  /** 试管颜色 */
  tubeColor?: string
  /** 标本类型 */
  sampleType?: string
  /** 容器类型 */
  containerType?: string
  /** 检验项目名称列表 */
  testItemNames: string[]
  /** 检验项目数 */
  itemCount: number
  /** 原始 TubeGroup (未生成条码时) */
  tubeGroup?: TubeGroup
  /** 已有条码标本 (已生成时) */
  sample?: SampleVO
  /** 显示用条码号 */
  sampleBarcode?: string
  /** 申请科室 (取第一个项目所属申请) */
  applyDept?: string
  /** 就诊号 */
  visitNo?: string
  /** 患者姓名 */
  patientName?: string
  /** 患者ID */
  patientId?: number | string
  /** 就诊ID */
  visitId?: number | string
}

const tubeLoading = ref(false)
/** 合管结果 + 已有标本 合并后展示的行数据 */
const mergedTubeRows = ref<MergedTubeRow[]>([])
/** 勾选的行 */
const tubeGroupsSelection = ref<MergedTubeRow[]>([])
/** 加载当前就诊下所有已存在的标本 */
const currentVisitSamples = ref<SampleVO[]>([])

/** 勾选中有多少条未生成条码的 (用于生成条码按钮计数) */
const selectedTubeRowNoBarcodeCount = computed(() =>
  tubeGroupsSelection.value.filter(r => !r.sampleBarcode).length
)
/** 勾选中有多少条已有条码的 (用于取消条码按钮计数) */
const selectedTubeRowBarcodeCount = computed(() =>
  tubeGroupsSelection.value.filter(r => !!r.sampleBarcode && r.sample?.status === 0).length
)
/** 整个就诊下，未生成条码的合并标本数 */
const tubeRowNoBarcodeCount = computed(() =>
  mergedTubeRows.value.filter(r => !r.sampleBarcode).length
)
/** 整个就诊下，已有条码的合并标本数 */
const tubeRowBarcodeCount = computed(() =>
  mergedTubeRows.value.filter(r => !!r.sampleBarcode).length
)

/** 是否有可打印条码 */
const hasPrintableBarcode = computed(() =>
  mergedTubeRows.value.some(r => !!r.sampleBarcode)
)

/** 是否有可拒收标本 */
const hasRejectable = computed(() =>
  currentVisitSamples.value.some(s => {
    const st = s.status
    return st !== undefined && st !== 6 && st !== 7 && st !== 8 && st !== 9
  })
)

// ========== 已采集标本表格 (该就诊下 status >= 1 的标本) ==========
/** 已采集标本列表 (currentVisitSamples中status>=1的部分，包含已采集/已送检/已签收) */
const collectedSamplesInVisit = computed<SampleVO[]>(() =>
  currentVisitSamples.value.filter(s => s.status !== undefined && s.status >= 1 && s.status < 6)
)
/** 已采集表格勾选行 */
const collectedSelection = ref<SampleVO[]>([])
function handleCollectedSelectionChange(rows: SampleVO[]) {
  collectedSelection.value = rows
}
/** 勾选中可取消采集的 (仅status===1) */
const selectedCollectedCancelCount = computed(() =>
  collectedSelection.value.filter(s => s.status === 1).length
)
/** 勾选中可送检的 (仅status===1) */
const selectedCollectedDeliverCount = computed(() =>
  collectedSelection.value.filter(s => s.status === 1).length
)

/** 单条取消采集 */
async function doSingleCancelCollect(sample: SampleVO) {
  if (!sample.sampleBarcode || sample.status !== 1) return
  try {
    await ElMessageBox.confirm(
      `确认取消采集?\n条码: ${sample.sampleBarcode}\n患者: ${sample.patientName || ''}\n取消后状态将回退为「已申请」，采集时间和采集人会被清除。`,
      '确认取消采集',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await cancelCollectSamples({ barcodes: [sample.sampleBarcode] })
    ElMessage.success(`已取消采集: ${sample.sampleBarcode}`)
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
}

/** 批量取消采集 (勾选操作栏) */
async function doSelectedCancelCollect() {
  const rows = collectedSelection.value.filter(s => s.status === 1 && !!s.sampleBarcode)
  if (rows.length === 0) {
    ElMessage.warning('请勾选已采集但未送检的标本')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量取消采集 ${rows.length} 个标本?\n` +
      rows.map(r => '  · ' + r.sampleBarcode).slice(0, 6).join('\n') +
      (rows.length > 6 ? `\n  ... 共 ${rows.length} 条` : ''),
      '确认取消采集',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await cancelCollectSamples({ barcodes: rows.map(r => r.sampleBarcode!) })
    ElMessage.success(`已取消采集 ${rows.length} 个标本`)
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
}

/** 单条送检 */
async function doSingleDeliver(sample: SampleVO) {
  if (!sample.sampleBarcode || sample.status !== 1) return
  try {
    await ElMessageBox.confirm(
      `确认送检标本?\n条码: ${sample.sampleBarcode}\n患者: ${sample.patientName || ''}\n送检人: ${userStore.realName || userStore.username}`,
      '确认送检',
      { type: 'success' }
    )
  } catch { return }
  try {
    await deliverSamples({ barcodes: [sample.sampleBarcode] })
    ElMessage.success(`已送检: ${sample.sampleBarcode}`)
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
}

/** 批量送检 (勾选操作栏) */
async function doSelectedDeliver() {
  const rows = collectedSelection.value.filter(s => s.status === 1 && !!s.sampleBarcode)
  if (rows.length === 0) {
    ElMessage.warning('请勾选已采集未送检的标本')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认送检 ${rows.length} 个标本?\n送检人: ${userStore.realName || userStore.username}`,
      '确认送检',
      { type: 'success' }
    )
  } catch { return }
  try {
    await deliverSamples({ barcodes: rows.map(r => r.sampleBarcode!) })
    ElMessage.success(`已送检 ${rows.length} 个标本`)
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
}

/** 单条取消送检 (status===2) */
async function doSingleCancelDeliver(sample: SampleVO) {
  if (!sample.sampleBarcode || sample.status !== 2) return
  try {
    await ElMessageBox.confirm(
      `确认取消送检?\n条码: ${sample.sampleBarcode}\n取消后状态将回退为「已采集」，送检信息将被清除。`,
      '确认取消送检',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await cancelDeliverSamples({ barcodes: [sample.sampleBarcode] })
    ElMessage.success(`已取消送检: ${sample.sampleBarcode}`)
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
}

/** 当前就诊的患者信息 (从所有申请中取第一条) */
const currentVisitPatient = computed(() => {
  if (tableData.value.length > 0) return tableData.value[0]
  return null
})

// 兼容旧的计数计算 (显示顶部统计)
const pendingCount = computed(() => tubeRowNoBarcodeCount.value)
const collectedCount = computed(() =>
  currentVisitSamples.value.filter(s => s.status >= 1).length
)

/** 是否可以采集: 有已生成条码且未采集的标本 */
const canCollect = computed(() => {
  return currentVisitSamples.value.some(s => s.status < 1)
})

/** 是否可以送检: 有已采集未送检的标本 */
const canDeliver = computed(() => {
  return currentVisitSamples.value.some(s => s.status === 1)
})

/** 当前选中的所有标本（兼容已有代码）*/
const selectedOrderSamples = computed<SampleVO[]>(() => currentVisitSamples.value)
/** selectedOrder 向后兼容 */
const selectedOrder = computed<OrderVO | null>(() => tableData.value[0] || null)

const patientInfoDisplay = reactive({
  visitNo: '',
  name: '',
  gender: '',
  age: '',
  department: '',
  doctor: ''
})

/** 更新左侧患者信息展示 (多就诊时合并显示) */
function updatePatientInfoFromVisit() {
  const orders = tableData.value
  if (orders.length === 0) {
    Object.assign(patientInfoDisplay, { visitNo: '', name: '', gender: '', age: '', department: '', doctor: '' })
    return
  }
  const first = orders[0]
  const visitNos = [...new Set(orders.map(o => o.visitNo).filter(Boolean))].join('、')
  const names = [...new Set(orders.map(o => o.patientName).filter(Boolean))].join('、')
  const genders = [...new Set(orders.map(o => o.patientGender).filter(Boolean))].join('、')
  const ages = [...new Set(orders.map(o => o.patientAge).filter(Boolean))].join('、')
  const depts = [...new Set(orders.map(o => o.applyDept).filter(Boolean))].join('、')
  const doctors = [...new Set(orders.map(o => o.applyDoctor).filter(Boolean))].join('、')
  Object.assign(patientInfoDisplay, {
    visitNo: visitNos || (first.visitNo ?? ''),
    name: names || (first.patientName ?? ''),
    gender: genders || (first.patientGender ?? ''),
    age: ages || (first.patientAge ?? ''),
    department: depts || (first.applyDept ?? ''),
    doctor: doctors || (first.applyDoctor ?? '')
  })
}

/** 加载申请列表 + 合并标本视图 */
async function loadData() {
  if (!searchText.value) {
    ElMessage.warning('请输入就诊号/申请单号/患者姓名')
    return
  }
  loading.value = true
  tubeLoading.value = true
  const kw = searchText.value.trim()
  const params: any = { current: 1, size: 50 }
  if (/^V\d+$/i.test(kw) || /^MZ\d+/i.test(kw) || /^ZY\d+/i.test(kw)) {
    params.visitNo = kw
  } else if (/^O\d+$/i.test(kw)) {
    params.orderNo = kw
  } else {
    params.patientName = kw
  }

  try {
    const res = await getOrderPage(params)
    tableData.value = res.records || []
    if (tableData.value.length === 0) {
      ElMessage.info('未找到对应的检验申请')
      mergedTubeRows.value = []
      currentVisitSamples.value = []
      updatePatientInfoFromVisit()
      return
    }
    updatePatientInfoFromVisit()
    // 按就诊维度加载合并标本
    await loadMergedTubeRowsFromVisit()
  } catch {
    tableData.value = []
    mergedTubeRows.value = []
    currentVisitSamples.value = []
  } finally {
    loading.value = false
    tubeLoading.value = false
  }
}

function searchPatient() {
  loadData()
}

// 兼容保留
function handleRowClick() {}
function selectOrder() {}

/**
 * 加载当前查询结果的合并标本行:
 * 1) 若多个就诊 (按姓名查询常见), 对每个就诊分别调 getTubeGroups 获取合管分组 (未生成条码的项目)
 * 2) 对所有匹配就诊一次性调 getSamplePage(visitIds) 获取已生成的标本
 * 3) 合并为 mergedTubeRows，一条 = 一根管
 * 4) 仅加载查询条件返回的患者/就诊，不污染全量数据
 */
async function loadMergedTubeRowsFromVisit() {
  // 收集查询到的所有唯一 visitId
  const visitIds: Array<number | string> = []
  tableData.value.forEach(o => {
    if (o.visitId != null && !visitIds.includes(o.visitId)) visitIds.push(o.visitId)
  })
  const rows: MergedTubeRow[] = []
  if (visitIds.length === 0) {
    mergedTubeRows.value = rows
    currentVisitSamples.value = []
    return
  }

  tubeLoading.value = true
  try {
    // 并行: 对每个就诊调合管分组 + 一次性查询所有visit的标本 (限最大分页 2000)
    const groupPromises = visitIds.map(vid =>
      getTubeGroupsReliable(vid).then(g => ({ vid, groups: g || [] }))
                    .catch(() => ({ vid, groups: [] as TubeGroup[] }))
    )
    const visitSet = [...new Set(tableData.value.map(o => `${o.id}`))]
    const samplePromise = getSamplePage({
      current: 1,
      size: Math.max(2000, visitIds.length * 500),
      visitIds
    } as any).catch(() => ({ records: [] as SampleVO[] }))

    const [groupResults, sampleRes] = await Promise.all([
      Promise.all(groupPromises),
      samplePromise
    ])

    // 缓存分组
    groupResults.forEach(({ vid, groups }) => {
      tubeGroupsCache.value.set(vid, groups || [])
    })

    // 仅保留属于本次查询订单集合内的标本 (双重保险)
    const orderIds = new Set(tableData.value.map(o => String(o.id)))
    const allSamples: SampleVO[] = (sampleRes as any).records || []
    const samples: SampleVO[] = allSamples.filter(s => {
      if (s == null) return false
      // 标本必须属于本次查询的任一 visitId
      return s.visitId != null && visitIds.some(v => String(v) === String(s.visitId))
    })
    currentVisitSamples.value = samples

    // 已被已有标本(无论采集与否, 只要已生成为样本记录就占用合管槽)占用的合管键集合,
    // 用于避免 tubeGroups 分支再重复生成一行待合并项
    const occupiedGroupKeys = new Set<string>()
    function groupKey(vid: any, tubeId: any, sampleType: any): string {
      return `${vid}__${tubeId ?? 'noTube'}__${sampleType ?? ''}`
    }
    for (const s of samples) {
      const k = groupKey(s.visitId, (s as any).tubeId, s.sampleType || s.specimenType)
      occupiedGroupKeys.add(k)
    }

    // 以已生成的标本为基础构造行
    for (const s of samples) {
      // 已完成采集(含送检/签收/核收等)的标本不进入"待合并"列表, 仅保留未采集(status<1)的
      if (s.status !== undefined && s.status >= 1) continue
      const orderItems: any[] = (s as any).orderItems || (s as any).items || []
      rows.push({
        rowKey: 'sample_' + (s.id || s.sampleBarcode),
        sampleType: s.sampleType,
        containerType: s.containerType,
        tubeName: s.containerType,
        testItemNames: orderItems.map((i: any) => i.testItemName || i.itemName).filter(Boolean),
        itemCount: orderItems.length || 1,
        sample: s,
        sampleBarcode: s.sampleBarcode,
        applyDept: (s as any).department || s.applyDept,
        visitNo: s.visitNo,
        patientName: s.patientName,
        patientId: s.patientId,
        visitId: s.visitId
      })
    }

    // 加上未生成条码的合管分组行 (按就诊分组合并)
    for (const { vid, groups } of groupResults) {
      const visitFirstOrder = tableData.value.find(o => String(o.visitId) === String(vid))
      for (const g of (groups || [])) {
        if (!g.testItems || g.testItems.length === 0) continue
        // 如果该合管组已被同一 visit+试管+标本类型的已有样本记录占用, 不再作为待合并展示
        const k = groupKey(vid, g.tubeId, g.sampleType)
        if (occupiedGroupKeys.has(k)) continue
        const names = g.testItems.map(t => t.itemName || '').filter(Boolean)
        const firstItem = g.testItems[0]
        const order = firstItem?.orderId
          ? tableData.value.find(o => String(o.id) === String(firstItem.orderId))
          : undefined
        rows.push({
          rowKey: 'group_' + vid + '_' + g.groupIndex + '_' + (g.tubeId || 'noTube') + '_' + (g.sampleType || ''),
          groupIndex: g.groupIndex,
          tubeId: g.tubeId,
          tubeName: g.tubeName,
          tubeColor: g.tubeColor,
          sampleType: g.sampleType,
          containerType: g.containerType,
          testItemNames: names,
          itemCount: g.itemCount || names.length,
          tubeGroup: g,
          sampleBarcode: undefined,
          applyDept: (order || visitFirstOrder)?.applyDept,
          visitNo: (order || visitFirstOrder)?.visitNo,
          patientName: (order || visitFirstOrder)?.patientName,
          patientId: (order || visitFirstOrder)?.patientId,
          visitId: vid
        })
      }
    }

    mergedTubeRows.value = rows
    // 消除 orderIds 未使用告警
    void visitSet; void orderIds
  } finally {
    tubeLoading.value = false
  }
}

/** 重新加载标本显示 (刷新按钮) */
async function reloadSpecimenDisplay() {
  if (tableData.value.length === 0) {
    ElMessage.warning('请先查询就诊')
    return
  }
  tableData.value.forEach(o => {
    if (o.visitId != null) tubeGroupsCache.value.delete(o.visitId)
  })
  await loadMergedTubeRowsFromVisit()
}

/** 勾选行变化 */
function handleTubeSelectionChange(rows: MergedTubeRow[]) {
  tubeGroupsSelection.value = rows
}

/** 点击行: 向后兼容，暂不做特殊处理 (用户可能想查看详情) */
function handleTubeRowClick() {}


/** 预加载合管数据(按就诊维度, 查询时自动执行, 点击生成条码时直接使用缓存, 实现10ms内响应) */
const tubeGroupsCache = ref<Map<any, TubeGroup[]>>(new Map())
/** 正在预加载的visitId集合, 避免重复请求 */
const preloadingVisits = ref<Set<any>>(new Set())
function preloadTubeGroups(visitId: number | string) {
  if (visitId == null) return
  if (tubeGroupsCache.value.has(visitId) || preloadingVisits.value.has(visitId)) return
  preloadingVisits.value.add(visitId)
  getTubeGroupsReliable(visitId).then(groups => {
    tubeGroupsCache.value.set(visitId, groups || [])
  }).catch(() => {
    tubeGroupsCache.value.set(visitId, [])
  }).finally(() => {
    preloadingVisits.value.delete(visitId)
  })
}

/** 带重试的getTubeGroups调用(网关偶发丢失query param时自动重试) */
async function getTubeGroupsReliable(visitId: number | string, retries = 2): Promise<TubeGroup[]> {
  let lastErr: any
  for (let i = 0; i <= retries; i++) {
    try {
      return await getTubeGroups(visitId)
    } catch (e) {
      lastErr = e
      if (i < retries) await new Promise(r => setTimeout(r, 100))
    }
  }
  throw lastErr
}

/** 加载申请对应的标本列表 (向后兼容: 刷新后 mergedTubeRows 也更新) */
async function loadSamplesByOrderId(_orderId?: number | string) {
  // 改为按就诊维度刷新合并标本视图即可
  if (tableData.value.length > 0) {
    const visitId = tableData.value[0]?.visitId
    if (visitId) await loadMergedTubeRowsFromVisit()
  }
}

function formatTime(time?: string) {
  if (!time) return ''
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

function statusText(status: number): string {
  const map: Record<number, string> = { 0: '已申请', 1: '已采集', 2: '已送检', 3: '已签收', 4: '已核收', 5: '检测中', 6: '已报告', 7: '已退费', 8: '已作废', 9: '已拒收' }
  return map[status] || '未知'
}

function statusTagType(status: number): string {
  const map: Record<number, string> = { 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'success', 5: 'primary', 6: 'success', 7: 'info', 8: 'info', 9: 'danger' }
  return map[status] || 'info'
}

// ============ 批量生成/取消条码 + 打印 + 采集 + 送检 + 拒收 ============

/**
 * 从合并标本行中提取就诊信息(所有行都是同一个就诊)
 */
function getVisitMetaFromRows(rows: MergedTubeRow[]) {
  const first = rows[0]
  const patient = currentVisitPatient.value
  return {
    visitId: first?.visitId ?? patient?.visitId,
    patientId: first?.patientId ?? patient?.patientId,
    patientName: first?.patientName ?? patient?.patientName ?? '',
    visitNo: first?.visitNo ?? patient?.visitNo ?? '',
    department: first?.applyDept ?? patient?.applyDept ?? '检验科',
  }
}

/** 单根合管分组生成条码 */
async function callBatchCreate(rows: MergedTubeRow[]) {
  const meta = getVisitMetaFromRows(rows)
  if (!meta.visitId) {
    ElMessage.warning('缺少就诊信息, 无法生成条码')
    return []
  }
  const groups: BarcodeGroup[] = []
  for (const r of rows) {
    if (r.sampleBarcode || !r.tubeGroup) continue
    const g = r.tubeGroup
    if (!g.testItems?.length) continue
    groups.push({
      tubeId: g.tubeId,
      tubeName: g.tubeName,
      sampleType: g.sampleType,
      containerType: g.containerType,
      testItems: g.testItems.map(t => ({
        testItemId: t.testItemId,
        orderId: t.orderId as any,
        orderItemId: t.orderItemId as any,
      }))
    })
  }
  if (groups.length === 0) return []
  const dto: BatchBarcodeDTO = {
    visitId: meta.visitId,
    patientId: meta.patientId as any,
    patientName: meta.patientName,
    visitNo: meta.visitNo,
    department: meta.department,
    groups,
  }
  return await batchCreateBarcodes(dto)
}

/** 批量/单个 生成条码 */
async function doBatchGenerateBarcode() {
  const noBarcodeRows = tubeGroupsSelection.value.filter(r => !r.sampleBarcode)
  if (noBarcodeRows.length === 0) {
    ElMessage.warning('请先勾选未生成条码的标本行')
    return
  }
  generating.value = true
  try {
    const created = await callBatchCreate(noBarcodeRows)
    if (created && created.length > 0) {
      ElMessage.success(`已生成 ${created.length} 个条码`)
    }
    const vid = getVisitMetaFromRows(noBarcodeRows).visitId
    if (vid) tubeGroupsCache.value.delete(vid as any)
    await reloadSpecimenDisplay()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error('条码生成失败: ' + (e?.message || '系统异常'))
    }
  } finally {
    generating.value = false
  }
}

/** 行内按钮: 单个生成条码 */
async function doSingleGenerateBarcode(row: MergedTubeRow) {
  if (row.sampleBarcode) return
  generating.value = true
  try {
    const created = await callBatchCreate([row])
    if (created && created.length > 0) {
      ElMessage.success(`已生成 ${created.length} 个条码`)
    }
    if (row.visitId) tubeGroupsCache.value.delete(row.visitId as any)
    await reloadSpecimenDisplay()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error('条码生成失败: ' + (e?.message || '系统异常'))
    }
  } finally {
    generating.value = false
  }
}

/** 批量取消条码 (仅 status=0 可取消) */
async function doBatchCancelBarcode() {
  const rows = tubeGroupsSelection.value.filter(r => !!r.sampleBarcode && r.sample?.status === 0)
  if (rows.length === 0) {
    ElMessage.warning('请勾选有待取消的条码行(仅未采集标本可取消)')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认取消 ${rows.length} 个条码? 取消后标本将删除, 可重新生成条码:\n` +
      rows.map(r => '  · ' + r.sampleBarcode).slice(0, 6).join('\n') +
      (rows.length > 6 ? `\n  ... 共 ${rows.length} 条` : ''),
      '确认取消条码',
      { type: 'warning' }
    )
  } catch { return }

  let ok = 0, fail = 0
  for (const r of rows) {
    try {
      if (r.sampleBarcode) {
        await cancelBarcode(r.sampleBarcode)
        ok++
      }
    } catch { fail++ }
  }
  if (ok > 0) ElMessage.success(`成功取消 ${ok} 个条码${fail > 0 ? `, 失败 ${fail} 个` : ''}`)
  else if (fail > 0) ElMessage.error(`取消失败 ${fail} 个`)
  const vid = rows[0]?.visitId
  if (vid) tubeGroupsCache.value.delete(vid as any)
  await reloadSpecimenDisplay()
}

/** 行内按钮: 单个取消条码 */
async function doSingleCancelBarcode(row: MergedTubeRow) {
  if (!row.sampleBarcode || row.sample?.status !== 0) return
  try {
    await ElMessageBox.confirm(
      `确认取消条码 ${row.sampleBarcode}? 取消后标本将删除, 可重新生成条码`,
      '确认取消条码',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await cancelBarcode(row.sampleBarcode)
    ElMessage.success(`条码 ${row.sampleBarcode} 已取消`)
    if (row.visitId) tubeGroupsCache.value.delete(row.visitId as any)
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
}

/** 打印条码: 把当前就诊下所有有条码的标本全部打印 */
function printBarcode() {
  const printable = mergedTubeRows.value.filter(r => !!r.sampleBarcode && r.sample)
  if (printable.length === 0) {
    ElMessage.warning('当前就诊还没有可打印的条码, 请先生成条码')
    return
  }
  const patient = currentVisitPatient.value
  if (!patient) return
  const cfg = getPrintConfig('sample-barcode')
  currentPrintConfig.value = cfg
  const jsbc = buildJsBarcodeOptions(cfg)
  const fontSize = cfg.labelFontSize

  const orderItemNames = (patient.items || []).map((i: any) => i.testItemName).filter(Boolean).join('、')

  const labelsHtml = printable.map((row, idx) => {
    const s = row.sample!
    const itemNames = (row.testItemNames && row.testItemNames.length > 0)
      ? row.testItemNames.join('、')
      : orderItemNames
    return `
    <div class="barcode-label">
      <div class="label-header">
        <span class="patient-name">${escapeHtml(patient.patientName || '')}</span>
        <span class="patient-info">${escapeHtml(patient.patientGender || '')} ${escapeHtml(patient.patientAge || '')}</span>
      </div>
      <div class="label-row">
        <span class="label-field">就诊号:</span>
        <span class="label-value">${escapeHtml(patient.visitNo || row.visitNo || '')}</span>
      </div>
      <div class="label-row">
        <span class="label-field">科室:</span>
        <span class="label-value">${escapeHtml(patient.applyDept || row.applyDept || '')}</span>
      </div>
      <div class="label-row">
        <span class="label-field">标本:</span>
        <span class="label-value">${escapeHtml(s.sampleType || row.sampleType || '')} / ${escapeHtml(s.containerType || row.tubeName || '')}</span>
      </div>
      <div class="label-row">
        <span class="label-field">项目:</span>
        <span class="label-value item-names">${escapeHtml(itemNames)}</span>
      </div>
      <div class="barcode-area">
        <svg id="barcode-${idx}"></svg>
      </div>
      <div class="barcode-text">${escapeHtml(row.sampleBarcode!)}</div>
    </div>`
  }).join('')

  const svgH = Math.max(22, cfg.barcodeHeight)
  const headerSize = Math.max(fontSize + 2, 13)
  const infoSize = Math.max(fontSize - 1, 9)

  const win = openPrintWindow(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>打印条码 - ${escapeHtml(patient.patientName || '')}</title>
<style>
${buildPrintCss(cfg, '.barcode-label')}
  .label-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 0.3mm dashed #999;
    padding-bottom: 1mm;
    margin-bottom: 1mm;
  }
  .patient-name { font-size: ${headerSize}px; font-weight: bold; }
  .patient-info { font-size: ${infoSize}px; color: #666; }
  .label-row { font-size: ${fontSize}px; line-height: 1.5; display: flex; margin-bottom: 1px; }
  .label-field { color: #666; width: 14mm; flex-shrink: 0; }
  .label-value { color: #333; flex: 1; }
  .item-names { word-break: break-all; }
  .barcode-area { text-align: center; margin-top: auto; padding-top: 1mm; }
  .barcode-area svg { height: ${svgH}px; display: block; margin: 0 auto; max-width: 100%; }
  .barcode-text { text-align: center; font-weight: bold; letter-spacing: 1px; font-size: ${Math.max(10, fontSize)}px; }
</style>
<script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.6/dist/JsBarcode.all.min.js"><\/script>
</head>
<body>
<div class="print-scale-wrap">
${labelsHtml}
</div>
<script>
  window.onload = function() {
    ${printable.map((r, idx) => `try { JsBarcode("#barcode-${idx}", ${JSON.stringify(r.sampleBarcode)}, ${JSON.stringify(jsbc)}); } catch(e) { console.error(e); }`).join('\n    ')}
    setTimeout(function() { window.print(); }, 400);
  };
<\/script>
</body>
</html>`, { width: 600, height: 800 })
  if (!win) {
    ElMessage.error('无法打开打印窗口, 请检查浏览器弹窗拦截设置')
    return
  }
  ElMessage.success(`正在打印 ${printable.length} 个条码标签`)
}

/**
 * 生成培养皿条码并打印
 */
async function generateAndPrintPlates(sample: SampleVO) {
  if (!sample.sampleBarcode) return
  const patient = currentVisitPatient.value
  if (!patient) {
    ElMessage.warning('请先查询就诊')
    return
  }
  // 合并行模式下收集所有 testItemIds
  const testItemIds: any[] = []
  for (const row of mergedTubeRows.value) {
    if (row.sample?.sampleBarcode === sample.sampleBarcode) {
      // 从 sample.orderItems 取
      const ois: any[] = (sample as any).orderItems || (sample as any).items || []
      for (const oi of ois) if (oi.testItemId) testItemIds.push(oi.testItemId)
      if (testItemIds.length === 0 && row.tubeGroup?.testItems) {
        for (const ti of row.tubeGroup.testItems) testItemIds.push(ti.testItemId)
      }
    }
  }
  // fallback: 从申请单 items 合并
  if (testItemIds.length === 0) {
    for (const o of tableData.value) {
      (o.items || []).forEach((i: any) => { if (i.testItemId) testItemIds.push(i.testItemId) })
    }
  }
  if (!testItemIds.length) {
    ElMessage.warning('没有检验项目信息, 无法生成培养皿')
    return
  }
  try {
    const plates = await generateSamplePlates({
      sampleBarcode: sample.sampleBarcode,
      sampleId: typeof sample.id === 'number' ? sample.id : undefined,
      testItemIds,
      patientName: patient.patientName || sample.patientName || '',
      operatorId: userStore.userId,
      operatorName: userStore.realName || userStore.username || ''
    })
    if (!plates || plates.length === 0) {
      ElMessage.warning('未生成培养皿, 请确认该检验项目已配置培养方案')
      return
    }
    ElMessage.success(`已生成 ${plates.length} 个培养皿条码`)
    printPlates(plates, patient.patientName || sample.patientName || '')
  } catch (e: any) {
    ElMessage.error('生成培养皿条码失败: ' + (e?.message || '系统异常'))
  }
}

/** 打印培养皿条码标签 */
function printPlates(plates: SamplePlate[], patientName: string) {
  const cfg = getPrintConfig('plate-barcode')
  const jsbc = buildJsBarcodeOptions(cfg)
  const fontSize = cfg.labelFontSize

  const labelsHtml = plates.map((p, idx) => `
    <div class="plate-label">
      <div class="plate-header">
        <span class="patient-name">${escapeHtml(patientName || '')}</span>
        <span class="medium-name">${escapeHtml(p.mediumTypeName || '')}</span>
      </div>
      <div class="plate-row">
        <span class="label-field">标本条码:</span>
        <span class="label-value">${escapeHtml(p.sampleBarcode || '')}</span>
      </div>
      <div class="plate-row">
        <span class="label-field">培养基:</span>
        <span class="label-value">${escapeHtml(p.mediumTypeName || '')}</span>
      </div>
      <div class="barcode-area">
        <svg id="plate-${idx}"></svg>
      </div>
      <div class="barcode-text">${escapeHtml(p.plateBarcode)}</div>
    </div>
  `).join('')

  const svgH = Math.max(18, cfg.barcodeHeight)
  const patientSize = Math.max(fontSize + 1, 12)
  const mediumSize = Math.max(fontSize - 1, 9)

  const win = openPrintWindow(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>打印培养皿条码 - ${escapeHtml(patientName || '')}</title>
<style>
${buildPrintCss(cfg, '.plate-label')}
  .plate-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 0.3mm dashed #999;
    padding-bottom: 0.8mm;
    margin-bottom: 0.8mm;
  }
  .patient-name { font-size: ${patientSize}px; font-weight: bold; }
  .medium-name { font-size: ${mediumSize}px; color: #1a73e8; font-weight: bold; }
  .plate-row { font-size: ${fontSize}px; line-height: 1.5; display: flex; margin-bottom: 1px; }
  .label-field { color: #666; width: 15mm; flex-shrink: 0; }
  .label-value { color: #333; flex: 1; }
  .barcode-area { text-align: center; margin-top: auto; padding-top: 1mm; }
  .barcode-area svg { height: ${svgH}px; display: block; margin: 0 auto; max-width: 100%; }
  .barcode-text { text-align: center; font-weight: bold; letter-spacing: 1px; font-size: ${Math.max(10, fontSize)}px; }
</style>
<script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.6/dist/JsBarcode.all.min.js"><\/script>
</head>
<body>
<div class="print-scale-wrap">
${labelsHtml}
</div>
<script>
  window.onload = function() {
    ${plates.map((p, idx) => `try { JsBarcode("#plate-${idx}", ${JSON.stringify(p.plateBarcode)}, ${JSON.stringify(jsbc)}); } catch(e) { console.error(e); }`).join('\n    ')}
    setTimeout(function() { window.print(); }, 400);
  };
<\/script>
</body>
</html>`, { width: 600, height: 800 })
  if (!win) {
    ElMessage.error('无法打开打印窗口, 请检查浏览器弹窗拦截设置')
  }
}

/** 完成采集: 当前就诊下所有未采集(status<1)的标本 */
async function completeCollect() {
  const toCollect = currentVisitSamples.value.filter(s => s.status < 1 && s.sampleBarcode)
  if (toCollect.length === 0) {
    ElMessage.warning('当前就诊没有待采集的标本')
    return
  }
  try {
    const { value: remark } = await ElMessageBox.prompt(
      `确认完成采集 ${toCollect.length} 个标本？\n患者: ${currentVisitPatient.value?.patientName || ''}\n采集人: ${userStore.realName || userStore.username}\n可填写采集备注(可选):`,
      '采集确认',
      {
        type: 'success',
        inputType: 'textarea',
        inputPlaceholder: '采集备注(可选)',
        confirmButtonText: '确认采集',
        cancelButtonText: '取消'
      }
    )
    for (const s of toCollect) {
      try { await collectSample({ sampleBarcode: s.sampleBarcode!, remark: remark || undefined }) } catch { /* 单个失败不阻断 */ }
    }
    ElMessage.success('采集成功')
    await reloadSpecimenDisplay()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('采集失败')
  }
}

/** 扫码采集 */
async function handleScanCollect() {
  const barcode = collectScanBarcode.value.trim()
  if (!barcode) {
    scanCollectMsg.value = '请输入或扫描标本条码'
    scanCollectMsgType.value = 'warning'
    triggerScanFlash('warning')
    return
  }
  collectScanBarcode.value = ''

  if (identityCheck.value) {
    if (!wristbandPatient.value) {
      scanCollectMsg.value = `请先扫描患者腕带再采集 ${barcode}`
      scanCollectMsgType.value = 'warning'
      triggerScanFlash('warning')
      nextTick(() => wristbandRef.value?.focus())
      return
    }
    try {
      const sample = await getSampleByBarcode(barcode)
      const sampleVisit = sample.visitNo || ''
      const wristVisit = wristbandPatient.value.visitNo || ''
      const nameMatch = sample.patientName === wristbandPatient.value.patientName
      const visitMatch = !!wristVisit && sampleVisit === wristVisit
      if (!nameMatch && !visitMatch) {
        identityMismatch.value = true
        scanCollectMsg.value = `警告! 标本归属不符: 标本[${sample.patientName}/${sampleVisit}] ≠ 腕带[${wristbandPatient.value.patientName}/${wristVisit}]`
        scanCollectMsgType.value = 'error'
        triggerScanFlash('error')
        playScanSound('error')
        return
      }
      identityMismatch.value = false
    } catch (e: any) {
      scanCollectMsg.value = `无法校验标本归属: ${barcode}`
      scanCollectMsgType.value = 'error'
      triggerScanFlash('error')
      playScanSound('error')
      return
    }
  }
  try {
    const result = await collectSample({ sampleBarcode: barcode })
    scanCollectMsg.value = `✓ 已采集: ${result.patientName || ''} ${barcode}`
    scanCollectMsgType.value = 'success'
    triggerScanFlash('success')
    playScanSound('success')
    await reloadSpecimenDisplay()
  } catch (e: any) {
    scanCollectMsg.value = `采集失败: ${barcode}`
    scanCollectMsgType.value = 'error'
    triggerScanFlash('error')
    playScanSound('error')
  } finally {
    nextTick(() => collectScanRef.value?.focus())
  }
}

/** 行内"采集"按钮 (向后兼容占位) */
function handleCollect(_row: OrderVO) { completeCollect() }

/** 送检: 当前就诊下所有已采集未送检的标本 */
async function deliverSample() {
  const toDeliver = currentVisitSamples.value.filter(s => s.status === 1 && s.sampleBarcode)
  if (toDeliver.length === 0) {
    ElMessage.warning('当前就诊没有可送检的标本(需先采集)')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认送检 ${toDeliver.length} 个标本？\n送检人: ${userStore.realName || userStore.username}`,
      '送检确认',
      { type: 'success' }
    )
    await deliverSamples({ barcodes: toDeliver.map(s => s.sampleBarcode!) })
    ElMessage.success(`已送检 ${toDeliver.length} 个标本 (送检人: ${userStore.realName || userStore.username})`)
    await reloadSpecimenDisplay()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('送检失败')
  }
}

/** 查看标本弹窗 */
function handleViewSamples(_row?: OrderVO) {
  viewSamples.value = [...currentVisitSamples.value]
  samplesDialogVisible.value = viewSamples.value.length > 0
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

/** 可拒收的标本: 排除已报告/已退费/已作废/已拒收 */
const rejectableSamples = computed(() => {
  return currentVisitSamples.value.filter(s => {
    const st = s.status
    return st !== undefined && st !== 6 && st !== 7 && st !== 8 && st !== 9 && !!s.sampleBarcode
  })
})

/** 打开拒收弹窗 */
function openRejectDialog() {
  if (rejectableSamples.value.length === 0) {
    ElMessage.warning('当前就诊没有可拒收的标本')
    return
  }
  rejectForm.value = { barcode: '', rejectType: '', rejectReason: '', operator: '' }
  rejectDialogVisible.value = true
}

/** 确认拒收 */
async function confirmReject() {
  if (!rejectForm.value.barcode) { ElMessage.warning('请选择要拒收的标本条码'); return }
  if (!rejectForm.value.rejectType) { ElMessage.warning('请选择不合格类型'); return }
  rejecting.value = true
  try {
    await rejectSample({ ...rejectForm.value })
    ElMessage.success(`拒收成功: ${rejectForm.value.barcode} (${rejectForm.value.rejectType})`)
    rejectDialogVisible.value = false
    await reloadSpecimenDisplay()
  } catch (e: any) { /* 拦截器已提示 */ }
  finally { rejecting.value = false }
}

/** 向后兼容: 旧的 generateBarcode / handleGenerateBarcode / handleCancelBarcode 仍保留为调用新版 */
async function generateBarcode() { await doBatchGenerateBarcode() }
function handleGenerateBarcode(_row: OrderVO) { /* no-op */ }
async function handleCancelBarcode(row: SampleVO) {
  const matched = mergedTubeRows.value.find(r => r.sample?.id === row.id || r.sampleBarcode === row.sampleBarcode)
  if (matched) await doSingleCancelBarcode(matched)
}

const route = useRoute()

// ========== 右侧视图切换: 申请列表/今日已采集 ==========
const rightTab = ref<'specimen' | 'collected'>('specimen')
const collectedLoading = ref(false)
const collectedTodayData = ref<SampleVO[]>([])
const collectDateRange = ref<string[] | null>(null)

/** 切换tab */
function handleRightTabChange(tab: string) {
  if (tab === 'collected') {
    loadCollectedToday()
  }
}

/** 加载今日已采集的标本 */
async function loadCollectedToday() {
  collectedLoading.value = true
  try {
    const today = new Date()
    const fmt = (d: Date) => {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    }
    const endDate = fmt(today)
    const startDate = collectDateRange.value?.[0] || endDate
    const all: SampleVO[] = []
    // 查询采集人+日期范围内所有状态的标本 (已采集/已送检/已签收等)
    for (const status of [1, 2, 3]) {
      try {
        const r: any = await getSamplePage({
          current: 1,
          size: 500,
          status,
          startDate,
          endDate,
          collectorId: userStore.userId
        })
        all.push(...(r.records || []))
      } catch {
        // ignore
      }
    }
    // 去重: 按 sampleBarcode, 并按采集时间倒序
    const seen = new Set<string>()
    collectedTodayData.value = all.filter(s => {
      if (!s.sampleBarcode || seen.has(s.sampleBarcode)) return false
      seen.add(s.sampleBarcode)
      return true
    }).sort((a, b) => (b.collectTime || '').localeCompare(a.collectTime || ''))
  } catch (e) {
    collectedTodayData.value = []
  } finally {
    collectedLoading.value = false
  }
}

const collectedTodayCount = computed(() => collectedTodayData.value.length)

/** 点击已采集行: 把患者信息同步到左侧, 方便继续操作 */
function handleCollectedRowClick(row: SampleVO) {
  // 尝试加载对应申请的标本, 以便在左侧继续查看和操作
  if (row.orderId) {
    loadSamplesByOrderId(row.orderId)
    const order = tableData.value.find(o => String(o.id) === String(row.orderId))
    if (order) {
      selectedOrder.value = order
      Object.assign(patientInfoDisplay, {
        visitNo: order.visitNo || '',
        name: order.patientName || '',
        age: order.patientAge || '',
        department: order.applyDept || '',
        doctor: order.applyDoctor || ''
      })
    }
  }
}

onMounted(() => {
  const visitNo = route.query.visitNo as string
  if (visitNo) {
    searchText.value = visitNo
    loadData()
  }
})
</script>

<style scoped lang="scss">
.collect-page {
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
  gap: 12px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.top-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.top-right {
  display: flex;
  gap: 8px;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 8px;
  overflow: hidden;
}

.scan-collect-bar {
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

  .identity-switch {
    display: inline-flex;
    align-items: center;
    white-space: nowrap;
  }

  // 扫码即时视觉反馈
  &.flash-success { animation: collectFlashSuccess 0.9s ease; }
  &.flash-error { animation: collectFlashError 0.9s ease; }
  &.flash-warning { animation: collectFlashWarning 0.9s ease; }
}

@keyframes collectFlashSuccess {
  0%, 100% { border: 1px solid #e1f3d8; }
  30% { border: 2px solid #67c23a; background: #f0f9eb; }
  70% { border: 1px solid #e1f3d8; }
}

@keyframes collectFlashError {
  0%, 100% { border: 1px solid #fde2e2; }
  30% { border: 2px solid #f56c6c; background: #fef0f0; }
  70% { border: 1px solid #fde2e2; }
}

@keyframes collectFlashWarning {
  0%, 100% { border: 1px solid #faecd8; }
  30% { border: 2px solid #e6a23c; background: #fdf6ec; }
  70% { border: 1px solid #faecd8; }
}

.left-panel {
  width: 300px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.patient-info-card, .sample-card {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.sample-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.patient-name-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 4px;
  border-bottom: 1px dashed #ebeef5;
}

.patient-fullname {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.patient-age {
  font-size: 13px;
  color: #909399;
}

.patient-info-card :deep(.el-form-item) {
  margin-bottom: 6px;
}

.patient-info-card :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
  padding-right: 8px;
}

.patient-info-card :deep(.el-input__wrapper) {
  box-shadow: none;
  background: #f5f7fa;
}

.patient-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
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

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafbfc;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.table-wrapper {
  flex: 1;
  padding: 8px 8px 0 8px;
  overflow: auto;
}

.specimen-section {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 10px 10px 10px;
  margin-bottom: 8px;

  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    padding: 2px 4px 8px 4px;
    border-bottom: 1px dashed #ebeef5;
    margin-bottom: 8px;

    &.collected {
      color: #2f7d32;
      border-bottom-color: #e1f3d8;
    }
  }
}

.text-gray {
  color: #c0c4cc;
}
</style>
