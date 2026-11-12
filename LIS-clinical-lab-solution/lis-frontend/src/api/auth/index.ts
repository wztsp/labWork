import request from '@/api/request'
export interface LoginParams { username: string; password: string }
export interface LoginResult { token: string; userId: number; username: string; realName: string; permissions: string[] }
export function login(data: LoginParams) { return request.post<LoginResult>('/auth/login', data) }
export function logout() { return request.post('/auth/logout') }
export function getUserInfo() { return request.get('/auth/info') }
