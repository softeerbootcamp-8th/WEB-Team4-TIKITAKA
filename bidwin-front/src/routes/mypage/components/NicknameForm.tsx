import { useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../../components/ui/Button'
import TextInput from '../../../components/ui/TextInput'
import { NICKNAME_MAX_LENGTH, validateNickname } from '../../../lib/auth/validation'
import { requestNicknameUpdate } from '../../../lib/api/mypage'
import { useToast } from '../../../hooks/useToast'
import { MY_INFO_TEXT } from '../constants'

/*
 * 값이 그대로면 요청하지 않고, 성공 응답의 닉네임으로 상위 프로필을 갱신한다.
 */
function NicknameForm({
  nickname,
  onChangeNickname,
}: {
  nickname: string
  onChangeNickname: (nickname: string) => void
}) {
  const { showToast } = useToast()
  const [value, setValue] = useState(nickname)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const trimmed = value.trim()

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (isSubmitting) return

    const validationError = validateNickname(trimmed)
    if (validationError) {
      setError(validationError)
      return
    }
    if (trimmed === nickname) {
      setError(MY_INFO_TEXT.nicknameUnchanged)
      return
    }

    setError('')
    setIsSubmitting(true)
    const result = await requestNicknameUpdate(trimmed)
    setIsSubmitting(false)
    if (!result.ok) {
      setError(result.message)
      return
    }

    setValue(result.data.nickname)
    onChangeNickname(result.data.nickname)
    showToast(MY_INFO_TEXT.nicknameDone)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-sm">
      <TextInput
        label={MY_INFO_TEXT.nicknameLabel}
        value={value}
        maxLength={NICKNAME_MAX_LENGTH}
        onChange={(event) => {
          setValue(event.target.value)
          setError('')
        }}
        error={error}
      />
      {!error && <p className="text-xs text-muted">{MY_INFO_TEXT.nicknameHint}</p>}
      <Button
        type="submit"
        variant="secondary"
        disabled={trimmed.length === 0 || isSubmitting}
      >
        {isSubmitting ? '변경 중…' : MY_INFO_TEXT.nicknameSubmit}
      </Button>
    </form>
  )
}

export default NicknameForm
