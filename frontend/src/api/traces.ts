import { api } from './client'

export interface TraceEvent {
  phase: string
  step: number
  status: string
  toolName?: string
  latencyMs: number
  outputSummary?: string
}

export interface TraceRun {
  traceId: string
  status: string
  events: TraceEvent[]
}

export const traceApi = {
  list: () => api.get<TraceRun[]>('/traces'),
  get: (traceId: string) => api.get<TraceRun>(`/traces/${traceId}`),
}
