import { useEffect, useMemo, useRef, useState } from 'react'
import {
  BookHeart, ChevronRight, CircleHelp, Heart,
  Archive, Mic, PenLine, RefreshCw, Sparkles, Star, Trash2, Wind,
} from 'lucide-react'
import { treeholeApi, type Mood, type TreeholeEntry, type TreeholeInsights } from '../api/treehole'

type NoticeTone = 'success' | 'info' | 'error'

const moods: { name: Mood; emoji: string; className: string }[] = [
  { name: '开心', emoji: '😊', className: 'happy' },
  { name: '平静', emoji: '😌', className: 'calm' },
  { name: '疲惫', emoji: '😮‍💨', className: 'tired' },
  { name: '焦虑', emoji: '😟', className: 'anxious' },
  { name: '难过', emoji: '😔', className: 'sad' },
]

function createdAt(value: string) {
  const date = new Date(value)
  const today = new Date().toDateString()
  return date.toDateString() === today
    ? `今天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
    : date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

export default function Treehole() {
  const [entries, setEntries] = useState<TreeholeEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [mood, setMood] = useState<Mood>('开心')
  const [content, setContent] = useState('')
  const [insights, setInsights] = useState<TreeholeInsights | null>(null)
  const [prompts, setPrompts] = useState<string[]>([])
  const [showArchived, setShowArchived] = useState(false)
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState<{ text: string; tone: NoticeTone } | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const timerRef = useRef<number | undefined>(undefined)

  const showNotice = (text: string, tone: NoticeTone = 'info') => {
    window.clearTimeout(timerRef.current)
    setNotice({ text, tone })
    timerRef.current = window.setTimeout(() => setNotice(null), 2800)
  }

  const loadEntries = async (signal?: AbortSignal, archived = showArchived) => {
    try {
      const [entriesResult, insightsResult, promptsResult] = await Promise.all([
        treeholeApi.list({ signal }, archived), treeholeApi.insights(30, { signal }), treeholeApi.prompts({ signal }),
      ])
      if (entriesResult.code === 0) setEntries(entriesResult.data)
      if (insightsResult.code === 0) setInsights(insightsResult.data)
      if (promptsResult.code === 0) setPrompts(promptsResult.data)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      showNotice('树洞加载失败，请稍后重试', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    loadEntries(controller.signal)
    return () => controller.abort()
  }, [])

  const createEntry = async () => {
    const value = content.trim()
    if (!value) {
      textareaRef.current?.focus()
      showNotice('写下一点此刻的感受后再保存吧', 'error')
      return
    }
    setCreating(true)
    try {
      const title = `${mood} · ${value.replace(/\s+/g, ' ').slice(0, 18)}`
      const res = await treeholeApi.create({ title, content: value, mood })
      if (res.code !== 0) throw new Error(res.message)
      setEntries(previous => [res.data, ...previous])
      setContent('')
      void treeholeApi.insights(30).then(insightResult => {
        if (insightResult.code === 0) setInsights(insightResult.data)
      }).catch(() => undefined)
      showNotice('这份心情已经被好好收下', 'success')
    } catch {
      showNotice('保存失败，请稍后重试', 'error')
    } finally {
      setCreating(false)
    }
  }

  const deleteEntry = async (id: number) => {
    if (!window.confirm('确定删除这条树洞记录吗？')) return
    try {
      await treeholeApi.delete(id)
      setEntries(previous => previous.filter(entry => entry.id !== id))
      showNotice('树洞记录已删除', 'success')
    } catch {
      showNotice('删除失败，请稍后重试', 'error')
    }
  }

  const archiveEntry = async (entry: TreeholeEntry) => {
    try {
      const res = await treeholeApi.archive(entry.id, !entry.archived)
      if (res.code === 0) setEntries(previous => previous.filter(item => item.id !== entry.id))
      showNotice(entry.archived ? '记录已恢复' : '记录已归档', 'success')
    } catch { showNotice('归档失败，请稍后重试', 'error') }
  }

  const toggleArchiveView = () => {
    const next = !showArchived
    setShowArchived(next)
    setLoading(true)
    void loadEntries(undefined, next)
  }

  const favoriteEntry = async (entry: TreeholeEntry) => {
    try {
      const res = await treeholeApi.favorite(entry.id, !entry.favorite)
      if (res.code === 0) setEntries(previous => previous.map(item => item.id === entry.id ? res.data : item))
    } catch { showNotice('收藏状态更新失败', 'error') }
  }

  const visibleEntries = useMemo(() => entries.slice(0, 3), [entries])
  const selectedMood = moods.find(item => item.name === mood) ?? moods[0]
  const moodEmoji = (value: string) => moods.find(item => item.name === value)?.emoji ?? '😌'
  const trend = insights?.trend.slice(-7) ?? []
  const chartPoints = trend.map((item, index) => `${index * (330 / Math.max(1, trend.length - 1))},${item.score ? 94 - item.score * 15 : 94}`).join(' ')
  const calendarMood = insights?.calendar ?? {}
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth()
  const monthDays = new Date(currentYear, currentMonth + 1, 0).getDate()

  return <section className="treehole-workspace">
    <header className="treehole-hero"><div><h1>树洞 <span>❧</span></h1><p>记录你的心情，AI 会给你温暖的回应 <Heart size={16} /></p></div><button onClick={() => textareaRef.current?.focus()}><PenLine size={17} /> 写树洞</button></header>

    <div className="treehole-top-grid">
      <section className="treehole-card treehole-composer"><h2>快速记录心情</h2><p>选择此刻的心情，开始记录吧</p><div className="treehole-moods">{moods.map(item => <button key={item.name} className={`${item.className} ${mood === item.name ? 'active' : ''}`} onClick={() => setMood(item.name)}><span>{item.emoji}</span>{item.name}</button>)}</div><div className="treehole-input-wrap"><textarea ref={textareaRef} value={content} onChange={event => setContent(event.target.value)} maxLength={5000} placeholder="此刻的你，想对树洞说点什么呢…" /><div><span>{content.length} / 5000</span><button title="语音转写服务尚未配置" onClick={() => showNotice('语音转写需先配置 ASR 服务')}><Mic size={16} /></button></div></div><footer><span><span className={`treehole-mood-dot ${selectedMood.className}`} />当前心情：{mood}</span><button onClick={createEntry} disabled={creating}>{creating ? '正在收下你的心情…' : '保存这份心情'} <ChevronRight size={16} /></button></footer></section>

      <section className="treehole-card treehole-gentle"><h2>AI 温柔陪伴</h2><p>选一个引导，从一句话开始</p><div>{[[Heart, '给自己写一封温柔的信', '亲爱的自己，我想对你说……'], [Star, '感恩三件小事', '今天值得感谢的三件小事是……'], [Wind, '深呼吸，慢慢来', '此刻我的身体和情绪正在告诉我……']].map(([ActionIcon, title, text]) => <button key={title as string} onClick={() => { setContent(text as string); textareaRef.current?.focus() }}><ActionIcon size={22} /><span><strong>{title as string}</strong><small>{text as string}</small></span><ChevronRight size={18} /></button>)}</div></section>
    </div>

    <div className="treehole-middle-grid">
      <section className="treehole-empty-scene"><div className="treehole-scene-art"><div className="treehole-moon" /><div className="treehole-tree"><i /><i /><i /><i /><i /></div></div><div><h2>{entries.length ? '把心情继续写下去' : '还没有树洞'}</h2><h3>{entries.length ? '每一份感受，都值得被认真聆听' : '写下你的第一条树洞吧'}</h3><Heart size={19} fill="currentColor" /><p>在这里，你可以自由表达心情与想法<br />AI 会认真倾听，给你温暖的回应</p><div className="treehole-scene-actions"><button onClick={() => textareaRef.current?.focus()}><PenLine size={15} /> 写下今天的心情</button><button onClick={() => { setContent(prompts[0] ?? '今天，我想记录……'); textareaRef.current?.focus() }}><Sparkles size={14} /> 从一句话开始</button><button title="语音转写服务尚未配置" onClick={() => showNotice('语音转写需先配置 ASR 服务')}><Mic size={14} /> 语音记录</button></div></div></section>

      <section className="treehole-card treehole-recent"><header><h2><BookHeart size={18} /> {showArchived ? '已归档' : '最近树洞'}</h2><button onClick={toggleArchiveView}>{showArchived ? '返回最近' : '查看归档'} <ChevronRight size={15} /></button></header>{loading ? <p className="treehole-loading">正在整理你的树洞…</p> : visibleEntries.length ? <div>{visibleEntries.map((entry, index) => <article key={entry.id}><span className={`treehole-entry-face face-${index}`}>{moodEmoji(entry.mood)}</span><div><strong>{entry.title}</strong><p>{entry.content}</p></div><time>{createdAt(entry.createdAt)}</time><button className="treehole-delete" title={entry.favorite ? '取消收藏' : '收藏'} onClick={() => void favoriteEntry(entry)}><Star size={14} fill={entry.favorite ? 'currentColor' : 'none'} /></button><button className="treehole-delete" title={entry.archived ? '恢复' : '归档'} onClick={() => void archiveEntry(entry)}><Archive size={14} /></button><button className="treehole-delete" title="删除记录" onClick={() => void deleteEntry(entry.id)}><Trash2 size={14} /></button></article>)}</div> : <p className="treehole-loading">{showArchived ? '暂无归档记录。' : '第一条树洞，会从这里开始。'}</p>}</section>
    </div>

    <div className="treehole-bottom-grid">
      <section className="treehole-card treehole-inspiration"><header><h2><Sparkles size={18} /> 写作灵感</h2><button onClick={() => setPrompts(current => current.length > 1 ? [...current.slice(1), current[0]] : current)}><RefreshCw size={15} /></button></header><p>不知道写什么？试试这些开头吧</p><div>{prompts.map(text => <button key={text} onClick={() => { setContent(text); textareaRef.current?.focus() }}><CircleHelp size={17} />{text}</button>)}</div></section>
      <section className="treehole-card treehole-trend"><header><div><h2>〽 心情轨迹</h2><p>近 7 天心情变化</p></div></header><div className="treehole-chart"><svg viewBox="0 0 330 100" preserveAspectRatio="none"><defs><linearGradient id="mood-line" x1="0" x2="1"><stop stopColor="#f5be58" /><stop offset=".5" stopColor="#ba65ec" /><stop offset="1" stopColor="#52a8f7" /></linearGradient></defs>{chartPoints && <polyline points={chartPoints} fill="none" stroke="url(#mood-line)" strokeWidth="3" />}</svg><div>{trend.map(item => <small key={item.date}>{item.date.slice(5).replace('-', '/')}</small>)}</div></div><aside><span>{selectedMood.emoji}</span><strong>{insights?.active ?? 0} 份心情</strong><small>数据来自你的真实记录</small></aside></section>
      <section className="treehole-card treehole-calendar"><header><h2>▣ 情绪日历</h2><span>{currentMonth + 1} 月</span></header><div className="treehole-calendar-grid">{['一','二','三','四','五','六','日', ...Array.from({ length: monthDays }, (_, index) => String(index + 1))].map((day, index) => { const date = index < 7 ? '' : `${currentYear}-${String(currentMonth + 1).padStart(2, '0')}-${String(index - 6).padStart(2, '0')}`; const entryMood = calendarMood[date]; return <span key={`${day}-${index}`} className={entryMood ? 'has-mood' : ''}>{entryMood ? moodEmoji(entryMood) : day}</span> })}</div></section>
    </div>

    {notice && <div className={`treehole-notice ${notice.tone}`}><span>{notice.text}</span><button onClick={() => setNotice(null)}>×</button></div>}
  </section>
}
