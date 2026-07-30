import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import TextInput from '../../components/ui/TextInput'
import { useToast } from '../../hooks/useToast'

const TEXT = {
  title: '로그인',
  subtitle: '계속하려면 로그인해주세요.',
  emailLabel: '이메일',
  emailPlaceholder: '이메일 주소를 입력하세요',
  passwordLabel: '비밀번호',
  passwordPlaceholder: '비밀번호를 입력하세요',
  submit: '로그인',
  forgotPassword: '비밀번호 찾기',
  signupPrompt: '아직 계정이 없으신가요?',
  signup: '회원가입',
  imagePlaceholder: '이미지 영역',
  loginNotReady: '로그인 API 연동은 아직 준비 중입니다.',
}

const ROUTE = {
  passwordReset: '/password-reset',
  signup: '/signup',
}

/* 백엔드 로그인 API(POST /api/v1/auth/login)의 입력 경계와 같은 값을 쓴다. */
const EMAIL_MAX_LENGTH = 320
const PASSWORD_MAX_LENGTH = 64
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const ERROR_MESSAGE = {
  emptyField: '이메일과 비밀번호를 모두 입력해주세요.',
  invalidEmail: '이메일 형식이 올바르지 않습니다.',
  emailTooLong: `이메일은 ${EMAIL_MAX_LENGTH}자 이하로 입력해주세요.`,
  passwordTooLong: `비밀번호는 ${PASSWORD_MAX_LENGTH}자 이하로 입력해주세요.`,
}

const FORM_ERROR_ID = 'login-form-error'

/* 링크형 버튼(비밀번호 찾기·회원가입)도 Button과 같은 포커스·눌림 반응을 갖도록 맞춘다. */
const LINK_INTERACTION_CLASSES =
  'rounded-xs transition-colors active:opacity-70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-canvas'

function validateCredentials(email: string, password: string) {
  if (!email || !password) return ERROR_MESSAGE.emptyField
  if (email.length > EMAIL_MAX_LENGTH) return ERROR_MESSAGE.emailTooLong
  if (!EMAIL_PATTERN.test(email)) return ERROR_MESSAGE.invalidEmail
  if (password.length > PASSWORD_MAX_LENGTH) return ERROR_MESSAGE.passwordTooLong
  return null
}

function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { showToast } = useToast()

  const handleEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    setEmail(event.target.value)
    setError(null)
  }

  const handlePasswordChange = (event: ChangeEvent<HTMLInputElement>) => {
    setPassword(event.target.value)
    setError(null)
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const nextError = validateCredentials(email.trim(), password)
    setError(nextError)
    if (nextError) return

    /*
     * TODO: POST /api/v1/auth/login (credentials: 'include') 연동.
     * 401(MEMBER_401_1) 응답이면 setError로 인증 실패 메시지를 아래 같은 자리에 표시한다.
     */
    showToast(TEXT.loginNotReady, 'info')
  }

  const hasError = error !== null

  return (
    /* TopNav(h-16 = 4rem)를 뺀 나머지 화면 전체를 차지한다. */
    <main className="grid min-h-[calc(100dvh-4rem)] md:grid-cols-2">
      <section className="flex items-center justify-center px-lg py-xxl">
        <form
          noValidate
          onSubmit={handleSubmit}
          className="flex w-full max-w-[400px] flex-col gap-lg"
        >
          <div className="flex flex-col gap-xxs">
            <h1 className="text-3xl font-bold text-ink">{TEXT.title}</h1>
            <p className="text-base text-muted">{TEXT.subtitle}</p>
          </div>

          <div className="flex flex-col gap-base">
            <TextInput
              label={TEXT.emailLabel}
              type="email"
              value={email}
              onChange={handleEmailChange}
              placeholder={TEXT.emailPlaceholder}
              autoComplete="email"
              maxLength={EMAIL_MAX_LENGTH}
              aria-invalid={hasError}
              aria-describedby={hasError ? FORM_ERROR_ID : undefined}
            />
            <TextInput
              label={TEXT.passwordLabel}
              type="password"
              value={password}
              onChange={handlePasswordChange}
              placeholder={TEXT.passwordPlaceholder}
              autoComplete="current-password"
              maxLength={PASSWORD_MAX_LENGTH}
              aria-invalid={hasError}
              aria-describedby={hasError ? FORM_ERROR_ID : undefined}
            />
            <div className="flex justify-end">
              <Link
                to={ROUTE.passwordReset}
                className={`text-sm font-medium text-body hover:text-ink hover:underline ${LINK_INTERACTION_CLASSES}`}
              >
                {TEXT.forgotPassword}
              </Link>
            </div>
          </div>

          {/* 로그인 실패 메시지는 입력 필드와 로그인 버튼 사이에 표시한다. */}
          {hasError && (
            <p
              id={FORM_ERROR_ID}
              role="alert"
              className="rounded-sm bg-down-tint px-base py-sm text-sm font-medium text-down"
            >
              {error}
            </p>
          )}

          <div className="flex flex-col gap-base">
            <Button type="submit" size="lg" className="w-full">
              {TEXT.submit}
            </Button>
            <p className="text-center text-sm text-body">
              {TEXT.signupPrompt}{' '}
              <Link
                to={ROUTE.signup}
                className={`font-semibold text-primary hover:text-primary-active hover:underline ${LINK_INTERACTION_CLASSES}`}
              >
                {TEXT.signup}
              </Link>
            </p>
          </div>
        </form>
      </section>

      {/* 우측 이미지는 아직 비워둔다 (자리만 확보) */}
      <div
        aria-hidden
        className="hidden items-center justify-center border-l border-hairline-soft bg-surface-soft md:flex"
      >
        <span className="text-sm text-muted-soft">{TEXT.imagePlaceholder}</span>
      </div>
    </main>
  )
}

export default LoginPage
