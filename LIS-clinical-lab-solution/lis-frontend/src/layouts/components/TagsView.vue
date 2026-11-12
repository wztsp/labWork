<template>
  <div class="tags-view">
    <div class="tags-view-left">
      <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
    </div>

    <div class="tags-view-wrapper">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :to="tag.path"
        class="tags-view-item"
        :class="{ active: isActive(tag) }"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        <span class="tag-dot" />
        <span class="tag-title">{{ tag.title }}</span>
        <el-icon
          v-if="!tag.affix"
          class="tag-close"
          @click.prevent.stop="closeTag(tag)"
        >
          <Close />
        </el-icon>
      </router-link>
    </div>

    <!-- 弹性间隔块：吸收剩余空间，把右侧区域推到最右边，同时不压缩中间标签区 -->
    <div class="tags-view-spacer" aria-hidden="true" />

    <div class="tags-view-right">
      <MessageBell />
      <el-dropdown @command="handleCommand">
        <span class="user-info">
          <el-icon><User /></el-icon>
          <span class="username">{{ displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 右键菜单 -->
    <ul v-show="menuVisible" class="context-menu" :style="menuStyle">
      <li @click="closeCurrent">{{ currentTag?.affix ? '首页不可关闭' : '关闭当前' }}</li>
      <li @click="closeOthers">
        <el-icon><CircleClose /></el-icon>
        关闭其他
      </li>
      <li @click="closeAll">
        <el-icon><FolderDelete /></el-icon>
        关闭所有
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Close, CircleClose, FolderDelete, Fold, Expand, User, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useTagsStore, type TagView } from '@/stores/tags'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import MessageBell from './MessageBell.vue'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()
const appStore = useAppStore()
const userStore = useUserStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const displayName = computed(() => userStore.realName || userStore.username || '用户')

const visitedViews = computed(() => tagsStore.visitedViews)

function isActive(tag: TagView) {
  return tag.path === route.path || tag.path === route.fullPath
}

function closeTag(tag: TagView) {
  // 仅移除标签, 不做路由跳转, 避免切换/重载当前页面 (关闭分页时不刷新整个系统页面)
  tagsStore.delView(tag)
}

// 右键菜单
const menuVisible = ref(false)
const menuStyle = ref({ left: '0px', top: '0px' })
const currentTag = ref<TagView | null>(null)

function openMenu(tag: TagView, e: MouseEvent) {
  currentTag.value = tag
  menuStyle.value = { left: `${e.clientX}px`, top: `${e.clientY}px` }
  menuVisible.value = true
}

function closeMenu() {
  menuVisible.value = false
}

function closeCurrent() {
  if (!currentTag.value || currentTag.value.affix) {
    menuVisible.value = false
    return
  }
  closeTag(currentTag.value)
  menuVisible.value = false
}

function closeOthers() {
  if (!currentTag.value) return
  tagsStore.delOthers(currentTag.value)
  if (!isActive(currentTag.value)) {
    router.push(currentTag.value.path)
  }
  menuVisible.value = false
}

function closeAll() {
  tagsStore.delAll()
  router.push('/')
  menuVisible.value = false
}

async function handleCommand(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch {
      // 用户取消
    }
  }
}

onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<style scoped lang="scss">
.tags-view {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 14px;
  position: relative;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 0;
  flex-wrap: nowrap;
  white-space: nowrap;
  height: 44px;
  box-sizing: border-box;

  .tags-view-left {
    display: flex;
    align-items: center;
    flex: 0 0 auto;
    flex-shrink: 0;
    white-space: nowrap;
    padding-right: 10px;
  }

  .collapse-btn {
    font-size: 18px;
    cursor: pointer;
    color: #303133;
    &:hover { color: #1a73e8; }
  }

  /* 标签区：内容驱动宽度，空间不够时水平滚动 */
  .tags-view-wrapper {
    flex: 0 1 auto;       /* 不主动扩张，按内容宽度；允许收缩 */
    min-width: 0 !important;
    display: flex;
    flex-wrap: nowrap;
    gap: 6px;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 0 8px;
    scrollbar-width: thin;
    align-items: center;
    &::-webkit-scrollbar { height: 2px; }
  }

  /* ★ 弹性间隔块：吸收剩余空间，把右侧推到最右边 */
  .tags-view-spacer {
    flex: 1 1 0%;        /* 从 0 开始增长，吸走所有剩余空间 */
    min-width: 0;
    height: 1px;         /* 不影响行高 */
  }

  .tags-view-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 0 0 auto;
    flex-shrink: 0 !important;
    flex-basis: auto;
    white-space: nowrap;
    padding-left: 10px;

    .user-info {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      color: #303133;
      outline: none;
      white-space: nowrap;
      .username { font-size: 13px; }
    }
  }

  .tags-view-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    height: 28px;
    font-size: 12px;
    color: #495060;
    background: #fff;
    border: 1px solid #d8dce5;
    border-radius: 3px;
    cursor: pointer;
    text-decoration: none;
    flex-shrink: 0;

    .tag-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #c0c4cc;
    }

    .tag-close {
      font-size: 12px;
      border-radius: 50%;
      padding: 1px;
      &:hover {
        background: #f56c6c;
        color: #fff;
      }
    }

    &:hover {
      color: #1a73e8;
      border-color: #1a73e8;
    }

    &.active {
      color: #fff;
      background: #1a73e8;
      border-color: #1a73e8;

      .tag-dot {
        background: #fff;
      }

      .tag-close:hover {
        background: #fff;
        color: #1a73e8;
      }
    }
  }

  .context-menu {
    position: fixed;
    z-index: 3000;
    margin: 0;
    padding: 5px 0;
    background: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
    list-style: none;
    min-width: 120px;

    li {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      font-size: 13px;
      color: #333;
      cursor: pointer;

      &:hover {
        background: #f5f7fa;
        color: #1a73e8;
      }
    }
  }
}
</style>