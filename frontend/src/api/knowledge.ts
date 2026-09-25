import { api, type RequestOptions } from './client'

export interface KnowledgeDocument {
  docId: string
  title: string
  sourcePath: string
  chunkCount: number
  contentHash: string
  indexFingerprint?: string
  status: string
  createdAt: string
}

export interface KnowledgeChunk {
  chunkId: string
  content: string
  similarity: number
  source: string
  documentId: string
  documentName: string
}

export interface KnowledgeSearchResult {
  chunks: KnowledgeChunk[]
  totalHits: number
  query: string
  retrievalTimeMs: number
}

export interface IngestResult {
  docId: string
  chunkCount: number
  message: string
  status: 'CREATED' | 'UPDATED' | 'REINDEXED' | 'SKIPPED' | null
}

export const knowledgeApi = {
  listDocuments: (options?: RequestOptions) =>
    api.get<KnowledgeDocument[]>('/knowledge/documents', options),
  ingestText: (title: string, content: string, sourcePath?: string, options?: RequestOptions) =>
    api.postText<IngestResult>(`/knowledge/documents/text?title=${encodeURIComponent(title)}${sourcePath ? `&sourcePath=${encodeURIComponent(sourcePath)}` : ''}`, content, options),
  uploadDocument: async (file: File, force = false) => {
    const token = api.getToken()
    const form = new FormData()
    form.append('file', file)
    form.append('force', String(force))
    const response = await fetch('/api/knowledge/documents', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    })
    if (!response.ok) throw new Error(`文档上传失败：HTTP ${response.status}`)
    return response.json() as Promise<{ code: number; message: string; data: IngestResult }>
  },
  deleteDocument: (docId: string, options?: RequestOptions) =>
    api.delete<void>(`/knowledge/documents/${docId}`, options),
  search: (query: string, topK = 5, options?: RequestOptions) =>
    api.get<KnowledgeSearchResult>(`/knowledge/search?q=${encodeURIComponent(query)}&topK=${topK}`, options),
}
