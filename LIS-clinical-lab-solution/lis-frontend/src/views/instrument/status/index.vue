<template>
  <div class="instrument-status-page">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <span>仪器状态监控</span>
          <div>
            <el-button type="primary" link size="small" @click="fetchList">手动刷新</el-button>
            <el-switch
              v-model="autoRefresh"
              active-text="自动刷新(10s)"
              inline-prompt
              style="margin-left: 12px"
            />
          </div>
        </div>
      </template>

      <!-- 统计概览 -->
      <div class="summary-bar">
        <div class="summary-item">
          <span class="summary-label">仪器总数</span>
          <span class="summary-value">{{ instrumentList.length }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">在线</span>
          <span class="summary-value color-success">{{ onlineCount }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">离线</span>
          <span class="summary-value color-info">{{ offlineCount }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">维护中</span>
          <span class="summary-value color-warning">{{ maintenanceCount }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">故障中</span>
          <span class="summary-value color-danger">{{ faultCount }}</span>
        </div>
      </div>

      <div v-loading="loading" class="cards-wrapper">
        <el-row :gutter="16">
          <el-col v-for="item in instrumentList" :key="item.id" :span="6">
            <el-card
              class="instrument-card"
              :class="cardClass(item)"
              shadow="hover"
            >
              <!-- 仪器名称 + 状态指示灯 -->
              <div class="card-header">
                <div class="title">
                  <span class="name">{{ item.instrumentName }}</span>
                  <el-tag
                    v-if="item.workStatus && item.workStatus !== 1"
                    :type="WORK_STATUS_TAG_TYPE[item.workStatus] || 'info'"
                    size="small"
                    effect="dark"
                    class="work-status-tag"
                  >
                    {{ WORK_STATUS_MAP[item.workStatus] || '未知' }}
                  </el-tag>
                </div>
                <div class="status-light">
                  <span class="dot" :class="item.online ? 'dot-online' : 'dot-offline'"></span>
                  <span :class="item.online ? 'text-online' : 'text-offline'">
                    {{ item.online ? '在线' : '离线' }}
                  </span>
                </div>
              </div>

              <!-- 仪器信息 -->
              <div class="card-body">
                <div class="info-row">
                  <span class="label">品牌型号:</span>
                  <span class="value">{{ item.brand || '-' }} {{ item.model || '' }}</span>
                </div>
                <div class="info-row">
                  <span class="label">使用科室:</span>
                  <span class="value">{{ item.department || '-' }}</span>
                </div>
                <div class="info-row">
                  <span class="label">协议类型:</span>
                  <span class="value">{{ item.protocolType }}</span>
                </div>
                <div class="info-row">
                  <span class="label">连接方式:</span>
                  <span class="value">
                    {{ CONNECT_TYPE_MAP[item.connectType] || item.connectType }}
                    <el-tag
                      v-if="item.channelType === 'TCP_SERIAL'"
                      size="small"
                      type="primary"
                      effect="plain"
                      class="channel-tag"
                    >双通道</el-tag>
                  </span>
                </div>
                <div class="info-row">
                  <span class="label">地址:</span>
                  <span class="value">
                    <template v-if="item.connectType === 'TCP'">
                      {{ item.ipAddress }}:{{ item.port }}
                    </template>
                    <template v-else-if="item.connectType === 'SERIAL'">
                      {{ item.serialPort }}@{{ item.baudRate }}
                    </template>
                    <template v-else>-</template>
                  </span>
                </div>
                <div class="info-row">
                  <span class="label">保修到期:</span>
                  <span class="value" :class="{ 'warranty-warn': isWarrantyExpiringSoon(item.warrantyExpiry) }">
                    {{ formatDate(item.warrantyExpiry) }}
                    <el-tag
                      v-if="isWarrantyExpired(item.warrantyExpiry)"
                      size="small"
                      type="danger"
                      effect="dark"
                      class="warranty-tag"
                    >已过期</el-tag>
                    <el-tag
                      v-else-if="isWarrantyExpiringSoon(item.warrantyExpiry)"
                      size="small"
                      type="warning"
                      effect="dark"
                      class="warranty-tag"
                    >即将到期</el-tag>
                  </span>
                </div>
                <div class="info-row">
                  <span class="label">最后在线:</span>
                  <span class="value">{{ formatDateTime(item.lastOnlineTime) }}</span>
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="card-footer">
                <el-button
                  v-if="!item.online"
                  type="primary"
                  size="small"
                  :loading="actionLoading === item.id"
                  :disabled="item.workStatus === 3"
                  @click="handleConnect(item)"
                >连接</el-button>
                <el-button
                  v-else
                  type="danger"
                  size="small"
                  :loading="actionLoading === item.id"
                  @click="handleDisconnect(item)"
                >断开</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-if="!instrumentList.length && !loading" description="暂无仪器" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getInstrumentStatusList,
  connectInstrument,
  disconnectInstrument,
  CONNECT_TYPE_MAP,
  WORK_STATUS_MAP,
  WORK_STATUS_TAG_TYPE,
  type InstrumentStatus
} from '@/api/instrument'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const autoRefresh = ref(true)
const actionLoading = ref<number | null>(null)
const instrumentList = ref<InstrumentStatus[]>([])
let timer: number | null = null

// 统计概览
const onlineCount = computed(() => instrumentList.value.filter((i) => i.online).length)
const offlineCount = computed(() => instrumentList.value.filter((i) => !i.online).length)
const maintenanceCount = computed(() => instrumentList.value.filter((i) => i.workStatus === 2).length)
const faultCount = computed(() => instrumentList.value.filter((i) => i.workStatus === 3).length)

// 卡片样式: 故障中 > 维护中 > 离线 > 在线
function cardClass(item: InstrumentStatus): Record<string, boolean> {
  if (item.workStatus === 3) return { 'card-fault': true }
  if (item.workStatus === 2) return { 'card-maintenance': true }
  if (item.online) return { online: true }
  return { offline: true }
}

function formatDate(date?: string): string {
  return date ? dayjs(date).format('YYYY-MM-DD') : '-'
}

// 保修已过期
function isWarrantyExpired(date?: string): boolean {
  if (!date) return false
  return dayjs(date).isBefore(dayjs(), 'day')
}

// 保修30天内到期
function isWarrantyExpiringSoon(date?: string): boolean {
  if (!date) return false
  const expiry = dayjs(date)
  if (expiry.isBefore(dayjs(), 'day')) return false
  return expiry.isBefore(dayjs().add(30, 'day'), 'day')
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getInstrumentStatusList()
    instrumentList.value = res || []
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleConnect(item: InstrumentStatus) {
  if (item.workStatus === 3) {
    ElMessage.warning(`仪器 ${item.instrumentName} 处于故障中, 暂不可连接`)
    return
  }
  actionLoading.value = item.id
  try {
    await connectInstrument(item.id)
    ElMessage.success(`仪器 ${item.instrumentName} 连接成功`)
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    actionLoading.value = null
  }
}

async function handleDisconnect(item: InstrumentStatus) {
  actionLoading.value = item.id
  try {
    await disconnectInstrument(item.id)
    ElMessage.success(`仪器 ${item.instrumentName} 已断开`)
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    actionLoading.value = null
  }
}

function startTimer() {
  stopTimer()
  if (autoRefresh.value) {
    timer = window.setInterval(() => {
      fetchList()
    }, 10000)
  }
}

function stopTimer() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (val) => {
  if (val) startTimer()
  else stopTimer()
})

onMounted(() => {
  fetchList()
  startTimer()
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped lang="scss">
.instrument-status-page {
  padding: 16px;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .summary-bar {
    display: flex;
    gap: 32px;
    padding: 12px 16px;
    margin-bottom: 16px;
    background-color: var(--el-fill-color-light);
    border-radius: 4px;

    .summary-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .summary-label {
        font-size: 13px;
        color: var(--el-text-color-secondary);
      }

      .summary-value {
        font-size: 20px;
        font-weight: bold;
        color: var(--el-text-color-primary);
      }

      .color-success {
        color: var(--el-color-success);
      }

      .color-info {
        color: var(--el-text-color-secondary);
      }

      .color-warning {
        color: var(--el-color-warning);
      }

      .color-danger {
        color: var(--el-color-danger);
      }
    }
  }

  .cards-wrapper {
    min-height: 200px;
  }

  .instrument-card {
    margin-bottom: 16px;
    border-left: 4px solid var(--el-color-info);

    &.online {
      border-left-color: var(--el-color-success);
    }

    &.offline {
      border-left-color: var(--el-color-info);
    }

    &.card-maintenance {
      border-left-color: var(--el-color-warning);
      background-color: var(--el-color-warning-light-9);
    }

    &.card-fault {
      border-left-color: var(--el-color-danger);
      background-color: var(--el-color-danger-light-9);
    }

    :deep(.el-card__body) {
      padding: 16px;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 12px;

      .title {
        display: flex;
        flex-direction: column;
        gap: 6px;

        .name {
          font-size: 16px;
          font-weight: bold;
          color: var(--el-text-color-primary);
        }

        .work-status-tag {
          align-self: flex-start;
        }
      }

      .status-light {
        display: flex;
        align-items: center;
        gap: 6px;

        .dot {
          display: inline-block;
          width: 10px;
          height: 10px;
          border-radius: 50%;

          &.dot-online {
            background-color: var(--el-color-success);
            box-shadow: 0 0 6px var(--el-color-success);
          }

          &.dot-offline {
            background-color: var(--el-color-info);
          }
        }

        .text-online {
          color: var(--el-color-success);
          font-size: 13px;
        }

        .text-offline {
          color: var(--el-text-color-secondary);
          font-size: 13px;
        }
      }
    }

    .card-body {
      .info-row {
        display: flex;
        margin-bottom: 8px;
        font-size: 13px;

        .label {
          width: 80px;
          color: var(--el-text-color-secondary);
        }

        .value {
          flex: 1;
          color: var(--el-text-color-primary);
          word-break: break-all;

          .channel-tag {
            margin-left: 6px;
          }

          .warranty-tag {
            margin-left: 6px;
          }

          &.warranty-warn {
            color: var(--el-color-warning);
          }
        }
      }
    }

    .card-footer {
      margin-top: 12px;
      text-align: right;
    }
  }
}
</style>
