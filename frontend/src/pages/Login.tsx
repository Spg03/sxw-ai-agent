import { useState, type FormEvent, type ReactNode } from 'react'
import {
  BrainCircuit, Check, Eye, EyeOff, Heart, LockKeyhole,
  MessageCircleMore, ShieldCheck, Sparkles, UserRound,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'

function BrandMark({ size = 'large' }: { size?: 'large' | 'small' }) {
  const dimensions = size === 'large' ? 'h-[84px] w-[84px]' : 'h-11 w-11'
  return (
    <div className={`relative grid ${dimensions} place-items-center`} aria-label="AgentForge">
      <div className="absolute inset-[7%] rotate-45 rounded-[30%] border-[7px] border-fuchsia-400/90 shadow-[0_0_26px_rgba(236,72,153,.45)]" />
      <div className="absolute inset-[17%] rotate-45 rounded-[34%] border-[5px] border-violet-500/90" />
      <Heart className="relative fill-rose-400 text-rose-300 drop-shadow-[0_0_12px_rgba(251,113,133,.8)]" size={size === 'large' ? 32 : 18} />
      <Sparkles className="absolute right-[12%] top-[9%] text-pink-200" size={size === 'large' ? 15 : 9} />
    </div>
  )
}

function BrandName({ compact = false }: { compact?: boolean }) {
  return <div className={compact ? 'text-2xl font-semibold tracking-tight' : 'text-5xl font-semibold tracking-[-0.06em] md:text-6xl'}>
    <span className="text-white">Agent</span><span className="bg-gradient-to-r from-rose-400 via-fuchsia-400 to-violet-400 bg-clip-text text-transparent">Forge</span>
  </div>
}

const capabilities = [
  { icon: MessageCircleMore, title: '通用助手', subtitle: '工作、学习与知识处理', description: '帮你写作、整理、分析、总结，提升效率，探索知识边界。', color: 'from-violet-500/30 to-fuchsia-500/10' },
  { icon: Heart, title: '情感伙伴', subtitle: '倾听、陪伴与情绪复盘', description: '在这里倾诉想法、释放情绪，和你一起回顾、理解、成长。', color: 'from-rose-500/30 to-orange-500/10' },
]

const highlights = [
  { icon: BrainCircuit, label: '长期记忆' },
  { icon: ShieldCheck, label: '安全可控' },
  { icon: Sparkles, label: '智能协作' },
]

export default function Login() {
  const [isRegister, setIsRegister] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login, register } = useAuth()

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      if (isRegister) await register(username, password, nickname || undefined)
      else await login(username, password)
      if (!remember) sessionStorage.setItem('agentforge-session-only', 'true')
    } catch (err: any) {
      setError(err.message || '操作失败，请稍后再试')
    } finally {
      setLoading(false)
    }
  }

  const toggleMode = () => {
    setIsRegister(value => !value)
    setError('')
  }

  return (
    <div className="login-page">
      <div className="login-backdrop" />
      <div className="login-orb" />
      <div className="login-horizon" />
      <div className="login-star login-star-one" />
      <div className="login-star login-star-two" />

      <div className="login-layout">
        <section className="login-hero">
          <div className="login-brand"><BrandMark /><div><BrandName /><p>AI Agent 工程化平台</p></div></div>
          <div className="login-hero-copy">
            <p className="text-4xl font-semibold tracking-tight text-white xl:text-5xl">你的 AI 助手工作台</p>
            <p className="mt-5 text-lg leading-8 text-slate-400">与你的专属 AI 助手协作，专注工作与成长，陪伴你每一步。</p>
          </div>
          <div className="login-capabilities">
            {capabilities.map(({ icon: Icon, title, subtitle, description, color }) => <div key={title} className={`login-feature-card bg-gradient-to-br ${color}`}><div className="login-feature-head"><div className="login-feature-icon"><Icon size={29} /></div><div><h2>{title}</h2><p>{subtitle}</p></div></div><p className="login-feature-description">{description}</p></div>)}
          </div>
          <div className="login-highlights">
            {highlights.map(({ icon: Icon, label }) => <div key={label} className="flex items-center gap-2 text-sm text-slate-300"><Icon size={17} className="text-fuchsia-300" />{label}</div>)}
          </div>
        </section>

        <section className="login-panel">
          <div className="mb-8 text-center"><div className="login-panel-mark"><BrandMark size="small" /></div><h1 className="text-3xl font-bold tracking-tight">{isRegister ? '创建账号' : '欢迎回来'}</h1><p className="mt-3 text-sm text-slate-400">{isRegister ? '开启你的 AgentForge 协作空间' : '登录到你的 AgentForge'}</p></div>
          <form onSubmit={handleSubmit} className="space-y-5">
            <Field label="用户名" icon={UserRound}><input value={username} onChange={event => setUsername(event.target.value)} placeholder="请输入用户名或邮箱" required minLength={3} maxLength={64} autoComplete="username" className="auth-input" /></Field>
            {isRegister && <Field label="昵称（可选）" icon={Sparkles}><input value={nickname} onChange={event => setNickname(event.target.value)} placeholder="你希望我们如何称呼你" maxLength={64} className="auth-input" /></Field>}
            <Field label="密码" icon={LockKeyhole}><input value={password} onChange={event => setPassword(event.target.value)} type={showPassword ? 'text' : 'password'} placeholder="请输入密码" required minLength={8} maxLength={128} autoComplete={isRegister ? 'new-password' : 'current-password'} className="auth-input pr-12" /><button type="button" onClick={() => setShowPassword(value => !value)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300" aria-label={showPassword ? '隐藏密码' : '显示密码'}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button></Field>
            {!isRegister && <div className="flex items-center justify-between text-sm"><label className="flex cursor-pointer items-center gap-2 text-slate-400"><input type="checkbox" checked={remember} onChange={event => setRemember(event.target.checked)} className="sr-only" /><span className={`grid h-5 w-5 place-items-center rounded border ${remember ? 'border-fuchsia-400 bg-fuchsia-500' : 'border-slate-500 bg-transparent'}`}>{remember && <Check size={14} />}</span>记住我</label><button type="button" onClick={() => setError('密码找回功能暂未开放，请联系管理员。')} className="text-fuchsia-300 hover:text-fuchsia-200">忘记密码？</button></div>}
            {error && <div role="alert" className="rounded-xl border border-rose-400/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-200">{error}</div>}
            <button type="submit" disabled={loading} className="w-full rounded-xl bg-gradient-to-r from-violet-600 via-fuchsia-500 to-orange-400 py-3.5 text-sm font-semibold text-white shadow-lg shadow-fuchsia-500/20 transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60">{loading ? '正在处理…' : isRegister ? '创建账号' : '登录'}</button>
          </form>
          <div className="mt-7 text-center text-sm text-slate-400">{isRegister ? '已有账号？' : '没有账号？'}<button onClick={toggleMode} className="ml-2 font-medium text-fuchsia-300 hover:text-fuchsia-200">{isRegister ? '去登录' : '去注册'}</button></div>
          {!isRegister && <><div className="login-divider"><span>或使用其他方式登录</span></div><div className="login-socials"><button type="button" onClick={() => setError('GitHub 登录暂未配置。')}><span className="font-bold">●</span> GitHub 登录</button><button type="button" onClick={() => setError('邮箱快捷登录暂未配置。')}><span>✉</span> 邮箱登录</button></div></>}
          <p className="login-terms"><ShieldCheck className="mr-1 inline-block" size={14} />继续即表示你同意平台的 <span>服务条款</span> 和 <span>隐私政策</span></p>
        </section>
      </div>
    </div>
  )
}

function Field({ label, icon: Icon, children }: { label: string; icon: LucideIcon; children: ReactNode }) {
  return <label className="block"><span className="mb-2 block text-sm font-medium text-slate-200">{label}</span><span className="relative block"><Icon size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" />{children}</span></label>
}
