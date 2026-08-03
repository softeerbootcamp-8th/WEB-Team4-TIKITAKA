import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import TextInput from '../../components/ui/TextInput'
import EmailSentCard from '../../components/auth/EmailSentCard'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function PasswordResetRequestPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [sentEmail, setSentEmail] = useState<string | null>(null)

  const sendResetLink = () => {
    // TODO: 실제 백엔드 API 연동 시 이메일로 재설정 링크 발송 요청으로 교체
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!EMAIL_PATTERN.test(email)) {
      setError('올바른 이메일 주소를 입력해주세요.')
      return
    }
    setError('')
    sendResetLink()
    setSentEmail(email)
  }

  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      {sentEmail ? (
        <EmailSentCard
          title="이메일을 확인해주세요"
          description={
            <>
              <span className="font-semibold text-ink">{sentEmail}</span>
              {' 주소로'}
              <br />
              비밀번호 재설정 링크를 보내드렸어요.
            </>
          }
          resendLabel="재설정 메일 재전송"
          resendToastMessage="재설정 메일을 다시 보냈어요."
          onResend={sendResetLink}
          footer={
            <Link
              to="/login"
              className="text-sm font-medium text-muted hover:text-body"
            >
              로그인 화면으로 돌아가기
            </Link>
          }
        />
      ) : (
        <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl shadow-soft">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-tint">
            <KeyRound size={28} className="text-primary" strokeWidth={2} />
          </span>

          <div className="flex flex-col items-center gap-xs text-center">
            <h1 className="text-2xl font-bold text-ink">비밀번호 찾기</h1>
            <p className="text-base leading-relaxed text-body">
              가입하신 이메일 주소를 입력하시면
              <br />
              비밀번호 재설정 링크를 보내드려요.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="flex w-full flex-col gap-lg">
            <TextInput
              label="이메일"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={error}
              autoFocus
            />
            <Button type="submit" size="lg" className="w-full">
              재설정 링크 보내기
            </Button>
          </form>

          <Link
            to="/login"
            className="text-sm font-medium text-muted hover:text-body"
          >
            로그인 화면으로 돌아가기
          </Link>
        </Card>
      )}
    </main>
  )
}

export default PasswordResetRequestPage
