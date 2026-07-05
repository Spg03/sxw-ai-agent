import { api } from './client'

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
  listCases: () => api.get<EvalCase[]>('/eval/cases'),
  createCase: (c: Omit<EvalCase, 'id'>) => api.post<EvalCase>('/eval/cases', c),
  deleteCase: (id: number) => api.delete<void>(`/eval/cases/${id}`),
  listRuns: () => api.get<EvalRun[]>('/eval/runs'),
  startRun: (name: string, caseIds?: number[]) => api.post<EvalRun>('/eval/runs', { name, caseIds }),
  getRun: (id: number) => api.get<EvalRun>(`/eval/runs/${id}`),
}
