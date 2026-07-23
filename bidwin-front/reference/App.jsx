import TopNav from './components/TopNav'
import Header from './components/Header'
import ImageGallery from './components/ImageGallery'
import BidPanel from './components/BidPanel'
import BidHistory from './components/BidHistory'
import ProductTabs from './components/ProductTabs'
import MobileStickyBar from './components/MobileStickyBar'
import Toast from './components/Toast'
import { useAuction } from './hooks/useAuction'
import { useCountdown } from './hooks/useCountdown'
import styles from './App.module.css'

export default function App() {
  const {
    auction,
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
  } = useAuction()

  const { remaining, isUrgent } = useCountdown(deadline ?? Date.now())

  if (!auction) {
    return (
      <div id="top" className={styles.page}>
        <TopNav />
        <main className={styles.container}>불러오는 중…</main>
      </div>
    )
  }

  return (
    <div id="top" className={styles.page}>
      <TopNav />
      <main className={styles.container}>
        <Header
          auction={auction}
          interestCount={interestCount}
          interested={interested}
          onToggleInterest={toggleInterest}
        />

        <div className={styles.grid}>
          <div className={styles.gallerySlot}>
            <ImageGallery />
          </div>

          <div className={styles.priceSlot}>
            <BidPanel
              currentPrice={currentPrice}
              bidCount={bidCount}
              bidUnit={auction.bidUnit}
              viewCount={viewCount}
              deposit={auction.deposit}
              buyNowPrice={auction.buyNowPrice}
              nextMinBid={nextMinBid}
              deadline={deadline}
              remaining={remaining}
              isUrgent={isUrgent}
              ended={ended}
              onPlaceBid={placeBid}
              onBuyNow={buyNow}
            />
          </div>

          <div className={styles.tabsSlot}>
            <ProductTabs auction={auction} />
          </div>

          <div className={styles.historySlot}>
            <BidHistory bidLog={bidLog} />
          </div>
        </div>
      </main>

      <MobileStickyBar
        currentPrice={currentPrice}
        nextMinBid={nextMinBid}
        remaining={remaining}
        isUrgent={isUrgent}
        ended={ended}
        onQuickBid={placeBid}
      />
      <Toast toast={toast} />
    </div>
  )
}
