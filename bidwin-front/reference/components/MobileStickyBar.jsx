import { formatWon, formatClock } from '../utils/format'
import styles from './MobileStickyBar.module.css'

export default function MobileStickyBar({ currentPrice, nextMinBid, remaining, isUrgent, ended, onQuickBid }) {
  return (
    <div className={styles.bar}>
      <div className={styles.priceCol}>
        <span className={styles.label}>현재가</span>
        <span className={`${styles.price} mono`}>{formatWon(currentPrice)}</span>
        {!ended && (
          <span className={`${styles.timer} mono ${isUrgent ? styles.timerUrgent : ''}`}>
            {formatClock(remaining)}
          </span>
        )}
      </div>
      <button className={styles.cta} disabled={ended} onClick={() => onQuickBid(nextMinBid)}>
        {ended ? '경매 종료' : `${formatWon(nextMinBid)} 입찰`}
      </button>
    </div>
  )
}
