const API_BASE = '/api'

interface Result<T> {
  code: number
  message: string
  data: T
}

export interface RequestOptions {
  signal?: AbortSignal
}

/**
 * API 客户端，支持 JWT access token + refresh token 自动续期。
 * <p>
 * 401 处理流程：
 * 1. 收到 401 → 尝试用 refresh token 换取新 access token
 * 2. 刷新成功 → 用新 token 重放原请求
 * 3. 刷新失败 → 清除 token，跳转登录页
 * <p>
 * 并发请求的 401 处理：使用 singleflight 模式，多个并发 401 只触发一次 refresh。
 */
class ApiClient {
  private token: string | null = null
  private refreshToken: string | null = null
  private refreshPromise: Promise<boolean> | null = null

  setToken(token: string | null) {
    this.token = token
    if (token) {
      localStorage.setItem('token', token)
    } else {
      localStorage.removeItem('token')
    }
  }

  getToken(): string | null {
    if (!this.token) {
      this.token = localStorage.getItem('token')
    }
    return this.token
  }

  setRefreshToken(refreshToken: string | null) {
    this.refreshToken = refreshToken
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken)
    } else {
      localStorage.removeItem('refreshToken')
    }
  }

  getRefreshToken(): string | null {
    if (!this.refreshToken) {
      this.refreshToken = localStorage.getItem('refreshToken')
    }
    return this.refreshToken
  }

  /**
   * 清除所有认证状态（access token + refresh token）。
   */
  clearAuth() {
    this.setToken(null)
    this.setRefreshToken(null)
  }

  private async request<T>(
    path: string,
    options: RequestInit = {},
    requestOptions: RequestOptions = {},
    isRetry = false,
  ): Promise<Result<T>> {
    const token = this.getToken()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }

    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      signal: requestOptions.signal ?? options.signal,
    })

    // Handle 401 — try refresh token before giving up
    if (response.status === 401 && !isRetry && path !== '/auth/refresh' && path !== '/auth/login') {
      const refreshed = await this.tryRefreshToken()
      if (refreshed) {
        // Retry the original request with the new token
        return this.request<T>(path, options, requestOptions, true)
      }
      // Refresh failed — clear auth and redirect to login
      this.clearAuth()
      window.location.reload()
      throw new Error('登录已过期，请重新登录')
    }

    if (!response.ok) {
      const errorText = await response.text().catch(() => '')
      throw new Error(`HTTP ${response.status}: ${response.statusText}${errorText ? ` - ${errorText}` : ''}`)
    }

    return response.json()
  }

  /**
   * Singleflight refresh：多个并发 401 只触发一次 /auth/refresh 调用。
   * @returns true 表示刷新成功，false 表示失败
   */
  private async tryRefreshToken(): Promise<boolean> {
    // 如果已有 refresh 正在进行，复用它
    if (this.refreshPromise) {
      return this.refreshPromise
    }

    const rt = this.getRefreshToken()
    if (!rt) {
      return false
    }

    this.refreshPromise = (async () => {
      try {
        const res = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: rt }),
        })

        if (!res.ok) {
          return false
        }

        const json: Result<{ token: string; refreshToken: string }> = await res.json()
        if (json.code === 0 && json.data) {
          this.setToken(json.data.token)
          this.setRefreshToken(json.data.refreshToken)
          return true
        }
        return false
      } catch {
        return false
      } finally {
        this.refreshPromise = null
      }
    })()

    return this.refreshPromise
  }

  async get<T>(path: string, options?: RequestOptions): Promise<Result<T>> {
    return this.request<T>(path, {}, options)
  }

  async post<T>(path: string, body?: any, options?: RequestOptions): Promise<Result<T>> {
    return this.request<T>(path, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }, options)
  }

  async put<T>(path: string, body?: any, options?: RequestOptions): Promise<Result<T>> {
    return this.request<T>(path, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    }, options)
  }

  async patch<T>(path: string, body?: any, options?: RequestOptions): Promise<Result<T>> {
    return this.request<T>(path, {
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    }, options)
  }

  async delete<T>(path: string, options?: RequestOptions): Promise<Result<T>> {
    return this.request<T>(path, {
      method: 'DELETE',
    }, options)
  }
}

export const api = new ApiClient()
