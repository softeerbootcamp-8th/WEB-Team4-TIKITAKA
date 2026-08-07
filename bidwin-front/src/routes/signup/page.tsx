import { useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Check, ShieldCheck } from 'lucide-react'
import AuthFormError from '../../components/auth/AuthFormError'
import AuthSplitLayout from '../../components/auth/AuthSplitLayout'
import { LINK_INTERACTION_CLASSES } from '../../components/auth/auth-styles'
import Button from '../../components/ui/Button'
import TextInput from '../../components/ui/TextInput'
import { useToast } from '../../hooks/useToast'
import {
  requestEmailAvailability,
  requestNicknameAvailability,
  requestSignUp,
} from '../../lib/api/auth'
import {
  AUTH_ERROR_MESSAGE,
  EMAIL_MAX_LENGTH,
  NICKNAME_MAX_LENGTH,
  NICKNAME_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  validateEmail,
  validateNewPassword,
  validateNickname,
} from '../../lib/auth/validation'
import { formatPhoneNumber } from '../../lib/format'
import PassVerificationModal from './PassVerificationModal'
import type { VerifiedIdentity } from './PassVerificationModal'

const TEXT = {
  title: '회원가입',
  subtitle: '급처마켓 계정을 만들고 경매에 참여해보세요.',
  emailLabel: '이메일',
  emailPlaceholder: '이메일 주소를 입력하세요',
  availabilityCheck: '중복 확인',
  availabilityChecking: '확인 중…',
  availabilityChecked: '확인 완료',
  emailAvailable: '사용 가능한 이메일입니다.',
  emailUnavailable: '이미 사용 중인 이메일입니다.',
  passwordLabel: '비밀번호',
  passwordPlaceholder: `${PASSWORD_MIN_LENGTH}자 이상, 특수문자를 포함해주세요`,
  passwordConfirmLabel: '비밀번호 확인',
  passwordConfirmPlaceholder: '비밀번호를 다시 입력하세요',
  nicknameLabel: '닉네임',
  nicknamePlaceholder: `${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자로 입력하세요`,
  nicknameAvailable: '사용 가능한 닉네임입니다.',
  nicknameUnavailable: '이미 사용 중인 닉네임입니다.',
  identityLabel: '본인인증',
  passVerify: 'PASS로 본인인증',
  passVerified: '본인인증 완료',
  submit: '회원가입',
  submitting: '가입 처리 중…',
  loginPrompt: '이미 계정이 있으신가요?',
  login: '로그인',
  imagePlaceholder: '이미지 영역 2',
  signUpSuccess: '회원가입이 완료됐어요. 이메일 인증을 진행해주세요.',
  passVerifiedToast: '본인인증이 완료됐어요.',
}

const ROUTE = {
  login: '/login',
  emailVerification: '/email-verification',
}

const ERROR_MESSAGE = {
  emptyField: '이메일, 비밀번호, 비밀번호 확인, 닉네임을 모두 입력해주세요.',
  emailAvailabilityRequired: '이메일 중복 확인을 완료해주세요.',
  nicknameAvailabilityRequired: '닉네임 중복 확인을 완료해주세요.',
  identityRequired: 'PASS 본인인증을 완료해주세요.',
}

const FORM_ERROR_ID = 'signup-form-error'
const EMAIL_AVAILABILITY_ID = 'signup-email-availability'
const NICKNAME_AVAILABILITY_ID = 'signup-nickname-availability'
const PASS_BUTTON_ICON_SIZE = 18

type AvailabilityStatus = 'idle' | 'checking' | 'available' | 'error'

interface AvailabilityCheck {
  status: AvailabilityStatus
  value: string
  message: string | null
}

const INITIAL_AVAILABILITY_CHECK: AvailabilityCheck = {
  status: 'idle',
  value: '',
  message: null,
}

interface SignUpFields {
  email: string
  password: string
  passwordConfirm: string
  nickname: string
}

function validateSignUpFields({
  email,
  password,
  passwordConfirm,
  nickname,
}: SignUpFields) {
  if (!email || !password || !passwordConfirm || !nickname) {
    return ERROR_MESSAGE.emptyField
  }

  const emailError = validateEmail(email)
  if (emailError) return emailError

  const passwordError = validateNewPassword(password)
  if (passwordError) return passwordError

  if (password !== passwordConfirm) return AUTH_ERROR_MESSAGE.passwordMismatch

  return validateNickname(nickname)
}

function SignupPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [emailAvailability, setEmailAvailability] = useState(INITIAL_AVAILABILITY_CHECK)
  const [nicknameAvailability, setNicknameAvailability] = useState(INITIAL_AVAILABILITY_CHECK)
  const emailAvailabilityRequestId = useRef(0)
  const nicknameAvailabilityRequestId = useRef(0)
  /* PASS 인증으로 받은 이름·전화번호. 성공 이후에는 다시 인증할 수 없다. */
  const [identity, setIdentity] = useState<VerifiedIdentity | null>(null)
  const [isPassModalOpen, setIsPassModalOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { showToast } = useToast()
  const navigate = useNavigate()

  const isIdentityVerified = identity !== null
  const trimmedEmail = email.trim()
  const trimmedNickname = nickname.trim()
  const isEmailAvailable = emailAvailability.status === 'available'
    && emailAvailability.value === trimmedEmail
  const isNicknameAvailable = nicknameAvailability.status === 'available'
    && nicknameAvailability.value === trimmedNickname
  const showEmailAvailability = emailAvailability.value === trimmedEmail
    && emailAvailability.message !== null
  const showNicknameAvailability = nicknameAvailability.value === trimmedNickname
    && nicknameAvailability.message !== null

  const handleFieldChange =
    (setField: (value: string) => void) => (event: ChangeEvent<HTMLInputElement>) => {
      setField(event.target.value)
      setError(null)
    }

  const handleEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    emailAvailabilityRequestId.current += 1
    setEmail(event.target.value)
    setEmailAvailability(INITIAL_AVAILABILITY_CHECK)
    setError(null)
  }

  const handleNicknameChange = (event: ChangeEvent<HTMLInputElement>) => {
    nicknameAvailabilityRequestId.current += 1
    setNickname(event.target.value)
    setNicknameAvailability(INITIAL_AVAILABILITY_CHECK)
    setError(null)
  }

  const handleCheckEmailAvailability = async () => {
    setError(null)
    const validationError = validateEmail(trimmedEmail)
    if (validationError) {
      setEmailAvailability({
        status: 'error',
        value: trimmedEmail,
        message: validationError,
      })
      return
    }

    const requestId = ++emailAvailabilityRequestId.current
    setEmailAvailability({ status: 'checking', value: trimmedEmail, message: null })
    const result = await requestEmailAvailability(trimmedEmail)
    if (requestId !== emailAvailabilityRequestId.current) return

    if (!result.ok || !result.data.available) {
      setEmailAvailability({
        status: 'error',
        value: trimmedEmail,
        message: result.ok ? TEXT.emailUnavailable : result.message,
      })
      return
    }

    setEmailAvailability({
      status: 'available',
      value: trimmedEmail,
      message: TEXT.emailAvailable,
    })
  }

  const handleCheckNicknameAvailability = async () => {
    setError(null)
    const validationError = validateNickname(trimmedNickname)
    if (validationError) {
      setNicknameAvailability({
        status: 'error',
        value: trimmedNickname,
        message: validationError,
      })
      return
    }

    const requestId = ++nicknameAvailabilityRequestId.current
    setNicknameAvailability({ status: 'checking', value: trimmedNickname, message: null })
    const result = await requestNicknameAvailability(trimmedNickname)
    if (requestId !== nicknameAvailabilityRequestId.current) return

    if (!result.ok || !result.data.available) {
      setNicknameAvailability({
        status: 'error',
        value: trimmedNickname,
        message: result.ok ? TEXT.nicknameUnavailable : result.message,
      })
      return
    }

    setNicknameAvailability({
      status: 'available',
      value: trimmedNickname,
      message: TEXT.nicknameAvailable,
    })
  }

  const handleOpenPassModal = () => {
    /* 한 번 인증에 성공하면 버튼을 막는다. */
    if (isIdentityVerified) return
    setIsPassModalOpen(true)
  }

  const handlePassVerified = (verified: VerifiedIdentity) => {
    setIdentity(verified)
    setIsPassModalOpen(false)
    setError(null)
    showToast(TEXT.passVerifiedToast)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) return

    const fieldError = validateSignUpFields({
      email: trimmedEmail,
      password,
      passwordConfirm,
      nickname: trimmedNickname,
    })
    if (fieldError) {
      setError(fieldError)
      return
    }
    if (!isEmailAvailable) {
      setError(ERROR_MESSAGE.emailAvailabilityRequired)
      return
    }
    if (!isNicknameAvailable) {
      setError(ERROR_MESSAGE.nicknameAvailabilityRequired)
      return
    }
    if (identity === null) {
      setError(ERROR_MESSAGE.identityRequired)
      return
    }
    setError(null)

    setIsSubmitting(true)
    const result = await requestSignUp({
      email: trimmedEmail,
      password,
      name: identity.name,
      phoneNumber: identity.phoneNumber,
      nickname: trimmedNickname,
    })
    setIsSubmitting(false)

    if (!result.ok) {
      /* 중복 이메일·닉네임 등 백엔드 검증 실패는 서버 메시지를 그대로 같은 자리에 보여준다. */
      setError(result.message)
      return
    }

    showToast(TEXT.signUpSuccess)
    /* 신규 회원은 PENDING 상태이므로 이메일 인증 안내 화면으로 이어준다. */
    navigate(ROUTE.emailVerification, { state: { email: result.data.email } })
  }

  const hasError = error !== null
  const errorProps = {
    'aria-invalid': hasError,
    'aria-describedby': hasError ? FORM_ERROR_ID : undefined,
  }

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
          <div className="flex flex-col gap-xs">
            <div className="flex items-end gap-xs">
              <div className="min-w-0 flex-1">
                <TextInput
                  label={TEXT.emailLabel}
                  type="email"
                  value={email}
                  onChange={handleEmailChange}
                  placeholder={TEXT.emailPlaceholder}
                  autoComplete="email"
                  maxLength={EMAIL_MAX_LENGTH}
                  {...errorProps}
                  aria-invalid={hasError || emailAvailability.status === 'error'}
                  aria-describedby={[
                    showEmailAvailability ? EMAIL_AVAILABILITY_ID : null,
                    errorProps['aria-describedby'],
                  ].filter(Boolean).join(' ') || undefined}
                />
              </div>
              <Button
                variant="secondary"
                onClick={handleCheckEmailAvailability}
                disabled={
                  isSubmitting
                  || emailAvailability.status === 'checking'
                  || isEmailAvailable
                }
                className="shrink-0 whitespace-nowrap px-base text-sm"
              >
                {emailAvailability.status === 'checking'
                  ? TEXT.availabilityChecking
                  : isEmailAvailable
                    ? TEXT.availabilityChecked
                    : TEXT.availabilityCheck}
              </Button>
            </div>
            {showEmailAvailability && (
              <p
                id={EMAIL_AVAILABILITY_ID}
                role={emailAvailability.status === 'error' ? 'alert' : 'status'}
                className={`text-sm ${emailAvailability.status === 'available' ? 'text-up' : 'text-down'}`}
              >
                {emailAvailability.message}
              </p>
            )}
          </div>
          <TextInput
            label={TEXT.passwordLabel}
            type="password"
            value={password}
            onChange={handleFieldChange(setPassword)}
            placeholder={TEXT.passwordPlaceholder}
            autoComplete="new-password"
            maxLength={PASSWORD_MAX_LENGTH}
            {...errorProps}
          />
          <TextInput
            label={TEXT.passwordConfirmLabel}
            type="password"
            value={passwordConfirm}
            onChange={handleFieldChange(setPasswordConfirm)}
            placeholder={TEXT.passwordConfirmPlaceholder}
            autoComplete="new-password"
            maxLength={PASSWORD_MAX_LENGTH}
            {...errorProps}
          />
          <div className="flex flex-col gap-xs">
            <div className="flex items-end gap-xs">
              <div className="min-w-0 flex-1">
                <TextInput
                  label={TEXT.nicknameLabel}
                  value={nickname}
                  onChange={handleNicknameChange}
                  placeholder={TEXT.nicknamePlaceholder}
                  autoComplete="nickname"
                  maxLength={NICKNAME_MAX_LENGTH}
                  {...errorProps}
                  aria-invalid={hasError || nicknameAvailability.status === 'error'}
                  aria-describedby={[
                    showNicknameAvailability ? NICKNAME_AVAILABILITY_ID : null,
                    errorProps['aria-describedby'],
                  ].filter(Boolean).join(' ') || undefined}
                />
              </div>
              <Button
                variant="secondary"
                onClick={handleCheckNicknameAvailability}
                disabled={
                  isSubmitting
                  || nicknameAvailability.status === 'checking'
                  || isNicknameAvailable
                }
                className="shrink-0 whitespace-nowrap px-base text-sm"
              >
                {nicknameAvailability.status === 'checking'
                  ? TEXT.availabilityChecking
                  : isNicknameAvailable
                    ? TEXT.availabilityChecked
                    : TEXT.availabilityCheck}
              </Button>
            </div>
            {showNicknameAvailability && (
              <p
                id={NICKNAME_AVAILABILITY_ID}
                role={nicknameAvailability.status === 'error' ? 'alert' : 'status'}
                className={`text-sm ${nicknameAvailability.status === 'available' ? 'text-up' : 'text-down'}`}
              >
                {nicknameAvailability.message}
              </p>
            )}
          </div>

          {/* 본인인증: 인증 전에는 PASS 모달을 열고, 성공하면 버튼이 막힌다. */}
          <div className="flex flex-col gap-xs">
            <span className="text-sm font-semibold text-body">
              {TEXT.identityLabel}
            </span>
            <Button
              variant="secondary"
              onClick={handleOpenPassModal}
              disabled={isIdentityVerified}
              className="w-full"
            >
              {isIdentityVerified ? (
                <>
                  <Check size={PASS_BUTTON_ICON_SIZE} className="text-up" />
                  {TEXT.passVerified}
                </>
              ) : (
                <>
                  <ShieldCheck size={PASS_BUTTON_ICON_SIZE} />
                  {TEXT.passVerify}
                </>
              )}
            </Button>
            {identity && (
              <p className="text-sm text-muted">
                {identity.name} · {formatPhoneNumber(identity.phoneNumber)}
              </p>
            )}
          </div>
        </div>

        {/* 오류 메시지는 로그인 화면과 같은 형식으로 입력 필드와 가입 버튼 사이에 표시한다. */}
        {hasError && <AuthFormError id={FORM_ERROR_ID} message={error} />}

        <div className="flex flex-col gap-base">
          <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? TEXT.submitting : TEXT.submit}
          </Button>
          <p className="text-center text-sm text-body">
            {TEXT.loginPrompt}{' '}
            <Link
              to={ROUTE.login}
              className={`font-semibold text-primary hover:text-primary-active hover:underline ${LINK_INTERACTION_CLASSES}`}
            >
              {TEXT.login}
            </Link>
          </p>
        </div>
      </form>

      <PassVerificationModal
        isOpen={isPassModalOpen}
        onClose={() => setIsPassModalOpen(false)}
        onVerified={handlePassVerified}
      />
    </AuthSplitLayout>
  )
}

export default SignupPage
