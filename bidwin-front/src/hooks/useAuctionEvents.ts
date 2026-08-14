import { useCallback, useEffect, useRef, useState } from 'react'
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
  onHeartbeat?: (serverTime: number) => void
}

type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'disconnected'

const DETAIL_STALE_AFTER_MS = 45_000
const STALE_CHECK_INTERVAL_MS = 5_000

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
): { status: ConnectionStatus; reconnect: () => void } {
  const handlersRef = useRef(handlers)
  handlersRef.current = handlers
  const uniqueIds = [...new Set(auctionIds)].sort((a, b) => a - b)
  const idsKey = uniqueIds.join(',')
  const [status, setStatus] = useState<ConnectionStatus>('idle')
  const [reconnectToken, setReconnectToken] = useState(0)
  const reconnect = useCallback(() => setReconnectToken((value) => value + 1), [])

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
    let lastActivityAt = Date.now()
    setStatus('connecting')

    const markActivity = () => {
      lastActivityAt = Date.now()
    }

    const handleState = (event: Event) => {
      markActivity()
      const state = parseEvent<AuctionLiveState>(event)
      if (state && ids.includes(state.auctionId)) handlersRef.current.onState?.(state)
    }
    const handleBid = (event: Event) => {
      markActivity()
      const bid = parseEvent<BidHistoryItem>(event)
      if (bid?.entryId) handlersRef.current.onBidCreated?.(bid)
    }
    const handleHistory = (event: Event) => {
      markActivity()
      const history = parseEvent<BidHistoryResponse>(event)
      if (history && Array.isArray(history.bidLog)) {
        handlersRef.current.onBidHistorySnapshot?.(history)
      }
    }
    const handleHeartbeat = (event: Event) => {
      markActivity()
      const serverTime = parseEvent<number>(event)
      if (typeof serverTime === 'number' && Number.isFinite(serverTime)) {
        handlersRef.current.onHeartbeat?.(serverTime)
      }
    }

    source.onopen = () => {
      markActivity()
      setStatus('open')
    }
    source.onerror = () => setStatus(
      mode === 'detail' && source.readyState === EventSource.CLOSED
        ? 'disconnected'
        : 'reconnecting',
    )
    source.addEventListener('auction-state', handleState)
    source.addEventListener('bid-created', handleBid)
    source.addEventListener('bid-history-snapshot', handleHistory)
    source.addEventListener('heartbeat', handleHeartbeat)
    const staleCheckId = mode === 'detail'
      ? window.setInterval(() => {
          if (Date.now() - lastActivityAt < DETAIL_STALE_AFTER_MS) return
          source.close()
          setStatus('disconnected')
        }, STALE_CHECK_INTERVAL_MS)
      : undefined

    return () => {
      if (staleCheckId !== undefined) window.clearInterval(staleCheckId)
      source.close()
    }
  }, [idsKey, mode, reconnectToken])

  return { status, reconnect }
}

export type { ConnectionStatus }
