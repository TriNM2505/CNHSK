import type { HTMLAttributes } from 'react'

import { cn } from '@/shared/lib/cn'

export interface HanziTextProps extends HTMLAttributes<HTMLSpanElement> {
  size?: 'lg' | 'md' | 'sm'
}

const sizeClasses: Record<NonNullable<HanziTextProps['size']>, string> = {
  lg: 'text-hanzi-lg',
  md: 'text-hanzi-md',
  sm: 'text-h2',
}

export function HanziText({ className, size = 'md', ...props }: HanziTextProps) {
  return (
    <span
      {...props}
      className={cn('font-hanzi text-ink', sizeClasses[size], className)}
      lang="zh-CN"
    />
  )
}
