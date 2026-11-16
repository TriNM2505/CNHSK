import { Construction, LockKeyhole } from 'lucide-react'

import { Button, Card, PageHeader, Skeleton, StatusBadge } from '@/shared/ui'

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
    <div className="flex flex-wrap items-center gap-3">
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
      <main className="mx-auto min-h-[calc(100vh-4rem)] w-full min-w-0 max-w-reading px-4 py-10 sm:px-6 sm:py-16">
        <Skeleton className="min-h-72" variant="card" />
      </main>
    )
  }

  const isUnderConstruction = mode === 'under-construction'
  const isGuest = mode === 'guest'
  const title = isUnderConstruction
    ? `Khu vực ${joinAreaLabels(areaLabels)} đang được xây dựng`
    : 'Không có quyền truy cập'
  const description = isUnderConstruction
    ? 'Khu vực dành cho vai trò của bạn chưa có trong phiên bản hiện tại.'
    : isGuest
      ? 'Bạn cần đăng nhập bằng tài khoản có quyền phù hợp để mở khu vực này.'
      : 'Tài khoản hiện tại không có quyền mở khu vực vừa yêu cầu. Không có dữ liệu riêng tư nào được hiển thị.'

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-10 sm:px-6 sm:py-16">
      <div>
        <div className="mb-3">
          {isUnderConstruction ? (
            <StatusBadge tone="info">Đang phát triển</StatusBadge>
          ) : isGuest ? (
            <StatusBadge tone="warning">Yêu cầu đăng nhập</StatusBadge>
          ) : (
            <StatusBadge tone="danger">403 · Hạn chế quyền</StatusBadge>
          )}
        </div>
        <PageHeader className="mb-0" title={title} />
      </div>

      <Card className="space-y-6 p-5 sm:p-8">
        <div className="flex items-start gap-4">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-pill bg-info-bg text-info">
            {isUnderConstruction ? (
              <Construction aria-hidden="true" className="size-6" />
            ) : (
              <LockKeyhole aria-hidden="true" className="size-6" />
            )}
          </div>
          <div className="min-w-0 space-y-2">
            <p className="text-body text-ink">{description}</p>
            <p className="text-body-sm text-ink-secondary">
              Quyền truy cập trên CNHSK được kiểm tra tường minh theo từng vai trò; bạn có thể quay
              về khu vực được phép bên dưới.
            </p>
          </div>
        </div>

        {state === 'error' ? (
          <div
            className="flex flex-wrap items-center justify-between gap-3 rounded-sm border border-line bg-surface-subtle p-3 text-body-sm text-ink-secondary"
            role="status"
          >
            <span>Chưa kiểm tra được phiên đăng nhập.</span>
            <Button onClick={onRetry} size="sm" variant="outline">
              Thử lại
            </Button>
          </div>
        ) : null}

        {state === 'empty' ? (
          <p
            className="rounded-sm border border-line bg-canvas p-3 text-body-sm text-ink-secondary"
            role="status"
          >
            Tài khoản chưa có khu vực riêng. Bạn có thể trở về trang chủ.
          </p>
        ) : null}

        <div className="border-t border-line pt-5">
          {isGuest ? (
            <GuestActions />
          ) : isUnderConstruction ? (
            <Button to="/">Về trang chủ</Button>
          ) : (
            <Button to={returnTo}>{returnLabel}</Button>
          )}
        </div>
      </Card>
    </main>
  )
}
