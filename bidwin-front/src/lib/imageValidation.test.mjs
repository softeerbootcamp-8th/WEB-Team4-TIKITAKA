import assert from 'node:assert/strict'
import test from 'node:test'
import { detectImageMimeType } from './imageValidation.ts'

test('파일 시그니처로 JPEG PNG WEBP의 실제 MIME을 구분한다', () => {
  // given
  const jpeg = new Uint8Array([0xff, 0xd8, 0xff])
  const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  const webp = new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50])

  // when
  const detected = [jpeg, png, webp].map(detectImageMimeType)

  // then
  assert.deepEqual(detected, ['image/jpeg', 'image/png', 'image/webp'])
})

test('이미지 시그니처가 없는 파일은 이미지 MIME으로 판별하지 않는다', () => {
  // given
  const text = new TextEncoder().encode('not an image')

  // when
  const detected = detectImageMimeType(text)

  // then
  assert.equal(detected, null)
})
