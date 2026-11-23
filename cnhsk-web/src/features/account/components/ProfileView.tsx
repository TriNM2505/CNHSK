import { Camera, ChevronRight, Crown, KeyRound, Pencil, Star, Trophy, UserRound } from 'lucide-react'
import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'
import { Button, ErrorState, Skeleton, SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import type { Profile } from '../types'
import { FormNotice, type FormNoticeValue } from './FormNotice'

export type ProfileViewProps = {
  profile?: Profile
  state: 'loading' | 'error' | 'ready'
  loadError?: { message: string; requestId?: string }
  onRetry: () => void
  /** What is being saved right now, so only that control waits. */
  busy?: 'avatar' | 'name' | 'level'
  /** Avatar upload progress from 0 to 1. */
  uploadProgress?: number
  notice?: FormNoticeValue
  onAvatarFile: (file: File) => void
  onRename: (name: string) => Promise<boolean>
  onLevelChange: (level: number | null) => void
}

const DATE_TIME = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
})
const DATE = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric' })

/** Supported levels plus a saved level outside them, so the select never silently shows another value. */
function levelOptions(saved?: number | null): number[] {
  const levels: number[] = [...SUPPORTED_HSK_LEVELS]
  if (saved && !levels.includes(saved)) levels.push(saved)
  return levels.sort((a, b) => a - b)
}

/** "22.08.2022 | 17:43", the way Hanzii writes the account creation time. */
function stamp(value?: string): string {
  if (!value) return '—'
  const parts = DATE_TIME.formatToParts(new Date(value))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('day')}.${part('month')}.${part('year')} | ${part('hour')}:${part('minute')}`
}

function isPremium(profile: Profile): boolean {
  return profile.planCode === 'PREMIUM' && Boolean(profile.planExpiresAt) && new Date(profile.planExpiresAt!).getTime() > Date.now()
}

/** Badges need no storage: they follow from the plan and how long the account has existed. */
function badge(profile: Profile): string {
  if (isPremium(profile)) return 'Học viên Premium'
  const age = profile.createdAt ? Date.now() - new Date(profile.createdAt).getTime() : 0
  return age < 30 * 24 * 60 * 60 * 1000 ? 'Thành viên mới' : 'Thành viên'
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="min-w-0 space-y-2">
      <p className="text-label text-ink">{label}</p>
      <div className="flex min-h-14 min-w-0 items-center gap-2 overflow-hidden rounded-lg border border-line bg-surface px-4 text-body text-ink-secondary">
        {children}
      </div>
    </div>
  )
}

function Avatar({ profile }: { profile: Profile }) {
  const [broken, setBroken] = useState(false)
  useEffect(() => setBroken(false), [profile.avatarUrl])
  if (profile.avatarUrl && !broken) {
    return <img alt="" className="size-full object-cover" onError={() => setBroken(true)} src={profile.avatarUrl} />
  }
  const initial = profile.displayName.trim().charAt(0).toLocaleUpperCase('vi')
  return (
    <span className="flex size-full items-center justify-center bg-brand-50 text-display-sm text-brand-700">
      {initial || <UserRound aria-hidden="true" className="size-10" />}
    </span>
  )
}

/** SCR-007: profile card on the left, read-only account facts on the right. */
export function ProfileView({
  busy,
  loadError,
  notice,
  onAvatarFile,
  onLevelChange,
  onRename,
  onRetry,
  profile,
  state,
  uploadProgress,
}: ProfileViewProps) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')

  if (state === 'loading') return <div className="mx-auto max-w-content px-4 py-6"><Skeleton variant="card" /></div>
  if (state === 'error' || !profile) {
    return (
      <div className="mx-auto max-w-content px-4 py-6">
        <ErrorState message={loadError?.message ?? 'Không tải được hồ sơ.'} onRetry={onRetry} requestId={loadError?.requestId} />
      </div>
    )
  }

  async function commitName() {
    if (await onRename(draft)) setEditing(false)
  }

  function nameKeys(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') void commitName()
    if (event.key === 'Escape') setEditing(false)
  }

  const premium = isPremium(profile)
  return (
    <main className="mx-auto grid w-full min-w-0 max-w-content items-start gap-5 px-4 py-6 pb-16 sm:px-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <section aria-label="Hồ sơ" className="space-y-4 rounded-lg bg-surface p-5 shadow-sm sm:p-6">
        {notice ? <FormNotice notice={notice} /> : null}
        <div className="flex items-center gap-5">
          <div className="relative shrink-0">
            {premium ? <Crown aria-hidden="true" className="absolute -left-1 -top-2 z-10 size-6 -rotate-12 fill-accent-500 text-accent-700" /> : null}
            <span className="block size-24 overflow-hidden rounded-full ring-2 ring-accent-500 ring-offset-2 ring-offset-surface">
              <Avatar profile={profile} />
            </span>
            <button
              aria-label="Đổi ảnh đại diện"
              className="absolute -bottom-1 -right-1 flex size-9 items-center justify-center rounded-full border border-line bg-surface text-ink shadow-sm hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:opacity-60"
              disabled={busy === 'avatar'}
              onClick={() => fileInput.current?.click()}
              type="button"
            >
              <Camera aria-hidden="true" className="size-4" />
            </button>
            <input
              accept="image/*"
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (file) onAvatarFile(file)
              }}
              ref={fileInput}
              tabIndex={-1}
              type="file"
            />
            {busy === 'avatar' ? (
              <span className="absolute inset-0 flex items-center justify-center rounded-full bg-ink/50 text-label text-surface" role="status">
                {Math.round((uploadProgress ?? 0) * 100)}%
              </span>
            ) : null}
          </div>

          <div className="min-w-0 flex-1 space-y-2">
            {editing ? (
              <input
                aria-label="Tên hiển thị"
                autoFocus
                className="w-full rounded-md border border-brand-600 px-3 py-1.5 text-h3 text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                disabled={busy === 'name'}
                maxLength={100}
                onBlur={() => void commitName()}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={nameKeys}
                value={draft}
              />
            ) : (
              <div className="flex min-w-0 items-center gap-2">
                <h1 className="truncate text-h2 text-ink">{profile.displayName}</h1>
                <Star aria-hidden="true" className="size-5 shrink-0 fill-accent-500 text-accent-700" />
                <button
                  aria-label="Sửa tên hiển thị"
                  className="flex size-8 shrink-0 items-center justify-center rounded-full text-ink hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                  onClick={() => {
                    setDraft(profile.displayName)
                    setEditing(true)
                  }}
                  type="button"
                >
                  <Pencil aria-hidden="true" className="size-4" />
                </button>
              </div>
            )}
            {profile.hskLevel ? (
              <span className="inline-flex rounded-pill border border-ink px-3 py-0.5 text-label text-ink">HSK {profile.hskLevel}</span>
            ) : null}
          </div>
        </div>

        {!profile.emailVerified ? (
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-warning-bg px-3 py-2 text-body-sm text-accent-700">
            Email chưa được xác thực.
            <Button size="sm" to="/verify-email" variant="outline">Xác thực ngay</Button>
          </div>
        ) : null}

        <div className="divide-y divide-line border-t border-line">
          <div className="flex flex-wrap items-center justify-between gap-3 py-4">
            <span className="text-body text-ink-secondary">Trình độ</span>
            <div className="flex items-center gap-2">
              <Link
                className="inline-flex min-h-10 items-center gap-1.5 rounded-md border border-line px-3 text-body-sm text-ink no-underline hover:bg-canvas"
                to="/learn/exams"
              >
                <Trophy aria-hidden="true" className="size-4 fill-accent-500 text-accent-700" />
                Kiểm tra
              </Link>
              <select
                aria-label="Cấp HSK mục tiêu"
                className="min-h-10 rounded-md border border-line bg-surface px-3 text-body-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                disabled={busy === 'level'}
                onChange={(event) => onLevelChange(event.target.value ? Number(event.target.value) : null)}
                value={profile.hskLevel ?? ''}
              >
                <option value="">Chưa chọn</option>
                {levelOptions(profile.hskLevel).map((level) => (
                  <option key={level} value={level}>
                    HSK {level}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex items-center justify-between gap-3 py-4">
            <span className="text-body text-ink-secondary">Mật khẩu</span>
            <Link
              className="inline-flex min-h-10 items-center gap-1.5 rounded-md border border-line px-3 text-body-sm text-ink no-underline hover:bg-canvas"
              to="/me/password"
            >
              <KeyRound aria-hidden="true" className="size-4" />
              Đổi mật khẩu
            </Link>
          </div>
        </div>
      </section>

      <section aria-label="Thông tin tài khoản" className="grid gap-4 rounded-lg bg-surface p-5 shadow-sm sm:grid-cols-2 sm:p-6">
        <div className="sm:col-span-2">
          <Field label="Email">
            <span className="truncate">{profile.email}</span>
          </Field>
        </div>
        <div className="space-y-2 sm:col-span-2">
          <p className="text-label text-ink">Phiên bản</p>
          <Link
            className="flex min-h-14 items-stretch overflow-hidden rounded-lg border border-line text-body no-underline hover:border-brand-600"
            to="/account/credits"
          >
            <span className={cn('flex items-center px-4 font-semibold', premium ? 'bg-warning-bg text-accent-700' : 'bg-canvas text-ink-secondary')}>
              {premium ? 'VIP' : 'FREE'}
            </span>
            <span className="flex flex-1 items-center px-4 text-ink-secondary">
              {premium ? `Cao cấp đến ${DATE.format(new Date(profile.planExpiresAt!))}` : 'Miễn phí'}
            </span>
            <ChevronRight aria-hidden="true" className="mr-3 size-5 self-center text-ink-muted" />
          </Link>
        </div>
        <Field label="ID">{profile.id ?? '—'}</Field>
        <Field label="Huy hiệu">
          <Star aria-hidden="true" className="size-5 shrink-0 fill-accent-500 text-accent-700" />
          {badge(profile)}
        </Field>
        <Field label="Đăng nhập với">email</Field>
        <Field label="Số thiết bị">{profile.deviceCount ?? 1}</Field>
        <div className="sm:col-span-2">
          <Field label="Tạo lúc">{stamp(profile.createdAt)}</Field>
        </div>
      </section>
    </main>
  )
}
