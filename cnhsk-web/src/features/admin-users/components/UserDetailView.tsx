import { ArrowLeft, ShieldAlert, UserRoundX } from 'lucide-react'
import type { ReactNode } from 'react'

import type { RoleCode } from '@/features/auth/types'
import {
  Button,
  ConfirmPanel,
  DataTable,
  type DataTableColumn,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  Select,
  Skeleton,
  StatusBadge,
  Textarea,
} from '@/shared/ui'

import { formatAdminUserDate } from '../formatters'
import { ROLE_LABELS, ROLE_OPTIONS, USER_STATUS_LABELS } from '../labels'
import type { AdminUserDetail, AdminUserRoleDetail, UserStatus } from '../types'
import {
  BAN_REASON_MAX_LENGTH,
  type UserDetailAction,
  type UserDetailActionError,
} from '../userDetailActions'

export type UserDetailViewState = 'loading' | 'empty' | 'error' | 'ready'

export type UserDetailNotice =
  { kind: 'success'; message: string } | { kind: 'error'; message: string; requestId?: string }

export interface UserDetailViewProps {
  action?: UserDetailAction
  actionError?: UserDetailActionError
  banReason: string
  banReasonError?: string
  data?: AdminUserDetail
  error?: { message: string; requestId?: string }
  isSelf: boolean
  notice?: UserDetailNotice
  onBanReasonChange: (value: string) => void
  onCancelAction: () => void
  onConfirmAction: () => void
  onRequestAction: (action: UserDetailAction) => void
  onRetry: () => void
  onRoleChange: (role: RoleCode | '') => void
  pending: boolean
  refreshing: boolean
  selectedRole: RoleCode | ''
  state: UserDetailViewState
}

const STATUS_TONES: Record<UserStatus, 'success' | 'warning' | 'danger'> = {
  ACTIVE: 'success',
  UNVERIFIED: 'warning',
  BANNED: 'danger',
}

function InlineNotice({ notice }: { notice: UserDetailNotice | UserDetailActionError }) {
  const success = 'kind' in notice && notice.kind === 'success'
  const requestId = 'requestId' in notice ? notice.requestId : undefined
  return (
    <div
      className={`rounded-md border p-4 text-body-sm ${
        success
          ? 'border-success bg-success-bg text-success'
          : 'border-danger bg-danger-bg text-danger'
      }`}
      role={success ? 'status' : 'alert'}
    >
      <p>{notice.message}</p>
      {!success && requestId ? <p className="mt-1 text-caption">Mã lỗi: {requestId}</p> : null}
    </div>
  )
}

function DefinitionItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <dt className="text-label text-ink-secondary">{label}</dt>
      <dd className="mt-1 break-words text-body text-ink">{value}</dd>
    </div>
  )
}

function ActionMessage({
  action,
  actionError,
  banReason,
  banReasonError,
  data,
  onBanReasonChange,
  pending,
}: Pick<
  UserDetailViewProps,
  | 'action'
  | 'actionError'
  | 'banReason'
  | 'banReasonError'
  | 'data'
  | 'onBanReasonChange'
  | 'pending'
>) {
  if (!action || !data) return null

  return (
    <div className="space-y-4">
      {actionError ? <InlineNotice notice={actionError} /> : null}

      {action.kind === 'grant' ? (
        <p>
          Cấp vai trò <strong>{ROLE_LABELS[action.role]}</strong> cho{' '}
          <strong>{data.displayName}</strong>. Quyền mới có hiệu lực từ request tiếp theo.
        </p>
      ) : null}

      {action.kind === 'revoke' ? (
        <div className="space-y-2">
          <p>
            Thu hồi vai trò <strong>{ROLE_LABELS[action.role]}</strong> của{' '}
            <strong>{data.displayName}</strong>?
          </p>
          <p className="font-semibold text-danger">
            Người này sẽ mất quyền ngay ở request tiếp theo.
          </p>
        </div>
      ) : null}

      {action.kind === 'ban' ? (
        <>
          <p>
            Cấm tài khoản <strong>{data.displayName}</strong>? Đăng xuất khỏi mọi thiết bị.
          </p>
          <FormField
            className="mb-0"
            error={banReasonError}
            hint={`${banReason.length}/${BAN_REASON_MAX_LENGTH} ký tự`}
            htmlFor="ban-reason"
            label="Lý do cấm"
            required
          >
            <Textarea
              aria-label="Lý do cấm"
              disabled={pending}
              maxLength={BAN_REASON_MAX_LENGTH}
              onChange={(event) => onBanReasonChange(event.target.value)}
              value={banReason}
            />
          </FormField>
        </>
      ) : null}

      {action.kind === 'unban' ? (
        <p>
          Gỡ cấm tài khoản <strong>{data.displayName}</strong>? Các phiên đăng nhập cũ không được
          khôi phục.
        </p>
      ) : null}
    </div>
  )
}

function actionPanelLabels(action?: UserDetailAction) {
  switch (action?.kind) {
    case 'grant':
      return { confirm: 'Cấp vai trò', title: 'Xác nhận cấp vai trò' }
    case 'revoke':
      return { confirm: 'Thu hồi vai trò', title: 'Xác nhận thu hồi vai trò' }
    case 'ban':
      return { confirm: 'Cấm tài khoản', title: 'Xác nhận cấm tài khoản' }
    case 'unban':
      return { confirm: 'Gỡ cấm', title: 'Xác nhận gỡ cấm' }
    default:
      return { confirm: 'Xác nhận', title: 'Xác nhận thao tác' }
  }
}

export function UserDetailView({
  action,
  actionError,
  banReason,
  banReasonError,
  data,
  error,
  isSelf,
  notice,
  onBanReasonChange,
  onCancelAction,
  onConfirmAction,
  onRequestAction,
  onRetry,
  onRoleChange,
  pending,
  refreshing,
  selectedRole,
  state,
}: UserDetailViewProps) {
  const labels = actionPanelLabels(action)
  const busy = pending || refreshing
  const assignedRoles = new Set(data?.roles.map((role) => role.code) ?? [])
  const missingRoles = ROLE_OPTIONS.filter(([role]) => !assignedRoles.has(role))
  const rolesColumns: readonly DataTableColumn<AdminUserRoleDetail>[] = [
    {
      header: 'Vai trò',
      id: 'role',
      render: (role) => <StatusBadge tone="neutral">{ROLE_LABELS[role.code]}</StatusBadge>,
    },
    {
      header: 'Người cấp',
      id: 'grantedBy',
      render: (role) => (role.grantedBy === null ? 'Không ghi nhận' : `#${role.grantedBy}`),
    },
    {
      header: 'Thời điểm cấp',
      id: 'grantedAt',
      render: (role) => (
        <time dateTime={role.grantedAt}>{formatAdminUserDate(role.grantedAt)}</time>
      ),
    },
    {
      header: 'Thao tác',
      id: 'action',
      render: (role) => {
        const selfAdmin = isSelf && role.code === 'ADMIN'
        return (
          <Button
            disabled={busy || selfAdmin}
            onClick={() => onRequestAction({ kind: 'revoke', role: role.code })}
            size="sm"
            title={selfAdmin ? 'Không thể tự thu hồi vai trò Quản trị viên.' : undefined}
            variant="ghost"
          >
            Thu hồi
          </Button>
        )
      },
    },
  ]

  return (
    <main className="mx-auto w-full max-w-content p-4 sm:p-6">
      <PageHeader
        actions={
          <Button to="/admin/users" variant="outline">
            <ArrowLeft aria-hidden="true" className="size-4" />
            Danh sách tài khoản
          </Button>
        }
        subtitle="Xem thông tin định danh, vai trò và trạng thái truy cập của tài khoản."
        title="Chi tiết tài khoản"
      />

      {state === 'loading' ? <Skeleton variant="card" /> : null}

      {state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không tải được chi tiết tài khoản. Vui lòng thử lại.'}
          onRetry={onRetry}
          requestId={error?.requestId}
        />
      ) : null}

      {state === 'empty' ? (
        <EmptyState
          action={<Button to="/admin/users">Về danh sách tài khoản</Button>}
          description="Tài khoản có thể đã bị xoá hoặc đường dẫn không còn hợp lệ."
          icon={<UserRoundX aria-hidden="true" className="size-5" />}
          title="Không tìm thấy tài khoản"
        />
      ) : null}

      {state === 'ready' && data ? (
        <div className="space-y-6">
          {notice ? <InlineNotice notice={notice} /> : null}
          {refreshing ? (
            <p aria-live="polite" className="text-body-sm text-ink-secondary">
              Đang cập nhật thông tin tài khoản…
            </p>
          ) : null}

          <section className="rounded-md border border-line bg-surface p-4 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-h2-sm text-ink sm:text-h2">{data.displayName}</h2>
                <p className="mt-1 break-all text-body text-ink-secondary">{data.email}</p>
              </div>
              <StatusBadge tone={STATUS_TONES[data.status]}>
                {USER_STATUS_LABELS[data.status]}
              </StatusBadge>
            </div>

            <dl className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              <DefinitionItem label="Mã tài khoản" value={`#${data.id}`} />
              <DefinitionItem
                label="Ngày tạo"
                value={<time dateTime={data.createdAt}>{formatAdminUserDate(data.createdAt)}</time>}
              />
              <DefinitionItem
                label="Xác minh email"
                value={
                  data.emailVerifiedAt ? (
                    <time dateTime={data.emailVerifiedAt}>
                      {formatAdminUserDate(data.emailVerifiedAt)}
                    </time>
                  ) : (
                    'Chưa xác minh'
                  )
                }
              />
              {data.bannedAt ? (
                <DefinitionItem
                  label="Thời điểm cấm"
                  value={<time dateTime={data.bannedAt}>{formatAdminUserDate(data.bannedAt)}</time>}
                />
              ) : null}
              {data.banReason ? <DefinitionItem label="Lý do cấm" value={data.banReason} /> : null}
            </dl>
          </section>

          <section className="rounded-md border border-line bg-surface p-4 sm:p-6">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="text-h3 text-ink">Vai trò được cấp</h2>
                <p className="mt-1 text-body-sm text-ink-secondary">
                  Quyền thay đổi có hiệu lực từ request tiếp theo.
                </p>
              </div>
              <div className="flex w-full flex-wrap items-end gap-2 sm:w-auto">
                <FormField
                  className="mb-0 min-w-56 flex-1"
                  htmlFor="role-to-grant"
                  label="Vai trò cần cấp"
                >
                  <Select
                    disabled={busy || isSelf || data.status === 'BANNED' || !missingRoles.length}
                    onChange={(event) => onRoleChange(event.target.value as RoleCode | '')}
                    value={selectedRole}
                  >
                    <option value="">Chọn vai trò</option>
                    {missingRoles.map(([role, label]) => (
                      <option key={role} value={role}>
                        {label}
                      </option>
                    ))}
                  </Select>
                </FormField>
                <Button
                  disabled={
                    busy ||
                    isSelf ||
                    data.status === 'BANNED' ||
                    !selectedRole ||
                    !missingRoles.length
                  }
                  onClick={() =>
                    selectedRole && onRequestAction({ kind: 'grant', role: selectedRole })
                  }
                >
                  Cấp vai trò
                </Button>
              </div>
            </div>

            {isSelf ? (
              <p className="mt-3 text-body-sm text-ink-secondary">
                Bạn không thể tự cấp vai trò hoặc tự thu hồi vai trò Quản trị viên.
              </p>
            ) : null}
            {data.status === 'BANNED' ? (
              <p className="mt-3 text-body-sm text-ink-secondary">
                Hãy gỡ cấm tài khoản trước khi cấp thêm vai trò.
              </p>
            ) : null}

            <DataTable
              caption="Vai trò đã cấp cho tài khoản"
              className="mt-5"
              columns={rolesColumns}
              rowKey={(role) => role.code}
              rows={data.roles}
            />
          </section>

          <section className="rounded-md border border-danger bg-danger-bg p-4 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-start gap-3">
                <ShieldAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-danger" />
                <div>
                  <h2 className="text-h3 text-ink">Trạng thái truy cập</h2>
                  <p className="mt-1 text-body-sm text-ink-secondary">
                    Cấm tài khoản sẽ thu hồi toàn bộ phiên đăng nhập hiện tại.
                  </p>
                </div>
              </div>
              {data.status === 'BANNED' ? (
                <Button
                  disabled={busy}
                  onClick={() => onRequestAction({ kind: 'unban' })}
                  variant="outline"
                >
                  Gỡ cấm tài khoản
                </Button>
              ) : (
                <Button
                  disabled={busy || isSelf}
                  onClick={() => onRequestAction({ kind: 'ban' })}
                  title={isSelf ? 'Không thể cấm tài khoản của chính mình.' : undefined}
                  variant="danger"
                >
                  Cấm tài khoản
                </Button>
              )}
            </div>
            {isSelf && data.status !== 'BANNED' ? (
              <p className="mt-3 text-body-sm text-ink-secondary">
                Bạn không thể cấm tài khoản của chính mình.
              </p>
            ) : null}
          </section>
        </div>
      ) : null}

      <ConfirmPanel
        confirmLabel={labels.confirm}
        danger={action?.kind === 'ban' || action?.kind === 'revoke'}
        message={
          <ActionMessage
            action={action}
            actionError={actionError}
            banReason={banReason}
            banReasonError={banReasonError}
            data={data}
            onBanReasonChange={onBanReasonChange}
            pending={pending}
          />
        }
        onCancel={onCancelAction}
        onConfirm={onConfirmAction}
        open={Boolean(action)}
        pending={pending}
        title={labels.title}
      />
    </main>
  )
}
