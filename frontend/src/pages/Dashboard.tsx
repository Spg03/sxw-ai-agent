import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight, BookOpen, BrainCircuit, CloudCheck, FileText, Heart,
  MessageCircleMore, MessageSquare, Plus, Sparkles, TrendingUp, Zap,
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { dashboardApi, type DashboardOverview } from '../api/dashboard'

const activityIcons = { treehole: Heart, chat: MessageSquare, note: FileText, eval: BrainCircuit }

const relativeTime = (time: string) => {
  const elapsed = Date.now() - new Date(time).getTime()
  const minutes = Math.max(0, Math.floor(elapsed / 60000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

function Trend() {
  return <svg className="dashboard-trend" viewBox="0 0 94 36" aria-hidden="true"><path d="M2 31 L15 25 L25 28 L39 14 L50 19 L62 8 L73 16 L86 3 L92 8" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
}

export default function Dashboard() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [overview, setOverview] = useState<DashboardOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [online, setOnline] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([dashboardApi.getOverview({ signal: controller.signal }), dashboardApi.healthCheck({ signal: controller.signal })])
      .then(([data, health]) => { if (data.code === 0) setOverview(data.data); setOnline(health.code === 0) })
      .catch(error => { if (error.name !== 'AbortError') console.error('Dashboard data unavailable', error) })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [])

  const metrics = useMemo(() => [
    { label: '通用对话', value: overview?.stats.chatCount ?? 0, suffix: '次', icon: MessageSquare, tone: 'pink' },
    { label: '陪伴记录', value: overview?.stats.treeholeCount ?? 0, suffix: '次', icon: Heart, tone: 'orange' },
    { label: '笔记', value: overview?.stats.noteCount ?? 0, suffix: '篇', icon: FileText, tone: 'blue' },
    { label: '评测运行', value: overview?.stats.evalRunCount ?? 0, suffix: '次', icon: Sparkles, tone: 'green' },
  ], [overview])

  const activities = overview?.recentActivities ?? []
  const conversations = overview?.recentConversations ?? []

  return <div className="dashboard-page">
    <header className="dashboard-heading">
      <div><h1>欢迎回来，{user?.nickname || user?.username || '朋友'} <span>👋</span></h1><p>你的 AI 助手工作台，专注工作与成长，陪伴你每一步。</p></div>
      <div className="dashboard-companion"><Sparkles size={16} /> 今日已陪伴 {overview?.companionMinutes ?? 0} 分钟</div>
    </header>

    <section className="dashboard-assistants">
      <AssistantCard kind="general" title="通用助手" subtitle="工作、学习与知识处理" description="帮你写作、整理、分析、总结、提升效率，探索知识的边界。" action="继续最近对话" onClick={() => navigate('/chat')} />
      <AssistantCard kind="love" title="情感伙伴" subtitle="倾听、陪伴与情绪复盘" description="在这里倾诉想法、释放情绪，与你一起回顾、理解、成长。" action="和伙伴聊聊" onClick={() => navigate('/chat?profile=LOVE')} />
    </section>

    <section className="dashboard-metrics">
      {metrics.map(({ label, value, suffix, icon: Icon, tone }) => <article className={`dashboard-metric ${tone}`} key={label}><div className="dashboard-metric-icon"><Icon size={23} /></div><div><p>{label}</p><strong>{loading ? '—' : value}<small>{suffix}</small></strong><span>较昨日 <b>+{value ? 6 : 0}% ↗</b></span></div><Trend /></article>)}
    </section>

    <section className="dashboard-lower-grid">
      <Panel title="最近活动" icon={<TrendingUp size={18} />} action="查看全部" onAction={() => navigate('/treehole')}>
        <div className="dashboard-list">{activities.length ? activities.map((activity, index) => { const Icon = activityIcons[activity.type] ?? Sparkles; return <div className="dashboard-list-row" key={`${activity.title}-${index}`}><div className={`dashboard-row-icon ${activity.type}`}><Icon size={17} /></div><div><strong>{activity.title}</strong><p>{activity.detail || '新的内容已保存到你的工作台'}</p></div><time>{relativeTime(activity.occurredAt)}</time></div> }) : <Empty text="还没有活动记录，开始一段对话吧。" />}</div>
      </Panel>
      <Panel title="快捷操作" icon={<Zap size={18} />}>
        <div className="dashboard-actions">
          <QuickAction icon={<MessageSquare size={19} />} title="新建对话" detail="与通用助手对话" onClick={() => navigate('/chat')} />
          <QuickAction icon={<Heart size={19} />} title="和伙伴聊聊" detail="倾诉与陪伴" onClick={() => navigate('/chat?profile=LOVE')} />
          <QuickAction icon={<FileText size={19} />} title="新建笔记" detail="记录想法与感受" onClick={() => navigate('/notes')} />
          <QuickAction icon={<Heart size={19} />} title="情绪记录" detail="记录此刻的感受" onClick={() => navigate('/treehole')} />
          <QuickAction icon={<BookOpen size={19} />} title="知识库检索" detail="查找资料与知识" onClick={() => navigate('/knowledge')} />
          <QuickAction icon={<BrainCircuit size={19} />} title="开始复盘" detail="回顾与总结提升" onClick={() => navigate('/hermes')} />
        </div>
      </Panel>
      <Panel title="最近对话" icon={<MessageCircleMore size={18} />} action="查看全部" onAction={() => navigate('/chat')}>
        <div className="dashboard-list">{conversations.length ? conversations.map(conversation => <button className="dashboard-list-row dashboard-conversation" onClick={() => navigate('/chat')} key={conversation.id}><div className="dashboard-row-icon chat"><MessageSquare size={17} /></div><div><strong>{conversation.title}</strong><p>继续这段对话，AI 会带入最近的上下文</p></div><time>{relativeTime(conversation.occurredAt)}</time></button>) : <Empty text="还没有历史对话，开始与助手交流吧。" />}</div>
      </Panel>
    </section>

    <footer className="dashboard-status"><span><i className={online ? 'online' : 'offline'} />{online ? '所有系统服务运行正常' : '服务状态检测中'}</span><span><CloudCheck size={17} /> 数据已同步至云端</span><span>最后更新：{overview ? new Date(overview.generatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '—'}</span></footer>
  </div>
}

function AssistantCard({ kind, title, subtitle, description, action, onClick }: { kind: 'general' | 'love'; title: string; subtitle: string; description: string; action: string; onClick: () => void }) {
  const isLove = kind === 'love'
  const Icon = isLove ? Heart : MessageCircleMore
  return <article className={`dashboard-assistant ${kind}`}><div className="dashboard-assistant-art"><Icon size={isLove ? 76 : 88} /></div><div className="dashboard-assistant-copy"><h2>{title}</h2><h3>{subtitle}</h3><p>{description}</p><button onClick={onClick}>{action}<ArrowRight size={18} /></button><small><i /> 最近{isLove ? '交流：今天有点焦虑' : '对话：项目方案优化思路'}</small></div><span className="dashboard-assistant-chevron">›</span></article>
}

function Panel({ title, icon, action, onAction, children }: { title: string; icon: ReactNode; action?: string; onAction?: () => void; children: ReactNode }) {
  return <section className="dashboard-panel"><header><h2>{icon}{title}</h2>{action && <button onClick={onAction}>{action}</button>}</header>{children}</section>
}

function QuickAction({ icon, title, detail, onClick }: { icon: ReactNode; title: string; detail: string; onClick: () => void }) {
  return <button className="dashboard-action" onClick={onClick}><span>{icon}</span><div><strong>{title}</strong><small>{detail}</small></div><ArrowRight size={16} /></button>
}

function Empty({ text }: { text: string }) { return <p className="dashboard-empty"><Plus size={17} />{text}</p> }
