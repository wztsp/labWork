<template>
  <div class="mobile-page scan-check">
    <header class="mobile-header">
      <h3>扫码盘点</h3>
      <div class="pending-badge" v-if="pending > 0">
        <el-button type="warning" size="small" @click="handleFlush">{{ pending }} 条待上传</el-button>
      </div>
    </header>

    <!-- 盘点单切换 -->
    <div class="check-selector">
      <el-select v-model="currentCheckId" placeholder="选择盘点单" style="width: 100%" @change="loadItems">
        <el-option v-for="c in checkList" :key="c.id" :label="`${c.checkNo}（${checkStatusText(c.status)}）`" :value="c.id!">
          <span>{{ c.checkNo }}</span>
          <el-tag size="small" :type="c.status === 1 ? 'success' : 'info'">{{ checkStatusText(c.status) }}</el-tag>
        </el-option>
      </el-select>
      <el-button type="primary" style="margin-top: 8px" block @click="handleCreateCheck">新建盘点单</el-button>
    </div>

    <!-- 扫码 -->
    <div class="scan-input">
      <el-input
        v-model="barcode"
        size="large"
        placeholder="扫描条码录入实盘数量"
        clearable
        @keyup.enter="handleScan"
      >
        <template #append>
          <el-button @click="handleScan">扫入</el-button>
        </template>
      </el-input>
    </div>

    <!-- 进度 -->
    <div v-if="currentCheckId" class="progress">
      已盘点 {{ scannedCount }} / {{ items.length }}，待盘 {{ items.length - scannedCount }}
    </div>

    <!-- 盘点明细 -->
    <div v-loading="loadingItems" class="items">
      <div v-for="item in displayItems" :key="item.id" class="item-card" :class="{ done: item.actualQty !== undefined && item.actualQty !== null }">
        <div class="item-info">
          <div class="item-name">{{ item.reagentName || '未知试剂' }}{{ item.actualQty === undefined || item.actualQty === null ? '' : ' ✓' }}</div>
          <div class="item-meta">账面 {{ item.bookQty }} / 实盘 {{ item.actualQty ?? '-' }}</div>
          <div class="item-meta">批号 {{ item.lotNo || '-' }}，位置 {{ item.location || '-' }}</div>
        </div>
        <div class="item-input">
          <el-input-number v-model="item.actualQty" :min="0" size="small" controls-position="right" style="width: 110px" />
        </div>
      </div>
    </div>
    <el-empty v-if="currentCheckId && !loadingItems && items.length === 0" description="该盘点单暂无明细" />

    <!-- 提交 -->
    <el-button
      v-if="currentCheckId"
      type="primary"
      size="large"
      block
      :loading="submitting"
      :disabled="items.length === 0"
      style="margin-top: 16px"
      @click="handleSubmit"
    >
      提交盘点（{{ scannedCount }} 项）
    </el-button>

    <!-- 离线待上传 -->
    <div v-if="pending > 0" class="offline-bar">
      <span>有 {{ pending }} 条盘点记录暂存本地</span>
      <el-button type="primary" size="small" :loading="flushing" @click="handleFlush">一键上传</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getStockCheckPage,
  createStockCheck,
  getStockCheckItems,
  submitStockCheck,
  scanQueryBarcode,
  type ReagentStockCheck,
  type ReagentStockCheckItem
} from '@/api/reagent'
import { enqueue, flush, pendingCount } from '@/utils/offlineQueue'

const barcode = ref('')
const checkList = ref<ReagentStockCheck[]>([])
const currentCheckId = ref<number | undefined>()
const items = ref<ReagentStockCheckItem[]>([])
const loadingItems = ref(false)
const submitting = ref(false)
const pending = ref(0)
const flushing = ref(false)
const userStore = useUserStore()

const scannedCount = computed(() =>
  items.value.filter((it) => it.actualQty !== undefined && it.actualQty !== null).length
)

const displayItems = computed(() => items.value)

async function refreshPending() {
  pending.value = (await pendingCount()) || 0
}

async function fetchChecks() {
  try {
    const res = await getStockCheckPage({ pageNum: 1, pageSize: 20 })
    checkList.value = res.records || []
    // 默认选中第一个进行中的盘点单
    const active = checkList.value.find((c) => c.status === 0)
    if (active && active.id) {
      currentCheckId.value = active.id
      loadItems()
    } else if (checkList.value.length > 0 && checkList.value[0].id) {
      currentCheckId.value = checkList.value[0].id
      loadItems()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载盘点单失败')
  }
}

async function handleCreateCheck() {
  try {
    const res = await createStockCheck({
      operatorId: userStore.userId || undefined,
      operatorName: userStore.realName || undefined,
      remark: '移动端扫码盘点'
    })
    ElMessage.success('盘点单已创建')
    await fetchChecks()
    if (res.id) {
      currentCheckId.value = res.id
      loadItems()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '创建盘点单失败')
  }
}

async function loadItems() {
  if (!currentCheckId.value) return
  loadingItems.value = true
  try {
    items.value = await getStockCheckItems(currentCheckId.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载盘点明细失败')
  } finally {
    loadingItems.value = false
  }
}

async function handleScan() {
  if (!currentCheckId.value) {
    ElMessage.warning('请先选择或新建盘点单')
    return
  }
  if (!barcode.value) {
    ElMessage.warning('请输入条码')
    return
  }
  try {
    const stock = await scanQueryBarcode(barcode.value.trim())
    if (!stock) {
      ElMessage.warning('未匹配到该条码的库存')
      return
    }
    const item = items.value.find((it) => it.stockId === stock.id)
    if (!item) {
      ElMessage.warning('该条码不在当前盘点单中')
      return
    }
    // 已盘过的直接累加常见数量，未盘过默认账面数
    if (item.actualQty === undefined || item.actualQty === null) {
      item.actualQty = item.bookQty ?? 1
    } else {
      item.actualQty = (item.actualQty ?? 0) + 1
    }
    ElMessage.success(`已录入 ${stock.reagentName || '试剂'}，实盘 ${item.actualQty}`)
    barcode.value = ''
  } catch (e: any) {
    ElMessage.error(e?.message || '扫码失败')
  }
}

async function handleSubmit() {
  if (!currentCheckId.value) return
  const submits = items.value
    .filter((it) => it.actualQty !== undefined && it.actualQty !== null)
    .map((it) => ({ id: it.id!, actualQty: it.actualQty! }))
  if (submits.length === 0) {
    ElMessage.warning('尚未录入任何实盘数量')
    return
  }
  submitting.value = true
  try {
    await submitStockCheck(currentCheckId.value, submits, {
      operatorId: userStore.userId || undefined,
      operatorName: userStore.realName || undefined
    })
    ElMessage.success('盘点提交成功')
    await loadItems()
  } catch (e: any) {
    // 断网/失败 → 暂存本地离线队列（幂等键 = checkId + itemId）
    for (const s of submits) {
      enqueue(`${currentCheckId.value}-${s.id}`, { checkId: currentCheckId.value, itemId: s.id, actualQty: s.actualQty })
    }
    refreshPending()
    ElMessage.warning(`提交失败，已暂存 ${submits.length} 条待上传`)
  } finally {
    submitting.value = false
  }
}

async function handleFlush() {
  flushing.value = true
  try {
    const res = await flush(async (item) => {
      const p = item.payload
      await submitStockCheck(p.checkId, [{ id: p.itemId, actualQty: p.actualQty }], {
        operatorId: userStore.userId || undefined,
        operatorName: userStore.realName || undefined
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

function checkStatusText(s?: number): string {
  if (s === 1) return '已完成'
  if (s === 2) return '已作废'
  return '进行中'
}

onMounted(fetchChecks)
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

.check-selector {
  margin-bottom: 12px;
}

.scan-input {
  margin-bottom: 12px;
}

.progress {
  font-size: 14px;
  color: #1a73e8;
  margin-bottom: 12px;
}

.items .item-card {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: space-between;

  &.done {
    border-left: 3px solid #67c23a;
  }

  .item-name {
    font-size: 15px;
    font-weight: 600;
  }

  .item-meta {
    font-size: 12px;
    color: #909399;
    margin-top: 2px;
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