import { type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import {
  LayoutDashboard,
  MessageSquare,
  Heart,
  StickyNote,
  TestTube,
  Wrench,
  Activity,
  Brain,
  BookOpen,
  LogOut
} from 'lucide-react'

interface LayoutProps {
  children: ReactNode
}

const navItems = [
  { path: '/', icon: LayoutDashboard, label: '仪表盘' },
  { path: '/chat', icon: MessageSquare, label: 'AI 对话' },
  { path: '/treehole', icon: Heart, label: '树洞' },
  { path: '/notes', icon: StickyNote, label: '笔记' },
  { path: '/knowledge', icon: BookOpen, label: '知识库' },
  { path: '/hermes', icon: Brain, label: 'Hermes 复盘' },
  { path: '/eval', icon: TestTube, label: '评测' },
  { path: '/skills', icon: Wrench, label: '技能' },
  { path: '/traces', icon: Activity, label: '追踪' },
]

export default function Layout({ children }: LayoutProps) {
  const location = useLocation()
  const { user, logout } = useAuth()

  return (
    <div className="flex min-h-screen">
      {/* Sidebar */}
      <aside className="w-64 glass border-r border-white/10 flex flex-col">
        {/* Logo */}
        <div className="p-6 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-rose-400 to-amber-300 flex items-center justify-center text-slate-900 font-bold shadow-lg shadow-rose-400/20">
              ❤
            </div>
            <div>
              <div className="font-semibold text-slate-100">AgentForge</div>
              <div className="text-xs text-slate-400">AI Agent 工程化平台</div>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 p-4 space-y-1">
          {navItems.map(item => {
            const Icon = item.icon
            const active = location.pathname === item.path
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm transition-all ${
                  active
                    ? 'bg-gradient-to-r from-rose-500/20 to-amber-500/20 text-white border border-rose-500/30'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                }`}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </Link>
            )
          })}
        </nav>

        {/* User */}
        <div className="p-4 border-t border-white/10">
          <div className="flex items-center gap-3 px-4 py-2 rounded-lg bg-white/5">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-sky-400 to-indigo-500 flex items-center justify-center text-white text-sm font-semibold">
              {user?.nickname?.[0] || user?.username?.[0] || 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm text-slate-200 truncate">
                {user?.nickname || user?.username}
              </div>
              <div className="text-xs text-slate-500">{user?.role}</div>
            </div>
            <button
              onClick={logout}
              className="text-slate-400 hover:text-rose-400 transition-colors"
              title="退出登录"
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 min-w-0 overflow-auto">
        <div className={location.pathname === '/chat' ? 'h-screen animate-fade-in' : 'p-8 animate-fade-in'}>
          {children}
        </div>
      </main>
    </div>
  )
}
