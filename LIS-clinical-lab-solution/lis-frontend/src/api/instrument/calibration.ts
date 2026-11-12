import { request } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// ==================== 仪器校准记录 ====================

/** 仪器校准记录 */
export interface InstrumentCalibration {
  id: number
  instrumentId: number // 仪器ID
  calibrationNo: string // 校准编号
  calibrationType?: string // 校准类型: 日常/月度/季度/年度
  calibrationTime?: string // 校准时间
  nextCalibrationTime?: string // 下次校准时间
  operatorId?: number // 操作人员ID
  operatorName?: string // 操作人员姓名
  result?: string // 校准结果: PASS/FAIL
  remark?: string // 备注
  createTime?: string
}

export interface CalibrationQuery extends PageQuery {
  instrumentId?: number
}

// 校准记录分页 (后端 /api/instrument/calibration, 参数 current/size/instrumentId)
export function getCalibrationPage(params: CalibrationQuery) {
  const { pageNum = 1, pageSize = 10, instrumentId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (instrumentId) query.instrumentId = instrumentId
  return request.get<PageResult<InstrumentCalibration>>('/instrument/calibration', query)
}

// 新增/编辑校准记录 (后端 POST/PUT /api/instrument/calibration)
export function saveCalibration(data: InstrumentCalibration) {
  if (data.id) {
    return request.put<boolean>('/instrument/calibration', data)
  }
  return request.post<boolean>('/instrument/calibration', data)
}

// 删除校准记录 (后端 DELETE /api/instrument/calibration/{id})
export function deleteCalibration(id: number) {
  return request.delete<boolean>(`/instrument/calibration/${id}`)
}

// ==================== 仪器维修记录 ====================

/** 仪器维修记录 */
export interface InstrumentRepair {
  id: number
  instrumentId: number // 仪器ID
  repairNo: string // 维修编号
  faultTime?: string // 故障时间
  faultDesc?: string // 故障描述
  repairTime?: string // 维修时间(开始)
  finishTime?: string // 完成时间
  repairman?: string // 维修人
  downtimeHours?: number // 停机时长(小时)
  result?: string // 维修结果: FIXED/PENDING
  partsReplaced?: string // 更换配件
  cost?: number // 费用
  remark?: string // 备注
  createTime?: string
}

export interface RepairQuery extends PageQuery {
  instrumentId?: number
}

// 维修记录分页 (后端 /api/instrument/repair, 参数 current/size/instrumentId)
export function getRepairPage(params: RepairQuery) {
  const { pageNum = 1, pageSize = 10, instrumentId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (instrumentId) query.instrumentId = instrumentId
  return request.get<PageResult<InstrumentRepair>>('/instrument/repair', query)
}

// 新增/编辑维修记录 (后端 POST/PUT /api/instrument/repair)
export function saveRepair(data: InstrumentRepair) {
  if (data.id) {
    return request.put<boolean>('/instrument/repair', data)
  }
  return request.post<boolean>('/instrument/repair', data)
}

// 删除维修记录 (后端 DELETE /api/instrument/repair/{id})
export function deleteRepair(id: number) {
  return request.delete<boolean>(`/instrument/repair/${id}`)
}
