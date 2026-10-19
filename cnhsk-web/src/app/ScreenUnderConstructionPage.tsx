import { CircleAlert } from 'lucide-react'
import { useLocation } from 'react-router-dom'

import type { AuthNavigationState } from '@/shared/auth'

type ScreenUnderConstructionPageProps = {
  scr: `SCR-${string}`
  showAuthNotice?: boolean
}

export function ScreenUnderConstructionPage({
  scr,
  showAuthNotice = false,
}: ScreenUnderConstructionPageProps) {
  const location = useLocation()
  const state = location.state as AuthNavigationState | null
  const authNotice = showAuthNotice ? state?.authNotice : undefined

  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas p-4 font-sans">
      <div className="w-full max-w-reading text-center">
        {authNotice?.reason === 'ACCOUNT_BANNED' ? (
          <section
            className="mb-6 rounded-md border border-danger bg-danger-bg p-4 text-left text-danger"
            role="alert"
          >
            <div className="flex items-start gap-3">
              <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              <div>
                <h2 className="text-label">Tài khoản của bạn đã bị cấm.</h2>
                {authNotice.banReason ? (
                  <p className="mt-1 text-body-sm">Lý do: {authNotice.banReason}</p>
                ) : null}
              </div>
            </div>
          </section>
        ) : null}
        <p className="text-label text-brand-600">CNHSK</p>
        <h1 className="mt-2 text-h1-sm text-ink sm:text-h1">Màn {scr} đang dựng</h1>
        <p className="mt-3 text-body text-ink-secondary">
          Nội dung màn hình sẽ được hoàn thiện ở task chuyên biệt.
        </p>
      </div>
    </main>
  )
}
