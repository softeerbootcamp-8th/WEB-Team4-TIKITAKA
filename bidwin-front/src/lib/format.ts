export function formatWon(amount: number) {
  return `${amount.toLocaleString('ko-KR')}원`
}

export function formatClock(totalSeconds: number) {
  const safe = Math.max(0, totalSeconds)
  const m = Math.floor(safe / 60)
  const s = Math.floor(safe % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

/*
 * 마감까지 남은 시간을 가장 큰 단위 하나로만 보여준다("2일", "3시간", "12분").
 * formatClock은 분·초 단위 카운트다운용이라 며칠 남은 결제·수령 기한에는 맞지 않는다.
 * 이미 지난 기한은 호출하는 쪽에서 따로 표시하라고 null을 돌려준다.
 */
export function formatRemainingDuration(remainingMs: number) {
  if (remainingMs <= 0) return null
  if (remainingMs >= DAY_MS) return `${Math.floor(remainingMs / DAY_MS)}일`
  if (remainingMs >= HOUR_MS) return `${Math.floor(remainingMs / HOUR_MS)}시간`
  return `${Math.max(1, Math.floor(remainingMs / MINUTE_MS))}분`
}

/** 가입일처럼 시각이 필요 없는 날짜를 "2026년 7월 19일"로 보여준다. */
export function formatDate(date: Date) {
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

export function formatTimeOfDay(date: Date) {
  return date.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

/* 하이픈 없이 저장·전송하는 전화번호를 010-1234-5678 형태로 보여준다. */
const PHONE_NUMBER_PREFIX_LENGTH = 3
const PHONE_NUMBER_SUFFIX_LENGTH = 4
const PHONE_NUMBER_MIN_LENGTH = PHONE_NUMBER_PREFIX_LENGTH + PHONE_NUMBER_SUFFIX_LENGTH + 1
const PHONE_NUMBER_SEPARATOR = '-'

/* 입력 중인 번호를 끊어 보여줄 자릿수. 앞에서부터 채우므로 하이픈이 뒤로 튀지 않는다. */
const PHONE_NUMBER_INPUT_GROUP_LENGTHS = [3, 4, 4]

/* 자릿수가 다 채워진 번호를 보여줄 때 쓴다(10자리 010-123-4567, 11자리 010-1234-5678). */
export function formatPhoneNumber(digits: string) {
  if (digits.length < PHONE_NUMBER_MIN_LENGTH) return digits

  const prefix = digits.slice(0, PHONE_NUMBER_PREFIX_LENGTH)
  const middle = digits.slice(PHONE_NUMBER_PREFIX_LENGTH, -PHONE_NUMBER_SUFFIX_LENGTH)
  const suffix = digits.slice(-PHONE_NUMBER_SUFFIX_LENGTH)
  return [prefix, middle, suffix].join(PHONE_NUMBER_SEPARATOR)
}

/*
 * 입력 중인(아직 자릿수가 덜 찬) 번호를 3-4-4로 끊어 보여준다.
 * 마지막 그룹이 비면 하이픈도 붙이지 않아, 지우는 동안 하이픈만 남는 상태가 없다.
 */
export function formatPartialPhoneNumber(digits: string) {
  const groups: string[] = []
  let rest = digits

  for (const groupLength of PHONE_NUMBER_INPUT_GROUP_LENGTHS) {
    if (!rest) break
    groups.push(rest.slice(0, groupLength))
    rest = rest.slice(groupLength)
  }

  return groups.join(PHONE_NUMBER_SEPARATOR)
}

export function formatDeadline(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}.${m}.${d} ${hh}:${mm} 마감`
}
