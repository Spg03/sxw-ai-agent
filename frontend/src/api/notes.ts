import { api } from './client'

export const notesApi = {
  create: (title: string, content: string) =>
    api.post<string>('/notes', { title, content }),
  append: (title: string, content: string) =>
    api.post<string>('/notes/append', { title, content }),
  read: (title: string) => api.get<string>(`/notes?title=${encodeURIComponent(title)}`),
  list: () => api.get<string>('/notes/list'),
  search: (keyword: string) => api.get<string>(`/notes/search?keyword=${encodeURIComponent(keyword)}`),
  delete: (title: string) => api.delete<string>(`/notes?title=${encodeURIComponent(title)}`),
}
