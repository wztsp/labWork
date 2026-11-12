<template>
  <div class="mobile-page stock-query">
    <header class="mobile-header">
      <h3>扫码查库存</h3>
      <div class="pending-badge" v-if="pending > 0">
        <el-badge :value="pending" :max="99" type="warning">待上传</el-badge>
      </div>
    </header>

    <!-- 扫码输入 -->
    <div class="scan-input">
      <el-input
        v-model="barcode"
        size="large"
        placeholder="扫描或输入试剂条码"
        clearable
        @keyup.enter="handleQuery"
      >
        <template #append>
          <el-button @click="handleQuery">查询</el-button>
        </template>
      </el-input>
    </div>

    <!-- 库存信息 -->
    <div v-loading="loading" class="stock-card" v-if="stock">
      <div class="stock-name">{{ stock.reagentName || '未知试剂' }}</div>
      <div class="stock-row"><span>条码</span><b>{{ stock.barcode }}</b></div>
      <div class="stock-row"><span>批号</span><b>{{ stock.batchNo || '-' }}</b></div>
      <div class="stock-row"><span>库存量</span><b :class="lowClass">{{ stock.quantity ?? 0 }} {{ stock.unit || '' }}</b></div>
      <div class="stock-row"><span>效期</span><b>{{ stock.expiryDate || '-' }}</b></div>
      <div class="stock-row"><span>位置</span><b>{{ stock.location || '-' }}</b></div>
      <div class="stock-row"><span>状态</span><b>
        <el-tag :type="stock.status === 1 ? 'success' : 'danger'" size="small">
          {{ stock.status === 1 ? '正常' : '不足' }}
        </el-tag>
      </b></div>
    </div>
    <el-empty v-if="!loading && queried && !stock" description="未匹配到该条码的库存" />

    <!-- 离线待上传 -->
    <div v-if="pending > 0" class="offline-bar">
      <span>有 {{ pending }} 条离线扫码记录待上传</span>
      <el-button type="primary" size="small" :loading="flushing" @click="handleFlush">一键上传</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { scanQueryBarcode, scanOut, type ReagentStock } from '@/api/reagent'
import { flush, pendingCount, listPending } from '@/utils/offlineQueue'

const barcode = ref('')
const stock = ref<ReagentStock | null>(null)
const loading = ref(false)
const queried = ref(false)
const pending = ref(0)
const flushing = ref(false)
const userStore = useUserStore()

async function refreshPending() {
  pending.value = (await pendingCount()) || 0
}

async function handleQuery() {
  if (!barcode.value) {
    ElMessage.warning('请输入条码')
    return
  }
  loading.value = true
  try {
    stock.value = await scanQueryBarcode(barcode.value.trim())
    queried.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

async function handleFlush() {
  flushing.value = true
  try {
    const items = listPending<{ barcode: string; quantity: number; operatorId?: number; operatorName?: string }>()
    const res = await flush(async (item) => {
      await scanOut({
        barcode: item.payload.barcode,
        quantity: item.payload.quantity,
        operatorId: userStore.userId || undefined,
        operatorName: userStore.realName || undefined,
        sourceNo: 'MOBILE-OFFLINE-' + item.id,
        remark: '移动端离线补传'
      })
    })
    if (res.failed > 0) {
      ElMessage.warning(`上传 ${res.success} 条，失败 ${res.failed} 条${res.error ? '：' + res.error : ''}`)
    } else {
      ElMessage.success(`已上传 ${res.success} 条`)
    }
    refreshPending()
  } finally {
    flushing.value = false
  }
}

const lowClass = () => (stock.value && stock.value.status !== 1 ? 'low' : '')

onMounted(refreshPending)
</script>

<style scoped lang="scss">
.mobile-page {
  max-width: 480px;
  margin: 0 auto;
  padding: 16px;
  min-height: 100vh;
  background: #f5f7fa;
}

.mobile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-size: 18px;
  }
}

.scan-input {
  margin-bottom: 16px;
}

.stock-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .stock-name {
    font-size: 18px;
    font-weight: 600;
    margin-bottom: 12px;
  }

  .stock-row {
    display: flex;
    justify-content: space-between;
    padding: 8px 0;
    border-bottom: 1px solid #f0f0f0;
    font-size: 14px;

    span {
      color: #909399;
    }

    .low {
      color: #f56c6c;
      font-weight: 600;
    }
  }
}

.offline-bar {
  margin-top: 16px;
  background: #fff3e0;
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
}
</style>