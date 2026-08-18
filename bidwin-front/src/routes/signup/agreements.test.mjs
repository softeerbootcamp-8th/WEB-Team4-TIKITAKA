import assert from 'node:assert/strict'
import test from 'node:test'
import {
  AGREEMENT_ITEMS,
  INITIAL_AGREEMENT_STATE,
  acceptEveryAgreement,
  isEveryAgreementAccepted,
} from './agreements.ts'

/* 필수 동의 항목이 늘어나도 화면과 검증이 함께 따라오도록 목록 하나만 보고 판단한다. */

test('가입 화면은 아무 항목도 동의하지 않은 상태로 시작한다', () => {
  // given
  const state = INITIAL_AGREEMENT_STATE

  // when
  const accepted = isEveryAgreementAccepted(state)

  // then
  assert.equal(accepted, false)
})

/* 초기 상태에 빠진 항목이 있으면 그 항목은 화면에서 체크할 수 없게 된다. */
test('초기 상태는 필수 동의 항목을 빠짐없이 담는다', () => {
  // given
  const requiredIds = AGREEMENT_ITEMS.map((item) => item.id)

  // when
  const stateIds = Object.keys(INITIAL_AGREEMENT_STATE)

  // then
  assert.deepEqual(stateIds.toSorted(), requiredIds.toSorted())
})

test('모든 필수 항목에 동의해야 가입을 진행할 수 있다', () => {
  // given
  const state = acceptEveryAgreement(true)

  // when
  const accepted = isEveryAgreementAccepted(state)

  // then
  assert.equal(accepted, true)
})

test('한 항목이라도 동의하지 않으면 가입을 막는다', () => {
  // given
  const partiallyAccepted = AGREEMENT_ITEMS.map((item) => ({
    ...acceptEveryAgreement(true),
    [item.id]: false,
  }))

  // when
  const accepted = partiallyAccepted.map(isEveryAgreementAccepted)

  // then
  assert.deepEqual(accepted, partiallyAccepted.map(() => false))
})

test('전체 동의를 해제하면 모든 항목이 함께 풀린다', () => {
  // given
  const everyAccepted = acceptEveryAgreement(true)

  // when
  const everyCleared = acceptEveryAgreement(false)

  // then
  assert.equal(isEveryAgreementAccepted(everyAccepted), true)
  assert.deepEqual(Object.values(everyCleared), Object.values(everyCleared).map(() => false))
})
