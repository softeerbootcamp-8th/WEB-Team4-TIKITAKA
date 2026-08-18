import assert from 'node:assert/strict'
import test from 'node:test'
import {
  AUTH_ERROR_MESSAGE,
  EMAIL_MAX_LENGTH,
  NAME_MAX_LENGTH,
  NICKNAME_MAX_LENGTH,
  NICKNAME_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  normalizePhoneNumber,
  validateBirthDate,
  validateEmail,
  validateLoginPassword,
  validateName,
  validateNewPassword,
  validateNickname,
  validatePhoneNumber,
} from './validation.ts'

/* 통과는 null, 실패는 화면에 그대로 보여줄 메시지를 돌려준다. */

test('아이디@도메인.최상위 형태를 갖춘 이메일은 통과한다', () => {
  // given
  const email = 'bidwin@example.com'

  // when
  const error = validateEmail(email)

  // then
  assert.equal(error, null)
})

test('골뱅이나 점이 빠진 이메일은 형식 오류로 막는다', () => {
  // given
  const withoutAt = 'bidwin.example.com'
  const withoutDot = 'bidwin@example'

  // when
  const errors = [withoutAt, withoutDot].map(validateEmail)

  // then
  assert.deepEqual(errors, [AUTH_ERROR_MESSAGE.invalidEmail, AUTH_ERROR_MESSAGE.invalidEmail])
})

test('형식이 맞아도 최대 길이를 넘긴 이메일은 길이 오류로 막는다', () => {
  // given
  const domain = '@example.com'
  const tooLong = `${'a'.repeat(EMAIL_MAX_LENGTH - domain.length + 1)}${domain}`

  // when
  const error = validateEmail(tooLong)

  // then
  assert.equal(tooLong.length, EMAIL_MAX_LENGTH + 1)
  assert.equal(error, AUTH_ERROR_MESSAGE.emailTooLong)
})

/*
 * 로그인은 이미 만들어진 비밀번호를 그대로 보낸다. 규칙이 바뀌기 전에 가입한 사람이
 * 로그인조차 못 하는 일이 없도록, 길이·공백·특수문자 규칙은 새 비밀번호에만 건다.
 */
test('로그인 비밀번호는 짧거나 특수문자가 없어도 통과한다', () => {
  // given
  const oldStylePassword = 'abc'

  // when
  const error = validateLoginPassword(oldStylePassword)

  // then
  assert.equal(error, null)
})

test('로그인 비밀번호도 최대 길이를 넘기면 막는다', () => {
  // given
  const tooLong = 'a'.repeat(PASSWORD_MAX_LENGTH + 1)

  // when
  const error = validateLoginPassword(tooLong)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.passwordTooLong)
})

test('길이·공백·특수문자 규칙을 모두 만족하는 새 비밀번호는 통과한다', () => {
  // given
  const password = 'bidwin-1234!'

  // when
  const error = validateNewPassword(password)

  // then
  assert.equal(error, null)
})

test('새 비밀번호가 최소 길이보다 짧으면 짧다고 알린다', () => {
  // given
  const tooShort = `${'a'.repeat(PASSWORD_MIN_LENGTH - 2)}!`

  // when
  const error = validateNewPassword(tooShort)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.passwordTooShort)
})

test('새 비밀번호가 최대 길이를 넘으면 길다고 알린다', () => {
  // given
  const tooLong = `${'a'.repeat(PASSWORD_MAX_LENGTH)}!`

  // when
  const error = validateNewPassword(tooLong)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.passwordTooLong)
})

test('새 비밀번호에 공백이 섞이면 공백을 쓸 수 없다고 알린다', () => {
  // given
  const withWhitespace = 'bidwin 1234!'

  // when
  const error = validateNewPassword(withWhitespace)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.passwordHasWhitespace)
})

test('문자와 숫자로만 이뤄진 새 비밀번호는 특수문자를 넣으라고 알린다', () => {
  // given
  const withoutSpecialCharacter = 'bidwin1234'

  // when
  const error = validateNewPassword(withoutSpecialCharacter)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.passwordNeedsSpecialCharacter)
})

test('경계 길이 닉네임은 통과한다', () => {
  // given
  const shortest = '가'.repeat(NICKNAME_MIN_LENGTH)
  const longest = '가'.repeat(NICKNAME_MAX_LENGTH)

  // when
  const errors = [shortest, longest].map(validateNickname)

  // then
  assert.deepEqual(errors, [null, null])
})

test('닉네임이 허용 길이를 벗어나면 막는다', () => {
  // given
  const tooShort = '가'.repeat(NICKNAME_MIN_LENGTH - 1)
  const tooLong = '가'.repeat(NICKNAME_MAX_LENGTH + 1)

  // when
  const errors = [tooShort, tooLong].map(validateNickname)

  // then
  assert.deepEqual(errors, [
    AUTH_ERROR_MESSAGE.invalidNickname,
    AUTH_ERROR_MESSAGE.invalidNickname,
  ])
})

test('이름이 최대 길이를 넘으면 막는다', () => {
  // given
  const longest = '김'.repeat(NAME_MAX_LENGTH)
  const tooLong = '김'.repeat(NAME_MAX_LENGTH + 1)

  // when
  const errors = [longest, tooLong].map(validateName)

  // then
  assert.deepEqual(errors, [null, AUTH_ERROR_MESSAGE.nameTooLong])
})

test('전화번호에서 하이픈과 공백을 걷어내고 숫자만 남긴다', () => {
  // given
  const typed = '010-1234 5678'

  // when
  const normalized = normalizePhoneNumber(typed)

  // then
  assert.equal(normalized, '01012345678')
})

test('통신사 식별번호로 시작하는 10자리·11자리 번호는 통과한다', () => {
  // given
  const tenDigits = '0111234567'
  const elevenDigits = '01012345678'

  // when
  const errors = [tenDigits, elevenDigits].map(validatePhoneNumber)

  // then
  assert.deepEqual(errors, [null, null])
})

test('휴대폰이 아닌 번호는 막는다', () => {
  // given
  const landline = '0212345678'

  // when
  const error = validatePhoneNumber(landline)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidPhoneNumber)
})

test('자릿수가 모자란 번호는 막는다', () => {
  // given
  const tooFewDigits = '010123456'

  // when
  const error = validatePhoneNumber(tooFewDigits)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidPhoneNumber)
})

/* 검증은 숫자만 남은 값을 받는 계약이다. 화면이 정규화를 건너뛰면 통과시키지 않는다. */
test('하이픈이 남아 있는 번호는 정규화를 거치지 않았으므로 막는다', () => {
  // given
  const notNormalized = '010-1234-5678'

  // when
  const error = validatePhoneNumber(notNormalized)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidPhoneNumber)
})

test('실제로 있는 날짜의 생년월일은 통과한다', () => {
  // given
  const leapDay = '20240229'

  // when
  const error = validateBirthDate(leapDay)

  // then
  assert.equal(error, null)
})

test('생년월일이 8자리가 아니면 형식 오류로 막는다', () => {
  // given
  const sevenDigits = '2024022'

  // when
  const error = validateBirthDate(sevenDigits)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidBirthDate)
})

test('생년월일에 숫자가 아닌 문자가 섞이면 형식 오류로 막는다', () => {
  // given
  const withHyphen = '2024-2-29'

  // when
  const error = validateBirthDate(withHyphen)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidBirthDate)
})

/* 8자리 숫자라도 달력에 없는 날이면 막는다(윤년이 아닌 해의 2월 29일). */
test('달력에 없는 날짜는 형식 오류로 막는다', () => {
  // given
  const notALeapYear = '20250229'

  // when
  const error = validateBirthDate(notALeapYear)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidBirthDate)
})

/*
 * 형식은 맞지만 연도가 비현실적인 경우는 형식 오류와 다른 메시지로 안내한다.
 * "8자리로 입력하세요"만 반복하면 무엇이 틀렸는지 알 수 없다.
 */
test('1900년보다 앞선 생년월일은 형식이 아니라 연도 오류로 알린다', () => {
  // given
  const beforeMinimumYear = '18991231'

  // when
  const error = validateBirthDate(beforeMinimumYear)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidBirthYear)
})

test('아직 오지 않은 날짜는 연도 오류로 알린다', () => {
  // given
  const future = new Date(Date.now() + 24 * 60 * 60 * 1000)
  const tomorrow = `${future.getFullYear()}${String(future.getMonth() + 1).padStart(2, '0')}${String(future.getDate()).padStart(2, '0')}`

  // when
  const error = validateBirthDate(tomorrow)

  // then
  assert.equal(error, AUTH_ERROR_MESSAGE.invalidBirthYear)
})
