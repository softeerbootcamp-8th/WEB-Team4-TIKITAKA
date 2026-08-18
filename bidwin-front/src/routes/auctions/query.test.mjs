import assert from 'node:assert/strict'
import test from 'node:test'
import { FIRST_PAGE, PAGE_WINDOW_SIZE } from './constants.ts'
import { getPageWindow } from './query.ts'

/* 페이지가 많아도 번호 버튼은 정해진 개수만 띄우고, 현재 페이지 주변만 보여준다. */

test('총 페이지가 창 크기보다 적으면 있는 만큼만 보여준다', () => {
  // given
  const totalPages = PAGE_WINDOW_SIZE - 2

  // when
  const pages = getPageWindow(FIRST_PAGE, totalPages)

  // then
  assert.deepEqual(pages, [1, 2, 3])
})

test('첫 페이지에서는 1번부터 창 크기만큼 보여준다', () => {
  // given
  const totalPages = 20

  // when
  const pages = getPageWindow(FIRST_PAGE, totalPages)

  // then
  assert.deepEqual(pages, [1, 2, 3, 4, 5])
})

/* 창이 현재 페이지를 따라 한 칸씩 움직이면 번호 버튼이 매번 흔들려, 묶음 단위로 끊어 보여준다. */
test('가운데 페이지에서는 그 페이지가 속한 묶음만 보여준다', () => {
  // given
  const currentPage = 10

  // when
  const pages = getPageWindow(currentPage, 20)

  // then
  assert.deepEqual(pages, [6, 7, 8, 9, 10])
  assert.equal(pages.length, PAGE_WINDOW_SIZE)
})

/* 마지막 쪽에서 창이 계속 따라가면 있지도 않은 페이지 번호가 생긴다. */
test('마지막 페이지에서는 창이 총 페이지를 넘지 않고 멈춘다', () => {
  // given
  const totalPages = 20

  // when
  const pages = getPageWindow(totalPages, totalPages)

  // then
  assert.deepEqual(pages, [16, 17, 18, 19, 20])
})

test('결과가 하나도 없으면 페이지 번호를 만들지 않는다', () => {
  // given
  const totalPages = 0

  // when
  const pages = getPageWindow(FIRST_PAGE, totalPages)

  // then
  assert.deepEqual(pages, [])
})
