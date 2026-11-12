<template>
  <el-dialog
    v-model="visible"
    :title="`批量审核 (共 ${samples.length} 个标本)`"
    width="720px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      勾选「批量通过」可一键将所有可审核标本设为通过；否则可在下方逐条设置。
    </el-alert>
    <div class="toolbar" style="margin-bottom: 10px">
      <el-radio-group v-model="bulkPass" size="small" @change="applyBulk">
        <el-radio-button :label="true">批量通过</el-radio-button>
        <el-radio-button :label="false">批量驳回</el-radio-button>
        <el-radio-button :label="null">逐条设置</el-radio-button>
      </el-radio-group>
      <el-input
        v-if="bulkPass === false"
        v-model="bulkReason"
        size="small"
        placeholder="请输入统一驳回原因"
        style="width: 260px; margin-left: 12px"
      />
    </div>
    <div class="audit-list" style="max-height: 360px; overflow: auto; border: 1px solid #ebeef5; border-radius: 4px">
      <el-table :data="auditRows" size="small" border stripe>
        <el-table-column label="#" width="42" type="index" />
        <el-table-column label="姓名" prop="patientName" width="80" show-overflow-tooltip />
        <el-table-column label="条码号" prop="sampleBarcode" width="120" show-overflow-tooltip />
        <el-table-column label="检验项目" prop="testItems" width="160" show-overflow-tooltip />
        <el-table-column label="原状态" width="72" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本次审核" width="150" align="center">
          <template #default="{ row }">
            <el-radio-group v-model="row.pass" size="small" :disabled="!row.auditable">
              <el-radio-button :label="true">通过</el-radio-button>
              <el-radio-button :label="false">驳回</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>
        <el-table-column label="驳回原因" min-width="150">
          <template #default="{ row }">
            <el-input
              v-if="row.pass === false"
              v-model="row.reason"
              size="small"
              placeholder="请输入驳回原因"
            />
            <span v-else class="text-gray">-</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <div class="summary" style="float: left; line-height: 32px; color: #606266">
        通过 <b class="text-green">{{ passCount }}</b>，
        驳回 <b class="text-red">{{ rejectCount }}</b>，
        跳过 <b class="text-gray">{{ skipCount }}</b>
      </div>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        确认审核 ({{ passCount + rejectCount }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  batchAuditTechnician,
  type TechnicianSample
} from '@/api/sample'

interface AuditRow extends TechnicianSample {
  pass: boolean | null
  reason: string
  auditable: boolean
}

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
const auditRows = ref<AuditRow[]>([])
const bulkPass = ref<boolean | null>(null)
const bulkReason = ref('')
const submitting = ref(false)

const passCount = computed(() => auditRows.value.filter(r => r.pass === true).length)
const rejectCount = computed(() => auditRows.value.filter(r => r.pass === false).length)
const skipCount = computed(() => auditRows.value.filter(r => r.pass === null).length)

function getStatusType(s: number) {
  return ({ 0: 'warning', 1: '', 2: 'danger', 3: 'success' } as any)[s] || 'info'
}
function getStatusText(s: number) {
  return ({ 0: '待审', 1: '终审', 2: '急诊', 3: '已完成' } as any)[s] || '未知'
}

function handleOpen() {
  auditRows.value = props.samples.map(s => {
    // 可审核的条件：待审或急诊状态
    const auditable = s.status === 0 || s.status === 2
    return {
      ...s,
      pass: auditable ? null : null,
      reason: '',
      auditable
    }
  })
  bulkPass.value = null
  bulkReason.value = ''
}

function applyBulk() {
  if (bulkPass.value === null) return
  auditRows.value.forEach(r => {
    if (r.auditable) {
      r.pass = bulkPass.value
      if (bulkPass.value === false) r.reason = bulkReason.value || '批量驳回'
    }
  })
}

async function submit() {
  const passIds = auditRows.value.filter(r => r.pass === true).map(r => r.id)
  const rejectRows = auditRows.value.filter(r => r.pass === false)
  if (passIds.length === 0 && rejectRows.length === 0) {
    ElMessage.warning('请至少对一个标本设置审核结果')
    return
  }
  submitting.value = true
  try {
    let total = 0
    if (passIds.length) {
      const res = await batchAuditTechnician({ sampleIds: passIds, pass: true })
      total += res.success
    }
    if (rejectRows.length) {
      // 逐条驳回（每条可以有独立原因，这里简化为批量+reason取第一条）
      const ids = rejectRows.map(r => r.id)
      const reason = rejectRows[0]?.reason || '批量驳回'
      const res = await batchAuditTechnician({ sampleIds: ids, pass: false, reason })
      total += res.success
    }
    ElMessage.success(`批量审核完成：成功 ${total} 条`)
    emit('success')
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '批量审核失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.text-green { color: #67c23a; font-weight: 600; }
.text-red { color: #f56c6c; font-weight: 600; }
.text-gray { color: #909399; }
</style>
