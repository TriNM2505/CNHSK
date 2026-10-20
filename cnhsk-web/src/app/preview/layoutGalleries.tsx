import { useState, type ReactNode } from 'react'

import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { AdminLayout, LearnerLayout, PublicLayout } from '@/shared/layout'
import {
  previewAdministrator,
  previewContentEditor,
  previewLearner,
  previewMultiAreaUser,
  previewUnverifiedLearner,
} from '@/shared/layout/Layout.preview'
import { Button, Card } from '@/shared/ui'

function PreviewAuth({ children, user }: { children: ReactNode; user: CurrentUser | null }) {
  const value: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: user ? 'authenticated' : 'guest',
    user,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function LayoutSample({ children, title }: { children?: ReactNode; title: string }) {
  return (
    <main className="mx-auto w-full max-w-content p-4 sm:p-6">
      <Card>
        <h1 className="text-h1-sm text-ink sm:text-h1">{title}</h1>
        {children ? <div className="mt-4 flex flex-wrap gap-2">{children}</div> : null}
      </Card>
    </main>
  )
}

export function PublicLayoutGallery() {
  const [user, setUser] = useState<CurrentUser | null>(null)

  return (
    <PreviewAuth user={user}>
      <PublicLayout>
        <LayoutSample title="Bố cục công khai">
          <Button onClick={() => setUser(null)} size="sm" variant={!user ? 'primary' : 'outline'}>
            Khách
          </Button>
          <Button
            onClick={() => setUser(previewMultiAreaUser)}
            size="sm"
            variant={user ? 'primary' : 'outline'}
          >
            Đã đăng nhập
          </Button>
        </LayoutSample>
      </PublicLayout>
    </PreviewAuth>
  )
}

export function LearnerLayoutGallery() {
  const [verified, setVerified] = useState(false)
  const user = verified ? previewLearner : previewUnverifiedLearner

  return (
    <PreviewAuth user={user}>
      <LearnerLayout gameOrigin="http://localhost:5174">
        <LayoutSample title="Bố cục học tập">
          <Button
            onClick={() => setVerified(false)}
            size="sm"
            variant={!verified ? 'primary' : 'outline'}
          >
            Chưa xác minh
          </Button>
          <Button
            onClick={() => setVerified(true)}
            size="sm"
            variant={verified ? 'primary' : 'outline'}
          >
            Đã xác minh
          </Button>
        </LayoutSample>
      </LearnerLayout>
    </PreviewAuth>
  )
}

type AdminVariant = 'editor' | 'admin' | 'multi'

export function AdminLayoutGallery() {
  const [variant, setVariant] = useState<AdminVariant>('multi')
  const users: Record<AdminVariant, CurrentUser> = {
    admin: previewAdministrator,
    editor: previewContentEditor,
    multi: previewMultiAreaUser,
  }

  return (
    <PreviewAuth user={users[variant]}>
      <AdminLayout>
        <LayoutSample title="Bố cục quản trị">
          {(
            [
              ['editor', 'Biên tập viên'],
              ['admin', 'Quản trị viên'],
              ['multi', 'Nhiều khu vực'],
            ] as const
          ).map(([value, label]) => (
            <Button
              key={value}
              onClick={() => setVariant(value)}
              size="sm"
              variant={variant === value ? 'primary' : 'outline'}
            >
              {label}
            </Button>
          ))}
        </LayoutSample>
      </AdminLayout>
    </PreviewAuth>
  )
}
