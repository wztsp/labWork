<template>
  <div class="report-renderer-wrapper">
    <div
      v-for="(pg, pi) in renderPages"
      :key="pi"
      class="report-page"
      :class="{ 'page-last': pi === renderPages.length - 1 }"
    >
      <div class="report-renderer" :style="pageStyle">
        <!-- ========== 1. 标题区 ========== -->
        <div class="report-header">
          <div class="header-left">
            <img
              v-if="computedHospitalLogo"
              :src="computedHospitalLogo"
              class="hospital-logo"
              alt="医院Logo"
            />
            <div class="hospital-names">
              <div class="hospital-main">{{ hospitalName }}</div>
              <div v-if="hospitalSubName" class="hospital-sub">{{ hospitalSubName }}</div>
            </div>
          </div>
          <div class="header-center">
            <div class="report-title">{{ reportTitle }}</div>
          </div>
          <div class="header-right">
            <span class="page-indicator">第 {{ pi + 1 }}页/共 {{ pageCount }}页</span>
          </div>
        </div>

        <!-- ========== 2. 患者/标本信息 3行 规范布局 ========== -->
        <div class="info-block">
          <div class="info-row">
            <div class="info-cell"><span class="lbl">姓名:</span><span class="val">{{ pick('patientName') }}</span></div>
            <div class="info-cell"><span class="lbl">性别:</span><span class="val">{{ formatGender(pick('patientGender')) }}</span></div>
            <div class="info-cell"><span class="lbl">年龄:</span><span class="val">{{ pick('patientAge') }}岁</span></div>
            <div class="info-cell"><span class="lbl">标本条码:</span><span class="val">{{ pick('sampleBarcode') }}</span></div>
            <div class="info-cell info-cell-wide"><span class="lbl">仪器名称:</span><span class="val">{{ instrumentName }}</span></div>
          </div>
          <div class="info-row">
            <div class="info-cell"><span class="lbl">科室:</span><span class="val">{{ pick('department') }}</span></div>
            <div class="info-cell"><span class="lbl">床号:</span><span class="val">{{ pick('bedNo') }}</span></div>
            <div class="info-cell"><span class="lbl">住院号:</span><span class="val">{{ inpatientNo }}</span></div>
            <div class="info-cell"><span class="lbl">标本编号:</span><span class="val">{{ specimenNo }}</span></div>
            <div class="info-cell"><span class="lbl">标本类型:</span><span class="val">{{ specimenType }}</span></div>
          </div>
          <div class="info-row">
            <div class="info-cell info-cell-block">
              <span class="lbl">临床诊断:</span>
              <span class="val">{{ pick('clinicalDiag') }}</span>
            </div>
            <div class="info-cell info-cell-block">
              <span class="lbl">申请项目:</span>
              <span class="val">{{ applyItemNames }}</span>
            </div>
          </div>
        </div>

        <!-- ========== 3. 结果表格区 ========== -->
        <!-- 微生物模式: 细菌鉴定 + 药敏 (独立渲染) -->
        <div v-if="isMicroMode" class="micro-wrap">
          <!-- 血培养信息块 (micro-blood) -->
          <div v-if="hasMicroBlood" class="micro-block micro-blood-block">
            <div class="micro-title">血培养信息</div>
            <table class="render-table micro-bc-table">
              <thead>
                <tr>
                  <th>培养瓶号</th><th>瓶类型</th><th>血量</th><th>接种时间</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>{{ pick('bottleNo') }}</td>
                  <td>{{ pick('bottleType') }}</td>
                  <td>{{ pick('volume') }}</td>
                  <td>{{ pick('inoculateTime') }}</td>
                </tr>
              </tbody>
              <thead>
                <tr>
                  <th>报警时间</th><th>TTP</th><th>血培养结果</th><th></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>{{ pick('alarmTime') }}</td>
                  <td>{{ (props.data as any)?.ttpHours != null ? ((props.data as any).ttpHours + ' 小时') : '-' }}</td>
                  <td class="bc-result">{{ bloodCultureResultText }}</td>
                  <td></td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 阴性结论块 (micro-negative) -->
          <div v-if="hasMicroNegative" class="micro-block micro-negative-block">
            <div class="micro-title">培养结论（阴性）</div>
            <div class="micro-negative-text">{{ microNegativeText }}</div>
          </div>

          <div class="micro-block" v-for="(org, oidx) in organismsForPage(pg)" :key="'o'+oidx">
            <div class="micro-title">细菌鉴定</div>
            <table class="render-table micro-organism-table">
              <thead>
                <tr>
                  <th style="width:42%">细菌名称</th>
                  <th style="width:18%">细菌编码</th>
                  <th style="width:14%">生长量</th>
                  <th style="width:26%">培养方法</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="mo-name">{{ org.organismName || '-' }}</td>
                  <td>{{ org.organismCode || '-' }}</td>
                  <td>{{ org.quantity || '-' }}</td>
                  <td>{{ org.colonsMethod || '-' }}</td>
                </tr>
              </tbody>
            </table>
            <div class="micro-title" style="margin-top:8px;">{{ org.organismName }} - 药敏结果 (CLSI判读)</div>
            <table class="render-table micro-sens-table">
              <thead>
                <tr>
                  <th style="width:42%">抗生素</th>
                  <th style="width:18%">MIC</th>
                  <th style="width:18%">结果</th>
                  <th style="width:22%">判读界值</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(s, sidx) in org.sensitivities || []" :key="'s'+sidx">
                  <td>{{ s.antibioticName || '-' }}</td>
                  <td>{{ s.micValue || '-' }}</td>
                  <td :class="sensClass(s.result)">{{ sensText(s.result) }}</td>
                  <td>{{ s.breakpoint || '-' }}</td>
                </tr>
                <tr v-if="!(org.sensitivities || []).length">
                  <td colspan="4" class="empty-row">该菌暂无药敏结果</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!organismsForPage(pg).length" class="empty-row" style="padding:20px;">暂无微生物鉴定结果</div>
        </div>

        <!-- 尿常规双栏模式 -->
        <div v-else-if="isUrineMode" class="urine-wrap">
          <table class="render-table urine-half">
            <caption class="urine-caption">尿液化学检测项目</caption>
            <thead>
              <tr>
                <th style="width:34%">中文名称</th>
                <th style="width:22%">结果</th>
                <th style="width:16%">单位</th>
                <th style="width:28%">参考</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, idx) in urineChemRowsForPage(pg)" :key="'c'+idx" :class="rowClass(r)">
                <td class="c-name">{{ r.testItemName }}</td>
                <td>{{ formatCell('resultValue', r) }}</td>
                <td>{{ r.unit || '-' }}</td>
                <td>{{ r.refRange || '-' }}</td>
              </tr>
              <tr v-if="!urineChemRowsForPage(pg).length">
                <td colspan="4" class="empty-row">暂无尿液化学结果</td>
              </tr>
            </tbody>
          </table>
          <div class="urine-divider"></div>
          <table class="render-table urine-half">
            <caption class="urine-caption">尿沉渣检测项目</caption>
            <thead>
              <tr>
                <th style="width:34%">中文名称</th>
                <th style="width:22%">结果</th>
                <th style="width:16%">单位</th>
                <th style="width:28%">参考</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, idx) in urineSedimentRowsForPage(pg)" :key="'s'+idx" :class="rowClass(r)">
                <td class="c-name">{{ r.testItemName }}</td>
                <td>{{ formatCell('resultValue', r) }}</td>
                <td>{{ r.unit || '-' }}</td>
                <td>{{ r.refRange || '-' }}</td>
              </tr>
              <tr v-if="!urineSedimentRowsForPage(pg).length">
                <td colspan="4" class="empty-row">暂无尿沉渣结果</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 常规单列模式: 按结果列配置(7列 或 10列多参考) -->
        <div v-else class="regular-wrap">
          <table class="render-table regular-table">
            <colgroup>
              <col v-for="(cw, i) in regularColWidths" :key="i" :style="{ width: cw }" />
            </colgroup>
            <thead>
              <tr>
                <th v-for="col in regularColumns" :key="col">{{ regularColLabels[col] || col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, idx) in regularRowsForPage(pg)" :key="idx" :class="rowClass(r)">
                <td v-for="(col, ci) in regularColumns" :key="ci" :class="regularCellClass(col, r)">
                  <template v-if="col === 'idx'">
                    <span v-if="isMutual(r)" class="hr-star">*</span>
                    <span class="idx-num">{{ idx + 1 + pg * regularRowsPerPage }}</span>
                  </template>
                  <template v-else-if="col === 'abnormalFlag'">
                    {{ r.abnormalFlag === 'H' ? '↑' : (r.abnormalFlag === 'L' ? '↓' : (r.abnormalFlag || '-')) }}
                  </template>
                  <template v-else-if="col === 'isCritical'">
                    {{ Number((r as any).isCritical) === 1 ? '⚠危急' : '-' }}
                  </template>
                  <template v-else-if="col === 'lastResult'">{{ (r as any).lastResult ?? '—' }}</template>
                  <template v-else-if="col === 'lastDate'">{{ (r as any).lastDate ?? '—' }}</template>
                  <template v-else-if="col === 'lastFlag'">
                    <span :class="flagClass((r as any).lastFlag)">{{ (r as any).lastFlag ?? '—' }}</span>
                  </template>
                  <template v-else>{{ formatResultCell(col, r) }}</template>
                </td>
              </tr>
              <tr v-if="!regularRowsForPage(pg).length">
                <td :colspan="regularColumns.length" class="empty-row">暂无检验结果</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- ========== 4. 温馨提示 + 广东省HR标识 ========== -->
        <div class="tips-block">
          <div class="tips-content">
            <div v-for="(t, i) in warmTips" :key="i" class="tip-line">温馨提示: {{ t }}</div>
            <div v-if="hasMutualResult" class="tip-line tip-note">注意: 带"*"号为广东省检验结果互认标识</div>
          </div>
          <div class="hr-badge" :class="{ wide: !isUrineMode && !isMicroMode }">
            {{ isUrineMode ? '尿常规项目为广东省HR' : (isMicroMode ? '本报告为广东省HR' : '广东省HR') }}
          </div>
        </div>

        <!-- ========== 5. 备注横线 ========== -->
        <div class="remark-line">
          <span class="remark-lbl">备注:</span>
          <span class="remark-val">{{ remarkText }}</span>
        </div>

        <!-- ========== 6. 签名行 ========== -->
        <div class="sign-row">
          <div class="sign-cell"><span class="lbl">开嘱医生:</span><span class="val-line">{{ orderDoctor }}</span></div>
          <div class="sign-cell"><span class="lbl">采样人:</span><span class="val-line">{{ collectorName }}</span></div>
          <div class="sign-cell"><span class="lbl">接收人:</span><span class="val-line">{{ receiverName }}</span></div>
          <div class="sign-cell"><span class="lbl">检测医师:</span><span class="val-line">{{ testerName }}</span></div>
          <div class="sign-cell"><span class="lbl">{{ isUrineMode ? '报告人:' : '审核者:' }}</span><span class="val-line">{{ isUrineMode ? reporterName : auditorName }}</span></div>
        </div>

        <!-- ========== 7. 时间行 ========== -->
        <div class="time-row">
          <span class="tm-item">开嘱时间: {{ ft(orderTime) }}</span>
          <span class="tm-item">采样时间: {{ ft(collectTime) }}</span>
          <span class="tm-item">接收时间: {{ ft(receiveTime) }}</span>
          <span class="tm-item">报告时间: {{ ft(reportTime) }}</span>
        </div>

        <!-- ========== 8. 标本图像(仅首页) ========== -->
        <div v-if="pi === 0 && sampleImages.length" class="render-images">
          <div class="images-grid">
            <div v-for="(img, idx) in sampleImages" :key="idx" class="image-item">
              <img :src="img.imageUrl || img.url" :alt="img.imageName || img.title || '标本图'" />
              <div v-if="img.imageName || img.title" class="image-caption">{{ img.imageName || img.title }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import type { ReportTemplate, ReportVO, ResultVO } from '@/api/report'
import { getDefaultTemplate, loadLocalTemplate } from '@/api/report/template'

const props = defineProps<{
  data: ReportVO & Record<string, any>
  template?: ReportTemplate
  /** 显示双列尿常规 (左右分栏) */
  urineSplitMode?: boolean
  /** 兼容别名 (同 urineSplitMode) */
  dualColumn?: boolean
  fitToPage?: boolean
}>()

const localTemplate = ref<ReportTemplate>(getDefaultTemplate())

onMounted(() => {
  if (!props.template) localTemplate.value = loadLocalTemplate()
})

const template = computed(() => props.template || localTemplate.value)

// ============ 字段兜底取值 ============
function pick(key: string): string {
  const d = props.data as any
  const v = d?.[key]
  return v === null || v === undefined || v === '' ? '-' : String(v)
}
function formatGender(v: any): string {
  const n = Number(v)
  if (n === 1) return '男'
  if (n === 2) return '女'
  return v ? String(v) : '-'
}
const inpatientNo = computed(() => {
  const d = props.data as any
  return d?.inpatientNo || d?.visitNo || '-'
})
const specimenNo = computed(() => (props.data as any)?.specimenNo || (props.data as any)?.sampleNo || '-')
const specimenType = computed(() => (props.data as any)?.specimenType || (props.data as any)?.sampleType || '-')
const instrumentName = computed(() => {
  const d = props.data as any
  if (d?.instrumentName) return d.instrumentName
  const firstR = (d?.results || [])[0]
  return firstR?.instrumentName || '-'
})
const applyItemNames = computed(() => {
  const d = props.data as any
  if (d?.applyItemNames) return d.applyItemNames
  if (d?.testItems) return d.testItems
  const names = Array.from(new Set((d?.results || []).map((r: ResultVO) => r.panelName || r.testItemName)))
  return names.join('、') || '-'
})
const hospitalName = computed(() => (props.data as any)?.hospitalName || '医院名称')
const hospitalSubName = computed(() => (props.data as any)?.hospitalSubName || '')
const computedHospitalLogo = computed(() => (props.data as any)?.hospitalLogo || '')

// ============ 模式判断 ============
/** 微生物报告模式: organisms 有数据 或 结果为0且提示微生物 */
const allOrganisms = computed<any[]>(() => (props.data as any)?.organisms || [])

// ============ 微生物 - 阴性结论 / 血培养信息 ============
const hasMicroNegative = computed(() => Number((props.data as any)?.isNegative) === 1 || !!((props.data as any)?.negativeRemark))
const microNegativeText = computed(() => (props.data as any)?.negativeRemark || '培养48小时, 未见致病菌生长。')
const hasMicroBlood = computed(() =>
  !!((props.data as any)?.bottleNo) || !!((props.data as any)?.bottleType) || !!((props.data as any)?.bloodCultureResult))
const bloodCultureResultText = computed(() => {
  const v = (props.data as any)?.bloodCultureResult
  if (v === 'POSITIVE') return '阳性'
  if (v === 'NEGATIVE') return '阴性'
  return v ? String(v) : '-'
})
const isMicroMode = computed(() => {
  // 从模板类型判断 (若 templateType 携带 'micro')
  const tt = (template.value as any)?.templateType as string | undefined
  if (tt && ['micro', 'micro-negative', 'micro-blood'].includes(tt)) return true
  // 按数据: 有 organisms 且非空, 优先视为微生物报告
  if (allOrganisms.value.length) return true
  return false
})

const isUrineMode = computed(() => {
  if (isMicroMode.value) return false
  if (typeof props.urineSplitMode === 'boolean') return props.urineSplitMode
  if (typeof props.dualColumn === 'boolean') return props.dualColumn
  const keys = Array.from(new Set((props.data?.results || []).map((r: any) => r.groupKey)))
  if (keys.includes('urine-chem') || keys.includes('urine-sediment')) return true
  const names = (props.data?.results || []).map((r: ResultVO) => r.testItemName)
  const hasChem = names.some(n => /尿(比重|糖|蛋白|胆红素|胆原|酮体|潜血|白细胞|亚硝酸盐|pH|酸碱度|颜色|浊度)/.test(n))
  const hasSed = names.some(n => /(红细胞|白细胞|管型|上皮细胞|结晶|脓球|维生素C)/.test(n))
  return hasChem && hasSed
})

const reportTitle = computed(() => {
  const titleEl = template.value.elements.find(e => e.type === 'report-title')
  if (titleEl?.text) return titleEl.text
  if (isMicroMode.value) return `${hospitalName.value}医学检验中心 微生物检验报告单`
  if (isUrineMode.value) return `${hospitalName.value}医学检验中心 尿液分析报告单`
  return `${hospitalName.value}医学检验中心报告单`
})

// ============ 结果列配置(从template.elements读取,默认7列) ============
const regularColLabels: Record<string, string> = {
  idx: '序号', testItemName: '中文名称', englishCode: '英文缩写',
  resultValue: '结果', unit: '单位', refRange: '参考', testMethod: '检验方法',
  abnormalFlag: '异常', isCritical: '危急值', testTime: '检测时间', instrumentName: '仪器',
  lastResult: '上次结果', lastDate: '上次日期', lastFlag: '对比'
}
const DEFAULT_COLS_7 = ['idx', 'testItemName', 'englishCode', 'resultValue', 'unit', 'refRange', 'testMethod']
const COL_WIDTHS_MAP: Record<string, string> = {
  idx: '5%', testItemName: '16%', englishCode: '11%', resultValue: '11%',
  unit: '7%', refRange: '13%', testMethod: '14%',
  abnormalFlag: '6%', isCritical: '7%', testTime: '11%', instrumentName: '12%',
  lastResult: '9%', lastDate: '9%', lastFlag: '5%'
}

const regularColumns = computed<string[]>(() => {
  // 优先从 template 的 results-table 元素读 columns
  const resultsEl = template.value.elements.find(e => e.type === 'results-table' && Array.isArray(e.columns) && e.columns.length)
  if (resultsEl && resultsEl.columns && resultsEl.columns.length) return resultsEl.columns
  return DEFAULT_COLS_7
})
const regularColWidths = computed<string[]>(() =>
  regularColumns.value.map(c => COL_WIDTHS_MAP[c] || `${Math.floor(100 / regularColumns.value.length)}%`)
)

// 签名字段兜底
const orderDoctor = computed(() => (props.data as any)?.orderDoctor || (props.data as any)?.doctor || '________')
const collectorName = computed(() => (props.data as any)?.collectorName || (props.data as any)?.collector || '________')
const receiverName = computed(() => (props.data as any)?.receiverName || (props.data as any)?.receiver || '________')
const testerName = computed(() => (props.data as any)?.testerName || (props.data as any)?.checker || '________')
const auditorName = computed(() => {
  const d = props.data as any
  return d?.auditorName || d?.auditor || d?.reportDoctor || '________'
})
const reporterName = computed(() => {
  const d = props.data as any
  return d?.reporterName || d?.reportDoctor || d?.auditor || '________'
})

// 时间字段
const orderTime = computed(() => {
  const d = props.data as any
  return d?.orderTime || d?.applyTime || d?.createTime
})
const collectTime = computed(() => (props.data as any)?.collectTime)
const receiveTime = computed(() => (props.data as any)?.receiveTime)
const reportTime = computed(() => {
  const d = props.data as any
  return d?.reportTime || d?.publishTime || d?.auditTime || d?.createTime
})
function ft(t?: string) {
  if (!t) return '-'
  const d = dayjs(t)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : String(t)
}

// 温馨提示 & 互认
const warmTips = computed(() => {
  const d = props.data as any
  if (Array.isArray(d?.warmTips) && d.warmTips.length) return d.warmTips
  if (isMicroMode.value) {
    return ['本报告药敏结果采用CLSI M100 34th版标准判读;S=敏感,I=中介,R=耐药;培养结果仅供临床参考。']
  }
  const hasHepatitis = (d?.results || []).some((r: ResultVO) =>
    /HBsAg|乙肝/.test(r.testItemName) || /HBsAg|乙肝/.test(r.englishCode || '')
  )
  if (hasHepatitis) {
    return ['乙肝表面抗原(HBsAg)阳性者, 建议进行乙型肝炎病毒DNA荧光定量检测, 便于进一步诊断病情及治疗。']
  }
  return []
})
function isMutual(r: ResultVO) {
  return Number((r as any).mutuallyRecognized) === 1
}
const hasMutualResult = computed(() => {
  const d = props.data as any
  return (d?.results || []).some(isMutual) || (d?.mutuallyRecognized === 1)
})

const remarkText = computed(() => {
  const d = props.data as any
  if (d?.remark) return d.remark
  const el = template.value.elements.find(e => e.type === 'remark' || e.type === 'custom-text')
  return el?.text || ''
})

const sampleImages = computed(() => (props.data as any)?.sampleImages || [])

// ============ 结果分组 ============
const allResults = computed<ResultVO[]>(() => props.data?.results || [])

const urineChemRows = computed(() =>
  allResults.value.filter(r => {
    const g = (r as any).groupKey
    if (g) return g === 'urine-chem'
    return /尿(比重|糖|蛋白|胆红素|胆原|酮体|潜血|白细胞|亚硝酸盐|pH|酸碱度|颜色|浊度)/.test(r.testItemName)
  })
)
const urineSedimentRows = computed(() =>
  allResults.value.filter(r => {
    const g = (r as any).groupKey
    if (g) return g === 'urine-sediment'
    return /(红细胞|白细胞|管型|上皮细胞|结晶|脓球|维生素C)/.test(r.testItemName)
  })
)
const regularRows = computed(() =>
  (isUrineMode.value || isMicroMode.value) ? [] : allResults.value
)

function englishCode(r: ResultVO): string {
  const anyR = r as any
  return anyR.englishCode || anyR.englishName || anyR.testItemCode || '-'
}
function testMethod(r: ResultVO): string {
  const anyR = r as any
  return anyR.testMethod || anyR.method || anyR.instrumentName || '-'
}

// ============ 格式化工具函数 ============
function formatCell(col: string, r: ResultVO): string {
  const v = (r as any)[col]
  if (col === 'resultValue') {
    if (v === null || v === undefined || v === '') return (r as any).resultText || '-'
    return String(v)
  }
  return v ?? '-'
}
function formatResultCell(col: string, r: ResultVO): string {
  if (col === 'testItemName') return r.testItemName || '-'
  if (col === 'englishCode') return englishCode(r)
  if (col === 'resultValue') return formatCell('resultValue', r)
  if (col === 'unit') return r.unit || '-'
  if (col === 'refRange') return r.refRange || '-'
  if (col === 'testMethod') return testMethod(r)
  if (col === 'testTime') return ft((r as any).testTime)
  if (col === 'instrumentName') return (r as any).instrumentName || '-'
  return ((r as any)[col] ?? '-') as string
}
function regularCellClass(col: string, r: ResultVO): Record<string, boolean> {
  const cls: Record<string, boolean> = {}
  if (col === 'idx') { cls['c-idx'] = true; return cls }
  if (col === 'resultValue') {
    if (Number((r as any).isCritical) === 1) cls['critical-cell'] = true
    else if (r.abnormalFlag === 'H') cls['high-cell'] = true
    else if (r.abnormalFlag === 'L') cls['low-cell'] = true
  }
  return cls
}
function rowClass(r: ResultVO) {
  return {
    'critical-row': Number(r.isCritical) === 1,
    'high-row': r.abnormalFlag === 'H',
    'low-row': r.abnormalFlag === 'L'
  }
}
function flagClass(flag: any): string {
  if (flag === '↑' || String(flag).includes('升')) return 'high-cell'
  if (flag === '↓' || String(flag).includes('降')) return 'low-cell'
  return 'neutral-flag'
}
function sensText(result: any): string {
  const m: Record<string, string> = { S: '敏感(S)', I: '中介(I)', R: '耐药(R)' }
  return m[result] || (result || '-')
}
function sensClass(result: any): string {
  if (result === 'S') return 's-sensitive'
  if (result === 'I') return 'i-intermediate'
  if (result === 'R') return 'r-resistant'
  return ''
}

// ============ 分页 ============
const REGULAR_ROW_H = 28
const URINE_ROW_H = 28
const MICRO_ORG_BLOCK_H = 360 // 每个细菌鉴定+药敏块预估高度

const regularRowsPerPage = computed(() => {
  const table = template.value.elements.find(e => e.type === 'results-table' && e.visible !== false)
  const h = table?.height || 420
  return Math.max(1, Math.floor(h / REGULAR_ROW_H))
})
const urineRowsPerPage = computed(() => {
  const table = template.value.elements.find(e => e.type === 'results-table' && e.visible !== false)
  const h = table?.height || 420
  return Math.max(1, Math.floor(h / URINE_ROW_H))
})
const microOrgsPerPage = computed(() => {
  const sens = template.value.elements.find(e => e.type === 'micro-sensitivity')
  const h = sens?.height || 460
  return Math.max(1, Math.floor(h / MICRO_ORG_BLOCK_H))
})

const pageCount = computed(() => {
  if (isMicroMode.value) {
    const n = allOrganisms.value.length
    return Math.max(1, Math.ceil(n / Math.max(1, microOrgsPerPage.value)))
  }
  if (isUrineMode.value) {
    const n = Math.max(urineChemRows.value.length, urineSedimentRows.value.length)
    return Math.max(1, Math.ceil(n / urineRowsPerPage.value))
  }
  if (!regularRows.value.length) return 1
  return Math.max(1, Math.ceil(regularRows.value.length / regularRowsPerPage.value))
})
const renderPages = computed<number[]>(() => Array.from({ length: pageCount.value }, (_, i) => i))

function regularRowsForPage(pg: number): ResultVO[] {
  if (isUrineMode.value || isMicroMode.value) return []
  const s = pg * regularRowsPerPage.value
  return regularRows.value.slice(s, s + regularRowsPerPage.value)
}
function urineChemRowsForPage(pg: number): ResultVO[] {
  const s = pg * urineRowsPerPage.value
  return urineChemRows.value.slice(s, s + urineRowsPerPage.value)
}
function urineSedimentRowsForPage(pg: number): ResultVO[] {
  const s = pg * urineRowsPerPage.value
  return urineSedimentRows.value.slice(s, s + urineRowsPerPage.value)
}
function organismsForPage(pg: number): any[] {
  if (!isMicroMode.value) return []
  const s = pg * microOrgsPerPage.value
  return allOrganisms.value.slice(s, s + microOrgsPerPage.value)
}

// ============ 画布样式 ============
const pageStyle = computed<Record<string, string>>(() => ({
  position: 'relative',
  width: template.value.canvasWidth + 'px',
  height: template.value.canvasHeight + 'px',
  background: template.value.backgroundColor || '#fff',
  marginBottom: '16px',
  overflow: 'hidden'
}))
</script>

<style scoped lang="scss">
.report-renderer-wrapper { background: #f5f7fa; padding: 16px; overflow: auto; }
.report-renderer {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  background: #fff;
  padding: 24px 28px 16px 28px;
  font-family: "SimSun", "宋体", "Microsoft YaHei", serif;
  font-size: 12px;
  color: #000;
  box-sizing: border-box;
}

/* ========== 1. 标题区 ========== */
.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 2px solid #000;
  padding-bottom: 8px;
  margin-bottom: 8px;
}
.header-left { display: flex; align-items: center; gap: 10px; min-width: 26%; }
.hospital-logo { height: 48px; max-width: 80px; object-fit: contain; }
.hospital-names { display: flex; flex-direction: column; }
.hospital-main { font-size: 15px; font-weight: 700; letter-spacing: 1px; }
.hospital-sub { font-size: 12px; color: #333; margin-top: 2px; }
.header-center { flex: 1; text-align: center; }
.report-title { font-size: 22px; font-weight: 800; letter-spacing: 2px; }
.header-right { min-width: 26%; text-align: right; }
.page-indicator { font-size: 12px; color: #333; }

/* ========== 2. 患者信息区 ========== */
.info-block { margin-bottom: 6px; }
.info-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #333;
  line-height: 1.9;
}
.info-cell {
  flex: 1;
  padding: 0 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.info-cell-wide { flex: 1.6; }
.info-cell-block { flex: 1; }
.lbl { color: #333; flex-shrink: 0; }
.val { color: #000; font-weight: 500; }

/* ========== 3. 表格区 ========== */
.render-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  margin-top: 4px;
  margin-bottom: 6px;
  th, td {
    border: 1px solid #000;
    padding: 4px 6px;
    text-align: left;
    vertical-align: middle;
  }
  th {
    background: #f5f7fa;
    font-weight: 700;
    text-align: center;
  }
  .empty-row { text-align: center; color: #888; padding: 16px; }
}
/* 常规表格 */
.regular-table .c-idx { text-align: center; white-space: nowrap; }
.hr-star { color: #d9001b; font-weight: 700; margin-right: 2px; }
.idx-num { display: inline-block; }
/* 尿常规双栏 */
.urine-wrap { display: flex; align-items: stretch; margin-top: 4px; margin-bottom: 6px; gap: 0; }
.urine-half { flex: 1; margin: 0; }
.urine-divider { width: 2px; background: #000; }
.urine-caption {
  caption-side: top;
  text-align: center;
  font-weight: 700;
  padding: 4px 0;
  color: #000;
  font-size: 13px;
}

/* 异常/危急值 */
.critical-row td { color: #d9001b; font-weight: 800; }
.high-row td:nth-child(4) { color: #d9001b; font-weight: 700; }
.low-row td:nth-child(4) { color: #2f55d4; font-weight: 700; }
.critical-cell { color: #d9001b; font-weight: 800; }
.high-cell { color: #d9001b; font-weight: 700; }
.low-cell { color: #2f55d4; font-weight: 700; }
.neutral-flag { color: #606266; font-weight: 600; }

/* 微生物模式样式 */
.micro-wrap { margin-top: 4px; margin-bottom: 6px; }
.micro-block + .micro-block { margin-top: 14px; }
.micro-title {
  font-weight: 700;
  font-size: 13px;
  padding: 4px 0;
  color: #000;
}
.micro-organism-table .mo-name { font-weight: 700; }
.micro-sens-table .s-sensitive { color: #2f9e44; font-weight: 700; background: #ebfbee; text-align: center; }
.micro-sens-table .i-intermediate { color: #e67700; font-weight: 700; background: #fff9db; text-align: center; }
.micro-sens-table .r-resistant  { color: #d9001b; font-weight: 800; background: #fff5f5; text-align: center; }
/* 微生物 - 阴性结论 & 血培养信息 */
.micro-negative-block .micro-negative-text {
  border: 1px solid #000;
  padding: 10px 12px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.8;
}
.micro-blood-block .micro-bc-table .bc-result { font-weight: 700; text-align: center; }

/* ========== 4. 温馨提示 + HR ========== */
.tips-block {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-top: 4px;
  margin-bottom: 4px;
}
.tips-content { flex: 1; }
.tip-line { font-size: 11.5px; color: #333; line-height: 1.6; }
.tip-note { color: #000; }
.hr-badge {
  flex-shrink: 0;
  border: 1.5px solid #000;
  padding: 4px 10px;
  font-weight: 700;
  font-size: 12px;
  white-space: nowrap;
  text-align: center;
}
.hr-badge.wide { min-width: 108px; }

/* ========== 5. 备注 ========== */
.remark-line {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #000;
  border-top: 1px solid #000;
  line-height: 28px;
  padding: 0 4px;
  margin-top: 4px;
  margin-bottom: 4px;
}
.remark-lbl { flex-shrink: 0; }
.remark-val { flex: 1; color: #333; }

/* ========== 6. 签名行 ========== */
.sign-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  line-height: 2.2;
  font-size: 12px;
}
.sign-cell {
  display: flex;
  align-items: baseline;
  gap: 4px;
  flex: 1;
  padding: 0 4px;
}
.val-line {
  min-width: 72px;
  border-bottom: 1px solid #333;
  display: inline-block;
  padding: 0 6px;
  text-align: center;
  font-weight: 500;
}

/* ========== 7. 时间行 ========== */
.time-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  line-height: 1.8;
  padding: 0 4px;
  color: #000;
}
.tm-item { white-space: nowrap; }

/* ========== 8. 标本图像 ========== */
.render-images { width: 100%; margin-top: 4px; }
.images-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.image-item { flex: 0 0 auto; max-width: 32%; }
.image-item img { display: block; max-width: 100%; max-height: 140px; object-fit: contain; border: 1px solid #dcdfe6; }
.image-caption { font-size: 11px; color: #606266; text-align: center; margin-top: 2px; }
</style>
