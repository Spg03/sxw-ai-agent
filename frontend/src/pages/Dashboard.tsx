import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { dashboardApi, type DashboardStats } from '../api/dashboard'
import { notesApi } from '../api/notes'
import { treeholeApi } from '../api/treehole'
import { evalApi } from '../api/eval'
import { MessageSquare, Heart, StickyNote, TestTube, Activity, TrendingUp } from 'lucide-react'

export default function Dashboard() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [recentActivities, setRecentActivities] = useState<Array<{ type: string; text: string; time: string }>>([])
  const [loading, setLoading] = useState(true)
  const [apiStatus, setApiStatus] = useState<'checking' | 'up' | 'down'>('checking')

  useEffect(() => {
    const controller = new AbortController()
    loadData(controller.signal)
    return () => controller.abort()
  }, [])

  const loadData = async (signal?: AbortSignal) => {
    try {
      const [statsRes, notesRes, treeholeRes, evalRes] = await Promise.all([
        dashboardApi.getStats({ signal }),
        notesApi.list({ signal }),
        treeholeApi.list({ signal }),
        evalApi.listRuns({ signal }),
      ])

      if (statsRes.code === 0) {
        setStats(statsRes.data)
      }

      // 构建最近活动
      const activities: Array<{ type: string; text: string; time: string }> = []
      
      if (treeholeRes.code === 0 && treeholeRes.data.length > 0) {
        const latest = treeholeRes.data[0]
        activities.push({
          type: 'treehole',
          text: `创建了新树洞「${latest.title}」`,
          time: formatTime(latest.createdAt),
        })
      }

      if (notesRes.code === 0 && notesRes.data) {
        const notes = notesRes.data.split('\n').filter(Boolean)
        if (notes.length > 0) {
          activities.push({
            type: 'note',
            text: `笔记「${notes[0]}」已更新`,
            time: '最近更新',
          })
        }
      }

      if (evalRes.code === 0 && evalRes.data.length > 0) {
        const latest = evalRes.data[0]
        activities.push({
          type: 'eval',
          text: `运行了评测「${latest.name}」`,
          time: latest.startedAt ? formatTime(latest.startedAt) : '最近',
        })
      }

      setRecentActivities(activities.slice(0, 5))

      // 真实健康检查
      dashboardApi.healthCheck({ signal })
        .then(() => setApiStatus('up'))
        .catch(() => setApiStatus('down'))
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      console.error('Failed to load dashboard data', err)
    } finally {
      setLoading(false)
    }
  }

  const formatTime = (dateStr: string) => {
    const date = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)
    const diffDays = Math.floor(diffMs / 86400000)

    if (diffMins < 1) return '刚刚'
    if (diffMins < 60) return `${diffMins}分钟前`
    if (diffHours < 24) return `${diffHours}小时前`
    if (diffDays < 7) return `${diffDays}天前`
    return date.toLocaleDateString('zh-CN')
  }

  const statsDisplay = [
    { 
      label: '对话次数', 
      value: stats?.chatCount ?? '-', 
      icon: MessageSquare, 
      color: 'from-rose-500 to-pink-500' 
    },
    { 
      label: '树洞记录', 
      value: stats?.treeholeCount ?? '-', 
      icon: Heart, 
      color: 'from-amber-500 to-orange-500' 
    },
    { 
      label: '笔记数量', 
      value: stats?.noteCount ?? '-', 
      icon: StickyNote, 
      color: 'from-sky-500 to-blue-500' 
    },
    { 
      label: '评测运行', 
      value: stats?.evalRunCount ?? '-', 
      icon: TestTube, 
      color: 'from-emerald-500 to-teal-500' 
    },
  ]

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-slate-400">加载中...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-slate-100">
          欢迎回来，{user?.nickname || user?.username} 👋
        </h1>
        <p className="text-slate-400 mt-2">
          这是你的 AI 助手控制台，管理你的对话、笔记和评测。
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {statsDisplay.map(stat => {
          const Icon = stat.icon
          return (
            <div
              key={stat.label}
              className="glass rounded-xl p-6 hover:border-rose-500/30 transition-all hover:-translate-y-1"
            >
              <div className="flex items-center justify-between mb-4">
                <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${stat.color} flex items-center justify-center shadow-lg`}>
                  <Icon size={24} className="text-white" />
                </div>
                <TrendingUp size={20} className="text-emerald-400" />
              </div>
              <div className="text-3xl font-bold text-slate-100">{stat.value}</div>
              <div className="text-sm text-slate-400 mt-1">{stat.label}</div>
            </div>
          )
        })}
      </div>

      {/* Two Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Activities */}
        <div className="glass rounded-xl p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-slate-100">最近活动</h2>
            <Activity size={20} className="text-slate-400" />
          </div>
          <div className="space-y-3">
            {recentActivities.length === 0 ? (
              <div className="text-center py-8 text-slate-400 text-sm">暂无活动记录</div>
            ) : (
              recentActivities.map((activity, idx) => (
                <div
                  key={idx}
                  className="flex items-start gap-3 p-3 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
                >
                  <div className="w-2 h-2 rounded-full bg-rose-400 mt-2 pulse-dot" />
                  <div className="flex-1">
                    <div className="text-sm text-slate-200">{activity.text}</div>
                    <div className="text-xs text-slate-500 mt-1">{activity.time}</div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="glass rounded-xl p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-slate-100">快速操作</h2>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <button 
              onClick={() => navigate('/chat')}
              className="p-4 rounded-lg bg-gradient-to-br from-rose-500/20 to-pink-500/20 border border-rose-500/30 hover:border-rose-500/50 transition-all text-left"
            >
              <MessageSquare className="text-rose-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">开始对话</div>
              <div className="text-xs text-slate-400 mt-1">与 AI 助手聊天</div>
            </button>
            <button 
              onClick={() => navigate('/treehole')}
              className="p-4 rounded-lg bg-gradient-to-br from-amber-500/20 to-orange-500/20 border border-amber-500/30 hover:border-amber-500/50 transition-all text-left"
            >
              <Heart className="text-amber-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">写树洞</div>
              <div className="text-xs text-slate-400 mt-1">记录心情</div>
            </button>
            <button 
              onClick={() => navigate('/notes')}
              className="p-4 rounded-lg bg-gradient-to-br from-sky-500/20 to-blue-500/20 border border-sky-500/30 hover:border-sky-500/50 transition-all text-left"
            >
              <StickyNote className="text-sky-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">新建笔记</div>
              <div className="text-xs text-slate-400 mt-1">记录想法</div>
            </button>
            <button 
              onClick={() => navigate('/eval')}
              className="p-4 rounded-lg bg-gradient-to-br from-emerald-500/20 to-teal-500/20 border border-emerald-500/30 hover:border-emerald-500/50 transition-all text-left"
            >
              <TestTube className="text-emerald-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">运行评测</div>
              <div className="text-xs text-slate-400 mt-1">测试模型</div>
            </button>
          </div>
        </div>
      </div>

      {/* System Status */}
      <div className="glass rounded-xl p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-4">系统状态</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 rounded-lg bg-white/5">
            <div className="flex items-center gap-2 mb-2">
              <div className={`w-2 h-2 rounded-full ${apiStatus === 'up' ? 'bg-emerald-400 pulse-dot' : apiStatus === 'down' ? 'bg-rose-400' : 'bg-amber-400 animate-pulse'}`} />
              <span className="text-sm text-slate-300">API 服务</span>
            </div>
            <div className="text-xs text-slate-500">
              {apiStatus === 'up' ? '运行正常' : apiStatus === 'down' ? '连接失败' : '检测中...'}
            </div>
          </div>
          <div className="p-4 rounded-lg bg-white/5">
            <div className="flex items-center gap-2 mb-2">
              <div className={`w-2 h-2 rounded-full ${apiStatus === 'up' ? 'bg-emerald-400 pulse-dot' : apiStatus === 'down' ? 'bg-rose-400' : 'bg-amber-400 animate-pulse'}`} />
              <span className="text-sm text-slate-300">LLM 服务</span>
            </div>
            <div className="text-xs text-slate-500">
              {apiStatus === 'up' ? '已就绪 · 通过 API 检测' : apiStatus === 'down' ? '未知 · API 不可达' : '检测中...'}
            </div>
          </div>
          <div className="p-4 rounded-lg bg-white/5">
            <div className="flex items-center gap-2 mb-2">
              <div className={`w-2 h-2 rounded-full ${apiStatus === 'up' ? 'bg-emerald-400 pulse-dot' : apiStatus === 'down' ? 'bg-rose-400' : 'bg-amber-400 animate-pulse'}`} />
              <span className="text-sm text-slate-300">数据库</span>
            </div>
            <div className="text-xs text-slate-500">
              {apiStatus === 'up' ? '已连接 · 通过 API 检测' : apiStatus === 'down' ? '未知 · API 不可达' : '检测中...'}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
