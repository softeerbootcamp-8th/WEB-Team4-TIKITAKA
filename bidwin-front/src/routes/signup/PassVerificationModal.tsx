import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import AuthFormError from '../../components/auth/AuthFormError'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import TextInput from '../../components/ui/TextInput'
import {
  BIRTH_DATE_LENGTH,
  NAME_MAX_LENGTH,
  PHONE_NUMBER_DIGIT_MAX_LENGTH,
  PHONE_NUMBER_INPUT_MAX_LENGTH,
  normalizePhoneNumber,
  validateBirthDate,
  validateName,
  validatePhoneNumber,
} from '../../lib/auth/validation'
import { formatPartialPhoneNumber } from '../../lib/format'

const TEXT = {
  title: 'PASS 본인인증',
  description: '통신사 본인인증을 위해 아래 정보를 입력해주세요.',
  nameLabel: '이름',
  namePlaceholder: '실명을 입력하세요',
  phoneNumberLabel: '전화번호',
  phoneNumberPlaceholder: '010-1234-5678',
  birthDateLabel: '생년월일',
  birthDatePlaceholder: 'YYYYMMDD',
  notice: 'PASS 앱 연동은 준비 중이라, 입력한 정보로 인증을 대신합니다.',
  cancel: '취소',
  confirm: '확인',
}

const ERROR_MESSAGE = {
  emptyField: '이름, 전화번호, 생년월일을 모두 입력해주세요.',
}

const FORM_ERROR_ID = 'pass-verification-form-error'

/* 인증이 끝나면 회원가입 API로 그대로 넘길 값들 */
interface VerifiedIdentity {
  name: string
  /* 하이픈을 뺀 숫자만 */
  phoneNumber: string
  /* YYYYMMDD. 현재 회원가입 API에는 보내지 않고 인증 화면에서만 쓴다. */
  birthDate: string
}

interface PassVerificationModalProps {
  isOpen: boolean
  onClose: () => void
  onVerified: (identity: VerifiedIdentity) => void
}

function validateIdentity(name: string, phoneNumber: string, birthDate: string) {
  if (!name || !phoneNumber || !birthDate) return ERROR_MESSAGE.emptyField
  return (
    validateName(name) ?? validatePhoneNumber(phoneNumber) ?? validateBirthDate(birthDate)
  )
}

/*
 * PASS 본인인증 모달.
 * 실제 PASS 연동은 아직 할 수 없으므로, 입력값 검증을 통과하면 인증 성공으로 처리한다.
 * TODO: 실제 PASS 인증 연동 시 이 모달의 확인 처리를 인증 결과 콜백으로 교체한다.
 */
function PassVerificationModal({
  isOpen,
  onClose,
  onVerified,
}: PassVerificationModalProps) {
  const [name, setName] = useState('')
  /* 화면에는 하이픈을 넣어 보여주되, 상태에는 숫자만 담는다. */
  const [phoneNumberDigits, setPhoneNumberDigits] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [error, setError] = useState<string | null>(null)

  const resetForm = () => {
    setName('')
    setPhoneNumberDigits('')
    setBirthDate('')
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

  const handleBirthDateChange = (event: ChangeEvent<HTMLInputElement>) => {
    setBirthDate(event.target.value)
    setError(null)
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const trimmedName = name.trim()
    const trimmedBirthDate = birthDate.trim()

    const nextError = validateIdentity(
      trimmedName,
      phoneNumberDigits,
      trimmedBirthDate,
    )
    setError(nextError)
    if (nextError) return

    onVerified({
      name: trimmedName,
      phoneNumber: phoneNumberDigits,
      birthDate: trimmedBirthDate,
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
          <TextInput
            label={TEXT.birthDateLabel}
            inputMode="numeric"
            value={birthDate}
            onChange={handleBirthDateChange}
            placeholder={TEXT.birthDatePlaceholder}
            autoComplete="bday"
            maxLength={BIRTH_DATE_LENGTH}
            aria-invalid={hasError}
            aria-describedby={hasError ? FORM_ERROR_ID : undefined}
          />
        </div>

        {/* 오류 문구 자리와 형식은 로그인·회원가입 화면과 같다. */}
        {hasError && <AuthFormError id={FORM_ERROR_ID} message={error} />}

        <p className="text-sm text-muted">{TEXT.notice}</p>

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

export default PassVerificationModal
export type { PassVerificationModalProps, VerifiedIdentity }
