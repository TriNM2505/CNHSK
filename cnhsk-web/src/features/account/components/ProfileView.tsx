import { UserRound } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'

import {
  Button,
  Card,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Select,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import type { ProfileFieldErrors, ProfileFormValues } from '../accountForms'
import { FormNotice, type FormNoticeValue } from './FormNotice'

export type ProfileViewState = 'loading' | 'error' | 'ready' | 'saving'

export interface ProfileViewProps {
  email?: string
  emailVerified?: boolean
  fieldErrors?: ProfileFieldErrors
  loadError?: { message: string; requestId?: string }
  notice?: FormNoticeValue
  onChange: (field: keyof ProfileFormValues, value: string) => void
  onRetry: () => void
  onSubmit: () => void
  state: ProfileViewState
  values: ProfileFormValues
}

const HSK_LEVELS = [1, 2, 3, 4, 5, 6, 7, 8, 9]

/** Ảnh đại diện từ URL; ảnh hỏng thì hiện chữ cái đầu của tên. */
function Avatar({ name, url }: { name: string; url: string }) {
  const [broken, setBroken] = useState(false)
  useEffect(() => setBroken(false), [url])
  const initial = name.trim().charAt(0).toUpperCase()

  if (url && !broken) {
    return (
      <img
        alt=""
        className="size-16 shrink-0 rounded-pill border border-line object-cover"
        onError={() => setBroken(true)}
        src={url}
      />
    )
  }
  return (
    <span
      aria-hidden="true"
      className="flex size-16 shrink-0 items-center justify-center rounded-pill bg-brand-50 text-h3 text-brand-700"
    >
      {initial || <UserRound className="size-7" />}
    </span>
  )
}

export function ProfileView({
  email,
  emailVerified,
  fieldErrors = {},
  loadError,
  notice,
  onChange,
  onRetry,
  onSubmit,
  state,
  values,
}: ProfileViewProps) {
  const saving = state === 'saving'
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <div className="mx-auto w-full max-w-reading">
      <PageHeader
        subtitle="Cập nhật tên hiển thị, ảnh đại diện và cấp HSK mục tiêu."
        title="Thông tin cá nhân"
      />

      {state === 'loading' ? <Skeleton variant="card" /> : null}

      {state === 'error' ? (
        <ErrorState
          message={loadError?.message ?? 'Không tải được hồ sơ. Vui lòng thử lại.'}
          onRetry={onRetry}
          requestId={loadError?.requestId}
        />
      ) : null}

      {state === 'ready' || state === 'saving' ? (
        <Card className="p-4 sm:p-6">
          <div className="mb-6 flex min-w-0 items-center gap-4">
            <Avatar name={values.displayName} url={values.avatarUrl.trim()} />
            <div className="min-w-0">
              <p className="break-words text-h3 text-ink">{values.displayName || 'Chưa có tên'}</p>
              <p className="break-all text-body-sm text-ink-secondary">{email}</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                {emailVerified ? (
                  <StatusBadge tone="success">Đã xác thực email</StatusBadge>
                ) : (
                  <>
                    <StatusBadge tone="warning">Chưa xác thực email</StatusBadge>
                    <Button size="sm" to="/verify-email" variant="ghost">
                      Xác thực ngay
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>

          {notice ? <FormNotice notice={notice} /> : null}

          <form noValidate onSubmit={handleSubmit}>
            <FormField
              error={fieldErrors.displayName}
              htmlFor="profile-display-name"
              label="Tên hiển thị"
              required
            >
              <Input
                autoComplete="name"
                disabled={saving}
                maxLength={101}
                name="displayName"
                onChange={(event) => onChange('displayName', event.target.value)}
                value={values.displayName}
              />
            </FormField>

            <FormField
              error={fieldErrors.avatarUrl}
              hint="Dán đường dẫn ảnh bắt đầu bằng https://. Để trống nếu không dùng ảnh."
              htmlFor="profile-avatar-url"
              label="Ảnh đại diện (đường dẫn)"
            >
              <Input
                disabled={saving}
                inputMode="url"
                maxLength={501}
                name="avatarUrl"
                onChange={(event) => onChange('avatarUrl', event.target.value)}
                placeholder="https://"
                type="url"
                value={values.avatarUrl}
              />
            </FormField>

            <FormField
              error={fieldErrors.hskLevel}
              htmlFor="profile-hsk-level"
              label="Cấp HSK mục tiêu"
            >
              <Select
                disabled={saving}
                name="hskLevel"
                onChange={(event) => onChange('hskLevel', event.target.value)}
                value={values.hskLevel}
              >
                <option value="">Chưa chọn</option>
                {HSK_LEVELS.map((level) => (
                  <option key={level} value={String(level)}>
                    HSK {level}
                  </option>
                ))}
              </Select>
            </FormField>

            <div className="flex flex-wrap items-center gap-3">
              <Button loading={saving} type="submit">
                {saving ? 'Đang lưu' : 'Lưu hồ sơ'}
              </Button>
              <Button to="/me/password" variant="outline">
                Đổi mật khẩu
              </Button>
            </div>
          </form>

          <p className="mt-6 text-caption text-ink-muted">
            Email đăng nhập không đổi được tại đây.
          </p>
        </Card>
      ) : null}
    </div>
  )
}
