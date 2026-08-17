import { useEffect, useRef, useState } from 'react'
import type { TradeStatus } from '../lib/api/mypage'
import { apiUrl } from '../lib/api/client'
import { useServerClock } from './useServerClock'

export interface TradeLiveState {
  tradeId: number
  auctionId: number
  status: TradeStatus
}

type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'reconnecting'

const TRADE_STATE_EVENT = 'trade-state'
const HEARTBEAT_EVENT = 'heartbeat'

function parseEvent<T>(event: Event): T | null {
  if (!(event instanceof MessageEvent) || typeof event.data !== 'string') return null
  try {
    return JSON.parse(event.data) as T
  } catch {
    return null
  }
}

/*
 * 거래 화면 전용 SSE 구독. 경매 스트림과 달리 인증이 필요한 개인화 채널이므로
 * withCredentials로 세션 쿠키를 함께 보낸다. 페이로드는 연락처 같은 개인 정보를 담지 않고
 * 상태만 전하므로, 상태가 바뀌면 화면은 인증 조회로 상세를 다시 읽어 반영한다.
 * tradeId가 null이면(종료된 거래 등) 연결하지 않는다.
 */
export function useTradeEvents(
  tradeId: number | null,
  onState: (state: TradeLiveState) => void,
): ConnectionStatus {
  const handlerRef = useRef(onState)
  handlerRef.current = onState
  /* 하트비트는 모든 SSE 연결에 함께 내려오므로, 거래 화면도 같은 방식으로 시계를 보정한다. */
  const { synchronize } = useServerClock()
  const [status, setStatus] = useState<ConnectionStatus>('idle')

  useEffect(() => {
    if (tradeId === null) {
      setStatus('idle')
      return
    }

    const source = new EventSource(apiUrl(`/api/v1/trades/${tradeId}/events`), {
      withCredentials: true,
    })
    setStatus('connecting')

    const handleState = (event: Event) => {
      const state = parseEvent<TradeLiveState>(event)
      if (state && state.tradeId === tradeId) handlerRef.current(state)
    }
    const handleHeartbeat = (event: Event) => {
      const serverTime = parseEvent<number>(event)
      if (typeof serverTime === 'number' && Number.isFinite(serverTime)) {
        synchronize(serverTime)
      }
    }

    source.onopen = () => setStatus('open')
    source.onerror = () => setStatus('reconnecting')
    source.addEventListener(TRADE_STATE_EVENT, handleState)
    source.addEventListener(HEARTBEAT_EVENT, handleHeartbeat)

    return () => {
      source.close()
      setStatus('idle')
    }
  }, [tradeId, synchronize])

  return status
}
