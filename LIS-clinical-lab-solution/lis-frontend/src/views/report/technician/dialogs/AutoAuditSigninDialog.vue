<template>
  <el-dialog v-model="visible" title="自动审核签到" width="500px" :close-on-click-modal="false" @open="handleOpen">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      自动审核签到：签到后该用户将进入自动审核轮询；签退后退出轮询。实际自动审核由后端服务执行。
    </el-alert>
    <el-form :model="form" label-width="100px">
      <el-form-item label="签到人员" required>
        <el-select v-model="form.operatorId" filterable placeholder="请选择自动审核人员" style="width: 100%" @change="onUserChange">
          <el-option
            v-for="u in userList"
            :key="u.id"
            :label="`${u.realName || u.username} (${u.username})`"
            :value="u.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作类型" required>
        <el-radio-group v-model="form.action">
          <el-radio-button label="signin">签到（加入轮询）</el-radio-button>
          <el-radio-button label="signout">签退（退出轮询）</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ form.action === 'signin' ? '确认签到' : '确认签退' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { autoAuditSignin } from '@/api/sample'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({
  operatorId: null as number | null,
  operatorName: '',
  action: 'signin' as 'signin' | 'signout'
})
const userList = ref([
  { id: 1, username: 'admin', realName: '系统管理员' },
  { id: 2, username: 'tech01', realName: '检验技师A' },
  { id: 3, username: 'tech02', realName: '检验技师B' }
])
const submitting = ref(false)

function onUserChange(id: number) {
  const u = userList.value.find(x => x.id === id)
  if (u) form.operatorName = u.realName || u.username
}
function handleOpen() { form.operatorId = null; form.operatorName = ''; form.action = 'signin' }

async function submit() {
  if (!form.operatorId || !form.operatorName) { ElMessage.warning('请选择自动审核人员'); return }
  submitting.value = true
  try {
    await autoAuditSignin({
      operatorId: form.operatorId!,
      operatorName: form.operatorName,
      action: form.action
    })
    ElMessage.success(form.action === 'signin' ? '自动审核签到成功' : '自动审核签退成功')
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { submitting.value = false }
}
</script>
