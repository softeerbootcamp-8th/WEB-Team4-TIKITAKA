import { createContext } from 'react'

export interface ServerClockValue {
  /** 서버 시각 − 브라우저 시각. 화면은 Date.now()에 이 값을 더해 서버 기준 시각을 만든다. */
  serverOffsetMs: number
  /** SSE 하트비트나 조회 응답에 실려온 서버 시각으로 보정한다. */
  synchronize: (serverTime: number) => void
}

export const ServerClockContext = createContext<ServerClockValue | null>(null)
