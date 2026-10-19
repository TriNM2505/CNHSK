import { Search, UsersRound } from 'lucide-react'

import {
  Button,
  DataTable,
  type DataTableColumn,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import { ROLE_LABELS, ROLE_OPTIONS, USER_STATUS_LABELS } from '../labels'
import type { AdminUserListQuery } from '../userListQuery'
import {
  ADMIN_USER_PAGE_SIZE,
  formatAdminUserCreatedAt,
  hasAdminUserFilters,
} from '../userListQuery'
import type { AdminUserPage, AdminUserSummary, UserStatus } from '../types'

export type UserListViewState = 'loading' | 'empty' | 'error' | 'ready'

export interface UserListViewProps {
  data?: AdminUserPage
  error?: { message: string; requestId?: string }
  onClearFilters: () => void
  onFirstPage: () => void
  onOpenUser: (id: number) => void
  onPageChange: (page: number) => void
  onQueryChange: (value: string) => void
  onRetry: () => void
  onRoleChange: (value: AdminUserListQuery['role']) => void
  onStatusChange: (value: AdminUserListQuery['status']) => void
  query: AdminUserListQuery
  queryDraft: string
  queryError?: string
  state: UserListViewState
}

const STATUS_TONES: Record<UserStatus, 'success' | 'warning' | 'danger'> = {
  ACTIVE: 'success',
  UNVERIFIED: 'warning',
  BANNED: 'danger',
}

const columns: readonly DataTableColumn<AdminUserSummary>[] = [
  {
    header: 'Mã tài khoản',
    id: 'id',
    numeric: true,
    render: (user) => user.id,
  },
  {
    header: 'Tên hiển thị',
    id: 'displayName',
    render: (user) => <span className="font-semibold">{user.displayName}</span>,
  },
  {
    header: 'Email đã che thông tin',
    id: 'emailMasked',
    render: (user) => <span className="break-all">{user.emailMasked}</span>,
  },
  {
    header: 'Vai trò được cấp',
    id: 'roles',
    render: (user) => (
      <div className="flex flex-wrap gap-1.5">
        {user.roles.map((role) => (
          <StatusBadge key={role} tone="neutral">
            {ROLE_LABELS[role]}
          </StatusBadge>
        ))}
      </div>
    ),
  },
  {
    header: 'Trạng thái tài khoản',
    id: 'status',
    render: (user) => (
      <StatusBadge tone={STATUS_TONES[user.status]}>{USER_STATUS_LABELS[user.status]}</StatusBadge>
    ),
  },
  {
    header: 'Ngày tạo',
    id: 'createdAt',
    render: (user) => (
      <time dateTime={user.createdAt}>{formatAdminUserCreatedAt(user.createdAt)}</time>
    ),
  },
]

function UserListEmpty({
  data,
  onClearFilters,
  onFirstPage,
  query,
}: Pick<UserListViewProps, 'data' | 'onClearFilters' | 'onFirstPage' | 'query'>) {
  if ((data?.totalElements ?? 0) > 0) {
    return (
      <EmptyState
        action={<Button onClick={onFirstPage}>Về trang đầu</Button>}
        description="Trang đã chọn nằm ngoài phạm vi dữ liệu hiện có."
        title="Trang này không có dữ liệu"
      />
    )
  }

  if (hasAdminUserFilters(query)) {
    return (
      <EmptyState
        action={
          <Button onClick={onClearFilters} variant="outline">
            Xoá bộ lọc
          </Button>
        }
        description="Hãy thay đổi từ khoá hoặc bộ lọc rồi thử lại."
        icon={<Search aria-hidden="true" className="size-5" />}
        title="Không tìm thấy tài khoản phù hợp"
      />
    )
  }

  return (
    <EmptyState
      description="Danh sách tài khoản sẽ xuất hiện tại đây khi có dữ liệu."
      icon={<UsersRound aria-hidden="true" className="size-5" />}
      title="Chưa có tài khoản nào"
    />
  )
}

export function UserListView({
  data,
  error,
  onClearFilters,
  onFirstPage,
  onOpenUser,
  onPageChange,
  onQueryChange,
  onRetry,
  onRoleChange,
  onStatusChange,
  query,
  queryDraft,
  queryError,
  state,
}: UserListViewProps) {
  const controlsDisabled = state === 'loading'

  return (
    <main className="mx-auto w-full max-w-content p-4 sm:p-6">
      <PageHeader
        subtitle="Tìm kiếm và xem thông tin tóm tắt của các tài khoản trong hệ thống."
        title="Quản lý tài khoản"
      />

      <section
        aria-label="Bộ lọc tài khoản"
        className="mb-6 rounded-md border border-line bg-surface p-4"
      >
        <div className="grid gap-x-4 sm:grid-cols-2 lg:grid-cols-3">
          <FormField
            className="mb-0 sm:col-span-2 lg:col-span-1"
            error={queryError}
            hint="Để trống hoặc nhập từ 2 ký tự."
            htmlFor="admin-user-query"
            label="Email hoặc tên cần tìm"
          >
            <Input
              disabled={controlsDisabled}
              maxLength={100}
              onChange={(event) => onQueryChange(event.target.value)}
              type="search"
              value={queryDraft}
            />
          </FormField>

          <FormField className="mb-0" htmlFor="admin-user-role" label="Lọc theo vai trò">
            <Select
              disabled={controlsDisabled}
              onChange={(event) => onRoleChange(event.target.value as AdminUserListQuery['role'])}
              value={query.role}
            >
              <option value="">Tất cả vai trò</option>
              {ROLE_OPTIONS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField className="mb-0" htmlFor="admin-user-status" label="Lọc theo trạng thái">
            <Select
              disabled={controlsDisabled}
              onChange={(event) =>
                onStatusChange(event.target.value as AdminUserListQuery['status'])
              }
              value={query.status}
            >
              <option value="">Tất cả trạng thái</option>
              {Object.entries(USER_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
      </section>

      <div aria-live="polite">
        {state === 'loading' ? <Skeleton variant="table" /> : null}

        {state === 'error' ? (
          <ErrorState
            message={error?.message ?? 'Không tải được danh sách tài khoản. Vui lòng thử lại.'}
            onRetry={onRetry}
            requestId={error?.requestId}
          />
        ) : null}

        {state === 'empty' ? (
          <UserListEmpty
            data={data}
            onClearFilters={onClearFilters}
            onFirstPage={onFirstPage}
            query={query}
          />
        ) : null}

        {state === 'ready' && data ? (
          <section aria-label="Danh sách tài khoản">
            <p className="mb-3 text-body-sm text-ink-secondary">
              Có {data.totalElements} tài khoản
            </p>
            <DataTable
              caption="Danh sách tài khoản"
              columns={columns}
              getRowLabel={(user) => `Mở tài khoản ${user.displayName}`}
              onRowClick={(user) => onOpenUser(user.id)}
              rowKey={(user) => user.id}
              rows={data.items}
            />
            <div className="mt-6">
              <Pagination
                label="Phân trang danh sách tài khoản"
                onChange={onPageChange}
                page={data.page + 1}
                size={data.size || ADMIN_USER_PAGE_SIZE}
                total={data.totalElements}
              />
            </div>
          </section>
        ) : null}
      </div>
    </main>
  )
}
