import { useEffect, useRef, useState } from 'react'

/*
 * 값이 방금 바뀌었는지를 알려준다. 마감처럼 한 번뿐인 상태 변화에 애니메이션을 걸 때 쓴다.
 * 첫 렌더는 "바뀐 것"으로 보지 않으므로, 이미 마감된 경매를 열었을 때 마감 연출이 재생되지 않는다.
 */
export function useRecentChange<T>(value: T, holdMs: number) {
  const [isRecent, setIsRecent] = useState(false)
  const previousRef = useRef(value)

  useEffect(() => {
    if (previousRef.current === value) return
    previousRef.current = value
    setIsRecent(true)
    const timer = window.setTimeout(() => setIsRecent(false), holdMs)
    return () => window.clearTimeout(timer)
  }, [value, holdMs])

  return isRecent
}
