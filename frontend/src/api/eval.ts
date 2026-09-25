import { api, type RequestOptions } from './client'

// Field names mirror backend records; operations use business IDs, not database IDs.
export interface EvalCase {
  caseId: string
  caseName: string
  inputPrompt: string
  expectedOutput?: string
  profileCode: string
  caseType: string
  status: string
  validationMode: string
  judgeCriteria?: string
  tags?: string[]
}
export interface EvalRun {
  runId: string
  runName: string
  status: string
  profileCode: string
  totalCases: number
  passedCases: number
  failedCases: number
  skippedCases: number
  passRate: number // Backend percentage: 0–100.
  startedAt?: string
  completedAt?: string
  durationMs?: number
  errorMessage?: string
}
export interface CreateEvalCase {
  name: string
  input: string
  expectedOutput?: string
  profileCode: string
  validationMode: string
  judgeCriteria?: string
}
export const evalApi = {
  // The backend returns full lists; the console paginates locally.
  listCases: (options?: RequestOptions) => api.get<EvalCase[]>('/eval/cases', options),
  createCase: (data: CreateEvalCase, options?: RequestOptions) => api.post<EvalCase>('/eval/cases', data, options),
  deleteCase: (caseId: string, options?: RequestOptions) => api.delete<string>('/eval/cases/' + encodeURIComponent(caseId), options),
  listRuns: (options?: RequestOptions) => api.get<EvalRun[]>('/eval/runs', options),
  setStatus: (caseId: string, action: 'activate' | 'disable') => api.post<EvalCase>('/eval/cases/' + encodeURIComponent(caseId) + '/' + action),
  startRun: (name: string, profileCode: string, caseIds: string[], options?: RequestOptions) => api.post<EvalRun>('/eval/run', { name, profileCode, caseIds }, options),
  getRun: (runId: string, options?: RequestOptions) => api.get<EvalRun>('/eval/runs/' + encodeURIComponent(runId), options),
}
