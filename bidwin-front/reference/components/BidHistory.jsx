import { formatWon, formatTimeOfDay } from '../utils/format'
import styles from './BidHistory.module.css'

export default function BidHistory({ bidLog }) {
  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <h2 className={styles.title}>입찰 기록</h2>
        <span className={styles.count}>전체 {bidLog.length}회</span>
      </div>
      <div className={styles.list}>
        {bidLog.map((bid, i) => (
          <div key={bid.id} className={`${styles.row} ${i === 0 ? styles.rowNew : ''} ${bid.isMe ? styles.rowMe : ''}`}>
            <span className={`${styles.time} mono`}>{formatTimeOfDay(bid.time)}</span>
            <span className={styles.user}>
              {bid.isMe ? <span className={styles.meTag}>나의 입찰</span> : bid.user}
            </span>
            <span className={`${styles.amount} mono`}>{formatWon(bid.amount)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
