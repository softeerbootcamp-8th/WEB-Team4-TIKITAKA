import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AuthFormError from '../../components/auth/AuthFormError'
import AuthSplitLayout from '../../components/auth/AuthSplitLayout'
import { LINK_INTERACTION_CLASSES } from '../../components/auth/auth-styles'
import Button from '../../components/ui/Button'
import TextInput from '../../components/ui/TextInput'
import { useAuth } from '../../hooks/useAuth'
import { requestLogin } from '../../lib/api/auth'
import {
  EMAIL_MAX_LENGTH,
  PASSWORD_MAX_LENGTH,
  validateEmail,
  validateLoginPassword,
} from '../../lib/auth/validation'

const TEXT = {
  title: '로그인',
  subtitle: '계속하려면 로그인해주세요.',
  emailLabel: '이메일',
  emailPlaceholder: '이메일 주소를 입력하세요',
  passwordLabel: '비밀번호',
  passwordPlaceholder: '비밀번호를 입력하세요',
  submit: '로그인',
  submitting: '로그인 중…',
  forgotPassword: '비밀번호 찾기',
  signupPrompt: '아직 계정이 없으신가요?',
  signup: '회원가입',
  imagePlaceholder: '이미지 영역',
}

const ROUTE = {
  home: '/',
  passwordReset: '/password-reset',
  signup: '/signup',
}

const ERROR_MESSAGE = {
  emptyField: '이메일과 비밀번호를 모두 입력해주세요.',
}

const FORM_ERROR_ID = 'login-form-error'

function validateCredentials(email: string, password: string) {
  if (!email || !password) return ERROR_MESSAGE.emptyField
  return validateEmail(email) ?? validateLoginPassword(password)
}

function safeNextPath(next: string | null) {
  if (!next?.startsWith('/') || next.startsWith('//') || next.includes('\\')) {
    return ROUTE.home
  }

  const target = new URL(next, window.location.origin)
  return target.origin === window.location.origin
    ? `${target.pathname}${target.search}${target.hash}`
    : ROUTE.home
}

function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { setAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const handleEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    setEmail(event.target.value)
    setError(null)
  }

  const handlePasswordChange = (event: ChangeEvent<HTMLInputElement>) => {
    setPassword(event.target.value)
    setError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) return

    const nextError = validateCredentials(email.trim(), password)
    setError(nextError)
    if (nextError) return

    setIsSubmitting(true)
    const result = await requestLogin({ email: email.trim(), password })
    setIsSubmitting(false)

    if (!result.ok) {
      setError(result.message)
      return
    }

    setAuthenticated(true)
    navigate(safeNextPath(searchParams.get('next')), { replace: true })
  }

  const hasError = error !== null

  return (
    <AuthSplitLayout imagePlaceholder={TEXT.imagePlaceholder}>
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
        {hasError && <AuthFormError id={FORM_ERROR_ID} message={error} />}

        <div className="flex flex-col gap-base">
          <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? TEXT.submitting : TEXT.submit}
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
    </AuthSplitLayout>
  )
}

export default LoginPage
