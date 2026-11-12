import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

/** HIS 接口调用日志 */
export interface HisInterfaceLog {
  id: number
  /** 服务编号 S1001~S4020 */
  serviceNo?: string
  /** 报文流水号 */
  actionNo?: string
  /** 1出站(LIS调HIS) 2入站(HIS调LIS) */
  direction?: number
  url?: string
  requestBody?: string
  responseBody?: string
  httpStatus?: number
  /** 0失败 1成功 */
  success?: number
  errorMsg?: string
  costMs?: number
  /** 关联单号：申请号/条码/医嘱号 */
  refNo?: string
  createTime?: string
}

/** 中联新标准服务名称 */
export const HIS_SERVICE_MAP: Record<string, string> = {
  S1001: '人员信息',
  S1002: '部门信息',
  S1003: '部门人员',
  S1008: '诊疗项目字典',
  S1009: '收费项目字典',
  S3001: '生成费用单',
  S3002: '删除费用单',
  S3031: '查询收费状态',
  S3103: '危急值回传',
  S3123: '批量查询医嘱',
  S4009: '申请状态回写',
  S4010: '报告回传',
  S4011: '取消报告',
  S4014: '撤销检验申请',
  S4020: '批量查询申请',
  APPLY: '检验申请接收'
}

export function getHisLogPage(params: {
  current?: number
  size?: number
  serviceNo?: string
  direction?: number
  success?: number
  keyword?: string
  beginTime?: string
  endTime?: string
}) {
  return request.get<PageResult<HisInterfaceLog>>('/his/zl/logs', params)
}
