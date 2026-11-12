<template>
  <el-dialog v-model="visible" title="批量撤销审核" width="500px" :close-on-click-modal="false">
    <div class="summary">共选中 <b class="text-orange">{{ samples.length }}</b> 个已审核标本，撤销后将回到「待审」状态。</div>
    <el-form :model="form" label-width="90px" style="margin-top: 12px">
      <el-form-item label="撤销原因">
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入撤销审核的原因（可选）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="warning" :loading="submitting" @click="submit">确认撤销审核</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { batchUnauditTechnician, type TechnicianSample } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; samples: TechnicianSample[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({ reason: '' })
const submitting = ref(false)

async function submit() {
  submitting.value = true
  try {
    const res = await batchUnauditTechnician(props.samples.map(s => s.id))
    ElMessage.success(`撤销审核完成：成功 ${res.success}，失败 ${res.failed}`)
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.text-orange { color: #e6a23c; font-weight: 600 }
.summary { padding: 10px; background: #fdf6ec; border: 1px solid #faecd8; border-radius: 4px }
</style>
