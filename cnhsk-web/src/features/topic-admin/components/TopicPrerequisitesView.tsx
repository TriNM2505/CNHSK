import { AlertTriangle, GitBranch, X } from 'lucide-react'

import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  LevelBadge,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
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
      className="min-w-0 space-y-4 rounded-md border border-line bg-surface p-4"
    >
      <h2 className="text-h2">{candidates ? 'Thêm chủ đề tiên quyết' : 'Chủ đề cần sửa'}</h2>
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
        <Skeleton className="h-40" />
      ) : state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không thể tải chủ đề.'}
          onRetry={onRetry}
          requestId={error?.requestId}
        />
      ) : !data?.items.length ? (
        <EmptyState title="Không có chủ đề phù hợp" description="Thử thay đổi tên hoặc cấp HSK." />
      ) : (
        <ul className="space-y-2">
          {data.items.map((topic) => {
            const checked = selectedIds.includes(topic.id)
            return (
              <li key={topic.id}>
                {candidates ? (
                  <label className="flex min-h-touch items-start gap-3 rounded-sm border border-line p-3">
                    <input
                      checked={checked}
                      className="mt-1 size-4 shrink-0"
                      disabled={
                        pending ||
                        topic.id === selected?.id ||
                        (!checked && selectedIds.length >= 10)
                      }
                      onChange={() => onSelect(topic)}
                      type="checkbox"
                    />
                    <span className="min-w-0 flex-1 break-words">
                      {topic.name}
                      <span className="block text-caption text-ink-muted">
                        HSK {topic.hskLevel}
                        {topic.id === selected?.id ? ' · Chủ đề đang sửa' : ''}
                      </span>
                    </span>
                  </label>
                ) : (
                  <button
                    aria-pressed={selected?.id === topic.id}
                    className="flex min-h-touch w-full items-start justify-between gap-3 rounded-sm border border-line p-3 text-left hover:bg-brand-50 aria-pressed:border-brand-600 aria-pressed:bg-brand-50"
                    disabled={pending}
                    onClick={() => onSelect(topic)}
                    type="button"
                  >
                    <span className="min-w-0 break-words">
                      {topic.name}
                      <span className="block text-caption text-ink-muted">
                        {topic.isRoot ? 'Chủ đề gốc' : `${topic.prereqs.length} tiên quyết`}
                      </span>
                    </span>
                    <LevelBadge exact level={topic.hskLevel} />
                  </button>
                )}
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
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 text-ink [overflow-wrap:anywhere]">
      <PageHeader
        title="Tiên quyết chủ đề"
        subtitle="Thiết lập điều kiện mở chủ đề cho người học."
      />
      <p className="rounded-md border border-line bg-surface p-4 text-body-sm">
        Đây là tiên quyết chủ đề (cổng 90%), khác tiên quyết KID của cây tri thức
      </p>
      {!selected && error ? (
        <p className="break-words text-danger" role="alert">
          {error.message}
        </p>
      ) : null}
      <div className="grid min-w-0 gap-5 xl:grid-cols-2">
        <TopicPicker {...topics} />
        {selected ? (
          <div className="min-w-0 space-y-4">
            <section
              aria-label="Tiên quyết đã chọn"
              className="space-y-3 rounded-md border border-line bg-surface p-4"
            >
              <h2 className="break-words text-h2">{selected.name}</h2>
              <p className="text-body-sm text-ink-secondary">
                {chosen.length}/10 tiên quyết ·{' '}
                {selected.isRoot ? 'Hiện là chủ đề gốc' : 'Hiện có tiên quyết'}
              </p>
              {chosen.length ? (
                <ul className="space-y-2">
                  {chosen.map((topic) => (
                    <li className="flex min-w-0 items-center justify-between gap-2" key={topic.id}>
                      <span className="min-w-0 break-words">
                        {topic.name} <span className="text-caption">(HSK {topic.hskLevel})</span>
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
              <Button disabled={saving || saveDisabled} onClick={onSave}>
                <span className="flex items-center gap-2">
                  <GitBranch aria-hidden="true" className="size-4 shrink-0" />
                  {saving ? 'Đang lưu…' : 'Lưu tiên quyết'}
                </span>
              </Button>
              {error ? (
                <div className="break-words text-danger" role="alert">
                  <p>{error.message}</p>
                  {cycleNames?.length ? (
                    <p className="mt-2">Đường vòng: {cycleNames.join(' → ')}</p>
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
