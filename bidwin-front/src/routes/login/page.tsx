import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link } from 'react-router-dom'
import AuthFormError from '../../components/auth/AuthFormError'
import AuthSplitLayout from '../../components/auth/AuthSplitLayout'
import { LINK_INTERACTION_CLASSES } from '../../components/auth/auth-styles'
import Button from '../../components/ui/Button'
import TextInput from '../../components/ui/TextInput'
import { useToast } from '../../hooks/useToast'
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

const ERROR_MESSAGE = {
  emptyField: '이메일과 비밀번호를 모두 입력해주세요.',
}

const FORM_ERROR_ID = 'login-form-error'

function validateCredentials(email: string, password: string) {
  if (!email || !password) return ERROR_MESSAGE.emptyField
  return validateEmail(email) ?? validateLoginPassword(password)
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
    </AuthSplitLayout>
  )
}

export default LoginPage
