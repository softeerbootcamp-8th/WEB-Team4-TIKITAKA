import type { AuctionSort } from '../../lib/api/auctions'
import { FIRST_PAGE, PAGE_WINDOW_SIZE } from './constants'

export const SORT_OPTIONS: { key: AuctionSort; label: string }[] = [
  { key: 'recommended', label: '추천순' },
  { key: 'deadline', label: '마감임박순' },
  { key: 'latest', label: '최신순' },
  { key: 'priceLow', label: '낮은 가격순' },
  { key: 'priceHigh', label: '높은 가격순' },
]

export type SortKey = AuctionSort
export const DEFAULT_SORT: SortKey = 'recommended'

/** 페이지가 많을 때 현재 페이지 주변만 남긴 번호 목록 */
export function getPageWindow(currentPage: number, totalPages: number): number[] {
  const size = Math.min(PAGE_WINDOW_SIZE, totalPages)
  const half = Math.floor(size / 2)
  const end = Math.min(totalPages, Math.max(currentPage + half, size))
  const start = Math.max(FIRST_PAGE, end - size + 1)
  return Array.from({ length: size }, (_, index) => start + index)
}
