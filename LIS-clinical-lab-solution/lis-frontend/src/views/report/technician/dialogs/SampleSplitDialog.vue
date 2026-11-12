<template>
  <el-dialog v-model="visible" title="标本拆分" width="700px" :close-on-click-modal="false" @open="handleOpen">
    <div v-if="sample" class="sample-info" style="margin-bottom: 12px">
      <el-tag>当前标本：{{ sample.patientName }} / {{ sample.sampleBarcode }}</el-tag>
      <el-tag type="info" style="margin-left: 8px">检验项目：{{ sample.testItems }}</el-tag>
    </div>
    <el-form :model="form" label-width="110px" size="default">
      <el-form-item label="拆分数目" required>
        <el-input-number v-model="form.splitCount" :min="2" :max="10" @change="onSplitCountChange" />
        <span class="tip ml-8">拆分后生成的子标本数（含原标本）</span>
      </el-form-item>
    </el-form>
    <div class="section-title">拆分分配（勾选每个子标本包含的指标）</div>
    <div class="split-list">
      <el-card v-for="(sub, idx) in splitItems" :key="idx" shadow="never" style="margin-bottom: 8px">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <b>子标本 {{ idx + 1 }} ({{ sub.name }})</b>
            <el-tag type="info" size="small">已勾选 {{ sub.selectedIds.length }} 项</el-tag>
          </div>
        </template>
        <el-checkbox-group v-model="sub.selectedIds">
          <el-checkbox
            v-for="it in indicators"
            :key="it.testItemId"
            :label="it.testItemId"
            style="display:inline-block;width:48%;padding:4px 0"
          >
            {{ it.testItemName }} ({{ it.testItemCode }})
          </el-checkbox>
        </el-checkbox-group>
      </el-card>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认拆分</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { splitSample, type TechnicianSample, type TechnicianResult } from '@/api/sample'
import { getTechnicianDetail } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; sample: TechnicianSample | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({ splitCount: 2 })
const indicators = ref<TechnicianResult[]>([])
const splitItems = ref<{ name: string; selectedIds: (number | string)[] }[]>([])
const submitting = ref(false)

function onSplitCountChange() {
  while (splitItems.value.length < form.splitCount) {
    splitItems.value.push({
      name: `子标本-${splitItems.value.length + 1}`,
      selectedIds: indicators.value.map(i => i.testItemId!)
    })
  }
  while (splitItems.value.length > form.splitCount) splitItems.value.pop()
}

async function handleOpen() {
  form.splitCount = 2
  splitItems.value = []
  indicators.value = []
  if (props.sample) {
    try {
      const d = await getTechnicianDetail(props.sample.id)
      indicators.value = d.results || []
    } catch {}
  }
  onSplitCountChange()
}

async function submit() {
  if (!props.sample) return
  const hasEmpty = splitItems.value.some(s => s.selectedIds.length === 0)
  if (hasEmpty) { ElMessage.warning('每个子标本至少分配 1 个指标'); return }
  submitting.value = true
  try {
    const res = await splitSample({
      sampleId: props.sample.id,
      splitCount: form.splitCount,
      splits: splitItems.value.map((s, i) => ({ index: i, testItemIds: s.selectedIds }))
    })
    ElMessage.success(`拆分完成：成功 ${res.success}，失败 ${res.failed}`)
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '拆分失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.ml-8 { margin-left: 8px }
.tip { font-size: 12px; color: #909399 }
.section-title { font-weight: 600; margin: 10px 0; color: #303133 }
.split-list { max-height: 360px; overflow: auto }
</style>
