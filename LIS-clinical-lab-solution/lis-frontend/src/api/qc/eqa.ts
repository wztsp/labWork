import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

// EQA 记录 (前端展示用)
export interface EqaRecord {
  id: number
  batchNo: string
  itemName: string
  instrumentName: string
  targetValue: number
  measureValue: number
  score?: number
  grade?: string
  qualified?: number // 0不合格 1合格
}

export interface EqaStats {
  totalCount: number
  qualifiedCount: number
  passRate: number
}

export interface EqaQuery {
  pageNum?: number
  pageSize?: number
  batchNo?: string
  itemName?: string
  qualified?: number
}

// EQA 分页查询 (后端 /api/qc/eqa/page, 参数 current/size)
// 后端字段: eqaBatchNo/testItemName/instrumentName/passed, 前端字段: batchNo/itemName/instrumentName/qualified
export function getEqaPage(params: EqaQuery) {
  const { pageNum = 1, pageSize = 10, batchNo, itemName, qualified } = params
  const query: Record<string, any> = {
    current: pageNum,
    size: pageSize
  }
  if (batchNo) query.batchNo = batchNo
  if (itemName) query.itemName = itemName
  if (qualified !== undefined) query.qualified = qualified
  return request.get<PageResult<any>>('/qc/eqa/page', query).then((res) => {
    const records = (res.records || []).map((r: any) => ({
      id: r.id,
      batchNo: r.eqaBatchNo || r.batchNo || '',
      itemName: r.testItemName || r.itemName || (r.testItemId ? `指标ID:${r.testItemId}` : ''),
      instrumentName: r.instrumentName || (r.instrumentId ? `仪器ID:${r.instrumentId}` : ''),
      targetValue: Number(r.targetValue ?? 0),
      measureValue: Number(r.measureValue ?? 0),
      score: r.score != null ? Number(r.score) : undefined,
      grade: r.grade,
      qualified: r.qualified != null ? r.qualified : (r.passed === true ? 1 : r.passed === false ? 0 : undefined)
    })) as EqaRecord[]
    return {
      records,
      total: res.total,
      current: res.current,
      size: res.size
    } as PageResult<EqaRecord>
  })
}

// 提交 EQA 数据 (后端 POST /api/qc/eqa)
// 同时传入名称字段，后端会持久化名称
export function submitEqa(data: {
  batchNo: string
  itemName: string
  instrumentName: string
  targetValue: number
  measureValue: number
}) {
  const body: Record<string, any> = {
    eqaBatchNo: data.batchNo,
    testItemName: data.itemName,
    instrumentName: data.instrumentName,
    targetValue: data.targetValue,
    measureValue: data.measureValue
  }
  return request.post<boolean>('/qc/eqa', body)
}

// 批量提交 EQA 数据 (后端 POST /api/qc/eqa/batch)
// 用于手工批量生成数据，避免循环单条调用
export function batchSubmitEqa(list: Array<{
  batchNo: string
  itemName: string
  instrumentName: string
  targetValue: number
  measureValue: number
}>) {
  const body = list.map(d => ({
    eqaBatchNo: d.batchNo,
    testItemName: d.itemName,
    instrumentName: d.instrumentName,
    targetValue: d.targetValue,
    measureValue: d.measureValue
  }))
  return request.post<number>('/qc/eqa/batch', body)
}

// 计算 EQA 得分 (后端 POST /api/qc/eqa/{id}/calculate)
export function calculateEqaScore(id: number) {
  return request.post<boolean>(`/qc/eqa/${id}/calculate`)
}

// EQA 统计 (后端 /api/qc/eqa/stats)
// 后端返回 {total, passed, passedRate}, 前端期望 {totalCount, qualifiedCount, passRate}
export function getEqaStats(params?: { batchNo?: string; itemName?: string }) {
  const query: Record<string, any> = {}
  if (params?.batchNo) query.batchNo = params.batchNo
  if (params?.itemName) query.itemName = params.itemName
  return request.get<any>('/qc/eqa/stats', query).then((data) => {
    return {
      totalCount: data?.total ?? 0,
      qualifiedCount: data?.passed ?? 0,
      passRate: data?.passedRate ?? 0
    } as EqaStats
  })
}
