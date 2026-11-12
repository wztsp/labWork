import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getUserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('lis_token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const username = ref('')
  const realName = ref('')
  const permissions = ref<string[]>([])
  const infoLoaded = ref(false)

  async function login(loginUsername: string, loginPassword: string) {
    const res = await loginApi({ username: loginUsername, password: loginPassword })
    token.value = res.token
    userId.value = res.userId
    username.value = res.username
    realName.value = res.realName
    permissions.value = res.permissions
    localStorage.setItem('lis_token', res.token)
    localStorage.setItem('userId', String(res.userId))
  }

  async function fetchUserInfo() {
    const res = await getUserInfo()
    if (res) {
      userId.value = res.userId
      username.value = res.username
      realName.value = res.realName
      permissions.value = res.permissions || []
      localStorage.setItem('userId', String(res.userId))
      infoLoaded.value = true
    }
  }

  // 确保已加载用户信息与权限 (刷新/冷启动后调用, 幂等)
  async function ensureInfo() {
    if (infoLoaded.value || !token.value) return
    try {
      await fetchUserInfo()
    } catch (e) {
      infoLoaded.value = true
    }
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    realName.value = ''
    permissions.value = []
    infoLoaded.value = false
    localStorage.removeItem('lis_token')
    localStorage.removeItem('userId')
  }

  function hasPermission(perm: string) {
    return permissions.value.includes(perm) || permissions.value.includes('*')
  }

  // 判断是否拥有多个权限码中的任意一个 (用于菜单按权限显隐)
  function hasAnyPermission(perms: string[]) {
    return perms.some((p) => permissions.value.includes(p) || permissions.value.includes('*'))
  }

  return { token, userId, username, realName, permissions, login, fetchUserInfo, ensureInfo, logout, hasPermission, hasAnyPermission }
})
