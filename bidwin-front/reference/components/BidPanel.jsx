import { useEffect, useState } from 'react'
import { ShieldCheck, Zap, MessageCircle } from 'lucide-react'
import { formatWon, formatClock, formatDeadline } from '../utils/format'
import styles from './BidPanel.module.css'

const CHIP_MULTIPLIERS = [1, 2, 6]

export default function BidPanel({
  currentPrice,
  bidCount,
  bidUnit,
  viewCount,
  deposit,
  buyNowPrice,
  nextMinBid,
  deadline,
  remaining,
  isUrgent,
  ended,
  onPlaceBid,
  onBuyNow,
}) {
  const [bidAmount, setBidAmount] = useState(nextMinBid)
  const [activeChip, setActiveChip] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    setBidAmount((prev) => (prev < nextMinBid ? nextMinBid : prev))
    setActiveChip(null)
  }, [nextMinBid])

  const handleChip = (mult) => {
    const amount = currentPrice + bidUnit * mult
    setBidAmount(amount)
    setActiveChip(mult)
    setError(null)
  }

  const handleInput = (e) => {
    const raw = e.target.value.replace(/[^0-9]/g, '')
    setBidAmount(raw === '' ? 0 : Number(raw))
    setActiveChip(null)
    setError(null)
  }

  const handleSubmit = async () => {
    const result = await onPlaceBid(bidAmount)
    if (!result.ok) setError(result.message)
  }

  return (
    <div className={styles.card}>
      <div className={styles.statusRow}>
        <span className={`${styles.badge} ${ended ? styles.badgeEnded : styles.badgeLive}`}>
          {!ended && <span className={styles.liveDot} />}
          {ended ? '마감' : '진행 중'}
        </span>
        <span className={styles.badgeMuted}>
          <ShieldCheck size={13} />
          판매자 인증
        </span>
      </div>

      <div className={styles.priceBlock}>
        <div className={styles.priceLabel}>현재 최고가</div>
        <div className={`${styles.price} mono`}>{formatWon(currentPrice)}</div>
        <div className={styles.timerRow}>
          <span className={`${styles.timer} mono ${isUrgent ? styles.timerUrgent : ''}`}>
            {ended ? '경매 종료' : `${formatClock(remaining)} 남음`}
          </span>
          <span className={styles.deadlineText}>· {formatDeadline(new Date(deadline))}</span>
        </div>
      </div>

      <div className={styles.statsRow}>
        <Stat label="입찰 수" value={`${bidCount}회`} />
        <Stat label="입찰 단위" value={formatWon(bidUnit)} />
        <Stat label="조회" value={viewCount.toLocaleString('ko-KR')} />
        <Stat label="보증금" value={formatWon(deposit)} />
      </div>

      {!ended && (
        <>
          <div className={styles.chipRow}>
            {CHIP_MULTIPLIERS.map((mult) => (
              <button
                key={mult}
                className={`${styles.chip} ${activeChip === mult ? styles.chipActive : ''}`}
                onClick={() => handleChip(mult)}
              >
                +{(bidUnit * mult).toLocaleString('ko-KR')}
              </button>
            ))}
          </div>

          <label className={styles.inputLabel} htmlFor="bid-amount">
            입찰 금액
          </label>
          <div className={styles.inputWrap}>
            <input
              id="bid-amount"
              className="mono"
              inputMode="numeric"
              value={bidAmount.toLocaleString('ko-KR')}
              onChange={handleInput}
            />
            <span className={styles.won}>원</span>
          </div>
          {error && <div className={styles.error}>{error}</div>}

          <button className={styles.primaryBtn} onClick={handleSubmit}>
            {bidAmount.toLocaleString('ko-KR')}원으로 입찰하기
          </button>

          <div className={styles.secondaryRow}>
            <button className={styles.secondaryBtn} onClick={onBuyNow}>
              <Zap size={15} />
              즉시구매 {formatWon(buyNowPrice)}
            </button>
            <button className={styles.secondaryBtn}>
              <MessageCircle size={15} />
              문의
            </button>
          </div>

          <p className={styles.notice}>입찰 시 보증금 {formatWon(deposit)}이 잠시 사용 제한됩니다.</p>
        </>
      )}
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className={styles.stat}>
      <div className={styles.statLabel}>{label}</div>
      <div className={`${styles.statValue} mono`}>{value}</div>
    </div>
  )
}
