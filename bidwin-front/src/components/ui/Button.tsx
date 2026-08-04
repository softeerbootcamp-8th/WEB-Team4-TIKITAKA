import type { ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'tertiary'
type ButtonSize = 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

/*
 * 공통 인터랙션: 눌림(active) 축소, 키보드 포커스 링, 색·그림자 전환.
 * 마우스와 키보드 모두에서 버튼이 눌리는 감각이 같게 보이도록 여기서 한 번만 정의한다.
 */
const INTERACTION_CLASSES =
  'transition-[background-color,box-shadow,transform] duration-150 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-canvas disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100'

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-on-primary hover:bg-primary-active hover:shadow-soft disabled:bg-primary-disabled disabled:shadow-none',
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
      className={`inline-flex items-center justify-center gap-2 rounded-pill font-semibold ${INTERACTION_CLASSES} ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${className}`}
      {...props}
    />
  )
}

export default Button
export type { ButtonProps, ButtonSize, ButtonVariant }
