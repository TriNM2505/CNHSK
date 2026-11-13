import type { ReactNode } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { Button, Card, FormField, PageHeader, Pagination, Select, StatusBadge } from '@/shared/ui'

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
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 text-ink [overflow-wrap:anywhere]">
      <PageHeader
        title="Tài nguyên tham khảo"
        actions={
          <Button disabled={busy} onClick={props.onCreate}>
            Tạo tài nguyên
          </Button>
        }
      />
      {notice ? <p role="status">{notice}</p> : null}
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
        <div className="grid min-w-0 grid-cols-1 gap-x-4 min-[641px]:grid-cols-3">
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
          <label className="mb-4 flex min-h-touch items-center gap-2 text-body-sm min-[641px]:mt-7">
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
        {state === 'loading' ? <p role="status">Đang tải tài nguyên…</p> : null}
        {state === 'error' ? (
          <div role="alert" className="space-y-3">
            <p>{error?.message ?? 'Không tải được tài nguyên.'}</p>
            <Button onClick={props.onRetry} variant="outline">
              Thử lại
            </Button>
          </div>
        ) : null}
        {state === 'empty' ? <p role="status">Chưa có tài nguyên phù hợp.</p> : null}
        {state === 'ready' && data ? (
          <div
            role="region"
            aria-label="Bảng tài nguyên tham khảo"
            tabIndex={0}
            className="max-w-full overflow-x-auto rounded-sm border border-line"
          >
            <table className="w-full min-w-[760px] border-collapse text-left text-body-sm">
              <caption className="sr-only">Danh sách tài nguyên tham khảo</caption>
              <thead>
                <tr className="bg-canvas">
                  {['Tên', 'Loại', 'Trạng thái', 'Số lần báo lỗi', 'Báo gần nhất', 'Thao tác'].map(
                    (label) => (
                      <th key={label} scope="col" className="border-b border-line p-3">
                        {label}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.id} className="border-b border-line last:border-0">
                    <th scope="row" className="max-w-xs p-3 font-medium">
                      {item.title}
                    </th>
                    <td className="p-3">{kindLabels[item.kind]}</td>
                    <td className="p-3">
                      <StatusBadge tone={item.status === 'PUBLISHED' ? 'success' : 'neutral'}>
                        {statusLabels[item.status]}
                      </StatusBadge>
                    </td>
                    <td className="p-3 tabular-nums">{item.reportCount}</td>
                    <td className="p-3">
                      {item.lastReportedAt
                        ? new Date(item.lastReportedAt).toLocaleString('vi-VN', {
                            timeZone: 'Asia/Ho_Chi_Minh',
                          })
                        : 'Chưa có'}
                    </td>
                    <td className="p-3">
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
