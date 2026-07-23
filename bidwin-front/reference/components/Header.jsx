import { Heart, Flag } from 'lucide-react'
import styles from './Header.module.css'

export default function Header({ auction, interestCount, interested, onToggleInterest }) {
  return (
    <div className={styles.wrap}>
      <div className={styles.crumbs}>
        {auction.breadcrumb.map((c, i) => (
          <span key={c}>
            {i > 0 && <span className={styles.sep}>/</span>}
            {c}
          </span>
        ))}
        <span className={styles.sep}>/</span>
        <span className={styles.current}>{auction.title}</span>
      </div>

      <div className={styles.titleRow}>
        <div>
          <h1 className={styles.title}>{auction.title}</h1>
          <div className={styles.meta}>
            {auction.auctionType} · 상품번호 {auction.productNumber}
          </div>
        </div>
        <div className={styles.actions}>
          <button
            className={`${styles.pillBtn} ${interested ? styles.pillBtnActive : ''}`}
            onClick={onToggleInterest}
          >
            <Heart size={15} fill={interested ? 'currentColor' : 'none'} />
            관심 {interestCount}
          </button>
          <button className={styles.pillBtn}>
            <Flag size={15} />
            신고
          </button>
        </div>
      </div>
    </div>
  )
}
