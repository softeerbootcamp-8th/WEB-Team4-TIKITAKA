import { useId } from 'react'
import type { SelectHTMLAttributes } from 'react'

interface SelectOption {
  value: string
  label: string
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  options: SelectOption[]
  placeholder?: string
  error?: string
}

function Select({
  label,
  options,
  placeholder,
  error,
  id,
  className = '',
  ...props
}: SelectProps) {
  const generatedId = useId()
  const selectId = id ?? generatedId

  return (
    <div className="flex flex-col gap-xs">
      {label && (
        <label htmlFor={selectId} className="text-sm font-semibold text-body">
          {label}
        </label>
      )}
      <select
        id={selectId}
        className={`h-12 rounded-md border border-hairline bg-canvas px-base text-base text-ink outline-none focus:border-2 focus:border-primary ${className}`}
        {...props}
      >
        {placeholder && (
          <option value="" disabled hidden>
            {placeholder}
          </option>
        )}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && <p className="text-sm text-down">{error}</p>}
    </div>
  )
}

export default Select
export type { SelectOption, SelectProps }
