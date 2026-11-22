import type { ReactNode } from 'react'

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
  mainLabel?: string
  variant: AuthInkWashVariant
}

/**
 * Nền tranh thủy mặc và gấu trúc của các màn Auth, với một tấm giấy chứa nội dung.
 * Tranh luôn cao đúng phần màn hình dưới header nên không nhảy khi nội dung dài ngắn khác nhau.
 */
export function AuthInkWashStage({ children, mainLabel, variant }: AuthInkWashStageProps) {
  return (
    <main aria-label={mainLabel} className="auth-inkwash-stage">
      <div
        aria-hidden="true"
        className="auth-inkwash-painting"
        style={{ backgroundImage: `url(${AUTH_BACKGROUND_MAP[variant]})` }}
      />
      <div className="auth-inkwash-content">
        <div className="auth-inkwash-card">{children}</div>
      </div>
    </main>
  )
}
