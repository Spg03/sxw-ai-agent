import { useEffect, useMemo, useRef, useState } from 'react'
import {
  BookHeart, ChevronRight, CircleHelp, Heart,
  Mic, MoreHorizontal, PenLine, RefreshCw, Sparkles, Star, Trash2, Wind,
} from 'lucide-react'
import { treeholeApi, type TreeholeEntry } from '../api/treehole'

type Mood = '开心' | '平静' | '疲惫' | '焦虑' | '难过'
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
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState<{ text: string; tone: NoticeTone } | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const timerRef = useRef<number | undefined>(undefined)

  const showNotice = (text: string, tone: NoticeTone = 'info') => {
    window.clearTimeout(timerRef.current)
    setNotice({ text, tone })
    timerRef.current = window.setTimeout(() => setNotice(null), 2800)
  }

  const loadEntries = async (signal?: AbortSignal) => {
    try {
      const res = await treeholeApi.list({ signal })
      if (res.code === 0) setEntries(res.data)
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
      const res = await treeholeApi.create({ title, content: value })
      if (res.code !== 0) throw new Error(res.message)
      setEntries(previous => [res.data, ...previous])
      setContent('')
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

  const visibleEntries = useMemo(() => entries.slice(0, 3), [entries])
  const selectedMood = moods.find(item => item.name === mood) ?? moods[0]

  return <section className="treehole-workspace">
    <header className="treehole-hero"><div><h1>树洞 <span>❧</span></h1><p>记录你的心情，AI 会给你温暖的回应 <Heart size={16} /></p></div><button onClick={() => textareaRef.current?.focus()}><PenLine size={17} /> 写树洞</button></header>

    <div className="treehole-top-grid">
      <section className="treehole-card treehole-composer"><h2>快速记录心情</h2><p>选择此刻的心情，开始记录吧</p><div className="treehole-moods">{moods.map(item => <button key={item.name} className={`${item.className} ${mood === item.name ? 'active' : ''}`} onClick={() => setMood(item.name)}><span>{item.emoji}</span>{item.name}</button>)}</div><div className="treehole-input-wrap"><textarea ref={textareaRef} value={content} onChange={event => setContent(event.target.value)} maxLength={5000} placeholder="此刻的你，想对树洞说点什么呢…" /><div><span>{content.length} / 5000</span><button title="TODO：语音转文字记录待后端实现" onClick={() => showNotice('语音记录待后端接口实现')}><Mic size={16} /></button></div></div><footer><span><span className={`treehole-mood-dot ${selectedMood.className}`} />当前心情：{mood}</span><button onClick={createEntry} disabled={creating}>{creating ? '正在收下你的心情…' : '保存这份心情'} <ChevronRight size={16} /></button></footer></section>

      <section className="treehole-card treehole-gentle"><h2>AI 温柔陪伴</h2><p>需要一点陪伴？试试这些小建议</p><div>{[[Heart, '给自己写一封温柔的信', '把想对自己说的话，写下来吧'], [Star, '感恩三件小事', '发现生活中的微小美好'], [Wind, '深呼吸，慢慢来', '花几分钟，和自己好好待一会儿']].map(([ActionIcon, title, text]) => <button key={title as string} title="TODO：AI 陪伴引导待后端实现" onClick={() => showNotice(`${title}：待后端 AI 陪伴能力实现`)}><ActionIcon size={22} /><span><strong>{title as string}</strong><small>{text as string}</small></span><ChevronRight size={18} /></button>)}</div></section>
    </div>

    <div className="treehole-middle-grid">
      <section className="treehole-empty-scene"><div className="treehole-scene-art"><div className="treehole-moon" /><div className="treehole-tree"><i /><i /><i /><i /><i /></div></div><div><h2>{entries.length ? '把心情继续写下去' : '还没有树洞'}</h2><h3>{entries.length ? '每一份感受，都值得被认真聆听' : '写下你的第一条树洞吧'}</h3><Heart size={19} fill="currentColor" /><p>在这里，你可以自由表达心情与想法<br />AI 会认真倾听，给你温暖的回应</p><div className="treehole-scene-actions"><button onClick={() => textareaRef.current?.focus()}><PenLine size={15} /> 写下今天的心情</button><button title="TODO：随机写作引导待后端实现" onClick={() => showNotice('随机写作引导待后端实现')}><Sparkles size={14} /> 从一句话开始</button><button title="TODO：语音转文字记录待后端实现" onClick={() => showNotice('语音记录待后端接口实现')}><Mic size={14} /> 语音记录</button></div></div></section>

      <section className="treehole-card treehole-recent"><header><h2><BookHeart size={18} /> 最近树洞</h2><button title="当前仅展示最近三条" onClick={() => showNotice('当前后端仅提供基础列表，完整归档待后续实现')}>查看全部 <ChevronRight size={15} /></button></header>{loading ? <p className="treehole-loading">正在整理你的树洞…</p> : visibleEntries.length ? <div>{visibleEntries.map((entry, index) => <article key={entry.id}><span className={`treehole-entry-face face-${index}`}>{['😊', '😌', '😔'][index]}</span><div><strong>{entry.title}</strong><p>{entry.content}</p></div><time>{createdAt(entry.createdAt)}</time><button className="treehole-delete" title="删除记录" onClick={() => deleteEntry(entry.id)}><Trash2 size={14} /></button></article>)}</div> : <p className="treehole-loading">第一条树洞，会从这里开始。</p>}</section>
    </div>

    <div className="treehole-bottom-grid">
      <section className="treehole-card treehole-inspiration"><header><h2><Sparkles size={18} /> 写作灵感</h2><button title="TODO：写作灵感服务待后端实现" onClick={() => showNotice('写作灵感服务待后端实现')}><RefreshCw size={15} /></button></header><p>不知道写什么？试试这些开头吧</p><div>{['“今天，我想记录一件小事…”', '“最近让我感到温暖的是…”', '“如果可以对未来的自己说…”'].map(text => <button key={text} title="TODO：灵感模板填充待实现" onClick={() => { setContent(text.slice(1, -1)); textareaRef.current?.focus() }}><CircleHelp size={17} />{text}</button>)}</div></section>
      <section className="treehole-card treehole-trend"><header><div><h2>〽 心情轨迹</h2><p>近 7 天心情变化</p></div><button title="TODO：心情趋势统计待后端实现" onClick={() => showNotice('心情趋势统计待后端接口实现')}><MoreHorizontal size={18} /></button></header><div className="treehole-chart"><svg viewBox="0 0 330 100" preserveAspectRatio="none"><defs><linearGradient id="mood-line" x1="0" x2="1"><stop stopColor="#f5be58" /><stop offset=".5" stopColor="#ba65ec" /><stop offset="1" stopColor="#52a8f7" /></linearGradient></defs><path d="M4 72 C35 49 55 38 78 55 S120 74 146 46 S194 19 219 42 S267 80 326 28" fill="none" stroke="url(#mood-line)" strokeWidth="3" /><path d="M4 72 C35 49 55 38 78 55 S120 74 146 46 S194 19 219 42 S267 80 326 28 L326 98 L4 98Z" fill="url(#mood-area)" opacity=".18" /></svg><div>{['5/15', '5/16', '5/17', '5/18', '5/19', '5/20', '今天'].map(day => <small key={day}>{day}</small>)}</div></div><aside><span>{selectedMood.emoji}</span><strong>还不错</strong><small>保持温暖的节奏</small></aside></section>
      <section className="treehole-card treehole-calendar"><header><h2>▣ 情绪日历</h2><button title="TODO：情绪日历查询待后端实现" onClick={() => showNotice('情绪日历待后端接口实现')}>‹　5月　›</button></header><div className="treehole-calendar-grid">{['一','二','三','四','五','六','日', ...Array.from({ length: 31 }, (_, index) => String(index + 1))].map((day, index) => <span key={`${day}-${index}`} className={index > 6 && [10, 15, 18, 22].includes(index) ? 'has-mood' : ''}>{index === 17 ? '😊' : index === 22 ? '😌' : day}</span>)}</div></section>
    </div>

    {notice && <div className={`treehole-notice ${notice.tone}`}><span>{notice.text}</span><button onClick={() => setNotice(null)}>×</button></div>}
  </section>
}
