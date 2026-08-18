import assert from 'node:assert/strict'
import test from 'node:test'
import {
  DEFAULT_FILTER_SELECTION,
  FILTER_GROUP_ID,
  clearGroup,
  countSelectedOptions,
  createFilterGroups,
  hasNonDefaultSelection,
  summarizeGroup,
  toAuctionListFilters,
  toggleOption,
} from './filters.ts'

const CATEGORIES = [
  { code: 'ELECTRONICS', label: '디지털/가전' },
  { code: 'FASHION', label: '패션/잡화' },
]

function groupOf(id, categories = CATEGORIES) {
  return createFilterGroups(categories).find((group) => group.id === id)
}

const MULTI_SELECT_GROUP = {
  id: 'tag',
  label: '태그',
  guide: '여러 개를 선택할 수 있어요',
  multiple: true,
  sections: [
    {
      id: 'tag',
      label: '태그',
      options: [
        { id: 'new', label: '새 상품' },
        { id: 'fast', label: '빠른 배송' },
      ],
    },
  ],
}

/* 목록에 처음 들어오면 이미 끝난 경매까지 섞이지 않게 활성 경매만 보여준다. */
test('아무것도 고르지 않은 첫 화면은 활성 경매만 보여준다', () => {
  // given
  const selection = DEFAULT_FILTER_SELECTION

  // when
  const filters = toAuctionListFilters(selection, true)

  // then
  assert.deepEqual(filters, { status: 'ACTIVE', category: undefined })
})

/* 필터를 끄면 고른 값이 남아 있어도 조건 없이 전체를 조회한다. */
test('필터를 끄면 고른 값이 남아 있어도 조건 없이 조회한다', () => {
  // given
  const selection = { status: ['ENDED'], category: ['FASHION'] }

  // when
  const filters = toAuctionListFilters(selection, false)

  // then
  assert.deepEqual(filters, {})
})

test('고른 상태와 카테고리를 목록 조회 조건으로 넘긴다', () => {
  // given
  const selection = { status: ['ENDED'], category: ['ELECTRONICS'] }

  // when
  const filters = toAuctionListFilters(selection, true)

  // then
  assert.deepEqual(filters, { status: 'ENDED', category: 'ELECTRONICS' })
})

/* 하나만 고를 수 있는 그룹은 새로 누른 값이 앞의 선택을 밀어낸다. */
test('단일 선택 그룹은 다른 옵션을 누르면 앞의 선택을 대체한다', () => {
  // given
  const group = groupOf(FILTER_GROUP_ID.status)
  const selection = { [FILTER_GROUP_ID.status]: ['ACTIVE'] }

  // when
  const next = toggleOption(selection, group, 'ENDED')

  // then
  assert.deepEqual(next[FILTER_GROUP_ID.status], ['ENDED'])
})

test('여러 개를 고를 수 있는 그룹은 누른 옵션을 쌓는다', () => {
  // given
  const selection = { tag: ['new'] }

  // when
  const next = toggleOption(selection, MULTI_SELECT_GROUP, 'fast')

  // then
  assert.deepEqual(next.tag, ['new', 'fast'])
})

test('이미 고른 옵션을 다시 누르면 그 옵션만 빠진다', () => {
  // given
  const selection = { tag: ['new', 'fast'] }

  // when
  const next = toggleOption(selection, MULTI_SELECT_GROUP, 'new')

  // then
  assert.deepEqual(next.tag, ['fast'])
})

/* 빈 배열이 남으면 "고른 게 없는데 그룹은 있는" 상태가 되어 요약·개수가 어긋난다. */
test('마지막 옵션까지 해제하면 그룹 자체가 선택에서 사라진다', () => {
  // given
  const group = groupOf(FILTER_GROUP_ID.category)
  const selection = { [FILTER_GROUP_ID.category]: ['ELECTRONICS'] }

  // when
  const next = toggleOption(selection, group, 'ELECTRONICS')

  // then
  assert.equal(FILTER_GROUP_ID.category in next, false)
})

test('옵션을 켜고 꺼도 원래 선택 상태는 그대로 남는다', () => {
  // given
  const group = groupOf(FILTER_GROUP_ID.status)
  const selection = { [FILTER_GROUP_ID.status]: ['ACTIVE'] }

  // when
  toggleOption(selection, group, 'ENDED')

  // then
  assert.deepEqual(selection, { [FILTER_GROUP_ID.status]: ['ACTIVE'] })
})

test('그룹을 초기화하면 그 그룹의 선택만 사라진다', () => {
  // given
  const selection = { status: ['ENDED'], category: ['FASHION'] }

  // when
  const next = clearGroup(selection, FILTER_GROUP_ID.category)

  // then
  assert.deepEqual(next, { status: ['ENDED'] })
})

test('고른 게 없는 그룹은 사이드바에 요약을 붙이지 않는다', () => {
  // given
  const group = groupOf(FILTER_GROUP_ID.category)

  // when
  const summary = summarizeGroup(group, {})

  // then
  assert.equal(summary, null)
})

test('하나만 고른 그룹은 그 옵션의 이름을 요약으로 보여준다', () => {
  // given
  const group = groupOf(FILTER_GROUP_ID.category)

  // when
  const summary = summarizeGroup(group, { [FILTER_GROUP_ID.category]: ['FASHION'] })

  // then
  assert.equal(summary, '패션/잡화')
})

test('여러 개를 고른 그룹은 개수로 요약한다', () => {
  // given
  const selection = { tag: ['new', 'fast'] }

  // when
  const summary = summarizeGroup(MULTI_SELECT_GROUP, selection)

  // then
  assert.equal(summary, '2개')
})

test('기본 선택 그대로면 필터를 건드리지 않은 것으로 본다', () => {
  // given
  const selection = DEFAULT_FILTER_SELECTION

  // when
  const changed = hasNonDefaultSelection(selection)

  // then
  assert.equal(changed, false)
})

test('상태를 바꾸거나 카테고리를 더하거나 상태를 비우면 필터를 건드린 것으로 본다', () => {
  // given
  const statusChanged = { status: ['ENDED'] }
  const categoryAdded = { status: ['ACTIVE'], category: ['FASHION'] }
  const statusCleared = {}

  // when
  const changed = [statusChanged, categoryAdded, statusCleared].map(hasNonDefaultSelection)

  // then
  assert.deepEqual(changed, [true, true, true])
})

test('고른 옵션 개수는 그룹을 가리지 않고 모두 더한다', () => {
  // given
  const selection = { status: ['ENDED'], tag: ['new', 'fast'] }

  // when
  const count = countSelectedOptions(selection)

  // then
  assert.equal(count, 3)
})

/* 카테고리를 아직 못 받아온 상태와 받아왔는데 없는 상태는 안내 문구가 달라야 한다. */
test('카테고리를 불러오는 중과 카테고리가 없는 경우를 다른 문구로 안내한다', () => {
  // given
  const loading = groupOf(FILTER_GROUP_ID.category, null)
  const loadedButEmpty = groupOf(FILTER_GROUP_ID.category, [])

  // when
  const [loadingText, emptyText] = [loading, loadedButEmpty].map(
    (group) => group.sections[0].emptyText,
  )

  // then
  assert.notEqual(loadingText, emptyText)
})

test('서버에서 받은 카테고리를 그대로 고를 수 있는 옵션으로 펼친다', () => {
  // given
  const group = groupOf(FILTER_GROUP_ID.category)

  // when
  const options = group.sections[0].options

  // then
  assert.deepEqual(options, [
    { id: 'ELECTRONICS', label: '디지털/가전' },
    { id: 'FASHION', label: '패션/잡화' },
  ])
})
