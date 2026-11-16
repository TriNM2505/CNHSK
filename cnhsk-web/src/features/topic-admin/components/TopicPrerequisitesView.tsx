import { AlertTriangle, GitBranch, X } from 'lucide-react'

import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  LevelBadge,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatTile,
  StatusBadge,
  SUPPORTED_HSK_LEVELS,
} from '@/shared/ui'
import type { ApiQueryState } from '@/shared/api/useApiQuery'

import type { AdminTopic, AdminTopicPage, TopicFilters, TopicRef } from '../types'

type TopicListProps = {
  candidates?: boolean
  data?: AdminTopicPage
  error?: { message: string; requestId?: string }
  filters: TopicFilters
  onFilter: (filters: TopicFilters) => void
  onRetry: () => void
  onSelect: (topic: AdminTopic) => void
  pending: boolean
  selected?: AdminTopic
  selectedIds: readonly number[]
  state: ApiQueryState
}

function TopicPicker({
  candidates = false,
  data,
  error,
  filters,
  onFilter,
  onRetry,
  onSelect,
  pending,
  selected,
  selectedIds,
  state,
}: TopicListProps) {
  const prefix = candidates ? 'prerequisite' : 'target-topic'
  return (
    <section
      aria-label={candidates ? 'Chọn tiên quyết' : 'Chọn chủ đề'}
      className="min-w-0 space-y-4 rounded-md border border-line bg-surface p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-h2 text-ink">
          {candidates ? 'Thêm chủ đề tiên quyết' : 'Chủ đề cần sửa'}
        </h2>
        {data ? <StatusBadge tone="neutral">{data.totalElements} chủ đề</StatusBadge> : null}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <FormField htmlFor={`${prefix}-search`} label="Tìm theo tên">
          <Input
            disabled={pending}
            maxLength={100}
            onChange={(e) => onFilter({ ...filters, q: e.target.value, page: 0 })}
            type="search"
            value={filters.q}
          />
        </FormField>
        <FormField htmlFor={`${prefix}-hsk`} label="Cấp HSK">
          <Select
            disabled={pending}
            onChange={(e) =>
              onFilter({
                ...filters,
                hsk: SUPPORTED_HSK_LEVELS.find((level) => String(level) === e.target.value),
                page: 0,
              })
            }
            value={filters.hsk ?? ''}
          >
            <option value="">Tất cả</option>
            {SUPPORTED_HSK_LEVELS.map((level) => (
              <option key={level} value={level}>
                HSK {level}
              </option>
            ))}
          </Select>
        </FormField>
      </div>
      {state === 'loading' ? (
        <Skeleton variant="table" />
      ) : state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không thể tải chủ đề.'}
          onRetry={onRetry}
          requestId={error?.requestId}
        />
      ) : !data?.items.length ? (
        <EmptyState title="Không có chủ đề phù hợp" description="Thử thay đổi tên hoặc cấp HSK." />
      ) : candidates ? (
        <div className="overflow-hidden rounded-md lg:border lg:border-line lg:bg-surface">
          <table className="w-full border-collapse text-body-sm">
            <caption className="sr-only">Bảng chọn chủ đề tiên quyết</caption>
            <thead className="hidden bg-surface-subtle lg:table-header-group">
              <tr>
                <th className="border-b border-line p-3 text-left text-label text-ink" scope="col">
                  Chọn làm tiên quyết
                </th>
                <th
                  className="w-28 border-b border-line p-3 text-right text-label text-ink"
                  scope="col"
                >
                  Cấp HSK
                </th>
              </tr>
            </thead>
            <tbody className="block space-y-2 lg:table-row-group lg:space-y-0">
              {data.items.map((topic) => {
                const checked = selectedIds.includes(topic.id)
                return (
                  <tr
                    className={cn(
                      'block rounded-sm border border-line bg-surface lg:table-row lg:rounded-none lg:border-x-0 lg:border-t-0 lg:border-b lg:last:border-b-0',
                      checked && 'bg-brand-50/60',
                    )}
                    key={topic.id}
                  >
                    <td className="block p-3 lg:table-cell">
                      <label className="flex min-h-touch cursor-pointer items-start gap-3">
                        <input
                          checked={checked}
                          className="mt-1 size-4 shrink-0 accent-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                          disabled={
                            pending ||
                            topic.id === selected?.id ||
                            (!checked && selectedIds.length >= 10)
                          }
                          onChange={() => onSelect(topic)}
                          type="checkbox"
                        />
                        <span className="min-w-0 flex-1 break-words text-ink">
                          <span className="font-medium">{topic.name}</span>
                          <span className="mt-0.5 block text-caption text-ink-muted">
                            HSK {topic.hskLevel}
                            {topic.id === selected?.id ? ' · Chủ đề đang sửa' : ''}
                          </span>
                        </span>
                      </label>
                    </td>
                    <td className="hidden p-3 text-right align-middle lg:table-cell">
                      <LevelBadge exact level={topic.hskLevel} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <DataTable
          caption="Danh sách chủ đề cần sửa"
          columns={[
            {
              header: 'Chủ đề',
              id: 'name',
              render: (topic) => (
                <div className="min-w-0 break-words">
                  <p className="font-semibold text-ink">{topic.name}</p>
                  <p className="mt-0.5 text-caption text-ink-muted">
                    {topic.isRoot ? 'Chủ đề gốc' : `${topic.prereqs.length} tiên quyết`}
                  </p>
                </div>
              ),
            },
            {
              header: 'Cấp HSK',
              id: 'hskLevel',
              render: (topic) => (
                <div className="flex flex-wrap items-center gap-2">
                  <LevelBadge exact level={topic.hskLevel} />
                  {selected?.id === topic.id ? (
                    <StatusBadge tone="info">Đang chọn</StatusBadge>
                  ) : null}
                </div>
              ),
            },
          ]}
          getRowLabel={(topic) =>
            `${topic.name} (${topic.isRoot ? 'Chủ đề gốc' : `${topic.prereqs.length} tiên quyết`}) HSK ${topic.hskLevel}`
          }
          onRowClick={(topic) => {
            if (!pending) onSelect(topic)
          }}
          rowKey={(topic) => topic.id}
          rows={data.items}
        />
      )}
      {state === 'ready' && data ? (
        <Pagination
          label={candidates ? 'Phân trang tiên quyết' : 'Phân trang chủ đề'}
          onChange={(page) => {
            if (!pending) onFilter({ ...filters, page: page - 1 })
          }}
          page={data.page + 1}
          size={data.size}
          total={data.totalElements}
        />
      ) : null}
    </section>
  )
}

export type TopicPrerequisitesViewProps = {
  topics: TopicListProps
  candidates: TopicListProps
  selected?: AdminTopic
  chosen: readonly TopicRef[]
  onRemove: (id: number) => void
  onSave: () => void
  saving: boolean
  saveDisabled?: boolean
  message?: string
  error?: { message: string; requestId?: string }
  cycleNames?: readonly string[]
  warnings: readonly string[]
}

export function TopicPrerequisitesView({
  topics,
  candidates,
  selected,
  chosen,
  onRemove,
  onSave,
  saving,
  saveDisabled,
  message,
  error,
  cycleNames,
  warnings,
}: TopicPrerequisitesViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-20 text-ink [overflow-wrap:anywhere] sm:px-6">
      <PageHeader
        title="Tiên quyết chủ đề"
        subtitle="Thiết lập điều kiện mở chủ đề cho người học."
      />
      <Card className="border-brand-100 bg-brand-50 p-4 text-body-sm text-ink">
        Đây là tiên quyết chủ đề (cổng 90%), khác tiên quyết KID của cây tri thức
      </Card>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile label="Tổng số chủ đề" value={topics.data?.totalElements ?? 0} />
        <StatTile
          label="Chủ đề đang chọn"
          value={
            <span className="block truncate text-h3">
              {selected ? `HSK ${selected.hskLevel}` : 'Chưa chọn'}
            </span>
          }
        />
        <StatTile label="Tiên quyết đang gán" value={selected ? `${chosen.length}/10` : '—'} />
      </div>
      {!selected && error ? (
        <p
          className="break-words rounded-md border border-danger bg-danger-bg p-4 text-danger"
          role="alert"
        >
          {error.message}
        </p>
      ) : null}
      <div className="grid min-w-0 gap-6 xl:grid-cols-2">
        <TopicPicker {...topics} />
        {selected ? (
          <div className="min-w-0 space-y-6">
            <section
              aria-label="Tiên quyết đã chọn"
              className="space-y-4 rounded-md border border-line bg-surface p-5"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="break-words text-h2 text-ink">{selected.name}</h2>
                  <p className="mt-1 text-body-sm text-ink-secondary">
                    {chosen.length}/10 tiên quyết ·{' '}
                    {selected.isRoot ? 'Hiện là chủ đề gốc' : 'Hiện có tiên quyết'}
                  </p>
                </div>
                <LevelBadge exact level={selected.hskLevel} />
              </div>
              {chosen.length ? (
                <ul className="divide-y divide-line rounded-sm border border-line bg-canvas px-3">
                  {chosen.map((topic) => (
                    <li
                      className="flex min-w-0 items-center justify-between gap-2 py-2.5"
                      key={topic.id}
                    >
                      <span className="min-w-0 break-words text-body-sm text-ink">
                        {topic.name}{' '}
                        <span className="text-caption text-ink-secondary">
                          (HSK {topic.hskLevel})
                        </span>
                      </span>
                      <Button
                        aria-label={`Bỏ tiên quyết ${topic.name}`}
                        disabled={saving}
                        onClick={() => onRemove(topic.id)}
                        size="sm"
                        variant="outline"
                      >
                        <X aria-hidden="true" className="size-4" />
                      </Button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="rounded-sm bg-canvas p-3 text-body-sm text-ink-secondary">
                  Không có tiên quyết. Lưu để đặt thành chủ đề gốc.
                </p>
              )}
              <Button disabled={saving || saveDisabled} onClick={onSave}>
                <span className="inline-flex items-center gap-2">
                  <GitBranch aria-hidden="true" className="size-4 shrink-0" />
                  {saving ? 'Đang lưu…' : 'Lưu tiên quyết'}
                </span>
              </Button>
              {error ? (
                <div
                  className="break-words rounded-sm border border-danger bg-danger-bg p-3 text-body-sm text-danger"
                  role="alert"
                >
                  <p>{error.message}</p>
                  {cycleNames?.length ? (
                    <p className="mt-2 font-medium">Đường vòng: {cycleNames.join(' → ')}</p>
                  ) : null}
                  {error.requestId ? (
                    <p className="mt-1 text-caption">Mã yêu cầu: {error.requestId}</p>
                  ) : null}
                </div>
              ) : null}
              {message ? (
                <p
                  className="break-words rounded-sm border border-success bg-success-bg p-3 text-body-sm text-success"
                  role="status"
                >
                  {message}
                </p>
              ) : null}
              {warnings.length ? (
                <div
                  className="rounded-sm border border-warning bg-warning-bg p-3 text-body-sm text-ink"
                  role="status"
                >
                  <p className="flex items-center gap-2 font-semibold text-warning">
                    <AlertTriangle aria-hidden="true" className="size-4 shrink-0" />
                    Cảnh báo cấp HSK
                  </p>
                  <ul className="mt-2 list-inside list-disc break-words">
                    {warnings.map((warning, index) => (
                      <li key={index}>{warning}</li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </section>
            <TopicPicker {...candidates} candidates />
          </div>
        ) : (
          <EmptyState
            title="Chọn chủ đề cần sửa"
            description="Chọn một chủ đề trong danh sách để xem và sửa tiên quyết."
          />
        )}
      </div>
    </main>
  )
}
