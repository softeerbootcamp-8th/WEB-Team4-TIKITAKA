import { useId } from 'react'
import type { InputHTMLAttributes } from 'react'

interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
}

function TextInput({
  label,
  error,
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
      <input
        id={inputId}
        className={`h-12 rounded-md border border-hairline px-base text-base text-ink outline-none focus:border-2 focus:border-primary ${className}`}
        {...props}
      />
      {error && <p className="text-sm text-down">{error}</p>}
    </div>
  )
}

export default TextInput
export type { TextInputProps }
