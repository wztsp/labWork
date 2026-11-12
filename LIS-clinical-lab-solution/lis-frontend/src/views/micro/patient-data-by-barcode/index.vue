<template>
  <div class="patient-plate-page">
    <!-- 顶部: 条码扫描 + 方案/操作 -->
    <el-card class="top-card" shadow="never">
      <div class="top-row">
        <div class="scan-block">
          <el-icon :size="18" color="#1a73e8"><Cpu /></el-icon>
          <span class="scan-label">采集条码扫描:</span>
          <el-input
            ref="scanRef"
            v-model="scanBarcode"
            placeholder="扫描或输入采集工作站产生的标本条码后回车查询"
            clearable
            size="large"
            style="width: 360px"
            @keyup.enter="handleQueryByBarcode"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" size="large" :icon="Search" @click="handleQueryByBarcode" :loading="queryLoading">
            查询患者
          </el-button>
        </div>
        <div class="action-block">
          <el-tooltip content="按采集条码查询患者与申请项目，并可加载该标本已生成的培养基条码">
            <el-tag effect="plain" type="info">操作说明：扫描采集条码 → 勾选检验项目 → 选择培养方案 → 生成培养基条码</el-tag>
          </el-tooltip>
        </div>
      </div>
    </el-card>

    <div class="main-grid" v-loading="queryLoading">
      <!-- 左侧: 患者 + 检验项目 -->
      <div class="left-col">
        <!-- 患者信息 -->
        <el-card class="patient-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><User /></el-icon> 患者与标本信息</span>
              <el-tag v-if="sample?.isUrgent === 1" type="danger" size="small" effect="dark">急</el-tag>
            </div>
          </template>
          <div v-if="sample" class="patient-body">
            <div class="patient-name-row">
              <el-avatar :size="44" :style="{ background: sample.patientName?.startsWith?.('') !== false ? '#1a73e8' : '#f56c6c' }">
                {{ sample.patientName?.charAt?.(0) || '?' }}
              </el-avatar>
              <div class="patient-meta">
                <div class="patient-name">{{ sample.patientName }}</div>
                <div class="patient-sub">
                  <el-tag size="small" effect="plain">{{ sample.sampleType }}</el-tag>
                  <el-tag size="small" type="info" effect="plain">条码: {{ sample.sampleBarcode }}</el-tag>
                </div>
              </div>
            </div>
            <el-descriptions :column="2" border size="small" class="patient-desc">
              <el-descriptions-item label="就诊号">{{ sample.visitNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="标本状态">
                <el-tag size="small">{{ sample.statusText || sample.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="申请科室">{{ sample.department || '-' }}</el-descriptions-item>
              <el-descriptions-item label="采集/送检">
                {{ sample.collectTime ? sample.collectTime.slice(0, 16) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="容器类型">{{ sample.containerType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="签收时间">
                {{ sample.signOffTime ? sample.signOffTime.slice(0, 16) : '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else description="请先扫描/输入采集条码查询标本信息" :image-size="70" />
        </el-card>

        <!-- 检验项目勾选 -->
        <el-card class="items-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Tickets /></el-icon> 检验项目 (勾选后用于生成培养基条码)</span>
              <div class="header-tip">
                <el-checkbox
                  :model-value="isAllItemsChecked"
                  :indeterminate="isItemsIndeterminate"
                  :disabled="!sample || orderItems.length === 0"
                  @change="toggleAllItems"
                >全选</el-checkbox>
              </div>
            </div>
          </template>
          <div v-if="orderItems.length > 0" class="items-list">
            <div
              v-for="it in orderItems"
              :key="it.id"
              class="item-row"
              :class="{ active: selectedItemIds.includes(String(it.testItemId)) }"
            >
              <el-checkbox
                :model-value="selectedItemIds.includes(String(it.testItemId))"
                :disabled="!sample"
                @change="(v: boolean) => toggleItem(it.testItemId, v)"
              />
              <div class="item-info">
                <div class="item-name">{{ it.testItemName }}</div>
                <div class="item-sub">项目代码: {{ it.testItemCode }}</div>
              </div>
              <el-tag v-if="matchedPlanByItem[it.testItemId]" size="small" type="success" effect="plain">
                匹配方案: {{ matchedPlanByItem[it.testItemId].planName }}
              </el-tag>
            </div>
          </div>
          <el-empty v-else description="暂无检验项目" :image-size="60" />
        </el-card>
      </div>

      <!-- 右侧: 培养方案 + 生成结果 -->
      <div class="right-col">
        <!-- 培养方案选择 -->
        <el-card class="plan-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Collection /></el-icon> 培养方案配置</span>
            </div>
          </template>
          <div class="plan-body">
            <el-form label-width="90px" size="small">
              <el-form-item label="选择方案">
                <el-select
                  v-model="selectedPlanId"
                  placeholder="请选择培养方案"
                  style="width: 100%"
                  filterable
                  :disabled="!sample"
                  @change="handlePlanChange"
                >
                  <el-option
                    v-for="p in eligiblePlans"
                    :key="p.id"
                    :label="p.planName + (p.testItemName ? ` (${p.testItemName})` : '') + (p.sampleType ? ` [${p.sampleType}]` : '')"
                    :value="p.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="selectedPlanDetail" label="方案明细">
                <div class="plan-items">
                  <div v-for="(it, idx) in selectedPlanDetail.items" :key="it.id || idx" class="plan-item-chip">
                    <el-tag type="primary" effect="plain">{{ idx + 1 }}. {{ it.mediumTypeName }}</el-tag>
                    <span class="plan-qty">×{{ it.quantity || 1 }}</span>
                  </div>
                  <el-empty v-if="!selectedPlanDetail.items?.length" description="方案未配置培养基明细" :image-size="40" />
                </div>
              </el-form-item>
              <el-form-item label="操作人">
                <el-tag effect="plain" size="small">{{ userName }}</el-tag>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  :icon="MagicStick"
                  :disabled="!canGenerate || hasReceivedPlates"
                  :loading="generating"
                  @click="handleGeneratePlates"
                >
                  生成培养基条码
                </el-button>
                <el-button
                  :icon="Refresh"
                  :disabled="!sample"
                  @click="reloadGeneratedPlates"
                >
                  刷新已生成
                </el-button>
              </el-form-item>
              <div v-if="hasReceivedPlates" class="plate-summary">
                <el-alert
                  type="error"
                  :closable="false"
                  show-icon
                  title="已存在已核收的培养基条码，请先撤销所有微生物报告(或取消核收)后再重新生成"
                />
              </div>
              <div v-else-if="plateTotalCounts > 0" class="plate-summary">
                <el-alert
                  type="success"
                  :closable="false"
                  show-icon
                  :title="`本次方案将生成 ${plateTotalCounts} 个培养基条码 (${selectedPlanDetail?.planName || ''})`"
                />
              </div>
            </el-form>
          </div>
        </el-card>

        <!-- 已生成培养基条码 -->
        <el-card class="plate-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title"><el-icon><Grid /></el-icon> 已生成培养基条码</span>
              <div class="header-actions">
                <el-tag size="small" type="info" effect="plain" v-if="generatedPlates.length > 0">共 {{ generatedPlates.length }} 管</el-tag>
                <el-tooltip content="打印设置（纸张尺寸、缩放比例等）">
                  <el-button
                    link
                    size="small"
                    :icon="Setting"
                    @click="openPrintConfigDialog"
                  >打印设置</el-button>
                </el-tooltip>
                <el-button
                  type="danger"
                  link
                  size="small"
                  :icon="Refresh"
                  :disabled="generatedPlates.length === 0 || !hasActivePlates"
                  @click="handleCancelAll"
                >取消全部培养基条码</el-button>
                <el-button
                  type="primary"
                  link
                  size="small"
                  :icon="Printer"
                  :disabled="generatedPlates.length === 0"
                  @click="handlePrintAll"
                >打印全部条码</el-button>
              </div>
            </div>
          </template>
          <div v-if="generatedPlates.length > 0" class="plate-list">
            <div v-for="(p, idx) in generatedPlates" :key="p.id || idx" class="plate-row">
              <div class="plate-seq">{{ p.seqNo || (idx + 1) }}</div>
              <div class="plate-info">
                <div class="plate-medium">{{ p.mediumTypeName }}</div>
                <div class="plate-barcode-text">{{ p.plateBarcode }}</div>
              </div>
              <div class="plate-status">
                <el-tag :type="plateStatusType(p.status)" size="small" effect="plain">
                  {{ plateStatusText(p.status) }}
                </el-tag>
              </div>
              <div class="plate-action">
                <el-button
                  v-if="(p.status ?? 0) === 0"
                  type="danger"
                  link
                  size="small"
                  @click="handleCancelOne(p)"
                >取消条码</el-button>
                <el-tooltip v-if="(p.status ?? 0) === 2" content="已作废条码不能打印">
                  <el-button type="primary" link size="small" :icon="Printer" :disabled="true" @click="handlePrintOne(p)">打印</el-button>
                </el-tooltip>
                <el-button v-else type="primary" link size="small" :icon="Printer" @click="handlePrintOne(p)">打印</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="还没有生成的培养基条码，请先扫描采集条码并选择方案后生成" :image-size="70" />
        </el-card>
      </div>
    </div>
    <PrintConfigDialog
      v-model:visible="printConfigVisible"
      scene="plate-barcode"
      @saved="onPrintConfigSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import {
  Search, User, Tickets, Collection, MagicStick, Printer, Refresh, Cpu, Setting
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getSampleByBarcode, type SampleVO, type OrderItemVO } from '@/api/sample'
import {
  getAllCulturePlans,
  getCulturePlanDetail,
  generateSamplePlates,
  getSamplePlatesByBarcode,
  cancelSamplePlate,
  cancelAllPlatesBySample,
  type CulturePlan,
  type CulturePlanVO,
  type SamplePlate
} from '@/api/micro'
import { openPrintWindow } from '@/utils/print'
import { escapeHtml } from '@/utils/sanitize'
import {
  buildPrintCss,
  buildJsBarcodeOptions,
  getPrintConfig,
  type PrintConfig,
} from '@/utils/printConfig'
import PrintConfigDialog from '@/components/PrintConfigDialog.vue'

const userStore = useUserStore()
const userName = computed(() => userStore.realName || userStore.username || '当前用户')

// ========== 状态 ==========
const scanRef = ref<any>(null)
const scanBarcode = ref('')
const queryLoading = ref(false)
const generating = ref(false)

const sample = ref<SampleVO | null>(null)
const orderItems = ref<OrderItemVO[]>([])
const selectedItemIds = ref<string[]>([])

const culturePlans = ref<CulturePlan[]>([])
const selectedPlanId = ref<number | ''>('')
const selectedPlanDetail = ref<CulturePlanVO | null>(null)

const generatedPlates = ref<SamplePlate[]>([])
const matchedPlanByItem = reactive<Record<number, CulturePlan>>({})

// ========== 打印设置 ==========
const printConfigVisible = ref(false)
const currentPrintConfig = ref<PrintConfig>(getPrintConfig('plate-barcode'))

// ========== 计算 ==========
const isAllItemsChecked = computed(() =>
  orderItems.value.length > 0 && orderItems.value.every((it) => selectedItemIds.value.includes(String(it.testItemId)))
)
const isItemsIndeterminate = computed(() => {
  const selected = orderItems.value.filter((it) => selectedItemIds.value.includes(String(it.testItemId))).length
  return selected > 0 && selected < orderItems.value.length
})

/** 当前标本的标本类型(医生下达申请的标本类型) */
const currentSampleType = computed(() => (sample.value?.sampleType || '').trim())
/** 适用于当前标本类型的培养方案: 未加载标本时展示全部; 有标本类型时过滤出专属或"全部"方案 */
const eligiblePlans = computed(() => {
  const st = currentSampleType.value
  if (!st) return culturePlans.value
  return culturePlans.value.filter((p) => {
    const ps = (p.sampleType || '').trim()
    return ps === '' || ps === st
  })
})

const plateTotalCounts = computed(() => {
  if (!selectedPlanDetail.value?.items?.length) return 0
  return selectedPlanDetail.value.items.reduce((s, it) => s + (it.quantity || 1), 0)
})

const canGenerate = computed(() => {
  return !!sample.value && selectedItemIds.value.length > 0 && !!selectedPlanId.value
})

/** 是否存在可取消的培养基 (未核收 = status 0/null，且未作废) */
const hasActivePlates = computed(() =>
  generatedPlates.value.some((p) => (p.status ?? 0) === 0)
)

/** 是否存在已核收的培养基条码 (status=1): 此时禁用生成按钮, 需先撤销核收报告 */
const hasReceivedPlates = computed(() =>
  generatedPlates.value.some((p) => (p.status ?? 0) === 1)
)

// ========== 方法 ==========
function plateStatusText(status?: number) {
  switch (status) {
    case 0: return '未核收'
    case 1: return '已核收'
    case 2: return '已作废(取消)'
    default: return '未核收'
  }
}
function plateStatusType(status?: number) {
  switch (status) {
    case 0: return 'info'      // 未核收 - 普通
    case 1: return 'success'   // 已核收 - 成功
    case 2: return 'danger'    // 已作废 - 危险
    default: return 'info'
  }
}

/** 取消单条培养基条码 */
async function handleCancelOne(p: SamplePlate) {
  if (!p.id) return
  try {
    await ElMessageBox.confirm(
      `确认作废该培养基条码（${p.plateBarcode || p.id}）？\n作废后该条码不可使用；如已核收请先撤销微生物报告。`,
      '取消条码确认',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await cancelSamplePlate(p.id)
    ElMessage.success('已作废')
    // 刷新列表
    if (sample.value?.sampleBarcode) await reloadGeneratedPlates()
    else generatedPlates.value = generatedPlates.value.map(x => x.id === p.id ? { ...x, status: 2 } : x)
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}

/** 取消当前标本全部培养基条码 */
async function handleCancelAll() {
  if (!sample.value?.sampleBarcode && !sample.value?.id) return
  try {
    await ElMessageBox.confirm(
      `确认作废该标本的全部可取消培养基条码？\n已核收的条码需先撤销微生物报告后才会取消。`,
      '批量取消确认',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await cancelAllPlatesBySample({ sampleId: sample.value?.id, sampleBarcode: sample.value?.sampleBarcode })
    ElMessage.success('批量取消成功')
    if (sample.value?.sampleBarcode) await reloadGeneratedPlates()
    else generatedPlates.value = generatedPlates.value.map(p => ({ ...p, status: 2 }))
  } catch (e: any) {
    ElMessage.error(e?.message || '批量取消失败')
  }
}

async function loadCulturePlans() {
  try {
    const data = await getAllCulturePlans()
    culturePlans.value = Array.isArray(data) ? data : (data as any)?.data || []
  } catch (e) {
    // 降级: 用分页方式获取
    try {
      const { getCulturePlanPage } = await import('@/api/micro')
      const res: any = await getCulturePlanPage({ pageSize: 500 })
      culturePlans.value = res?.records || res?.list || []
    } catch (_) {
      culturePlans.value = []
    }
  }
}

async function handleQueryByBarcode() {
  const code = scanBarcode.value?.trim()
  if (!code) {
    ElMessage.warning('请输入或扫描采集条码')
    scanRef.value?.focus?.()
    return
  }
  queryLoading.value = true
  try {
    const res: any = await getSampleByBarcode(code)
    // 兼容 {data:xxx} 或直接返回对象两种风格
    const s: SampleVO = (res?.sampleBarcode ? res : (res?.data?.sampleBarcode ? res.data : res)) as SampleVO
    if (!s?.sampleBarcode) {
      ElMessage.warning(`未找到条码为 ${code} 的标本`)
      sample.value = null
      orderItems.value = []
      selectedItemIds.value = []
      generatedPlates.value = []
      return
    }
    sample.value = s
    orderItems.value = s.orderItems || []
    // 默认勾选全部
    selectedItemIds.value = orderItems.value.map((it) => String(it.testItemId))
    // 按项目匹配方案
    await matchPlansForItems()
    // 自动选中方案: 优先选已匹配且与标本类型精确匹配的方案; 仅匹配一个时直接用
    const uniquePlans = Array.from(new Set(Object.values(matchedPlanByItem).map((p) => p.id)))
    const st = currentSampleType.value
    const exactMatchId = st ? uniquePlans.find((pid) => {
      const p = culturePlans.value.find(x => x.id === pid)
      return p && (p.sampleType || '').trim() === st
    }) : undefined
    const targetPlanId = exactMatchId ?? (uniquePlans.length === 1 ? uniquePlans[0] : undefined)
    if (targetPlanId) {
      selectedPlanId.value = targetPlanId as number
      await handlePlanChange()
    } else if (culturePlans.value.length > 0 && !selectedPlanId.value) {
      selectedPlanId.value = ''
      selectedPlanDetail.value = null
    }
    // 加载已生成的条码
    await reloadGeneratedPlates()
    ElMessage.success(`已加载患者: ${s.patientName}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '查询标本失败')
    sample.value = null
    orderItems.value = []
    selectedItemIds.value = []
    generatedPlates.value = []
  } finally {
    queryLoading.value = false
    nextTick(() => scanRef.value?.focus?.())
  }
}

async function matchPlansForItems() {
  // 清理旧匹配
  Object.keys(matchedPlanByItem).forEach((k) => delete matchedPlanByItem[Number(k)])
  for (const it of orderItems.value) {
    // 优先按 testItemName 模糊匹配方案 (eligiblePlans 已按标本类型过滤)
    const byName = eligiblePlans.value.find(
      (p) => p.testItemName && it.testItemName && (
        p.testItemName.includes(it.testItemName) || it.testItemName.includes(p.testItemName.replace('方案', ''))
      )
    )
    if (byName) {
      matchedPlanByItem[it.testItemId] = byName
      continue
    }
    // 再按方案名里的关键字匹配
    const kw = it.testItemName
    const byPlanName = eligiblePlans.value.find((p) =>
      (kw.includes('痰') && p.planName.includes('痰')) ||
      (kw.includes('尿') && p.planName.includes('尿')) ||
      (kw.includes('血') && (p.planName.includes('血培养'))) ||
      (kw.includes('分泌') && p.planName.includes('分泌')) ||
      (kw.includes('脑脊液') && p.planName.includes('脑脊液')) ||
      (kw.includes('胸腹') && p.planName.includes('胸腹')) ||
      (kw.includes('粪便') && p.planName.includes('粪便')) ||
      (kw.includes('真菌') && p.planName.includes('真菌')) ||
      (kw.includes('厌氧') && p.planName.includes('厌氧')) ||
      (kw.includes('淋球菌') && p.planName.includes('淋球菌'))
    )
    if (byPlanName) matchedPlanByItem[it.testItemId] = byPlanName
  }
}

function toggleItem(testItemId: number | string, checked: boolean) {
  const key = String(testItemId)
  if (checked) {
    if (!selectedItemIds.value.includes(key)) selectedItemIds.value.push(key)
  } else {
    selectedItemIds.value = selectedItemIds.value.filter((x) => x !== key)
  }
}

function toggleAllItems(val: boolean) {
  if (val) {
    selectedItemIds.value = orderItems.value.map((it) => String(it.testItemId))
  } else {
    selectedItemIds.value = []
  }
}

async function handlePlanChange() {
  selectedPlanDetail.value = null
  if (!selectedPlanId.value) return
  try {
    const res: any = await getCulturePlanDetail(Number(selectedPlanId.value))
    selectedPlanDetail.value = (res?.planName ? res : (res?.data?.planName ? res.data : res)) as CulturePlanVO
  } catch (e: any) {
    ElMessage.error(e?.message || '获取方案详情失败')
  }
}

async function reloadGeneratedPlates() {
  generatedPlates.value = []
  if (!sample.value?.sampleBarcode) return
  try {
    const res: any = await getSamplePlatesByBarcode(sample.value.sampleBarcode)
    const list = Array.isArray(res) ? res : (res?.data || [])
    generatedPlates.value = (list as SamplePlate[]).slice().sort((a, b) => (a.seqNo || 0) - (b.seqNo || 0))
  } catch (_) {
    generatedPlates.value = []
  }
}

async function handleGeneratePlates() {
  if (!canGenerate.value) return
  generating.value = true
  try {
    // testItemIds 以 String 形式传递, 避免 Long 雪花 ID 经 Number(s) 精度丢失.
    // Jackson 可将 JSON String 自动反序列化为后端 Long 类型.
    const ids = selectedItemIds.value.slice()
    const res: any = await generateSamplePlates({
      sampleBarcode: sample.value!.sampleBarcode,
      sampleId: sample.value!.id,
      sampleType: sample.value!.sampleType,
      testItemIds: ids,
      planId: selectedPlanId.value || undefined,
      patientName: sample.value!.patientName,
      operatorName: userName.value
    })
    const list = Array.isArray(res) ? res : (res?.data || [])
    generatedPlates.value = (list as SamplePlate[]).slice().sort((a, b) => (a.seqNo || 0) - (b.seqNo || 0))
    ElMessage.success(`成功生成 ${generatedPlates.value.length} 个培养基条码`)
  } catch (e: any) {
    ElMessage.error(e?.message || '生成培养基条码失败')
  } finally {
    generating.value = false
  }
}

// ========== 打印 ==========
/** 判断该条码是否允许打印 (已作废status=2禁止打印) */
function isPlatePrintable(p: SamplePlate): boolean {
  return !!p.plateBarcode && (p.status ?? 0) !== 2
}

function buildPrintHtml(title: string, labels: SamplePlate[]) {
  // 实时拉取最新配置
  const cfg = getPrintConfig('plate-barcode')
  currentPrintConfig.value = cfg
  const jsbc = buildJsBarcodeOptions(cfg)

  const labelsHtml = labels
    .map((it, idx) => `
      <div class="plate-label">
        <div class="pl-row"><span class="pl-field">患者:</span><span class="pl-value">${escapeHtml(it.patientName || sample.value?.patientName || '')}</span></div>
        <div class="pl-row"><span class="pl-field">培养基:</span><span class="pl-value">${escapeHtml(it.mediumTypeName || '')}</span></div>
        <div class="pl-row"><span class="pl-field">序号:</span><span class="pl-value">${(it.seqNo ?? idx + 1)} / ${labels.length}</span></div>
        <div class="pl-row"><span class="pl-field">标本:</span><span class="pl-value">${escapeHtml(it.sampleBarcode || sample.value?.sampleBarcode || '')}</span></div>
        <div class="pl-barcode"><svg id="pbc-${idx}"></svg></div>
        <div class="pl-barcode-text">${escapeHtml(it.plateBarcode || '')}</div>
      </div>
    `)
    .join('')

  // 条码高度: 按配置取 cfg.barcodeHeight px, svg 容器高度 = barcodeHeight + 少量margin
  const svgH = Math.max(18, cfg.barcodeHeight)
  const svgStyle = `height: ${svgH}px; max-width: 100%;`

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
<style>
${buildPrintCss(cfg, '.plate-label')}
  .pl-row { display: flex; margin-bottom: 2px; }
  .pl-field { color: #666; width: 14mm; flex-shrink: 0; }
  .pl-value { color: #222; flex: 1; word-break: break-all; font-weight: 500; }
  .pl-barcode { margin-top: auto; text-align: center; padding-top: 1mm; }
  .pl-barcode svg { ${svgStyle} display: block; margin: 0 auto; max-width: 100%; }
  .pl-barcode-text { text-align: center; font-weight: bold; letter-spacing: 1px; margin-top: 1mm; }
</style>
<script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.6/dist/JsBarcode.all.min.js"><\/script>
</head>
<body>
<div class="print-scale-wrap">
${labelsHtml}
</div>
<script>
  window.onload = function() {
    ${labels.map((it, idx) => `try { JsBarcode("#pbc-${idx}", ${JSON.stringify(it.plateBarcode || '')}, ${JSON.stringify(jsbc)}); } catch(e) { console.error(e); }`).join('\n    ')}
    setTimeout(function() { window.print(); }, 400);
  };
<\/script>
</body>
</html>`
}

function handlePrintOne(p: SamplePlate) {
  if (!isPlatePrintable(p)) {
    ElMessage.warning('该培养基条码已作废, 不能打印')
    return
  }
  const ok = openPrintWindow(buildPrintHtml('培养基条码-' + (p.mediumTypeName || ''), [p]), { width: 600, height: 800 })
  if (!ok) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
}

function handlePrintAll() {
  const toPrint = generatedPlates.value.filter(isPlatePrintable)
  if (toPrint.length === 0) {
    if (generatedPlates.value.length === 0) {
      ElMessage.warning('还没有可打印的培养基条码')
    } else {
      ElMessage.warning('所有培养基条码均已作废, 不能打印')
    }
    return
  }
  const skipped = generatedPlates.value.length - toPrint.length
  const ok = openPrintWindow(buildPrintHtml('培养基条码-批量打印', toPrint), { width: 600, height: 800 })
  if (!ok) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
    return
  }
  if (skipped > 0) {
    ElMessage.info(`已自动跳过 ${skipped} 个已作废的培养基条码`)
  }
}

function openPrintConfigDialog() {
  printConfigVisible.value = true
}

function onPrintConfigSaved(cfg: PrintConfig) {
  currentPrintConfig.value = cfg
  ElMessage.success('打印设置已保存，下次打印时生效')
}

// ========== 生命周期 ==========
onMounted(async () => {
  await loadCulturePlans()
  nextTick(() => scanRef.value?.focus?.())
})
</script>

<style scoped lang="scss">
.patient-plate-page {
  padding: 12px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);

  .top-card {
    margin-bottom: 12px;
    .top-row {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }
    .scan-block {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .scan-label {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      white-space: nowrap;
    }
  }

  .main-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  @media (max-width: 1200px) {
    .main-grid { grid-template-columns: 1fr; }
  }

  .left-col, .right-col {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    .card-title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      color: #303133;
    }
  }

  // ---- 患者卡 ----
  .patient-card {
    .patient-body { padding: 4px 2px; }
    .patient-name-row {
      display: flex; align-items: center; gap: 12px;
      margin-bottom: 12px;
    }
    .patient-meta {
      flex: 1;
      .patient-name { font-size: 18px; font-weight: 700; color: #1f2d3d; }
      .patient-sub {
        margin-top: 4px; display: flex; gap: 8px; flex-wrap: wrap;
      }
    }
  }

  // ---- 检验项目 ----
  .items-card {
    .items-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 320px;
      overflow-y: auto;
    }
    .item-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      border: 1px solid #ebeef5;
      border-radius: 6px;
      background: #fafbfc;
      transition: all .15s;
      &.active {
        border-color: #409eff88;
        background: #ecf5ff;
      }
    }
    .item-info {
      flex: 1;
      .item-name { font-weight: 600; color: #303133; }
      .item-sub { font-size: 12px; color: #909399; margin-top: 2px; }
    }
  }

  // ---- 方案 ----
  .plan-card {
    .plan-body { padding: 4px 2px; }
    .plan-items {
      display: flex; flex-wrap: wrap; gap: 8px;
    }
    .plan-item-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      border: 1px solid #d9ecff;
      background: #ecf5ff;
      padding: 2px 4px 2px 6px;
      border-radius: 12px;
      .plan-qty {
        color: #409eff;
        font-weight: 600;
        font-size: 12px;
      }
    }
    .plate-summary { margin-top: 4px; }
  }

  // ---- 生成培养基条码列表 ----
  .plate-card {
    .plate-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-height: 420px;
      overflow-y: auto;
    }
    .plate-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      border: 1px solid #ebeef5;
      border-radius: 6px;
      background: #fff;
    }
    .plate-seq {
      width: 28px; height: 28px;
      border-radius: 50%;
      background: linear-gradient(135deg, #409eff, #1a73e8);
      color: #fff;
      font-weight: 700;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
      font-size: 13px;
    }
    .plate-info {
      flex: 1;
      .plate-medium { font-weight: 600; color: #303133; }
      .plate-barcode-text {
        font-family: Consolas, Menlo, monospace;
        font-size: 12px;
        color: #606266;
        margin-top: 2px;
      }
    }
    .plate-status { min-width: 72px; text-align: center; }
  }
}
</style>
