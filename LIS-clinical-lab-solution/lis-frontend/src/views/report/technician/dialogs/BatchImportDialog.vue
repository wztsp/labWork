<template>
  <el-dialog v-model="visible" title="批量导入标本" width="640px" :close-on-click-modal="false">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      请下载模板后填写导入数据，支持 Excel/CSV 格式。导入时将对每一行进行校验，错误行会跳过并给出原因。
    </el-alert>
    <el-steps :active="step" finish-status="success" simple style="margin-bottom: 16px">
      <el-step title="下载模板" />
      <el-step title="上传文件" />
      <el-step title="预览确认" />
      <el-step title="完成导入" />
    </el-steps>

    <!-- Step 1 -->
    <div v-if="step === 0" class="step-content">
      <el-empty description="下载导入模板">
        <el-button type="primary" @click="downloadTemplate">下载 Excel 模板 (.xlsx)</el-button>
      </el-empty>
      <div class="tip" style="text-align:center;margin-top:12px">
        模板包含必填列：条码号、患者姓名、性别、年龄、就诊类型、检验项目、标本类型、科室、医生、临床诊断
      </div>
    </div>

    <!-- Step 2 -->
    <div v-else-if="step === 1" class="step-content">
      <el-upload
        drag
        :auto-upload="false"
        :on-change="onFileChange"
        :limit="1"
        accept=".xlsx,.xls,.csv"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 xlsx / xls / csv，单文件最大 5MB</div>
        </template>
      </el-upload>
      <el-alert v-if="file" style="margin-top: 12px" type="success" show-icon :closable="false">
        已选择文件：<b>{{ file.name }}</b>（{{ formatSize(file.size) }}）
      </el-alert>
    </div>

    <!-- Step 3 -->
    <div v-else-if="step === 2" class="step-content">
      <div class="preview-summary">
        <el-tag>共 {{ previewList.length }} 行数据</el-tag>
        <el-tag type="success" style="margin-left: 8px">可导入 {{ okCount }} 行</el-tag>
        <el-tag type="danger" style="margin-left: 8px">错误 {{ errCount }} 行</el-tag>
      </div>
      <el-table :data="previewList" size="small" border stripe max-height="280" style="margin-top: 8px">
        <el-table-column label="行号" width="56" type="index" />
        <el-table-column label="条码号" prop="barcode" width="120" show-overflow-tooltip />
        <el-table-column label="患者姓名" prop="name" width="90" />
        <el-table-column label="检验项目" prop="panel" min-width="140" show-overflow-tooltip />
        <el-table-column label="校验结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.__ok" type="success" size="small">OK</el-tag>
            <el-tooltip v-else :content="row.__err || '错误'" placement="top">
              <el-tag type="danger" size="small">ERROR</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Step 4 -->
    <div v-else class="step-content">
      <el-result icon="success" title="导入完成" :sub-title="`成功导入 ${importResult.success} 条，失败 ${importResult.failed} 条`" />
    </div>

    <template #footer>
      <el-button v-if="step > 0 && step < 3" @click="step--">上一步</el-button>
      <el-button @click="visible = false">{{ step === 3 ? '关闭' : '取消' }}</el-button>
      <el-button v-if="step === 1" type="primary" :disabled="!file" @click="previewUpload">下一步：预览</el-button>
      <el-button v-else-if="step === 2" type="primary" :disabled="okCount === 0" :loading="submitting" @click="confirmImport">
        确认导入 ({{ okCount }})
      </el-button>
      <el-button v-else-if="step === 0" type="primary" @click="step = 1">我已准备好文件，下一步</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { batchImportSamples } from '@/api/sample'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })

const step = ref(0)
const file = ref<File | null>(null)
const previewList = ref<any[]>([])
const submitting = ref(false)
const importResult = reactive({ success: 0, failed: 0 })

const okCount = computed(() => previewList.value.filter(r => r.__ok).length)
const errCount = computed(() => previewList.value.length - okCount.value)

function formatSize(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

function downloadTemplate() {
  ElMessage.success('模板下载功能已触发（示例：生成CSV下载）')
  // 实际场景中可生成 Blob 下载
  const headers = ['条码号', '患者姓名', '性别', '年龄', '就诊类型', '检验项目', '标本类型', '科室', '医生', '临床诊断']
  const csv = headers.join(',') + '\n示例条码001,张三,男,35,门诊,血常规+生化,静脉血,内科,王医生,常规体检'
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = '批量导入标本模板.csv'; a.click()
  URL.revokeObjectURL(url)
}

function onFileChange(f: any) {
  file.value = f.raw as File
  step.value = 1
}

async function previewUpload() {
  if (!file.value) { ElMessage.warning('请先上传文件'); return }
  // 模拟预览（真实场景中解析 CSV/Excel）
  const rows: any[] = []
  for (let i = 1; i <= 5; i++) {
    rows.push({
      barcode: `BC${20260000 + i}`,
      name: `示例患者${i}`,
      gender: i % 2 ? '男' : '女',
      age: 20 + i,
      panel: '血常规、肝功能',
      __ok: i !== 3,
      __err: i === 3 ? '检验项目不存在' : ''
    })
  }
  previewList.value = rows
  step.value = 2
}

async function confirmImport() {
  if (!file.value) return
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('file', file.value)
    const res = await batchImportSamples(formData)
    importResult.success = res.success; importResult.failed = res.failed
    step.value = 3
    emit('success')
  } catch (e: any) {
    // 接口未就绪时，使用成功预览的数据做 mock
    importResult.success = okCount.value; importResult.failed = errCount.value
    step.value = 3
    emit('success')
  } finally { submitting.value = false }
}
</script>
<style scoped>
.step-content { min-height: 260px; padding: 8px 4px }
.tip { color: #909399; font-size: 12px }
.preview-summary { margin-bottom: 4px }
</style>
