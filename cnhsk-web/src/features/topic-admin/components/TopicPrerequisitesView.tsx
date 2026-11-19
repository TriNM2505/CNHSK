import { AlertTriangle, BookOpen, Check, GitBranch, Info, X } from 'lucide-react'

import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  LevelBadge,
  PageBanner,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  SUPPORTED_HSK_LEVELS,
} from '@/shared/ui'
import type { ApiQueryState } from '@/shared/api/useApiQuery'

import type { AdminTopic, AdminTopicPage, TopicFilters, TopicRef } from '../types'
import { cn } from '@/shared/lib/cn'
import { TopicLandscapeArt, TopicText } from './TopicPrerequisiteParts'
import './TopicPrerequisitesView.css'

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
      className="topic-picker min-w-0 space-y-4 rounded-md border border-brand-100 bg-surface p-4 sm:p-5"
    >
      <div className="flex items-center gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600">
          {candidates ? (
            <GitBranch aria-hidden="true" className="size-5" />
          ) : (
            <BookOpen aria-hidden="true" className="size-5" />
          )}
        </span>
        <h2 className="text-h3">{candidates ? 'Thêm chủ đề tiên quyết' : 'Chủ đề cần sửa'}</h2>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <FormField className="mb-0" htmlFor={`${prefix}-search`} label="Tìm theo tên">
          <Input
            disabled={pending}
            maxLength={100}
            onChange={(e) => onFilter({ ...filters, q: e.target.value, page: 0 })}
            type="search"
            value={filters.q}
          />
        </FormField>
        <FormField className="mb-0" htmlFor={`${prefix}-hsk`} label="Cấp HSK">
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
        <Skeleton className="h-40" />
      ) : state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không thể tải chủ đề.'}
          onRetry={onRetry}
          requestId={error?.requestId}
        />
      ) : !data?.items.length ? (
        <EmptyState title="Không có chủ đề phù hợp" description="Thử thay đổi tên hoặc cấp HSK." />
      ) : !candidates ? (
        <table className="block w-full border-collapse text-body-sm lg:table">
          <caption className="sr-only">Danh sách chủ đề</caption>
          <thead className="hidden bg-surface-subtle lg:table-header-group">
            <tr>
              {['Chủ đề', 'Cấp HSK', 'Tiên quyết'].map((label) => (
                <th
                  className="border-b border-line p-3 text-left text-label"
                  key={label}
                  scope="col"
                >
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="block space-y-3 lg:table-row-group lg:space-y-0">
            {data.items.map((topic) => (
              <tr
                className="block rounded-md border border-line p-3 lg:table-row lg:rounded-none lg:border-x-0 lg:border-t-0 lg:p-0"
                key={topic.id}
              >
                <td className="block lg:table-cell lg:p-3">
                  <Button
                    variant="ghost"
                    className={cn(
                      'w-full min-w-0 justify-start whitespace-normal px-2 py-2 text-left',
                      selected?.id === topic.id &&
                        'bg-brand-50 text-brand-700 ring-1 ring-brand-600',
                    )}
                    aria-pressed={selected?.id === topic.id}
                    disabled={pending}
                    onClick={() => onSelect(topic)}
                  >
                    <TopicText text={topic.name} />
                  </Button>
                </td>
                <td className="inline-block px-2 py-2 lg:table-cell lg:p-3">
                  <LevelBadge exact level={topic.hskLevel} />
                </td>
                <td className="inline-block px-2 py-2 lg:table-cell lg:p-3">
                  <span className={topic.isRoot ? 'text-success' : 'text-ink-secondary'}>
                    {topic.isRoot ? 'Chủ đề gốc' : topic.prereqs.length + ' tiên quyết'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <ul className="space-y-2">
          {data.items.map((topic) => {
            const checked = selectedIds.includes(topic.id)
            return (
              <li key={topic.id}>
                <label
                  className={cn(
                    'flex min-h-touch items-start gap-3 rounded-md border p-3 transition-colors motion-reduce:transition-none',
                    checked
                      ? 'border-brand-600 bg-brand-50'
                      : 'border-line hover:border-brand-600 hover:bg-surface-subtle',
                  )}
                >
                  <Input
                    checked={checked}
                    className="mt-1 size-5 min-h-0 w-5 shrink-0 p-0 accent-brand-600"
                    disabled={
                      pending || topic.id === selected?.id || (!checked && selectedIds.length >= 10)
                    }
                    onChange={() => onSelect(topic)}
                    type="checkbox"
                  />
                  <span className="min-w-0 flex-1 break-words">
                    <TopicText text={topic.name} />
                    <span className="mt-1 block text-caption text-ink-secondary">
                      HSK {topic.hskLevel}
                      {topic.id === selected?.id ? ' · Chủ đề đang sửa' : ''}
                    </span>
                  </span>
                  {checked ? (
                    <Check aria-hidden="true" className="mt-1 size-4 shrink-0 text-brand-600" />
                  ) : null}
                </label>
              </li>
            )
          })}
        </ul>
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
    <main className="topic-prerequisites mx-auto w-full min-w-0 max-w-content space-y-4 px-4 py-6 pb-24 text-ink sm:px-6 lg:px-8">
      <PageBanner
        title="Tiên quyết chủ đề"
        subtitle="Thiết lập điều kiện mở chủ đề cho người học."
      />
      <div className="flex items-start gap-3 rounded-md border border-brand-100 bg-surface p-4 text-body-sm">
        <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-brand-600" />
        <p>Đây là tiên quyết chủ đề (cổng 90%), khác tiên quyết KID của cây tri thức</p>
      </div>
      {!selected && error ? (
        <p className="break-words text-danger" role="alert">
          <TopicText text={error.message} />
        </p>
      ) : null}
      <div className="grid min-w-0 items-start gap-4 lg:grid-cols-2">
        <TopicPicker {...topics} />
        {selected ? (
          <div className="min-w-0 space-y-4">
            <section
              aria-label="Tiên quyết đã chọn"
              className="topic-selection relative isolate space-y-4 overflow-hidden rounded-md border border-brand-100 bg-surface p-4 shadow-sm sm:p-5"
            >
              <TopicLandscapeArt />
              <div className="relative space-y-4">
                <div className="space-y-2">
                  <StatusBadge tone="info">Đang chỉnh sửa</StatusBadge>
                  <h2 className="break-words text-h3">
                    <TopicText text={selected.name} />
                  </h2>
                  <p className="text-body-sm text-ink-secondary">
                    {chosen.length}/10 tiên quyết ·{' '}
                    {selected.isRoot ? 'Hiện là chủ đề gốc' : 'Hiện có tiên quyết'}
                  </p>
                </div>
                {chosen.length ? (
                  <ul className="space-y-2">
                    {chosen.map((topic) => (
                      <li
                        className="flex min-w-0 items-center justify-between gap-3 rounded-md border border-brand-100 bg-surface/90 p-3"
                        key={topic.id}
                      >
                        <span className="min-w-0 break-words">
                          <TopicText text={topic.name} />{' '}
                          <span className="text-caption">(HSK {topic.hskLevel})</span>
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
                  <p className="text-body-sm text-ink-secondary">
                    Không có tiên quyết. Lưu để đặt thành chủ đề gốc.
                  </p>
                )}
                <Button className="w-full" disabled={saving || saveDisabled} onClick={onSave}>
                  <span className="flex items-center gap-2">
                    <GitBranch aria-hidden="true" className="size-4 shrink-0" />
                    {saving ? 'Đang lưu…' : 'Lưu tiên quyết'}
                  </span>
                </Button>
                {error ? (
                  <div className="break-words text-danger" role="alert">
                    <p>
                      <TopicText text={error.message} />
                    </p>
                    {cycleNames?.length ? (
                      <p className="mt-2">
                        Đường vòng: <TopicText text={cycleNames.join(' → ')} />
                      </p>
                    ) : null}
                    {error.requestId ? (
                      <p className="text-caption">Mã yêu cầu: {error.requestId}</p>
                    ) : null}
                  </div>
                ) : null}
                {message ? (
                  <p className="break-words text-success" role="status">
                    {message}
                  </p>
                ) : null}
                {warnings.length ? (
                  <div className="rounded-sm bg-warning-bg p-3" role="status">
                    <p className="flex items-center gap-2 font-semibold">
                      <AlertTriangle aria-hidden="true" className="size-4 shrink-0" />
                      Cảnh báo cấp HSK
                    </p>
                    <ul className="mt-2 list-inside list-disc break-words">
                      {warnings.map((warning, index) => (
                        <li key={index}>
                          <TopicText text={warning} />
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            </section>
            <TopicPicker {...candidates} candidates />
          </div>
        ) : (
          <Card className="relative isolate overflow-hidden border-brand-100 p-5 sm:p-6">
            <TopicLandscapeArt />
            <div className="relative">
              <EmptyState
                title="Chọn chủ đề cần sửa"
                description="Chọn một chủ đề trong danh sách để xem và sửa tiên quyết."
              />
            </div>
          </Card>
        )}
      </div>
    </main>
  )
}
