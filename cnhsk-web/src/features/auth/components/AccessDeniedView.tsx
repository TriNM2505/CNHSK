import { Button, Skeleton } from '@/shared/ui'

import { AuthHeading } from './AuthHeading'
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
      : 'Tài khoản hiện tại không có quyền mở khu vực này.'

  return (
    <AuthInkWashStage variant="079">
      <AuthHeading seal="止步" title={title}>
        {description}
      </AuthHeading>

      {state === 'error' ? (
        <div
          className="mb-4 flex flex-wrap items-center justify-between gap-3 text-body-sm text-ink-secondary"
          role="status"
        >
          <span>Chưa kiểm tra được phiên đăng nhập.</span>
          <Button onClick={onRetry} variant="outline">
            Thử lại
          </Button>
        </div>
      ) : null}

      {state === 'empty' ? (
        <p className="mb-4 text-body-sm text-ink-secondary" role="status">
          Tài khoản chưa có khu vực riêng. Bạn có thể trở về trang chủ.
        </p>
      ) : null}

      {isGuest ? (
        <GuestActions />
      ) : isUnderConstruction ? (
        <Button to="/">Về trang chủ</Button>
      ) : (
        <Button to={returnTo}>{returnLabel}</Button>
      )}
    </AuthInkWashStage>
  )
}
