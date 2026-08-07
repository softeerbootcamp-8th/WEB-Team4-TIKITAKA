import { useEffect, useRef, useState } from 'react'
import type { BidHistoryItem, BidHistoryResponse } from '../lib/api/auctions'
import { apiUrl } from '../lib/api/client'

export interface AuctionLiveState {
  auctionId: number
  revision: number
  auctionType: 'UP' | 'DOWN'
  status: 'OPEN' | 'BID_ONGOING' | 'WINNER_DETERMINING' | 'COMPLETED' | 'UNSOLD'
  currentPrice: number
  bidCount: number
}

interface AuctionEventHandlers {
  onState?: (state: AuctionLiveState) => void
  onBidCreated?: (bid: BidHistoryItem) => void
  onBidHistorySnapshot?: (history: BidHistoryResponse) => void
}

type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'reconnecting'

function parseEvent<T>(event: Event): T | null {
  if (!(event instanceof MessageEvent) || typeof event.data !== 'string') return null
  try {
    return JSON.parse(event.data) as T
  } catch {
    return null
  }
}

export function useAuctionEvents(
  mode: 'detail' | 'list',
  auctionIds: number[],
  handlers: AuctionEventHandlers,
): ConnectionStatus {
  const handlersRef = useRef(handlers)
  handlersRef.current = handlers
  const uniqueIds = [...new Set(auctionIds)].sort((a, b) => a - b)
  const idsKey = uniqueIds.join(',')
  const [status, setStatus] = useState<ConnectionStatus>('idle')

  useEffect(() => {
    if (idsKey.length === 0) {
      setStatus('idle')
      return
    }

    const ids = idsKey.split(',').map(Number)
    const path = mode === 'detail'
      ? `/api/v1/auctions/${ids[0]}/events`
      : `/api/v1/auctions/events?${ids.map((id) => `auctionIds=${id}`).join('&')}`
    const source = new EventSource(apiUrl(path))
    setStatus('connecting')

    const handleState = (event: Event) => {
      const state = parseEvent<AuctionLiveState>(event)
      if (state && ids.includes(state.auctionId)) handlersRef.current.onState?.(state)
    }
    const handleBid = (event: Event) => {
      const bid = parseEvent<BidHistoryItem>(event)
      if (bid?.entryId) handlersRef.current.onBidCreated?.(bid)
    }
    const handleHistory = (event: Event) => {
      const history = parseEvent<BidHistoryResponse>(event)
      if (history && Array.isArray(history.bidLog)) {
        handlersRef.current.onBidHistorySnapshot?.(history)
      }
    }

    source.onopen = () => setStatus('open')
    source.onerror = () => setStatus('reconnecting')
    source.addEventListener('auction-state', handleState)
    source.addEventListener('bid-created', handleBid)
    source.addEventListener('bid-history-snapshot', handleHistory)

    return () => {
      source.close()
      setStatus('idle')
    }
  }, [idsKey, mode])

  return status
}
