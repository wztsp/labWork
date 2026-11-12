<template>
  <el-dialog v-model="visible" title="标本复制" width="500px" :close-on-click-modal="false" @open="handleOpen">
    <div class="summary">共选中 <b class="text-blue">{{ samples.length }}</b> 个标本进行复制</div>
    <el-form :model="form" label-width="120px" style="margin-top: 12px">
      <el-form-item label="每个标本复制几份" required>
        <el-input-number v-model="form.copyCount" :min="1" :max="10" style="width: 160px" />
      </el-form-item>
      <el-form-item label="是否复制检验结果">
        <el-switch v-model="form.copyResults" active-text="复制结果值" inactive-text="仅复制标本和指标（空值）" />
      </el-form-item>
      <el-form-item label="新条码生成规则">
        <el-tag type="info" effect="plain">系统将自动生成新条码号，格式：原条码_C1, _C2...</el-tag>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认复制</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { copySample, type TechnicianSample } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; samples: TechnicianSample[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({ copyCount: 1, copyResults: false })
const submitting = ref(false)
function handleOpen() { form.copyCount = 1; form.copyResults = false }
async function submit() {
  submitting.value = true
  try {
    const res = await copySample({
      sampleIds: props.samples.map(s => s.id),
      copyCount: form.copyCount,
      copyResults: form.copyResults
    })
    ElMessage.success(`复制完成：成功生成 ${res.success} 个新标本，失败 ${res.failed}`)
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.text-blue { color: #1a73e8; font-weight: 600 }
.summary { padding: 10px; background: #ecf5ff; border: 1px solid #d9ecff; border-radius: 4px }
</style>
