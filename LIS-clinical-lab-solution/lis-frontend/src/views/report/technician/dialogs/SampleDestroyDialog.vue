<template>
  <el-dialog v-model="visible" title="标本销毁" width="520px" :close-on-click-modal="false" @open="handleOpen">
    <el-alert type="error" :closable="false" show-icon style="margin-bottom: 12px">
      标本销毁是不可逆操作！销毁后标本及所有关联检验结果数据将标记为删除/归档状态。
    </el-alert>
    <div class="summary">共选中 <b class="text-red">{{ samples.length }}</b> 个标本进行销毁</div>
    <el-form :model="form" label-width="100px" style="margin-top: 12px">
      <el-form-item label="销毁原因" required>
        <el-select v-model="form.reasonType" placeholder="请选择销毁原因">
          <el-option label="超过保存期限" value="EXPIRED" />
          <el-option label="标本污染/破损" value="CONTAMINATED" />
          <el-option label="患者撤回" value="PATIENT_WITHDRAW" />
          <el-option label="重复/错误标本" value="DUPLICATE" />
          <el-option label="其他原因" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="详细说明" required>
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入详细原因" />
      </el-form-item>
      <el-form-item label="审批人">
        <el-input v-model="form.approver" placeholder="审批人姓名" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请输入管理员密码二次确认" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submit">确认销毁</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { destroySample, type TechnicianSample } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; samples: TechnicianSample[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({ reasonType: '', reason: '', approver: '', confirmPassword: '' })
const submitting = ref(false)

function handleOpen() {
  form.reasonType = ''; form.reason = ''; form.approver = ''; form.confirmPassword = ''
}

async function submit() {
  if (!form.reasonType || !form.reason) { ElMessage.warning('请选择销毁原因并填写说明'); return }
  try {
    await ElMessageBox.confirm(
      `此操作将永久标记 ${props.samples.length} 个标本为已销毁，是否继续？`,
      '销毁确认', { type: 'error', confirmButtonText: '继续销毁', cancelButtonText: '取消' }
    )
  } catch { return }
  submitting.value = true
  try {
    const res = await destroySample({
      sampleIds: props.samples.map(s => s.id),
      reason: `[${form.reasonType}] ${form.reason}`,
      approver: form.approver || undefined,
      confirmPassword: form.confirmPassword || undefined
    })
    ElMessage.success(`销毁完成：成功 ${res.success}，失败 ${res.failed}`)
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.text-red { color: #f56c6c; font-weight: 600 }
.summary { padding: 10px; background: #fef0f0; border: 1px solid #fde2e2; border-radius: 4px }
</style>
