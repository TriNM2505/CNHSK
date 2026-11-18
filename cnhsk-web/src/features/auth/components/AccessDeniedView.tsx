import { Button, PageHeader, Skeleton, StatusBadge } from '@/shared/ui'

import { AuthInkWashStage } from './AuthInkWashStage'

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
      <AuthInkWashStage variant="079">
        <Skeleton className="min-h-72" variant="card" />
      </AuthInkWashStage>
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
    <AuthInkWashStage variant="079">
      <div className="space-y-5">
        <div>
          <div className="mb-3 flex flex-wrap items-center gap-2">
            {isUnderConstruction ? (
              <StatusBadge tone="info">Đang phát triển</StatusBadge>
            ) : isGuest ? (
              <StatusBadge tone="warning">Yêu cầu đăng nhập</StatusBadge>
            ) : (
              <StatusBadge tone="danger">403 · Hạn chế quyền</StatusBadge>
            )}
            <span className="text-caption text-brand-700">Kiểm soát truy cập CNHSK</span>
          </div>
          <PageHeader className="mb-0" subtitle={description} title={title} />
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

        <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-4">
          {isGuest ? (
            <GuestActions />
          ) : isUnderConstruction ? (
            <Button to="/">Về trang chủ</Button>
          ) : (
            <Button to={returnTo}>{returnLabel}</Button>
          )}
        </div>
      </div>
    </AuthInkWashStage>
  )
}
