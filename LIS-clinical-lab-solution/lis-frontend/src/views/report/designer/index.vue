<template>
  <div class="report-designer">
    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button-group>
          <el-button size="small" :icon="Document" @click="handleNew">新建</el-button>
          <el-button size="small" :icon="RefreshLeft" @click="handleReset">恢复默认</el-button>
        </el-button-group>
        <el-divider direction="vertical" />
        <el-select v-model="currentTemplateId" placeholder="选择模板" size="small" style="width: 200px" @change="onTemplateSelect">
          <el-option v-for="t in templateList" :key="t.templateId" :label="t.templateName + (t.instrumentName ? ' [' + t.instrumentName + ']' : '') + (t.isDefault ? ' ★' : '')" :value="t.templateId" />
        </el-select>
        <el-input v-model="currentTemplateName" placeholder="模板名称" size="small" style="width: 150px" />
        <el-select v-model="currentInstrumentId" placeholder="关联仪器" size="small" clearable style="width: 160px">
          <el-option v-for="ins in instrumentOptions" :key="ins.id" :label="ins.instrumentName" :value="ins.id" />
        </el-select>
        <el-tooltip content="结果双列显示" placement="top">
          <el-switch v-model="currentDualColumn" active-text="双列" inactive-text="单列" size="small" />
        </el-tooltip>
        <el-button-group>
          <el-button size="small" :icon="Plus" @click="zoomIn">放大</el-button>
          <el-button size="small">{{ Math.round(zoom * 100) }}%</el-button>
          <el-button size="small" :icon="Minus" @click="zoomOut">缩小</el-button>
          <el-button size="small" @click="zoom = 1">100%</el-button>
        </el-button-group>
        <el-divider direction="vertical" />
        <el-button size="small" :icon="View" @click="previewVisible = true">预览</el-button>
        <el-button size="small" :icon="Printer" @click="handlePrint">打印</el-button>
      </div>
      <div class="toolbar-right">
        <span v-if="template.updateTime" class="update-time">
          最后保存: {{ formatTime(template.updateTime) }}
        </span>
        <el-button type="primary" size="small" :icon="Check" :loading="saving" @click="handleSave">
          保存模板
        </el-button>
        <el-button size="small" :icon="DocumentAdd" @click="handleSaveAsNew">另存为</el-button>
        <el-button size="small" type="danger" :icon="Delete" @click="handleDeleteTemplate" :disabled="!currentTemplateId">删除</el-button>
      </div>
    </div>

    <div class="designer-body">
      <!-- 左侧: 元素库 -->
      <div class="designer-left">
        <div class="panel-title">基础元素</div>
        <div class="element-list">
          <div
            v-for="item in elementLibrary"
            :key="item.type"
            class="element-item"
            @click="addElement(item.type)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </div>
        </div>

        <div class="panel-title" style="margin-top: 16px;">患者字段</div>
        <div class="field-list">
          <div
            v-for="f in fieldLibrary.patient"
            :key="f.value"
            class="field-item"
            @click="addField(f.value, f.label)"
          >
            {{ f.label }}
          </div>
        </div>

        <div class="panel-title" style="margin-top: 12px;">标本字段</div>
        <div class="field-list">
          <div
            v-for="f in fieldLibrary.sample"
            :key="f.value"
            class="field-item"
            @click="addField(f.value, f.label)"
          >
            {{ f.label }}
          </div>
        </div>

        <div class="panel-title" style="margin-top: 12px;">签名字段</div>
        <div class="field-list">
          <div
            v-for="f in fieldLibrary.signature"
            :key="f.value"
            class="field-item"
            @click="addField(f.value, f.label)"
          >
            {{ f.label }}
          </div>
        </div>

        <div class="panel-title" style="margin-top: 12px;">时间字段</div>
        <div class="field-list">
          <div
            v-for="f in fieldLibrary.time"
            :key="f.value"
            class="field-item"
            @click="addField(f.value, f.label)"
          >
            {{ f.label }}
          </div>
        </div>

        <div class="panel-title" style="margin-top: 16px;">数据源选择 <span class="panel-sub">自定义报表查询扩展</span></div>
        <div class="scene-category" v-for="cat in sceneCategories" :key="cat">
          <div class="scene-cat-label">{{ cat }}</div>
          <div class="scene-list">
            <div
              v-for="s in DATA_SCENES.filter(i => i.category === cat)"
              :key="s.key"
              class="scene-item"
              :class="{ active: currentSceneKey === s.key }"
              @click="currentSceneKey = s.key; applyScene(s.key)"
            >
              <span class="scene-name">{{ s.label }}</span>
              <span class="scene-desc" :title="s.desc">{{ s.desc }}</span>
            </div>
          </div>
        </div>
        <div class="panel-title" style="margin-top: 16px;">内置模板</div>
        <div class="builtin-list">
          <div
            v-for="m in BUILTIN_TEMPLATE_TYPES"
            :key="m.value"
            class="builtin-item"
            @click="loadBuiltInTemplate(m.value)"
          >
            <span class="builtin-name">{{ m.label }}</span>
            <span class="builtin-desc">{{ m.desc }}</span>
          </div>
        </div>

        <div class="panel-title" style="margin-top: 16px;">画布设置</div>
        <div class="canvas-settings">
          <el-form label-width="80px" size="small">
            <el-form-item label="纸张">
              <el-select v-model="paperSize" @change="applyPaperSize">
                <el-option v-for="(ps, key) in PAPER_SIZES" :key="key" :label="ps.label" :value="key" />
                <el-option label="自定义" value="custom" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="paperSize !== 'custom'" label="方向">
              <el-radio-group v-model="orientation" @change="applyOrientation">
                <el-radio-button value="portrait">纵向</el-radio-button>
                <el-radio-button value="landscape">横向</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="paperSize === 'custom'" label="宽度">
              <el-input-number v-model="template.canvasWidth" :min="300" :max="2000" :step="10" />
            </el-form-item>
            <el-form-item v-if="paperSize === 'custom'" label="高度">
              <el-input-number v-model="template.canvasHeight" :min="300" :max="3000" :step="10" />
            </el-form-item>
            <el-form-item label="背景色">
              <el-color-picker v-model="template.backgroundColor" />
            </el-form-item>
            <el-form-item label="边距">
              <el-input-number v-model="template.padding" :min="0" :max="100" :step="5" />
            </el-form-item>
          </el-form>
        </div>

        <div class="panel-title" style="margin-top: 16px;">图层管理</div>
        <div class="layer-list">
          <div
            v-for="(el, idx) in template.elements"
            :key="el.id"
            class="layer-item"
            :class="{ active: selectedId === el.id }"
            @click="selectedId = el.id"
          >
            <span class="layer-index">{{ idx + 1 }}</span>
            <span class="layer-name">{{ el.label }}</span>
            <el-icon class="layer-toggle" @click.stop="el.visible = !el.visible">
              <View v-if="el.visible" />
              <Hide v-else />
            </el-icon>
            <el-icon class="layer-delete" @click.stop="deleteElement(el.id)"><Delete /></el-icon>
          </div>
          <div v-if="!template.elements.length" class="empty-tip">暂无元素</div>
        </div>
      </div>

      <!-- 中间: 画布 -->
      <div class="designer-canvas-wrapper" ref="canvasWrapper">
        <div class="canvas-scroll">
          <div
            class="designer-canvas"
            :style="canvasStyle"
            @mousedown.self="selectedId = null"
          >
            <div
              v-for="el in template.elements"
              :key="el.id"
              v-show="el.visible"
              class="report-element"
              :class="{ selected: selectedId === el.id }"
              :style="elementStyle(el)"
              @mousedown.stop="startDrag($event, el)"
              @click.stop="selectedId = el.id"
            >
              <!-- 元素内容预览 -->
              <div class="element-content" :style="contentStyle(el)">
                <ElementPreview :el="el" :sample-data="sampleData" />
              </div>

              <!-- 选中时的边框和调整手柄 -->
              <template v-if="selectedId === el.id">
                <div class="element-border"></div>
                <div
                  v-for="h in resizeHandles"
                  :key="h"
                  class="resize-handle"
                  :class="'handle-' + h"
                  @mousedown.stop="startResize($event, el, h)"
                ></div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧: 属性面板 -->
      <div class="designer-right">
        <div class="panel-title">属性配置</div>
        <div v-if="!selectedElement" class="empty-tip">请选择一个元素</div>
        <el-form v-else label-width="80px" size="small" class="prop-form">
          <el-form-item label="元素名称">
            <el-input v-model="selectedElement.label" />
          </el-form-item>

          <el-divider content-position="left">位置与大小</el-divider>
          <el-form-item label="X坐标">
            <el-input-number v-model="selectedElement.x" :min="0" :step="1" />
          </el-form-item>
          <el-form-item label="Y坐标">
            <el-input-number v-model="selectedElement.y" :min="0" :step="1" />
          </el-form-item>
          <el-form-item label="宽度">
            <el-input-number v-model="selectedElement.width" :min="20" :step="1" />
          </el-form-item>
          <el-form-item label="高度">
            <el-input-number v-model="selectedElement.height" :min="20" :step="1" />
          </el-form-item>

          <el-divider content-position="left">字体样式</el-divider>
          <el-form-item label="字号">
            <el-input-number v-model="selectedElement.fontSize" :min="8" :max="72" :step="1" />
          </el-form-item>
          <el-form-item label="字重">
            <el-select v-model="selectedElement.fontWeight">
              <el-option label="细体 300" :value="300" />
              <el-option label="常规 400" :value="400" />
              <el-option label="中等 500" :value="500" />
              <el-option label="粗体 700" :value="700" />
              <el-option label="特粗 900" :value="900" />
            </el-select>
          </el-form-item>
          <el-form-item label="字体颜色">
            <el-color-picker v-model="selectedElement.color" />
          </el-form-item>
          <el-form-item label="背景色">
            <el-color-picker v-model="selectedElement.backgroundColor" show-alpha />
          </el-form-item>
          <el-form-item label="对齐">
            <el-radio-group v-model="selectedElement.textAlign">
              <el-radio-button value="left">左</el-radio-button>
              <el-radio-button value="center">中</el-radio-button>
              <el-radio-button value="right">右</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="边框">
            <el-switch v-model="selectedElement.border" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="显示">
            <el-switch v-model="selectedElement.visible" />
          </el-form-item>

          <!-- 自定义文本 -->
          <template v-if="selectedElement.type === 'custom-text' || selectedElement.type === 'hospital-name' || selectedElement.type === 'report-title'">
            <el-divider content-position="left">文本内容</el-divider>
            <el-form-item label="内容">
              <el-input
                v-model="selectedElement.text"
                type="textarea"
                :rows="3"
                placeholder="请输入文本内容"
              />
            </el-form-item>
          </template>

          <!-- 细粒度字段 -->
          <template v-if="selectedElement.type === 'field'">
            <el-divider content-position="left">字段信息</el-divider>
            <el-form-item label="字段名">
              <el-input v-model="selectedElement.fieldLabel" placeholder="如 姓名" />
            </el-form-item>
            <el-form-item label="字段键">
              <el-input v-model="selectedElement.fieldKey" placeholder="如 patientName" />
            </el-form-item>
          </template>

          <!-- 结果表格列 -->
          <template v-if="selectedElement.type === 'results-table'">
            <el-divider content-position="left">表格列</el-divider>
            <el-checkbox-group v-model="selectedElement.columns">
              <el-checkbox v-for="c in resultColumns" :key="c.value" :value="c.value" class="field-checkbox">
                {{ c.label }}
              </el-checkbox>
            </el-checkbox-group>
          </template>

          <!-- 微生物细菌鉴定列 -->
          <template v-if="selectedElement.type === 'micro-organism'">
            <el-divider content-position="left">细菌鉴定列</el-divider>
            <el-checkbox-group v-model="selectedElement.columns">
              <el-checkbox v-for="c in microOrganismColumns" :key="c.value" :value="c.value" class="field-checkbox">
                {{ c.label }}
              </el-checkbox>
            </el-checkbox-group>
          </template>

          <!-- 微生物药敏列 -->
          <template v-if="selectedElement.type === 'micro-sensitivity'">
            <el-divider content-position="left">药敏列</el-divider>
            <el-checkbox-group v-model="selectedElement.columns">
              <el-checkbox v-for="c in microSensitivityColumns" :key="c.value" :value="c.value" class="field-checkbox">
                {{ c.label }}
              </el-checkbox>
            </el-checkbox-group>
          </template>

          <el-divider content-position="left">操作</el-divider>
          <el-form-item label=" ">
            <el-button size="small" @click="deleteElement(selectedElement.id)">删除元素</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="报告预览" width="900px" class="preview-dialog">
      <div class="preview-wrapper">
        <ReportRenderer :template="template" :data="sampleData" :dual-column="currentDualColumn" fit-to-page />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, h, defineComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document, RefreshLeft, Plus, Minus, View, Hide, Delete, Check, Printer,
  OfficeBuilding, Files, PieChart, User, Box, Calendar, EditPen, ChatLineSquare, Bell, DocumentAdd
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  type ReportTemplate, type ReportElement, type ReportElementType, type ReportTemplateConfig,
  getDefaultTemplate, loadTemplate, saveTemplate, resetTemplate,
  getTemplateList, getTemplateById, createTemplate, updateTemplate, deleteTemplate, setDefaultTemplate,
  getBuiltInTemplate, BUILTIN_TEMPLATE_TYPES, PAPER_SIZES, applyPaperSizeToTemplate
} from '@/api/report/template'
import { request } from '@/api/request'
import ReportRenderer from '@/views/report/components/ReportRenderer.vue'

// ========== 元素库定义 ==========
const elementLibrary: { type: ReportElementType; label: string; icon: any; fieldKey?: string; fieldLabel?: string }[] = [
  { type: 'hospital-name', label: '医院名称', icon: OfficeBuilding },
  { type: 'report-title', label: '报告标题', icon: Document },
  { type: 'barcode', label: '条码', icon: PieChart },
  { type: 'results-table', label: '结果表格', icon: Files },
  { type: 'custom-text', label: '自定义文本', icon: Bell },
  { type: 'micro-organism', label: '细菌鉴定', icon: ChatLineSquare },
  { type: 'micro-sensitivity', label: '药敏结果', icon: EditPen }
]

// 细粒度字段库(按分类)
const fieldLibrary = {
  patient: [
    { value: 'patientName', label: '姓名' },
    { value: 'gender', label: '性别' },
    { value: 'age', label: '年龄' },
    { value: 'visitNo', label: '就诊号' },
    { value: 'department', label: '科室' },
    { value: 'bedNo', label: '床号' },
    { value: 'doctor', label: '申请医生' },
    { value: 'clinicalDiag', label: '临床诊断' },
    { value: 'idCard', label: '身份证号' },
    { value: 'phone', label: '电话' }
  ],
  sample: [
    { value: 'sampleBarcode', label: '条码号' },
    { value: 'sampleType', label: '标本类型' },
    { value: 'containerType', label: '容器类型' },
    { value: 'collectTime', label: '采集时间' },
    { value: 'deliverTime', label: '送检时间' },
    { value: 'signOffTime', label: '签收时间' },
    { value: 'receiveTime', label: '核收时间' },
    { value: 'specimenNo', label: '标本号' }
  ],
  signature: [
    { value: 'checker', label: '检验者' },
    { value: 'auditor', label: '审核者' },
    { value: 'reportDoctor', label: '报告医生' },
    { value: 'collector', label: '采集者' },
    { value: 'receiver', label: '核收者' }
  ],
  time: [
    { value: 'reportTime', label: '报告时间' }
  ]
}

// 字段定义
const patientFields = [
  { value: 'patientName', label: '姓名' },
  { value: 'gender', label: '性别' },
  { value: 'age', label: '年龄' },
  { value: 'visitNo', label: '就诊号' },
  { value: 'department', label: '科室' },
  { value: 'bedNo', label: '床号' },
  { value: 'doctor', label: '申请医生' },
  { value: 'clinicalDiag', label: '临床诊断' },
  { value: 'idCard', label: '身份证号' },
  { value: 'phone', label: '电话' }
]
const sampleFields = [
  { value: 'sampleBarcode', label: '条码号' },
  { value: 'sampleType', label: '标本类型' },
  { value: 'containerType', label: '容器类型' },
  { value: 'collectTime', label: '采集时间' },
  { value: 'deliverTime', label: '送检时间' },
  { value: 'signOffTime', label: '签收时间' },
  { value: 'receiveTime', label: '核收时间' },
  { value: 'specimenNo', label: '标本号' }
]
const signatureFields = [
  { value: 'checker', label: '检验者' },
  { value: 'auditor', label: '审核者' },
  { value: 'reportDoctor', label: '报告医生' },
  { value: 'collector', label: '采集者' },
  { value: 'receiver', label: '核收者' }
]
const resultColumns = [
  { value: 'idx', label: '序号' },
  { value: 'testItemName', label: '中文名称' },
  { value: 'englishCode', label: '英文缩写' },
  { value: 'resultValue', label: '结果' },
  { value: 'unit', label: '单位' },
  { value: 'refRange', label: '参考' },
  { value: 'lastResult', label: '上次结果' },
  { value: 'lastDate', label: '上次日期' },
  { value: 'lastFlag', label: '对比' },
  { value: 'testMethod', label: '检验方法' },
  { value: 'abnormalFlag', label: '异常标志' },
  { value: 'isCritical', label: '危急值' },
  { value: 'testTime', label: '检测时间' },
  { value: 'instrumentName', label: '检测仪器' }
]
// 微生物细菌鉴定列
const microOrganismColumns = [
  { value: 'organismName', label: '细菌名称' },
  { value: 'organismCode', label: '细菌编码' },
  { value: 'quantity', label: '生长量' },
  { value: 'colonsMethod', label: '培养方法' }
]
// 微生物药敏列
const microSensitivityColumns = [
  { value: 'antibioticName', label: '抗生素' },
  { value: 'micValue', label: 'MIC' },
  { value: 'result', label: '结果' },
  { value: 'breakpoint', label: '判读界值' }
]

// ========== 模板数据 ==========
const template = reactive<ReportTemplate>(getDefaultTemplate())
const selectedId = ref<string | null>(null)
const zoom = ref(1)
const paperSize = ref('A4')
const orientation = ref<'portrait' | 'landscape'>('portrait')
const saving = ref(false)
const templateList = ref<ReportTemplateConfig[]>([])
const currentTemplateId = ref<number | null>(null)
const currentTemplateName = ref('')
const currentInstrumentId = ref<number | null>(null)
const currentDualColumn = ref(false)
const instrumentOptions = ref<any[]>([])
const previewVisible = ref(false)

const selectedElement = computed(() => template.elements.find(e => e.id === selectedId.value) || null)

const canvasStyle = computed(() => ({
  width: template.canvasWidth + 'px',
  height: template.canvasHeight + 'px',
  backgroundColor: template.backgroundColor,
  padding: template.padding + 'px',
  transform: `scale(${zoom.value})`,
  transformOrigin: 'top center'
}))

function elementStyle(el: ReportElement) {
  return {
    left: el.x + 'px',
    top: el.y + 'px',
    width: el.width + 'px',
    height: el.height + 'px'
  }
}

function contentStyle(el: ReportElement) {
  return {
    fontSize: el.fontSize + 'px',
    fontWeight: el.fontWeight,
    color: el.color,
    backgroundColor: el.backgroundColor === 'transparent' ? 'transparent' : el.backgroundColor,
    textAlign: el.textAlign as any,
    border: el.border ? `1px solid #606266` : 'none'
  }
}

// ========== 拖拽逻辑 ==========
const dragState = reactive({
  active: false,
  startX: 0,
  startY: 0,
  origX: 0,
  origY: 0,
  el: null as ReportElement | null
})

function startDrag(e: MouseEvent, el: ReportElement) {
  selectedId.value = el.id
  dragState.active = true
  dragState.startX = e.clientX
  dragState.startY = e.clientY
  dragState.origX = el.x
  dragState.origY = el.y
  dragState.el = el
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
}

function onDrag(e: MouseEvent) {
  if (!dragState.active || !dragState.el) return
  const dx = (e.clientX - dragState.startX) / zoom.value
  const dy = (e.clientY - dragState.startY) / zoom.value
  const el = dragState.el
  const maxX = Math.max(0, template.canvasWidth - el.width)
  const maxY = Math.max(0, template.canvasHeight - el.height)
  el.x = Math.min(maxX, Math.max(0, Math.round(dragState.origX + dx)))
  el.y = Math.min(maxY, Math.max(0, Math.round(dragState.origY + dy)))
}

function endDrag() {
  dragState.active = false
  dragState.el = null
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
}

// ========== 调整大小逻辑 ==========
const resizeHandles = ['tl', 'tr', 'bl', 'br', 't', 'b', 'l', 'r']
const resizeState = reactive({
  active: false,
  handle: '',
  startX: 0,
  startY: 0,
  origX: 0, origY: 0, origW: 0, origH: 0,
  el: null as ReportElement | null
})

function startResize(e: MouseEvent, el: ReportElement, handle: string) {
  resizeState.active = true
  resizeState.handle = handle
  resizeState.startX = e.clientX
  resizeState.startY = e.clientY
  resizeState.origX = el.x
  resizeState.origY = el.y
  resizeState.origW = el.width
  resizeState.origH = el.height
  resizeState.el = el
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', endResize)
}

function onResize(e: MouseEvent) {
  if (!resizeState.active || !resizeState.el) return
  const dx = (e.clientX - resizeState.startX) / zoom.value
  const dy = (e.clientY - resizeState.startY) / zoom.value
  const el = resizeState.el
  const h = resizeState.handle
  const maxW = template.canvasWidth
  const maxH = template.canvasHeight

  if (h.includes('l')) {
    let nx = Math.max(0, resizeState.origX + dx)
    nx = Math.min(nx, resizeState.origX + resizeState.origW - 20)
    el.x = Math.round(nx)
    el.width = Math.round(resizeState.origW - (nx - resizeState.origX))
  }
  if (h.includes('r')) {
    el.width = Math.min(maxW - el.x, Math.max(20, Math.round(resizeState.origW + dx)))
  }
  if (h.includes('t')) {
    let ny = Math.max(0, resizeState.origY + dy)
    ny = Math.min(ny, resizeState.origY + resizeState.origH - 20)
    el.y = Math.round(ny)
    el.height = Math.round(resizeState.origH - (ny - resizeState.origY))
  }
  if (h.includes('b')) {
    el.height = Math.min(maxH - el.y, Math.max(20, Math.round(resizeState.origH + dy)))
  }
}

function endResize() {
  resizeState.active = false
  resizeState.el = null
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', endResize)
}

// ========== 元素操作 ==========
function addElement(type: ReportElementType) {
  const id = 'el-' + Date.now()
  const libItem = elementLibrary.find(i => i.type === type)!
  const newEl: ReportElement = {
    id,
    type,
    label: libItem.label,
    x: 50 + (template.elements.length % 10) * 10,
    y: 50 + (template.elements.length % 10) * 10,
    width: 200,
    height: 60,
    fontSize: 13,
    fontWeight: 400,
    color: '#000000',
    backgroundColor: 'transparent',
    textAlign: 'left',
    border: 0,
    visible: true
  }
  if (type === 'custom-text') newEl.text = '请输入文本'
  if (type === 'hospital-name') { newEl.text = 'XX医院检验科'; newEl.fontSize = 24; newEl.fontWeight = 700; newEl.textAlign = 'center'; newEl.width = 714 }
  if (type === 'report-title') { newEl.text = '检验报告单'; newEl.fontSize = 18; newEl.fontWeight = 600; newEl.textAlign = 'center'; newEl.width = 714 }
  if (type === 'results-table') {
    newEl.width = 889
    newEl.height = 280
    newEl.border = 1
    newEl.columns = ['idx', 'testItemName', 'englishCode', 'resultValue', 'unit', 'refRange', 'testMethod']
  }
  if (type === 'micro-organism') {
    newEl.width = 700
    newEl.height = 180
    newEl.border = 1
    newEl.columns = ['organismName', 'organismCode', 'quantity', 'colonsMethod']
  }
  if (type === 'micro-sensitivity') {
    newEl.width = 700
    newEl.height = 200
    newEl.border = 1
    newEl.columns = ['antibioticName', 'micValue', 'result', 'breakpoint']
  }
  template.elements.push(newEl)
  selectedId.value = id
  ElMessage.success(`已添加: ${libItem.label}`)
}

/** 添加细粒度字段 */
function addField(fieldKey: string, fieldLabel: string) {
  const id = 'el-' + Date.now() + '-' + fieldKey
  const newEl: ReportElement = {
    id,
    type: 'field',
    label: fieldLabel,
    x: 50 + (template.elements.length % 10) * 5,
    y: 50 + (template.elements.length % 10) * 5,
    width: 150,
    height: 24,
    fontSize: 13,
    fontWeight: 400,
    color: '#000000',
    backgroundColor: 'transparent',
    textAlign: 'left',
    border: 0,
    visible: true,
    fieldKey,
    fieldLabel
  }
  template.elements.push(newEl)
  selectedId.value = id
  ElMessage.success(`已添加字段: ${fieldLabel}`)
}

function deleteElement(id: string) {
  const idx = template.elements.findIndex(e => e.id === id)
  if (idx >= 0) {
    template.elements.splice(idx, 1)
    if (selectedId.value === id) selectedId.value = null
  }
}

// ========== 纸张大小 ==========
function applyPaperSize(size: string) {
  if (applyPaperSizeToTemplate(template, size, orientation.value)) {
    paperSize.value = size
    return
  }
  // 自定义纸张: 保持当前画布尺寸, 仅记录 paperSize
  paperSize.value = size
}

/** 切换横向/纵向: 交换画布宽高 */
function applyOrientation(dir: 'portrait' | 'landscape') {
  if (paperSize.value === 'custom') {
    // 自定义纸张: 直接交换宽高
    const w = template.canvasWidth
    template.canvasWidth = template.canvasHeight
    template.canvasHeight = w
    return
  }
  applyPaperSizeToTemplate(template, paperSize.value, dir)
}

// ========== 缩放 ==========
function zoomIn() { zoom.value = Math.min(2, zoom.value + 0.1) }
function zoomOut() { zoom.value = Math.max(0.3, zoom.value - 0.1) }

// ========== 模板操作 ==========
function handleNew() {
  ElMessageBox.confirm('确认新建空白模板? 当前未保存的修改将丢失', '提示', { type: 'warning' })
    .then(() => {
      template.canvasWidth = 794
      template.canvasHeight = 1123
      template.backgroundColor = '#ffffff'
      template.padding = 40
      template.elements = []
      selectedId.value = null
      ElMessage.success('已新建空白模板')
    })
    .catch(() => {})
}

function handleReset() {
  ElMessageBox.confirm('确认恢复为默认模板? 当前模板将被覆盖', '提示', { type: 'warning' })
    .then(() => {
      const def = resetTemplate()
      Object.assign(template, JSON.parse(JSON.stringify(def)))
      selectedId.value = null
      ElMessage.success('已恢复默认模板')
    })
    .catch(() => {})
}

/** 加载内置模板预设到画布 (作为新模板, 可继续编辑后另存) */
function loadBuiltInTemplate(type: any) {
  const preset = getBuiltInTemplate(type)
  const def = getDefaultTemplate()
  Object.assign(template, JSON.parse(JSON.stringify({ ...def, ...preset })))
  // 严格按对应纸张设置画布尺寸 (A4/B5 等), 保证画布与纸张一致
  const ps = preset.paperSize || 'A4'
  orientation.value = (preset.orientation as 'portrait' | 'landscape') || 'portrait'
  paperSize.value = applyPaperSizeToTemplate(template, ps, orientation.value) ? ps : 'custom'
  // 内容超出画布的元素自动收缩到画布内 (超标部分自动缩进)
  fitElementsToCanvas()
  currentTemplateId.value = null
  currentTemplateName.value = (preset.templateName || '内置模板') + '_副本'
  currentDualColumn.value = preset.dualColumn || false
  selectedId.value = null
  ElMessage.success(`已加载内置模板: ${preset.templateName}`)
}

/** 将超出画布边界的元素自动收缩/约束到画布内 */
function fitElementsToCanvas() {
  const cw = template.canvasWidth
  const ch = template.canvasHeight
  template.elements.forEach(el => {
    if (el.x + el.width > cw) el.width = Math.max(20, cw - el.x)
    if (el.y + el.height > ch) el.height = Math.max(20, ch - el.y)
    if (el.x > cw - 20) el.x = Math.max(0, cw - el.width)
    if (el.y > ch - 20) el.y = Math.max(0, ch - el.height)
  })
}

async function loadTemplateList() {
  templateList.value = await getTemplateList()
}

async function loadInstruments() {
  try {
    const res: any = await request.get('/base/instruments/all')
    instrumentOptions.value = res || []
  } catch {
    instrumentOptions.value = []
  }
}

async function onTemplateSelect(id: number | null) {
  if (!id) return
  const t = await getTemplateById(id)
  if (t) {
    const def = getDefaultTemplate()
    Object.assign(template, JSON.parse(JSON.stringify({ ...def, ...t })))
    paperSize.value = t.paperSize || 'A4'
    orientation.value = (t.orientation as 'portrait' | 'landscape') || 'portrait'
    // 按保存的纸张与方向重设画布 (兼容旧数据/保证方向生效)
    applyPaperSizeToTemplate(template, paperSize.value, orientation.value)
    currentTemplateName.value = t.templateName || ''
    currentInstrumentId.value = t.instrumentId || null
    currentDualColumn.value = t.dualColumn || false
    selectedId.value = null
    ElMessage.success(`已加载模板: ${t.templateName}`)
  }
}

async function handleSave() {
  if (!currentTemplateName.value.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  saving.value = true
  try {
    const config: ReportTemplateConfig = {
      ...template,
      templateId: currentTemplateId.value || undefined,
      templateName: currentTemplateName.value,
      instrumentId: currentInstrumentId.value,
      dualColumn: currentDualColumn.value,
      paperSize: paperSize.value,
      orientation: orientation.value,
      margin: 10
    }
    if (currentTemplateId.value) {
      const updated = await updateTemplate(config)
      if (updated) {
        ElMessage.success('模板更新成功')
      } else {
        ElMessage.error('更新失败')
      }
    } else {
      const created = await createTemplate(config)
      if (created && created.templateId) {
        currentTemplateId.value = created.templateId
        ElMessage.success('模板创建成功')
      } else {
        ElMessage.error('创建失败')
      }
    }
    await loadTemplateList()
    // 同时保存到本地缓存
    await saveTemplate(template)
  } catch (e) {
    ElMessage.error('保存失败: ' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function handleSaveAsNew() {
  currentTemplateId.value = null
  currentTemplateName.value = currentTemplateName.value ? currentTemplateName.value + '_副本' : '新模板'
  await handleSave()
}

async function handleDeleteTemplate() {
  if (!currentTemplateId.value) return
  ElMessageBox.confirm('确认删除当前模板? 删除后不可恢复', '提示', { type: 'warning' })
    .then(async () => {
      const ok = await deleteTemplate(currentTemplateId.value!)
      if (ok) {
        ElMessage.success('模板已删除')
        currentTemplateId.value = null
        currentTemplateName.value = ''
        await loadTemplateList()
        // 加载默认模板
        const def = resetTemplate()
        Object.assign(template, JSON.parse(JSON.stringify(def)))
      } else {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

function handlePrint() {
  previewVisible.value = true
  setTimeout(() => {
    window.print()
  }, 500)
}

function formatTime(t?: string) {
  if (!t) return '-'
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

// ========== 示例数据(预览用) ==========
const sampleData = reactive<any>({
  patientName: '张三',
  gender: '男',
  age: '35岁',
  patientGender: 1,
  patientAge: 35,
  visitNo: 'V20260731001',
  inpatientNo: 'ZY20260731008',
  department: '心血管内科',
  bedNo: '12床',
  doctor: '李医生',
  orderDoctor: '李明',
  clinicalDiag: '原发性高血压(2级,中危);2型糖尿病',
  idCard: '110101********1234',
  phone: '138****8888',
  sampleBarcode: '260731000001',
  sampleType: '静脉血清',
  sampleTypeDict: '静脉全血',
  specimenType: '血清',
  specimenNo: 'S001',
  containerType: '黄头管 促凝管',
  collectTime: '2026-07-31 08:30:00',
  deliverTime: '2026-07-31 08:45:00',
  signOffTime: '2026-07-31 09:00:00',
  receiveTime: '2026-07-31 09:15:00',
  receiverName: '王丽华',
  collectorName: '赵雪梅',
  checker: '王建国',
  testerName: '王建国',
  auditor: '陈志远',
  auditorName: '陈志远',
  reportDoctor: '陈志远',
  reporterName: '陈志远',
  instrumentName: 'BS-2000M / MAGLUMI X8',
  hospitalName: '广东省第一人民医院',
  hospitalSubName: 'Guangdong First Provincial People\'s Hospital',
  reportTime: '2026-07-31 10:30:00',
  applyTime: '2026-07-31 08:00:00',
  orderTime: '2026-07-31 08:00:00',
  mutuallyRecognized: 1,
  results: [
    { testItemName: '白细胞计数(WBC)', englishCode: 'WBC', resultValue: '6.5', unit: '10^9/L', refRange: '4.0-10.0', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '激光流式法', instrumentName: 'BC-7500', panelName: '血常规' },
    { testItemName: '红细胞计数(RBC)', englishCode: 'RBC', resultValue: '5.2', unit: '10^12/L', refRange: '4.3-5.8', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '鞘流阻抗法', instrumentName: 'BC-7500', panelName: '血常规' },
    { testItemName: '血红蛋白浓度(HGB)', englishCode: 'HGB', resultValue: '165', unit: 'g/L', refRange: '130-175', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '比色法', instrumentName: 'BC-7500', panelName: '血常规' },
    { testItemName: '血小板计数(PLT)', englishCode: 'PLT', resultValue: '238', unit: '10^9/L', refRange: '125-350', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式荧光法', instrumentName: 'BC-7500', panelName: '血常规' },
    { testItemName: '空腹血糖(GLU)', englishCode: 'GLU', resultValue: '2.8', unit: 'mmol/L', refRange: '3.9-6.1', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '葡萄糖氧化酶法', instrumentName: 'BS-2000M', panelName: '生化全套' },
    { testItemName: '总胆固醇(TC)', englishCode: 'TC', resultValue: '6.82', unit: 'mmol/L', refRange: '<5.18', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'COD-PAP法', instrumentName: 'BS-2000M', panelName: '生化全套' },
    { testItemName: '甘油三酯(TG)', englishCode: 'TG', resultValue: '2.41', unit: 'mmol/L', refRange: '<1.70', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'GPO-PAP法', instrumentName: 'BS-2000M', panelName: '生化全套' },
    { testItemName: '高密度脂蛋白胆固醇(HDL-C)', englishCode: 'HDL-C', resultValue: '1.05', unit: 'mmol/L', refRange: '>1.04', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '选择抑制法', instrumentName: 'BS-2000M', panelName: '生化全套' },
    { testItemName: '肌酐(CREA)', englishCode: 'CREA', resultValue: '82', unit: 'μmol/L', refRange: '57-97', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '肌氨酸氧化酶法', instrumentName: 'BS-2000M', panelName: '生化全套' },
    { testItemName: '丙氨酸氨基转移酶(ALT)', englishCode: 'ALT', resultValue: '42', unit: 'U/L', refRange: '9-50', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: 'IFCC速率法', instrumentName: 'BS-2000M', panelName: '肝功能' },
    { testItemName: '乙肝表面抗原(HBsAg)', englishCode: 'HBsAg', resultValue: '0.05', unit: 'IU/mL', refRange: '<0.05', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8', panelName: '术前感染筛查' },
    { testItemName: '糖化血红蛋白(HbA1c)', englishCode: 'HbA1c', resultValue: '7.8', unit: '%', refRange: '4.0-6.0', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '高效液相色谱法(HPLC)', instrumentName: 'HLC-723G8', panelName: '糖尿病专项' }
  ],
  warmTips: ['乙肝表面抗原(HBsAg)阴性者,建议定期复查;阳性者建议进一步做乙肝DNA定量及肝功能评估。'],
  remark: '本报告结果仅对该送检标本负责,如有疑问请于3个工作日内与检验科联系。',
  organisms: [
    {
      organismName: '金黄色葡萄球菌',
      organismCode: 'SA',
      quantity: '++',
      colonsMethod: '需氧培养',
      sensitivities: [
        { antibioticName: '青霉素', micValue: '≥0.5', result: 'R', breakpoint: '≤0.125' },
        { antibioticName: '万古霉素', micValue: '≤1', result: 'S', breakpoint: '≤2' },
        { antibioticName: '利奈唑胺', micValue: '≤2', result: 'S', breakpoint: '≤4' },
        { antibioticName: '红霉素', micValue: '≥8', result: 'R', breakpoint: '≤0.5' }
      ]
    }
  ]
})

// ========== 自定义查询场景数据集 (用户可在此自行扩展数据源) ==========
interface SceneDataSet {
  key: string
  label: string
  desc: string
  category: '检验报告' | '微生物报告' | '历史对比' | '统计报表'
  build: () => Record<string, any>
}
function basePatient() {
  return {
    patientName: sampleData.patientName, gender: sampleData.gender, age: sampleData.age,
    patientGender: sampleData.patientGender, patientAge: sampleData.patientAge,
    visitNo: sampleData.visitNo, inpatientNo: sampleData.inpatientNo,
    department: sampleData.department, bedNo: sampleData.bedNo,
    doctor: sampleData.doctor, orderDoctor: sampleData.orderDoctor,
    clinicalDiag: sampleData.clinicalDiag, idCard: sampleData.idCard, phone: sampleData.phone,
    sampleBarcode: sampleData.sampleBarcode, sampleType: sampleData.sampleType,
    specimenType: sampleData.specimenType, specimenNo: sampleData.specimenNo,
    containerType: sampleData.containerType, collectTime: sampleData.collectTime,
    deliverTime: sampleData.deliverTime, signOffTime: sampleData.signOffTime,
    receiveTime: sampleData.receiveTime, receiverName: sampleData.receiverName,
    collectorName: sampleData.collectorName, checker: sampleData.checker,
    testerName: sampleData.testerName, auditor: sampleData.auditor,
    auditorName: sampleData.auditorName, reportDoctor: sampleData.reportDoctor,
    reporterName: sampleData.reporterName, instrumentName: sampleData.instrumentName,
    hospitalName: sampleData.hospitalName, hospitalSubName: sampleData.hospitalSubName,
    reportTime: sampleData.reportTime, applyTime: sampleData.applyTime,
    orderTime: sampleData.orderTime, mutuallyRecognized: 1
  }
}
const DATA_SCENES: SceneDataSet[] = [
  {
    key: 'chem_blood',
    label: '生化+血常规(默认)',
    desc: '标准7列:生化+血常规,含危急值GLU',
    category: '检验报告',
    build: () => ({ ...basePatient(),
      results: [
        { testItemName: '白细胞计数(WBC)', englishCode: 'WBC', resultValue: '6.5', unit: '10^9/L', refRange: '4.0-10.0', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '激光流式法', instrumentName: 'BC-7500' },
        { testItemName: '红细胞计数(RBC)', englishCode: 'RBC', resultValue: '5.2', unit: '10^12/L', refRange: '4.3-5.8', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '鞘流阻抗法', instrumentName: 'BC-7500' },
        { testItemName: '血红蛋白浓度(HGB)', englishCode: 'HGB', resultValue: '165', unit: 'g/L', refRange: '130-175', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '比色法', instrumentName: 'BC-7500' },
        { testItemName: '血小板计数(PLT)', englishCode: 'PLT', resultValue: '238', unit: '10^9/L', refRange: '125-350', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式荧光法', instrumentName: 'BC-7500' },
        { testItemName: '空腹血糖(GLU)', englishCode: 'GLU', resultValue: '2.8', unit: 'mmol/L', refRange: '3.9-6.1', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '葡萄糖氧化酶法', instrumentName: 'BS-2000M' },
        { testItemName: '总胆固醇(TC)', englishCode: 'TC', resultValue: '6.82', unit: 'mmol/L', refRange: '<5.18', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'COD-PAP法', instrumentName: 'BS-2000M' },
        { testItemName: '甘油三酯(TG)', englishCode: 'TG', resultValue: '2.41', unit: 'mmol/L', refRange: '<1.70', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'GPO-PAP法', instrumentName: 'BS-2000M' },
        { testItemName: '高密度脂蛋白胆固醇(HDL-C)', englishCode: 'HDL-C', resultValue: '1.05', unit: 'mmol/L', refRange: '>1.04', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '选择抑制法', instrumentName: 'BS-2000M' },
        { testItemName: '肌酐(CREA)', englishCode: 'CREA', resultValue: '82', unit: 'μmol/L', refRange: '57-97', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '肌氨酸氧化酶法', instrumentName: 'BS-2000M' },
        { testItemName: '丙氨酸氨基转移酶(ALT)', englishCode: 'ALT', resultValue: '42', unit: 'U/L', refRange: '9-50', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: 'IFCC速率法', instrumentName: 'BS-2000M' },
        { testItemName: '乙肝表面抗原(HBsAg)', englishCode: 'HBsAg', resultValue: '0.05', unit: 'IU/mL', refRange: '<0.05', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '糖化血红蛋白(HbA1c)', englishCode: 'HbA1c', resultValue: '7.8', unit: '%', refRange: '4.0-6.0', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '高效液相色谱法(HPLC)', instrumentName: 'HLC-723G8' }
      ],
      remark: '本报告结果仅对该送检标本负责,如有疑问请于3个工作日内与检验科联系。',
      warmTips: ['乙肝表面抗原(HBsAg)阴性者建议定期复查;阳性者建议进一步做乙肝DNA定量。'],
      organisms: []
    })
  },
  {
    key: 'urinalysis',
    label: '尿液分析(化学+沉渣)',
    desc: '尿常规专用:左右双栏(化学+沉渣)',
    category: '检验报告',
    build: () => ({ ...basePatient(),
      sampleType: '随机尿液', specimenType: '尿液', containerType: '尿管 10mL',
      mutuallyRecognized: 1,
      results: [
        // 尿液化学 (左表)
        { testItemName: '尿比重(SG)', englishCode: 'SG', resultValue: '1.020', unit: '', refRange: '1.003-1.030', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '试带法', groupKey: 'urine-chem' },
        { testItemName: '尿pH', englishCode: 'pH', resultValue: '6.0', unit: '', refRange: '4.5-8.0', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '试带法', groupKey: 'urine-chem' },
        { testItemName: '尿蛋白(PRO)', englishCode: 'PRO', resultValue: '++', unit: '', refRange: '阴性', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '磺基水杨酸法', groupKey: 'urine-chem' },
        { testItemName: '尿葡萄糖(GLU)', englishCode: 'GLU', resultValue: '3+', unit: '', refRange: '阴性', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '葡萄糖氧化酶法', groupKey: 'urine-chem' },
        { testItemName: '尿酮体(KET)', englishCode: 'KET', resultValue: '阴性', unit: '', refRange: '阴性', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '亚硝基铁氰化钠法', groupKey: 'urine-chem' },
        { testItemName: '尿胆红素(BIL)', englishCode: 'BIL', resultValue: '阴性', unit: '', refRange: '阴性', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '重氮法', groupKey: 'urine-chem' },
        { testItemName: '尿胆原(URO)', englishCode: 'URO', resultValue: '正常', unit: '', refRange: '正常', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: 'Ehrlich法', groupKey: 'urine-chem' },
        { testItemName: '尿隐血(BLD)', englishCode: 'BLD', resultValue: '+', unit: '', refRange: '阴性', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '过氧化物酶法', groupKey: 'urine-chem' },
        { testItemName: '尿白细胞(LEU)', englishCode: 'LEU', resultValue: '2+', unit: '', refRange: '阴性', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '酯酶法', groupKey: 'urine-chem' },
        { testItemName: '尿亚硝酸盐(NIT)', englishCode: 'NIT', resultValue: '阳性', unit: '', refRange: '阴性', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'Griess法', groupKey: 'urine-chem' },
        { testItemName: '尿液颜色', englishCode: 'COLOR', resultValue: '黄', unit: '', refRange: '黄-琥珀', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '目测法', groupKey: 'urine-chem' },
        { testItemName: '尿液浊度', englishCode: 'TURB', resultValue: '微混', unit: '', refRange: '清亮', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '目测法', groupKey: 'urine-chem' },
        // 尿沉渣 (右表)
        { testItemName: '红细胞(URBC)', englishCode: 'URBC', resultValue: '28', unit: '个/μL', refRange: '0-17', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' },
        { testItemName: '白细胞(UWBC)', englishCode: 'UWBC', resultValue: '65', unit: '个/μL', refRange: '0-28', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' },
        { testItemName: '上皮细胞(EC)', englishCode: 'EC', resultValue: '12', unit: '个/μL', refRange: '0-8', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' },
        { testItemName: '管型(CAST)', englishCode: 'CAST', resultValue: '0.8', unit: '个/μL', refRange: '0-2', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' },
        { testItemName: '结晶(CRYSTAL)', englishCode: 'CRY', resultValue: '草酸钙++', unit: '', refRange: '无/少量', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' },
        { testItemName: '细菌(BACT)', englishCode: 'BACT', resultValue: '1280', unit: '个/μL', refRange: '0-3000', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' },
        { testItemName: '病理管型(P.CAST)', englishCode: 'P.CAST', resultValue: '0.1', unit: '个/μL', refRange: '<0.5', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '流式沉渣法', groupKey: 'urine-sediment' }
      ],
      remark: '本尿液报告使用AX-4030+UF-5000全自动尿液分析流水线,仅对该送检标本负责。',
      warmTips: ['尿蛋白/葡萄糖阳性建议复查晨尿并做24小时尿蛋白定量/血糖评估。'],
      organisms: []
    })
  },
  {
    key: 'tumor',
    label: '肿瘤标志物筛查',
    desc: 'AFP/CEA/CA125/CA199等全套,化学发光法',
    category: '检验报告',
    build: () => ({ ...basePatient(),
      clinicalDiag: '体检筛查；患者自述家族肿瘤史',
      mutuallyRecognized: 1,
      results: [
        { testItemName: '甲胎蛋白(AFP)', englishCode: 'AFP', resultValue: '3.2', unit: 'ng/mL', refRange: '0-8.78', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '癌胚抗原(CEA)', englishCode: 'CEA', resultValue: '18.65', unit: 'ng/mL', refRange: '0-5', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '糖类抗原125(CA125)', englishCode: 'CA125', resultValue: '12.5', unit: 'U/mL', refRange: '0-35', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '糖类抗原19-9(CA19-9)', englishCode: 'CA199', resultValue: '258.3', unit: 'U/mL', refRange: '0-27', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '糖类抗原15-3(CA15-3)', englishCode: 'CA153', resultValue: '10.4', unit: 'U/mL', refRange: '0-25', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '糖类抗原72-4(CA72-4)', englishCode: 'CA724', resultValue: '36.8', unit: 'U/mL', refRange: '0-6.9', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '总前列腺特异性抗原(T-PSA)', englishCode: 'T-PSA', resultValue: '2.3', unit: 'ng/mL', refRange: '0-4', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '游离前列腺特异性抗原(F-PSA)', englishCode: 'F-PSA', resultValue: '0.5', unit: 'ng/mL', refRange: '0-0.93', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '细胞角蛋白19片段(CYFRA21-1)', englishCode: 'CYFRA21-1', resultValue: '2.1', unit: 'ng/mL', refRange: '0-3.24', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '神经原特异性烯醇化酶(NSE)', englishCode: 'NSE', resultValue: '12.0', unit: 'ng/mL', refRange: '0-16.3', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' },
        { testItemName: '鳞状细胞癌抗原(SCCA)', englishCode: 'SCCA', resultValue: '0.6', unit: 'ng/mL', refRange: '0-1.5', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '化学发光法', instrumentName: 'MAGLUMI X8' }
      ],
      remark: '本结果仅对送检标本负责,肿瘤标志物升高建议结合影像学及临床进一步评估。',
      warmTips: ['CEA/CA199/CA724联合升高建议完善胃肠镜+腹部增强CT排除消化道肿瘤。'],
      organisms: []
    })
  },
  {
    key: 'multi_ref',
    label: '历史对比(多参考)',
    desc: '含上次结果/上次日期/对比箭头(适用于复查患者)',
    category: '历史对比',
    build: () => ({ ...basePatient(),
      mutuallyRecognized: 1,
      results: [
        { testItemName: '空腹血糖(GLU)', englishCode: 'GLU', resultValue: '6.8', unit: 'mmol/L', refRange: '3.9-6.1', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '葡萄糖氧化酶法', lastResult: '7.2', lastDate: '2026-06-28', lastFlag: '↓' },
        { testItemName: '糖化血红蛋白(HbA1c)', englishCode: 'HbA1c', resultValue: '7.5', unit: '%', refRange: '4.0-6.0', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'HPLC法', lastResult: '8.1', lastDate: '2026-06-28', lastFlag: '↓' },
        { testItemName: '总胆固醇(TC)', englishCode: 'TC', resultValue: '6.52', unit: 'mmol/L', refRange: '<5.18', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'COD-PAP法', lastResult: '6.48', lastDate: '2026-06-28', lastFlag: '→' },
        { testItemName: '甘油三酯(TG)', englishCode: 'TG', resultValue: '2.18', unit: 'mmol/L', refRange: '<1.70', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: 'GPO-PAP法', lastResult: '2.46', lastDate: '2026-06-28', lastFlag: '↓' },
        { testItemName: '低密度脂蛋白胆固醇(LDL-C)', englishCode: 'LDL-C', resultValue: '4.12', unit: 'mmol/L', refRange: '<3.40', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '清除法', lastResult: '4.08', lastDate: '2026-06-28', lastFlag: '→' },
        { testItemName: '高密度脂蛋白胆固醇(HDL-C)', englishCode: 'HDL-C', resultValue: '1.02', unit: 'mmol/L', refRange: '>1.04', abnormalFlag: 'L', isCritical: 0, mutuallyRecognized: 1, testMethod: '选择抑制法', lastResult: '1.00', lastDate: '2026-06-28', lastFlag: '→' },
        { testItemName: '肌酐(CREA)', englishCode: 'CREA', resultValue: '85', unit: 'μmol/L', refRange: '57-97', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '肌氨酸氧化酶法', lastResult: '83', lastDate: '2026-06-28', lastFlag: '→' },
        { testItemName: '尿素(UREA)', englishCode: 'UREA', resultValue: '6.2', unit: 'mmol/L', refRange: '3.1-8.0', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: '尿素酶法', lastResult: '6.0', lastDate: '2026-06-28', lastFlag: '→' },
        { testItemName: '尿酸(UA)', englishCode: 'UA', resultValue: '458', unit: 'μmol/L', refRange: '208-428', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '尿酸酶法', lastResult: '472', lastDate: '2026-06-28', lastFlag: '↓' },
        { testItemName: '丙氨酸氨基转移酶(ALT)', englishCode: 'ALT', resultValue: '44', unit: 'U/L', refRange: '9-50', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: 'IFCC速率法', lastResult: '40', lastDate: '2026-06-28', lastFlag: '→' },
        { testItemName: '天门冬氨酸氨基转移酶(AST)', englishCode: 'AST', resultValue: '38', unit: 'U/L', refRange: '15-40', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 1, testMethod: 'IFCC速率法', lastResult: '37', lastDate: '2026-06-28', lastFlag: '→' }
      ],
      remark: '本报告结果仅对送检标本负责,历史对比仅供临床参考,请结合患者症状及影像学评估。',
      warmTips: ['血糖/血脂较上次复查有所改善,建议继续保持饮食控制+运动治疗,3个月后复查。'],
      organisms: []
    })
  },
  {
    key: 'micro',
    label: '微生物鉴定+药敏',
    desc: '细菌鉴定+CLSI药敏结果判读(S/I/R着色)',
    category: '微生物报告',
    build: () => ({ ...basePatient(),
      sampleBarcode: 'W260731000033', specimenType: '痰', sampleType: '痰液标本', containerType: '痰培养杯',
      department: '呼吸内科', bedNo: '呼吸ICU-05床', clinicalDiag: '社区获得性肺炎;发热待查',
      mutuallyRecognized: 0,
      results: [],
      remark: '培养结果及药敏结果采用CLSI M100 34th版标准判读;结果仅供临床参考。',
      warmTips: ['检出CRE(碳青霉烯耐药肠杆菌)建议启动接触隔离,必要时多学科会诊;痰培养结果需结合痰涂片质量综合评估。'],
      organisms: [
        {
          organismName: '肺炎克雷伯菌(KP)', organismCode: 'KPN', quantity: '大量+++', colonsMethod: '血平板/麦康凯 需氧培养 35℃ 24h',
          sensitivities: [
            { antibioticName: '氨苄西林', micValue: '≥32', result: 'R', breakpoint: '≤8' },
            { antibioticName: '头孢唑啉', micValue: '≥64', result: 'R', breakpoint: '≤2' },
            { antibioticName: '头孢曲松', micValue: '≥64', result: 'R', breakpoint: '≤1' },
            { antibioticName: '头孢他啶', micValue: '≥64', result: 'R', breakpoint: '≤4' },
            { antibioticName: '美罗培南', micValue: '≥16', result: 'R', breakpoint: '≤1' },
            { antibioticName: '亚胺培南', micValue: '≥8', result: 'R', breakpoint: '≤1' },
            { antibioticName: '阿米卡星', micValue: '≤2', result: 'S', breakpoint: '≤16' },
            { antibioticName: '环丙沙星', micValue: '≥4', result: 'R', breakpoint: '≤1' },
            { antibioticName: '左氧氟沙星', micValue: '≥8', result: 'R', breakpoint: '≤2' },
            { antibioticName: '复方磺胺', micValue: '≥320', result: 'R', breakpoint: '≤2/38' },
            { antibioticName: '多粘菌素B', micValue: '≤1', result: 'S', breakpoint: '≤2' },
            { antibioticName: '替加环素', micValue: '≤0.5', result: 'S', breakpoint: '≤2' }
          ]
        },
        {
          organismName: '铜绿假单胞菌(PA)', organismCode: 'PAE', quantity: '中量++', colonsMethod: '血平板/麦康凯 需氧培养 35℃ 24h',
          sensitivities: [
            { antibioticName: '哌拉西林/他唑巴坦', micValue: '16/8', result: 'I', breakpoint: '≤16/4' },
            { antibioticName: '头孢他啶', micValue: '8', result: 'I', breakpoint: '≤4' },
            { antibioticName: '头孢吡肟', micValue: '16', result: 'R', breakpoint: '≤8' },
            { antibioticName: '亚胺培南', micValue: '4', result: 'I', breakpoint: '≤2' },
            { antibioticName: '美罗培南', micValue: '4', result: 'I', breakpoint: '≤2' },
            { antibioticName: '阿米卡星', micValue: '8', result: 'S', breakpoint: '≤16' },
            { antibioticName: '环丙沙星', micValue: '1', result: 'S', breakpoint: '≤1' },
            { antibioticName: '妥布霉素', micValue: '4', result: 'I', breakpoint: '≤2' }
          ]
        },
        {
          organismName: '金黄色葡萄球菌(SA)', organismCode: 'SA-MRSA', quantity: '少量+', colonsMethod: '血平板 需氧培养 35℃ 24h',
          sensitivities: [
            { antibioticName: '青霉素G', micValue: '≥0.5', result: 'R', breakpoint: '≤0.125' },
            { antibioticName: '苯唑西林(头孢西丁)', micValue: '≥8', result: 'R', breakpoint: '≤4' },
            { antibioticName: '红霉素', micValue: '≥8', result: 'R', breakpoint: '≤0.5' },
            { antibioticName: '克林霉素', micValue: '≥4', result: 'R', breakpoint: '≤0.5' },
            { antibioticName: '左氧氟沙星', micValue: '≥8', result: 'R', breakpoint: '≤2' },
            { antibioticName: '万古霉素', micValue: '≤1', result: 'S', breakpoint: '≤2' },
            { antibioticName: '利奈唑胺', micValue: '≤2', result: 'S', breakpoint: '≤4' },
            { antibioticName: '达托霉素', micValue: '≤0.5', result: 'S', breakpoint: '≤1' },
            { antibioticName: '复方磺胺', micValue: '≤0.5/9.5', result: 'S', breakpoint: '≤2/38' }
          ]
        }
      ]
    })
  },
  {
    key: 'stat_daily',
    label: '统计报表-日工作量',
    desc: '自定义延伸场景:统计类报表非检验结果类(可自行加列查询)',
    category: '统计报表',
    build: () => ({ ...basePatient(),
      clinicalDiag: '—',
      // 自定义报表: 各科室当日检验工作量(放入 results 结构复用 results-table 渲染)
      results: [
        { testItemName: '心血管内科', englishCode: 'Cardiology', resultValue: '86', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '生化20+免疫15+临检35+微生物16' },
        { testItemName: '呼吸内科', englishCode: 'Respiratory', resultValue: '124', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '临检42+生化38+微生物28+免疫16' },
        { testItemName: '消化内科', englishCode: 'GI Medicine', resultValue: '68', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '生化32+免疫14+临检16+其他6' },
        { testItemName: '肿瘤科', englishCode: 'Oncology', resultValue: '92', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '肿瘤标志物45+生化24+临检18+其他5' },
        { testItemName: '普通外科', englishCode: 'General Surg', resultValue: '75', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '术前感染筛查30+生化22+凝血16+其他7' },
        { testItemName: '骨科', englishCode: 'Orthopedic', resultValue: '48', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '生化18+术前筛查15+凝血9+其他6' },
        { testItemName: '妇产科', englishCode: 'Ob/Gyn', resultValue: '62', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '产前筛查24+临检20+生化12+其他6' },
        { testItemName: '儿科', englishCode: 'Pediatrics', resultValue: '94', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '临检52+生化22+免疫14+其他6' },
        { testItemName: '重症医学科(ICU)', englishCode: 'ICU', resultValue: '56', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '血气22+生化15+微生物12+临检7' },
        { testItemName: '急诊科', englishCode: 'Emergency', resultValue: '110', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '急查生化35+临检40+血气20+凝血15' },
        { testItemName: '健康管理中心(体检)', englishCode: 'Physical Exam', resultValue: '318', unit: '份', refRange: '—', abnormalFlag: '', isCritical: 0, mutuallyRecognized: 0, testMethod: '体检生化90+肿瘤标志物50+临检78+免疫30+其他70' }
      ],
      remark: '本页为自定义工作量统计报表,数据可通过SQL/JPA查询实时生成;当日累计检验 1133 份,已审核通过 98.6%。',
      warmTips: ['可在 DATA_SCENES 里继续扩展:危急值通报统计、阳性率统计、科室收费明细、各仪器TAT周转时长统计等场景。'],
      organisms: []
    })
  },
  {
    key: 'stat_critical',
    label: '统计报表-危急值通报',
    desc: '自定义延伸场景:当月危急值通报明细(含通报时长/处理人)',
    category: '统计报表',
    build: () => ({ ...basePatient(),
      clinicalDiag: '—',
      results: [
        // lastResult / lastDate 字段在此复用:通报时间(通报人);lastFlag复用:TAT处理耗时(min)
        { testItemName: '空腹血糖(GLU)', englishCode: 'GLU', resultValue: '2.1', unit: 'mmol/L', refRange: '危急低:<2.5', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '葡萄糖氧化酶法', lastResult: '急诊ICU(刘敏)', lastDate: '2026-08-01 03:45', lastFlag: '8min' },
        { testItemName: '空腹血糖(GLU)', englishCode: 'GLU', resultValue: '22.8', unit: 'mmol/L', refRange: '危急高:>22.2', abnormalFlag: 'H', isCritical: 1, mutuallyRecognized: 1, testMethod: '葡萄糖氧化酶法', lastResult: '内分泌(王静)', lastDate: '2026-08-01 09:12', lastFlag: '12min' },
        { testItemName: '血钾(K+)', englishCode: 'K+', resultValue: '2.2', unit: 'mmol/L', refRange: '危急低:<2.5', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: 'ISE间接离子电极法', lastResult: '心内CCU(陈芳)', lastDate: '2026-08-02 14:28', lastFlag: '6min' },
        { testItemName: '血钾(K+)', englishCode: 'K+', resultValue: '6.8', unit: 'mmol/L', refRange: '危急高:>6.5', abnormalFlag: 'H', isCritical: 1, mutuallyRecognized: 1, testMethod: 'ISE间接离子电极法', lastResult: '肾内透析(孙涛)', lastDate: '2026-08-03 11:05', lastFlag: '10min' },
        { testItemName: '血钙(T-Ca)', englishCode: 'T-Ca', resultValue: '1.42', unit: 'mmol/L', refRange: '危急低:<1.5', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '偶氮胂III比色法', lastResult: '甲乳外科(刘洋)', lastDate: '2026-08-04 16:42', lastFlag: '9min' },
        { testItemName: '肌钙蛋白I(cTnI)', englishCode: 'cTnI', resultValue: '28.5', unit: 'ng/mL', refRange: '危急高:>5', abnormalFlag: 'H', isCritical: 1, mutuallyRecognized: 1, testMethod: '化学发光法', lastResult: '胸痛中心(周磊)', lastDate: '2026-08-05 02:17', lastFlag: '5min' },
        { testItemName: '白细胞计数(WBC)', englishCode: 'WBC', resultValue: '0.6', unit: '10^9/L', refRange: '危急低:<1.0', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '激光流式法', lastResult: '化疗科(赵丽)', lastDate: '2026-08-06 10:35', lastFlag: '11min' },
        { testItemName: '血小板计数(PLT)', englishCode: 'PLT', resultValue: '12', unit: '10^9/L', refRange: '危急低:<30', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '流式荧光法', lastResult: '血液科(黄磊)', lastDate: '2026-08-07 08:48', lastFlag: '7min' },
        { testItemName: '凝血酶原时间(PT)', englishCode: 'PT', resultValue: '32.6', unit: 's', refRange: '危急高:>30', abnormalFlag: 'H', isCritical: 1, mutuallyRecognized: 1, testMethod: '凝固法(ACL-TOP750)', lastResult: '肝外手术(冯伟)', lastDate: '2026-08-08 15:22', lastFlag: '15min' },
        { testItemName: '活化部分凝血活酶时间(APTT)', englishCode: 'APTT', resultValue: '98.4', unit: 's', refRange: '危急高:>100', abnormalFlag: 'H', isCritical: 0, mutuallyRecognized: 1, testMethod: '凝固法(ACL-TOP750)', lastResult: '产科重症(吴敏)', lastDate: '2026-08-09 17:04', lastFlag: '13min' },
        { testItemName: '动脉血pH', englishCode: 'pH', resultValue: '7.12', unit: '', refRange: '危急低:<7.2', abnormalFlag: 'L', isCritical: 1, mutuallyRecognized: 1, testMethod: '血气分析ABL90', lastResult: '急诊抢救(郑鹏)', lastDate: '2026-08-10 19:51', lastFlag: '4min' },
        { testItemName: '胆红素(新生儿TBil)', englishCode: 'TBil-NB', resultValue: '458', unit: 'μmol/L', refRange: '危急高:>425', abnormalFlag: 'H', isCritical: 1, mutuallyRecognized: 1, testMethod: '钒酸盐氧化法', lastResult: 'NICU(林晓)', lastDate: '2026-08-11 20:38', lastFlag: '6min' }
      ],
      remark: '自定义延伸场景示例:当月累计危急值 12 项,全部通报成功,TAT平均处理时长 9.0 分钟,符合≤15min要求。',
      warmTips: ['已按国家卫健委《临床实验室危急值报告管理制度》登记;涉及抗菌药物临床应用、输血前感染筛查阳性等可继续扩展其他报表场景。'],
      organisms: []
    })
  }
]

const currentSceneKey = ref<string>('chem_blood')
const sceneCategories = computed(() => {
  const set = new Set<string>()
  DATA_SCENES.forEach(s => set.add(s.category))
  return Array.from(set)
})
const applyScene = (key: string) => {
  const sc = DATA_SCENES.find(s => s.key === key)
  if (!sc) return
  const built = sc.build()
  // 逐字段合并到 reactive sampleData
  const keys = new Set([...Object.keys(sampleData), ...Object.keys(built)])
  keys.forEach(k => { (sampleData as any)[k] = (built as any)[k] })
  // 同步切到匹配的内置模板,方便预览
  if (key === 'urinalysis') {
    loadBuiltInTemplate('urine')
  } else if (key === 'multi_ref') {
    loadBuiltInTemplate('multi-ref')
  } else if (key === 'micro') {
    loadBuiltInTemplate('micro')
  } else if (key.startsWith('stat_')) {
    loadBuiltInTemplate('b5-vert') // 统计类默认竖版
  } else {
    loadBuiltInTemplate('standard')
  }
  ElMessage.info(`已切换数据源: ${sc.label}`)
}

// ========== 元素预览组件 ==========
const ElementPreview = defineComponent({
  props: {
    el: { type: Object as () => ReportElement, required: true },
    sampleData: { type: Object, required: true }
  },
  setup(props) {
    return () => {
      const el = props.el
      const data = props.sampleData as any

      if (el.type === 'hospital-name' || el.type === 'report-title' || el.type === 'remark' || el.type === 'custom-text') {
        return h('div', { class: 'preview-text' }, el.text || '')
      }

      // 细粒度字段(每个字段独立元素)
      if (el.type === 'field') {
        const value = data[el.fieldKey!] || '-'
        return h('div', { class: 'preview-field' }, [
          h('span', { class: 'field-label' }, el.fieldLabel + ': '),
          h('span', { class: 'field-value' }, value)
        ])
      }

      if (el.type === 'barcode') {
        return h('div', { class: 'preview-barcode' }, [
          h('div', { class: 'barcode-bars' }, '||||| ||| ||||| | || ||||| ||||'),
          h('div', { class: 'barcode-text' }, data.sampleBarcode)
        ])
      }

      if (el.type === 'patient-info') {
        const fields = el.fields || []
        const labels: Record<string, string> = {
          patientName: '姓名', gender: '性别', age: '年龄', visitNo: '就诊号',
          department: '科室', bedNo: '床号', doctor: '申请医生', clinicalDiag: '临床诊断',
          idCard: '身份证', phone: '电话'
        }
        return h('div', { class: 'preview-info-grid' },
          fields.map((f: string) =>
            h('div', { class: 'info-item' }, [
              h('span', { class: 'info-label' }, labels[f] + ': '),
              h('span', { class: 'info-value' }, data[f] || '-')
            ])
          )
        )
      }

      if (el.type === 'sample-info') {
        const fields = el.fields || []
        const labels: Record<string, string> = {
          sampleBarcode: '条码', sampleType: '标本类型', containerType: '容器',
          collectTime: '采集时间', deliverTime: '送检时间', signOffTime: '签收时间',
          receiveTime: '核收时间', specimenNo: '标本号'
        }
        return h('div', { class: 'preview-info-grid' },
          fields.map((f: string) =>
            h('div', { class: 'info-item' }, [
              h('span', { class: 'info-label' }, labels[f] + ': '),
              h('span', { class: 'info-value' }, data[f] || '-')
            ])
          )
        )
      }

      if (el.type === 'results-table') {
        const cols = el.columns || []
        const colLabels: Record<string, string> = {
          idx: '序号', testItemName: '中文名称', englishCode: '英文缩写',
          resultValue: '结果', unit: '单位', refRange: '参考', testMethod: '检验方法',
          abnormalFlag: '异常', isCritical: '危急值', testTime: '检测时间', instrumentName: '仪器',
          lastResult: '上次结果', lastDate: '上次日期', lastFlag: '对比'
        }
        return h('table', { class: 'preview-table preview-table-std' }, [
          h('colgroup', cols.map((c: string) => {
            const widths: Record<string, string> = {
              idx: '5%', testItemName: '16%', englishCode: '11%', resultValue: '11%',
              unit: '7%', refRange: '13%', testMethod: '14%',
              lastResult: '9%', lastDate: '9%', lastFlag: '5%'
            }
            return h('col', widths[c] ? { style: { width: widths[c] } } : null)
          })),
          h('thead', [h('tr', cols.map((c: string) => h('th', colLabels[c] || c))) ]),
          h('tbody', data.results.map((r: any, rowIdx: number) =>
            h('tr', {
              class: {
                'critical-row': Number(r.isCritical) === 1,
                'high-row': r.abnormalFlag === 'H',
                'low-row': r.abnormalFlag === 'L'
              }
            }, cols.map((c: string) => {
              if (c === 'idx') {
                const star = Number(r.mutuallyRecognized) === 1
                  ? h('span', { class: 'hr-star' }, '*')
                  : null
                return h('td', { class: 'c-idx' }, [star, h('span', String(rowIdx + 1))])
              }
              let v: any = r[c]
              if (v === null || v === undefined || v === '') {
                if (c === 'englishCode') v = r.englishName || r.testItemCode || '-'
                else if (c === 'testMethod') v = r.method || r.instrumentName || '-'
                else if (c === 'resultValue') v = r.resultText || '-'
                else if (c === 'lastResult') v = r.lastResult ?? '—'
                else if (c === 'lastDate') v = r.lastDate ?? '—'
                else if (c === 'lastFlag') v = r.lastFlag ?? '—'
                else v = '-'
              }
              if (c === 'abnormalFlag') v = v === 'H' ? '↑' : (v === 'L' ? '↓' : (v || '-'))
              if (c === 'isCritical') v = Number(v) === 1 ? '⚠危急' : '-'
              const cls: Record<string, boolean> = {}
              if (c === 'resultValue') {
                if (Number(r.isCritical) === 1) cls['critical-cell'] = true
                else if (r.abnormalFlag === 'H') cls['high-cell'] = true
                else if (r.abnormalFlag === 'L') cls['low-cell'] = true
              }
              if (c === 'lastFlag') {
                if (v === '↑' || v === '上升' || String(v).includes('升')) cls['high-cell'] = true
                else if (v === '↓' || v === '下降' || String(v).includes('降')) cls['low-cell'] = true
                else cls['neutral-flag'] = true
              }
              return h('td', Object.keys(cls).length ? { class: cls } : null, String(v))
            }))
          ))
        ])
      }

      if (el.type === 'micro-organism') {
        const cols = el.columns || []
        const colLabels: Record<string, string> = {
          organismName: '细菌名称', organismCode: '细菌编码', quantity: '生长量', colonsMethod: '培养方法'
        }
        return h('div', [
          h('div', { class: 'preview-micro-title' }, '细菌鉴定'),
          h('table', { class: 'preview-table' }, [
            h('thead', [h('tr', cols.map((c: string) => h('th', colLabels[c]))) ]),
            h('tbody', (data.organisms || []).map((o: any) =>
              h('tr', cols.map((c: string) => h('td', o[c] || '-')))
            ))
          ])
        ])
      }

      if (el.type === 'micro-sensitivity') {
        const cols = el.columns || []
        const colLabels: Record<string, string> = {
          antibioticName: '抗生素', micValue: 'MIC', result: '结果', breakpoint: '判读界值'
        }
        const orgs = (data.organisms || []) as any[]
        return h('div', [
          orgs.length === 0
            ? h('div', { class: 'preview-micro-title' }, '暂无药敏结果')
            : orgs.map((o: any) => [
                h('div', { class: 'preview-micro-title' }, o.organismName + ' - 药敏结果'),
                h('table', { class: 'preview-table micro-sens-table' }, [
                  h('thead', [h('tr', cols.map((c: string) => h('th', colLabels[c]))) ]),
                  h('tbody', (o.sensitivities || []).map((s: any) =>
                    h('tr', cols.map((c: string) => {
                      let v = s[c] || '-'
                      const cls: Record<string, boolean> = {}
                      if (c === 'result') {
                        const raw = s.result
                        if (raw === 'S') { cls['s-sensitive'] = true; v = '敏感(S)' }
                        else if (raw === 'I') { cls['i-intermediate'] = true; v = '中介(I)' }
                        else if (raw === 'R') { cls['r-resistant'] = true; v = '耐药(R)' }
                        else v = ({ S: '敏感(S)', I: '中介(I)', R: '耐药(R)' } as Record<string,string>)[v] || v
                      }
                      return h('td', Object.keys(cls).length ? { class: cls } : null, String(v))
                    }))
                  ))
                ])
              ])
        ])
      }

      if (el.type === 'doctor-signature') {
        const fields = el.fields || []
        const labels: Record<string, string> = {
          checker: '检验者', auditor: '审核者', reportDoctor: '报告医生',
          collector: '采集者', receiver: '核收者'
        }
        return h('div', { class: 'preview-signature' },
          fields.map((f: string) =>
            h('div', { class: 'sig-item' }, [
              h('span', { class: 'sig-label' }, labels[f] + ': '),
              h('span', { class: 'sig-value' }, data[f] || '________')
            ])
          )
        )
      }

      if (el.type === 'report-time') {
        return h('div', { class: 'preview-time' }, '报告时间: ' + (data.reportTime || '-'))
      }

      return h('div', '(元素)')
    }
  }
})

// ========== 初始化 ==========
onMounted(async () => {
  try {
    const t = await loadTemplate()
    Object.assign(template, JSON.parse(JSON.stringify(t)))
  } catch {
    // 使用默认模板
  }
  await loadTemplateList()
  await loadInstruments()
})
</script>

<style scoped lang="scss">
.report-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  flex-shrink: 0;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .update-time {
    font-size: 12px;
    color: #909399;
  }
}

.designer-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.designer-left {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e6e6e6;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.designer-right {
  width: 320px;
  background: #fff;
  border-left: 1px solid #e6e6e6;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding: 4px 0;
  margin-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  .panel-sub {
    font-size: 11px;
    font-weight: 400;
    color: #909399;
    margin-left: 6px;
  }
}
.scene-category { margin-bottom: 8px; }
.scene-cat-label {
  font-size: 11px;
  color: #909399;
  padding: 2px 0;
  margin-bottom: 4px;
}
.scene-list { display: flex; flex-direction: column; gap: 4px; }
.scene-item {
  padding: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 2px;
  &:hover {
    background: #ecf5ff;
    border-color: #1a73e8;
    .scene-name { color: #1a73e8; }
  }
  &.active {
    background: linear-gradient(90deg, #e8f0fe 0%, #ecf5ff 100%);
    border-color: #1a73e8;
    box-shadow: inset 2px 0 0 #1a73e8;
    .scene-name { color: #1a73e8; font-weight: 600; }
  }
  .scene-name { font-size: 12px; color: #303133; }
  .scene-desc {
    font-size: 10.5px;
    color: #909399;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.element-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.element-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px 4px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;

  &:hover {
    background: #ecf5ff;
    border-color: #1a73e8;
    color: #1a73e8;
  }
  .el-icon {
    font-size: 18px;
    margin-bottom: 4px;
  }
}

.field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.field-item {
  padding: 4px 8px;
  border: 1px solid #e4e7ed;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;

  &:hover {
    background: #ecf5ff;
    border-color: #1a73e8;
    color: #1a73e8;
  }
}

.canvas-settings {
  :deep(.el-form-item) {
    margin-bottom: 8px;
  }
}

.builtin-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.builtin-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
  .builtin-name {
    font-size: 13px;
    color: #303133;
    font-weight: 500;
  }
  .builtin-desc {
    font-size: 11px;
    color: #909399;
  }
  &:hover {
    background: #ecf5ff;
    border-color: #1a73e8;
    .builtin-name { color: #1a73e8; }
  }
}

.layer-list {
  max-height: 300px;
  overflow-y: auto;
}

.layer-item {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  gap: 6px;

  &:hover {
    background: #f5f7fa;
  }
  &.active {
    background: #ecf5ff;
    color: #1a73e8;
  }
  .layer-index {
    width: 18px;
    height: 18px;
    background: #909399;
    color: #fff;
    border-radius: 50%;
    text-align: center;
    line-height: 18px;
    font-size: 11px;
    flex-shrink: 0;
  }
  .layer-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .layer-toggle, .layer-delete {
    cursor: pointer;
    color: #909399;
    &:hover { color: #f56c6c; }
  }
}

.designer-canvas-wrapper {
  flex: 1;
  overflow: auto;
  background: #e8eaed;
  padding: 20px;
}

.canvas-scroll {
  display: flex;
  justify-content: center;
  min-height: 100%;
}

.designer-canvas {
  position: relative;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  flex-shrink: 0;
}

.report-element {
  position: absolute;
  cursor: move;
  box-sizing: border-box;

  &.selected {
    .element-border {
      position: absolute;
      inset: 0;
      border: 1px dashed #1a73e8;
      pointer-events: none;
    }
    .resize-handle {
      position: absolute;
      width: 8px;
      height: 8px;
      background: #1a73e8;
      border: 1px solid #fff;
      border-radius: 50%;
      z-index: 10;
    }
    .handle-tl { top: -4px; left: -4px; cursor: nwse-resize; }
    .handle-tr { top: -4px; right: -4px; cursor: nesw-resize; }
    .handle-bl { bottom: -4px; left: -4px; cursor: nesw-resize; }
    .handle-br { bottom: -4px; right: -4px; cursor: nwse-resize; }
    .handle-t { top: -4px; left: 50%; transform: translateX(-50%); cursor: ns-resize; }
    .handle-b { bottom: -4px; left: 50%; transform: translateX(-50%); cursor: ns-resize; }
    .handle-l { left: -4px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }
    .handle-r { right: -4px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }
  }
}

.element-content {
  width: 100%;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
  padding: 4px;
  pointer-events: none;
}

.preview-text {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  word-break: break-all;
}

.preview-field {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  .field-label {
    color: #606266;
    flex-shrink: 0;
  }
  .field-value {
    font-weight: 500;
  }
}

.preview-barcode {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  .barcode-bars {
    font-family: 'Courier New', monospace;
    letter-spacing: 2px;
    font-size: 20px;
    line-height: 1;
  }
  .barcode-text {
    font-size: 12px;
    margin-top: 4px;
  }
}

.preview-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
  width: 100%;
  .info-item {
    display: flex;
    .info-label {
      color: #606266;
      flex-shrink: 0;
    }
    .info-value {
      font-weight: 500;
    }
  }
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: inherit;
  th, td {
    border: 1px solid #000;
    padding: 4px 6px;
    text-align: left;
    vertical-align: middle;
  }
  th {
    background: #f5f7fa;
    font-weight: 700;
    text-align: center;
  }
  .critical-cell {
    color: #d9001b;
    font-weight: 800;
  }
  .high-cell {
    color: #d9001b;
    font-weight: 700;
  }
  .low-cell {
    color: #2f55d4;
    font-weight: 700;
  }
  .critical-row td { color: #d9001b; font-weight: 800; }
  .high-row td:nth-child(4) { color: #d9001b; font-weight: 700; }
  .low-row td:nth-child(4) { color: #2f55d4; font-weight: 700; }
}
.preview-table-std {
  .c-idx { text-align: center; white-space: nowrap; }
  .hr-star { color: #d9001b; font-weight: 700; margin-right: 2px; }
  .neutral-flag { color: #606266; font-weight: 600; text-align: center; }
}
.micro-sens-table {
  .s-sensitive { color: #2f9e44; font-weight: 700; background: #ebfbee; }
  .i-intermediate { color: #e67700; font-weight: 700; background: #fff9db; }
  .r-resistant  { color: #d9001b; font-weight: 800; background: #fff5f5; }
}

/* HR互认标识 */
.hr-badge {
  display: inline-block;
  border: 1.5px solid #000;
  padding: 3px 10px;
  font-weight: 700;
  font-size: 12px;
  text-align: center;
}

/* 医院信息行/患者信息网格样式 */
.preview-hospital-line {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-checkbox {
  width: 100%;
  margin: 0 0 4px 0 !important;
}

.preview-micro-title {
  font-weight: 600;
  margin: 4px 0;
}

.preview-signature {
  display: flex;
  justify-content: space-around;
  align-items: center;
  height: 100%;
  .sig-item {
    display: flex;
    .sig-label {
      color: #606266;
    }
    .sig-value {
      font-weight: 500;
      border-bottom: 1px solid #303133;
      padding: 0 8px;
    }
  }
}

.preview-time {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.empty-tip {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  padding: 12px 0;
}

.prop-form {
  :deep(.el-form-item) {
    margin-bottom: 10px;
  }
}

.field-checkbox {
  width: 100%;
  margin: 0 0 4px 0 !important;
}

.preview-dialog {
  :deep(.el-dialog__body) {
    max-height: 70vh;
    overflow-y: auto;
  }
}

.preview-wrapper {
  display: flex;
  justify-content: center;
  background: #f5f7fa;
  padding: 16px;
}

.report-renderer {
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}

@media print {
  body * { visibility: hidden; }
  .preview-wrapper, .preview-wrapper * { visibility: visible; }
  .preview-wrapper {
    position: absolute;
    left: 0;
    top: 0;
    background: #fff;
    padding: 0;
  }
}
</style>
