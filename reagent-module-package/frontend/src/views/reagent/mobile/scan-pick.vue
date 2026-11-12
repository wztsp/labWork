<template>
  <div class="mobile-page scan-pick">
    <header class="mobile-header">
      <h3>扫码领用</h3>
      <div class="pending-badge" v-if="pending > 0">
        <el-badge :value="pending" :max="99" type="warning">待上传</el-badge>
      </div>
    </header>

    <!-- 扫码 -->
    <div class="scan-input">
      <el-input
        v-model="barcode"
        size="large"
        placeholder="扫描或输入试剂条码"
        clearable
        @keyup.enter="handleQuery"
      >
        <template #append>
          <el-button @click="handleQuery">确认</el-button>
        </template>
      </el-input>
    </div>

    <!-- 待领用试剂卡 -->
    <div v-loading="loading" v-if="stock" class="pick-card">
      <div class="clickable" @click="addToCart">
        <div class="stock-name">{{ stock.reagentName || '未知试剂' }}</div>
        <div class="stock-row"><span>条码</span><b>{{ stock.barcode }}</b></div>
        <div class="stock-row"><span>批号</span><b>{{ stock.batchNo || '-' }}</b></div>
        <div class="stock-row"><span>库存量</span><b>{{ stock.quantity ?? 0 }} {{ stock.unit || '' }}</b></div>
        <div class="stock-row"><span>效期</span><b>{{ stock.expiryDate || '-' }}</b></div>
        <div class="stock-row"><span>存放位置</span><b>{{ stock.location || '-' }}</b></div>
      </div>
      <div class="pick-actions">
        <el-input-number v-model="qty" :min="1" :max="9999" size="large" style="width: 120px" />
        <el-button type="primary" size="large" @click="doOut">领用出库</el-button>
      </div>
    </div>
    <el-empty v-if="!loading && queried && !stock" description="未匹配到该条码的库存" />

    <!-- 领用清单 -->
    <div v-if="cart.length > 0" class="cart">
      <div class="cart-title">本次领用清单（{{ cart.length }} 项）</div>
      <div v-for="(c, i) in cart" :key="i" class="cart-item">
        <div class="cart-info">
          <div class="cart-name">{{ c.reagentName }}</div>
          <div class="cart-meta">{{ c.barcode }} × {{ c.quantity }}</div>
        </div>
        <el-button type="danger" link size="small" @click="cart.splice(i, 1)">移除</el-button>
      </div>
      <el-button type="primary" size="large" block :loading="submitting" @click="submitAll">提交领用</el-button>
    </div>

    <!-- 离线待上传 -->
    <div v-if="pending > 0" class="offline-bar">
      <span>有 {{ pending }} 条离线领用记录待上传</span>
      <el-button type="primary" size="small" :loading="flushing" @click="handleFlush">一键上传</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { scanQueryBarcode, scanOut, type ReagentStock } from '@/api/reagent'
import { enqueue, flush, pendingCount, listPending } from '@/utils/offlineQueue'

const barcode = ref('')
const qty = ref(1)
const stock = ref<ReagentStock | null>(null)
const loading = ref(false)
const queried = ref(false)
const cart = ref<Array<{ barcode: string; reagentName?: string; quantity: number }>>([])
const submitting = ref(false)
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
    if (stock.value) {
      qty.value = 1
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function addToCart() {
  if (!stock.value) return
  cart.value.push({
    barcode: stock.value.barcode || barcode.value.trim(),
    reagentName: stock.value.reagentName,
    quantity: qty.value
  })
  ElMessage.success('已加入领用清单')
  barcode.value = ''
  stock.value = null
}

async function submitAll() {
  if (cart.value.length === 0) return
  submitting.value = true
  let success = 0
  let failed = 0
  try {
    for (const item of cart.value) {
      try {
        await scanOut({
          barcode: item.barcode,
          quantity: item.quantity,
          operatorId: userStore.userId || undefined,
          operatorName: userStore.realName || undefined,
          sourceNo: 'MOBILE-PICK-' + item.barcode,
          remark: '移动端扫码领用'
        })
        success++
      } catch (err: any) {
        // 提交失败 → 暂存本地离线队列（幂等键 = barcode）
        const added = await enqueue(`${item.barcode}`, item)
        failed++
        if (!added) {
          ElMessage.warning(`条码 ${item.barcode} 已在待上传队列中`)
        }
      }
    }
    cart.value = []
    refreshPending()
    if (success > 0) ElMessage.success(`已领用 ${success} 项`)
    if (failed > 0) ElMessage.warning(`${failed} 项提交失败，已暂存待上传`)
  } finally {
    submitting.value = false
  }
}

async function handleFlush() {
  flushing.value = true
  try {
    const items = listPending<{ barcode: string; quantity: number }>()
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

.pick-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .clickable {
    cursor: pointer;
  }

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
  }

  .pick-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 12px;
  }
}

.cart {
  margin-top: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .cart-title {
    font-weight: 600;
    margin-bottom: 8px;
  }

  .cart-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 0;
    border-bottom: 1px solid #f0f0f0;

    .cart-name {
      font-size: 15px;
    }

    .cart-meta {
      font-size: 12px;
      color: #909399;
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