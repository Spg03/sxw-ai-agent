import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { api } from '../api/client'
import { authApi, type UserView } from '../api/auth'

interface AuthContextType {
  user: UserView | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string, nickname?: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = api.getToken()
    if (token) {
      authApi.me()
        .then(res => {
          if (res.code === 0) setUser(res.data)
        })
        .catch(() => api.setToken(null))
        .finally(() => setLoading(false))
    } else {
      setLoading(false)
    }
  }, [])

  const login = async (username: string, password: string) => {
    const res = await authApi.login({ username, password })
    if (res.code === 0) {
      api.setToken(res.data.token)
      setUser(res.data.user)
    } else {
      throw new Error(res.message)
    }
  }

  const register = async (username: string, password: string, nickname?: string) => {
    const res = await authApi.register({ username, password, nickname })
    if (res.code === 0) {
      api.setToken(res.data.token)
      setUser(res.data.user)
    } else {
      throw new Error(res.message)
    }
  }

  const logout = () => {
    api.setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
