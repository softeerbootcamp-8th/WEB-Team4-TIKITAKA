import { useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../../components/ui/Button'
import TextInput from '../../../components/ui/TextInput'
import {
  AUTH_ERROR_MESSAGE,
  PASSWORD_MAX_LENGTH,
  validateNewPassword,
} from '../../../lib/auth/validation'
import { useToast } from '../../../hooks/useToast'
import { requestPasswordUpdate } from '../../../lib/api/mypage'
import { MY_INFO_TEXT } from '../constants'

/*
 * 비밀번호 변경. 현재 비밀번호로 본인을 확인하고, 새 비밀번호를 두 번 받아 오타를 거른다.
 * 오류는 원인이 된 입력칸 밑에 붙여, 어디를 고쳐야 하는지 바로 보이게 한다.
 */
type PasswordField = 'current' | 'next' | 'confirm'

interface PasswordError {
  field: PasswordField
  message: string
}

function PasswordChangeForm() {
  const { showToast } = useToast()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [error, setError] = useState<PasswordError | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isFilled =
    currentPassword.length > 0 && newPassword.length > 0 && newPasswordConfirm.length > 0

  function errorOf(field: PasswordField) {
    return error?.field === field ? error.message : undefined
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (isSubmitting) return

    if (!currentPassword) {
      setError({ field: 'current', message: MY_INFO_TEXT.currentPasswordRequired })
      return
    }

    const newPasswordError = validateNewPassword(newPassword)
    if (newPasswordError) {
      setError({ field: 'next', message: newPasswordError })
      return
    }
    if (newPassword === currentPassword) {
      setError({ field: 'next', message: MY_INFO_TEXT.passwordSameAsCurrent })
      return
    }
    if (newPassword !== newPasswordConfirm) {
      setError({ field: 'confirm', message: AUTH_ERROR_MESSAGE.passwordMismatch })
      return
    }

    setError(null)
    setIsSubmitting(true)
    const result = await requestPasswordUpdate({
      currentPassword,
      newPassword,
      newPasswordConfirm,
    })
    setIsSubmitting(false)
    if (!result.ok) {
      setError({ field: 'current', message: result.message })
      return
    }

    setCurrentPassword('')
    setNewPassword('')
    setNewPasswordConfirm('')
    showToast(MY_INFO_TEXT.passwordDone)
  }

  /* 입력을 고치기 시작하면 이전 오류 문구는 바로 치운다. */
  function changeWith(setValue: (value: string) => void) {
    return (event: { target: { value: string } }) => {
      setValue(event.target.value)
      setError(null)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-sm">
      <TextInput
        label={MY_INFO_TEXT.currentPasswordLabel}
        type="password"
        autoComplete="current-password"
        maxLength={PASSWORD_MAX_LENGTH}
        value={currentPassword}
        onChange={changeWith(setCurrentPassword)}
        error={errorOf('current')}
      />
      <TextInput
        label={MY_INFO_TEXT.newPasswordLabel}
        type="password"
        autoComplete="new-password"
        placeholder={MY_INFO_TEXT.newPasswordHint}
        maxLength={PASSWORD_MAX_LENGTH}
        value={newPassword}
        onChange={changeWith(setNewPassword)}
        error={errorOf('next')}
      />
      <TextInput
        label={MY_INFO_TEXT.newPasswordConfirmLabel}
        type="password"
        autoComplete="new-password"
        maxLength={PASSWORD_MAX_LENGTH}
        value={newPasswordConfirm}
        onChange={changeWith(setNewPasswordConfirm)}
        error={errorOf('confirm')}
      />
      <Button type="submit" variant="secondary" disabled={!isFilled || isSubmitting}>
        {isSubmitting ? '변경 중…' : MY_INFO_TEXT.passwordSubmit}
      </Button>
    </form>
  )
}

export default PasswordChangeForm
