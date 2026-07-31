import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCircle2, KeyRound, ShieldAlert } from 'lucide-react'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import TextInput from '../../../components/ui/TextInput'
import { getPasswordError } from '../../../lib/validation'

function PasswordResetConfirmPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')

  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [error, setError] = useState('')
  const [isComplete, setIsComplete] = useState(false)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const passwordError = getPasswordError(password)
    if (passwordError) {
      setError(passwordError)
      return
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않아요.')
      return
    }
    setError('')
    // TODO: 실제 백엔드 API 연동 시 token과 새 비밀번호로 재설정 요청으로 교체
    setIsComplete(true)
  }

  if (!token) {
    return (
      <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
        <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl text-center shadow-soft">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-down-tint">
            <ShieldAlert size={28} className="text-down" strokeWidth={2} />
          </span>
          <div className="flex flex-col gap-xs">
            <h1 className="text-2xl font-bold text-ink">유효하지 않은 링크예요</h1>
            <p className="text-base leading-relaxed text-body">
              링크가 만료되었거나 잘못됐어요.
              <br />
              비밀번호 재설정을 다시 요청해주세요.
            </p>
          </div>
          <Link to="/password-reset">
            <Button size="lg" className="min-w-[220px]">
              재설정 다시 요청하기
            </Button>
          </Link>
        </Card>
      </main>
    )
  }

  if (isComplete) {
    return (
      <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
        <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl text-center shadow-soft">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-up-tint">
            <CheckCircle2 size={28} className="text-up" strokeWidth={2} />
          </span>
          <div className="flex flex-col gap-xs">
            <h1 className="text-2xl font-bold text-ink">비밀번호가 변경됐어요</h1>
            <p className="text-base leading-relaxed text-body">
              새 비밀번호로 다시 로그인해주세요.
            </p>
          </div>
          <Button size="lg" className="min-w-[220px]" onClick={() => navigate('/login')}>
            로그인하러 가기
          </Button>
        </Card>
      </main>
    )
  }

  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl shadow-soft">
        <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-tint">
          <KeyRound size={28} className="text-primary" strokeWidth={2} />
        </span>

        <div className="flex flex-col items-center gap-xs text-center">
          <h1 className="text-2xl font-bold text-ink">새 비밀번호 설정</h1>
          <p className="text-base leading-relaxed text-body">
            새로 사용하실 비밀번호를 입력해주세요.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex w-full flex-col gap-lg">
          <div className="flex flex-col gap-base">
            <TextInput
              label="새 비밀번호"
              type="password"
              placeholder="8~64자, 특수문자 1개 이상 포함"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoFocus
            />
            <TextInput
              label="새 비밀번호 확인"
              type="password"
              value={passwordConfirm}
              onChange={(event) => setPasswordConfirm(event.target.value)}
              error={error}
            />
          </div>
          <Button type="submit" size="lg" className="w-full">
            비밀번호 변경
          </Button>
        </form>
      </Card>
    </main>
  )
}

export default PasswordResetConfirmPage
