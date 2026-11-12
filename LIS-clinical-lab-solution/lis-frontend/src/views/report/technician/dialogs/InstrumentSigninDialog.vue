<template>
  <el-dialog v-model="visible" title="仪器人员签到" width="520px" :close-on-click-modal="false" @open="handleOpen">
    <el-form :model="form" label-width="100px">
      <el-form-item label="签到仪器" required>
        <el-select v-model="form.instrumentId" placeholder="请选择仪器" filterable style="width: 100%">
          <el-option
            v-for="ins in instrumentOptions"
            :key="ins.id"
            :label="ins.instrumentName"
            :value="ins.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="签到人员" required>
        <el-select v-model="form.operatorId" filterable placeholder="请选择签到人员" style="width: 100%" @change="onUserChange">
          <el-option
            v-for="u in userList"
            :key="u.id"
            :label="`${u.realName || u.username} (${u.username})`"
            :value="u.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="签到时间">
        <el-date-picker
          v-model="form.signinTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="默认当前时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选备注" />
      </el-form-item>
    </el-form>
    <div class="record-section" v-if="records.length">
      <div class="section-title">最近签到记录</div>
      <el-table :data="records" size="small" border stripe>
        <el-table-column prop="instrumentName" label="仪器" width="160" />
        <el-table-column prop="operatorName" label="签到人" width="120" />
        <el-table-column prop="signinTime" label="签到时间" />
      </el-table>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认签到</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { instrumentSignin } from '@/api/sample'
import type { Instrument } from '@/api/instrument'

const props = defineProps<{ modelValue: boolean; instrumentOptions: Instrument[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({
  instrumentId: null as number | null,
  operatorId: null as number | null,
  operatorName: '',
  signinTime: '',
  remark: ''
})
const records = ref<any[]>([])
const userList = ref([
  { id: 1, username: 'admin', realName: '系统管理员' },
  { id: 2, username: 'tech01', realName: '检验技师A' },
  { id: 3, username: 'tech02', realName: '检验技师B' },
  { id: 4, username: 'tech03', realName: '检验技师C' }
])
const submitting = ref(false)

function onUserChange(id: number) {
  const u = userList.value.find(x => x.id === id)
  if (u) form.operatorName = u.realName || u.username
}

function handleOpen() {
  form.instrumentId = props.instrumentOptions[0]?.id || null
  form.operatorId = null; form.operatorName = ''
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  form.signinTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  form.remark = ''
}

async function submit() {
  if (!form.instrumentId || !form.operatorId || !form.operatorName) {
    ElMessage.warning('请完善仪器和签到人员信息'); return
  }
  submitting.value = true
  try {
    const res = await instrumentSignin({
      instrumentId: form.instrumentId!,
      operatorId: form.operatorId!,
      operatorName: form.operatorName,
      signinTime: form.signinTime || undefined,
      remark: form.remark || undefined
    })
    ElMessage.success('签到成功')
    records.value.unshift({
      instrumentName: props.instrumentOptions.find(i => i.id === form.instrumentId)?.instrumentName || '',
      operatorName: form.operatorName,
      signinTime: form.signinTime
    })
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '签到失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.section-title { font-weight: 600; margin: 8px 0; color: #303133 }
.record-section { margin-top: 12px }
</style>
