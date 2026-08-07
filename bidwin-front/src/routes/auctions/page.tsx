import { SearchX } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import { useAuctionEvents } from '../../hooks/useAuctionEvents'
import { requestAuctionList } from '../../lib/api/auctions'
import type { AuctionListResponse } from '../../lib/api/auctions'
import AuctionCard from './components/AuctionCard'
import AuctionToolbar from './components/AuctionToolbar'
import Pagination from './components/Pagination'
import { FIRST_PAGE, LIST_TEXT, PAGE_SIZE, SEARCH_QUERY_PARAM } from './constants'
import { DEFAULT_SORT } from './query'
import type { SortKey } from './query'
import type { AuctionTypeFilter } from './types'

const CONTENT_HEIGHT_CLASS = 'h-[calc(100dvh-4rem)]'

function AuctionListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const keyword = searchParams.get(SEARCH_QUERY_PARAM) ?? ''
  const [auctionType, setAuctionType] = useState<AuctionTypeFilter>('ALL')
  const [sort, setSort] = useState<SortKey>(DEFAULT_SORT)
  const queryKey = `${keyword}\u0000${auctionType}\u0000${sort}`
  const [pagination, setPagination] = useState({ queryKey, page: FIRST_PAGE })
  const page = pagination.queryKey === queryKey ? pagination.page : FIRST_PAGE
  const [response, setResponse] = useState<AuctionListResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryToken, setRetryToken] = useState(0)
  const [bookmarks, setBookmarks] = useState<ReadonlySet<number>>(() => new Set())
  const snapshotRef = useRef<{ queryKey: string; serverTime: number } | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const snapshot = snapshotRef.current?.queryKey === queryKey
      ? snapshotRef.current.serverTime
      : undefined

    setIsLoading(true)
    setError(null)
    setResponse(null)
    requestAuctionList({
      keyword,
      auctionType,
      sort,
      page,
      size: PAGE_SIZE,
      asOf: snapshot,
    }, controller.signal).then((result) => {
      if (!active) return
      setIsLoading(false)
      if (!result.ok) {
        setError(result.message)
        return
      }
      snapshotRef.current = { queryKey, serverTime: result.data.asOf }
      setResponse(result.data)
    })

    return () => {
      active = false
      controller.abort()
    }
  }, [auctionType, keyword, page, queryKey, retryToken, sort])

  const auctionIds = response?.items.map((auction) => auction.auctionId) ?? []
  useAuctionEvents('list', auctionIds, {
    onState: (state) => {
      setResponse((current) => {
        if (!current) return current
        let changed = false
        const items = current.items.map((auction) => {
          if (auction.auctionId !== state.auctionId || state.revision < auction.revision) {
            return auction
          }
          changed = true
          return {
            ...auction,
            revision: state.revision,
            status: state.status,
            currentPrice: state.currentPrice,
            bidCount: state.bidCount,
          }
        })
        return changed ? { ...current, items } : current
      })
    },
  })

  function changePage(nextPage: number) {
    setPagination({ queryKey, page: nextPage })
    listRef.current?.scrollTo({ top: 0 })
  }

  function toggleBookmark(auctionId: number) {
    setBookmarks((current) => {
      const next = new Set(current)
      if (!next.delete(auctionId)) next.add(auctionId)
      return next
    })
  }

  function resetSearch() {
    setAuctionType('ALL')
    setSort(DEFAULT_SORT)
    setSearchParams({})
  }

  const totalCount = response?.totalCount ?? 0
  const items = response?.items ?? []

  return (
    <main className={`mx-auto flex ${CONTENT_HEIGHT_CLASS} max-w-[1200px] flex-col px-lg py-base`}>
      <section className="flex min-h-0 min-w-0 flex-1 flex-col">
        <div className="shrink-0">
          <AuctionToolbar
            auctionType={auctionType}
            onChangeAuctionType={setAuctionType}
            sort={sort}
            onChangeSort={setSort}
          />
          <h1 className="py-sm text-lg font-bold text-ink">
            {keyword ? LIST_TEXT.searchResultPrefix(keyword) : LIST_TEXT.resultCountPrefix}{' '}
            <span className="text-primary">{totalCount.toLocaleString('ko-KR')}</span>
            {LIST_TEXT.resultCountSuffix}
          </h1>
        </div>

        <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto pb-base">
          {isLoading ? (
            <div className="flex h-full items-center justify-center text-sm text-muted">
              경매를 불러오는 중…
            </div>
          ) : error ? (
            <div className="flex h-full flex-col items-center justify-center gap-sm text-center">
              <p className="text-base font-semibold text-ink">경매를 불러오지 못했어요.</p>
              <p className="text-sm text-muted">{error}</p>
              <Button variant="secondary" onClick={() => setRetryToken((value) => value + 1)}>
                다시 시도
              </Button>
            </div>
          ) : items.length > 0 && response ? (
            <div className="grid grid-cols-1 gap-sm md:grid-cols-4">
              {items.map((auction) => (
                <AuctionCard
                  key={auction.auctionId}
                  auction={auction}
                  serverTime={response.serverTime}
                  isBookmarked={bookmarks.has(auction.auctionId)}
                  onToggleBookmark={toggleBookmark}
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
              <Button variant="secondary" onClick={resetSearch} className="mt-xs">
                검색 초기화
              </Button>
            </div>
          )}
        </div>

        {response && items.length > 0 && (
          <div className="shrink-0 border-t border-hairline-soft pt-base">
            <Pagination
              currentPage={response.page}
              totalPages={response.totalPages}
              onChange={changePage}
            />
          </div>
        )}
      </section>
    </main>
  )
}

export default AuctionListPage
