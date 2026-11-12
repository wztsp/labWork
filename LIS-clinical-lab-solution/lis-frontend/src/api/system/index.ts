import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// 用户
export interface SysUser {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  departmentId?: number
  departmentName?: string
  status: number // 0停用 1启用
  lastLoginTime?: string
  createTime?: string
  roleIds?: number[]
  roleNames?: string[]
}

// 角色
export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  description?: string
  status: number
  createTime?: string
  permissionIds?: number[]
}

// 权限
export interface SysPermission {
  id: number
  parentId: number
  permCode: string
  permName: string
  permType: number // 1菜单 2按钮 3接口
  path?: string
  component?: string
  icon?: string
  sortOrder: number
  status: number
  children?: SysPermission[]
}

// 操作日志
export interface AuditLog {
  id: number
  userId?: number
  username?: string
  module?: string
  operation?: string
  method?: string
  params?: string
  result?: string
  ip?: string
  costTime?: number
  createTime: string
}

// 角色/权限简表
export interface RoleOption {
  id: number
  roleCode: string
  roleName: string
}

// 科室简表
export interface DepartmentOption {
  id: number
  parentId: number
  deptCode: string
  deptName: string
  status: number
  children?: DepartmentOption[]
}

export interface PermissionOption {
  id: number
  permCode: string
  permName: string
}

export interface UserQuery extends PageQuery {
  username?: string
  realName?: string
  departmentId?: number
  status?: number
}

export interface RoleQuery extends PageQuery {
  roleCode?: string
  roleName?: string
  status?: number
}

export interface LogQuery extends PageQuery {
  username?: string
  module?: string
  beginTime?: string
  endTime?: string
}

// 用户 (后端 /api/system/users, GET 分页参数 current/size/username/realName/status)
export function getUserList(params: UserQuery) {
  const { pageNum = 1, pageSize = 10, username, realName, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (username) query.username = username
  if (realName) query.realName = realName
  if (status !== undefined) query.status = status
  return request.get<PageResult<SysUser>>('/system/users', query)
}

export function saveUser(data: SysUser) {
  if (data.id) {
    return request.put<boolean>('/system/users', data)
  }
  return request.post<boolean>('/system/users', data)
}

export function deleteUser(id: number) {
  return request.delete<boolean>(`/system/users/${id}`)
}

export function resetPassword(id: number, newPassword: string) {
  return request.put<boolean>(`/system/users/${id}/reset-password`, { newPassword })
}

export function assignRoles(data: { userId: number; roleIds: number[] }) {
  return request.put<boolean>(`/system/users/${data.userId}/roles`, { roleIds: data.roleIds })
}

export function toggleUserStatus(id: number, status: number) {
  return request.put<boolean>(`/system/users/${id}/status`, { status })
}

// 角色 (后端 /api/system/roles)
export function getRoleList(params: RoleQuery) {
  const { pageNum = 1, pageSize = 10, roleCode, roleName, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (roleCode) query.roleCode = roleCode
  if (roleName) query.roleName = roleName
  if (status !== undefined) query.status = status
  return request.get<PageResult<SysRole>>('/system/roles', query)
}

export function getAllRoles() {
  return request.get<RoleOption[]>('/system/roles/all')
}

// 科室 (后端 /api/system/departments, 返回树形结构)
export function getAllDepartments() {
  return request.get<DepartmentOption[]>('/system/departments')
}

export function saveRole(data: SysRole) {
  if (data.id) {
    return request.put<boolean>('/system/roles', data)
  }
  return request.post<boolean>('/system/roles', data)
}

export function deleteRole(id: number) {
  return request.delete<boolean>(`/system/roles/${id}`)
}

export function assignPermissions(data: { roleId: number; permissionIds: number[] }) {
  return request.put<boolean>(`/system/roles/${data.roleId}/permissions`, { permissionIds: data.permissionIds })
}

// 权限 (后端 /api/system/permissions)
export function getPermissionTree() {
  return request.get<SysPermission[]>('/system/permissions/tree')
}

export function savePermission(data: SysPermission) {
  if (data.id) {
    return request.put<boolean>('/system/permissions', data)
  }
  return request.post<boolean>('/system/permissions', data)
}

export function deletePermission(id: number) {
  return request.delete<boolean>(`/system/permissions/${id}`)
}

// 操作日志 (后端 /api/system/logs)
export function getLogList(params: LogQuery) {
  const { pageNum = 1, pageSize = 10, username, module, beginTime, endTime } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (username) query.username = username
  if (module) query.module = module
  if (beginTime) query.beginTime = beginTime
  if (endTime) query.endTime = endTime
  return request.get<PageResult<AuditLog>>('/system/logs', query)
}

export function getLogDetail(id: number) {
  return request.get<AuditLog>(`/system/logs/${id}`)
}

// 权限类型字典
export const PERM_TYPE_MAP: Record<number, { label: string; type: 'primary' | 'success' | 'warning' }> = {
  1: { label: '菜单', type: 'primary' },
  2: { label: '按钮', type: 'success' },
  3: { label: '接口', type: 'warning' }
}

// ==================== 数据备份 ====================
// 备份文件信息 (后端返回 Map<String, Object>: fileName/size/lastModified)
export interface BackupFile {
  fileName: string
  size: number
  lastModified: string
}

// 手动备份结果 (后端返回: fileName/type/backupTime/size/success/message)
export interface BackupResult {
  fileName: string
  type: string
  backupTime: string
  size: number
  success: boolean
  message?: string
}

// 备份文件列表 (后端 /api/system/backup/list)
export function listBackups() {
  return request.get<BackupFile[]>('/system/backup/list')
}

// 手动触发全量备份 (后端 /api/system/backup/manual)
export function manualBackup() {
  return request.post<BackupResult>('/system/backup/manual')
}

// 删除备份文件 (后端 /api/system/backup/{filename})
export function deleteBackup(fileName: string) {
  return request.delete<boolean>(`/system/backup/${encodeURIComponent(fileName)}`)
}

// 下载备份文件 (返回二进制流, 绕过统一响应拦截器)
// 后端 /api/system/backup/download/{filename} 返回 ResponseEntity<Resource>
export async function downloadBackup(fileName: string): Promise<void> {
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  const url = `/api/system/backup/download/${encodeURIComponent(fileName)}`
  const resp = await fetch(url, {
    headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}
  })
  if (!resp.ok) {
    throw new Error(`下载失败: ${resp.status}`)
  }
  const blob = await resp.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}
