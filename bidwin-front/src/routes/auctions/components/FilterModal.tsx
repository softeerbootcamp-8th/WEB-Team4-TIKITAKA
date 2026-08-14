import { RotateCcw, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import Button from '../../../components/ui/Button'
import { FILTER_MODAL_TEXT } from '../constants'
import {
  DEFAULT_FILTER_SELECTION,
  clearGroup,
  getSelectedIds,
  summarizeGroup,
  toggleOption,
} from '../filters'
import type { FilterGroup, FilterSelection } from '../filters'

/*
 * + 를 누르면 뜨는 필터 모달. 여기서 모든 필터 그룹을 한 번에 지정한다.
 * 어느 + 를 눌렀는지(initialGroupId)에 따라 그 그룹이 먼저 펼쳐진 상태로 열린다.
 *
 * 왼쪽 = 그룹 / 가운데 = 그룹 안의 갈래 / 오른쪽 = 실제로 고르는 옵션 칩.
 * 갈래가 하나뿐인 그룹은 가운데 열을 접고 옵션만 보여준다.
 */
const ESCAPE_KEY = 'Escape'

function FilterModal({
  groups,
  initialGroupId,
  selection,
  onApply,
  onClose,
}: {
  groups: readonly FilterGroup[]
  initialGroupId: string
  selection: FilterSelection
  onApply: (selection: FilterSelection) => void
  onClose: () => void
}) {
  const [draft, setDraft] = useState<FilterSelection>(selection)
  const [activeGroupId, setActiveGroupId] = useState(initialGroupId)
  const [sectionByGroup, setSectionByGroup] = useState<Record<string, string>>({})

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === ESCAPE_KEY) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const activeGroup = groups.find((group) => group.id === activeGroupId) ?? groups[0]
  if (!activeGroup || activeGroup.sections.length === 0) return null

  const activeSection =
    activeGroup.sections.find((section) => section.id === sectionByGroup[activeGroup.id]) ??
    activeGroup.sections[0]
  const SectionIcon = activeSection.icon

  const selectedGroups = groups.filter(
    (group) => getSelectedIds(draft, group.id).length > 0,
  )

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-lg"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={FILTER_MODAL_TEXT.title}
        onClick={(event) => event.stopPropagation()}
        className="flex max-h-[82dvh] w-full max-w-[900px] flex-col overflow-hidden rounded-xl bg-canvas shadow-card"
      >
        <div className="flex min-h-0 flex-1 flex-col md:flex-row">
          <nav className="shrink-0 overflow-y-auto border-b border-hairline-soft p-lg md:w-[200px] md:border-b-0 md:border-r">
            <h2 className="mb-base text-lg font-bold text-ink">{FILTER_MODAL_TEXT.title}</h2>
            <ul className="flex gap-1 overflow-x-auto md:flex-col md:overflow-visible">
              {groups.map((group) => (
                <li key={group.id} className="shrink-0 md:shrink">
                  <GroupTab
                    group={group}
                    isActive={group.id === activeGroup.id}
                    selectedCount={getSelectedIds(draft, group.id).length}
                    onSelect={() => setActiveGroupId(group.id)}
                  />
                </li>
              ))}
            </ul>
          </nav>

          <div className="flex min-h-0 flex-1 flex-col">
            <div className="flex shrink-0 items-center gap-sm border-b border-hairline-soft px-lg py-base">
              <h3 className="text-lg font-bold text-ink">{activeGroup.label}</h3>
              <p className="text-sm text-muted">{activeGroup.guide}</p>
              <button
                type="button"
                onClick={onClose}
                aria-label={FILTER_MODAL_TEXT.close}
                className="ml-auto flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-hairline text-body transition-colors hover:bg-surface-soft hover:text-ink"
              >
                <X size={18} />
              </button>
            </div>

            <div className="flex min-h-0 flex-1 flex-col md:flex-row">
              {activeGroup.sections.length > 1 && (
                <ul className="shrink-0 overflow-y-auto border-b border-hairline-soft p-sm md:w-[200px] md:border-b-0 md:border-r">
                  {activeGroup.sections.map((section) => {
                    const isActive = section.id === activeSection.id
                    const count = section.options.filter((option) =>
                      getSelectedIds(draft, activeGroup.id).includes(option.id),
                    ).length
                    return (
                      <li key={section.id}>
                        <button
                          type="button"
                          onClick={() =>
                            setSectionByGroup((current) => ({
                              ...current,
                              [activeGroup.id]: section.id,
                            }))
                          }
                          className={`flex w-full items-center gap-xs rounded-md px-sm py-2.5 text-left text-sm font-semibold transition-colors ${
                            isActive ? 'bg-surface-soft text-ink' : 'text-body hover:bg-surface-soft'
                          }`}
                        >
                          <span className="flex-1 truncate">{section.label}</span>
                          {count > 0 && <span className="text-xs text-primary">{count}</span>}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}

              <div className="min-h-0 flex-1 overflow-y-auto p-lg">
                <div className="mb-base flex items-center gap-sm">
                  {SectionIcon && (
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-primary-tint text-primary">
                      <SectionIcon size={20} />
                    </span>
                  )}
                  <span className="text-lg font-bold text-ink">{activeSection.label}</span>
                </div>

                <div className="flex flex-wrap gap-xs">
                  {activeSection.options.length === 0 ? (
                    <p className="text-sm text-muted">{activeSection.emptyText}</p>
                  ) : activeSection.options.map((option) => {
                    const isSelected = getSelectedIds(draft, activeGroup.id).includes(option.id)
                    return (
                      <button
                        key={option.id}
                        type="button"
                        aria-pressed={isSelected}
                        onClick={() =>
                          setDraft((current) => toggleOption(current, activeGroup, option.id))
                        }
                        className={`h-10 rounded-md border px-base text-sm font-semibold transition-colors ${
                          isSelected
                            ? 'border-primary bg-primary-tint text-primary'
                            : 'border-transparent bg-surface-soft text-body hover:bg-surface-strong'
                        }`}
                      >
                        {option.label}
                      </button>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-sm border-t border-hairline-soft px-lg py-base">
          <ul className="flex flex-1 flex-wrap gap-xs">
            {selectedGroups.map((group) => (
              <li key={group.id}>
                <span className="inline-flex h-8 items-center gap-1.5 rounded-sm bg-primary-tint px-sm text-sm font-semibold text-primary">
                  {group.label} {summarizeGroup(group, draft)}
                  <button
                    type="button"
                    onClick={() => setDraft((current) => clearGroup(current, group.id))}
                    aria-label={FILTER_MODAL_TEXT.removeAriaLabel(group.label)}
                  >
                    <X size={14} />
                  </button>
                </span>
              </li>
            ))}
          </ul>

          <button
            type="button"
            onClick={() => setDraft(DEFAULT_FILTER_SELECTION)}
            className="flex items-center gap-xs px-sm text-sm font-semibold text-body transition-colors hover:text-ink"
          >
            <RotateCcw size={14} />
            {FILTER_MODAL_TEXT.reset}
          </button>
          <Button onClick={() => onApply(draft)}>{FILTER_MODAL_TEXT.submit}</Button>
        </div>
      </div>
    </div>
  )
}

function GroupTab({
  group,
  isActive,
  selectedCount,
  onSelect,
}: {
  group: FilterGroup
  isActive: boolean
  selectedCount: number
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`flex w-full items-center gap-xs rounded-md px-base py-2.5 text-left text-sm font-semibold transition-colors ${
        isActive ? 'bg-surface-soft text-ink' : 'text-body hover:bg-surface-soft'
      }`}
    >
      <span className="flex-1 truncate">{group.label}</span>
      {selectedCount > 0 && <span className="text-xs text-primary">{selectedCount}</span>}
    </button>
  )
}

export default FilterModal
