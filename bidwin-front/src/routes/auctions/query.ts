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

/** 현재 페이지가 속한 고정 크기 묶음의 번호 목록 */
export function getPageWindow(currentPage: number, totalPages: number): number[] {
  const start = Math.floor((currentPage - FIRST_PAGE) / PAGE_WINDOW_SIZE)
    * PAGE_WINDOW_SIZE + FIRST_PAGE
  const end = Math.min(totalPages, start + PAGE_WINDOW_SIZE - 1)
  const size = end - start + 1
  return Array.from({ length: size }, (_, index) => start + index)
}
