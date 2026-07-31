import { api } from './client'

interface RequestOptions {
  signal?: AbortSignal
}

export interface EvalCase {
  id: number
  name: string
  input: string
  expectedOutput?: string
  tags?: string[]
}

export interface EvalRun {
  id: number
  name: string
  status: string
  totalCases: number
  passedCases: number
  failedCases: number
  passRate: number
  startedAt?: string
  finishedAt?: string
}

export const evalApi = {
  listCases: (options?: RequestOptions, page = 1, size = 20) =>
    api.get<EvalCase[]>(`/eval/cases?page=${page}&size=${size}`, options),
  createCase: (c: Omit<EvalCase, 'id'>, options?: RequestOptions) =>
    api.post<EvalCase>('/eval/cases', c, options),
  deleteCase: (id: number, options?: RequestOptions) =>
    api.delete<void>(`/eval/cases/${id}`, options),
  listRuns: (options?: RequestOptions, page = 1, size = 20) =>
    api.get<EvalRun[]>(`/eval/runs?page=${page}&size=${size}`, options),
  startRun: (name: string, caseIds?: number[], options?: RequestOptions) =>
    api.post<EvalRun>('/eval/runs', { name, caseIds }, options),
  getRun: (id: number, options?: RequestOptions) =>
    api.get<EvalRun>(`/eval/runs/${id}`, options),
}
