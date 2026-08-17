import { useCallback, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ServerClockContext } from './server-clock-context'

/*
 * 마감 카운트다운과 하락 가격은 모두 "서버가 보는 지금"을 기준으로 계산해야 한다.
 * 보정값을 화면마다 따로 들고 있으면 SSE를 여는 화면만 정확해지므로, 앱 전체가 하나의
 * 보정값을 공유한다. 처음에는 조회 응답의 serverTime으로 시작하고, 이후에는 SSE
 * 하트비트가 계속 표본을 채워 보정한다(useAuctionEvents·useTradeEvents).
 */

/*
 * 표본은 서버가 시각을 찍어 보낸 뒤 편도 지연만큼 늦게 도착하므로, 측정한 오프셋은
 * 항상 실제보다 지연분만큼 작다(offset = 실제 − 편도지연). 그래서 창 안에서 가장 큰
 * 오프셋, 즉 가장 덜 지연된 표본을 실제에 가장 가까운 값으로 본다.
 */
const SAMPLE_TTL_MS = 60_000
/** 창에 남길 표본 수 상한. 하트비트가 15초 주기라 TTL 안에서 이 정도면 충분하다. */
const MAX_SAMPLES = 8
/*
 * 왕복 지연은 매번 흔들리므로 그 흔들림까지 반영하면 카운트다운이 초 단위로 앞뒤로 튄다.
 * 이 폭을 넘는 차이만 실제 시계 오차로 보고 화면에 반영한다.
 */
const RESYNC_THRESHOLD_MS = 250

interface ClockSample {
  offsetMs: number
  receivedAt: number
}

function bestOffsetMs(samples: ClockSample[]) {
  return samples.reduce(
    (best, sample) => Math.max(best, sample.offsetMs),
    Number.NEGATIVE_INFINITY,
  )
}

function ServerClockProvider({ children }: { children: ReactNode }) {
  const [serverOffsetMs, setServerOffsetMs] = useState(0)
  const samplesRef = useRef<ClockSample[]>([])

  const synchronize = useCallback((serverTime: number) => {
    if (!Number.isFinite(serverTime)) return

    const receivedAt = Date.now()
    /* 오래된 표본은 버린다. 절전 복귀나 로컬 시계 변경 뒤에도 새 표본이 곧 기준이 된다. */
    const fresh = samplesRef.current
      .filter((sample) => receivedAt - sample.receivedAt < SAMPLE_TTL_MS)
      .concat({ offsetMs: serverTime - receivedAt, receivedAt })
    samplesRef.current = fresh.slice(-MAX_SAMPLES)

    const measured = bestOffsetMs(samplesRef.current)
    setServerOffsetMs((current) => (
      Math.abs(measured - current) < RESYNC_THRESHOLD_MS ? current : measured
    ))
  }, [])

  const value = useMemo(
    () => ({ serverOffsetMs, synchronize }),
    [serverOffsetMs, synchronize],
  )

  return (
    <ServerClockContext.Provider value={value}>{children}</ServerClockContext.Provider>
  )
}

export default ServerClockProvider
export { MAX_SAMPLES, RESYNC_THRESHOLD_MS, SAMPLE_TTL_MS }
