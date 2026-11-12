import request from '@/api/request'
export function getDepartmentTree() { return request.get('/system/departments') }
export function createDepartment(data: any) { return request.post('/system/departments', data) }
export function updateDepartment(id: number, data: any) { return request.put(`/system/departments/${id}`, data) }
export function deleteDepartment(id: number) { return request.delete(`/system/departments/${id}`) }
