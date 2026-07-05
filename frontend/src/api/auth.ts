import { api } from './client'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface UserView {
  id: number
  username: string
  nickname: string
  role: string
}

export interface AuthResponse {
  token: string
  user: UserView
}

export const authApi = {
  login: (req: LoginRequest) => api.post<AuthResponse>('/auth/login', req),
  register: (req: RegisterRequest) => api.post<AuthResponse>('/auth/register', req),
  me: () => api.get<UserView>('/auth/me'),
}
