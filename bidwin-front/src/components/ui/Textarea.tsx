import { useId } from 'react'
import type { TextareaHTMLAttributes } from 'react'

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  error?: string
}

function Textarea({
  label,
  error,
  id,
  className = '',
  ...props
}: TextareaProps) {
  const generatedId = useId()
  const textareaId = id ?? generatedId

  return (
    <div className="flex flex-col gap-xs">
      {label && (
        <label htmlFor={textareaId} className="text-sm font-semibold text-body">
          {label}
        </label>
      )}
      <textarea
        id={textareaId}
        className={`min-h-32 resize-none rounded-md border border-hairline px-base py-sm text-base text-ink outline-none focus:border-2 focus:border-primary ${className}`}
        {...props}
      />
      {error && <p className="text-sm text-down">{error}</p>}
    </div>
  )
}

export default Textarea
export type { TextareaProps }
