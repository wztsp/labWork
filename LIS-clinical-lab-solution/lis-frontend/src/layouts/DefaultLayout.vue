<template>
  <el-container class="layout-container">
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <Sidebar />
    </el-aside>
    <el-container>
      <!-- 必须包 <el-header>：Element Plus el-container 只按顶栏识别 el-header，把它放在 router-view 上方 -->
      <el-header class="tags-header">
        <TagsView />
      </el-header>
      <el-main class="main-content">
        <!-- keep-alive 缓存各标签页组件实例，切标签时不重新挂载/不重拉接口/不刷新页面
             :key 用 path，保证同一路由共用一份缓存；不同 query 的列表页可通过切换内部状态生效，
             若后续需要同页面不同参数独立缓存，改成 $route.fullPath 即可。 -->
        <router-view v-slot="{ Component, route }">
          <keep-alive>
            <component :is="Component" :key="route.path" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import Sidebar from './components/Sidebar.vue'
import TagsView from './components/TagsView.vue'

const appStore = useAppStore()
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
</script>

<style scoped lang="scss">
.layout-container { height: 100vh; }
.sidebar { background: #002140; transition: width 0.3s; overflow: hidden; }

/* 让 el-header 完全透明，高度由子组件 TagsView 决定 */
.tags-header {
  padding: 0 !important;
  margin: 0 !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  height: auto !important;
  line-height: 1;
  overflow: visible;
}

.main-content { background: #f0f2f5; padding: 16px; overflow-y: auto; }
</style>
