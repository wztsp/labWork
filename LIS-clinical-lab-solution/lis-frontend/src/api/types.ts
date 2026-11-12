export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
  traceId?: string
}

// 分页查询基础参数
export interface PageQuery {
  pageNum: number
  pageSize: number
}

// 分页返回结果
export interface PageResult<T = any> {
  records: T[]
  total: number
  current: number
  size: number
}
