import {
  ChevronDown,
  PanelLeftClose,
  PanelLeftOpen,
  SlidersHorizontal,
  TrendingDown,
  TrendingUp,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { FILTER_TEXT } from '../constants'
import { SORT_OPTIONS } from '../query'
import type { SortKey } from '../query'
import type { AuctionTypeFilter } from '../types'

/*
 * 목록 상단 도구 줄.
 * 왼쪽 = 필터 패널 접기 + 상향/하향 경매 선택, 오른쪽 = 정렬.
 * 상향/하향은 결과의 성격 자체가 달라서 사이드바 필터가 아니라 여기에 둔다.
 */
const SORT_LABEL = '정렬 기준'

const AUCTION_TYPE_OPTIONS: { key: AuctionTypeFilter; label: string; icon?: LucideIcon }[] = [
  { key: 'ALL', label: '전체 경매' },
  { key: 'UP', label: '상향 경매', icon: TrendingUp },
  { key: 'DOWN', label: '하향 경매', icon: TrendingDown },
]

function AuctionToolbar({
  isPanelOpen,
  onTogglePanel,
  onOpenFilterSheet,
  selectedFilterCount,
  auctionType,
  onChangeAuctionType,
  sort,
  onChangeSort,
}: {
  isPanelOpen: boolean
  onTogglePanel: () => void
  onOpenFilterSheet: () => void
  selectedFilterCount: number
  auctionType: AuctionTypeFilter
  onChangeAuctionType: (next: AuctionTypeFilter) => void
  sort: SortKey
  onChangeSort: (next: SortKey) => void
}) {
  const PanelIcon = isPanelOpen ? PanelLeftClose : PanelLeftOpen

  return (
    <div className="flex flex-wrap items-center gap-xs">
      <button
        type="button"
        onClick={onOpenFilterSheet}
        className="flex h-11 shrink-0 items-center gap-xs rounded-md border border-hairline px-base text-sm font-semibold text-body transition-colors hover:bg-surface-soft hover:text-ink lg:hidden"
      >
        <SlidersHorizontal size={16} />
        {FILTER_TEXT.openSheet}
        {selectedFilterCount > 0 && (
          <span className="rounded-xs bg-primary-tint px-1.5 py-0.5 text-xs text-primary">
            {selectedFilterCount}
          </span>
        )}
      </button>

      <button
        type="button"
        onClick={onTogglePanel}
        aria-expanded={isPanelOpen}
        className="hidden h-11 shrink-0 items-center gap-xs rounded-md border border-hairline px-base text-sm font-semibold text-body transition-colors hover:bg-surface-soft hover:text-ink lg:flex"
      >
        <PanelIcon size={16} />
        {isPanelOpen ? FILTER_TEXT.collapse : FILTER_TEXT.expand}
      </button>

      <div
        className="order-last flex w-full flex-wrap items-center gap-xs md:order-none md:w-auto"
        role="group"
      >
        {AUCTION_TYPE_OPTIONS.map((option) => {
          const isActive = auctionType === option.key
          const Icon = option.icon
          return (
            <button
              key={option.key}
              type="button"
              aria-pressed={isActive}
              onClick={() => onChangeAuctionType(option.key)}
              className={`flex h-11 shrink-0 items-center gap-1.5 rounded-pill border px-base text-sm font-semibold transition-colors ${
                isActive
                  ? 'border-primary bg-primary-tint text-primary'
                  : 'border-transparent bg-surface-soft text-body hover:bg-surface-strong'
              }`}
            >
              {Icon && <Icon size={15} />}
              {option.label}
            </button>
          )
        })}
      </div>

      <div className="relative ml-auto shrink-0">
        <select
          value={sort}
          onChange={(event) => onChangeSort(event.target.value as SortKey)}
          aria-label={SORT_LABEL}
          className="h-11 cursor-pointer appearance-none rounded-md bg-transparent py-0 pl-sm pr-7 text-sm font-semibold text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.key} value={option.key}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown
          size={16}
          className="pointer-events-none absolute right-1 top-1/2 -translate-y-1/2 text-body"
        />
      </div>
    </div>
  )
}

export default AuctionToolbar
