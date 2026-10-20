import { useEffect, useState } from 'react'

import { cn } from '@/shared/lib/cn'

export interface SkeletonProps {
  className?: string
  variant?: 'text' | 'card' | 'table'
}

const shapeClasses = 'rounded-sm bg-line opacity-60 motion-safe:animate-pulse'

export function Skeleton({ className, variant = 'text' }: SkeletonProps) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setVisible(true), 300)

    return () => window.clearTimeout(timeoutId)
  }, [])

  if (!visible) {
    return null
  }

  return (
    <div aria-label="Đang tải" className={className} role="status">
      <span className="sr-only">Đang tải</span>
      {variant === 'text' ? (
        <div aria-hidden="true" className={cn(shapeClasses, 'h-4 w-full')} />
      ) : null}
      {variant === 'card' ? (
        <div aria-hidden="true" className="space-y-3 rounded-md border border-line bg-surface p-4">
          <div className={cn(shapeClasses, 'h-6 w-2/5')} />
          <div className={cn(shapeClasses, 'h-4 w-full')} />
          <div className={cn(shapeClasses, 'h-4 w-4/5')} />
        </div>
      ) : null}
      {variant === 'table' ? (
        <div aria-hidden="true" className="space-y-3 rounded-md border border-line bg-surface p-4">
          <div className={cn(shapeClasses, 'h-6 w-1/3')} />
          <div className={cn(shapeClasses, 'h-12 w-full')} />
          <div className={cn(shapeClasses, 'h-12 w-full')} />
          <div className={cn(shapeClasses, 'h-12 w-full')} />
        </div>
      ) : null}
    </div>
  )
}
