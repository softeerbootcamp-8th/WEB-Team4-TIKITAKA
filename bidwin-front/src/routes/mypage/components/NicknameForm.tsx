import { useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../../components/ui/Button'
import TextInput from '../../../components/ui/TextInput'
import { NICKNAME_MAX_LENGTH, validateNickname } from '../../../lib/auth/validation'
import { useToast } from '../../../hooks/useToast'
import { MY_INFO_TEXT } from '../constants'

/*
 * 닉네임 변경. 지금은 화면에서만 바꾸고, 백엔드 연동 시 onSubmit 자리에 API 호출을 넣는다.
 * 값이 그대로면 굳이 요청하지 않도록 여기서 먼저 걸러 낸다.
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

  const trimmed = value.trim()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()

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
    /* TODO: 백엔드 닉네임 변경 API 연동 */
    onChangeNickname(trimmed)
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
      <Button type="submit" variant="secondary" disabled={trimmed.length === 0}>
        {MY_INFO_TEXT.nicknameSubmit}
      </Button>
    </form>
  )
}

export default NicknameForm
