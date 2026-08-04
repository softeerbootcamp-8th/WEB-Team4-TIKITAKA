import { SearchX } from 'lucide-react'
import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import AuctionCard from './components/AuctionCard'
import AuctionToolbar from './components/AuctionToolbar'
import FilterModal from './components/FilterModal'
import FilterPanel from './components/FilterPanel'
import FilterSheet from './components/FilterSheet'
import Pagination from './components/Pagination'
import { FIRST_PAGE, LIST_TEXT, SEARCH_QUERY_PARAM } from './constants'
import { EMPTY_SELECTION, countSelectedOptions } from './filters'
import type { FilterSelection } from './filters'
import { MOCK_AUCTIONS } from './mock'
import { DEFAULT_SORT, filterAuctions, getPageSlice, getTotalPages, sortAuctions } from './query'
import type { SortKey } from './query'
import type { AuctionTypeFilter } from './types'

/*
 * 검색 결과(경매 목록) 화면.
 *
 * 레이아웃 규칙: TopNav(h-16) 아래를 화면 높이에 딱 맞춰 고정하고, 스크롤은 카드
 * 그리드 영역에만 준다. 필터 패널 · 도구 줄 · 페이지네이션은 늘 같은 자리에 남는다.
 */
const CONTENT_HEIGHT_CLASS = 'h-[calc(100dvh-4rem)]'
/** 필터 패널 폭. 목록에 폭을 더 주려고 좁게 잡는다. */
const FILTER_PANEL_WIDTH_CLASS = 'w-[190px]'

function AuctionListPage() {
  const [searchParams] = useSearchParams()
  const keyword = searchParams.get(SEARCH_QUERY_PARAM) ?? ''

  const [isPanelOpen, setIsPanelOpen] = useState(true)
  const [isFilterEnabled, setIsFilterEnabled] = useState(true)
  const [selection, setSelection] = useState<FilterSelection>(EMPTY_SELECTION)
  const [openGroupId, setOpenGroupId] = useState<string | null>(null)
  const [isFilterSheetOpen, setIsFilterSheetOpen] = useState(false)
  const [auctionType, setAuctionType] = useState<AuctionTypeFilter>('ALL')
  const [sort, setSort] = useState<SortKey>(DEFAULT_SORT)
  const [page, setPage] = useState(FIRST_PAGE)
  const [bookmarks, setBookmarks] = useState<ReadonlySet<number>>(() => new Set())

  const listRef = useRef<HTMLDivElement>(null)

  function countFor(nextSelection: FilterSelection) {
    return filterAuctions(MOCK_AUCTIONS, {
      keyword,
      auctionType,
      selection: nextSelection,
      isFilterEnabled,
    }).length
  }

  const matched = filterAuctions(MOCK_AUCTIONS, {
    keyword,
    auctionType,
    selection,
    isFilterEnabled,
  })
  const totalPages = getTotalPages(matched.length)
  /* 조건이 바뀌어 페이지 수가 줄면 마지막 페이지로 당긴다. */
  const currentPage = Math.min(page, totalPages)
  const visibleAuctions = getPageSlice(sortAuctions(matched, sort), currentPage)

  function scrollListToTop() {
    listRef.current?.scrollTo({ top: 0 })
  }

  /* 조건이 바뀌면 1페이지부터 다시 본다. */
  function resetToFirstPage() {
    setPage(FIRST_PAGE)
    scrollListToTop()
  }

  function handleChangePage(nextPage: number) {
    setPage(nextPage)
    scrollListToTop()
  }

  function handleChangeAuctionType(next: AuctionTypeFilter) {
    setAuctionType(next)
    resetToFirstPage()
  }

  function handleChangeSort(next: SortKey) {
    setSort(next)
    resetToFirstPage()
  }

  function handleToggleFilterEnabled(next: boolean) {
    setIsFilterEnabled(next)
    resetToFirstPage()
  }

  function handleApplyFilter(nextSelection: FilterSelection) {
    setSelection(nextSelection)
    setOpenGroupId(null)
    resetToFirstPage()
  }

  function handleResetFilter() {
    setSelection(EMPTY_SELECTION)
    resetToFirstPage()
  }

  function handleToggleBookmark(auctionId: number) {
    setBookmarks((current) => {
      const next = new Set(current)
      if (!next.delete(auctionId)) next.add(auctionId)
      return next
    })
  }

  /* 데스크톱 사이드바와 모바일 시트가 같은 패널을 공유한다. */
  const filterPanel = (
    <FilterPanel
      selection={selection}
      isEnabled={isFilterEnabled}
      onToggleEnabled={handleToggleFilterEnabled}
      onOpenGroup={setOpenGroupId}
      onReset={handleResetFilter}
    />
  )

  return (
    <main className={`mx-auto flex ${CONTENT_HEIGHT_CLASS} max-w-[1200px] flex-col px-lg py-base`}>
      <div className="flex min-h-0 flex-1 gap-lg">
        {isPanelOpen && (
          <aside className={`hidden ${FILTER_PANEL_WIDTH_CLASS} shrink-0 lg:block`}>
            {filterPanel}
          </aside>
        )}

        <section className="flex min-h-0 min-w-0 flex-1 flex-col">
          <div className="shrink-0">
            <AuctionToolbar
              isPanelOpen={isPanelOpen}
              onTogglePanel={() => setIsPanelOpen((open) => !open)}
              onOpenFilterSheet={() => setIsFilterSheetOpen(true)}
              selectedFilterCount={countSelectedOptions(selection)}
              auctionType={auctionType}
              onChangeAuctionType={handleChangeAuctionType}
              sort={sort}
              onChangeSort={handleChangeSort}
            />

            <h1 className="py-sm text-lg font-bold text-ink">
              {keyword ? LIST_TEXT.searchResultPrefix(keyword) : LIST_TEXT.resultCountPrefix}{' '}
              <span className="text-primary">{matched.length.toLocaleString('ko-KR')}</span>
              {LIST_TEXT.resultCountSuffix}
            </h1>
          </div>

          {/* 스크롤은 이 영역에만 준다 */}
          <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto pb-base">
            {/* 모바일은 가로 리스트 행 1열, md 이상은 항상 4열 (형태는 AuctionCard가 맞춘다) */}
            {visibleAuctions.length > 0 ? (
              <div className="grid grid-cols-1 gap-sm md:grid-cols-4">
                {visibleAuctions.map((auction) => (
                  <AuctionCard
                    key={auction.auctionId}
                    auction={auction}
                    isBookmarked={bookmarks.has(auction.auctionId)}
                    onToggleBookmark={handleToggleBookmark}
                  />
                ))}
              </div>
            ) : (
              <div className="flex h-full flex-col items-center justify-center gap-sm">
                <span className="flex h-14 w-14 items-center justify-center rounded-full bg-surface-soft text-muted">
                  <SearchX size={24} />
                </span>
                <p className="text-base font-semibold text-ink">{LIST_TEXT.emptyTitle}</p>
                <p className="text-sm text-muted">{LIST_TEXT.emptyDescription}</p>
                <Button variant="secondary" onClick={handleResetFilter} className="mt-xs">
                  {LIST_TEXT.emptyAction}
                </Button>
              </div>
            )}
          </div>

          {visibleAuctions.length > 0 && (
            <div className="shrink-0 border-t border-hairline-soft pt-base">
              <Pagination
                currentPage={currentPage}
                totalPages={totalPages}
                onChange={handleChangePage}
              />
            </div>
          )}
        </section>
      </div>

      {isFilterSheetOpen && (
        <FilterSheet onClose={() => setIsFilterSheetOpen(false)}>{filterPanel}</FilterSheet>
      )}

      {/* 모바일 시트 위에서도 + 를 누를 수 있어야 하므로 모달을 시트보다 뒤에 그린다. */}
      {openGroupId && (
        <FilterModal
          initialGroupId={openGroupId}
          selection={selection}
          countFor={countFor}
          onApply={handleApplyFilter}
          onClose={() => setOpenGroupId(null)}
        />
      )}
    </main>
  )
}

export default AuctionListPage
