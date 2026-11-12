<template>
  <div class="pending-page">
    <!-- 顶部统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card" :class="{ active: filterType === 'all' }" @click="setFilter('all')">
        <div class="stat-label">全部申请</div>
        <div class="stat-value">{{ stats.total }}</div>
      </div>
      <div class="stat-card pending" :class="{ active: filterType === 'pending' }" @click="setFilter('pending')">
        <div class="stat-label">待采集</div>
        <div class="stat-value">{{ stats.pending }}</div>
      </div>
      <div class="stat-card collected" :class="{ active: filterType === 'collected' }" @click="setFilter('collected')">
        <div class="stat-label">已采集</div>
        <div class="stat-value">{{ stats.collected }}</div>
      </div>
      <div class="stat-card urgent" :class="{ active: filterType === 'urgent' }" @click="setFilter('urgent')">
        <div class="stat-label">急诊申请</div>
        <div class="stat-value">{{ stats.urgent }}</div>
      </div>
    </div>

    <!-- 查询条件 -->
    <div class="search-bar">
      <el-input
        v-model="query.visitNo"
        placeholder="就诊号"
        clearable
        style="width: 180px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="query.patientName"
        placeholder="患者姓名"
        clearable
        style="width: 160px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="query.orderNo"
        placeholder="申请单号"
        clearable
        style="width: 180px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.orderType" placeholder="申请类型" clearable style="width: 120px">
        <el-option label="门诊" value="门诊" />
        <el-option label="住院" value="住院" />
        <el-option label="急诊" value="急诊" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="goToApply">
        新建申请
      </el-button>
      <el-button type="success" :icon="Cpu" :disabled="!selectedRows.length" @click="batchGotoCollect">
        批量采集 ({{ selectedRows.length }})
      </el-button>
    </div>

    <!-- 申请列表 -->
    <div class="table-wrapper">
      <el-table
        v-loading="loading"
        :data="filteredData"
        border
        stripe
        height="100%"
        size="small"
        highlight-current-row
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" fixed />
        <el-table-column type="index" label="序号" width="55" align="center" fixed />
        <el-table-column prop="orderNo" label="申请单号" width="150" fixed show-overflow-tooltip />
        <el-table-column prop="visitNo" label="就诊号" width="140" fixed show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者姓名" width="90" fixed />
        <el-table-column prop="patientGender" label="性别" width="55" align="center">
          <template #default="{ row }">
            <el-tag :type="row.patientGender === '男' ? 'primary' : 'danger'" size="small" effect="plain">
              {{ row.patientGender || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="patientAge" label="年龄" width="65" align="center" />
        <el-table-column prop="orderType" label="类型" width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="orderTypeTag(row.orderType)" size="small">{{ row.orderType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检验项目" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag
              v-for="item in (row.items || [])"
              :key="item.id"
              size="small"
              effect="plain"
              style="margin: 2px"
            >
              {{ item.testItemName }}
            </el-tag>
            <span v-if="!row.items?.length" class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="items.length" label="项目数" width="70" align="center">
          <template #default="{ row }">{{ row.items?.length || 0 }}</template>
        </el-table-column>
        <el-table-column prop="applyDept" label="申请科室" width="100" show-overflow-tooltip />
        <el-table-column prop="applyDoctor" label="申请医生" width="90" />
        <el-table-column prop="clinicalDiag" label="临床诊断" width="120" show-overflow-tooltip />
        <el-table-column label="采集方式" width="140" align="center">
          <template #default="{ row }">
            <el-select
              v-model="row.collectionMethodId"
              placeholder="选择"
              size="small"
              clearable
              filterable
              style="width: 100%"
              @change="(val) => onCollectionMethodChange(row, val)"
            >
              <el-option
                v-for="m in collectionMethodOptions"
                :key="m.id"
                :label="m.methodName"
                :value="Number(m.id)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="150">
          <template #default="{ row }">{{ formatTime(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="条码" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.sampleCount > 0" type="success" size="small">{{ row.sampleCount }}管</el-tag>
            <el-tag v-else type="warning" size="small">未生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.sampleCount === 0" type="warning" size="small">待采集</el-tag>
            <el-tag v-else type="success" size="small">已生成条码</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click="gotoCollect(row)">采集</el-button>
            <el-button link size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待采集申请" />
        </template>
      </el-table>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="申请详情" width="700px">
      <el-descriptions :column="2" border v-if="currentDetail">
        <el-descriptions-item label="申请单号">{{ currentDetail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="就诊号">{{ currentDetail.visitNo }}</el-descriptions-item>
        <el-descriptions-item label="患者姓名">{{ currentDetail.patientName }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ currentDetail.patientGender }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ currentDetail.patientAge }}</el-descriptions-item>
        <el-descriptions-item label="申请类型">{{ currentDetail.orderType }}</el-descriptions-item>
        <el-descriptions-item label="申请科室">{{ currentDetail.applyDept }}</el-descriptions-item>
        <el-descriptions-item label="申请医生">{{ currentDetail.applyDoctor }}</el-descriptions-item>
        <el-descriptions-item label="临床诊断" :span="2">{{ currentDetail.clinicalDiag }}</el-descriptions-item>
        <el-descriptions-item label="生理条件" :span="2">
          <el-tag v-if="currentDetail.patientCondition" type="warning" size="small">{{ currentDetail.patientCondition }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="采集方式">
          <el-tag v-if="currentDetail.collectionMethodName" type="primary" size="small">{{ currentDetail.collectionMethodName }}</el-tag>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ formatTime(currentDetail.applyTime) }}</el-descriptions-item>
        <el-descriptions-item label="已生成条码数">{{ currentDetail.sampleCount || 0 }} 管</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="currentDetail.sampleCount === 0" type="warning" size="small">待采集</el-tag>
          <el-tag v-else type="success" size="small">已生成条码</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">检验项目</el-divider>
      <el-table :data="currentDetail?.items || []" size="small" border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="testItemCode" label="项目编码" width="120" />
        <el-table-column prop="testItemName" label="项目名称" />
        <el-table-column prop="fee" label="费用" width="100" align="right">
          <template #default="{ row }">¥{{ row.fee?.toFixed(2) || '0.00' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="success" @click="gotoCollect(currentDetail!)">前往采集</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Cpu, Plus } from '@element-plus/icons-vue'
import { getOrderPage, updateOrderCollectionMethod, type OrderVO, type OrderQuery } from '@/api/sample'
import { getAllCollectionMethods, type CollectionMethod } from '@/api/base'

const router = useRouter()
const loading = ref(false)
const tableData = ref<OrderVO[]>([])
const selectedRows = ref<OrderVO[]>([])
const filterType = ref<'all' | 'pending' | 'collected' | 'urgent'>('pending')
const detailVisible = ref(false)
const currentDetail = ref<OrderVO | null>(null)
const collectionMethodOptions = ref<CollectionMethod[]>([])
/** 正在更新采集方式的申请ID集合(防止重复提交) */
const updatingMethodOrderIds = ref<Set<string | number>>(new Set())

const query = reactive<OrderQuery>({
  visitNo: '',
  patientName: '',
  orderNo: '',
  current: 1,
  size: 200
})

const stats = computed(() => {
  const total = tableData.value.length
  const pending = tableData.value.filter(o => (o.sampleCount || 0) === 0).length
  const collected = tableData.value.filter(o => (o.sampleCount || 0) > 0).length
  const urgent = tableData.value.filter(o => o.orderType === '急诊').length
  return { total, pending, collected, urgent }
})

/** 按筛选类型过滤 */
const filteredData = computed(() => {
  switch (filterType.value) {
    case 'pending':
      return tableData.value.filter(o => (o.sampleCount || 0) === 0)
    case 'collected':
      return tableData.value.filter(o => (o.sampleCount || 0) > 0)
    case 'urgent':
      return tableData.value.filter(o => o.orderType === '急诊')
    case 'all':
    default:
      return tableData.value
  }
})

function setFilter(type: 'all' | 'pending' | 'collected' | 'urgent') {
  filterType.value = type
}

async function loadData() {
  loading.value = true
  try {
    const res = await getOrderPage({ ...query, current: 1, size: 200 })
    tableData.value = res.records || []
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  query.visitNo = ''
  query.patientName = ''
  query.orderNo = ''
  query.orderType = undefined
  loadData()
}

function handleSelectionChange(rows: OrderVO[]) {
  selectedRows.value = rows
}

function formatTime(time?: string) {
  if (!time) return ''
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

function orderTypeTag(type?: string): string {
  if (type === '急诊') return 'danger'
  if (type === '住院') return 'warning'
  return 'primary'
}

function viewDetail(row: OrderVO) {
  currentDetail.value = row
  detailVisible.value = true
}

function gotoCollect(row: OrderVO) {
  // 跳转到采集工作站, 通过query传递就诊号便于自动查询
  router.push({ path: '/sample/barcode', query: { visitNo: row.visitNo } })
}

function batchGotoCollect() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请选择要采集的申请')
    return
  }
  // 取第一条跳转, 实际批量采集可在采集工作站逐个处理
  const first = selectedRows.value[0]
  ElMessage.info(`已选择 ${selectedRows.value.length} 条申请, 即将跳转到采集工作站`)
  gotoCollect(first)
}

/** 跳转到新建检验申请页面 */
function goToApply() {
  router.push('/sample/apply')
}

/** 加载采集方式选项 */
async function loadCollectionMethods() {
  try {
    collectionMethodOptions.value = await getAllCollectionMethods()
  } catch {
    collectionMethodOptions.value = []
  }
}

/** 行内修改采集方式: 立即调用后端保存 */
async function onCollectionMethodChange(row: OrderVO, val: number | undefined) {
  if (updatingMethodOrderIds.value.has(row.id)) return
  updatingMethodOrderIds.value.add(row.id)
  try {
    const method = collectionMethodOptions.value.find(m => Number(m.id) === val)
    const methodId = method ? method.id : null
    const methodName = method ? method.methodName : ''
    await updateOrderCollectionMethod(row.id, methodId as any, methodName)
    row.collectionMethodId = methodId as any
    row.collectionMethodName = methodName
    ElMessage.success(`已更新采集方式: ${methodName || '(已清空)'}`)
  } catch (e) {
    ElMessage.error('采集方式更新失败')
  } finally {
    updatingMethodOrderIds.value.delete(row.id)
  }
}

onMounted(() => {
  loadData()
  loadCollectionMethods()
})
</script>

<style scoped lang="scss">
.pending-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  padding: 10px;
  overflow: hidden;
  gap: 10px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.stat-card {
  background: #fff;
  border-radius: 6px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 21, 41, 0.1);
  }

  &.active {
    border-color: #1a73e8;
    background: #ecf5ff;
  }

  &.pending.active {
    border-color: #e6a23c;
    background: #fdf6ec;
  }

  &.collected.active {
    border-color: #67c23a;
    background: #f0f9eb;
  }

  &.urgent.active {
    border-color: #f56c6c;
    background: #fef0f0;
  }

  .stat-label {
    font-size: 13px;
    color: #909399;
  }

  .stat-value {
    font-size: 26px;
    font-weight: 700;
    color: #303133;
  }
}

.stat-card.pending .stat-value { color: #e6a23c; }
.stat-card.collected .stat-value { color: #67c23a; }
.stat-card.urgent .stat-value { color: #f56c6c; }

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 6px;
  flex-wrap: wrap;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.spacer {
  flex: 1;
}

.table-wrapper {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  padding: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.text-muted {
  color: #c0c4cc;
}
</style>
