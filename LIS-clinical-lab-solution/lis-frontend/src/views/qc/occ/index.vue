<template>
  <div class="occ-page">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:120px" @change="reload">
            <el-option v-for="(l, v) in STATUS_MAP" :key="v" :label="l" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-select v-model="filters.range" placeholder="全部" clearable style="width:120px" @change="reload">
            <el-option label="近30天" value="30D" /><el-option label="近90天" value="90D" /><el-option label="本年度" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.keyword" placeholder="失控单号/项目" clearable style="width:200px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 主体 grid-2-1 -->
    <div class="main-grid">
      <!-- 左: 事件列表 -->
      <el-card shadow="never" class="list-card">
        <template #header><div class="panel-header"><span>失控事件</span><el-tag size="small" type="info">共 {{ list.length }} 条</el-tag></div></template>
        <el-table
          v-loading="listLoading"
          :data="list"
          border
          stripe
          size="small"
          highlight-current-row
          @current-change="onSelect"
          style="width:100%"
        >
          <el-table-column prop="occNo" label="失控单号" width="150" />
          <el-table-column prop="projectName" label="项目" min-width="130" show-overflow-tooltip />
          <el-table-column prop="groupName" label="专业组" width="90" />
          <el-table-column label="规则" width="80" align="center">
            <template #default="{ row }">{{ row.triggerRules || '-' }}</template>
          </el-table-column>
          <el-table-column label="发现时间" width="150" align="center">
            <template #default="{ row }">{{ fmtDt(row.foundAt) }}</template>
          </el-table-column>
          <el-table-column label="根因" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.rootCauseCategory ? row.rootCauseCategory + (row.rootCauseDesc ? '·'+row.rootCauseDesc : '') : '待排查' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }"><el-tag :type="statusTag(row.status)" size="small">{{ STATUS_MAP[row.status||''] || row.status }}</el-tag></template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total"
            :page-sizes="[5,10,20]" layout="total, sizes, prev, pager, next" background @size-change="reload" @current-change="reload" small />
        </div>
      </el-card>

      <!-- 右: 处置工作台 -->
      <div v-if="event" class="workbench">
        <!-- 七步法流程条 -->
        <el-card shadow="never" class="card">
          <template #header><div class="panel-header"><span>七步法处置流程</span><el-tag size="small" :type="statusTag(event.status)">{{ STATUS_MAP[event.status||''] || event.status }}</el-tag></div></template>
          <div class="step-flow">
            <div v-for="(s, i) in event.steps" :key="s.stepNo" class="step-node" :class="stepClass(s)" @click="activeStep = s.stepNo ?? i+1">
              <div class="dot">{{ s.status==='DONE' ? '✓' : s.stepNo }}</div>
              <div class="name">{{ s.name }}</div>
            </div>
          </div>
        </el-card>

        <!-- 当前步骤详情 -->
        <el-card shadow="never" class="card">
          <template #header><div class="panel-header"><span>步骤详情：{{ currentStep?.name }}</span></div></template>
          <el-alert v-if="currentStep" :title="stepNotice(currentStep.stepNo)" type="info" :closable="false" show-icon style="margin-bottom:10px" />
          <div class="checklist">
            <el-checkbox
              v-for="(it, idx) in currentChecklist"
              :key="idx"
              :model-value="it.done"
              @change="(v:any)=>onToggle(it, idx, v)"
              style="display:block;margin-bottom:6px"
            >{{ it.text }}</el-checkbox>
          </div>
          <div v-if="(!currentStep || currentStep.stepNo !== 4)" style="margin-top:10px">
            <el-input v-model="conclusionInput" type="textarea" :rows="2" placeholder="本步骤处置结论" />
            <el-button type="primary" size="small" style="margin-top:8px" @click="saveConclusion">保存步骤</el-button>
          </div>
          <!-- 根因判定(步骤4) -->
          <div v-if="currentStep && currentStep.stepNo === 4" class="root-cause">
            <el-form inline>
              <el-form-item label="根因分类">
                <el-select v-model="rootForm.category" placeholder="选择根因" style="width:160px">
                  <el-option v-for="c in ['质控品','试剂','校准品','仪器','环境','操作','标本','其他']" :key="c" :label="c" :value="c" />
                </el-select>
              </el-form-item>
              <el-form-item label="描述"><el-input v-model="rootForm.description" style="width:200px" placeholder="根因描述" /></el-form-item>
              <el-form-item><el-button type="danger" :disabled="!allDone4" @click="confirmRootCause">确认根因</el-button></el-form-item>
            </el-form>
            <el-text v-if="!allDone4" type="info" size="small">需先完成本步骤全部排查项后才能确认根因</el-text>
          </div>
          <div v-if="!currentStep" class="empty"><el-empty description="无步骤数据" :image-size="50" /></div>
        </el-card>

        <!-- 报告概要 -->
        <el-card shadow="never" class="card">
          <template #header><div class="panel-header"><span>失控报告概要</span></div></template>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="失控单号">{{ event.occNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目">{{ event.projectName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="触发规则">{{ event.triggerRules || '-' }}</el-descriptions-item>
            <el-descriptions-item label="误差类型">{{ event.errorType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="质控值">{{ event.qcValues || '-' }}</el-descriptions-item>
            <el-descriptions-item label="根因">{{ event.rootCauseCategory ? event.rootCauseCategory + (event.rootCauseDesc?' · '+event.rootCauseDesc:'') : '-' }}</el-descriptions-item>
            <el-descriptions-item label="靶值">{{ event.targetValue ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="SD">{{ event.sd ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="Z分数(SDI)"><b :style="{color: Math.abs(maxZ||0)>=3 ? '#f56c6c':''}">{{ maxZ ?? '-' }}</b></el-descriptions-item>
            <el-descriptions-item label="对结果影响">{{ event.resultImpact || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 审批与归档 -->
        <el-card shadow="never" class="card">
          <template #header><div class="panel-header"><span>审批与归档</span></div></template>
          <div v-for="a in event.approvals" :key="a.levelNo" class="appr-row" :class="a.status?.toLowerCase()">
            <span class="lv">{{ a.levelName }}</span>
            <el-tag :type="a.status==='APPROVED'?'success':a.status==='REJECTED'?'danger':'info'" size="small">{{ a.status==='APPROVED'?'已通过':a.status==='REJECTED'?'已驳回':'待审批' }}</el-tag>
            <span v-if="a.status==='APPROVED'" class="ap">{{ a.approver }} {{ fmtDt(a.approvedAt) }}</span>
            <template v-else>
              <el-button type="primary" link size="small" @click="doApprove(a, true)">通过</el-button>
              <el-button type="danger" link size="small" @click="doApprove(a, false)">驳回</el-button>
            </template>
          </div>
        </el-card>
      </div>
      <div v-else class="workbench empty"><el-empty description="请选择左侧失控事件" /></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  getOccList, getOccDetail, updateOccStep, confirmOccRootCause, approveOcc,
  type OccEvent, type OccStepVO, type OccApprovalVO, type OccCheckItem
} from '@/api/qc'

const STATUS_MAP: Record<string, string> = { PENDING: '待处理', INVESTIGATING: '排查中', CORRECTED: '已纠正', CLOSED: '已关闭' }
const filters = reactive({ status: '', keyword: '', range: '' })
const list = ref<OccEvent[]>([])
const listLoading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const event = ref<OccEvent | null>(null)
const activeStep = ref(1)
const conclusionInput = ref('')
const rootForm = reactive({ category: '', description: '' })

const currentStep = computed<OccStepVO | null>(() => event.value?.steps?.find(s => s.stepNo === activeStep.value) || null)
const currentChecklist = computed<OccCheckItem[]>(() => currentStep.value?.checklist || [])
const allDone4 = computed(() => (event.value?.steps?.find(s => s.stepNo === 4)?.checklist || []).every(c => c.done))
const maxZ = computed(() => {
  const target = Number(event.value?.targetValue ?? 0)
  const sd = Number(event.value?.sd ?? 1) || 1
  if (!event.value?.qcValues || !target || !sd) return null
  const zs = event.value.qcValues.split(',').map(v => (Number(v) - target) / sd)
  return Math.max(...zs.map(Math.abs)).toFixed(2)
})

function statusTag(s?: string): any { const m: Record<string, any> = { PENDING: 'danger', INVESTIGATING: 'warning', CORRECTED: '', CLOSED: 'success' }; return m[s||''] || 'info' }
function stepClass(s: OccStepVO): string { return s.status === 'DONE' ? 'done' : s.status === 'CURRENT' ? 'current' : '' }
function fmtDt(t?: string) { return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-' }
function stepNotice(no?: number): string {
  const map: Record<number, string> = {
    1: '立即停止相关项目检测，张贴"失控待处理"标识，并通知当班人员与专业组组长。',
    2: '全面核对质控品、仪器、操作流程与数据录入四方面，排查人为因素。',
    3: '保留质控数据、原瓶质控品、试剂批号与校准记录，以备追溯。',
    4: '采用排除法定位根因，完成全部排查项后可确认根因判定。',
    5: '针对根因实施纠正：更换质控品/试剂，规范SOP操作。',
    6: '以新条件连续测定≥2批在控后，恢复相关项目检测。',
    7: '填写失控报告、评估既往结果影响并制定预防措施，提交复核。'
  }
  return map[no || 0] || ''
}

async function reload() {
  listLoading.value = true
  try {
    const res = await getOccList({ pageNum: pagination.pageNum, pageSize: pagination.pageSize, status: filters.status || undefined, keyword: filters.keyword || undefined, range: filters.range || undefined })
    list.value = res.records || []
    pagination.total = Number(res.total) || 0
    if (list.value.length && (!event.value || !list.value.some(e => e.id === event.value!.id))) select(list.value[0])
  } finally { listLoading.value = false }
}
async function onSelect(row: OccEvent) { if (row) select(row) }
async function select(row: OccEvent) {
  event.value = await getOccDetail(row.id!)
  activeStep.value = event.value?.currentStep || 1
}

async function onToggle(it: OccCheckItem, idx: number, val: boolean) {
  if (!event.value?.id || !currentStep.value) return
  currentChecklist.value[idx].done = val
  const next = await updateOccStep(event.value.id, activeStep.value, { checklist: [...currentChecklist.value] })
  event.value = next
  conclusionInput.value = currentStep.value?.conclusion || ''
}

async function saveConclusion() {
  if (!event.value?.id || !currentStep.value) return
  const next = await updateOccStep(event.value.id, activeStep.value, { conclusion: conclusionInput.value, checklist: [...currentChecklist.value] })
  event.value = next
  ElMessage.success('已保存')
}

async function confirmRootCause() {
  if (!event.value?.id) return
  if (!rootForm.category) { ElMessage.warning('请选择根因分类'); return }
  try {
    const next = await confirmOccRootCause(event.value.id, { category: rootForm.category, description: rootForm.description || rootForm.category })
    event.value = next
    ElMessage.success('根因已确认，可进入步骤5制定纠正措施')
  } catch (e: any) { ElMessage.error(e?.message || '确认失败') }
}

async function doApprove(a: OccApprovalVO, approved: boolean) {
  if (!event.value?.id) return
  let comment = ''
  if (!approved) { const { value } = await ElMessageBox.prompt('请输入驳回意见', '驳回', { inputValidator: v => !!v?.trim() || '意见不能为空' }); comment = value }
  const next = await approveOcc(event.value.id, { levelNo: a.levelNo!, approved, comment })
  event.value = next
  ElMessage.success(approved ? '审批通过' : '已驳回')
  reload()
}

onMounted(reload)
</script>

<style scoped lang="scss">
.occ-page { padding: 12px; }
.search-card { margin-bottom: 12px; :deep(.el-card__body){ padding: 12px 16px 0 16px; } }
.main-grid { display: grid; grid-template-columns: 1fr 2fr; gap: 12px; align-items: start; }
.list-card { :deep(.el-card__body){ padding: 8px; } .pager { display:flex; justify-content:flex-end; margin-top:10px; } }
.workbench { display: flex; flex-direction: column; gap: 12px; &.empty { border:1px dashed var(--el-border-color-lighter); border-radius:8px; padding:40px; } }
.panel-header { display:flex; justify-content:space-between; align-items:center; }
.step-flow { display:flex; gap:4px; flex-wrap:wrap;
  .step-node { flex:1; min-width:60px; text-align:center; cursor:pointer; padding:6px 2px; border-radius:6px; background:#f7f8fa; color:#909399;
    .dot { width:24px; height:24px; line-height:24px; border-radius:50%; margin:0 auto 4px; background:#e4e7ed; color:#fff; font-size:13px; }
    &.current { background:var(--el-color-primary-light-9); color:var(--el-color-primary); .dot{background:var(--el-color-primary);} }
    &.done { color:#67c23a; .dot{background:#67c23a;} }
  }
}
.checklist { margin:6px 0; }
.root-cause { margin-top:10px; }
.appr-row { display:flex; align-items:center; gap:10px; padding:8px 4px; border-bottom:1px solid var(--el-border-color-lighter); &:last-child{border-bottom:none;}
  .lv{width:80px;font-weight:600;} .ap{color:var(--el-text-color-secondary);font-size:12px;}
}
.card { :deep(.el-card__body){ padding:12px; } }
</style>