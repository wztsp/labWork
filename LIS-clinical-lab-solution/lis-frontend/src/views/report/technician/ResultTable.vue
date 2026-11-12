<template>
  <el-table
    :data="items"
    size="small"
    border
    stripe
    class="result-extended-table"
    :header-cell-style="{ background: '#f5f7fa', color: '#606266' }"
  >
    <el-table-column v-if="isVisible('no')" label="NO." width="48" align="center" fixed>
      <template #default="{ $index }">{{ $index + 1 }}</template>
    </el-table-column>

    <!-- 指标名称: 仅显示中文名, 英文名单独一列, 检验项目由分组标题展示 -->
    <el-table-column v-if="isVisible('name')" label="指标名称" min-width="130" fixed show-overflow-tooltip>
      <template #default="{ row }">
        <span
          class="indicator-name"
          :class="{ 'text-danger': row.isCritical, 'abnormal-item': isAbnormal(row) }"
        >{{ row.testItemName }}</span>
      </template>
    </el-table-column>

    <!-- 英文名单独一列 -->
    <el-table-column v-if="isVisible('en')" label="英文名" min-width="90" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="indicator-en">{{ row.itemNameEn || '-' }}</span>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('code')" prop="testItemCode" label="项目代码" width="90" />

    <el-table-column v-if="isVisible('result')" label="结果" width="130" align="center">
      <template #default="{ row }">
        <div class="value-wrapper">
          <el-input
            v-model="row.resultValue"
            size="small"
            :disabled="!editable"
            :class="{ 'abnormal': isAbnormal(row), 'critical': row.isCritical }"
            @change="onResultChange(row)"
          />
          <span class="arrow-flag" v-if="getArrowFlag(row)">{{ getArrowFlag(row) }}</span>
        </div>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('unit')" label="单位" width="70" align="center">
      <template #default="{ row }"><span class="unit-text">{{ row.unit || '-' }}</span></template>
    </el-table-column>

    <el-table-column v-if="isVisible('ref')" label="参考范围" width="120" align="center">
      <template #default="{ row }">{{ row.refRange || '-' }}</template>
    </el-table-column>

    <el-table-column v-if="isVisible('hint')" label="提示" width="70" align="center">
      <template #default="{ row }">
        <el-tag v-if="row.isCritical" type="danger" size="small">危急</el-tag>
        <span v-else-if="getArrowFlag(row)" class="abnormal-text">{{ getArrowFlag(row) }}</span>
        <span v-else class="text-green">正常</span>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('lastResult')" label="上次结果" width="100" align="center">
      <template #default="{ row }">
        <div class="last-value-cell">
          <span class="last-value">{{ row.lastResultValue || '-' }}</span>
          <span v-if="row.lastAbnormalFlag" class="last-flag">{{ row.lastAbnormalFlag === 'H' ? '↑' : row.lastAbnormalFlag === 'L' ? '↓' : '' }}</span>
        </div>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('lastFlag')" label="上次标识" width="80" align="center">
      <template #default="{ row }">
        <span v-if="row.lastAbnormalFlag">{{ row.lastAbnormalFlag === 'H' ? '偏高' : row.lastAbnormalFlag === 'L' ? '偏低' : '正常' }}</span>
        <span v-else>-</span>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('lastTime')" label="上次时间" width="135" align="center">
      <template #default="{ row }">
        <span v-if="row.lastResultTime" style="font-size:11px">{{ formatTime(row.lastResultTime as any) }}</span>
        <span v-else>-</span>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('od')" label="OD值" width="85" align="center">
      <template #default="{ row }"><span>{{ row.odValue ?? '-' }}</span></template>
    </el-table-column>

    <el-table-column v-if="isVisible('cutoff')" label="CUTOFF值" width="90" align="center">
      <template #default="{ row }"><span>{{ row.cutoffValue ?? '-' }}</span></template>
    </el-table-column>

    <el-table-column v-if="isVisible('sc')" label="S/CO值" width="90" align="center">
      <template #default="{ row }"><span>{{ row.scRatio ?? '-' }}</span></template>
    </el-table-column>

    <el-table-column v-if="isVisible('autoAudit')" label="自动审核" width="80" align="center">
      <template #default="{ row }">
        <el-tag v-if="row.autoAudit === 1" type="success" size="small">通过</el-tag>
        <el-tag v-else-if="row.autoAudit === 2" type="warning" size="small" :title="row.autoAuditMessage || ''">异常</el-tag>
        <span v-else>-</span>
      </template>
    </el-table-column>

    <el-table-column v-if="isVisible('autoAuditMsg')" label="自动审核异常信息" min-width="160" align="center" show-overflow-tooltip>
      <template #default="{ row }"><span class="auto-audit-msg">{{ row.autoAuditMessage || '-' }}</span></template>
    </el-table-column>

    <el-table-column v-if="isVisible('knowledge')" label="项目知识" min-width="160" align="center" show-overflow-tooltip>
      <template #default="{ row }"><span class="item-knowledge">{{ row.itemKnowledge || '-' }}</span></template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { TechnicianResult } from '@/api/sample'

const props = defineProps<{
  items: TechnicianResult[]
  enabledColumns: string[]
  editable: boolean
}>()

const emit = defineEmits<{
  (e: 'result-change', row: TechnicianResult): void
}>()

function isVisible(prop: string) {
  return props.enabledColumns.includes(prop)
}

function isAbnormal(row: TechnicianResult) {
  return row.abnormalFlag === 'H' || row.abnormalFlag === 'L'
}

function getArrowFlag(row: TechnicianResult): string {
  if (row.isCritical) return '↑↓'
  if (row.abnormalFlag === 'H' || row.abnormalFlag === '高') return '↑'
  if (row.abnormalFlag === 'L' || row.abnormalFlag === '低') return '↓'
  if (row.resultValue && row.refRange) {
    const val = parseFloat(row.resultValue)
    if (!isNaN(val)) {
      const m = String(row.refRange).match(/([\d.]+)\s*[-~]\s*([\d.]+)/)
      if (m) {
        const low = parseFloat(m[1])
        const high = parseFloat(m[2])
        if (val > high) return '↑'
        if (val < low) return '↓'
      }
    }
  }
  return ''
}

function formatTime(time: string | undefined) {
  if (!time) return '-'
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

function onResultChange(row: TechnicianResult) {
  emit('result-change', row)
}
</script>

<style scoped lang="scss">
.result-extended-table {
  :deep(.el-table__cell) {
    padding: 4px 6px;
    font-size: 12px;
  }
}
.indicator-name {
  font-weight: 500;
  color: #303133;
}
.indicator-en {
  font-size: 11px;
  color: #909399;
  font-style: italic;
}
.value-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.value-wrapper .el-input {
  flex: 1;
}
.abnormal-item {
  color: #e6a23c;
}
.text-danger {
  color: #f56c6c;
}
.text-green {
  color: #67c23a;
}
.unit-text {
  color: #909399;
  font-size: 11px;
}
.abnormal-text {
  color: #f56c6c;
  font-weight: 600;
  font-size: 14px;
}
.arrow-flag {
  font-size: 16px;
  font-weight: 700;
  color: #f56c6c;
  min-width: 18px;
}
.last-value-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
}
.last-value {
  color: #606266;
}
.last-flag {
  color: #f56c6c;
  font-weight: 700;
}
.auto-audit-msg {
  color: #e6a23c;
  font-size: 11px;
}
.item-knowledge {
  color: #606266;
  font-size: 11px;
}
</style>