<template>
  <div class="instrument-import-page">
    <el-card shadow="never">
      <template #header>
        <span>微生物仪器结果导入</span>
      </template>

      <!-- 步骤条 -->
      <el-steps :active="activeStep" finish-status="success" class="import-steps">
        <el-step title="填写标本信息" />
        <el-step title="粘贴/上传数据" />
        <el-step title="预览确认" />
        <el-step title="导入完成" />
      </el-steps>

      <!-- Step 1: 标本信息 -->
      <div v-if="activeStep === 0" class="step-content">
        <el-form ref="reportFormRef" :model="reportForm" :rules="reportRules" label-width="120px" style="max-width: 600px">
          <el-form-item label="标本ID" prop="sampleId">
            <el-input-number v-model="reportForm.sampleId" :min="1" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="患者姓名">
            <el-input v-model="reportForm.patientName" placeholder="请输入" />
          </el-form-item>
          <el-form-item label="标本类型" prop="specimenType">
            <el-select v-model="reportForm.specimenType" placeholder="请选择" filterable style="width: 100%">
              <el-option v-for="t in specimenTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="培养部位" prop="cultureSite">
            <el-input v-model="reportForm.cultureSite" placeholder="请输入" />
          </el-form-item>
        </el-form>
        <div class="step-actions">
          <el-button type="primary" @click="goToStep(1)">下一步</el-button>
        </div>
      </div>

      <!-- Step 2: 粘贴/上传数据 -->
      <div v-if="activeStep === 1" class="step-content">
        <el-tabs v-model="inputTab">
          <el-tab-pane label="粘贴CSV" name="csv">
            <div class="csv-help">
              <el-alert type="info" :closable="false" show-icon>
                <template #title>CSV格式说明</template>
                每行一条药敏结果，格式：细菌名称,细菌编码,抗生素名称,MIC值,结果(S/I/R)<br />
                示例：大肠埃希菌,ECO,氨苄西林,≤2,S
              </el-alert>
            </div>
            <el-input
              v-model="csvText"
              type="textarea"
              :rows="12"
              placeholder="细菌名称,细菌编码,抗生素名称,MIC值,结果(S/I/R)&#10;大肠埃希菌,ECO,氨苄西林,≤2,S&#10;大肠埃希菌,ECO,头孢曲松,≤1,S"
              style="font-family: monospace; font-size: 13px"
            />
          </el-tab-pane>
          <el-tab-pane label="上传JSON" name="json">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".json"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              drag
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽JSON文件到此处，或<em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">JSON格式：包含organisms和sensitivities的微生物报告数据</div>
              </template>
            </el-upload>
          </el-tab-pane>
        </el-tabs>
        <div class="step-actions">
          <el-button @click="goToStep(0)">上一步</el-button>
          <el-button type="primary" @click="parseData">解析数据</el-button>
        </div>
      </div>

      <!-- Step 3: 预览确认 -->
      <div v-if="activeStep === 2" class="step-content">
        <el-alert v-if="parsedOrganisms.length === 0" type="warning" :closable="false" show-icon>
          未解析到有效数据，请返回上一步检查输入。
        </el-alert>
        <template v-else>
          <el-descriptions :column="3" border size="small" class="mb12">
            <el-descriptions-item label="标本ID">{{ reportForm.sampleId }}</el-descriptions-item>
            <el-descriptions-item label="患者姓名">{{ reportForm.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="标本类型">{{ reportForm.specimenType }}</el-descriptions-item>
            <el-descriptions-item label="培养部位">{{ reportForm.cultureSite }}</el-descriptions-item>
            <el-descriptions-item label="细菌数">{{ parsedOrganisms.length }}</el-descriptions-item>
            <el-descriptions-item label="药敏总数">{{ totalSensitivityCount }}</el-descriptions-item>
          </el-descriptions>

          <el-table v-for="(org, idx) in parsedOrganisms" :key="idx" :data="org.sensitivities" border size="small" class="preview-table">
            <el-table-column label="细菌" min-width="200">
              <template #header>
                <span>{{ org.organismName }} ({{ org.organismCode || '-' }})</span>
              </template>
              <el-table-column prop="antibioticName" label="抗生素" min-width="140" />
              <el-table-column prop="micValue" label="MIC" width="100" align="center" />
              <el-table-column label="结果" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="sensitivityTagType(row.result)" size="small">{{ row.result }}</el-tag>
                </template>
              </el-table-column>
            </el-table-column>
          </el-table>
        </template>
        <div class="step-actions">
          <el-button @click="goToStep(1)">上一步</el-button>
          <el-button type="success" :loading="importLoading" :disabled="parsedOrganisms.length === 0" @click="handleImport">确认导入</el-button>
        </div>
      </div>

      <!-- Step 4: 完成 -->
      <div v-if="activeStep === 3" class="step-content">
        <el-result icon="success" title="导入成功" :sub-title="`报告编号: ${importedReportNo}`">
          <template #extra>
            <el-button type="primary" @click="handleReset">继续导入</el-button>
          </template>
        </el-result>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type UploadFile, type UploadFiles } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importMicroReport, type MicroReport, type Organism, type Sensitivity } from '@/api/report/micro'

const specimenTypes = ['血液', '尿液', '痰液', '分泌物', '脑脊液', '胸腹水', '粪便', '其他']

const activeStep = ref(0)
const inputTab = ref('csv')
const csvText = ref('')
const jsonFileData = ref<any>(null)
const importLoading = ref(false)
const importedReportNo = ref('')

const reportFormRef = ref<FormInstance>()
const reportForm = reactive({
  sampleId: undefined as number | undefined,
  patientName: '',
  specimenType: '',
  cultureSite: ''
})
const reportRules: FormRules = {
  sampleId: [{ required: true, message: '请输入标本ID', trigger: 'blur' }],
  specimenType: [{ required: true, message: '请选择标本类型', trigger: 'change' }],
  cultureSite: [{ required: true, message: '请输入培养部位', trigger: 'blur' }]
}

const parsedOrganisms = ref<Organism[]>([])
const totalSensitivityCount = computed(() => {
  return parsedOrganisms.value.reduce((sum, o) => sum + (o.sensitivities?.length || 0), 0)
})

function sensitivityTagType(result: string): string {
  const map: Record<string, string> = { S: 'success', I: 'warning', R: 'danger' }
  return map[result] || 'info'
}

function goToStep(step: number) {
  if (step === 1 && activeStep.value === 0) {
    if (!reportFormRef.value) return
    reportFormRef.value.validate((valid) => {
      if (valid) activeStep.value = step
    })
    return
  }
  activeStep.value = step
}

function handleFileChange(file: UploadFile, _files: UploadFiles) {
  if (!file.raw) return
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      jsonFileData.value = JSON.parse(e.target?.result as string)
    } catch (err) {
      ElMessage.error('JSON解析失败，请检查文件格式')
      jsonFileData.value = null
    }
  }
  reader.readAsText(file.raw)
}

function handleFileRemove() {
  jsonFileData.value = null
}

function parseData() {
  parsedOrganisms.value = []
  if (inputTab.value === 'csv') {
    parseCsv()
  } else {
    parseJson()
  }
}

function parseCsv() {
  const text = csvText.value.trim()
  if (!text) {
    ElMessage.warning('请输入CSV数据')
    return
  }
  const lines = text.split(/\r?\n/).filter(l => l.trim())
  const orgMap = new Map<string, Organism>()

  for (const line of lines) {
    // 跳过表头
    if (line.startsWith('细菌名称') || line.startsWith('Organism')) continue
    const parts = line.split(',').map(s => s.trim())
    if (parts.length < 5) continue

    const [orgName, orgCode, antibioticName, micValue, result] = parts
    const key = `${orgName}|${orgCode || ''}`
    if (!orgMap.has(key)) {
      const org: Organism = {
        organismName: orgName,
        organismCode: orgCode || '',
        quantity: '',
        colonsMethod: '',
        sensitivities: []
      }
      orgMap.set(key, org)
    }
    const org = orgMap.get(key)!
    const sensitivity: Sensitivity = {
      antibioticName,
      micValue: micValue || '',
      result: result.toUpperCase().startsWith('S') ? 'S' : result.toUpperCase().startsWith('R') ? 'R' : 'I',
      breakpoint: ''
    }
    org.sensitivities!.push(sensitivity)
  }

  parsedOrganisms.value = Array.from(orgMap.values())
  if (parsedOrganisms.value.length === 0) {
    ElMessage.warning('未解析到有效数据，请检查CSV格式')
    return
  }
  activeStep.value = 2
  ElMessage.success(`解析完成: ${parsedOrganisms.value.length} 个细菌, ${totalSensitivityCount.value} 条药敏`)
}

function parseJson() {
  if (!jsonFileData.value) {
    ElMessage.warning('请上传JSON文件')
    return
  }
  const data = jsonFileData.value
  if (data.organisms && Array.isArray(data.organisms)) {
    parsedOrganisms.value = data.organisms.map((o: any) => ({
      organismName: o.organismName || '',
      organismCode: o.organismCode || '',
      quantity: o.quantity || '',
      colonsMethod: o.colonsMethod || '',
      sensitivities: (o.sensitivities || []).map((s: any) => ({
        antibioticName: s.antibioticName || '',
        antibioticCode: s.antibioticCode || '',
        micValue: s.micValue || '',
        result: s.result || 'I',
        breakpoint: s.breakpoint || ''
      }))
    }))
    if (data.patientName) reportForm.patientName = data.patientName
    if (data.specimenType) reportForm.specimenType = data.specimenType
    if (data.cultureSite) reportForm.cultureSite = data.cultureSite
  } else {
    ElMessage.warning('JSON中未找到organisms数组')
    return
  }
  activeStep.value = 2
  ElMessage.success(`解析完成: ${parsedOrganisms.value.length} 个细菌, ${totalSensitivityCount.value} 条药敏`)
}

async function handleImport() {
  importLoading.value = true
  try {
    const payload: Partial<MicroReport> = {
      sampleId: reportForm.sampleId,
      patientName: reportForm.patientName,
      specimenType: reportForm.specimenType,
      cultureSite: reportForm.cultureSite,
      organisms: parsedOrganisms.value
    }
    const res = await importMicroReport(payload)
    importedReportNo.value = res?.reportNo || ''
    activeStep.value = 3
    ElMessage.success('导入成功')
  } catch (e) {
    // ignore
  } finally {
    importLoading.value = false
  }
}

function handleReset() {
  activeStep.value = 0
  csvText.value = ''
  jsonFileData.value = null
  parsedOrganisms.value = []
  importedReportNo.value = ''
  Object.assign(reportForm, {
    sampleId: undefined,
    patientName: '',
    specimenType: '',
    cultureSite: ''
  })
}
</script>

<style scoped lang="scss">
.instrument-import-page {
  padding: 16px;

  .import-steps {
    margin-bottom: 24px;
  }

  .step-content {
    min-height: 300px;
  }

  .step-actions {
    margin-top: 20px;
    text-align: center;
  }

  .csv-help {
    margin-bottom: 12px;
  }

  .mb12 {
    margin-bottom: 12px;
  }

  .preview-table {
    margin-bottom: 12px;
  }
}
</style>
