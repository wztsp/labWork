<template>
  <div class="batch-import-page">
    <el-card shadow="never" class="import-card">
      <template #header>
        <div class="card-header">
          <span>批量导入HIS检验申请</span>
          <el-button type="primary" link size="small" @click="downloadTemplate">下载CSV模板</el-button>
        </div>
      </template>

      <el-radio-group v-model="inputMode" class="mode-switch">
        <el-radio-button value="text">文本输入</el-radio-button>
        <el-radio-button value="csv">CSV上传</el-radio-button>
      </el-radio-group>

      <!-- 文本输入模式 -->
      <div v-if="inputMode === 'text'" class="input-area">
        <p class="input-tip">
          每行一条申请，字段用逗号分隔，格式：<br />
          <code>申请单号,患者姓名,就诊类型,检验项目名称,申请科室,临床诊断,项目编码</code><br />
          （前4个字段必填，就诊类型支持：门诊/住院/急诊）
        </p>
        <el-input
          v-model="textInput"
          type="textarea"
          :rows="10"
          placeholder="示例:&#10;ORD20260801001,张三,门诊,血常规,内科,贫血待查,CBC&#10;ORD20260801002,李四,急诊,血糖,急诊科,,GLU"
        />
      </div>

      <!-- CSV上传模式 -->
      <div v-else class="input-area">
        <p class="input-tip">
          上传CSV文件，首行为表头，列名：
          <code>orderNo,patientName,orderType,testItemName,applyDept,clinicalDiag,testItemCode</code>
        </p>
        <el-upload
          :auto-upload="false"
          accept=".csv"
          :show-file-list="true"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :limit="1"
          drag
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽CSV文件到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .csv 文件，单次不超过 1000 条</div>
          </template>
        </el-upload>
      </div>

      <div class="action-bar">
        <el-button type="primary" :loading="importing" :disabled="!canImport" @click="handleImport">
          开始导入
        </el-button>
        <el-button @click="handleClear">清空</el-button>
        <span v-if="parsedRecords.length" class="record-count">
          已解析 {{ parsedRecords.length }} 条申请
        </span>
      </div>
    </el-card>

    <!-- 导入结果 -->
    <el-card v-if="resultList.length" shadow="never" class="result-card">
      <template #header>
        <div class="card-header">
          <span>导入结果</span>
          <span class="result-summary">
            共 {{ resultList.length }} 条 ·
            <span class="text-success">成功 {{ successCount }}</span> ·
            <span class="text-danger">失败 {{ failedCount }}</span>
          </span>
        </div>
      </template>
      <el-table :data="resultList" border stripe size="small" max-height="400">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="orderNo" label="申请单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者姓名" width="100" />
        <el-table-column prop="testItemName" label="检验项目" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.success" type="success" size="small">成功</el-tag>
            <el-tag v-else type="danger" size="small">失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="信息" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { receiveOrder } from '@/api/sample'

const inputMode = ref<'text' | 'csv'>('text')
const textInput = ref('')
const importing = ref(false)

interface ImportRecord {
  orderNo: string
  patientName: string
  orderType: string
  testItemName: string
  testItemCode: string
  applyDept: string
  clinicalDiag: string
}

interface ImportResult {
  orderNo: string
  patientName: string
  testItemName: string
  success: boolean
  message: string
}

const parsedRecords = ref<ImportRecord[]>([])
const resultList = ref<ImportResult[]>([])

const successCount = computed(() => resultList.value.filter(r => r.success).length)
const failedCount = computed(() => resultList.value.filter(r => !r.success).length)
const canImport = computed(() => {
  if (importing.value) return false
  return inputMode.value === 'text'
    ? textInput.value.trim().length > 0
    : parsedRecords.value.length > 0
})

/** 解析文本输入: 每行一条, 逗号分隔 */
function parseTextInput(text: string): ImportRecord[] {
  const records: ImportRecord[] = []
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
  for (const line of lines) {
    // 跳过表头行
    if (/^orderNo/i.test(line)) continue
    const parts = line.split(/[,，]/).map(p => p.trim())
    if (parts.length < 4) {
      ElMessage.warning(`行格式错误(至少需4个字段): ${line}`)
      return []
    }
    records.push({
      orderNo: parts[0],
      patientName: parts[1],
      orderType: parts[2] || '门诊',
      testItemName: parts[3],
      applyDept: parts[4] || '',
      clinicalDiag: parts[5] || '',
      testItemCode: parts[6] || ''
    })
  }
  return records
}

/** 简易CSV行解析 (支持双引号包裹的含逗号字段) */
function parseCsvLine(line: string): string[] {
  const result: string[] = []
  let cur = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++ }
        else inQuotes = false
      } else {
        cur += ch
      }
    } else {
      if (ch === '"') inQuotes = true
      else if (ch === ',') { result.push(cur); cur = '' }
      else cur += ch
    }
  }
  result.push(cur)
  return result
}

/** 解析CSV文件内容 */
function parseCsvText(content: string): ImportRecord[] {
  const lines = content.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
  if (lines.length === 0) return []
  // 首行为表头
  const header = parseCsvLine(lines[0]).map(h => h.trim().toLowerCase())
  const idx = (name: string) => header.indexOf(name)
  const iOrderNo = idx('orderno')
  const iPatient = idx('patientname')
  const iOrderType = idx('ordertype')
  const iItemName = idx('testitemname')
  const iItemCode = idx('testitemcode')
  const iApplyDept = idx('applydept')
  const iClinicalDiag = idx('clinicaldiag')
  const records: ImportRecord[] = []
  for (let i = 1; i < lines.length; i++) {
    const cols = parseCsvLine(lines[i])
    const orderNo = iOrderNo >= 0 ? (cols[iOrderNo] || '').trim() : ''
    const patientName = iPatient >= 0 ? (cols[iPatient] || '').trim() : ''
    if (!orderNo || !patientName) continue
    records.push({
      orderNo,
      patientName,
      orderType: iOrderType >= 0 ? (cols[iOrderType] || '门诊').trim() : '门诊',
      testItemName: iItemName >= 0 ? (cols[iItemName] || '').trim() : '',
      testItemCode: iItemCode >= 0 ? (cols[iItemCode] || '').trim() : '',
      applyDept: iApplyDept >= 0 ? (cols[iApplyDept] || '').trim() : '',
      clinicalDiag: iClinicalDiag >= 0 ? (cols[iClinicalDiag] || '').trim() : ''
    })
  }
  return records
}

function handleFileChange(file: any) {
  if (!file.raw) return
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = (e.target?.result as string) || ''
    parsedRecords.value = parseCsvText(content)
    if (parsedRecords.value.length === 0) {
      ElMessage.warning('未解析到有效记录，请检查CSV格式')
    } else {
      ElMessage.success(`已解析 ${parsedRecords.value.length} 条记录`)
    }
  }
  reader.readAsText(file.raw, 'UTF-8')
}

function handleFileRemove() {
  parsedRecords.value = []
}

/** 重新解析文本输入并预览 */
function refreshParsed() {
  if (inputMode.value === 'text') {
    parsedRecords.value = parseTextInput(textInput.value)
  }
}

async function handleImport() {
  refreshParsed()
  if (parsedRecords.value.length === 0) {
    ElMessage.warning('未解析到可导入的申请记录')
    return
  }
  if (parsedRecords.value.length > 1000) {
    ElMessage.warning('单次导入不超过 1000 条')
    return
  }
  importing.value = true
  resultList.value = []
  // 逐条调用后端 /api/sample/orders/receive 接口
  for (const rec of parsedRecords.value) {
    const result: ImportResult = {
      orderNo: rec.orderNo,
      patientName: rec.patientName,
      testItemName: rec.testItemName,
      success: false,
      message: ''
    }
    try {
      const payload = {
        orderNo: rec.orderNo,
        patientName: rec.patientName,
        orderType: rec.orderType,
        applyDept: rec.applyDept || undefined,
        clinicalDiag: rec.clinicalDiag || undefined,
        items: rec.testItemName
          ? [{ testItemName: rec.testItemName, testItemCode: rec.testItemCode || undefined }]
          : []
      }
      const res = await receiveOrder(payload)
      result.success = true
      result.message = Array.isArray(res) && res.length > 0
        ? `已接收, 申请号: ${res[0].orderNo}`
        : '已接收'
    } catch (e: any) {
      result.success = false
      result.message = (e && e.message) ? e.message : '接收失败'
    }
    resultList.value.push(result)
  }
  importing.value = false
  ElMessage.success(`导入完成: 成功 ${successCount.value} 条, 失败 ${failedCount.value} 条`)
}

function handleClear() {
  textInput.value = ''
  parsedRecords.value = []
  resultList.value = []
}

function downloadTemplate() {
  const header = 'orderNo,patientName,orderType,testItemName,applyDept,clinicalDiag,testItemCode'
  const sample = 'ORD20260801001,张三,门诊,血常规,内科,贫血待查,CBC\nORD20260801002,李四,急诊,血糖,急诊科,,GLU'
  const blob = new Blob(['\ufeff' + header + '\n' + sample], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = 'his-orders-template.csv'
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>

<style scoped lang="scss">
.batch-import-page {
  padding: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .mode-switch {
    margin-bottom: 16px;
  }

  .input-area {
    margin-bottom: 16px;

    .input-tip {
      font-size: 13px;
      color: #606266;
      line-height: 1.8;
      margin: 0 0 12px 0;

      code {
        background: #f5f7fa;
        padding: 2px 6px;
        border-radius: 3px;
        color: #e6a23c;
        font-size: 12px;
      }
    }
  }

  .action-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-top: 8px;
    border-top: 1px solid #ebeef5;

    .record-count {
      font-size: 13px;
      color: #1a73e8;
    }
  }

  .result-card {
    margin-top: 16px;

    .result-summary {
      font-size: 13px;
      color: #606266;

      .text-success { color: #67c23a; font-weight: 600; }
      .text-danger { color: #f56c6c; font-weight: 600; }
    }
  }
}
</style>
