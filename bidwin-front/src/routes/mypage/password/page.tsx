import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, KeyRound } from 'lucide-react'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import TextInput from '../../../components/ui/TextInput'

const MIN_PASSWORD_LENGTH = 8
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function MyPagePasswordResetPage() {
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [error, setError] = useState('')
  const [isComplete, setIsComplete] = useState(false)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!EMAIL_PATTERN.test(email)) {
      setError('올바른 이메일 주소를 입력해주세요.')
      return
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`비밀번호는 ${MIN_PASSWORD_LENGTH}자 이상이어야 해요.`)
      return
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않아요.')
      return
    }
    setError('')
    // TODO: 실제 백엔드 API 연동 시 이메일 확인 및 비밀번호 변경 요청으로 교체
    setIsComplete(true)
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
              새 비밀번호로 다음 로그인부터 적용돼요.
            </p>
          </div>
          <Button size="lg" className="min-w-[220px]" onClick={() => navigate('/mypage')}>
            마이페이지로 돌아가기
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
          <h1 className="text-2xl font-bold text-ink">비밀번호 재설정</h1>
          <p className="text-base leading-relaxed text-body">
            본인 확인을 위해 이메일과 새 비밀번호를 입력해주세요.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex w-full flex-col gap-lg">
          <div className="flex flex-col gap-base">
            <TextInput
              label="이메일"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoFocus
            />
            <TextInput
              label="새 비밀번호"
              type="password"
              placeholder={`${MIN_PASSWORD_LENGTH}자 이상`}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
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

export default MyPagePasswordResetPage
