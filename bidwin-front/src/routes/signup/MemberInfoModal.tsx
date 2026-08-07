import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import AuthFormError from '../../components/auth/AuthFormError'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import TextInput from '../../components/ui/TextInput'
import {
  NAME_MAX_LENGTH,
  PHONE_NUMBER_DIGIT_MAX_LENGTH,
  PHONE_NUMBER_INPUT_MAX_LENGTH,
  normalizePhoneNumber,
  validateName,
  validatePhoneNumber,
} from '../../lib/auth/validation'
import { formatPartialPhoneNumber } from '../../lib/format'

const TEXT = {
  title: '본인 정보 입력',
  description: '회원가입에 필요한 정보를 입력해주세요.',
  nameLabel: '이름',
  namePlaceholder: '실명을 입력하세요',
  phoneNumberLabel: '전화번호',
  phoneNumberPlaceholder: '010-1234-5678',
  cancel: '취소',
  confirm: '입력 완료',
}

const ERROR_MESSAGE = {
  emptyField: '이름과 전화번호를 모두 입력해주세요.',
}

const FORM_ERROR_ID = 'member-info-form-error'

/* 회원가입 API로 그대로 넘길 값들 */
interface SignupIdentity {
  name: string
  /* 하이픈을 뺀 숫자만 */
  phoneNumber: string
}

interface MemberInfoModalProps {
  isOpen: boolean
  onClose: () => void
  onComplete: (identity: SignupIdentity) => void
}

function validateIdentity(name: string, phoneNumber: string) {
  if (!name || !phoneNumber) return ERROR_MESSAGE.emptyField
  return validateName(name) ?? validatePhoneNumber(phoneNumber)
}

/* 백엔드 회원가입 요청에 필요한 이름과 전화번호를 입력받는다. */
function MemberInfoModal({
  isOpen,
  onClose,
  onComplete,
}: MemberInfoModalProps) {
  const [name, setName] = useState('')
  /* 화면에는 하이픈을 넣어 보여주되, 상태에는 숫자만 담는다. */
  const [phoneNumberDigits, setPhoneNumberDigits] = useState('')
  const [error, setError] = useState<string | null>(null)

  const resetForm = () => {
    setName('')
    setPhoneNumberDigits('')
    setError(null)
  }

  /* 닫을 때는(취소·배경·ESC·닫기 버튼) 항상 입력값을 비워 다음 시도와 섞이지 않게 한다. */
  const handleClose = () => {
    resetForm()
    onClose()
  }

  const handleNameChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value)
    setError(null)
  }

  /* 하이픈은 자동으로 붙이므로, 사용자가 무엇을 넣든 숫자만 뽑아 자릿수까지 잘라 보관한다. */
  const handlePhoneNumberChange = (event: ChangeEvent<HTMLInputElement>) => {
    const digits = normalizePhoneNumber(event.target.value)
    setPhoneNumberDigits(digits.slice(0, PHONE_NUMBER_DIGIT_MAX_LENGTH))
    setError(null)
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const trimmedName = name.trim()
    const nextError = validateIdentity(trimmedName, phoneNumberDigits)
    setError(nextError)
    if (nextError) return

    onComplete({
      name: trimmedName,
      phoneNumber: phoneNumberDigits,
    })
    resetForm()
  }

  const hasError = error !== null

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={TEXT.title}
      description={TEXT.description}
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-lg">
        <div className="flex flex-col gap-base">
          <TextInput
            label={TEXT.nameLabel}
            value={name}
            onChange={handleNameChange}
            placeholder={TEXT.namePlaceholder}
            autoComplete="name"
            maxLength={NAME_MAX_LENGTH}
            aria-invalid={hasError}
            aria-describedby={hasError ? FORM_ERROR_ID : undefined}
          />
          <TextInput
            label={TEXT.phoneNumberLabel}
            type="tel"
            inputMode="numeric"
            value={formatPartialPhoneNumber(phoneNumberDigits)}
            onChange={handlePhoneNumberChange}
            placeholder={TEXT.phoneNumberPlaceholder}
            autoComplete="tel"
            maxLength={PHONE_NUMBER_INPUT_MAX_LENGTH}
            aria-invalid={hasError}
            aria-describedby={hasError ? FORM_ERROR_ID : undefined}
          />
        </div>

        {/* 오류 문구 자리와 형식은 로그인·회원가입 화면과 같다. */}
        {hasError && <AuthFormError id={FORM_ERROR_ID} message={error} />}

        <div className="flex gap-sm">
          <Button variant="secondary" onClick={handleClose} className="flex-1">
            {TEXT.cancel}
          </Button>
          <Button type="submit" className="flex-1">
            {TEXT.confirm}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export default MemberInfoModal
export type { MemberInfoModalProps, SignupIdentity }
