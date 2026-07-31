import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
  pageSize?: number
  onPageSizeChange?: (size: number) => void
}

const PAGE_SIZE_OPTIONS = [10, 20, 50]

export default function Pagination({
  currentPage,
  totalPages,
  onPageChange,
  pageSize,
  onPageSizeChange,
}: PaginationProps) {
  if (totalPages <= 1 && !onPageSizeChange) return null

  // 计算要显示的页码
  const getPageNumbers = (): (number | 'ellipsis')[] => {
    const pages: (number | 'ellipsis')[] = []
    const maxVisible = 5

    if (totalPages <= maxVisible + 2) {
      for (let i = 1; i <= totalPages; i++) pages.push(i)
      return pages
    }

    pages.push(1)

    let start = Math.max(2, currentPage - Math.floor(maxVisible / 2))
    let end = Math.min(totalPages - 1, start + maxVisible - 1)
    start = Math.max(2, end - maxVisible + 1)

    if (start > 2) pages.push('ellipsis')

    for (let i = start; i <= end; i++) pages.push(i)

    if (end < totalPages - 1) pages.push('ellipsis')

    pages.push(totalPages)
    return pages
  }

  return (
    <div className="flex items-center justify-between mt-4 pt-4 border-t border-white/10">
      <div className="flex items-center gap-2">
        {onPageSizeChange && pageSize !== undefined && (
          <div className="flex items-center gap-2 text-sm text-slate-400">
            <span>每页</span>
            <select
              value={pageSize}
              onChange={e => onPageSizeChange(Number(e.target.value))}
              className="px-2 py-1 rounded-lg text-sm bg-white/5 border border-white/10 text-slate-300"
            >
              {PAGE_SIZE_OPTIONS.map(size => (
                <option key={size} value={size}>{size}</option>
              ))}
            </select>
            <span>条</span>
          </div>
        )}
      </div>

      <div className="flex items-center gap-1">
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 1}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-sm text-slate-400 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronLeft size={16} />
        </button>

        {getPageNumbers().map((page, idx) =>
          page === 'ellipsis' ? (
            <span key={`ellipsis-${idx}`} className="w-8 h-8 flex items-center justify-center text-xs text-slate-500">
              ...
            </span>
          ) : (
            <button
              key={page}
              onClick={() => onPageChange(page)}
              className={`flex items-center justify-center w-8 h-8 rounded-lg text-sm transition-colors ${
                page === currentPage
                  ? 'bg-sky-500 text-white font-semibold'
                  : 'text-slate-400 hover:bg-white/10'
              }`}
            >
              {page}
            </button>
          )
        )}

        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-sm text-slate-400 hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronRight size={16} />
        </button>
      </div>

      <div className="text-sm text-slate-500">
        第 {currentPage} / {totalPages} 页
      </div>
    </div>
  )
}
