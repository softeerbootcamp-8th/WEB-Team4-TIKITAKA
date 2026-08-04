import { useLocation, useNavigate } from 'react-router-dom'
import EmailSentCard from '../../components/auth/EmailSentCard'

function EmailVerificationPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const email = (location.state as { email?: string } | null)?.email

  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      <EmailSentCard
        title="이메일을 확인해주세요"
        description={
          <>
            {email ? (
              <>
                <span className="font-semibold text-ink">{email}</span>
                {' 주소로'}
              </>
            ) : (
              '가입하신 이메일 주소로'
            )}
            <br />
            인증 링크를 보내드렸어요.
          </>
        }
        resendLabel="인증 메일 재전송"
        resendToastMessage="인증 메일을 다시 보냈어요."
        onResend={() => {}}
        footer={
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="text-sm font-medium text-muted hover:text-body"
          >
            로그인 화면으로 돌아가기
          </button>
        }
      />
    </main>
  )
}

export default EmailVerificationPage
