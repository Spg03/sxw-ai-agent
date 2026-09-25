import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ChevronLeft, ChevronRight, LoaderCircle, X, type LucideIcon } from 'lucide-react'
import './console.css'

export function ConsoleHeader({ icon: Icon, eyebrow, title, description, children }: {
  icon: LucideIcon; eyebrow: string; title: string; description: string; children?: ReactNode
}) {
  return <header className="console-hero"><div className="console-hero-copy">
    <span className="console-eyebrow"><Icon size={16} />{eyebrow}</span>
    <h1>{title}</h1><p>{description}</p>
  </div><div className="console-actions">{children}</div></header>
}

export function EmptyState({ icon: Icon, title, description, children, loading = false }: {
  icon: LucideIcon; title: string; description?: string; children?: ReactNode; loading?: boolean
}) {
  return <div className="console-empty" role={loading ? 'status' : undefined}>
    <span className="console-empty-icon">{loading ? <LoaderCircle className="console-spin" size={28} /> : <Icon size={28} />}</span>
    <h3>{title}</h3>{description && <p>{description}</p>}{children}
  </div>
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: '待审核', APPROVED: '已批准', REJECTED: '已拒绝', APPLIED: '已应用', APPLY_FAILED: '应用失败',
  DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用', RUNNING: '运行中', COMPLETED: '已完成',
  FAILED: '失败', SUCCESS: '成功', ERROR: '错误', CANCELLED: '已取消', SKIPPED: '已跳过',
}
export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const key = status.toUpperCase()
  const tone = ['APPLIED', 'COMPLETED', 'SUCCESS', 'ACTIVE', 'APPROVED'].includes(key) ? 'success'
    : ['FAILED', 'ERROR', 'APPLY_FAILED', 'REJECTED'].includes(key) ? 'danger'
      : ['PENDING', 'RUNNING'].includes(key) ? 'warning' : 'neutral'
  return <span className={`console-badge ${tone}`}><i />{label || STATUS_LABELS[key] || status}</span>
}

export function ConsoleNotice({ message, error = false }: { message: string; error?: boolean }) {
  return message ? <div className={`console-notice ${error ? 'danger' : ''}`} role={error ? 'alert' : 'status'}>{message}</div> : null
}

export function ConsolePager({ page, size, total, onPage, onSize }: {
  page: number; size: number; total: number; onPage: (page: number) => void; onSize: (size: number) => void
}) {
  const pages = Math.max(1, Math.ceil(total / size))
  return <div className="console-pager"><span>共 {total} 条</span>
    <label>每页 <select value={size} onChange={e => onSize(Number(e.target.value))} aria-label="每页条数">
      {[10, 20, 50].map(n => <option key={n} value={n}>{n}</option>)}
    </select> 条</label>
    <nav aria-label="分页"><button className="console-icon-button" aria-label="上一页" disabled={page <= 1} onClick={() => onPage(page - 1)}><ChevronLeft size={17} /></button>
      <span>{page} / {pages}</span><button className="console-icon-button" aria-label="下一页" disabled={page >= pages} onClick={() => onPage(page + 1)}><ChevronRight size={17} /></button>
    </nav></div>
}

// Native modal dialog supplies focus containment, Escape handling and focus restoration.
export function ConsoleDialog({ title, description, onClose, busy = false, children }: {
  title: string; description?: string; onClose: () => void; busy?: boolean; children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => { const dialog = ref.current; dialog?.showModal(); return () => dialog?.close() }, [])
  return createPortal(<dialog ref={ref} className="console-dialog" aria-labelledby={titleId}
    onCancel={e => { e.preventDefault(); if (!busy) onClose() }}>
    <header><div><h2 id={titleId}>{title}</h2>{description && <p>{description}</p>}</div>
      <button className="console-icon-button" aria-label="关闭弹窗" onClick={onClose} disabled={busy}><X size={20} /></button></header>
    {children}
  </dialog>, document.body)
}
