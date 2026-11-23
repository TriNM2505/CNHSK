import { Bell, BookOpen, ChevronDown, Crown, Database, KeyRound, LogOut, PenLine, ShieldCheck, UsersRound, Wallet } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'

type AccountMenuProps = {
  inverse?: boolean
  /** Smallest screen that shows the name beside the avatar; a crowded header waits for `xl`. */
  nameFrom?: 'sm' | 'xl'
}

type UserArea = {
  icon: ComponentType<SVGProps<SVGSVGElement>>
  label: string
  role: RoleCode
  to: string
}

const userAreas: readonly UserArea[] = [
  { icon: BookOpen, label: 'Học tập', role: 'LEARNER', to: '/learn' },
  { icon: PenLine, label: 'Chấm bài viết', role: 'TEACHER', to: '/teacher/grading' },
  { icon: ShieldCheck, label: 'Kiểm duyệt', role: 'MODERATOR', to: '/moderation/posts' },
  { icon: Database, label: 'Nhập dữ liệu', role: 'CONTENT_EDITOR', to: '/content/imports' },
  { icon: UsersRound, label: 'Quản trị người dùng', role: 'ADMIN', to: '/admin/users' },
]

const ROLE_BADGES: readonly [RoleCode, string][] = [
  ['ADMIN', 'Quản trị viên'],
  ['MODERATOR', 'Kiểm duyệt viên'],
  ['CONTENT_EDITOR', 'Biên tập viên'],
  ['TEACHER', 'Giáo viên'],
  ['LEARNER', 'Học viên'],
]

/** The highest role, shown as the badge on top of the card. */
function roleBadge(user: CurrentUser): string {
  return ROLE_BADGES.find(([role]) => user.roles.includes(role))?.[1] ?? 'Thành viên'
}

function Avatar({ className, user }: { className?: string; user: CurrentUser }) {
  return user.avatarUrl ? (
    <img alt="" className={cn('shrink-0 rounded-full object-cover', className)} src={user.avatarUrl} />
  ) : (
    <span className={cn('flex shrink-0 items-center justify-center rounded-full bg-brand-50 font-semibold text-brand-700', className)}>
      {user.displayName.trim().charAt(0).toLocaleUpperCase('vi') || 'C'}
    </span>
  )
}

function getUserAreas(user: CurrentUser): readonly UserArea[] {
  return userAreas.filter(({ role }) => user.roles.includes(role))
}

export function AccountMenu({ inverse = false, nameFrom = 'sm' }: AccountMenuProps) {
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
    'flex min-h-touch w-full items-center gap-3 rounded-md px-3 py-2 text-left text-body text-ink no-underline hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'
  const iconClasses = 'size-5 shrink-0 text-ink-secondary'
  const showAreas = areas.length >= 2 || user.roles.includes('TEACHER')

  return (
    <details className="group relative">
      <summary
        className={cn(
          'flex min-h-touch cursor-pointer list-none items-center gap-2 rounded-sm px-3 text-label focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus [&::-webkit-details-marker]:hidden',
          inverse ? 'text-surface hover:bg-brand-700' : 'text-ink hover:bg-brand-50',
        )}
      >
        <Avatar className="size-8 text-label" user={user} />

        <span
          className={cn('hidden max-w-40 truncate', nameFrom === 'xl' ? 'xl:inline' : 'sm:inline')}
        >
          {user.displayName}
        </span>
        <ChevronDown
          aria-hidden="true"
          className="size-4 transition-transform group-open:rotate-180"
        />
      </summary>

      <div className="absolute right-0 z-30 mt-2 w-[min(20rem,calc(100vw-2rem))] overflow-hidden rounded-lg border border-line bg-surface shadow-lg">
        <div className="space-y-3 border-b border-line p-4">
          <span className="inline-flex items-center gap-1.5 rounded-pill border border-accent-500 bg-warning-bg px-3 py-1 text-caption font-semibold text-accent-700">
            <Crown aria-hidden="true" className="size-3.5 fill-accent-500 text-accent-500" />
            {roleBadge(user)}
          </span>
          <div className="flex items-center gap-3">
            <Avatar className="size-12 text-h3" user={user} />
            <div className="min-w-0">
              <p className="truncate text-h3 text-ink">{user.displayName}</p>
              <p className="truncate text-body-sm text-ink-secondary">{user.email}</p>
            </div>
          </div>
          {hasLearnerArea ? (
            <div className="grid grid-cols-2 gap-2">
              <Link
                className="flex min-h-touch items-center justify-center rounded-pill bg-canvas px-3 text-label text-ink no-underline hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                to="/me/profile"
              >
                Xem hồ sơ
              </Link>
              <Link
                className="flex min-h-touch items-center justify-center rounded-pill bg-brand-800 px-3 text-label text-surface no-underline hover:bg-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                to="/account/credits"
              >
                Nâng cấp
              </Link>
            </div>
          ) : null}
        </div>

        <nav aria-label="Tài khoản" className="p-2">
          {showAreas ? <p className="px-3 pb-1 pt-1 text-caption text-ink-muted">Khu vực của bạn</p> : null}
          {showAreas
            ? areas.map((area) => (
                <Link className={itemClasses} key={area.role} to={area.to}>
                  <area.icon aria-hidden="true" className={iconClasses} />
                  {area.label}
                </Link>
              ))
            : null}
          {showAreas ? <hr className="my-1 border-line" /> : null}
          {hasLearnerArea ? (
            <Link className={itemClasses} to="/me/reminders">
              <Bell aria-hidden="true" className={iconClasses} />
              Nhắc lịch học
            </Link>
          ) : null}
          {hasLearnerArea ? (
            <Link className={itemClasses} to="/account/credits">
              <Wallet aria-hidden="true" className={iconClasses} />
              Gói dịch vụ và điểm
            </Link>
          ) : null}
          <Link className={itemClasses} to="/me/password">
            <KeyRound aria-hidden="true" className={iconClasses} />
            Đổi mật khẩu
          </Link>
          <button
            className={cn(itemClasses, 'text-danger hover:bg-danger-bg', signingOut && 'opacity-60')}
            disabled={signingOut}
            onClick={() => void handleSignOut()}
            type="button"
          >
            <LogOut aria-hidden="true" className="size-5 shrink-0 text-danger" />
            Đăng xuất
          </button>
          {signOutFailed ? (
            <p className="px-3 py-2 text-caption text-danger" role="alert">
              Chưa đăng xuất được, bạn vẫn đang đăng nhập. Vui lòng kiểm tra mạng rồi thử lại.
            </p>
          ) : null}
        </nav>
      </div>
    </details>
  )
}
