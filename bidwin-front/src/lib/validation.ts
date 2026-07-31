const PASSWORD_LENGTH_PATTERN = /^\S{8,64}$/
const SPECIAL_CHAR_PATTERN = /[^A-Za-z0-9]/

export function getPasswordError(password: string) {
  if (!PASSWORD_LENGTH_PATTERN.test(password)) {
    return '비밀번호는 공백 없이 8자 이상 64자 이하여야 해요.'
  }
  if (!SPECIAL_CHAR_PATTERN.test(password)) {
    return '비밀번호에 특수문자를 1개 이상 포함해주세요.'
  }
  return ''
}
