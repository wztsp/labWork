import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

/** 三方卫材对接配置 */
export interface ThirdPartyConfig {
  id?: number
  partnerCode: string
  partnerName: string
  enabled?: number
  baseUrl?: string
  authType?: number
  authHeader?: string
  authToken?: string
  appKey?: string
  appSecret?: string
  basicUsername?: string
  basicPassword?: string
  timeoutMs?: number
  retryTimes?: number
  orderPath?: string
  productPath?: string
  supplierPath?: string
  stockPath?: string
  inboundEnabled?: number
  inboundToken?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 字段映射 */
export interface ThirdPartyFieldMap {
  id?: number
  partnerCode?: string
  bizType?: string
  direction?: number
  localField: string
  remoteField: string
  defaultValue?: string
  remark?: string
}

/** 商品映射 */
export interface ThirdPartyProductMap {
  id?: number
  partnerCode?: string
  reagentId?: number
  reagentCode?: string
  reagentName?: string
  remoteCode: string
  remoteName?: string
  remoteSpec?: string
  remoteUnit?: string
  remotePrice?: number
  lastSyncTime?: string
}

/** 调用日志 */
export interface ThirdPartyLog {
  id: number
  partnerCode?: string
  bizType?: string
  direction?: number
  method?: string
  url?: string
  requestBody?: string
  responseBody?: string
  httpStatus?: number
  success?: number
  bizNo?: string
  errorMsg?: string
  costMs?: number
  operatorName?: string
  createTime?: string
}

export function getConfigPage(params: { current?: number; size?: number; keyword?: string; enabled?: number }) {
  return request.get<PageResult<ThirdPartyConfig>>('/reagent/thirdparty/config/page', params)
}

export function getConfigList() {
  return request.get<ThirdPartyConfig[]>('/reagent/thirdparty/config/list')
}

export function createConfig(data: ThirdPartyConfig) {
  return request.post<ThirdPartyConfig>('/reagent/thirdparty/config', data)
}

export function updateConfig(data: ThirdPartyConfig) {
  return request.put<ThirdPartyConfig>('/reagent/thirdparty/config', data)
}

export function deleteConfig(id: number) {
  return request.delete<void>(`/reagent/thirdparty/config/${id}`)
}

export function testConfig(id: number) {
  return request.post<string>(`/reagent/thirdparty/config/${id}/test`)
}

export function getFieldMaps(params: { partnerCode: string; bizType?: string; direction?: number }) {
  return request.get<ThirdPartyFieldMap[]>('/reagent/thirdparty/field-map', params)
}

export function saveFieldMaps(data: {
  partnerCode: string
  bizType: string
  direction: number
  maps: ThirdPartyFieldMap[]
}) {
  return request.post<void>('/reagent/thirdparty/field-map', data)
}

export function getProductMapPage(params: {
  current?: number
  size?: number
  partnerCode?: string
  keyword?: string
  mapped?: number
}) {
  return request.get<PageResult<ThirdPartyProductMap>>('/reagent/thirdparty/product-map/page', params)
}

export function saveProductMap(data: ThirdPartyProductMap) {
  return request.post<ThirdPartyProductMap>('/reagent/thirdparty/product-map', data)
}

export function deleteProductMap(id: number) {
  return request.delete<void>(`/reagent/thirdparty/product-map/${id}`)
}

export function autoMatchProductMap(partnerCode: string) {
  return request.post<number>('/reagent/thirdparty/product-map/auto-match', null, { params: { partnerCode } })
}

export function getLogPage(params: {
  current?: number
  size?: number
  partnerCode?: string
  bizType?: string
  direction?: number
  success?: number
  keyword?: string
}) {
  return request.get<PageResult<ThirdPartyLog>>('/reagent/thirdparty/log/page', params)
}

/** 推送采购订单至卫材系统 */
export function pushThirdPartyOrder(orderId: number, partnerCode: string, operatorName?: string) {
  return request.post<{ orderNo: string; remoteOrderNo?: string; response?: string }>(
    `/reagent/thirdparty/sync/order/${orderId}`,
    null,
    { params: { partnerCode, operatorName } }
  )
}

/** 拉取对方商品主数据 */
export function pullThirdPartyProducts(partnerCode: string) {
  return request.post<number>('/reagent/thirdparty/sync/products', null, { params: { partnerCode } })
}

/** 拉取对方供应商主数据 */
export function pullThirdPartySuppliers(partnerCode: string) {
  return request.post<number>('/reagent/thirdparty/sync/suppliers', null, { params: { partnerCode } })
}

/** 查询对方库存与效期 */
export function queryThirdPartyStock(partnerCode: string, reagentCode: string) {
  return request.get<Record<string, any>[]>('/reagent/thirdparty/sync/stock', { partnerCode, reagentCode })
}
