import { useState } from 'react'
import Button from '../../../components/ui/Button'
import Modal from '../../../components/ui/Modal'
import { LEAVE_MODAL_TEXT } from '../constants'

/*
 * 회원 탈퇴 확인. 되돌릴 수 없는 동작이라 잃는 것을 먼저 보여주고,
 * 확인 체크를 해야 탈퇴 버튼이 열린다.
 * 상대가 있는 거래나 묶여 있는 보증금이 남아 있으면 blockReason이 내려와 아예 막는다.
 */
const DANGER_BUTTON_CLASS =
  'inline-flex h-11 flex-1 items-center justify-center rounded-pill border border-down px-lg font-semibold text-down transition-colors hover:bg-down-tint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-down focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:border-hairline disabled:text-muted-soft disabled:hover:bg-transparent'

function LeaveAccountModal({
  isOpen,
  onClose,
  onConfirm,
  blockReason,
}: {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  blockReason?: string
}) {
  const [isConfirmed, setIsConfirmed] = useState(false)

  function handleClose() {
    setIsConfirmed(false)
    onClose()
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={LEAVE_MODAL_TEXT.title}
      description={LEAVE_MODAL_TEXT.description}
    >
      <ul className="flex list-disc flex-col gap-xs rounded-lg bg-surface-soft px-xl py-base text-sm leading-relaxed text-body">
        {LEAVE_MODAL_TEXT.losses.map((loss) => (
          <li key={loss}>{loss}</li>
        ))}
      </ul>

      {blockReason ? (
        <p className="rounded-lg bg-down-tint px-base py-sm text-sm font-semibold text-down">
          {blockReason}
        </p>
      ) : (
        <label className="flex cursor-pointer items-center gap-xs text-sm text-body">
          <input
            type="checkbox"
            checked={isConfirmed}
            onChange={(event) => setIsConfirmed(event.target.checked)}
            className="h-4 w-4 accent-down"
          />
          {LEAVE_MODAL_TEXT.confirmLabel}
        </label>
      )}

      <div className="flex gap-sm">
        <Button variant="secondary" onClick={handleClose} className="flex-1">
          {LEAVE_MODAL_TEXT.cancel}
        </Button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={Boolean(blockReason) || !isConfirmed}
          className={DANGER_BUTTON_CLASS}
        >
          {LEAVE_MODAL_TEXT.submit}
        </button>
      </div>
    </Modal>
  )
}

export default LeaveAccountModal
