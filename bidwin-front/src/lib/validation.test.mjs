import assert from 'node:assert/strict'
import test from 'node:test'
import { getPasswordError } from './validation.ts'

/*
 * 비밀번호 변경·재설정 화면이 쓰는 규칙.
 * 통과하면 빈 문자열, 실패하면 화면에 그대로 보여줄 메시지를 돌려준다.
 */

test('공백 없이 8자 이상이고 특수문자를 포함한 비밀번호는 통과한다', () => {
  // given
  const password = 'bidwin-1234!'

  // when
  const error = getPasswordError(password)

  // then
  assert.equal(error, '')
})

test('8자보다 짧은 비밀번호는 막는다', () => {
  // given
  const tooShort = 'bid1!'

  // when
  const error = getPasswordError(tooShort)

  // then
  assert.notEqual(error, '')
})

test('64자를 넘는 비밀번호는 막는다', () => {
  // given
  const tooLong = `${'a'.repeat(64)}!`

  // when
  const error = getPasswordError(tooLong)

  // then
  assert.notEqual(error, '')
})

/* 공백은 길이 규칙 안에서 함께 걸러, 안내 문구를 하나로 유지한다. */
test('공백이 섞인 비밀번호는 길이 안내와 같은 문구로 막는다', () => {
  // given
  const withWhitespace = 'bidwin 1234!'
  const tooShort = 'bid1!'

  // when
  const errors = [withWhitespace, tooShort].map(getPasswordError)

  // then
  assert.notEqual(errors[0], '')
  assert.equal(errors[0], errors[1])
})

test('특수문자가 없는 비밀번호는 길이와 다른 문구로 막는다', () => {
  // given
  const withoutSpecialCharacter = 'bidwin1234'
  const tooShort = 'bid1!'

  // when
  const errors = [withoutSpecialCharacter, tooShort].map(getPasswordError)

  // then
  assert.notEqual(errors[0], '')
  assert.notEqual(errors[0], errors[1])
})
