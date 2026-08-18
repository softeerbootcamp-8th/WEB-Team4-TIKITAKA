import assert from 'node:assert/strict'
import test from 'node:test'
import {
  formatClock,
  formatDeadline,
  formatPartialPhoneNumber,
  formatPhoneNumber,
  formatRemainingDuration,
  formatWon,
} from './format.ts'

test('금액은 천 단위로 끊고 원을 붙여 보여준다', () => {
  // given
  const amount = 1_234_567

  // when
  const text = formatWon(amount)

  // then
  assert.equal(text, '1,234,567원')
})

test('남은 초는 두 자리 분과 초로 채워 보여준다', () => {
  // given
  const totalSeconds = 90

  // when
  const text = formatClock(totalSeconds)

  // then
  assert.equal(text, '01:30')
})

/* 카운트다운이 0에서 멈춘 뒤 한 틱 더 흘러도 음수 시간이 보이지 않아야 한다. */
test('남은 시간이 음수여도 00:00 아래로는 내려가지 않는다', () => {
  // given
  const overdueSeconds = -5

  // when
  const text = formatClock(overdueSeconds)

  // then
  assert.equal(text, '00:00')
})

/* 분·초 카운트다운 전용이라 60분이 넘어도 시간 단위로 올리지 않는다. */
test('한 시간이 넘어도 분으로 이어서 센다', () => {
  // given
  const totalSeconds = 60 * 75

  // when
  const text = formatClock(totalSeconds)

  // then
  assert.equal(text, '75:00')
})

/* 며칠 남은 결제·수령 기한은 가장 큰 단위 하나로만 보여준다. */
test('하루가 넘게 남으면 일 단위로만 보여준다', () => {
  // given
  const twoDaysAndThreeHours = (2 * 24 + 3) * 60 * 60 * 1000

  // when
  const text = formatRemainingDuration(twoDaysAndThreeHours)

  // then
  assert.equal(text, '2일')
})

test('하루가 안 남고 한 시간이 넘으면 시간 단위로만 보여준다', () => {
  // given
  const threeHoursAndHalf = 3.5 * 60 * 60 * 1000

  // when
  const text = formatRemainingDuration(threeHoursAndHalf)

  // then
  assert.equal(text, '3시간')
})

test('한 시간이 안 남으면 분 단위로 보여준다', () => {
  // given
  const twelveMinutes = 12 * 60 * 1000

  // when
  const text = formatRemainingDuration(twelveMinutes)

  // then
  assert.equal(text, '12분')
})

/* 아직 시간이 남았는데 "0분"이라고 하면 이미 끝난 것처럼 읽힌다. */
test('일 분이 채 안 남아도 0분이 아니라 1분으로 보여준다', () => {
  // given
  const thirtySeconds = 30 * 1000

  // when
  const text = formatRemainingDuration(thirtySeconds)

  // then
  assert.equal(text, '1분')
})

/* 이미 지난 기한은 "0분 남음"이 아니라 화면마다 다른 문구로 안내해야 해서 값을 돌려주지 않는다. */
test('이미 지난 기한은 남은 시간을 돌려주지 않는다', () => {
  // given
  const passed = 0
  const longPassed = -60 * 1000

  // when
  const texts = [passed, longPassed].map(formatRemainingDuration)

  // then
  assert.deepEqual(texts, [null, null])
})

test('열한 자리 전화번호는 3-4-4로 끊어 보여준다', () => {
  // given
  const digits = '01012345678'

  // when
  const text = formatPhoneNumber(digits)

  // then
  assert.equal(text, '010-1234-5678')
})

test('열 자리 전화번호는 3-3-4로 끊어 보여준다', () => {
  // given
  const digits = '0111234567'

  // when
  const text = formatPhoneNumber(digits)

  // then
  assert.equal(text, '011-123-4567')
})

test('끊을 자릿수가 모자란 번호는 손대지 않고 그대로 보여준다', () => {
  // given
  const digits = '010123'

  // when
  const text = formatPhoneNumber(digits)

  // then
  assert.equal(text, '010123')
})

/* 입력 중에는 앞에서부터 3-4-4로 채워, 타이핑하는 동안 하이픈이 뒤로 튀지 않게 한다. */
test('입력 중인 번호는 자릿수가 찰 때마다 앞에서부터 하이픈을 넣는다', () => {
  // given
  const typing = ['0', '010', '0101', '01012345', '01012345678']

  // when
  const texts = typing.map(formatPartialPhoneNumber)

  // then
  assert.deepEqual(texts, ['0', '010', '010-1', '010-1234-5', '010-1234-5678'])
})

/* 지우는 도중 "010-1234-"처럼 하이픈만 매달린 상태가 보이면 안 된다. */
test('다음 자리가 비어 있으면 하이픈을 붙이지 않는다', () => {
  // given
  const groupJustFilled = '0101234'

  // when
  const text = formatPartialPhoneNumber(groupJustFilled)

  // then
  assert.equal(text, '010-1234')
})

test('한 자리도 입력하지 않았으면 빈 문자열을 보여준다', () => {
  // given
  const empty = ''

  // when
  const text = formatPartialPhoneNumber(empty)

  // then
  assert.equal(text, '')
})

test('마감 시각은 월·일·시·분을 두 자리로 채워 보여준다', () => {
  // given
  const deadline = new Date(2026, 7, 5, 9, 7)

  // when
  const text = formatDeadline(deadline)

  // then
  assert.equal(text, '2026.08.05 09:07 마감')
})
