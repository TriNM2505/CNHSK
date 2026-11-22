import { Bell, ChevronDown, KeyRound, LogOut, UserRound, Wallet } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'

type AccountMenuProps = {
  inverse?: boolean
}

type UserArea = {
  label: string
  role: RoleCode
  to: string
}

const userAreas: readonly UserArea[] = [
  { label: 'Học tập', role: 'LEARNER', to: '/learn' },
  { label: 'Chấm bài viết', role: 'TEACHER', to: '/teacher/grading' },
  { label: 'Kiểm duyệt', role: 'MODERATOR', to: '/moderation/posts' },
  { label: 'Nhập dữ liệu', role: 'CONTENT_EDITOR', to: '/content/imports' },
  { label: 'Quản trị người dùng', role: 'ADMIN', to: '/admin/users' },
]

function getUserAreas(user: CurrentUser): readonly UserArea[] {
  return userAreas.filter(({ role }) => user.roles.includes(role))
}

export function AccountMenu({ inverse = false }: AccountMenuProps) {
  const { signOut, user } = useCurrentUser()
  const [signingOut, setSigningOut] = useState(false)
  const [signOutFailed, setSignOutFailed] = useState(false)

  if (!user) return null

  async function handleSignOut() {
    setSigningOut(true)
    setSignOutFailed(false)
    try {
      await signOut()
    } catch {
      setSignOutFailed(true)
    } finally {
      setSigningOut(false)
    }
  }

  const areas = getUserAreas(user)
  const hasLearnerArea = user.roles.includes('LEARNER')
  const itemClasses =
    'flex min-h-touch w-full items-center gap-2 rounded-sm px-3 py-2 text-left text-body-sm text-ink no-underline hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

  return (
    <details className="group relative">
      <summary
        className={cn(
          'flex min-h-touch cursor-pointer list-none items-center gap-2 rounded-sm px-3 text-label focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus [&::-webkit-details-marker]:hidden',
          inverse ? 'text-surface hover:bg-brand-700' : 'text-ink hover:bg-brand-50',
        )}
      >
        <span className="flex size-8 items-center justify-center rounded-full bg-brand-50 text-label text-brand-700">
          {user.displayName.trim().charAt(0).toLocaleUpperCase('vi') || 'C'}
        </span>
        <span className="hidden max-w-40 truncate sm:inline">{user.displayName}</span>
        <ChevronDown
          aria-hidden="true"
          className="size-4 transition-transform group-open:rotate-180"
        />
      </summary>

      <div className="absolute right-0 z-30 mt-2 w-64 rounded-md border border-line bg-surface p-2 shadow-lg">
        <p className="truncate px-3 py-2 text-label text-ink">{user.displayName}</p>

        {areas.length >= 2 || user.roles.includes('TEACHER') ? (
          <div className="border-y border-line py-2">
            <p className="px-3 pb-1 text-caption text-ink-muted">Khu vực của bạn</p>
            {areas.map((area) => (
              <Link className={itemClasses} key={area.role} to={area.to}>
                {area.label}
              </Link>
            ))}
          </div>
        ) : null}

        <div className="pt-2">
          {hasLearnerArea ? (
            <Link className={itemClasses} to="/me/profile">
              <UserRound aria-hidden="true" className="size-4" />
              Hồ sơ
            </Link>
          ) : null}
          {hasLearnerArea ? (
            <Link className={itemClasses} to="/me/reminders">
              <Bell aria-hidden="true" className="size-4" />
              Nhắc lịch học
            </Link>
          ) : null}
          {hasLearnerArea ? (
            <Link className={itemClasses} to="/account/credits">
              <Wallet aria-hidden="true" className="size-4" />
              Gói dịch vụ và điểm
            </Link>
          ) : null}
          <Link className={itemClasses} to="/me/password">
            <KeyRound aria-hidden="true" className="size-4" />
            Đổi mật khẩu
          </Link>
          <button
            className={cn(itemClasses, signingOut && 'opacity-60')}
            disabled={signingOut}
            onClick={() => void handleSignOut()}
            type="button"
          >
            <LogOut aria-hidden="true" className="size-4" />
            Đăng xuất
          </button>
          {signOutFailed ? (
            <p className="px-3 py-2 text-caption text-danger" role="alert">
              Chưa đăng xuất được, bạn vẫn đang đăng nhập. Vui lòng kiểm tra mạng rồi thử lại.
            </p>
          ) : null}
        </div>
      </div>
    </details>
  )
}
