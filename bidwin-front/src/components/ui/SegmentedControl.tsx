interface SegmentedOption<TValue extends string> {
  value: TValue
  label: string
}

interface SegmentedControlProps<TValue extends string> {
  label?: string
  options: readonly SegmentedOption<TValue>[]
  value: TValue
  onChange: (value: TValue) => void
}

function SegmentedControl<TValue extends string>({
  label,
  options,
  value,
  onChange,
}: SegmentedControlProps<TValue>) {
  return (
    <div className="flex flex-col gap-xs">
      {label && <span className="text-sm font-semibold text-body">{label}</span>}
      <div className="flex gap-xs">
        {options.map((option) => {
          const isSelected = option.value === value
          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={isSelected}
              onClick={() => onChange(option.value)}
              className={`h-11 flex-1 rounded-pill text-sm font-semibold transition-colors ${
                isSelected
                  ? 'bg-primary-tint text-primary'
                  : 'bg-surface-strong text-body hover:bg-hairline'
              }`}
            >
              {option.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export default SegmentedControl
export type { SegmentedControlProps, SegmentedOption }
