import assert from 'node:assert/strict'
import test from 'node:test'
import { formatTimeOfDay } from './format.ts'

test('하향 경매 변동 시각을 한국 시간으로 표시한다', () => {
  const droppedAt = new Date('2026-08-18T00:00:00.000Z')

  assert.equal(formatTimeOfDay(droppedAt, 'Asia/Seoul'), '09:00:00')
})
