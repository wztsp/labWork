<template>
  <el-dialog
    v-model="visible"
    :title="`批量修改检验人 (共 ${samples.length} 个标本)`"
    width="520px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <el-form :model="form" label-width="110px">
      <el-form-item label="选择检验人" required>
        <el-select
          v-model="form.inspectorId"
          placeholder="请选择检验人"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="u in userList"
            :key="u.id"
            :label="`${u.realName || u.username} (${u.username})`"
            :value="u.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="检验人姓名">
        <el-input v-model="form.inspectorName" placeholder="请确认检验人姓名（冗余写入）" />
      </el-form-item>
      <el-form-item label="覆盖策略">
        <el-switch
          v-model="form.override"
          active-text="覆盖已有的检验人"
          inactive-text="仅填充检验人为空的"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { batchModifyInspector, type TechnicianSample } from '@/api/sample'

const props = defineProps<{
  modelValue: boolean
  samples: TechnicianSample[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const form = reactive({
  inspectorId: null as number | null,
  inspectorName: '',
  override: false
})

const userList = ref<{ id: number; username: string; realName?: string }[]>([
  { id: 1, username: 'admin', realName: '系统管理员' },
  { id: 2, username: 'tech01', realName: '检验技师A' },
  { id: 3, username: 'tech02', realName: '检验技师B' },
  { id: 4, username: 'tech03', realName: '检验技师C' }
])

const submitting = ref(false)

watch(
  () => form.inspectorId,
  id => {
    const u = userList.value.find(x => x.id === id)
    if (u) form.inspectorName = u.realName || u.username
  }
)

function handleOpen() {
  form.inspectorId = null
  form.inspectorName = ''
  form.override = false
}

async function submit() {
  if (!form.inspectorId || !form.inspectorName) {
    ElMessage.warning('请选择检验人并确认姓名')
    return
  }
  submitting.value = true
  try {
    const res = await batchModifyInspector({
      sampleIds: props.samples.map(s => s.id),
      inspectorId: form.inspectorId!,
      inspectorName: form.inspectorName,
      override: form.override
    })
    ElMessage.success(`修改完成：成功 ${res.success}，失败 ${res.failed}`)
    emit('success')
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    submitting.value = false
  }
}
</script>
