import { request } from '@/api/request'
import type { PageResult } from '@/api/types'

/** 打印模板实体（与后端 lis_print_template 对应） */
export interface PrintTemplate {
  id?: number
  templateCode: string
  templateName: string
  bizType: string
  paperWidth: number
  paperHeight: number
  margin: number
  templateJson: string
  isDefault?: number
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 业务类型（编码 + 名称） */
export interface PrintBizType {
  code: string
  name: string
}

/** 字段字典项 */
export interface PrintField {
  field: string
  title: string
}

/** 字段字典：表头 / 明细 / 公共 */
export interface PrintFieldCatalog {
  bizType: string
  bizName: string
  header: PrintField[]
  items: PrintField[]
  meta: PrintField[]
}

/** 单据打印数据 */
export interface PrintData {
  header: Record<string, string>
  items: Record<string, string>[]
  printTime?: string
}

/** 分页查询模板 */
export function getPrintTemplatePage(params: {
  current?: number
  size?: number
  bizType?: string
  keyword?: string
  status?: number
}) {
  return request.get<PageResult<PrintTemplate>>('/reagent/print-template/page', params)
}

/** 业务类型下拉 */
export function getPrintBizTypes() {
  return request.get<PrintBizType[]>('/reagent/print-template/biz-types')
}

/** 字段字典 */
export function getPrintFieldCatalog(bizType: string) {
  return request.get<PrintFieldCatalog>('/reagent/print-template/fields', { bizType })
}

/** 启用的模板列表 */
export function getEnabledPrintTemplates(bizType?: string) {
  return request.get<PrintTemplate[]>('/reagent/print-template/list', bizType ? { bizType } : undefined)
}

/** 模板详情 */
export function getPrintTemplate(id: number) {
  return request.get<PrintTemplate>(`/reagent/print-template/${id}`)
}

/** 新增模板 */
export function createPrintTemplate(data: PrintTemplate) {
  return request.post<PrintTemplate>('/reagent/print-template', data)
}

/** 修改模板 */
export function updatePrintTemplate(data: PrintTemplate) {
  return request.put<PrintTemplate>('/reagent/print-template', data)
}

/** 删除模板 */
export function deletePrintTemplate(id: number) {
  return request.delete<void>(`/reagent/print-template/${id}`)
}

/** 设为默认模板 */
export function setDefaultPrintTemplate(id: number) {
  return request.post<void>(`/reagent/print-template/${id}/set-default`)
}

/** 取单据打印数据；报损单 bizType=SCRAP_DISPOSAL，id 传报废单（领用单）ID */
export function getPrintData(bizType: string, id: number) {
  return request.get<PrintData>(`/reagent/print-data/${bizType}/${id}`)
}
