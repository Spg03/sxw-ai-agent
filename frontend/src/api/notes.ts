import { api } from './client'

interface RequestOptions {
  signal?: AbortSignal
}

export const notesApi = {
  create: (title: string, content: string, options?: RequestOptions) =>
    api.post<string>('/notes', { title, content }, options),
  append: (title: string, content: string, options?: RequestOptions) =>
    api.post<string>('/notes/append', { title, content }, options),
  read: (title: string, options?: RequestOptions) => api.get<string>(`/notes?title=${encodeURIComponent(title)}`, options),
  list: (options?: RequestOptions) => api.get<string>('/notes/list', options),
  search: (keyword: string, options?: RequestOptions) => api.get<string>(`/notes/search?keyword=${encodeURIComponent(keyword)}`, options),
  delete: (title: string, options?: RequestOptions) => api.delete<string>(`/notes?title=${encodeURIComponent(title)}`, options),
}
