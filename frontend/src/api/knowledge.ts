import { api, type RequestOptions } from './client'

export interface KnowledgeDocument {
  docId: string
  title: string
  sourcePath: string
  chunkCount: number
  contentHash: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface KnowledgeSearchResult {
  docId: string
  title: string
  content: string
  score: number
  chunkIndex: number
}

export interface IngestResult {
  docId: string
  title: string
  status: string
  chunkCount: number
}

export const knowledgeApi = {
  listDocuments: (options?: RequestOptions) =>
    api.get<KnowledgeDocument[]>('/knowledge/documents', options),
  ingestText: (title: string, content: string, sourcePath?: string, options?: RequestOptions) =>
    api.post<IngestResult>('/knowledge/documents/text', { title, content, sourcePath }, options),
  deleteDocument: (docId: string, options?: RequestOptions) =>
    api.delete<void>(`/knowledge/documents/${docId}`, options),
  search: (query: string, topK = 5, options?: RequestOptions) =>
    api.get<KnowledgeSearchResult[]>(`/knowledge/search?query=${encodeURIComponent(query)}&topK=${topK}`, options),
}
