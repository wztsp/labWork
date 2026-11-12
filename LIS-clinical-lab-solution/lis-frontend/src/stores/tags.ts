import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'

export interface TagView {
  path: string
  fullPath: string
  title: string
  name?: string
  affix?: boolean // 固定标签,不可关闭
}

const HOME_TAG: TagView = {
  path: '/home',
  fullPath: '/home',
  title: '首页',
  name: 'Home',
  affix: true
}

const STORAGE_KEY = 'lis_visited_tags'

/** 最大可打开标签页数量(含首页) */
export const MAX_VIEWS = 10

function loadStored(): TagView[] {
  // 刷新页面后默认只加载首页, 不恢复历史标签
  return [HOME_TAG]
}

export const useTagsStore = defineStore('tags', () => {
  const visitedViews = ref<TagView[]>(loadStored())

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(visitedViews.value))
  }

  function addView(route: RouteLocationNormalizedLoaded): boolean {
    if (route.path === '/login') return true
    const title = (route.meta.title as string) || '未命名'
    const path = route.path === '/' ? '/home' : route.path
    const existing = visitedViews.value.find((t) => t.path === path)
    if (existing) {
      existing.fullPath = route.fullPath
      existing.title = title
      persist()
      return true
    }
    // 已达最大标签数: 需先删除(关闭)一个标签后才能加载新页面
    if (visitedViews.value.length >= MAX_VIEWS) {
      return false
    }
    const tag: TagView = {
      path,
      fullPath: route.fullPath,
      title,
      name: route.name ? String(route.name) : undefined,
      affix: path === '/home'
    }
    visitedViews.value.push(tag)
    persist()
    return true
  }

  function delView(tag: TagView): TagView | undefined {
    const index = visitedViews.value.findIndex((t) => t.path === tag.path)
    if (index === -1) return undefined
    visitedViews.value.splice(index, 1)
    persist()
    // 返回被删除标签后面一个标签(若存在),否则返回前一个
    if (visitedViews.value.length > index) return visitedViews.value[index]
    if (visitedViews.value.length > 0) return visitedViews.value[visitedViews.value.length - 1]
    return undefined
  }

  function delOthers(tag: TagView) {
    visitedViews.value = visitedViews.value.filter((t) => t.affix || t.path === tag.path)
    persist()
  }

  function delAll() {
    visitedViews.value = visitedViews.value.filter((t) => t.affix)
    persist()
  }

  return { visitedViews, addView, delView, delOthers, delAll }
})