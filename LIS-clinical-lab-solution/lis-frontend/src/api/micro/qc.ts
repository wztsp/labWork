import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// ==================== 微生物室内质控 ====================

/** 质控类别 */
export const MICRO_QC_TYPES: Record<number, string> = {
  1: '生化反应试剂',
  2: '抗菌药物',
  3: '纸片法药敏',
  4: '诊断血清',
  5: '血培养瓶',
  6: '染色液',
  7: '标准菌株'
}

/** 微生物室内质控记录 */
export interface MicroQcRecord {
  id?: number
  /** 类别: 1生化反应试剂 2抗菌药物 3纸片法药敏 4诊断血清 5血培养瓶 6染色液 7标准菌株 */
  itemType?: number
  itemCode?: string // 物品编码
  itemName?: string // 物品名称
  qcLotNo?: string // 批号/质控编号
  controlStrain?: string // 质控标准菌株
  expectedResult?: string // 预期结果
  actualResult?: string // 实际结果
  /** 判定: PASS合格 FAIL不合格 */
  result?: string
  operatorId?: number
  operatorName?: string
  measureTime?: string
  remark?: string
}

export interface MicroQcQuery extends PageQuery {
  itemType?: number
  keyword?: string
  result?: string
}

export interface MicroQcStats {
  total: number
  pass: number
  fail: number
  passRate: number
}

// 分页 (后端 GET /api/micro/qc)
export function getMicroQcPage(params: MicroQcQuery) {
  const { pageNum = 1, pageSize = 20, itemType, keyword, result } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (itemType) query.itemType = itemType
  if (keyword) query.keyword = keyword
  if (result) query.result = result
  return request.get<PageResult<MicroQcRecord>>('/micro/qc', query)
}

// 统计 (后端 GET /api/micro/qc/stats)
export function getMicroQcStats(itemType?: number) {
  const query: Record<string, any> = {}
  if (itemType) query.itemType = itemType
  return request.get<MicroQcStats>('/micro/qc/stats', query)
}

// 新增/编辑 (后端 POST/PUT /api/micro/qc)
export function saveMicroQc(data: MicroQcRecord) {
  if (data.id) {
    return request.put<MicroQcRecord>('/micro/qc', data)
  }
  return request.post<MicroQcRecord>('/micro/qc', data)
}

// 删除 (后端 DELETE /api/micro/qc/{id})
export function deleteMicroQc(id: number) {
  return request.delete<boolean>(`/micro/qc/${id}`)
}