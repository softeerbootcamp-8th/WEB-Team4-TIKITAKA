import { useState } from 'react'
import { BadgeCheck, Star, MapPin, Truck, Clock } from 'lucide-react'
import styles from './ProductTabs.module.css'

const TABS = ['상품 정보', '배송·거래', '판매자 정보']

export default function ProductTabs({ auction }) {
  const [active, setActive] = useState(TABS[0])

  return (
    <div className={styles.card}>
      <div className={styles.tabRow}>
        {TABS.map((tab) => (
          <button
            key={tab}
            className={`${styles.tab} ${active === tab ? styles.tabActive : ''}`}
            onClick={() => setActive(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

      {active === '상품 정보' && (
        <div className={styles.panel}>
          <h3 className={styles.h}>상품 상태</h3>
          <p className={styles.p}>{auction.condition}</p>

          <h3 className={styles.h}>구성품</h3>
          <p className={styles.p}>{auction.components.join(', ')}</p>

          <SellerRow seller={auction.seller} />
        </div>
      )}

      {active === '배송·거래' && (
        <div className={styles.panel}>
          <div className={styles.infoRow}>
            <Truck size={16} />
            <span>택배거래 · 직거래 (강남구 인근) 모두 가능</span>
          </div>
          <div className={styles.infoRow}>
            <MapPin size={16} />
            <span>서울 강남구 · 거래 후 위치 상세 공개</span>
          </div>
          <div className={styles.infoRow}>
            <Clock size={16} />
            <span>낙찰 후 24시간 이내 결제, 결제 확인 즉시 발송</span>
          </div>
        </div>
      )}

      {active === '판매자 정보' && (
        <div className={styles.panel}>
          <SellerRow seller={auction.seller} expanded />
          <div className={styles.infoRow}>
            <Clock size={16} />
            <span>평균 응답 시간 10분 이내 · 최근 활동 5분 전</span>
          </div>
        </div>
      )}
    </div>
  )
}

function SellerRow({ seller, expanded }) {
  return (
    <div className={styles.sellerRow}>
      <div className={styles.sellerAvatar}>{seller.name[0]}</div>
      <div className={styles.sellerInfo}>
        <div className={styles.sellerName}>
          {seller.name}
          {seller.verified && <BadgeCheck size={14} className={styles.verifiedIcon} />}
        </div>
        <div className={styles.sellerMeta}>
          거래 {seller.dealCount}회 · <Star size={12} className={styles.starIcon} /> {seller.rating}
        </div>
      </div>
      {expanded && <span className={styles.verifiedBadge}>인증 완료</span>}
    </div>
  )
}
