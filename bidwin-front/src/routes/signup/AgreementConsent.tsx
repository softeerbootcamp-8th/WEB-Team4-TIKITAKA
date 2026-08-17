import { ExternalLink } from 'lucide-react'
import { LINK_INTERACTION_CLASSES } from '../../components/auth/auth-styles'
import {
  AGREEMENT_ITEMS,
  acceptEveryAgreement,
  isEveryAgreementAccepted,
} from './agreements'
import type { AgreementId, AgreementState } from './agreements'

const TEXT = {
  legend: '약관 동의',
  agreeAll: '약관 전체에 동의합니다.',
  required: '[필수]',
  view: '보기',
}

const LINK_ICON_SIZE = 12
const CHECKBOX_CLASS = 'h-4 w-4 shrink-0 accent-primary'

interface AgreementConsentProps {
  value: AgreementState
  onChange: (next: AgreementState) => void
  disabled?: boolean
}

/*
 * 필수 동의 항목. 전체 동의로 한 번에 켜고 끌 수 있게 하되, 어떤 문서에 동의하는지
 * 각 항목에서 바로 열어볼 수 있어야 하므로 원문 링크를 항목마다 둔다.
 */
function AgreementConsent({ value, onChange, disabled = false }: AgreementConsentProps) {
  const isAllAccepted = isEveryAgreementAccepted(value)

  function toggle(id: AgreementId, accepted: boolean) {
    onChange({ ...value, [id]: accepted })
  }

  return (
    <fieldset className="flex flex-col gap-xs">
      <legend className="text-sm font-semibold text-body">{TEXT.legend}</legend>

      <label className="flex cursor-pointer items-center gap-xs">
        <input
          type="checkbox"
          checked={isAllAccepted}
          onChange={(event) => onChange(acceptEveryAgreement(event.target.checked))}
          disabled={disabled}
          className={CHECKBOX_CLASS}
        />
        <span className="text-sm font-semibold text-ink">{TEXT.agreeAll}</span>
      </label>

      <div className="flex flex-col gap-xxs border-t border-hairline-soft pt-xs">
        {AGREEMENT_ITEMS.map((item) => (
          <div key={item.id} className="flex items-center justify-between gap-xs">
            <label className="flex cursor-pointer items-center gap-xs text-xs leading-relaxed text-body">
              <input
                type="checkbox"
                checked={value[item.id]}
                onChange={(event) => toggle(item.id, event.target.checked)}
                disabled={disabled}
                className={CHECKBOX_CLASS}
              />
              <span>
                <span className="font-semibold text-primary">{TEXT.required}</span>{' '}
                {item.label}
              </span>
            </label>
            {/* 가입 폼을 벗어나지 않도록 원문은 새 탭에서 연다. */}
            <a
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={item.linkLabel}
              className={`flex shrink-0 items-center gap-0.5 text-xs font-semibold text-muted underline hover:text-ink ${LINK_INTERACTION_CLASSES}`}
            >
              {TEXT.view}
              <ExternalLink size={LINK_ICON_SIZE} />
            </a>
          </div>
        ))}
      </div>
    </fieldset>
  )
}

export default AgreementConsent
export type { AgreementConsentProps }
