<script setup lang="ts">
import { computed } from 'vue'
import type { TechnicianSample } from '@/api/sample'

interface Props {
  row: TechnicianSample
  index: number
  isChecked: boolean
  isSelected: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'cardClick', row: TechnicianSample, ev: MouseEvent): void
  (e: 'cardContextMenu', row: TechnicianSample, ev: MouseEvent): void
  (e: 'checkChange', row: TechnicianSample, checked: boolean): void
  (e: 'unmatchedMatch', row: TechnicianSample): void
  (e: 'unmatchedDiscard', row: TechnicianSample): void
  (e: 'unmatchedToQc', row: TechnicianSample): void
}>()

/** 是否为无主标本 */
const isUnmatched = computed(() => !!props.row.isUnmatched)

/** 状态映射（Element Plus 允许的 type：primary/success/info/warning/danger） */
const statusType = computed<'primary' | 'success' | 'info' | 'warning' | 'danger'>(() => {
  if (isUnmatched.value) return 'warning'   // 无主标本：警告色（琥珀/橙黄）
  switch (props.row.status) {
    case 0: return 'warning'
    case 1: return 'primary'   // 终审：主色调
    case 3: return 'success'   // 已完成：成功绿
    case 2: return 'danger'    // 急诊：危险红（与顶部「急诊」徽章区分使用）
    default: return 'info'
  }
})
const statusText = computed<string>(() => {
  if (isUnmatched.value) {
    const n = props.row.unmatchedResultCount ?? 0
    return `无主${n ? ' · ' + n + '项' : ''}`
  }
  switch (props.row.status) {
    case 0: return '待审'
    case 1: return '终审'
    case 2: return '急诊'
    case 3: return '已完成'
    default: return '待核收'
  }
})

/** 将 testItems 字符串按常用分隔符拆分为单个项目数组（用于多行展示） */
const testItemList = computed<string[]>(() => {
  const s = props.row.testItems
  if (!s) return []
  const cleaned = String(s).replace(/\([^)]*\)/g, '').trim()
  if (!cleaned) return []
  const items = cleaned.split(/[,，;；、\s|/\\]+/).map(i => i.trim()).filter(Boolean)
  // 去重保序
  const seen = new Set<string>()
  const out: string[] = []
  for (const it of items) {
    if (seen.has(it)) continue
    seen.add(it)
    out.push(it)
  }
  return out
})

/** 无主/普通标本：项目摘要（兼容旧逻辑，未拆数组时备用） */
const testItemsShort = computed<string>(() => {
  if (isUnmatched.value && props.row.unmatchedResultSummary) {
    const s = props.row.unmatchedResultSummary
    return s.length <= 38 ? s : s.slice(0, 38) + '…'
  }
  const list = testItemList.value
  if (list.length === 0) return '-'
  // 这里返回完整内容（不再强制截断），由模板里的 tooltip 承载更多细节
  return list.join(' · ')
})

function fmtShortTime(t: string | undefined): string {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return t
  }
}

/* ============ 合并行文本：把多字段拼成一行纯文本，交给浏览器自然换行 ============ */

/** 行1：标本号 / 条码号 / 就诊号（编号类信息，不含 900 开头的长号） */
const nosLine = computed<string>(() => {
  const parts: string[] = []
  if (props.row.sampleNo) parts.push(`标本号 ${props.row.sampleNo}`)
  if (props.row.sampleBarcode) parts.push(`条码号 ${props.row.sampleBarcode}`)
  if (props.row.visitNo) parts.push(`就诊号 ${props.row.visitNo}`)
  return parts.join('　')
})
/** 行2：科室 / 送检医生 / 标本类型 */
const deptLine = computed<string>(() => {
  const parts: string[] = []
  if (props.row.department) parts.push(`科室 ${props.row.department}`)
  if (props.row.doctor) parts.push(`医生 ${props.row.doctor}`)
  const st = (props.row as any).sampleType
  if (st) parts.push(`类型 ${st}`)
  return parts.join('　')
})
/** 行3：三个时间 */
const timesLine = computed<string>(() => {
  const parts: string[] = []
  if (props.row.receiveTime) parts.push(`核收 ${fmtShortTime(props.row.receiveTime)}`)
  if (props.row.testTime) parts.push(`检验 ${fmtShortTime(props.row.testTime)}`)
  if (props.row.reportTime) parts.push(`报告 ${fmtShortTime(props.row.reportTime)}`)
  return parts.join('　')
})
/** 无主标本：接收时间 + 样本号 + 仪器 */
const unmatchedLine = computed<string>(() => {
  const parts: string[] = []
  if (props.row.unmatchedSampleNo) parts.push(`样本号 ${props.row.unmatchedSampleNo}`)
  if (props.row.unmatchedInstrumentName) parts.push(`仪器 ${props.row.unmatchedInstrumentName}`)
  if (props.row.receiveTime || (props.row as any).createTime) {
    parts.push(`接收 ${fmtShortTime(props.row.receiveTime || (props.row as any).createTime)}`)
  }
  if (props.row.unmatchedResultCount) parts.push(`指标 ${props.row.unmatchedResultCount} 项`)
  return parts.join('　')
})

function _onCheckChange(val: boolean | string | number) {
  emit('checkChange', props.row, !!val)
}

function _onMatch(ev: MouseEvent) {
  ev.stopPropagation()
  emit('unmatchedMatch', props.row)
}
function _onDiscard(ev: MouseEvent) {
  ev.stopPropagation()
  emit('unmatchedDiscard', props.row)
}
function _onToQc(ev: MouseEvent) {
  ev.stopPropagation()
  emit('unmatchedToQc', props.row)
}
</script>

<template>
  <div
    class="sample-card"
    :class="{
      'is-selected': isSelected,
      'is-checked': isChecked,
      'is-urgent': !!row.isUrgent && !isUnmatched,
      'is-even': (index % 2) === 1,
      'is-unmatched': isUnmatched
    }"
    tabindex="0"
    @click="emit('cardClick', row, $event)"
    @contextmenu.prevent="emit('cardContextMenu', row, $event)"
  >
    <!-- ===== 患者信息：单独一块（仅姓名 + 性别年龄就诊 + 选择框 + 状态标签）—— 绝对不再混标本号/ID号 ===== -->
    <div class="sc-patient">
      <div class="sc-patient__check" @click.stop v-if="!isUnmatched">
        <el-checkbox
          :model-value="isChecked"
          @change="_onCheckChange"
        />
      </div>
      <div v-else class="sc-patient__um-mark" @click.stop title="仪器回传未匹配标本">
        <span class="um-dot" />
      </div>

      <div class="sc-patient__main">
        <span class="sc-patient__name" :class="{ 'um-name': isUnmatched }">
          {{ row.patientName || '-' }}
        </span>
        <span v-if="!isUnmatched && (row.patientGender || row.patientAge || row.visitType)" class="sc-patient__meta">
          {{ row.patientGender }}<template v-if="row.patientAge"> · {{ row.patientAge }}岁</template><template v-if="row.visitType"> · {{ row.visitType }}</template>
        </span>
      </div>

      <div class="sc-patient__tags">
        <el-tag v-if="!isUnmatched && row.isUrgent" type="danger" size="small" effect="dark">急诊</el-tag>
        <el-tag v-if="isUnmatched" type="danger" size="small" effect="dark" style="font-weight: 600">
          <el-icon style="vertical-align: -2px"><Warning /></el-icon> 无主
        </el-tag>
        <el-tag :type="statusType" size="small">{{ statusText }}</el-tag>
      </div>
    </div>

    <!-- ===== 其他信息合并块（标本号/条码号/就诊号在这里，ID 也在这里 —— 与患者信息块彻底分开） ===== -->
    <div class="sc-detail">
      <!-- 无主标本 -->
      <template v-if="isUnmatched">
        <div v-if="unmatchedLine" class="sc-detail__line" :title="unmatchedLine">{{ unmatchedLine }}</div>
        <div v-if="row.testItems" class="sc-detail__line"><b>指标</b> {{ row.testItems }}</div>
        <div v-if="row.unmatchedResultSummary" class="sc-detail__line" :title="row.unmatchedResultSummary"><b>回传</b> {{ testItemsShort }}</div>
      </template>

      <!-- 普通标本：编号类信息（标本号 / 条码号 / 就诊号）放第一行，不再显示 900 开头长号 -->
      <template v-else>
        <div v-if="nosLine" class="sc-detail__line sc-detail__line--ids" :title="nosLine">{{ nosLine }}</div>
        <div v-if="deptLine" class="sc-detail__line" :title="deptLine">{{ deptLine }}</div>
        <!-- 检验项目：多个项目分行/tag 显示，数量过多时自动换行高度自适应 -->
        <div v-if="row.testItems" class="sc-detail__line sc-detail__line--items" :title="row.testItems">
          <span class="items-label">
            <b>项目</b>
            <i v-if="testItemList.length > 1" class="items-count">{{ testItemList.length }}项</i>
          </span>
          <span v-if="testItemList.length === 0" class="items-empty">-</span>
          <span v-else class="items-chips">
            <span
              v-for="(name, i) in testItemList"
              :key="i"
              class="item-chip"
              :title="name"
            >{{ name }}</span>
          </span>
        </div>
        <div v-if="timesLine" class="sc-detail__line sc-detail__line--time" :title="timesLine">{{ timesLine }}</div>
        <div v-if="(row as any).diagnosis" class="sc-detail__line" :title="(row as any).diagnosis"><b>诊断</b> {{ (row as any).diagnosis }}</div>
      </template>

      <!-- 无主标本：操作按钮区 -->
      <div v-if="isUnmatched" class="sc-detail__actions" @click.stop>
        <el-button type="success" size="small" :icon="CircleCheck" @click="_onMatch">认领</el-button>
        <el-button type="warning" size="small" :icon="Monitor" @click="_onToQc">转质控</el-button>
        <el-button type="danger" size="small" :icon="Delete" plain @click="_onDiscard">作废</el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { CircleCheck, Delete, Monitor, Warning } from '@element-plus/icons-vue'
export default { name: 'SampleCard' }
</script>

<style scoped lang="scss">
/* =====================================================================
   布局原则（防重叠三铁律）：
   1. 卡片 = 两块：患者信息块（单独） + 其他信息合并块
   2. 所有行都是 display:block 纯文本行，行内用 inline 文本自然换行
      —— 没有任何 flex 对齐 / line-clamp / inline-flex 混排，浏览器标准流
         一定正确累计高度，从结构上杜绝文字叠压
   3. 行与行之间只用 margin-bottom 分隔，每行 line-height:1.5
   ===================================================================== */
.sample-card {
  position: relative;
  display: block;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px 10px 9px;
  margin-bottom: 8px;
  cursor: pointer;
  box-sizing: border-box;
  width: 100%;
  transition: border-color .15s ease, box-shadow .15s ease;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 2px 8px rgba(64, 158, 255, .12);
  }
  &.is-selected {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px rgba(64, 158, 255, .18);
  }
  &.is-checked {
    background: rgba(64, 158, 255, .04);
  }
  &.is-urgent {
    border-left: 3px solid var(--el-color-danger);
  }
  &.is-even {
    background: #fbfbfb;
  }
  &.is-unmatched {
    border: 1px solid #f56c6c;
    box-shadow: 0 0 0 1px rgba(245, 108, 108, .18);
    background: linear-gradient(180deg, #fff7f7 0%, #ffffff 100%);
    &::before {
      content: "";
      position: absolute;
      inset: 0 0 auto 0;
      height: 3px;
      border-radius: 8px 8px 0 0;
      background: repeating-linear-gradient(45deg, #f56c6c 0 8px, #f79898 8px 16px);
    }
    .um-name { color: #c45656; font-weight: 700; }
  }
}

/* ===== 患者信息块：单独一行（勾选框 + 姓名 + meta + 标签）
   —— 绝对不与任何编号类/ID 类信息混排，用更厚的虚线 + 更大 padding-bottom 明确分界 ===== */
.sc-patient {
  display: flex;
  align-items: flex-start;   /* 单行结构，永不换行堆叠 */
  gap: 8px;
  width: 100%;
  padding-bottom: 8px;                 /* 加大分隔距离：8px 底部内边距 */
  border-bottom: 1.5px dashed var(--el-border-color);  /* 虚线 1.5px + 颜色更深，分界比之前更清楚 */
  margin-bottom: 8px;                  /* 与其他信息块之间 8px 外边距 + 8px 内边距共 16px 视觉分隔 */

  &__check, &__um-mark {
    flex-shrink: 0;
    width: 22px;
    padding-top: 2px;
  }
  &__um-mark {
    .um-dot {
      display: inline-block;
      width: 10px; height: 10px;
      border-radius: 50%;
      background: #f56c6c;
      box-shadow: 0 0 0 3px rgba(245, 108, 108, .22);
    }
  }
  &__main {
    flex: 1;
    min-width: 0;            /* 允许内部文本收缩，绝不撑破卡片 */
    display: block;
    line-height: 1.5;
  }
  &__name {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin-right: 6px;
  }
  &__meta {
    font-size: 12px;
    color: #909399;
    /* 不强制 nowrap：窄屏时 meta 允许换行到姓名下方第二行，
       —— 不会与其他行信息重叠，因为 sc-patient 是独立大盒
       —— 也不会和标签列冲突（标签列 flex-shrink:0，主区 min-width:0 允许收缩换行） */
    white-space: normal;
    word-break: keep-all;
    display: inline;
  }
  &__tags {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 3px;
  }
}

/* ===== 其他信息合并块：每行一个纯文本 block 行 ===== */
.sc-detail {
  display: block;           /* 纯块级流：行高由浏览器累计，绝不重叠 */
  width: 100%;

  &__line {
    display: block;
    width: 100%;
    font-size: 12px;
    line-height: 1.5;        /* 行高固定，多行文本自然向下换行 */
    color: #606266;
    margin-bottom: 2px;
    word-break: break-all;
    overflow-wrap: break-word;
    white-space: normal;

    b {
      color: #909399;
      font-weight: 500;
      margin-right: 4px;
    }
    &:last-child { margin-bottom: 0; }

    /* 编号类行：与患者信息虚线紧邻的第一行，颜色稍深，语义上更连贯但绝对不覆盖姓名块 */
    &--ids {
      color: #3a4049;
      font-weight: 500;
      font-size: 11.8px;
      margin-bottom: 2px;
      letter-spacing: .2px;
    }
    /* 时间行：更淡的小字 */
    &--time {
      color: #909399;
      font-size: 11.5px;
    }

    /* 检验项目行：多个项目以小 tag 形式分行展示，自动换行，高度自适应 */
    &--items {
      display: flex;
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 4px 0;
      margin-bottom: 3px;

      .items-label {
        display: inline-flex;
        align-items: center;
        flex-shrink: 0;
        gap: 4px;
        margin-right: 4px;
        line-height: 18px;
      }
      .items-count {
        font-style: normal;
        font-size: 11px;
        padding: 0 5px;
        border-radius: 10px;
        background: #ecf3ff;
        color: var(--el-color-primary);
        font-weight: 600;
      }
      .items-empty {
        color: #c0c4cc;
      }
      .items-chips {
        flex: 1;
        min-width: 0;
        display: inline-flex;
        flex-wrap: wrap;
        gap: 4px;
      }
      .item-chip {
        display: inline-block;
        padding: 1px 8px;
        border: 1px solid #d9e2f2;
        background: #f5f8ff;
        color: #2c3e50;
        border-radius: 10px;
        font-size: 12px;
        line-height: 18px;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;

        &:hover {
          background: #eaf0ff;
          border-color: var(--el-color-primary-light-5);
        }
      }
    }
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px dashed #f5c2c2;
  }
}

/* ============ 响应式：只压缩字号/间距，不隐藏任何信息 ============ */
@media (max-width: 1280px) {
  .sample-card {
    padding: 7px 9px 8px;
    margin-bottom: 7px;
  }
  .sc-patient__name { font-size: 14px; }
  .sc-detail__line { font-size: 11.5px; }
}
@media (max-width: 1024px) {
  .sample-card {
    padding: 6px 8px 7px;
    margin-bottom: 6px;
  }
  .sc-patient {
    gap: 6px;
    padding-bottom: 5px;
    margin-bottom: 5px;
    &__name { font-size: 13.5px; }
    &__meta { font-size: 11px; }
  }
  .sc-detail__line {
    font-size: 11px;
    line-height: 1.45;
    margin-bottom: 1px;
    &--time { font-size: 10.8px; }
  }
}
@media (max-width: 820px) {
  .sample-card {
    padding: 5px 7px 6px;
    margin-bottom: 6px;
  }
  .sc-patient__check, .sc-patient__um-mark { width: 20px; }
  .sc-detail__line { font-size: 10.8px; }
}

/* 左列拖到特别窄（<=300px）时：进一步紧凑，信息仍然全部显示 */
:global(.left-col--cards[data-left-narrow="true"]) .sample-card {
  padding: 5px 7px 6px !important;
  margin-bottom: 6px !important;

  .sc-patient__name { font-size: 13px !important; }
  .sc-patient__meta { font-size: 10.8px !important; }
  .sc-detail__line {
    font-size: 10.8px !important;
    line-height: 1.45 !important;
    &--time { font-size: 10.5px !important; }
  }
}
</style>
