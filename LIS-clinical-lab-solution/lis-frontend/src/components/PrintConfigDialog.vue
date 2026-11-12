<template>
  <el-dialog
    v-model="innerVisible"
    :title="dialogTitle"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form label-width="120px" size="default" class="print-config-form">
      <el-divider content-position="left">纸张尺寸</el-divider>
      <el-form-item label="纸张预设">
        <el-select v-model="form.paperSizePreset" style="width: 100%" @change="handlePresetChange">
          <el-option
            v-for="p in PAPER_PRESETS"
            :key="p.key"
            :label="p.name"
            :value="p.key"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="纸张宽高(mm)">
        <div style="display: flex; gap: 8px; align-items: center;">
          <el-input-number
            v-model="form.paperWidth"
            :min="20"
            :max="500"
            :step="1"
            :precision="0"
            :disabled="form.paperSizePreset !== 'CUSTOM'"
            style="flex: 1"
            controls-position="right"
          />
          <span>×</span>
          <el-input-number
            v-model="form.paperHeight"
            :min="20"
            :max="500"
            :step="1"
            :precision="0"
            :disabled="form.paperSizePreset !== 'CUSTOM'"
            style="flex: 1"
            controls-position="right"
          />
          <span>mm</span>
        </div>
      </el-form-item>
      <el-form-item label="外边距(mm)">
        <el-input-number
          v-model="form.margin"
          :min="0"
          :max="50"
          :step="1"
          :precision="0"
          style="width: 200px"
          controls-position="right"
        />
      </el-form-item>

      <el-divider content-position="left">外观参数</el-divider>
      <el-form-item label="打印缩放">
        <el-slider
          v-model="form.scale"
          :min="0.5"
          :max="2.0"
          :step="0.05"
          :show-tooltip="true"
          format-tooltip="(v) => `${(v * 100).toFixed(0)}%`"
          style="flex: 1; margin-right: 16px;"
        />
        <el-tag size="small" type="info">{{ (form.scale * 100).toFixed(0) }}%</el-tag>
      </el-form-item>
      <el-form-item label="条码条宽">
        <el-slider
          v-model="form.barcodeWidth"
          :min="0.8"
          :max="4.0"
          :step="0.1"
          :show-tooltip="true"
          format-tooltip="(v) => `${v.toFixed(1)}px`"
          style="flex: 1; margin-right: 16px;"
        />
        <el-tag size="small" type="info">{{ form.barcodeWidth.toFixed(1) }}px</el-tag>
      </el-form-item>
      <el-form-item label="条码高度">
        <el-input-number
          v-model="form.barcodeHeight"
          :min="10"
          :max="120"
          :step="2"
          :precision="0"
          style="width: 200px"
          controls-position="right"
        />
        <span style="margin-left: 8px; color: #909399;">px</span>
      </el-form-item>
      <el-form-item label="标签字体">
        <el-input-number
          v-model="form.labelFontSize"
          :min="6"
          :max="24"
          :step="1"
          :precision="0"
          style="width: 200px"
          controls-position="right"
        />
        <span style="margin-left: 8px; color: #909399;">px</span>
      </el-form-item>
      <el-form-item label="打印边框">
        <el-switch v-model="border" :active-value="1" :inactive-value="0" />
        <span style="margin-left: 8px; color: #909399;">{{ border === 1 ? '显示边框' : '隐藏边框' }}</span>
      </el-form-item>

      <el-divider content-position="left">预览</el-divider>
      <div class="preview-wrap">
        <div
          class="preview-page"
          :style="previewPageStyle"
        >
          <div class="preview-label" :style="previewLabelStyle">
            <div style="font-size: 11px; font-weight: bold;">预览标签</div>
            <div style="font-size: 9px; margin-top: 2px;">患者: 张三</div>
            <div style="font-size: 9px;">条码: L20260101000001</div>
            <div class="preview-barcode" :style="previewBarcodeStyle">
              ████████████████████████████
            </div>
            <div style="font-size: 9px; letter-spacing: 1px; font-weight: bold;">L20260101000001</div>
          </div>
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="handleReset" :icon="Refresh">恢复默认</el-button>
      <div style="flex: 1;"></div>
      <el-button @click="innerVisible = false">取消</el-button>
      <el-button type="primary" :icon="Check" @click="handleSave">保存设置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Refresh, Check } from '@element-plus/icons-vue'
import {
  PAPER_PRESETS,
  applyPaperPreset,
  getPrintConfig,
  resetPrintConfig,
  savePrintConfig,
  type PrintConfig,
  type PrintSceneKey,
} from '@/utils/printConfig'

const props = defineProps<{
  visible: boolean
  scene: PrintSceneKey
  sceneTitle?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved', cfg: PrintConfig): void
}>()

const SCENE_TITLES: Record<PrintSceneKey, string> = {
  'sample-barcode': '标本条码打印',
  'plate-barcode': '培养基条码打印',
  'reagent-barcode': '试剂条码打印',
  'report-clinical': '常规检验报告',
  'report-micro': '微生物检验报告',
  'report-qc': '质控报告',
}

const innerVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const dialogTitle = computed(() => {
  const st = props.sceneTitle || SCENE_TITLES[props.scene] || '打印设置'
  return `打印设置 - ${st}`
})

const form = ref<PrintConfig>(getPrintConfig(props.scene))

const border = computed({
  get: () => (form.value.border === 1 ? 1 : 0),
  set: (v) => (form.value.border = v as 0 | 1),
})

watch(() => props.visible, (v) => {
  if (v) {
    form.value = getPrintConfig(props.scene)
  }
})

watch(() => props.scene, () => {
  form.value = getPrintConfig(props.scene)
})

function handlePresetChange(key: string) {
  form.value = applyPaperPreset(form.value, key)
}

// 预览样式: 按 3:1 比例缩小展示 (mm -> px, 3px ≈ 1mm)
const PREVIEW_RATIO = 3 // 1mm = 3px in preview box
const previewPageStyle = computed(() => {
  const w = form.value.paperWidth * PREVIEW_RATIO
  const h = form.value.paperHeight * PREVIEW_RATIO
  return {
    width: `${w}px`,
    minHeight: `${h}px`,
    margin: '0 auto',
    background: '#fff',
    boxShadow: '0 2px 8px rgba(0,0,0,.1)',
    padding: `${form.value.margin * PREVIEW_RATIO}px`,
    boxSizing: 'border-box',
  }
})
const previewLabelStyle = computed(() => {
  const innerW = (form.value.paperWidth - form.value.margin * 2) * PREVIEW_RATIO
  const innerH = (form.value.paperHeight - form.value.margin * 2) * PREVIEW_RATIO
  return {
    width: `${innerW}px`,
    minHeight: `${innerH}px`,
    border: form.value.border === 1 ? '1px dashed #999' : 'none',
    padding: '6px 8px',
    boxSizing: 'border-box',
    transform: `scale(${form.value.scale})`,
    transformOrigin: 'top left',
    display: 'flex',
    flexDirection: 'column',
    fontSize: `${form.value.labelFontSize}px`,
  }
})
const previewBarcodeStyle = computed(() => ({
  marginTop: 'auto',
  height: `${Math.max(8, form.value.barcodeHeight * 0.6)}px`,
  background: 'repeating-linear-gradient(90deg, #000 0, #000 1px, #fff 1px, #fff 2px)',
  width: '100%',
  letterSpacing: '0',
  overflow: 'hidden',
  color: 'transparent',
  lineHeight: `${Math.max(8, form.value.barcodeHeight * 0.6)}px`,
  fontSize: '8px',
}))

function handleReset() {
  resetPrintConfig(props.scene)
  form.value = getPrintConfig(props.scene)
}

function handleSave() {
  const cfg: PrintConfig = { ...form.value, scene: props.scene }
  savePrintConfig(cfg)
  innerVisible.value = false
  emit('saved', cfg)
}
</script>

<style scoped lang="scss">
.print-config-form {
  .el-slider {
    display: inline-flex;
    vertical-align: middle;
  }
}
.preview-wrap {
  width: 100%;
  padding: 16px;
  background: #f2f3f5;
  border-radius: 6px;
  overflow: auto;
  box-sizing: border-box;
}
.preview-page {
  transition: all 0.2s ease;
}
.preview-label {
  transition: all 0.2s ease;
}
</style>
