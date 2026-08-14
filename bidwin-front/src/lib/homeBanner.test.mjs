import assert from 'node:assert/strict'
import test from 'node:test'
import {
  HOME_BANNER_ITEMS,
  HOME_BANNER_ROTATION_MS,
  HOME_BANNER_TRANSITION_MS,
  homeBannerTransitionDuration,
  nextHomeBannerIndex,
} from './homeBanner.ts'

test('배너 문구는 정해진 순서로 순환한다', () => {
  // given
  let index = 0

  // when
  const sequence = Array.from({ length: HOME_BANNER_ITEMS.length + 1 }, () => {
    const item = HOME_BANNER_ITEMS[index].label
    index = nextHomeBannerIndex(index)
    return item
  })

  // then
  assert.deepEqual(sequence, ['이불', '1+1 음식', '매트리스', '책상', '무드등', '건조기', '이불'])
})

test('배너는 3초마다 1초 동안 전환한다', () => {
  // given
  const expectedTiming = { rotation: 3000, transition: 1000 }

  // when
  const timing = {
    rotation: HOME_BANNER_ROTATION_MS,
    transition: HOME_BANNER_TRANSITION_MS,
  }

  // then
  assert.deepEqual(timing, expectedTiming)
})

test('롤링이 끝나면 반대 방향으로 되돌아가는 전환을 실행하지 않는다', () => {
  // given
  const isRolling = false

  // when
  const duration = homeBannerTransitionDuration(isRolling)

  // then
  assert.equal(duration, '0ms')
})
