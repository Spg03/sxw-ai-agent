import { useState } from 'react'
import { useAuth } from '../contexts/AuthContext'

export default function Login() {
  const [isRegister, setIsRegister] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login, register } = useAuth()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      if (isRegister) {
        await register(username, password, nickname || undefined)
      } else {
        await login(username, password)
      }
    } catch (err: any) {
      setError(err.message || '操作失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4" style={{
      background: 'linear-gradient(135deg, #0a0e1a 0%, #1a1f3a 50%, #2a1f4a 100%)'
    }}>
      {/* Decorative blurs */}
      <div className="fixed top-20 left-20 w-96 h-96 bg-rose-500/20 rounded-full blur-3xl" />
      <div className="fixed bottom-20 right-20 w-96 h-96 bg-sky-500/20 rounded-full blur-3xl" />
      
      <div className="relative w-full max-w-md">
        <div className="glass rounded-2xl p-8 shadow-2xl">
          {/* Logo */}
          <div className="text-center mb-8">
            <div className="inline-block w-16 h-16 rounded-2xl bg-gradient-to-br from-rose-400 to-amber-300 flex items-center justify-center text-3xl shadow-lg shadow-rose-400/30 mb-4">
              ❤
            </div>
            <h1 className="text-2xl font-bold text-slate-100">
              {isRegister ? '创建账号' : '欢迎回来'}
            </h1>
            <p className="text-sm text-slate-400 mt-2">
              {isRegister ? '开始你的 AI 助手之旅' : '登录到你的 AgentForge'}
            </p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm text-slate-300 mb-2">用户名</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="输入用户名"
                required
                minLength={3}
                maxLength={64}
                className="w-full px-4 py-2.5 rounded-lg text-sm"
              />
            </div>

            {isRegister && (
              <div>
                <label className="block text-sm text-slate-300 mb-2">昵称（可选）</label>
                <input
                  type="text"
                  value={nickname}
                  onChange={e => setNickname(e.target.value)}
                  placeholder="输入昵称"
                  maxLength={64}
                  className="w-full px-4 py-2.5 rounded-lg text-sm"
                />
              </div>
            )}

            <div>
              <label className="block text-sm text-slate-300 mb-2">密码</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="输入密码"
                required
                minLength={8}
                maxLength={128}
                className="w-full px-4 py-2.5 rounded-lg text-sm"
              />
            </div>

            {error && (
              <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-sm text-rose-300">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg text-sm font-semibold text-slate-900 btn-gradient disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? '处理中...' : (isRegister ? '注册' : '登录')}
            </button>
          </form>

          {/* Toggle */}
          <div className="mt-6 text-center text-sm text-slate-400">
            {isRegister ? '已有账号？' : '没有账号？'}
            <button
              onClick={() => {
                setIsRegister(!isRegister)
                setError('')
              }}
              className="ml-2 text-rose-400 hover:text-rose-300 font-medium"
            >
              {isRegister ? '去登录' : '去注册'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
