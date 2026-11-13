import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { AppShell, BrandLink } from '@/shared/layout/AppShell'
import { Button } from '@/shared/ui'
import { useCurrentUser } from '@/shared/auth'

type PublicLayoutProps = {
  children: ReactNode
}

export function PublicLayout({ children }: PublicLayoutProps) {
  const { user } = useCurrentUser()

  if (user) return <AppShell>{children}</AppShell>

  return (
    <div className="flex min-h-screen min-w-0 flex-col bg-canvas">
      <header className="sticky top-0 z-20 border-b border-line bg-surface px-4">
        <div className="mx-auto flex min-h-16 max-w-content flex-wrap items-center justify-between gap-3 py-2">
          <BrandLink />
          <nav aria-label="Tài liệu công khai" className="min-w-0 text-body-sm">
            <Link to="/references/media" className="inline-flex min-h-touch items-center">
              Tài liệu tham khảo
            </Link>
          </nav>
          <nav aria-label="Tài khoản" className="flex items-center gap-2">
            <Button size="sm" to="/login" variant="ghost">
              Đăng nhập
            </Button>
            <Button size="sm" to="/register">
              Đăng ký
            </Button>
          </nav>
        </div>
      </header>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}
