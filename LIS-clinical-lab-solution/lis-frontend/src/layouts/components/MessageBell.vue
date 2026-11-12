<template>
  <el-popover
    placement="bottom-end"
    :width="360"
    trigger="click"
    popper-class="message-bell-popper"
    @show="onOpen"
  >
    <template #reference>
      <div class="message-bell" @click="loadUnread">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
          <el-icon :size="20" class="bell-icon" @click="loadUnread"><Bell /></el-icon>
        </el-badge>
      </div>
    </template>

    <div class="message-panel">
      <div class="message-panel-header">
        <span class="title">消息通知</span>
        <el-button v-if="unreadCount > 0" type="primary" link size="small" @click="handleMarkAll">
          全部已读
        </el-button>
      </div>

      <div class="message-tabs">
        <el-radio-group v-model="activeType" size="small" @change="loadMessages">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="CRITICAL">危急值</el-radio-button>
          <el-radio-button value="INFECTIOUS">传染病</el-radio-button>
          <el-radio-button value="TAT">TAT超时</el-radio-button>
        </el-radio-group>
      </div>

      <el-scrollbar max-height="320px" class="message-list">
        <div v-loading="loading" class="message-empty" v-if="!loading && messages.length === 0">
          暂无消息
        </div>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="message-item"
          :class="{ unread: msg.isRead === 0 }"
          @click="handleRead(msg)"
        >
          <div class="msg-top">
            <el-tag :type="tagType(msg.messageType)" size="small">
              {{ typeLabel(msg.messageType) }}
            </el-tag>
            <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
          </div>
          <div class="msg-title">{{ msg.title }}</div>
          <div class="msg-content">{{ msg.content }}</div>
        </div>
      </el-scrollbar>

      <div class="message-panel-footer">
        <el-button type="primary" link size="small" @click="goToCenter">查看全部消息</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import {
  getMessagePage,
  getUnreadCount,
  markMessageRead,
  markAllMessageRead,
  MESSAGE_TYPE_MAP,
  type Message
} from '@/api/message'

const router = useRouter()
const unreadCount = ref(0)
const messages = ref<Message[]>([])
const loading = ref(false)
const activeType = ref('')

function typeLabel(type?: string) {
  return MESSAGE_TYPE_MAP[type || ''] || '通知'
}

function tagType(type?: string): 'danger' | 'warning' | 'info' {
  if (type === 'CRITICAL') return 'danger'
  if (type === 'INFECTIOUS') return 'warning'
  return 'info'
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

async function loadUnread() {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    // ignore
  }
}

async function loadMessages() {
  loading.value = true
  try {
    const res = await getMessagePage({ pageNum: 1, pageSize: 20, type: activeType.value || undefined })
    messages.value = res.records || []
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function onOpen() {
  loadMessages()
  loadUnread()
}

async function handleRead(msg: Message) {
  if (msg.isRead === 0) {
    await markMessageRead(msg.id!)
    msg.isRead = 1
    loadUnread()
  }
}

async function handleMarkAll() {
  await markAllMessageRead()
  messages.value.forEach(m => (m.isRead = 1))
  unreadCount.value = 0
  ElMessage.success('已全部标记为已读')
}

function goToCenter() {
  router.push('/monitor/message')
}

let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 30000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.message-bell {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 6px;
  color: #303133;

  .bell-icon {
    &:hover {
      color: #1a73e8;
    }
  }
}

.message-panel {
  .message-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .title {
      font-weight: 600;
      font-size: 14px;
    }
  }

  .message-tabs {
    margin-bottom: 8px;
  }

  .message-list {
    min-height: 60px;

    .message-empty {
      text-align: center;
      color: #909399;
      padding: 30px 0;
      font-size: 13px;
    }

    .message-item {
      padding: 10px 8px;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      border-radius: 4px;

      &:hover {
        background: #f5f7fa;
      }

      &.unread {
        background: #ecf5ff;

        .msg-title {
          font-weight: 600;
        }
      }

      .msg-top {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 4px;

        .msg-time {
          font-size: 12px;
          color: #909399;
        }
      }

      .msg-title {
        font-size: 13px;
        margin-bottom: 2px;
      }

      .msg-content {
        font-size: 12px;
        color: #606266;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
    }
  }

  .message-panel-footer {
    text-align: center;
    padding-top: 6px;
    border-top: 1px solid #f0f0f0;
    margin-top: 4px;
  }
}
</style>