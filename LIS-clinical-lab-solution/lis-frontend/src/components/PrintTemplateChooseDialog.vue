<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="520px"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
  >
    <div v-loading="loading" class="ptp-body">
      <el-empty v-if="!loading && templates.length === 0" description="该业务暂无可用打印模板" :image-size="80" />

      <el-form v-else label-width="88px" size="small">
        <el-form-item label="打印模板">
          <el-select v-model="templateId" style="width: 100%">
            <el-option
              v-for="t in templates"
              :key="t.id"
              :label="t.isDefault === 1 ? `${t.templateName}（默认）` : t.templateName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="打印份数">
          <el-input-number v-model="copies" :min="1" :max="20" size="small" />
          <span class="ptp-hint">整份重复</span>
        </el-form-item>
        <el-form-item label="打印内容">
          <div class="ptp-summary">
            <span>纸张 {{ paperText }}</span>
            <span>单据 {{ datasets.length }} 份</span>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" :disabled="!canPrint" @click="handlePreview">预览</el-button>
      <el-button size="small" type="primary" :disabled="!canPrint" @click="handlePrint">打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { printWithTemplate, previewTemplate } from '@/utils/printTemplate'
import {
  getEnabledPrintTemplates,
  getPrintData,
  type PrintData,
  type PrintTemplate
} from '@/api/reagent/printTemplate'

const props = defineProps<{
  visible: boolean
  /** 业务类型，如 INBOUND / REAGENT_LABEL */
  bizType: string
  /** 单据标题，用于弹窗标题 */
  title?: string
  /** 单据 ID 列表（同一业务类型），按顺序逐份打印 */
  docIds?: number[]
  /** 已经准备好的打印数据（试剂条码标签等无后端单据的场景直接传入） */
  localData?: PrintData[] | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'empty'): void
}>()

const visible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const templates = ref<PrintTemplate[]>([])
const templateId = ref<number | undefined>(undefined)
const copies = ref(1)
const datasets = ref<PrintData[]>([])
const loading = ref(false)

const currentTemplate = computed(() => templates.value.find(t => t.id === templateId.value))
const canPrint = computed(() => !!currentTemplate.value && datasets.value.length > 0)

const dialogTitle = computed(() => (props.title ? `${props.title}打印` : '单据打印'))

const paperText = computed(() => {
  const tpl = currentTemplate.value
  if (!tpl) return '-'
  return `${Number(tpl.paperWidth)}×${Number(tpl.paperHeight)}mm`
})

watch(
  () => props.visible,
  async (v) => {
    if (v) await init()
  }
)

async function init() {
  loading.value = true
  templates.value = []
  datasets.value = []
  templateId.value = undefined
  copies.value = 1
  try {
    templates.value = await getEnabledPrintTemplates(props.bizType)
    if (templates.value.length === 0) {
      emit('empty')
      visible.value = false
      ElMessage.warning('该业务暂无可用打印模板，请先在「打印模板管理」中新建模板')
      return
    }
    const def = templates.value.find(t => t.isDefault === 1) || templates.value[0]
    templateId.value = def.id

    if (props.localData && props.localData.length > 0) {
      datasets.value = props.localData
    } else if (props.docIds && props.docIds.length > 0) {
      datasets.value = await Promise.all(props.docIds.map(id => getPrintData(props.bizType, id)))
    }
  } catch (e) {
    // 请求层已提示错误，这里只保证弹窗状态可恢复
    datasets.value = []
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  const tpl = currentTemplate.value
  if (!tpl) return
  printWithTemplate(tpl, datasets.value, { copies: copies.value })
  visible.value = false
}

function handlePreview() {
  const tpl = currentTemplate.value
  if (!tpl) return
  previewTemplate(tpl, datasets.value, { copies: copies.value })
}
</script>

<style scoped>
.ptp-body {
  min-height: 120px;
}
.ptp-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.ptp-summary {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #606266;
}
</style>
