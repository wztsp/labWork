<template>
  <el-dialog
    v-model="visible"
    :title="`批量修改结果 (共 ${samples.length} 个标本)`"
    width="600px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <el-form :model="form" label-width="110px" size="default">
      <el-form-item label="选择指标" required>
        <el-select
          v-model="form.testItemId"
          placeholder="选择要修改的检验指标（可留空表示所有指标）"
          style="width: 100%"
          filterable
          clearable
        >
          <el-option label="全部可匹配的指标（按指标代码/名称自动匹配）" :value="null" />
          <el-option
            v-for="it in indicatorList"
            :key="it.testItemId"
            :label="`${it.testItemName} (${it.testItemCode})`"
            :value="it.testItemId"
          />
        </el-select>
        <div class="tip">选中的指标会在所选所有标本中被修改；若选择「全部」，则在各标本中按指标代码/名称匹配后修改。</div>
      </el-form-item>
      <el-form-item label="修改方式" required>
        <el-radio-group v-model="form.mode">
          <el-radio-button label="fixed">设为固定值</el-radio-button>
          <el-radio-button label="add">加偏移量 (值+Δ)</el-radio-button>
          <el-radio-button label="multiply">乘系数 (值×k)</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="修改值" required>
        <el-input-number
          v-model="form.value"
          :controls="true"
          :precision="form.mode === 'multiply' ? 2 : 3"
          :step="form.mode === 'multiply' ? 0.05 : 0.1"
          style="width: 240px"
        />
        <span class="tip ml-8">
          <template v-if="form.mode === 'fixed'">所有匹配结果设为该值</template>
          <template v-else-if="form.mode === 'add'">在原结果基础上加上该值（空值跳过）</template>
          <template v-else>原结果乘以此系数（空值跳过）</template>
        </span>
      </el-form-item>
      <el-form-item label="修改范围">
        <el-switch
          v-model="form.onlyEmpty"
          active-text="仅修改结果为空的（推荐，避免覆盖仪器回传）"
          inactive-text="覆盖所有结果（慎用）"
        />
      </el-form-item>
    </el-form>
    <el-alert type="warning" :closable="false" show-icon style="margin-top: 8px">
      修改操作不可撤销，建议先对 1-2 个标本测试效果后再批量执行。
    </el-alert>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  batchModifyResult,
  type TechnicianSample,
  type TechnicianResult
} from '@/api/sample'
import { getTechnicianDetail } from '@/api/sample'

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
  testItemId: null as number | null,
  mode: 'fixed' as 'fixed' | 'add' | 'multiply',
  value: 0 as number,
  onlyEmpty: true
})

const indicatorList = ref<TechnicianResult[]>([])
const submitting = ref(false)

async function handleOpen() {
  form.testItemId = null
  form.mode = 'fixed'
  form.value = 0
  form.onlyEmpty = true
  indicatorList.value = []
  // 收集第一个选中标本的指标作为候选列表
  if (props.samples.length > 0) {
    try {
      const detail = await getTechnicianDetail(props.samples[0].id)
      indicatorList.value = detail.results || []
    } catch {}
  }
}

async function submit() {
  if (form.mode === 'multiply' && (form.value as number) <= 0) {
    ElMessage.warning('乘法模式下系数必须 > 0')
    return
  }
  submitting.value = true
  try {
    const res = await batchModifyResult({
      sampleIds: props.samples.map(s => s.id),
      testItemId: form.testItemId ?? undefined,
      mode: form.mode,
      value: form.value,
      onlyEmpty: form.onlyEmpty
    })
    const msg = `批量修改完成：成功 ${res.success}，失败 ${res.failed}${
      res.failures?.length ? `\n失败原因示例：${res.failures.slice(0, 3).map(f => `${f.id}:${f.reason}`).join('；')}` : ''
    }`
    if (res.failed > 0) ElMessage.warning(msg)
    else ElMessage.success(msg)
    emit('success')
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '批量修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.tip { font-size: 12px; color: #909399; }
.ml-8 { margin-left: 8px; }
</style>
