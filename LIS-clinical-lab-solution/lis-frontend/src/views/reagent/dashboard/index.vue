<template>
  <div class="reagent-dashboard-page">
    <!-- 顶部统计卡片 -->
    <el-card class="search-card" shadow="never">
      <div class="dashboard-header">
        <span class="dashboard-title">库存看板</span>
        <el-button type="primary" plain :loading="loading" @click="loadAll">
          <el-icon style="margin-right: 4px"><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <el-row :gutter="16" class="stat-row">
        <el-col :span="4" v-for="(card, idx) in 5" :key="idx">
          <el-card shadow="hover" class="stat-card" :class="statCards[idx].cls" @click="statCards[idx].scrollTo ? scrollTo(statCards[idx].scrollTo!) : null">
            <div class="stat-content">
              <el-icon class="stat-icon" :style="{ color: statCards[idx].color }">
                <component :is="statCards[idx].icon" />
              </el-icon>
              <div class="stat-value">{{ statCards[idx].value }}</div>
              <div class="stat-label">{{ statCards[idx].label }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 近效期预警 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-title">
          <el-icon style="color: #e6a23c; margin-right: 6px"><Warning /></el-icon>
          <span>近效期预警 (30天内)</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="nearExpiryList" border stripe style="width: 100%" ref="nearExpiryTableRef">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160">
          <template #default="{ row }">{{ row.reagentName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" width="140" />
        <el-table-column label="到期日期" width="140" align="center">
          <template #default="{ row }">
            <span :class="expiryClass(row.expiryDate)">{{ row.expiryDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="库存数量" width="110" align="right" />
        <el-table-column prop="location" label="存放位置" width="160" />
      </el-table>
    </el-card>

    <!-- 低库存预警 -->
    <el-card shadow="never" class="table-card" ref="lowStockCardRef">
      <template #header>
        <div class="card-title">
          <el-icon style="color: #f56c6c; margin-right: 6px"><Goods /></el-icon>
          <span>低库存预警</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="lowStockList" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" />
        <el-table-column prop="totalQuantity" label="当前库存" width="120" align="right" />
        <el-table-column prop="safetyStock" label="安全库存" width="120" align="right" />
        <el-table-column label="缺口" width="120" align="right">
          <template #default="{ row }">
            <span class="gap-text">{{ gapOf(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 开瓶效期预警 -->
    <el-card shadow="never" class="table-card" ref="openedExpiryCardRef">
      <template #header>
        <div class="card-title">
          <el-icon style="color: #9c27b0; margin-right: 6px"><Timer /></el-icon>
          <span>开瓶效期预警</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="openedExpiryList" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reagentName" label="试剂名称" min-width="160" />
        <el-table-column prop="lotNo" label="批号" width="140" />
        <el-table-column label="开瓶时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.openTime) }}</template>
        </el-table-column>
        <el-table-column label="开瓶效期" width="170" align="center">
          <template #default="{ row }">
            <span :class="{ 'expired-text': isOpenedExpired(row) }">{{ formatTime(row.openedExpiry) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已过期' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Money, Warning, Goods, Timer, Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  getDashboardOverview,
  getNearExpiryList,
  getLowStockList,
  getOpenedExpiryList,
  type DashboardOverview
} from '@/api/reagent'

const loading = ref(false)
const overview = ref<DashboardOverview>({
  totalReagents: 0,
  totalStockValue: 0,
  nearExpiryCount: 0,
  lowStockCount: 0,
  openedExpiryCount: 0
})
const nearExpiryList = ref<any[]>([])
const lowStockList = ref<any[]>([])
const openedExpiryList = ref<any[]>([])

const nearExpiryTableRef = ref()
const lowStockCardRef = ref()
const openedExpiryCardRef = ref()

const statCards = computed(() => [
  {
    label: '试剂种类',
    value: overview.value.totalReagents,
    icon: Document,
    color: '#1a73e8',
    cls: 'stat-blue',
    scrollTo: ''
  },
  {
    label: '库存总值',
    value: `¥${Number(overview.value.totalStockValue || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    icon: Money,
    color: '#67c23a',
    cls: 'stat-green',
    scrollTo: ''
  },
  {
    label: '近效期预警',
    value: overview.value.nearExpiryCount,
    icon: Warning,
    color: '#e6a23c',
    cls: 'stat-orange',
    scrollTo: 'nearExpiry'
  },
  {
    label: '低库存预警',
    value: overview.value.lowStockCount,
    icon: Goods,
    color: '#f56c6c',
    cls: 'stat-red',
    scrollTo: 'lowStock'
  },
  {
    label: '开瓶效期预警',
    value: overview.value.openedExpiryCount,
    icon: Timer,
    color: '#9c27b0',
    cls: 'stat-purple',
    scrollTo: 'openedExpiry'
  }
])

function scrollTo(target: string) {
  if (target === 'nearExpiry' && nearExpiryTableRef.value?.$el) {
    nearExpiryTableRef.value.$el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else if (target === 'lowStock' && lowStockCardRef.value?.$el) {
    lowStockCardRef.value.$el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else if (target === 'openedExpiry' && openedExpiryCardRef.value?.$el) {
    openedExpiryCardRef.value.$el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function expiryClass(expiryDate?: string): string {
  if (!expiryDate) return ''
  const diff = dayjs(expiryDate).diff(dayjs(), 'day')
  if (diff < 0) return 'expired-text'
  if (diff < 7) return 'expired-text'
  if (diff < 30) return 'warning-text'
  return ''
}

function gapOf(row: any): string {
  const total = Number(row.totalQuantity || 0)
  const safety = Number(row.safetyStock || 0)
  const gap = safety - total
  return gap > 0 ? `${gap}` : '0'
}

function isOpenedExpired(row: any): boolean {
  if (row.status === 0) return true
  if (!row.openedExpiry) return false
  return dayjs(row.openedExpiry).isBefore(dayjs())
}

async function loadAll() {
  loading.value = true
  try {
    const [ov, near, low, opened] = await Promise.all([
      getDashboardOverview(),
      getNearExpiryList(30),
      getLowStockList(),
      getOpenedExpiryList()
    ])
    overview.value = ov
    nearExpiryList.value = near || []
    lowStockList.value = low || []
    openedExpiryList.value = opened || []
  } catch (e) {
    ElMessage.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped lang="scss">
.reagent-dashboard-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      .dashboard-title {
        font-size: 18px;
        font-weight: 600;
        color: #303133;
      }
    }

    .stat-row {
      .stat-card {
        cursor: default;
        transition: transform 0.2s;

        &:hover {
          transform: translateY(-2px);
        }

        &.stat-orange,
        &.stat-red,
        &.stat-purple {
          cursor: pointer;
        }

        .stat-content {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 8px 0;

          .stat-icon {
            font-size: 32px;
            margin-bottom: 8px;
          }

          .stat-value {
            font-size: 24px;
            font-weight: 700;
            color: #303133;
            line-height: 1.2;
          }

          .stat-label {
            font-size: 13px;
            color: #909399;
            margin-top: 6px;
          }
        }
      }
    }
  }

  .table-card {
    margin-bottom: 12px;

    .card-title {
      display: flex;
      align-items: center;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
    }
  }

  .expired-text {
    color: #f56c6c;
    font-weight: 600;
  }

  .warning-text {
    color: #e6a23c;
    font-weight: 600;
  }

  .gap-text {
    color: #f56c6c;
    font-weight: 600;
  }
}
</style>
