import type { HTMLAttributes } from 'react'

type CardProps = HTMLAttributes<HTMLDivElement>

function Card({ className = '', ...props }: CardProps) {
  return (
    <div
      className={`rounded-xl border border-hairline-soft bg-canvas p-xl ${className}`}
      {...props}
    />
  )
}

export default Card
export type { CardProps }
