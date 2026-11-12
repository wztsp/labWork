<template>
  <el-dialog
    v-model="visible"
    fullscreen
    :close-on-click-modal="false"
    :show-close="false"
    destroy-on-close
    class="pt-designer-dialog"
    @opened="fitCanvas"
  >
    <template #header>
      <div class="pt-head">
        <span class="pt-head-title">{{ form.id ? '编辑打印模板' : '新建打印模板' }}</span>
        <div class="pt-head-ops">
          <el-button size="small" @click="visible = false">关闭</el-button>
          <el-button size="small" @click="handlePreview">预览</el-button>
          <el-button size="small" type="primary" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <div class="pt-toolbar">
      <el-input v-model="form.templateName" placeholder="模板名称" size="small" style="width: 160px" />
      <el-input v-model="form.templateCode" placeholder="模板编码" size="small" style="width: 160px" :disabled="!!form.id" />
      <el-select v-model="form.bizType" size="small" style="width: 150px" :disabled="!!form.id" @change="handleBizTypeChange">
        <el-option v-for="b in bizTypes" :key="b.code" :label="b.name" :value="b.code" />
      </el-select>
      <el-checkbox v-model="form.isDefault" size="small">设为默认</el-checkbox>

      <span class="pt-sep" />

      <el-select v-model="paperPreset" size="small" style="width: 150px" @change="applyPreset">
        <el-option v-for="p in PAPER_PRESETS" :key="p.key" :label="p.name" :value="p.key" />
      </el-select>
      <span class="pt-label">宽</span>
      <el-input-number v-model="form.paperWidth" :min="20" :max="460" :step="1" size="small" controls-position="right" style="width: 100px" @change="applyPresetBySize" />
      <span class="pt-label">高</span>
      <el-input-number v-model="form.paperHeight" :min="20" :max="460" :step="1" size="small" controls-position="right" style="width: 100px" @change="applyPresetBySize" />
      <span class="pt-label">边距</span>
      <el-input-number v-model="form.margin" :min="0" :max="60" :step="0.5" size="small" controls-position="right" style="width: 90px" />

      <span class="pt-sep" />

      <span class="pt-label">缩放</span>
      <el-select v-model="zoom" size="small" style="width: 100px">
        <el-option v-for="z in zoomOptions" :key="z" :label="`${Math.round(z * 100)}%`" :value="z" />
      </el-select>
      <el-button size="small" @click="fitCanvas">适应宽度</el-button>
      <el-checkbox v-model="showGrid" size="small">网格</el-checkbox>
    </div>

    <div class="pt-body">
      <aside class="pt-palette">
        <div class="pt-palette-title">元素</div>
        <div class="pt-palette-btns">
          <el-button size="small" @click="addText">文本</el-button>
          <el-button size="small" @click="addBarcode">条码</el-button>
          <el-button size="small" @click="addQrcode">二维码</el-button>
          <el-button size="small" @click="addLine">线条</el-button>
          <el-button size="small" @click="addRect">矩形</el-button>
          <el-button size="small" @click="addTable">表格</el-button>
          <el-button size="small" @click="chooseImage">图片</el-button>
        </div>

        <div class="pt-palette-title">表头字段</div>
        <div class="pt-field-list">
          <div
            v-for="f in headerFields"
            :key="f.field"
            class="pt-field-item"
            @click="addFieldElement(f)"
          >
            <span class="pt-field-key">{{ f.field }}</span>
            <span class="pt-field-name">{{ f.title }}</span>
          </div>
          <div v-if="headerFields.length === 0" class="pt-empty-tip">请先选择业务类型</div>
        </div>

        <div class="pt-palette-title">明细字段</div>
        <div class="pt-field-list">
          <div
            v-for="f in itemFields"
            :key="f.field"
            class="pt-field-item"
            @click="addTableColumn(f)"
          >
            <span class="pt-field-key">{{ f.field }}</span>
            <span class="pt-field-name">{{ f.title }}</span>
          </div>
        </div>
        <div class="pt-palette-hint">点击明细字段：向当前选中的表格追加一列</div>
      </aside>

      <section class="pt-stage" ref="stageRef">
        <div class="pt-tip">
          画布 {{ contentW }}×{{ contentH }} mm（纸张 {{ form.paperWidth }}×{{ form.paperHeight }}，边距 {{ form.margin }}mm）
        </div>
        <div
          class="pt-canvas"
          :class="{ 'is-grid': showGrid }"
          :style="canvasStyle"
          @mousedown.self="selectedId = ''"
        >
          <div
            v-for="el in model.elements"
            :key="el.id"
            class="pt-el"
            :class="{ 'is-selected': el.id === selectedId }"
            :style="elementBoxStyle(el)"
            @mousedown.stop="startDrag($event, el, 'move')"
          >
            <!-- 文本 -->
            <div
              v-if="el.type === 'text'"
              class="pt-text"
              :style="{ fontSize: fs(el), textAlign: el.align || 'left', fontWeight: el.bold ? 'bold' : 'normal', color: el.color || '#000' }"
            >{{ displayText(el) }}</div>

            <!-- 字段 -->
            <div
              v-else-if="el.type === 'field'"
              class="pt-field-row"
              :style="{ fontSize: fs(el), fontWeight: el.bold ? 'bold' : 'normal', color: el.color || '#000' }"
            >
              <span v-if="el.label" class="pt-field-label" :style="{ width: (Number(el.labelWidth) || 0) * scale + 'px' }">{{ el.label }}</span>
              <span class="pt-field-value">{{ displayField(el) }}</span>
            </div>

            <!-- 条码 / 二维码 -->
            <div
              v-else-if="el.type === 'barcode' || el.type === 'qrcode'"
              class="pt-code"
              v-html="el.type === 'barcode' ? barcodeSvg(el) : qrSvg(el)"
            />

            <!-- 线条 -->
            <div v-else-if="el.type === 'line'" :style="{ height: (Number(el.thickness) || 0.3) * scale + 'px', background: el.color || '#000' }" />

            <!-- 矩形 -->
            <div
              v-else-if="el.type === 'rect'"
              class="pt-rect"
              :style="{ borderWidth: (Number(el.thickness) || 0.3) * scale + 'px', borderColor: el.color || '#000' }"
            />

            <!-- 图片 -->
            <img v-else-if="el.type === 'image'" :src="el.src" class="pt-img" />

            <!-- 表格 -->
            <table v-else-if="el.type === 'table'" class="pt-table" :style="{ fontSize: 10 * zoom + 'px' }">
              <colgroup>
                <col v-for="(c, ci) in el.columns || []" :key="ci" :style="{ width: (Number(c.width) || 20) * scale + 'px' }" />
              </colgroup>
              <thead>
                <tr>
                  <th v-for="(c, ci) in el.columns || []" :key="ci">{{ c.title }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="n in 3" :key="n" :style="{ height: (Number(el.rowHeight) || 8) * scale + 'px' }">
                  <td
                    v-for="(c, ci) in el.columns || []"
                    :key="ci"
                    :style="{ textAlign: c.align || 'left' }"
                  >{{ c.field === 'index' ? n : `【${c.title}】` }}</td>
                </tr>
              </tbody>
            </table>

            <div v-if="el.id === selectedId" class="pt-resize" @mousedown.stop="startDrag($event, el, 'resize')" />
          </div>
        </div>
      </section>

      <aside class="pt-props">
        <div class="pt-props-title">
          属性
          <span v-if="selected" class="pt-props-sub">{{ typeName(selected.type) }}</span>
          <el-button v-if="selected" link type="danger" size="small" style="float: right" @click="removeSelected">删除</el-button>
        </div>

        <div v-if="!selected" class="pt-empty-tip">点击画布中的元素进行编辑</div>

        <el-form v-else label-position="top" size="small" class="pt-form">
          <el-form-item label="位置与尺寸（mm）">
            <div class="pt-grid4">
              <el-input-number v-model="selected.x" :min="0" :step="0.5" controls-position="right" />
              <el-input-number v-model="selected.y" :min="0" :step="0.5" controls-position="right" />
              <el-input-number v-model="selected.w" :min="0.5" :step="0.5" controls-position="right" />
              <el-input-number v-model="selected.h" :min="0" :step="0.5" controls-position="right" />
            </div>
          </el-form-item>

          <template v-if="selected.type === 'text'">
            <el-form-item label="文字内容（支持 {{field}}）">
              <el-input v-model="selected.text" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="字号(px)">
              <el-input-number v-model="selected.fontSize" :min="5" :max="72" controls-position="right" />
            </el-form-item>
            <el-form-item label="样式">
              <el-checkbox v-model="selected.bold">加粗</el-checkbox>
              <el-select v-model="selected.align" size="small" style="width: 100px">
                <el-option label="左对齐" value="left" />
                <el-option label="居中" value="center" />
                <el-option label="右对齐" value="right" />
              </el-select>
            </el-form-item>
          </template>

          <template v-if="selected.type === 'field'">
            <el-form-item label="绑定字段">
              <el-select v-model="selected.field" filterable style="width: 100%">
                <el-option v-for="f in headerFields" :key="f.field" :label="`${f.title}（${f.field}）`" :value="f.field" />
              </el-select>
            </el-form-item>
            <el-form-item label="显示标签">
              <el-input v-model="selected.label" placeholder="留空则不显示标签" />
            </el-form-item>
            <el-form-item label="标签宽度(mm)">
              <el-input-number v-model="selected.labelWidth" :min="0" :step="1" controls-position="right" />
            </el-form-item>
            <el-form-item label="字号(px)">
              <el-input-number v-model="selected.fontSize" :min="5" :max="72" controls-position="right" />
            </el-form-item>
            <el-form-item label="加粗">
              <el-checkbox v-model="selected.bold">加粗</el-checkbox>
            </el-form-item>
          </template>

          <template v-if="selected.type === 'barcode' || selected.type === 'qrcode'">
            <el-form-item label="内容（支持 {{field}}）">
              <el-input v-model="selected.value" placeholder="例如 {{barcode}}" />
            </el-form-item>
            <el-form-item v-if="selected.type === 'barcode'" label="码制">
              <el-input model-value="CODE128" disabled />
            </el-form-item>
            <el-form-item v-if="selected.type === 'barcode'" label="显示可读字符">
              <el-switch v-model="selected.showText" />
            </el-form-item>
          </template>

          <template v-if="selected.type === 'line' || selected.type === 'rect'">
            <el-form-item label="线宽(mm)">
              <el-input-number v-model="selected.thickness" :min="0.1" :max="5" :step="0.1" controls-position="right" />
            </el-form-item>
            <el-form-item label="颜色">
              <el-input v-model="selected.color" placeholder="#000000" />
            </el-form-item>
          </template>

          <template v-if="selected.type === 'image'">
            <el-form-item label="图片">
              <el-button size="small" @click="chooseImage">重新选择</el-button>
            </el-form-item>
            <div class="pt-empty-tip">建议使用小于 100KB 的 PNG，避免模板过大</div>
          </template>

          <template v-if="selected.type === 'table'">
            <el-form-item label="行高(mm)">
              <el-input-number v-model="selected.rowHeight" :min="4" :max="20" :step="0.5" controls-position="right" />
            </el-form-item>
            <el-form-item label="列定义">
              <div class="pt-col-list">
                <div v-for="(c, ci) in selected.columns || []" :key="ci" class="pt-col-row">
                  <el-select v-model="c.field" size="small" style="width: 108px">
                    <el-option v-for="f in itemFields" :key="f.field" :label="f.title" :value="f.field" />
                  </el-select>
                  <el-input v-model="c.title" size="small" style="width: 80px" placeholder="表头" />
                  <el-input-number v-model="c.width" :min="4" :max="200" :step="1" size="small" controls-position="right" style="width: 82px" />
                  <el-select v-model="c.align" size="small" style="width: 74px">
                    <el-option label="左" value="left" />
                    <el-option label="中" value="center" />
                    <el-option label="右" value="right" />
                  </el-select>
                  <el-button link type="danger" size="small" @click="removeColumn(ci)">删</el-button>
                </div>
                <el-button size="small" @click="appendBlankColumn">添加列</el-button>
              </div>
            </el-form-item>
          </template>
        </el-form>
      </aside>
    </div>

    <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="handleFile" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { PAPER_PRESETS } from '@/utils/printConfig'
import { MM_TO_PX, renderBarcodeSvg, renderQrSvg } from '@/utils/barcodeRender'
import {
  interpolate,
  parseTemplateJson,
  previewTemplate,
  type PrintElement,
  type PrintTemplateModel
} from '@/utils/printTemplate'
import {
  getPrintBizTypes,
  getPrintFieldCatalog,
  type PrintBizType,
  type PrintData,
  type PrintField,
  type PrintFieldCatalog,
  type PrintTemplate
} from '@/api/reagent/printTemplate'

const props = defineProps<{
  visible: boolean
  template?: PrintTemplate | null
  bizType?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save', payload: PrintTemplate): void
}>()

const visible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const zoomOptions = ref<number[]>([0.25, 0.5, 0.75, 1, 1.25, 1.5, 2])

const form = reactive({
  id: undefined as number | undefined,
  templateCode: '',
  templateName: '',
  bizType: '',
  paperWidth: 210,
  paperHeight: 297,
  margin: 8,
  isDefault: false,
  status: 1,
  remark: ''
})

const model = ref<PrintTemplateModel>({ version: 1, canvas: { width: 194, height: 281 }, elements: [] })
const bizTypes = ref<PrintBizType[]>([])
const catalog = ref<PrintFieldCatalog | null>(null)
const selectedId = ref('')
const zoom = ref(1)
const showGrid = ref(true)
const paperPreset = ref<string>('A4')
const stageRef = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const scale = computed(() => MM_TO_PX * zoom.value)
const contentW = computed(() => Math.max(10, Number(form.paperWidth) - Number(form.margin) * 2))
const contentH = computed(() => Math.max(10, Number(form.paperHeight) - Number(form.margin) * 2))

const canvasStyle = computed(() => ({
  width: contentW.value * scale.value + 'px',
  height: contentH.value * scale.value + 'px',
  background: model.value.canvas.background || '#ffffff'
}))

const headerFields = computed<PrintField[]>(() => [
  ...(catalog.value?.header || []),
  ...(catalog.value?.meta || [])
])
const itemFields = computed<PrintField[]>(() => [
  { field: 'index', title: '序号' },
  ...(catalog.value?.items || [])
])

const selected = computed<PrintElement | undefined>(() =>
  model.value.elements.find(e => e.id === selectedId.value)
)

/** 预览用占位数据：用字段中文名占位，条码用 ASCII 占位以通过 CODE128 编码 */
const sampleHeader = computed<Record<string, string>>(() => {
  const header: Record<string, string> = {}
  for (const f of headerFields.value) {
    header[f.field] = f.field === 'barcode' ? 'RG202610290001' : `【${f.title}】`
  }
  header.printTime = '2026-10-29 10:00:00'
  return header
})

const sampleData = computed<PrintData>(() => ({
  header: sampleHeader.value,
  items: [{}],
  printTime: sampleHeader.value.printTime
}))

/* ---------------- 初始化 ---------------- */

async function loadBizTypes() {
  if (bizTypes.value.length) return
  try {
    bizTypes.value = await getPrintBizTypes()
  } catch {
    bizTypes.value = []
  }
}

async function loadCatalog(bizType: string) {
  catalog.value = null
  if (!bizType) return
  try {
    catalog.value = await getPrintFieldCatalog(bizType)
  } catch {
    catalog.value = null
  }
}

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    await loadBizTypes()
    initFromProps()
    await nextTick()
    fitCanvas()
  }
)

function initFromProps() {
  const tpl = props.template || null
  form.id = tpl?.id
  form.templateCode = tpl?.templateCode || `${props.bizType || 'REAGENT_LABEL'}_${Math.floor(Math.random() * 9000 + 1000)}`
  form.templateName = tpl?.templateName || ''
  form.bizType = tpl?.bizType || props.bizType || bizTypes.value[0]?.code || 'REAGENT_LABEL'
  form.paperWidth = Number(tpl?.paperWidth) || (form.bizType === 'REAGENT_LABEL' ? 60 : 210)
  form.paperHeight = Number(tpl?.paperHeight) || (form.bizType === 'REAGENT_LABEL' ? 40 : 297)
  form.margin = tpl ? Number(tpl.margin) || 0 : form.bizType === 'REAGENT_LABEL' ? 2 : 8
  form.isDefault = tpl?.isDefault === 1
  form.status = tpl?.status ?? 1
  form.remark = tpl?.remark || ''

  const parsed = parseTemplateJson(tpl?.templateJson)
  if (parsed) {
    model.value = {
      version: parsed.version || 1,
      canvas: {
        width: contentW.value,
        height: contentH.value,
        background: parsed.canvas?.background || '#ffffff'
      },
      elements: (parsed.elements || []).map(el => ({ ...el, id: el.id || newId() }))
    }
  } else {
    model.value = {
      version: 1,
      canvas: { width: contentW.value, height: contentH.value, background: '#ffffff' },
      elements: []
    }
    const bizName = bizTypes.value.find(b => b.code === form.bizType)?.name
    if (bizName) {
      model.value.elements.push({
        id: newId(),
        type: 'text',
        x: 0,
        y: 0,
        w: contentW.value,
        h: 10,
        text: bizName,
        fontSize: 20,
        bold: true,
        align: 'center'
      })
    }
  }
  selectedId.value = ''
  syncPaperPreset()
  loadCatalog(form.bizType)
}

function syncPaperPreset() {
  const hit = PAPER_PRESETS.find(
    p => p.key !== 'CUSTOM' && p.width === Number(form.paperWidth) && p.height === Number(form.paperHeight)
  )
  paperPreset.value = hit ? hit.key : 'CUSTOM'
}

function applyPreset(key: string) {
  const preset = PAPER_PRESETS.find(p => p.key === key)
  if (!preset || key === 'CUSTOM') return
  form.paperWidth = preset.width
  form.paperHeight = preset.height
  syncCanvasSize()
}

function applyPresetBySize() {
  syncPaperPreset()
  syncCanvasSize()
}

function syncCanvasSize() {
  model.value.canvas.width = contentW.value
  model.value.canvas.height = contentH.value
}

function handleBizTypeChange(v: string) {
  if (!form.id && model.value.elements.length === 0) {
    form.paperWidth = v === 'REAGENT_LABEL' ? 60 : 210
    form.paperHeight = v === 'REAGENT_LABEL' ? 40 : 297
    form.margin = v === 'REAGENT_LABEL' ? 2 : 8
  }
  syncPaperPreset()
  syncCanvasSize()
  loadCatalog(v)
}

/* ---------------- 画布与缩放 ---------------- */

function fitCanvas() {
  const box = stageRef.value
  if (!box) return
  const avail = Math.max(200, box.clientWidth - 56)
  const widthPx = contentW.value * MM_TO_PX
  const next = Math.min(2, Math.max(0.2, Math.round((avail / widthPx) * 20) / 20))
  zoom.value = next
  if (!zoomOptions.value.includes(next)) {
    zoomOptions.value = [...zoomOptions.value, next].sort((a, b) => a - b)
  }
}

function fs(el: PrintElement) {
  return (Number(el.fontSize) || 10) * zoom.value + 'px'
}

function elementBoxStyle(el: PrintElement) {
  const h = el.type === 'line' ? Math.max(Number(el.thickness) || 0.3, Number(el.h) || 0) : Number(el.h) || 0
  return {
    left: (Number(el.x) || 0) * scale.value + 'px',
    top: (Number(el.y) || 0) * scale.value + 'px',
    width: (Number(el.w) || 0) * scale.value + 'px',
    height: h * scale.value + 'px'
  }
}

/* ---------------- 拖拽与缩放 ---------------- */

function startDrag(e: MouseEvent, el: PrintElement, mode: 'move' | 'resize') {
  e.preventDefault()
  selectedId.value = el.id
  const startX = e.clientX
  const startY = e.clientY
  const ox = Number(el.x) || 0
  const oy = Number(el.y) || 0
  const ow = Number(el.w) || 0
  const oh = Number(el.h) || 0
  const unit = scale.value
  const round = (v: number) => Math.round(v * 2) / 2

  const onMove = (ev: MouseEvent) => {
    const dx = (ev.clientX - startX) / unit
    const dy = (ev.clientY - startY) / unit
    if (mode === 'move') {
      el.x = Math.max(0, round(ox + dx))
      el.y = Math.max(0, round(oy + dy))
    } else {
      el.w = Math.max(1, round(ow + dx))
      el.h = Math.max(el.type === 'line' ? 0 : 1, round(oh + dy))
    }
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

/* ---------------- 元素增删 ---------------- */

// 单调递增 + 随机后缀，保证重新打开模板时 id 不冲突
let idSeq = 0
function newId() {
  idSeq += 1
  return 'e' + Date.now().toString(36) + idSeq
}

function pushElement(el: PrintElement) {
  model.value.elements.push(el)
  selectedId.value = el.id
}

function nextY() {
  let max = 0
  for (const el of model.value.elements) {
    max = Math.max(max, (Number(el.y) || 0) + (Number(el.h) || 0))
  }
  const y = Math.min(max + 2, Math.max(0, contentH.value - 6))
  return Math.round(y * 2) / 2
}

function addText() {
  pushElement({
    id: newId(),
    type: 'text',
    x: 0,
    y: nextY(),
    w: Math.min(60, contentW.value),
    h: 8,
    text: '请输入文字',
    fontSize: 12,
    align: 'left'
  })
}

function addFieldElement(f: PrintField) {
  const labelWidth = Math.min(24, Math.max(10, f.title.length * 3))
  pushElement({
    id: newId(),
    type: 'field',
    x: 0,
    y: nextY(),
    w: contentW.value,
    h: 6,
    field: f.field,
    label: f.title,
    labelWidth,
    fontSize: 10
  })
}

function defaultCodeValue() {
  if (form.bizType === 'REAGENT_LABEL') return '{{barcode}}'
  const first = headerFields.value[0]
  return first ? `{{${first.field}}}` : '{{barcode}}'
}

function addBarcode() {
  pushElement({
    id: newId(),
    type: 'barcode',
    x: 0,
    y: nextY(),
    w: Math.min(52, contentW.value),
    h: 14,
    symbology: 'CODE128',
    value: defaultCodeValue(),
    showText: true
  })
}

function addQrcode() {
  const size = Math.min(30, contentW.value, Math.max(10, contentH.value))
  pushElement({
    id: newId(),
    type: 'qrcode',
    x: 0,
    y: nextY(),
    w: size,
    h: size,
    value: defaultCodeValue()
  })
}

function addLine() {
  pushElement({
    id: newId(),
    type: 'line',
    x: 0,
    y: nextY(),
    w: contentW.value,
    h: 0.6,
    thickness: 0.3,
    color: '#000000'
  })
}

function addRect() {
  pushElement({
    id: newId(),
    type: 'rect',
    x: 0,
    y: nextY(),
    w: Math.min(60, contentW.value),
    h: 20,
    thickness: 0.3,
    color: '#000000'
  })
}

function addTable() {
  const fields = itemFields.value.slice(0, 6)
  const per = Math.floor((contentW.value / Math.max(1, fields.length)) * 10) / 10
  pushElement({
    id: newId(),
    type: 'table',
    x: 0,
    y: nextY(),
    w: contentW.value,
    h: Math.min(120, Math.max(20, contentH.value - nextY() - 20)),
    rowHeight: 8,
    columns: fields.map(f => ({
      field: f.field,
      title: f.title,
      width: per,
      align: f.field === 'index' ? 'center' : 'left'
    }))
  })
}

function chooseImage() {
  fileInput.value?.click()
}

function handleFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 100 * 1024) {
    ElMessage.warning('图片超过 100KB，请压缩后再上传')
    input.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const src = String(reader.result || '')
    const cur = selected.value
    if (cur && cur.type === 'image') {
      cur.src = src
    } else {
      pushElement({
        id: newId(),
        type: 'image',
        x: 0,
        y: nextY(),
        w: 30,
        h: 20,
        src
      })
    }
  }
  reader.readAsDataURL(file)
  input.value = ''
}

function addTableColumn(f: PrintField) {
  const table = selected.value
  if (!table || table.type !== 'table') {
    ElMessage.warning('请先在画布中选中表格元素，再点击明细字段追加列')
    return
  }
  if (!table.columns) table.columns = []
  table.columns.push({ field: f.field, title: f.title, width: 20, align: 'left' })
}

function appendBlankColumn() {
  const table = selected.value
  if (!table || table.type !== 'table') return
  if (!table.columns) table.columns = []
  table.columns.push({ field: 'reagentCode', title: '列', width: 20, align: 'left' })
}

function removeColumn(index: number) {
  const table = selected.value
  if (!table || table.type !== 'table' || !table.columns) return
  table.columns.splice(index, 1)
}

function removeSelected() {
  const id = selectedId.value
  if (!id) return
  model.value.elements = model.value.elements.filter(e => e.id !== id)
  selectedId.value = ''
}

/* ---------------- 预览与保存 ---------------- */

function displayText(el: PrintElement) {
  return interpolate(el.text || '', sampleData.value)
}

function displayField(el: PrintElement) {
  return sampleHeader.value[el.field || ''] ?? ''
}

const svgCache = new Map<string, string>()

function barcodeSvg(el: PrintElement) {
  const value = interpolate(el.value || '', sampleData.value)
  const key = `B|${value}|${el.w}|${el.h}|${el.showText}`
  if (!svgCache.has(key)) {
    svgCache.set(key, renderBarcodeSvg(value, Number(el.w) || 0, Number(el.h) || 0, el.showText !== false))
  }
  return svgCache.get(key) || ''
}

function qrSvg(el: PrintElement) {
  const value = interpolate(el.value || '', sampleData.value)
  const key = `Q|${value}`
  if (!svgCache.has(key)) {
    svgCache.set(key, renderQrSvg(value))
  }
  return svgCache.get(key) || ''
}

const TYPE_NAMES: Record<string, string> = {
  text: '文本',
  field: '字段',
  barcode: '条码',
  qrcode: '二维码',
  line: '线条',
  rect: '矩形',
  image: '图片',
  table: '表格'
}

function typeName(type: string) {
  return TYPE_NAMES[type] || type
}

function buildPayload(): PrintTemplate | null {
  if (!form.templateCode.trim()) {
    ElMessage.warning('请填写模板编码')
    return null
  }
  if (!form.templateName.trim()) {
    ElMessage.warning('请填写模板名称')
    return null
  }
  if (!form.bizType) {
    ElMessage.warning('请选择业务类型')
    return null
  }
  if (model.value.elements.length === 0) {
    ElMessage.warning('请至少添加一个元素')
    return null
  }
  const canvas = {
    width: contentW.value,
    height: contentH.value,
    background: model.value.canvas.background || '#ffffff'
  }
  return {
    id: form.id,
    templateCode: form.templateCode.trim(),
    templateName: form.templateName.trim(),
    bizType: form.bizType,
    paperWidth: Number(form.paperWidth),
    paperHeight: Number(form.paperHeight),
    margin: Number(form.margin),
    templateJson: JSON.stringify({ version: model.value.version || 1, canvas, elements: model.value.elements }),
    isDefault: form.isDefault ? 1 : 0,
    status: form.status,
    remark: form.remark
  }
}

function handlePreview() {
  const payload = buildPayload()
  if (!payload) return
  previewTemplate(payload, [{ header: sampleHeader.value, items: [{}] }], { copies: 1 })
}

function handleSave() {
  const payload = buildPayload()
  if (!payload) return
  emit('save', payload)
}
</script>

<style scoped>
.pt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 12px;
}
.pt-head-title {
  font-size: 16px;
  font-weight: 600;
}
.pt-head-ops {
  display: flex;
  gap: 8px;
}
.pt-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}
.pt-sep {
  width: 1px;
  height: 20px;
  background: #dcdfe6;
  margin: 0 4px;
}
.pt-label {
  font-size: 12px;
  color: #606266;
}
.pt-body {
  display: flex;
  height: calc(100vh - 150px);
  min-height: 480px;
  border-top: 1px solid #e4e7ed;
}
.pt-palette {
  width: 210px;
  flex: 0 0 210px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  padding: 10px;
  background: #fbfcff;
}
.pt-palette-title {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  margin: 10px 0 6px;
}
.pt-palette-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pt-palette-btns .el-button {
  margin-left: 0;
}
.pt-field-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pt-field-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  background: #fff;
}
.pt-field-item:hover {
  border-color: #409eff;
  color: #409eff;
}
.pt-field-key {
  color: #909399;
  font-size: 11px;
}
.pt-field-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pt-palette-hint {
  margin-top: 6px;
  font-size: 11px;
  color: #a8abb2;
  line-height: 1.4;
}
.pt-stage {
  flex: 1;
  overflow: auto;
  padding: 16px;
  background: #eceff3;
}
.pt-tip {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}
.pt-canvas {
  position: relative;
  margin: 0 auto;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  outline: 1px dashed #c0c4cc;
}
.pt-canvas.is-grid {
  background-image:
    repeating-linear-gradient(to right, rgba(0, 0, 0, 0.06) 0 1px, transparent 1px 5mm),
    repeating-linear-gradient(to bottom, rgba(0, 0, 0, 0.06) 0 1px, transparent 1px 5mm);
}
.pt-el {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
  cursor: move;
}
.pt-el.is-selected {
  outline: 1px dashed #409eff;
  outline-offset: 1px;
}
.pt-resize {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  background: #409eff;
  cursor: se-resize;
}
.pt-text {
  width: 100%;
  height: 100%;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.35;
}
.pt-field-row {
  display: flex;
  align-items: flex-start;
  width: 100%;
  line-height: 1.35;
}
.pt-field-label {
  flex: 0 0 auto;
}
.pt-field-value {
  flex: 1;
  word-break: break-all;
}
.pt-code {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pt-code :deep(svg) {
  max-width: 100%;
  max-height: 100%;
}
.pt-rect {
  width: 100%;
  height: 100%;
  border-style: solid;
}
.pt-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.pt-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.pt-table th,
.pt-table td {
  border: 1px solid #333;
  padding: 0 2px;
  line-height: 1.2;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.pt-table th {
  background: #f2f2f2;
  font-weight: 600;
  text-align: center;
}
.pt-props {
  width: 300px;
  flex: 0 0 300px;
  border-left: 1px solid #e4e7ed;
  overflow-y: auto;
  padding: 10px;
}
.pt-props-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}
.pt-props-sub {
  margin-left: 6px;
  font-size: 12px;
  font-weight: normal;
  color: #909399;
}
.pt-form :deep(.el-form-item) {
  margin-bottom: 10px;
}
.pt-grid4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  width: 100%;
}
.pt-grid4 :deep(.el-input-number) {
  width: 100%;
}
.pt-col-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pt-col-row {
  display: flex;
  gap: 4px;
  align-items: center;
}
.pt-empty-tip {
  font-size: 12px;
  color: #a8abb2;
  padding: 12px 0;
}
</style>
