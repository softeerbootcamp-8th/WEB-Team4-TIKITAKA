import { useState } from 'react'
import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import { formatWon } from '../../../lib/format'
import { DEPOSIT_CHARGE_TEXT, DEPOSIT_TEXT, HISTORY_TAB, historyPath } from '../constants'
import type { DepositAccount } from '../types'
import DepositChargeModal from './DepositChargeModal'

const CHEVRON_SIZE = 14

/*
 * 보증금 현황. 낙찰·거래에 따른 입출금은 자동으로 처리되고,
 * 테스트를 위한 충전만 이 카드에서 직접 할 수 있다.
 */
function DepositCard({
  deposit,
  onChargeDeposit,
}: {
  deposit: DepositAccount
  onChargeDeposit: (deposit: DepositAccount) => void
}) {
  const [isChargeOpen, setIsChargeOpen] = useState(false)
  const total = deposit.balance + deposit.inUse

  return (
    <Card>
      <div className="flex items-center justify-between gap-base">
        <h2 className="text-base font-bold text-ink">{DEPOSIT_TEXT.title}</h2>
        <Link
          to={historyPath(HISTORY_TAB.deposit)}
          className="inline-flex shrink-0 items-center gap-0.5 text-sm font-semibold text-body hover:text-ink"
        >
          {DEPOSIT_TEXT.history}
          <ChevronRight size={CHEVRON_SIZE} />
        </Link>
      </div>

      <p className="mt-sm flex flex-wrap items-baseline gap-xs">
        <span className="text-sm text-body">{DEPOSIT_TEXT.balanceLabel}</span>
        <span className="text-3xl font-bold tracking-tight text-ink">
          {formatWon(total)}
        </span>
      </p>

      <div className="mt-base grid grid-cols-2 gap-sm">
        <DepositStat label={DEPOSIT_TEXT.inUseLabel} amount={deposit.inUse} />
        <DepositStat label={DEPOSIT_TEXT.availableLabel} amount={deposit.balance} isHighlighted />
      </div>

      <p className="mt-base text-xs leading-relaxed text-muted">{DEPOSIT_TEXT.notice}</p>

      <Button
        variant="secondary"
        className="mt-base w-full"
        onClick={() => setIsChargeOpen(true)}
      >
        {DEPOSIT_CHARGE_TEXT.action}
      </Button>

      <DepositChargeModal
        isOpen={isChargeOpen}
        onClose={() => setIsChargeOpen(false)}
        onCharge={onChargeDeposit}
      />
    </Card>
  )
}

function DepositStat({
  label,
  amount,
  isHighlighted = false,
}: {
  label: string
  amount: number
  isHighlighted?: boolean
}) {
  return (
    <div className="flex flex-col gap-0.5 rounded-lg bg-surface-soft px-base py-sm">
      <span className="text-xs text-muted">{label}</span>
      <span className={`text-base font-bold ${isHighlighted ? 'text-primary' : 'text-ink'}`}>
        {formatWon(amount)}
      </span>
    </div>
  )
}

export default DepositCard
