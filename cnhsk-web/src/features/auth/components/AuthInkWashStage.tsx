import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

import authBg002 from '../assets/auth-bg-002.webp'
import authBg003 from '../assets/auth-bg-003.webp'
import authBg004 from '../assets/auth-bg-004.webp'
import authBg005 from '../assets/auth-bg-005.webp'
import authBg006 from '../assets/auth-bg-006.webp'
import authBg079 from '../assets/auth-bg-079.webp'
import './authInkWash.css'

export type AuthInkWashVariant = '002' | '003' | '004' | '005' | '006' | '079'

const AUTH_BACKGROUND_MAP: Record<AuthInkWashVariant, string> = {
  '002': authBg002,
  '003': authBg003,
  '004': authBg004,
  '005': authBg005,
  '006': authBg006,
  '079': authBg079,
}

interface AuthInkWashStageProps {
  children: ReactNode
  compact?: boolean
  mainLabel?: string
  variant: AuthInkWashVariant
}

/**
 * Khung nền tranh thủy mặc đỏ và gấu trúc CNHSK đã duyệt cho 6 màn Auth.
 * Phủ toàn bộ vùng nội dung dưới Header và giữ nguyên tỷ lệ hình nền trên mọi độ phân giải.
 */
export function AuthInkWashStage({
  children,
  compact = false,
  mainLabel,
  variant,
}: AuthInkWashStageProps) {
  return (
    <main
      aria-label={mainLabel}
      className="auth-inkwash-stage relative flex min-h-[calc(100vh-4rem)] w-full min-w-0 items-center overflow-x-hidden px-4 py-6 sm:px-6 sm:py-8"
      style={{ backgroundImage: `url(${AUTH_BACKGROUND_MAP[variant]})` }}
    >
      <div
        className={cn(
          'auth-inkwash-card relative z-10 mx-auto p-5 sm:p-7',
          compact && 'auth-inkwash-card--compact',
        )}
      >
        {children}
      </div>
    </main>
  )
}
