<template>
  <el-dialog v-model="visible" title="撤销标本拆分" width="500px" :close-on-click-modal="false" @open="handleOpen">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
      撤销拆分后，所有子标本合并回原标本；已产生的结果会保留并合并到原标本。
    </el-alert>
    <div v-if="sample" class="info">
      <div>当前标本：<b>{{ sample.patientName }}</b> / {{ sample.sampleBarcode }}</div>
      <div style="margin-top:6px">检验项目：{{ sample.testItems }}</div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="warning" :loading="submitting" @click="submit">确认撤销拆分</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { unsplitSample, type TechnicianSample } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; sample: TechnicianSample | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const submitting = ref(false)

function handleOpen() {}

async function submit() {
  if (!props.sample) return
  try {
    await ElMessageBox.confirm(`确认撤销标本 [${props.sample.sampleBarcode}] 的拆分？此操作不可撤销。`, '撤销拆分确认', { type: 'warning' })
  } catch { return }
  submitting.value = true
  try {
    const res = await unsplitSample(props.sample.id)
    ElMessage.success(`撤销拆分完成：成功 ${res.success}，失败 ${res.failed}`)
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.info { padding: 12px; background: #f5f7fa; border-radius: 4px; line-height: 1.8 }
</style>
