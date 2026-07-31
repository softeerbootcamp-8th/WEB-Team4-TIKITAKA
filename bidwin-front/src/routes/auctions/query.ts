import { BID_SCORE_WEIGHT, FIRST_PAGE, PAGE_SIZE, PAGE_WINDOW_SIZE } from './constants'
import { FILTER_GROUPS, getSelectedIds } from './filters'
import type { FilterSelection } from './filters'
import type { AuctionSummary, AuctionTypeFilter } from './types'

/*
 * 목록에 보여줄 경매를 고르고 정렬하고 잘라내는 순수 함수 모음.
 * 화면 상태(page.tsx)와 그리는 일(components/)에서 계산을 떼어 둔다.
 */

export const SORT_OPTIONS = [
  { key: 'recommended', label: '추천순' },
  { key: 'deadline', label: '마감임박순' },
  { key: 'latest', label: '최신순' },
  { key: 'priceLow', label: '낮은 가격순' },
  { key: 'priceHigh', label: '높은 가격순' },
] as const

export type SortKey = (typeof SORT_OPTIONS)[number]['key']

export const DEFAULT_SORT: SortKey = 'recommended'

function recommendScore(auction: AuctionSummary) {
  return auction.viewCount + auction.bidCount * BID_SCORE_WEIGHT
}

const COMPARATORS: Record<SortKey, (a: AuctionSummary, b: AuctionSummary) => number> = {
  recommended: (a, b) => recommendScore(b) - recommendScore(a),
  deadline: (a, b) => a.deadline - b.deadline,
  latest: (a, b) => b.listedAt - a.listedAt,
  priceLow: (a, b) => a.currentPrice - b.currentPrice,
  priceHigh: (a, b) => b.currentPrice - a.currentPrice,
}

function matchesKeyword(auction: AuctionSummary, keyword: string) {
  if (keyword.length === 0) return true
  const normalized = keyword.trim().toLowerCase()
  if (normalized.length === 0) return true
  return [auction.title, auction.sellerName, auction.category, ...auction.hashtags].some((text) =>
    text.toLowerCase().includes(normalized),
  )
}

function matchesSelection(auction: AuctionSummary, selection: FilterSelection) {
  return FILTER_GROUPS.every((group) => {
    const selectedIds = getSelectedIds(selection, group.id)
    if (selectedIds.length === 0 || !group.match) return true
    return group.match(auction, selectedIds)
  })
}

export interface AuctionQuery {
  keyword: string
  auctionType: AuctionTypeFilter
  selection: FilterSelection
  /** 필터 스위치가 꺼져 있으면 선택값을 지우지 않은 채로 무시한다. */
  isFilterEnabled: boolean
}

export function filterAuctions(auctions: AuctionSummary[], query: AuctionQuery): AuctionSummary[] {
  return auctions.filter(
    (auction) =>
      matchesKeyword(auction, query.keyword) &&
      (query.auctionType === 'ALL' || auction.auctionType === query.auctionType) &&
      (!query.isFilterEnabled || matchesSelection(auction, query.selection)),
  )
}

export function sortAuctions(auctions: AuctionSummary[], sort: SortKey): AuctionSummary[] {
  return [...auctions].sort(COMPARATORS[sort])
}

export function getTotalPages(totalCount: number) {
  return Math.max(FIRST_PAGE, Math.ceil(totalCount / PAGE_SIZE))
}

export function getPageSlice<T>(items: T[], page: number): T[] {
  const start = (page - FIRST_PAGE) * PAGE_SIZE
  return items.slice(start, start + PAGE_SIZE)
}

/** 페이지가 많을 때 현재 페이지 주변만 남긴 번호 목록 */
export function getPageWindow(currentPage: number, totalPages: number): number[] {
  const size = Math.min(PAGE_WINDOW_SIZE, totalPages)
  const half = Math.floor(size / 2)
  const end = Math.min(totalPages, Math.max(currentPage + half, size))
  const start = Math.max(FIRST_PAGE, end - size + 1)
  return Array.from({ length: size }, (_, index) => start + index)
}
