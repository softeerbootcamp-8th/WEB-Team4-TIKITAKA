import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

const ESCAPE_KEY = 'Escape'
const TAB_KEY = 'Tab'
const CLOSE_LABEL = '닫기'
const LOCKED_BODY_OVERFLOW = 'hidden'
const CLOSE_ICON_SIZE = 18

/* 모달 안에서 Tab 순환에 참여하는 요소들 */
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  description?: string
  children: ReactNode
}

/*
 * 공용 모달. 배경 클릭·ESC·닫기 버튼으로 닫히고, 열려 있는 동안에는
 * 뒤쪽 페이지 스크롤을 막고 Tab 포커스를 모달 안에 붙잡아 둔다.
 */
function Modal({ isOpen, onClose, title, description, children }: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  /*
   * 포커스 이동은 열림·닫힘에만 반응해야 한다.
   * 다른 값(onClose 등)을 의존성에 넣으면 입력 중 리렌더에서 포커스를 다시 빼앗는다.
   */
  useEffect(() => {
    if (!isOpen) return

    const previouslyFocused = document.activeElement
    panelRef.current?.focus()

    return () => {
      /* 닫힌 뒤에는 모달을 열었던 곳으로 포커스를 돌려준다. */
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus()
    }
  }, [isOpen])

  /* 모달이 열려 있는 동안 뒤쪽 페이지가 스크롤되지 않게 막는다. */
  useEffect(() => {
    if (!isOpen) return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = LOCKED_BODY_OVERFLOW

    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [isOpen])

  /* ESC로 닫고, Tab 포커스는 모달 안에서만 순환시킨다. */
  useEffect(() => {
    if (!isOpen) return

    const handleKeyDown = (event: KeyboardEvent) => {
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

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-lg">
      {/* 배경: 마우스 사용자를 위한 닫기 영역. 키보드는 ESC·닫기 버튼을 쓴다. */}
      <div
        aria-hidden
        onClick={onClose}
        className="absolute inset-0 bg-surface-dark/40"
      />

      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className="relative flex w-full max-w-[420px] flex-col gap-lg rounded-xl border border-hairline-soft bg-canvas p-xl shadow-card outline-none"
      >
        <div className="flex items-start gap-base">
          <div className="flex flex-1 flex-col gap-xxs">
            <h2 id={titleId} className="text-xl font-bold text-ink">
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="text-sm text-body">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={CLOSE_LABEL}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-strong hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <X size={CLOSE_ICON_SIZE} />
          </button>
        </div>

        {children}
      </div>
    </div>
  )
}

export default Modal
export type { ModalProps }
