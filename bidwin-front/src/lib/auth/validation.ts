/*
 * 로그인·회원가입 입력 검증 규칙.
 * 길이와 정규식은 백엔드 LoginRequest·SignUpRequest의 검증 값과 같게 맞춘다.
 * 화면마다 규칙과 문구가 갈라지지 않도록 여기서 한 번만 정의한다.
 */

export const EMAIL_MAX_LENGTH = 320
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 64
export const NAME_MAX_LENGTH = 17
export const NICKNAME_MIN_LENGTH = 2
export const NICKNAME_MAX_LENGTH = 10
export const BIRTH_DATE_LENGTH = 8

/*
 * 전화번호는 입력 중 하이픈을 자동으로 넣어 보여주고(010-1234-5678 = 13자),
 * 상태와 전송값에는 숫자만 남긴다(최대 11자리).
 */
export const PHONE_NUMBER_INPUT_MAX_LENGTH = 13
export const PHONE_NUMBER_DIGIT_MAX_LENGTH = 11

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_NUMBER_PATTERN = /^01[016789]\d{7,8}$/
const PASSWORD_WHITESPACE_PATTERN = /\s/
/* 문자·숫자·공백이 아닌 문자를 특수문자로 본다(백엔드 SignUpRequest와 같은 기준). */
const PASSWORD_SPECIAL_CHARACTER_PATTERN = /[^\p{L}\p{N}\s]/u
const DIGITS_ONLY_PATTERN = /^\d+$/
const NON_DIGIT_PATTERN = /\D/g

const MIN_BIRTH_YEAR = 1900
const BIRTH_DATE_YEAR_END = 4
const BIRTH_DATE_MONTH_END = 6
const MONTH_INDEX_OFFSET = 1

export const AUTH_ERROR_MESSAGE = {
  invalidEmail: '이메일 형식이 올바르지 않습니다.',
  emailTooLong: `이메일은 ${EMAIL_MAX_LENGTH}자 이하로 입력해주세요.`,
  passwordTooShort: `비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상으로 입력해주세요.`,
  passwordTooLong: `비밀번호는 ${PASSWORD_MAX_LENGTH}자 이하로 입력해주세요.`,
  passwordHasWhitespace: '비밀번호에는 공백을 사용할 수 없습니다.',
  passwordNeedsSpecialCharacter: '비밀번호는 특수문자를 1개 이상 포함해야 합니다.',
  passwordMismatch: '비밀번호가 서로 일치하지 않습니다.',
  invalidNickname: `닉네임은 ${NICKNAME_MIN_LENGTH}자 이상 ${NICKNAME_MAX_LENGTH}자 이하로 입력해주세요.`,
  nameTooLong: `이름은 ${NAME_MAX_LENGTH}자 이하로 입력해주세요.`,
  invalidPhoneNumber: '전화번호 형식이 올바르지 않습니다. 휴대폰 번호를 확인해주세요.',
  invalidBirthDate: `생년월일은 ${BIRTH_DATE_LENGTH}자리(YYYYMMDD)로 정확히 입력해주세요.`,
}

/* 입력값에서 하이픈·공백을 걷어내고 숫자만 남긴다(백엔드는 숫자만 받는다). */
export function normalizePhoneNumber(value: string) {
  return value.replace(NON_DIGIT_PATTERN, '')
}

export function validateEmail(email: string) {
  if (email.length > EMAIL_MAX_LENGTH) return AUTH_ERROR_MESSAGE.emailTooLong
  if (!EMAIL_PATTERN.test(email)) return AUTH_ERROR_MESSAGE.invalidEmail
  return null
}

/* 로그인은 기존 비밀번호를 그대로 보내므로 백엔드 LoginRequest와 같은 최대 길이만 본다. */
export function validateLoginPassword(password: string) {
  if (password.length > PASSWORD_MAX_LENGTH) return AUTH_ERROR_MESSAGE.passwordTooLong
  return null
}

/* 새로 만드는 비밀번호는 백엔드 SignUpRequest의 길이·공백·특수문자 규칙을 모두 본다. */
export function validateNewPassword(password: string) {
  if (password.length < PASSWORD_MIN_LENGTH) return AUTH_ERROR_MESSAGE.passwordTooShort
  if (password.length > PASSWORD_MAX_LENGTH) return AUTH_ERROR_MESSAGE.passwordTooLong
  if (PASSWORD_WHITESPACE_PATTERN.test(password)) {
    return AUTH_ERROR_MESSAGE.passwordHasWhitespace
  }
  if (!PASSWORD_SPECIAL_CHARACTER_PATTERN.test(password)) {
    return AUTH_ERROR_MESSAGE.passwordNeedsSpecialCharacter
  }
  return null
}

export function validateNickname(nickname: string) {
  if (nickname.length < NICKNAME_MIN_LENGTH || nickname.length > NICKNAME_MAX_LENGTH) {
    return AUTH_ERROR_MESSAGE.invalidNickname
  }
  return null
}

export function validateName(name: string) {
  if (name.length > NAME_MAX_LENGTH) return AUTH_ERROR_MESSAGE.nameTooLong
  return null
}

/* 하이픈을 뺀 숫자만 받는다. 호출 전에 normalizePhoneNumber를 거친 값을 넘긴다. */
export function validatePhoneNumber(phoneNumber: string) {
  if (!PHONE_NUMBER_PATTERN.test(phoneNumber)) return AUTH_ERROR_MESSAGE.invalidPhoneNumber
  return null
}

/* YYYYMMDD 8자리. 존재하지 않는 날짜(20260231)와 미래 날짜를 함께 걸러낸다. */
export function validateBirthDate(birthDate: string) {
  if (birthDate.length !== BIRTH_DATE_LENGTH || !DIGITS_ONLY_PATTERN.test(birthDate)) {
    return AUTH_ERROR_MESSAGE.invalidBirthDate
  }

  const year = Number(birthDate.slice(0, BIRTH_DATE_YEAR_END))
  const month = Number(birthDate.slice(BIRTH_DATE_YEAR_END, BIRTH_DATE_MONTH_END))
  const day = Number(birthDate.slice(BIRTH_DATE_MONTH_END))
  const parsed = new Date(year, month - MONTH_INDEX_OFFSET, day)

  const isRealDate =
    parsed.getFullYear() === year &&
    parsed.getMonth() === month - MONTH_INDEX_OFFSET &&
    parsed.getDate() === day

  if (!isRealDate || year < MIN_BIRTH_YEAR || parsed.getTime() > Date.now()) {
    return AUTH_ERROR_MESSAGE.invalidBirthDate
  }
  return null
}
