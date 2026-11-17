import type { ReactNode } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import {
  actionLabels,
  availableActions,
  fieldErrors,
  kindLabels,
  statusLabels,
} from '../referenceForm'
import type { AdminListParams, AdminReference, AdminReferencePage, ReferenceAction } from '../types'

export type ReferenceAdminViewProps = {
  state: ApiQueryState
  data?: AdminReferencePage
  error?: ApiError
  filters: AdminListParams
  editor?: ReactNode
  busy: boolean
  notice?: string
  confirmation?: { item: AdminReference; action: ReferenceAction }
  actionError?: ApiError
  onFilter: (next: AdminListParams) => void
  onPage: (page: number) => void
  onRetry: () => void
  onCreate: () => void
  onEdit: (id: number) => void
  onAction: (item: AdminReference, action: ReferenceAction) => void
  onCancel: () => void
  onConfirm: () => void
}

export function ReferenceAdminView(props: ReferenceAdminViewProps) {
  const { state, data, error, filters, editor, busy, notice, confirmation, actionError } = props
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 break-words px-4 py-6 text-ink">
      <PageHeader
        title="Tài nguyên tham khảo"
        subtitle="Lọc tài nguyên, xem báo lỗi và quản lý trạng thái công bố."
        actions={
          <Button disabled={busy} onClick={props.onCreate}>
            Tạo tài nguyên
          </Button>
        }
      />
      {notice ? (
        <p role="status" className="rounded-md bg-success-bg p-4 text-body-sm text-success">
          {notice}
        </p>
      ) : null}
      {editor}
      {confirmation ? (
        <Card role="region" aria-label="Xác nhận thay đổi trạng thái" className="space-y-3">
          <h2 className="text-h3">
            {actionLabels[confirmation.action]}: {confirmation.item.title}?
          </h2>
          <p>
            {confirmation.action === 'unhide'
              ? 'Mục sẽ về Bản nháp, cần công bố lại để xuất hiện công khai.'
              : confirmation.action === 'hide'
                ? 'Mục sẽ không còn xuất hiện công khai.'
                : 'Mục sẽ xuất hiện trong danh mục công khai nếu đáp ứng điều kiện.'}
          </p>
          {actionError ? (
            <div role="alert">
              <p>{actionError.message}</p>
              <ul>
                {Object.entries(fieldErrors(actionError)).map(([field, message]) => (
                  <li key={field}>
                    {field === 'url'
                      ? 'Liên kết'
                      : field === 'thumbnailUrl'
                        ? 'Liên kết ảnh'
                        : field === 'author'
                          ? 'Tác giả'
                          : field === 'title'
                            ? 'Tên tài nguyên'
                            : field}
                    : {message}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button loading={busy} onClick={props.onConfirm}>
              Xác nhận {actionLabels[confirmation.action].toLowerCase()}
            </Button>
            <Button disabled={busy} variant="outline" onClick={props.onCancel}>
              Huỷ
            </Button>
          </div>
        </Card>
      ) : null}
      <Card>
        <div className="grid min-w-0 grid-cols-1 gap-x-4 sm:grid-cols-3">
          <FormField htmlFor="admin-ref-kind" label="Loại">
            <Select
              value={filters.kind ?? ''}
              onChange={(event) =>
                props.onFilter({
                  ...filters,
                  kind: event.target.value
                    ? (event.target.value as AdminListParams['kind'])
                    : undefined,
                })
              }
            >
              <option value="">Tất cả loại</option>
              {Object.entries(kindLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField htmlFor="admin-ref-status" label="Trạng thái">
            <Select
              value={filters.status ?? ''}
              onChange={(event) =>
                props.onFilter({
                  ...filters,
                  status: event.target.value
                    ? (event.target.value as AdminListParams['status'])
                    : undefined,
                })
              }
            >
              <option value="">Tất cả trạng thái</option>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>
          <label className="mb-4 flex min-h-touch items-center gap-2 text-body-sm sm:mt-7">
            <input
              type="checkbox"
              checked={filters.reported === true}
              onChange={(event) =>
                props.onFilter({ ...filters, reported: event.target.checked ? true : undefined })
              }
            />
            Có báo lỗi
          </label>
        </div>
        {state === 'loading' ? (
          <div role="status">
            <span className="sr-only">Đang tải tài nguyên…</span>
            <div aria-hidden="true">
              <Skeleton variant="table" />
            </div>
          </div>
        ) : null}
        {state === 'error' ? (
          <div role="alert">
            <ErrorState
              title="Chưa tải được danh sách"
              message={error?.message ?? 'Không tải được tài nguyên.'}
              requestId={error?.requestId}
              onRetry={props.onRetry}
            />
          </div>
        ) : null}
        {state === 'empty' ? (
          <EmptyState
            title="Chưa có tài nguyên phù hợp."
            description="Thử đổi bộ lọc để tìm tài nguyên."
          />
        ) : null}
        {state === 'ready' && data ? (
          <div
            role="region"
            aria-label="Bảng tài nguyên tham khảo"
            tabIndex={0}
            className="max-w-full rounded-sm border border-line"
          >
            <table role="table" className="block w-full border-collapse text-left text-body-sm lg:table lg:table-fixed">
              <caption className="sr-only">Danh sách tài nguyên tham khảo</caption>
              <thead role="rowgroup" className="sr-only lg:not-sr-only lg:table-header-group">
                <tr role="row" className="bg-canvas">
                  {['Tên', 'Loại', 'Trạng thái', 'Số lần báo lỗi', 'Báo gần nhất', 'Thao tác'].map(
                    (label) => (
                      <th key={label} role="columnheader" scope="col" className="border-b border-line p-3">
                        {label}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody role="rowgroup" className="block lg:table-row-group">
                {data.items.map((item) => (
                  <tr key={item.id} role="row" className="block border-b border-line p-3 last:border-0 lg:table-row lg:p-0">
                    <th role="rowheader" scope="row" className="block pb-2 font-medium lg:table-cell lg:p-3">
                      {item.title}
                    </th>
                    <td role="cell" className="block py-1 lg:table-cell lg:p-3">
                      <span aria-hidden="true" className="mr-2 text-ink-muted lg:hidden">Loại</span>
                      {kindLabels[item.kind]}
                    </td>
                    <td role="cell" className="block py-1 lg:table-cell lg:p-3">
                      <span aria-hidden="true" className="mr-2 text-ink-muted lg:hidden">Trạng thái</span>
                      <StatusBadge tone={item.status === 'PUBLISHED' ? 'success' : 'neutral'}>
                        {statusLabels[item.status]}
                      </StatusBadge>
                    </td>
                    <td role="cell" className="block py-1 tabular-nums lg:table-cell lg:p-3">
                      <span aria-hidden="true" className="mr-2 text-ink-muted lg:hidden">Số lần báo lỗi</span>
                      {item.reportCount}
                    </td>
                    <td role="cell" className="block py-1 lg:table-cell lg:p-3">
                      <span aria-hidden="true" className="mr-2 text-ink-muted lg:hidden">Báo gần nhất</span>
                      {item.lastReportedAt
                        ? new Date(item.lastReportedAt).toLocaleString('vi-VN', {
                            timeZone: 'Asia/Ho_Chi_Minh',
                          })
                        : 'Chưa có'}
                    </td>
                    <td role="cell" className="block pt-2 lg:table-cell lg:p-3">
                      <div className="flex flex-wrap gap-2">
                        <Button
                          size="sm"
                          disabled={busy}
                          variant="outline"
                          onClick={() => props.onEdit(item.id)}
                        >
                          Sửa
                        </Button>
                        {availableActions(item.status).map((action) => (
                          <Button
                            key={action}
                            size="sm"
                            disabled={busy}
                            variant="ghost"
                            onClick={() => props.onAction(item, action)}
                          >
                            {actionLabels[action]}
                          </Button>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {(state === 'ready' || state === 'empty') && data ? (
          <div className="mt-4">
            <Pagination
              page={data.page + 1}
              size={data.size}
              total={data.total}
              onChange={(page) => props.onPage(page - 1)}
            />
          </div>
        ) : null}
      </Card>
    </main>
  )
}
