import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useToast } from '../../hooks/useToast'
import ActiveTradeBanner from './components/ActiveTradeBanner'
import DepositCard from './components/DepositCard'
import MyInfoDrawer from './components/MyInfoDrawer'
import MyItemSection from './components/MyItemSection'
import ProfileCard from './components/ProfileCard'
import SettingsSection from './components/SettingsSection'
import {
  BUYING_SECTION_TEXT,
  HISTORY_TAB,
  LEAVE_MODAL_TEXT,
  MYPAGE_TEXT,
  SELLING_SECTION_TEXT,
  historyPath,
} from './constants'
import {
  MOCK_ACTIVE_TRADES,
  MOCK_BUYING_ITEMS,
  MOCK_DEPOSIT,
  MOCK_PROFILE,
  MOCK_SELLING_ITEMS,
} from './mock'
import { toBuyingCard, toSellingCard } from './view'

/*
 * 마이페이지. 경매 목록보다 카드 폭이 좁아야 읽히는 화면이라 컨테이너를 따로 잡는다.
 * (목록 화면은 1200px 안에 카드 4열을 넣지만, 여기는 프로필·보증금처럼 짧은 카드가 대부분이다.)
 */
const CONTENT_WIDTH_CLASS = 'max-w-[960px]'

const AUCTION_LIST_PATH = '/auctions'
const AUCTION_NEW_PATH = '/auctions/new'
const HOME_PATH = '/'

function MyPage() {
  const navigate = useNavigate()
  const { showToast } = useToast()

  /* API 연동 전이라 목 데이터를 초깃값으로 두고, 변경 사항은 화면에서만 반영한다. */
  const [profile, setProfile] = useState(MOCK_PROFILE)
  const [isMyInfoOpen, setIsMyInfoOpen] = useState(false)

  /*
   * 고른 이미지의 미리보기 URL. 드로어가 닫혀도 프로필 카드가 계속 써야 해서
   * 드로어가 아니라 페이지가 들고 있다가, 새 이미지로 바뀌거나 화면을 뜰 때 해제한다.
   */
  const previewUrlRef = useRef<string | null>(null)

  useEffect(
    () => () => {
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
    },
    [],
  )

  function handleChangeImage(file: File | null) {
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
    previewUrlRef.current = file ? URL.createObjectURL(file) : null
    /* TODO: 백엔드 프로필 이미지 업로드 API 연동 (지금은 미리보기까지만 한다) */
    setProfile((current) => ({
      ...current,
      profileImageUrl: previewUrlRef.current ?? undefined,
    }))
  }

  function handleChangeNickname(nickname: string) {
    setProfile((current) => ({ ...current, nickname }))
  }

  function handleLeave() {
    /* TODO: 백엔드 회원 탈퇴 API 연동 */
    setIsMyInfoOpen(false)
    showToast(LEAVE_MODAL_TEXT.done)
    navigate(HOME_PATH)
  }

  /*
   * 상대가 있는 거래나 입찰에 묶인 보증금이 남아 있으면 탈퇴시키지 않는다.
   * 이유는 하나만 보여 주면 되므로 먼저 걸리는 쪽을 쓴다.
   */
  const leaveBlockReason =
    MOCK_ACTIVE_TRADES.length > 0
      ? LEAVE_MODAL_TEXT.blockedByTrade(MOCK_ACTIVE_TRADES.length)
      : MOCK_DEPOSIT.inUse > 0
        ? LEAVE_MODAL_TEXT.blockedByDeposit
        : undefined

  return (
    <main className={`mx-auto flex ${CONTENT_WIDTH_CLASS} flex-col gap-lg px-lg py-xl`}>
      <h1 className="text-2xl font-bold text-ink">{MYPAGE_TEXT.title}</h1>

      <ActiveTradeBanner trades={MOCK_ACTIVE_TRADES} />

      <ProfileCard profile={profile} onManage={() => setIsMyInfoOpen(true)} />

      <DepositCard deposit={MOCK_DEPOSIT} />

      <MyItemSection
        title={SELLING_SECTION_TEXT.title}
        items={MOCK_SELLING_ITEMS.map(toSellingCard)}
        viewAllLabel={SELLING_SECTION_TEXT.viewAll}
        viewAllPath={historyPath(HISTORY_TAB.selling)}
        emptyMessage={SELLING_SECTION_TEXT.empty}
        emptyActionLabel={SELLING_SECTION_TEXT.emptyAction}
        emptyActionPath={AUCTION_NEW_PATH}
      />

      <MyItemSection
        title={BUYING_SECTION_TEXT.title}
        items={MOCK_BUYING_ITEMS.map(toBuyingCard)}
        viewAllLabel={BUYING_SECTION_TEXT.viewAll}
        viewAllPath={historyPath(HISTORY_TAB.purchase)}
        emptyMessage={BUYING_SECTION_TEXT.empty}
        emptyActionLabel={BUYING_SECTION_TEXT.emptyAction}
        emptyActionPath={AUCTION_LIST_PATH}
      />

      <SettingsSection onOpenMyInfo={() => setIsMyInfoOpen(true)} />

      <MyInfoDrawer
        isOpen={isMyInfoOpen}
        onClose={() => setIsMyInfoOpen(false)}
        profile={profile}
        onChangeNickname={handleChangeNickname}
        onChangeImage={handleChangeImage}
        onLeave={handleLeave}
        leaveBlockReason={leaveBlockReason}
      />
    </main>
  )
}

export default MyPage
