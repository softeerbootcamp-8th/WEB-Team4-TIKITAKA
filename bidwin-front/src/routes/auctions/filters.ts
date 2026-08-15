import type { LucideIcon } from 'lucide-react'
import type {
  AuctionCategory,
  AuctionCategoryOption,
  AuctionListStatusFilter,
} from '../../lib/api/auctions'

/*
 * 목록 화면의 필터 "스키마".
 *
 * 화면(사이드바 / 모달 / 선택 요약 칩)은 전부 같은 FilterGroup 목록을 읽어서 그린다.
 *
 * 구조는 3단계다.
 *   그룹(FilterGroup)   — 사이드바 한 줄. 예: 카테고리, 지역, 가격대
 *   섹션(FilterSection) — 모달 가운데 열. 그룹 안의 갈래. 예: 디지털/가전, 패션
 *   옵션(FilterOption)  — 모달 오른쪽 칩. 실제로 고르는 값. 예: 노트북, 태블릿
 * 갈래가 필요 없는 그룹이면 섹션 하나만 두면 된다.
 *
 */

export interface FilterOption {
  id: string
  label: string
}

export interface FilterSection {
  id: string
  label: string
  /** 모달 오른쪽 열 머리말에 붙는 아이콘 (lucide-react) */
  icon?: LucideIcon
  options: FilterOption[]
  emptyText?: string
}

export interface FilterGroup {
  id: string
  label: string
  /** 모달 오른쪽 상단에 회색으로 붙는 안내 문구 */
  guide: string
  /** 여러 옵션을 동시에 고를 수 있는지 */
  multiple: boolean
  sections: FilterSection[]
}

/** 그룹 id → 선택된 옵션 id 목록 */
export type FilterSelection = Record<string, string[]>

export const FILTER_GROUP_ID = {
  status: 'status',
  category: 'category',
} as const

export const DEFAULT_FILTER_SELECTION: FilterSelection = {
  [FILTER_GROUP_ID.status]: ['ACTIVE'],
}

const DISABLED_FILTERS: {
  status?: AuctionListStatusFilter
  category?: AuctionCategory
} = {}

export function createFilterGroups(
  categories: readonly AuctionCategoryOption[] | null,
): FilterGroup[] {
  return [
    {
      id: FILTER_GROUP_ID.status,
      label: '경매 상태',
      guide: '하나를 선택하세요',
      multiple: false,
      sections: [
        {
          id: FILTER_GROUP_ID.status,
          label: '경매 상태',
          options: [
            { id: 'ACTIVE', label: '활성' },
            { id: 'ENDED', label: '종료' },
          ],
        },
      ],
    },
    {
      id: FILTER_GROUP_ID.category,
      label: '카테고리',
      guide: '하나를 선택하세요',
      multiple: false,
      sections: [
        {
          id: FILTER_GROUP_ID.category,
          label: '카테고리',
          options: categories?.map(({ code, label }) => ({ id: code, label })) ?? [],
          emptyText: categories === null
            ? '카테고리를 불러오는 중…'
            : '사용 가능한 카테고리가 없어요.',
        },
      ],
    },
  ]
}

export function toAuctionListFilters(
  selection: FilterSelection,
  isEnabled: boolean,
): {
  status?: AuctionListStatusFilter
  category?: AuctionCategory
} {
  if (!isEnabled) return DISABLED_FILTERS
  return {
    status: getSelectedIds(selection, FILTER_GROUP_ID.status)[0] as
      | AuctionListStatusFilter
      | undefined,
    category: getSelectedIds(selection, FILTER_GROUP_ID.category)[0] as
      | AuctionCategory
      | undefined,
  }
}

export function hasNonDefaultSelection(selection: FilterSelection): boolean {
  const statuses = getSelectedIds(selection, FILTER_GROUP_ID.status)
  return statuses.length !== 1
    || statuses[0] !== 'ACTIVE'
    || getSelectedIds(selection, FILTER_GROUP_ID.category).length > 0
}

export function getSelectedIds(selection: FilterSelection, groupId: string): string[] {
  return selection[groupId] ?? []
}

/** 그룹 전체 옵션을 평평하게 편다. 선택 요약 칩에서 id로 라벨을 찾을 때 쓴다. */
export function getGroupOptions(group: FilterGroup): FilterOption[] {
  return group.sections.flatMap((section) => section.options)
}

/**
 * 사이드바에서 그룹 이름 옆에 작게 붙는 요약.
 * 하나만 골랐으면 그 옵션 이름을, 여러 개면 "N개"를 보여준다. 아무것도 없으면 null.
 */
export function summarizeGroup(group: FilterGroup, selection: FilterSelection): string | null {
  const selectedIds = getSelectedIds(selection, group.id)
  if (selectedIds.length === 0) return null
  if (selectedIds.length === 1) {
    const option = getGroupOptions(group).find((item) => item.id === selectedIds[0])
    return option?.label ?? null
  }
  return `${selectedIds.length}개`
}

/** 옵션 하나를 켜고 끈다. 단일 선택 그룹이면 기존 선택을 대체한다. */
export function toggleOption(
  selection: FilterSelection,
  group: FilterGroup,
  optionId: string,
): FilterSelection {
  const selectedIds = getSelectedIds(selection, group.id)
  const isSelected = selectedIds.includes(optionId)

  const nextIds = group.multiple
    ? isSelected
      ? selectedIds.filter((id) => id !== optionId)
      : [...selectedIds, optionId]
    : isSelected
      ? []
      : [optionId]

  if (nextIds.length === 0) {
    const { [group.id]: _removed, ...rest } = selection
    return rest
  }
  return { ...selection, [group.id]: nextIds }
}

export function clearGroup(selection: FilterSelection, groupId: string): FilterSelection {
  const { [groupId]: _removed, ...rest } = selection
  return rest
}

export function countSelectedOptions(selection: FilterSelection): number {
  return Object.values(selection).reduce((total, ids) => total + ids.length, 0)
}
