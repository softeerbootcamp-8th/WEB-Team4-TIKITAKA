/*
 * 백엔드 API가 없어서, 입찰/구매 후 바뀐 최고가나 하락경매 시작 시각을 메인
 * 페이지와 상세 페이지 양쪽이 똑같이 보게 하기 위한 아주 단순한 메모리 저장소.
 * 새로고침하면 초기화된다. 실제 API 연동 시 이 파일은 걷어내고 서버 응답값을
 * 그대로 쓰면 된다.
 */
const livePrices = new Map<number, number>()

export function getLiveAuctionPrice(auctionId: number): number | undefined {
  return livePrices.get(auctionId)
}

export function setLiveAuctionPrice(auctionId: number, price: number): void {
  livePrices.set(auctionId, price)
}

/*
 * 하락경매의 startedAt은 각 페이지 파일이 module이 처음 로드될 때 각자
 * Date.now()를 찍어서 정하는데, 홈/상세가 서로 다른 시점에 로드되면 기준 시각이
 * 어긋난다. 먼저 물어본 페이지가 실제 시작 시각을 "선점"하고, 나중에 같은
 * auctionId를 묻는 페이지는 그 값을 그대로 재사용해서 항상 같은 결과가 나오게 한다.
 */
const auctionStartTimes = new Map<number, number>()

export function getOrInitStartedAt(auctionId: number): number {
  const existing = auctionStartTimes.get(auctionId)
  if (existing !== undefined) return existing

  const now = Date.now()
  auctionStartTimes.set(auctionId, now)
  return now
}
