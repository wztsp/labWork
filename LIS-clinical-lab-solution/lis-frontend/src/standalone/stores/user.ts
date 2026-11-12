import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 试剂独立版用户上下文
 * 说明：独立部署不接入 LIS 统一认证，使用本地"操作员"身份，
 * 仅用于业务单据的 operatorId / operatorName 记录。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('reagent_token') || 'standalone')
  const userId = ref(Number(localStorage.getItem('reagent_operator_id')) || 1)
  const username = ref(localStorage.getItem('reagent_operator_name') || 'admin')
  const realName = ref(localStorage.getItem('reagent_operator_name') || '管理员')
  const permissions = ref<string[]>(['*'])
  const infoLoaded = ref(true)

  function setOperator(name: string, id?: number) {
    realName.value = name
    username.value = name
    if (id) userId.value = id
    localStorage.setItem('reagent_operator_name', name)
    localStorage.setItem('reagent_operator_id', String(userId.value))
  }

  function logout() {
    token.value = ''
    localStorage.removeItem('reagent_token')
    localStorage.removeItem('reagent_operator_name')
    localStorage.removeItem('reagent_operator_id')
    window.location.reload()
  }

  function hasPermission(_perm: string) {
    return true
  }

  function hasAnyPermission(_perms: string[]) {
    return true
  }

  return { token, userId, username, realName, permissions, infoLoaded, setOperator, logout, hasPermission, hasAnyPermission }
})
