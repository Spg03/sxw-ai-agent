export function formatDate(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export function resultData<T>(result: { code: number; message: string; data: T }): T {
  if (result.code !== 0) throw new Error(result.message || '操作未成功，请稍后重试')
  return result.data
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请检查网络后重试'
}
