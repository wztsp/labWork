import dayjs from 'dayjs'

// 格式化为 yyyy-MM-dd HH:mm:ss
export function formatDateTime(value?: string | number | Date | null): string {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

// 格式化为 yyyy-MM-dd
export function formatDate(value?: string | number | Date | null): string {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD')
}

// 格式化为 HH:mm:ss
export function formatTime(value?: string | number | Date | null): string {
  if (!value) return '-'
  return dayjs(value).format('HH:mm:ss')
}

// 当前时间字符串
export function nowDateTime(): string {
  return dayjs().format('YYYY-MM-DD HH:mm:ss')
}

export { dayjs }
