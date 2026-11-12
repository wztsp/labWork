import request from '@/api/request'
export function getConfigList(params: any) { return request.get('/system/configs', { params }) }
export function getConfigByKey(key: string) { return request.get(`/system/configs/${key}`) }
export function createConfig(data: any) { return request.post('/system/configs', data) }
export function updateConfig(id: number, data: any) { return request.put(`/system/configs/${id}`, data) }
export function deleteConfig(id: number) { return request.delete(`/system/configs/${id}`) }
