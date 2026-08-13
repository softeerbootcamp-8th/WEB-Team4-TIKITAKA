import assert from 'node:assert/strict'
import test from 'node:test'
import { estimateServerOffsetMs } from './serverClock.ts'

test('서버 시각은 RTT의 중간 지점을 기준으로 로컬 시각을 보정한다', () => {
  // given
  const serverTime = 10_150
  const clientRequestedAt = 10_000
  const roundTripTimeMs = 100

  // when
  const serverOffsetMs = estimateServerOffsetMs(serverTime, clientRequestedAt, roundTripTimeMs)

  // then
  assert.equal(serverOffsetMs, 100)
})

test('로컬 시계가 뒤로 조정돼도 음수 RTT를 가격 시각에 반영하지 않는다', () => {
  // given
  const serverTime = 10_100
  const clientRequestedAt = 10_000

  // when
  const serverOffsetMs = estimateServerOffsetMs(serverTime, clientRequestedAt, -20)

  // then
  assert.equal(serverOffsetMs, 100)
})
