import { useAuth } from '../contexts/AuthContext'
import { MessageSquare, Heart, StickyNote, TestTube, Activity, TrendingUp } from 'lucide-react'

export default function Dashboard() {
  const { user } = useAuth()

  const stats = [
    { label: '对话次数', value: '128', icon: MessageSquare, color: 'from-rose-500 to-pink-500' },
    { label: '树洞记录', value: '24', icon: Heart, color: 'from-amber-500 to-orange-500' },
    { label: '笔记数量', value: '56', icon: StickyNote, color: 'from-sky-500 to-blue-500' },
    { label: '评测运行', value: '12', icon: TestTube, color: 'from-emerald-500 to-teal-500' },
  ]

  const recentActivities = [
    { type: 'chat', text: '与 LoveApp 讨论了感情问题', time: '5分钟前' },
    { type: 'treehole', text: '创建了新树洞「今日感悟」', time: '1小时前' },
    { type: 'eval', text: '运行了情感分析评测套件', time: '2小时前' },
    { type: 'note', text: '笔记「会议纪要」已更新', time: '3小时前' },
  ]

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
        {stats.map(stat => {
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
            {recentActivities.map((activity, idx) => (
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
            ))}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="glass rounded-xl p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-slate-100">快速操作</h2>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <button className="p-4 rounded-lg bg-gradient-to-br from-rose-500/20 to-pink-500/20 border border-rose-500/30 hover:border-rose-500/50 transition-all text-left">
              <MessageSquare className="text-rose-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">开始对话</div>
              <div className="text-xs text-slate-400 mt-1">与 AI 助手聊天</div>
            </button>
            <button className="p-4 rounded-lg bg-gradient-to-br from-amber-500/20 to-orange-500/20 border border-amber-500/30 hover:border-amber-500/50 transition-all text-left">
              <Heart className="text-amber-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">写树洞</div>
              <div className="text-xs text-slate-400 mt-1">记录心情</div>
            </button>
            <button className="p-4 rounded-lg bg-gradient-to-br from-sky-500/20 to-blue-500/20 border border-sky-500/30 hover:border-sky-500/50 transition-all text-left">
              <StickyNote className="text-sky-400 mb-2" size={24} />
              <div className="text-sm font-medium text-slate-200">新建笔记</div>
              <div className="text-xs text-slate-400 mt-1">记录想法</div>
            </button>
            <button className="p-4 rounded-lg bg-gradient-to-br from-emerald-500/20 to-teal-500/20 border border-emerald-500/30 hover:border-emerald-500/50 transition-all text-left">
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
              <div className="w-2 h-2 rounded-full bg-emerald-400 pulse-dot" />
              <span className="text-sm text-slate-300">API 服务</span>
            </div>
            <div className="text-xs text-slate-500">运行正常 · 延迟 42ms</div>
          </div>
          <div className="p-4 rounded-lg bg-white/5">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-2 h-2 rounded-full bg-emerald-400 pulse-dot" />
              <span className="text-sm text-slate-300">LLM 服务</span>
            </div>
            <div className="text-xs text-slate-500">运行正常 · 可用</div>
          </div>
          <div className="p-4 rounded-lg bg-white/5">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-2 h-2 rounded-full bg-emerald-400 pulse-dot" />
              <span className="text-sm text-slate-300">向量数据库</span>
            </div>
            <div className="text-xs text-slate-500">运行正常 · 已索引 1,234 文档</div>
          </div>
        </div>
      </div>
    </div>
  )
}
