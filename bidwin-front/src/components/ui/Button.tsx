import type { ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'tertiary'
type ButtonSize = 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-on-primary hover:bg-primary-active disabled:bg-primary-disabled',
  secondary: 'bg-surface-strong text-ink hover:bg-hairline',
  tertiary: 'bg-transparent text-primary hover:underline',
}

const SIZE_CLASSES: Record<ButtonSize, string> = {
  md: 'h-11 px-lg text-base',
  lg: 'h-14 px-xl text-base',
}

function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  className = '',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center gap-2 rounded-pill font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${className}`}
      {...props}
    />
  )
}

export default Button
export type { ButtonProps, ButtonSize, ButtonVariant }
