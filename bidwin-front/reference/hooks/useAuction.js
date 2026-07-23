import { useCallback, useEffect, useRef, useState } from 'react'

const AUCTION_DURATION_MS = 5 * 60 * 1000
const LATE_BID_WINDOW_MS = 60 * 1000
const LATE_BID_EXTENSION_MS = 60 * 1000
const TOAST_DURATION_MS = 2600

const MOCK_AUCTION = {
  breadcrumb: ['전자기기', '오디오'],
  title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰 (미개봉)',
  auctionType: '경매',
  productNumber: 'BW-240311',
  condition: '미개봉 새 상품',
  components: ['본체', '충전 케이블', '파우치', '보증서'],
  seller: { name: '급처직거래', verified: true, dealCount: 128, rating: 4.9 },
  startPrice: 180000,
  bidUnit: 10000,
  deposit: 30000,
  buyNowPrice: 320000,
}

const MOCK_BID_USERS = ['민준**', '서연**', '지호**', '하윤**', '도윤**']

function makeId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function seedBidLog(startPrice, bidUnit) {
  const now = Date.now()
  return [3, 2, 1].map((minutesAgo, i) => ({
    id: makeId(),
    time: new Date(now - minutesAgo * 60 * 1000),
    user: MOCK_BID_USERS[i % MOCK_BID_USERS.length],
    amount: startPrice + bidUnit * (i + 1),
    isMe: false,
  }))
}

export function useAuction() {
  const [deadline, setDeadline] = useState(() => Date.now() + AUCTION_DURATION_MS)
  const [bidLog, setBidLog] = useState(() => seedBidLog(MOCK_AUCTION.startPrice, MOCK_AUCTION.bidUnit))
  const [currentPrice, setCurrentPrice] = useState(
    () => MOCK_AUCTION.startPrice + MOCK_AUCTION.bidUnit * 3,
  )
  const [viewCount] = useState(214)
  const [interestCount, setInterestCount] = useState(19)
  const [interested, setInterested] = useState(false)
  const [ended, setEnded] = useState(false)
  const [toast, setToast] = useState(null)

  const toastTimerRef = useRef(null)

  const showToast = useCallback((message, tone = 'default') => {
    setToast({ message, tone, key: makeId() })
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    toastTimerRef.current = setTimeout(() => setToast(null), TOAST_DURATION_MS)
  }, [])

  useEffect(() => () => toastTimerRef.current && clearTimeout(toastTimerRef.current), [])

  useEffect(() => {
    if (ended) return
    const id = setInterval(() => {
      if (Date.now() >= deadline) setEnded(true)
    }, 1000)
    return () => clearInterval(id)
  }, [deadline, ended])

  const bidCount = bidLog.length
  const nextMinBid = currentPrice + MOCK_AUCTION.bidUnit

  const placeBid = useCallback(
    async (amount) => {
      if (ended) return { ok: false, message: '이미 종료된 경매예요' }
      if (amount < nextMinBid) {
        return { ok: false, message: `최소 ${nextMinBid.toLocaleString('ko-KR')}원 이상 입찰해주세요` }
      }

      setCurrentPrice(amount)
      setBidLog((prev) => [{ id: makeId(), time: new Date(), user: '나', amount, isMe: true }, ...prev])

      if (deadline - Date.now() <= LATE_BID_WINDOW_MS) {
        setDeadline((prev) => prev + LATE_BID_EXTENSION_MS)
        showToast('막판 입찰로 마감이 60초 연장됐어요', 'info')
      } else {
        showToast(`${amount.toLocaleString('ko-KR')}원으로 입찰했어요`, 'success')
      }

      return { ok: true }
    },
    [deadline, ended, nextMinBid, showToast],
  )

  const buyNow = useCallback(async () => {
    if (ended) return
    setCurrentPrice(MOCK_AUCTION.buyNowPrice)
    setBidLog((prev) => [
      { id: makeId(), time: new Date(), user: '나', amount: MOCK_AUCTION.buyNowPrice, isMe: true },
      ...prev,
    ])
    setEnded(true)
    showToast('즉시구매로 낙찰되었어요!', 'success')
  }, [ended, showToast])

  const toggleInterest = useCallback(() => {
    setInterested((prev) => !prev)
    setInterestCount((prev) => (interested ? prev - 1 : prev + 1))
  }, [interested])

  return {
    auction: MOCK_AUCTION,
    deadline,
    bidLog,
    currentPrice,
    bidCount,
    nextMinBid,
    viewCount,
    interestCount,
    interested,
    toggleInterest,
    toast,
    placeBid,
    buyNow,
    ended,
  }
}
