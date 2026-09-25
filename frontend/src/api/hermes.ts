import { api, type RequestOptions } from './client'

export interface HermesCandidate {
  candidateId: string
  runId: string
  chatId: string
  type: string
  title: string
  content: string
  metadata: string | null
  status: string
  reviewedBy: string | null
  createdAt: string
  reviewedAt: string | null
  sourceTraceId: string | null
  confidence: number
}

export const hermesApi = {
  /** 获取待审核候选列表（后端仅返回 PENDING） */
  listPending: (options?: RequestOptions) =>
    api.get<HermesCandidate[]>('/hermes/candidates', options),
  /** 获取应用失败候选列表 */
  listFailed: (options?: RequestOptions) =>
    api.get<HermesCandidate[]>('/hermes/candidates/failed', options),
  /** 获取单个候选详情 */
  get: (candidateId: string, options?: RequestOptions) =>
    api.get<HermesCandidate>(`/hermes/candidates/${candidateId}`, options),
  /** 审核身份由后端从登录信息中获取。 */
  approve: (candidateId: string, options?: RequestOptions) =>
    api.post<string>(`/hermes/candidates/${encodeURIComponent(candidateId)}/approve`, undefined, options),
  reject: (candidateId: string, options?: RequestOptions) =>
    api.post<string>(`/hermes/candidates/${encodeURIComponent(candidateId)}/reject`, undefined, options),
  retry: (candidateId: string, options?: RequestOptions) =>
    api.post<string>(`/hermes/candidates/${encodeURIComponent(candidateId)}/retry`, undefined, options),
}
