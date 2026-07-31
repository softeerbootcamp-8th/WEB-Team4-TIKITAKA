import type { LucideIcon } from 'lucide-react'
import type { AuctionSummary } from './types'

/*
 * 목록 화면의 필터 "스키마".
 *
 * 화면(사이드바 / 모달 / 선택 요약 칩)은 전부 아래 FILTER_GROUPS를 읽어서 그려진다.
 * 그래서 새 필터 항목을 추가할 때 컴포넌트는 건드릴 필요가 없고, 이 파일의 배열에
 * 그룹을 한 덩어리 넣기만 하면 사이드바 행 · + 버튼 · 모달 3단 구성 · 선택 요약이
 * 한꺼번에 생긴다.
 *
 * 구조는 3단계다.
 *   그룹(FilterGroup)   — 사이드바 한 줄. 예: 카테고리, 지역, 가격대
 *   섹션(FilterSection) — 모달 가운데 열. 그룹 안의 갈래. 예: 디지털/가전, 패션
 *   옵션(FilterOption)  — 모달 오른쪽 칩. 실제로 고르는 값. 예: 노트북, 태블릿
 * 갈래가 필요 없는 그룹이면 섹션 하나만 두면 된다.
 *
 * 예시 (실제 필터가 정해지면 아래 형태로 배열에 넣는다):
 *
 *   {
 *     id: 'category',
 *     label: '카테고리',
 *     guide: '하나를 선택하세요',
 *     multiple: true,
 *     sections: [
 *       {
 *         id: 'digital',
 *         label: '디지털/가전',
 *         icon: Laptop,
 *         options: [
 *           { id: 'laptop', label: '노트북' },
 *           { id: 'tablet', label: '태블릿' },
 *         ],
 *       },
 *     ],
 *     match: (auction, selected) => selected.includes(auction.category),
 *   }
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
}

export interface FilterGroup {
  id: string
  label: string
  /** 모달 오른쪽 상단에 회색으로 붙는 안내 문구 */
  guide: string
  /** 여러 옵션을 동시에 고를 수 있는지 */
  multiple: boolean
  sections: FilterSection[]
  /**
   * 고른 옵션으로 경매를 거르는 규칙. 필터 항목을 추가할 때 이 함수도 같이 채운다.
   * 비워 두면 UI에만 노출되고 결과에는 영향을 주지 않는다.
   */
  match?: (auction: AuctionSummary, selectedOptionIds: string[]) => boolean
}

/** 그룹 id → 선택된 옵션 id 목록 */
export type FilterSelection = Record<string, string[]>

/*
 * 필터 항목은 아직 정해지지 않아 비어 있다.
 * 위 주석의 형태대로 그룹을 추가하면 사이드바와 모달이 자동으로 따라 그려진다.
 */
export const FILTER_GROUPS: FilterGroup[] = []

export const EMPTY_SELECTION: FilterSelection = {}

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
