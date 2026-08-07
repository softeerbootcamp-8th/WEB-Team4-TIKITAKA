import { X } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { DRAWER_TRANSITION_MS, MY_INFO_TEXT } from '../constants'
import type { MyProfile } from '../types'
import LeaveAccountModal from './LeaveAccountModal'
import NicknameForm from './NicknameForm'
import PasswordChangeForm from './PasswordChangeForm'
import ProfileImageField from './ProfileImageField'

/*
 * 내 정보 관리 드로어. 화면을 옮기지 않고 오른쪽에서 밀려 들어온다.
 *
 * 열림/닫힘을 두 단계로 나눠 둔 이유:
 *   isRendered — DOM에 붙어 있는지. 닫는 애니메이션이 끝난 뒤에 떼어 낸다.
 *   isEntered  — 제자리로 밀려 들어왔는지. 붙자마자 바꾸면 전환이 생략돼서 한 프레임 뒤에 바꾼다.
 * DOM에서 떨어질 때 안쪽 폼도 같이 사라지므로, 다시 열면 입력값이 비워진 상태로 시작한다.
 */
const ESCAPE_KEY = 'Escape'
const TAB_KEY = 'Tab'
const LOCKED_BODY_OVERFLOW = 'hidden'
const CLOSE_ICON_SIZE = 18

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

const SECTION_CLASS = 'flex flex-col gap-sm border-t border-hairline-soft px-lg py-lg'

interface MyInfoDrawerProps {
  isOpen: boolean
  onClose: () => void
  profile: MyProfile
  onChangeNickname: (nickname: string) => void
  onChangeImage: (profileImageUrl: string | null) => void
  /** 값이 있으면 탈퇴를 막고 그 이유를 보여준다. */
  leaveBlockReason?: string
}

function MyInfoDrawer({
  isOpen,
  onClose,
  profile,
  onChangeNickname,
  onChangeImage,
  leaveBlockReason,
}: MyInfoDrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  const [isRendered, setIsRendered] = useState(false)
  const [isEntered, setIsEntered] = useState(false)
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false)

  useEffect(() => {
    if (isOpen) {
      setIsRendered(true)
      /* 붙은 직후에는 브라우저가 시작 위치를 잡지 못해 전환이 생략된다. 두 프레임 뒤에 옮긴다. */
      let innerFrame = 0
      const outerFrame = requestAnimationFrame(() => {
        innerFrame = requestAnimationFrame(() => setIsEntered(true))
      })
      return () => {
        cancelAnimationFrame(outerFrame)
        cancelAnimationFrame(innerFrame)
      }
    }

    setIsEntered(false)
    const timer = setTimeout(() => setIsRendered(false), DRAWER_TRANSITION_MS)
    return () => clearTimeout(timer)
  }, [isOpen])

  /* 드로어가 떠 있는 동안 뒤쪽 페이지가 스크롤되지 않게 막는다. */
  useEffect(() => {
    if (!isRendered) return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = LOCKED_BODY_OVERFLOW

    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [isRendered])

  /* 열릴 때 포커스를 드로어 안으로 옮기고, 닫히면 열었던 자리로 돌려준다. */
  useEffect(() => {
    if (!isRendered) return

    const previouslyFocused = document.activeElement
    panelRef.current?.focus()

    return () => {
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus()
    }
  }, [isRendered])

  /*
   * ESC로 닫고, Tab 포커스는 드로어 안에서만 돌린다.
   * 탈퇴 모달이 열려 있는 동안에는 그쪽이 같은 일을 하므로 여기서는 손을 뗀다.
   */
  useEffect(() => {
    if (!isRendered || isLeaveModalOpen) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === ESCAPE_KEY) {
        onClose()
        return
      }

      const panel = panelRef.current
      if (event.key !== TAB_KEY || panel === null) return

      const focusable = panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
      if (focusable.length === 0) return

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      const active = document.activeElement

      if (event.shiftKey && (active === first || active === panel)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isRendered, isLeaveModalOpen, onClose])

  if (!isRendered) return null

  return (
    <>
      <div className="fixed inset-0 z-50">
        <div
          aria-hidden
          onClick={onClose}
          className={`absolute inset-0 bg-ink/40 transition-opacity duration-300 motion-reduce:transition-none ${
            isEntered ? 'opacity-100' : 'opacity-0'
          }`}
        />

        <div
          ref={panelRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          aria-describedby={descriptionId}
          tabIndex={-1}
          className={`absolute right-0 top-0 flex h-dvh w-full max-w-[420px] flex-col bg-canvas shadow-card outline-none transition-transform duration-300 motion-reduce:transition-none ${
            isEntered ? 'translate-x-0' : 'translate-x-full'
          }`}
        >
          <div className="flex shrink-0 items-start gap-base px-lg py-base">
            <div className="flex flex-1 flex-col gap-xxs">
              <h2 id={titleId} className="text-xl font-bold text-ink">
                {MY_INFO_TEXT.title}
              </h2>
              <p id={descriptionId} className="text-sm text-body">
                {MY_INFO_TEXT.description}
              </p>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label={MY_INFO_TEXT.close}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-strong hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <X size={CLOSE_ICON_SIZE} />
            </button>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto">
            <section className={SECTION_CLASS}>
              <h3 className="text-sm font-bold text-ink">{MY_INFO_TEXT.imageSectionTitle}</h3>
              <ProfileImageField
                nickname={profile.nickname}
                imageUrl={profile.profileImageUrl}
                onChangeImage={onChangeImage}
              />
            </section>

            <section className={SECTION_CLASS}>
              <h3 className="text-sm font-bold text-ink">{MY_INFO_TEXT.nicknameSectionTitle}</h3>
              <NicknameForm nickname={profile.nickname} onChangeNickname={onChangeNickname} />
            </section>

            <section className={SECTION_CLASS}>
              <h3 className="text-sm font-bold text-ink">{MY_INFO_TEXT.passwordSectionTitle}</h3>
              <PasswordChangeForm />
            </section>

            {/* 탈퇴는 실수로 누르면 안 되는 동작이라, 맨 아래에 작게 둔다. */}
            <div className="border-t border-hairline-soft px-lg py-lg">
              <button
                type="button"
                onClick={() => setIsLeaveModalOpen(true)}
                className="text-xs text-muted underline underline-offset-4 transition-colors hover:text-down focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                {MY_INFO_TEXT.leave}
              </button>
            </div>
          </div>
        </div>
      </div>

      <LeaveAccountModal
        isOpen={isLeaveModalOpen}
        onClose={() => setIsLeaveModalOpen(false)}
        onConfirm={() => setIsLeaveModalOpen(false)}
        blockReason={leaveBlockReason}
      />
    </>
  )
}

export default MyInfoDrawer
