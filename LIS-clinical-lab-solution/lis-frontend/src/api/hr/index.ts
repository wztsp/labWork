import { request, service } from '@/api/request'
import type { PageQuery, PageResult } from '@/api/types'

// ==================== 人员档案 ====================
// 字段与后端 com.lis.hr.entity.Staff 对齐: staffNo/name/groupId/education/major/signatureImage
export interface Staff {
  id: number
  staffNo: string // 员工工号
  name: string // 员工姓名
  phone?: string
  email?: string
  groupId?: number // 所属小组/科室ID
  groupName?: string // 小组名称 (前端关联展示, 后端不直接返回)
  userId?: number
  education?: string // 学历
  major?: string // 专业
  entryDate?: string // 入职日期
  leaveDate?: string // 离职日期
  idCard?: string // 身份证号
  signatureImage?: string // 电子签名 (Base64 或 URL)
  status: number // 0离职 1在职 2试用期
  createTime?: string
  updateTime?: string
}

// 员工查询参数 (后端 /api/hr/staff: current/size/keyword/groupId/status)
export interface StaffQuery extends PageQuery {
  keyword?: string
  groupId?: number
  status?: number
}

// ==================== 小组/科室 ====================
export interface LabGroup {
  id: number
  groupCode: string // 小组代码
  groupName: string // 小组名称
  parentId?: number // 父级ID
  leaderId?: number // 负责人ID
  leaderName?: string // 负责人姓名
  location?: string // 位置
  sortOrder?: number
  status?: number // 0停用 1启用
  createTime?: string
  children?: LabGroup[] // 子级小组 (树形接口返回)
}

// ==================== 排班 ====================
// 字段与后端 com.lis.hr.entity.StaffSchedule 对齐 (scheduleDate/shiftType/dutyType)
// 保留 shiftDate/startTime/endTime/remark 为可选, 以兼容已有排班页面
export interface StaffSchedule {
  id: number
  staffId: number
  staffName?: string
  scheduleDate?: string // 排班日期 (后端字段)
  shiftDate?: string // 排班日期 (历史字段, 兼容已有页面)
  shiftType?: string // 班次: 白班/夜班/休息
  dutyType?: string // 值班类型
  startTime?: string // 历史字段, 兼容已有页面
  endTime?: string // 历史字段, 兼容已有页面
  remark?: string // 历史字段, 兼容已有页面
  createTime?: string
}

// ==================== 资质证书 ====================
// 字段与后端 com.lis.hr.entity.StaffCertificate 对齐
export interface StaffCertificate {
  id: number
  staffId: number
  staffName?: string // 前端关联展示, 后端不直接返回
  certType?: string // 资质类型: PCR/HIV/输血/执业资格
  certName: string // 证书名称
  certNo?: string // 证书编号
  issueDate?: string // 颁发日期
  expiryDate?: string // 到期日期
  issueOrg?: string // 颁发机构
  attachmentUrl?: string // 附件URL
  alertDays?: number // 预警天数
  status?: number // 0过期 1有效 2作废
  createTime?: string
  attachmentCount?: number // 附件数量
}

// ==================== 资质证书附件 ====================
export interface CertificateAttachment {
  id: number
  certificateId: number
  fileName: string
  fileType?: string
  fileSize?: number
  contentType?: string
  uploaderId?: number
  uploaderName?: string
  uploadTime?: string
  remark?: string
}

// ==================== 职称字典 ====================
export interface TitleDict {
  id: number
  titleCode: string // 职称编码
  titleName: string // 职称名称
  titleLevel?: number // 职称级别
  sortOrder?: number
  status?: number // 0停用 1启用
}

// ==================== 人员职称记录 ====================
export interface StaffTitle {
  id: number
  staffId: number
  staffName?: string // 前端关联展示
  titleCode?: string
  titleName?: string
  acquireDate?: string // 取得日期
  expiryDate?: string // 到期日期
  certificateNo?: string // 证书编号
  attachmentUrl?: string
  isCurrent?: number // 是否当前职称 0否 1是
  createTime?: string
}

// ==================== 培训记录 ====================
export interface StaffTraining {
  id: number
  staffId: number
  staffName?: string // 前端关联展示
  trainingName: string // 培训主题
  trainingType?: string // 培训类型: 岗前/在岗/继续教育/外部培训
  trainingDate?: string // 培训日期
  trainer?: string // 培训人
  result?: string // 合格/不合格
  attachmentUrl?: string
  remark?: string
  createTime?: string
}

// ==================== 岗位授权 ====================
export interface StaffAuthorization {
  id: number
  staffId: number
  staffName?: string // 前端关联展示
  authType: string // 授权类型: REPORT_AUDIT/INSTRUMENT_OP/PROJECT_OP
  authScope?: string // 授权范围
  startDate?: string // 开始日期
  endDate?: string // 结束日期
  grantorId?: number // 授权人ID
  grantorName?: string // 授权人姓名
  attachmentUrl?: string
  status?: number // 0失效 1有效
  createTime?: string
}

// ==================== 文档资料 ====================
export interface LabDocument {
  id: number
  docCode?: string // 文档编号
  docName: string // 文档名称
  docType?: string // SOP/MANUAL/PROCEDURE/FORM
  version?: string // 版本
  groupId?: number // 所属小组
  groupName?: string
  fileUrl?: string // 文件URL/Base64
  uploaderId?: number
  uploadTime?: string // 上传时间
  status?: number // 0作废 1现行 2草稿
}

// 查询参数
export interface ScheduleQuery extends PageQuery {
  staffName?: string
  scheduleDate?: string
  shiftType?: string
}

export interface CertificateQuery extends PageQuery {
  staffName?: string
  certName?: string
  status?: number
}

export interface DocumentQuery extends PageQuery {
  keyword?: string
  docType?: string
  groupId?: number
}

// ==================== 员工档案 API ====================

// 分页查询员工 (后端 /api/hr/staff, 参数 current/size/keyword/groupId/status)
export function getStaffPage(params: StaffQuery) {
  const { pageNum = 1, pageSize = 10, keyword, groupId, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (groupId !== undefined) query.groupId = groupId
  if (status !== undefined) query.status = status
  return request.get<PageResult<Staff>>('/hr/staff', query)
}

// 新增/编辑员工 (根据 id 是否为 0 决定 POST/PUT)
export function saveStaff(data: Staff) {
  if (data.id && data.id !== 0) {
    return request.put<Staff>('/hr/staff', data)
  }
  return request.post<Staff>('/hr/staff', data)
}

// 删除员工
export function deleteStaff(id: number) {
  return request.delete<boolean>(`/hr/staff/${id}`)
}

// 按小组查询员工
export function listStaffByGroup(groupId: number) {
  return request.get<Staff[]>(`/hr/staff/group/${groupId}`)
}

/** 获取全部在职员工(下拉选择用, status=1) */
export function getAllActiveStaff() {
  return request.get<PageResult<Staff>>('/hr/staff', { current: 1, size: 500, status: 1 }).then((res: any) => {
    if (Array.isArray(res)) return res as Staff[]
    if (res && Array.isArray(res.records)) return res.records as Staff[]
    return []
  })
}

// ==================== 小组/科室 API ====================

export function getGroupList() {
  return request.get<LabGroup[]>('/hr/groups')
}

export function getGroupTree() {
  return request.get<LabGroup[]>('/hr/groups/tree')
}

export function saveGroup(data: LabGroup) {
  if (data.id && data.id !== 0) {
    return request.put<LabGroup>('/hr/groups', data)
  }
  return request.post<LabGroup>('/hr/groups', data)
}

export function deleteGroup(id: number) {
  return request.delete<boolean>(`/hr/groups/${id}`)
}

// ==================== 排班 API ====================

// 分页查询排班 (后端 /api/hr/schedules, 参数 current/size/staffName)
export function getSchedulePage(params: ScheduleQuery) {
  const { pageNum = 1, pageSize = 10, staffName, scheduleDate, shiftType } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (staffName) query.staffName = staffName
  if (scheduleDate) query.scheduleDate = scheduleDate
  if (shiftType) query.shiftType = shiftType
  return request.get<PageResult<StaffSchedule>>('/hr/schedules', query)
}

// 按日期区间查询排班 (排班表按周加载)
export function getScheduleRange(start: string, end: string) {
  return request.get<StaffSchedule[]>('/hr/schedules/range', { start, end })
}

// 批量指定班次: 多员工 × 多日期
export function assignScheduleBatch(data: {
  staffIds: number[]
  dates: string[]
  shiftType: string
  dutyType?: string
}) {
  return request.post<number>('/hr/schedules/batch', data)
}

export function saveSchedule(data: StaffSchedule) {
  if (data.id && data.id !== 0) {
    return request.put<boolean>('/hr/schedules', data)
  }
  return request.post<boolean>('/hr/schedules', data)
}

export function deleteSchedule(id: number) {
  return request.delete<boolean>(`/hr/schedules/${id}`)
}

// ==================== 资质证书 API ====================

// 分页查询证书 (后端 /api/hr/certificates, 参数 current/size)
export function getCertificatePage(params: CertificateQuery) {
  const { pageNum = 1, pageSize = 10, staffName, certName, status } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (staffName) query.staffName = staffName
  if (certName) query.certName = certName
  if (status !== undefined) query.status = status
  return request.get<PageResult<StaffCertificate>>('/hr/certificates', query)
}

// 查询即将到期证书 (前端预警标记用)
export function listExpiringCertificates(withinDays = 30) {
  return request.get<StaffCertificate[]>('/hr/certificates/expiring', { withinDays })
}

export function saveCertificate(data: StaffCertificate) {
  if (data.id && data.id !== 0) {
    return request.put<StaffCertificate>('/hr/certificates', data)
  }
  return request.post<StaffCertificate>('/hr/certificates', data)
}

export function deleteCertificate(id: number) {
  return request.delete<boolean>(`/hr/certificates/${id}`)
}

// ==================== 职称字典 API ====================

export function getTitleDictList() {
  return request.get<TitleDict[]>('/hr/title-dict')
}

export function saveTitleDict(data: TitleDict) {
  if (data.id && data.id !== 0) {
    return request.put<TitleDict>('/hr/title-dict', data)
  }
  return request.post<TitleDict>('/hr/title-dict', data)
}

export function deleteTitleDict(id: number) {
  return request.delete<boolean>(`/hr/title-dict/${id}`)
}

// ==================== 人员职称记录 API ====================

// 全部人员职称记录
export function getAllStaffTitles() {
  return request.get<StaffTitle[]>('/hr/titles')
}

// 某员工职称记录
export function getStaffTitles(staffId: number) {
  return request.get<StaffTitle[]>(`/hr/titles/staff/${staffId}`)
}

export function saveStaffTitle(data: StaffTitle) {
  if (data.id && data.id !== 0) {
    return request.put<StaffTitle>('/hr/titles', data)
  }
  return request.post<StaffTitle>('/hr/titles', data)
}

export function deleteStaffTitle(id: number) {
  return request.delete<boolean>(`/hr/titles/${id}`)
}

// ==================== 培训记录 API ====================

// 全部培训记录 (管理页面用)
export function getAllTrainings() {
  return request.get<StaffTraining[]>('/hr/training')
}

// 某员工培训记录
export function getStaffTrainings(staffId: number) {
  return request.get<StaffTraining[]>(`/hr/training/staff/${staffId}`)
}

export function saveTraining(data: StaffTraining) {
  if (data.id && data.id !== 0) {
    return request.put<StaffTraining>('/hr/training', data)
  }
  return request.post<StaffTraining>('/hr/training', data)
}

export function deleteTraining(id: number) {
  return request.delete<boolean>(`/hr/training/${id}`)
}

// ==================== 岗位授权 API ====================

// 全部授权记录 (管理页面用)
export function getAllAuthorizations() {
  return request.get<StaffAuthorization[]>('/hr/authorizations')
}

// 某员工授权记录
export function getStaffAuthorizations(staffId: number) {
  return request.get<StaffAuthorization[]>(`/hr/authorizations/staff/${staffId}`)
}

// 校验授权
export function checkAuthorization(staffId: number, authType: string, scope?: string) {
  const query: Record<string, any> = { staffId, authType }
  if (scope) query.scope = scope
  return request.get<boolean>('/hr/authorizations/check', query)
}

export function saveAuthorization(data: StaffAuthorization) {
  if (data.id && data.id !== 0) {
    return request.put<StaffAuthorization>('/hr/authorizations', data)
  }
  return request.post<StaffAuthorization>('/hr/authorizations', data)
}

export function deleteAuthorization(id: number) {
  return request.delete<boolean>(`/hr/authorizations/${id}`)
}

// ==================== 文档资料 API ====================

export function getDocumentPage(params: DocumentQuery) {
  const { pageNum = 1, pageSize = 10, keyword, docType, groupId } = params
  const query: Record<string, any> = { current: pageNum, size: pageSize }
  if (keyword) query.keyword = keyword
  if (docType) query.docType = docType
  if (groupId !== undefined) query.groupId = groupId
  return request.get<PageResult<LabDocument>>('/hr/documents', query)
}

export function saveDocument(data: LabDocument) {
  if (data.id && data.id !== 0) {
    return request.put<LabDocument>('/hr/documents', data)
  }
  return request.post<LabDocument>('/hr/documents', data)
}

export function abolishDocument(id: number) {
  return request.post<boolean>(`/hr/documents/${id}/abolish`)
}

export function deleteDocument(id: number) {
  return request.delete<boolean>(`/hr/documents/${id}`)
}

// 性别字典 (前端展示用, Staff 实体本身无 gender 字段, 保留以兼容历史调用)
export const GENDER_MAP: Record<number, string> = {
  1: '男',
  2: '女'
}

// 状态字典: 0离职 1在职 2试用期
export const STAFF_STATUS_MAP: Record<number, { label: string; type: 'info' | 'success' | 'warning' }> = {
  0: { label: '离职', type: 'info' },
  1: { label: '在职', type: 'success' },
  2: { label: '试用期', type: 'warning' }
}

// ==================== 资质证书附件 API ====================

// 上传附件
export function uploadCertificateAttachment(
  certificateId: number,
  file: File,
  uploaderId?: number,
  uploaderName?: string,
  remark?: string
) {
  const formData = new FormData()
  formData.append('certificateId', certificateId.toString())
  formData.append('file', file)
  if (uploaderId) formData.append('uploaderId', uploaderId.toString())
  if (uploaderName) formData.append('uploaderName', uploaderName)
  if (remark) formData.append('remark', remark)

  // 不要手动设置 multipart/form-data 的 Content-Type，
  // 否则浏览器不会自动补全 boundary 分隔符，后端将无法解析 form 字段。
  return request.post<CertificateAttachment>('/hr/certificates/attachments/upload', formData)
}

// 查询证书的附件列表
export function getCertificateAttachments(certificateId: number) {
  return request.get<CertificateAttachment[]>(`/hr/certificates/attachments/certificate/${certificateId}`)
}

// 获取附件数量
export function countCertificateAttachments(certificateId: number) {
  return request.get<number>(`/hr/certificates/attachments/count/${certificateId}`)
}

// 下载附件 (返回blob)
export function downloadCertificateAttachment(attachmentId: number) {
  return service.get(`/hr/certificates/attachments/download/${attachmentId}`, {
    responseType: 'blob'
  }).then(res => res.data)
}

// 预览附件 (返回blob)
export function previewCertificateAttachment(attachmentId: number) {
  return service.get(`/hr/certificates/attachments/preview/${attachmentId}`, {
    responseType: 'blob'
  }).then(res => res.data)
}

// 删除附件
export function deleteCertificateAttachment(attachmentId: number) {
  return request.delete<boolean>(`/hr/certificates/attachments/${attachmentId}`)
}

// 获取附件下载URL
export function getAttachmentDownloadUrl(attachmentId: number): string {
  return `${request.baseURL}/api/hr/certificates/attachments/download/${attachmentId}`
}
