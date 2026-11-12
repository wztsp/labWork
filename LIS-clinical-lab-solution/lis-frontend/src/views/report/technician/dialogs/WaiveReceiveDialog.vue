<template>
  <el-dialog v-model="visible" title="让步核收" width="500px" :close-on-click-modal="false" @open="handleOpen">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
      让步核收用于特殊情况下，对不合格/异常标本进行特殊处理，需记录原因和审批人。
    </el-alert>
    <div class="summary">共选中 <b class="text-red">{{ samples.length }}</b> 个标本进行让步核收</div>
    <el-form :model="form" label-width="80px" style="margin-top: 12px">
      <el-form-item label="核收原因" required>
        <el-select v-model="form.reasonType" placeholder="请选择">
          <el-option label="标本溶血（可接受）" value="HEMOLYSIS_ACCEPT" />
          <el-option label="标本量不足（但必须出结果）" value="LOW_VOLUME_URGENT" />
          <el-option label="临床急诊要求" value="CLINICAL_URGENT" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="详细说明" required>
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入让步核收的详细原因" />
      </el-form-item>
      <el-form-item label="审批人">
        <el-input v-model="form.approver" placeholder="请输入审批人姓名（如有）" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="其他备注信息" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="warning" :loading="submitting" @click="submit">确认让步核收</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { waiveReceive, type TechnicianSample } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; samples: TechnicianSample[] }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })

const form = reactive({ reasonType: '', reason: '', approver: '', remark: '' })
const submitting = ref(false)

function handleOpen() {
  form.reasonType = ''; form.reason = ''; form.approver = ''; form.remark = ''
}
async function submit() {
  if (!form.reasonType || !form.reason) { ElMessage.warning('请选择核收原因并填写说明'); return }
  submitting.value = true
  try {
    const res = await waiveReceive({
      sampleIds: props.samples.map(s => s.id),
      reason: `[${form.reasonType}] ${form.reason}`,
      approver: form.approver || undefined,
      remark: form.remark || undefined
    })
    ElMessage.success(`让步核收完成：成功 ${res.success}，失败 ${res.failed}`)
    emit('success')
    visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.text-red { color: #f56c6c; font-weight: 600 }
.summary { padding: 10px; background: #fdf6ec; border: 1px solid #faecd8; border-radius: 4px }
</style>
