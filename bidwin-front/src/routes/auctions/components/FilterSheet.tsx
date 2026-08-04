import { X } from 'lucide-react'
import { useEffect } from 'react'
import type { ReactNode } from 'react'
import Button from '../../../components/ui/Button'
import { FILTER_TEXT } from '../constants'

/*
 * 모바일용 필터 시트.
 * lg 미만에서는 좌측 사이드바를 숨기는 대신 "필터 보기" 버튼으로 이 시트를 띄운다.
 * 안에 들어가는 내용은 데스크톱 사이드바와 완전히 같은 FilterPanel이라 필터 항목이
 * 추가되면 양쪽이 동시에 따라온다.
 */
const ESCAPE_KEY = 'Escape'

function FilterSheet({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === ESCAPE_KEY) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-end bg-ink/40 lg:hidden" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={FILTER_TEXT.panelTitle}
        onClick={(event) => event.stopPropagation()}
        /* 높이를 고정하지 않아 필터 항목 수만큼만 올라오고, 많아지면 85dvh에서 멈춘다. */
        className="flex max-h-[85dvh] w-full flex-col rounded-t-xl bg-canvas px-lg pb-lg pt-sm"
      >
        <div className="flex shrink-0 justify-end">
          <button
            type="button"
            onClick={onClose}
            aria-label={FILTER_TEXT.closeSheet}
            className="flex h-9 w-9 items-center justify-center rounded-full text-body transition-colors hover:bg-surface-soft hover:text-ink"
          >
            <X size={20} />
          </button>
        </div>

        <div className="min-h-0 flex-1">{children}</div>

        <Button onClick={onClose} size="lg" className="mt-base w-full shrink-0">
          {FILTER_TEXT.applySheet}
        </Button>
      </div>
    </div>
  )
}

export default FilterSheet
