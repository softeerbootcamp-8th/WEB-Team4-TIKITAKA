import { useId } from 'react'
import type { InputHTMLAttributes } from 'react'

interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  /* 입력값 오른쪽에 붙는 단위 표시(원, 분 등). 표시만 하고 값에는 포함되지 않는다. */
  suffix?: string
}

function TextInput({
  label,
  error,
  suffix,
  id,
  className = '',
  ...props
}: TextInputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId

  return (
    <div className="flex flex-col gap-xs">
      {label && (
        <label htmlFor={inputId} className="text-sm font-semibold text-body">
          {label}
        </label>
      )}
      <div className="relative">
        <input
          id={inputId}
          className={`h-12 w-full rounded-md border border-hairline px-base text-base text-ink outline-none focus:border-2 focus:border-primary ${suffix ? 'pr-xxl' : ''} ${className}`}
          {...props}
        />
        {suffix && (
          <span className="pointer-events-none absolute right-base top-1/2 -translate-y-1/2 text-sm text-muted">
            {suffix}
          </span>
        )}
      </div>
      {error && <p className="text-sm text-down">{error}</p>}
    </div>
  )
}

export default TextInput
export type { TextInputProps }
