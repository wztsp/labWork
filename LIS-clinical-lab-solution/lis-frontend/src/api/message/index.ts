import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// ==================== 消息提醒 ====================
export interface Message {
  id?: number
  messageType?: string  // CRITICAL / INFECTIOUS / TAT
  title?: string
  content?: string
  userId?: number
  departmentId?: number
  businessId?: number
  sampleId?: number
  testItemId?: number
  isRead?: number       // 0未读 1已读
  readTime?: string
  createTime?: string
}

export interface MessageQuery extends PageQuery {
  type?: string
}

export const MESSAGE_TYPE_MAP: Record<string, string> = {
  CRITICAL: '危急值',
  INFECTIOUS: '传染病',
  TAT: 'TAT超时'
}

export function getMessagePage(params: MessageQuery) {
  const { pageNum = 1, pageSize = 10, type } = params
  const query: Record<string, any> = { page: pageNum, size: pageSize }
  if (type) query.type = type
  return request.get<PageResult<Message>>('/message/page', query)
}

export function getUnreadCount() {
  return request.get<number>('/message/unread-count')
}

export function markMessageRead(id: number) {
  return request.put<boolean>(`/message/${id}/read`)
}

export function markAllMessageRead() {
  return request.put<boolean>('/message/read-all')
}

export function deleteMessage(id: number) {
  return request.delete<boolean>(`/message/${id}`)
}