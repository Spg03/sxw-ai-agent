import { api, type RequestOptions } from './client'

export interface NoteEntry {
  id: number
  title: string
  content: string
  tags: string[]
  favorite: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface SaveNoteRequest {
  id?: number
  title: string
  content: string
  tags: string[]
  favorite: boolean
}

export interface NoteExport {
  filename: string
  mediaType: string
  content: string
}

export const notesApi = {
  save: (request: SaveNoteRequest, options?: RequestOptions) => api.post<NoteEntry>('/notes', request, options),
  append: (title: string, content: string, options?: RequestOptions) =>
    api.post<NoteEntry>('/notes/append', { title, content }, options),
  read: (id: number, options?: RequestOptions) => api.get<NoteEntry>(`/notes/${id}`, options),
  list: (query = '', favorite = false, options?: RequestOptions) => {
    const params = new URLSearchParams()
    if (query.trim()) params.set('query', query.trim())
    if (favorite) params.set('favorite', 'true')
    const suffix = params.size ? `?${params}` : ''
    return api.get<NoteEntry[]>(`/notes/list${suffix}`, options)
  },
  favorite: (id: number, favorite: boolean, options?: RequestOptions) =>
    api.patch<NoteEntry>(`/notes/${id}/favorite`, { favorite }, options),
  related: (id: number, options?: RequestOptions) => api.get<NoteEntry[]>(`/notes/${id}/related`, options),
  exportMarkdown: (id: number, options?: RequestOptions) => api.get<NoteExport>(`/notes/${id}/export`, options),
  delete: (id: number, options?: RequestOptions) => api.delete<void>(`/notes/${id}`, options),
}
