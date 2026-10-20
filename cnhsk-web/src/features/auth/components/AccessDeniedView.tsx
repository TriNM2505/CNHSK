import { Construction, LockKeyhole } from 'lucide-react'

import { Button, Skeleton } from '@/shared/ui'

export type AccessDeniedViewState = 'loading' | 'empty' | 'error' | 'ready'
export type AccessDeniedMode = 'guest' | 'forbidden' | 'under-construction'

export type AccessDeniedViewProps = {
  areaLabels?: readonly string[]
  mode: AccessDeniedMode
  onRetry: () => void
  returnLabel?: string
  returnTo?: string
  state: AccessDeniedViewState
}

function GuestActions() {
  return (
    <div className="flex flex-col justify-center gap-3 sm:flex-row">
      <Button to="/login">Đăng nhập</Button>
      <Button to="/" variant="outline">
        Về trang chủ
      </Button>
    </div>
  )
}

function joinAreaLabels(labels: readonly string[]): string {
  if (labels.length < 2) return labels[0] ?? 'của bạn'
  return `${labels.slice(0, -1).join(', ')} và ${labels.at(-1)}`
}

export function AccessDeniedView({
  areaLabels = [],
  mode,
  onRetry,
  returnLabel = 'Về khu vực được phép',
  returnTo = '/',
  state,
}: AccessDeniedViewProps) {
  if (state === 'loading') {
    return (
      <main className="mx-auto min-h-[calc(100vh-4rem)] max-w-reading px-4 py-12 sm:py-16">
        <Skeleton className="min-h-72" variant="card" />
      </main>
    )
  }

  const isUnderConstruction = mode === 'under-construction'
  const isGuest = mode === 'guest'
  const title = isUnderConstruction
    ? `Khu vực ${joinAreaLabels(areaLabels)} đang được xây dựng`
    : 'Không có quyền truy cập'

  return (
    <main className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-reading items-center px-4 py-12 sm:py-16">
      <section
        aria-labelledby="access-denied-title"
        className="w-full rounded-lg border border-line bg-surface px-5 py-10 text-center shadow-md sm:px-10 sm:py-12"
      >
        <div className="mx-auto flex size-14 items-center justify-center rounded-pill bg-info-bg text-info">
          {isUnderConstruction ? (
            <Construction aria-hidden="true" className="size-7" />
          ) : (
            <LockKeyhole aria-hidden="true" className="size-7" />
          )}
        </div>
        <h1 className="mt-5 text-h1-sm text-ink sm:text-h1" id="access-denied-title" tabIndex={-1}>
          {title}
        </h1>
        <p className="mx-auto mt-3 max-w-reading text-body text-ink-secondary">
          {isUnderConstruction
            ? 'Khu vực dành cho vai trò của bạn chưa có trong phiên bản hiện tại.'
            : isGuest
              ? 'Bạn cần đăng nhập bằng tài khoản có quyền phù hợp để mở khu vực này.'
              : 'Tài khoản hiện tại không có quyền mở khu vực vừa yêu cầu. Không có dữ liệu riêng tư nào được hiển thị.'}
        </p>

        <div className="mt-7">
          {isGuest ? (
            <GuestActions />
          ) : isUnderConstruction ? (
            <Button to="/">Về trang chủ</Button>
          ) : (
            <Button to={returnTo}>{returnLabel}</Button>
          )}
        </div>

        {state === 'error' ? (
          <div
            className="mt-5 flex flex-wrap items-center justify-center gap-2 text-body-sm text-ink-secondary"
            role="status"
          >
            <span>Chưa kiểm tra được phiên đăng nhập.</span>
            <Button onClick={onRetry} size="sm" variant="ghost">
              Thử lại
            </Button>
          </div>
        ) : null}

        {state === 'empty' ? (
          <p className="mt-5 text-body-sm text-ink-secondary" role="status">
            Tài khoản chưa có khu vực riêng. Bạn có thể trở về trang chủ.
          </p>
        ) : null}
      </section>
    </main>
  )
}
