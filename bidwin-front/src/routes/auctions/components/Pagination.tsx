import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { FIRST_PAGE, PAGE_WINDOW_SIZE, PAGINATION_TEXT } from '../constants'
import { getPageWindow } from '../query'

/*
 * 무한 스크롤이 아니라 1, 2, 3 … 으로 이어지는 번호 페이지네이션.
 * 페이지가 많으면 현재 페이지 주변만 남기고 앞뒤를 … 로 접는다.
 */
function Pagination({
  currentPage,
  totalPages,
  onChange,
}: {
  currentPage: number
  totalPages: number
  onChange: (page: number) => void
}) {
  const pages = getPageWindow(currentPage, totalPages)

  return (
    <nav aria-label={PAGINATION_TEXT.navLabel} className="flex items-center justify-center gap-1">
      <ArrowButton
        label={PAGINATION_TEXT.prev}
        disabled={currentPage <= FIRST_PAGE}
        onClick={() => onChange(Math.max(FIRST_PAGE, currentPage - PAGE_WINDOW_SIZE))}
      >
        <ChevronLeft size={16} />
      </ArrowButton>

      {pages.map((page) => (
        <PageButton
          key={page}
          page={page}
          isActive={page === currentPage}
          onClick={onChange}
        />
      ))}

      <ArrowButton
        label={PAGINATION_TEXT.next}
        disabled={currentPage >= totalPages}
        onClick={() => onChange(Math.min(totalPages, currentPage + PAGE_WINDOW_SIZE))}
      >
        <ChevronRight size={16} />
      </ArrowButton>
    </nav>
  )
}

function PageButton({
  page,
  isActive,
  onClick,
}: {
  page: number
  isActive: boolean
  onClick: (page: number) => void
}) {
  return (
    <button
      type="button"
      onClick={() => onClick(page)}
      aria-label={PAGINATION_TEXT.pageAriaLabel(page)}
      aria-current={isActive ? 'page' : undefined}
      className={`h-9 min-w-9 rounded-md px-2 text-sm font-semibold transition-colors ${
        isActive ? 'bg-ink text-on-dark' : 'text-body hover:bg-surface-soft hover:text-ink'
      }`}
    >
      {page}
    </button>
  )
}

function ArrowButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string
  disabled: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="flex h-9 w-9 items-center justify-center rounded-md text-body transition-colors hover:bg-surface-soft hover:text-ink disabled:cursor-not-allowed disabled:text-muted-soft disabled:hover:bg-transparent"
    >
      {children}
    </button>
  )
}

export default Pagination
