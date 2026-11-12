<template>
  <div class="reagent-scan-bar">
    <el-input
      ref="inputRef"
      :model-value="input"
      :placeholder="placeholder"
      clearable
      size="small"
      class="scan-input"
      @update:model-value="input = $event"
      @keyup.enter="handleScan"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>
    <span v-if="matchedLabel" class="scan-match" :title="matchedLabel">{{ matchedLabel }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import type { InputInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { resolveScannedCode, type ScannedReagent } from '@/utils/reagentScan'

/**
 * 通用试剂扫码输入条：
 * - 扫描/输入条码回车后自动解析（库存批次 → 试剂编码 → 试剂名称）
 * - 解析成功后通过 `scanned` 事件回传统一结构，并清空输入、继续聚焦以便连续批量扫码
 * - 底部展示最近一次命中的试剂信息（批量扫码时的反馈）
 */
defineOptions({ name: 'ReagentScanBar' })

const props = withDefaults(defineProps<{ placeholder?: string }>(), {
  placeholder: '扫描或输入试剂条码，回车快速追加'
})
const emit = defineEmits<{
  (e: 'scanned', payload: ScannedReagent): void
}>()

const input = ref('')
const inputRef = ref<InputInstance>()
const matchedLabel = ref('')

async function handleScan() {
  const payload = await resolveScannedCode(input.value)
  if (!payload) {
    ElMessage.warning('未匹配到该条码对应的库存批次或试剂，请检查后重试')
    return
  }
  matchedLabel.value = payload.reagentName + (payload.batchNo ? `（批号 ${payload.batchNo}）` : '')
  input.value = ''
  emit('scanned', payload)
  focusInput()
}

function focusInput() {
  nextTick(() => inputRef.value?.focus?.())
}
</script>

<style scoped lang="scss">
.reagent-scan-bar {
  display: flex;
  align-items: center;
  gap: 8px;

  .scan-input {
    width: 240px;
  }

  .scan-match {
    font-size: 12px;
    color: #67c23a;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 220px;
  }
}
</style>