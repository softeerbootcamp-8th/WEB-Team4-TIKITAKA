import { Plus, RotateCcw } from 'lucide-react'
import { FILTER_TEXT } from '../constants'
import { FILTER_GROUPS, countSelectedOptions, summarizeGroup } from '../filters'
import type { FilterSelection } from '../filters'

/*
 * 좌측 필터 패널. 그리는 내용은 전부 filters.ts의 FILTER_GROUPS에서 온다.
 * 그룹이 하나도 없으면 안내 문구만 두고, 항목이 추가되는 순간 행과 + 버튼이 생긴다.
 */
function FilterPanel({
  selection,
  isEnabled,
  onToggleEnabled,
  onOpenGroup,
  onReset,
}: {
  selection: FilterSelection
  isEnabled: boolean
  onToggleEnabled: (next: boolean) => void
  onOpenGroup: (groupId: string) => void
  onReset: () => void
}) {
  const hasGroups = FILTER_GROUPS.length > 0
  const selectedCount = countSelectedOptions(selection)

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-center justify-between pb-base">
        <h2 className="text-lg font-bold text-ink">
          {FILTER_TEXT.panelTitle}{' '}
          <span className={isEnabled ? 'text-primary' : 'text-muted-soft'}>
            {isEnabled ? FILTER_TEXT.switchOn : FILTER_TEXT.switchOff}
          </span>
        </h2>
        <button
          type="button"
          role="switch"
          aria-checked={isEnabled}
          aria-label={FILTER_TEXT.switchLabel}
          onClick={() => onToggleEnabled(!isEnabled)}
          className={`relative h-7 w-12 shrink-0 rounded-pill transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
            isEnabled ? 'bg-primary' : 'bg-hairline'
          }`}
        >
          <span
            className={`absolute top-1 h-5 w-5 rounded-full bg-canvas shadow-soft transition-[left] duration-150 ${
              isEnabled ? 'left-6' : 'left-1'
            }`}
          />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto border-t border-hairline">
        {hasGroups ? (
          <ul className={isEnabled ? '' : 'pointer-events-none opacity-50'}>
            {FILTER_GROUPS.map((group) => {
              const summary = summarizeGroup(group, selection)
              return (
                <li key={group.id} className="border-b border-hairline">
                  <button
                    type="button"
                    onClick={() => onOpenGroup(group.id)}
                    aria-label={FILTER_TEXT.addAriaLabel(group.label)}
                    className="group flex w-full items-center gap-xs py-base text-left"
                  >
                    <span className="text-base font-semibold text-ink">{group.label}</span>
                    {summary && (
                      <span className="rounded-xs bg-primary-tint px-1.5 py-0.5 text-xs font-semibold text-primary">
                        {summary}
                      </span>
                    )}
                    <Plus
                      size={18}
                      className="ml-auto shrink-0 text-muted group-hover:text-ink"
                    />
                  </button>
                </li>
              )
            })}
          </ul>
        ) : (
          <div className="py-xl">
            <p className="text-sm font-semibold text-body">{FILTER_TEXT.emptyPanel}</p>
            <p className="mt-xs text-xs leading-relaxed text-muted">
              {FILTER_TEXT.emptyPanelHint}
            </p>
          </div>
        )}
      </div>

      <div className="shrink-0 border-t border-hairline pt-base">
        <button
          type="button"
          onClick={onReset}
          disabled={selectedCount === 0}
          className="flex items-center gap-xs text-sm font-semibold text-body transition-colors hover:text-ink disabled:cursor-not-allowed disabled:text-muted-soft disabled:hover:text-muted-soft"
        >
          <RotateCcw size={14} />
          {FILTER_TEXT.reset}
        </button>
      </div>
    </div>
  )
}

export default FilterPanel
