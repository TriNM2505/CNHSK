import { ChevronRight, Search } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  type PageStateValue,
} from '@/shared/ui'

import { dictionaryDetailHref, dictionaryModes, type DictionarySearch } from '../dictionarySearch'
import type { RadicalCount, SearchResult } from '../types'

export interface DictionarySearchViewProps {
  draft: DictionarySearch
  onChange: (search: DictionarySearch) => void
  onSubmit: () => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onRadicalsRetry: () => void
  state: PageStateValue
  result?: SearchResult
  error?: ApiError
  formError?: string
  radicals: readonly RadicalCount[]
  radicalsError?: ApiError
  searched: boolean
  page: number
  returnTo: string
  loginTo: string
  canTranslate: boolean
  guest: boolean
}

export function DictionarySearchView({
  draft,
  onChange,
  onSubmit,
  onPageChange,
  onRetry,
  onRadicalsRetry,
  state,
  result,
  error,
  formError,
  radicals,
  radicalsError,
  searched,
  page,
  returnTo,
  loginTo,
  canTranslate,
  guest,
}: DictionarySearchViewProps) {
  const retryAfter = error?.extra.retryAfterSeconds
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-20 sm:px-6">
      <PageHeader
        title="Tra từ điển"
        subtitle="Tra chữ Hán, pinyin, nghĩa Việt hoặc bộ thủ và số nét."
      />
      <form
        className="space-y-4 rounded-md border border-line bg-surface p-5 sm:p-6"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField htmlFor="dictionary-type" label="Kiểu tra">
            <Select
              value={draft.type}
              onChange={(event) =>
                onChange({ ...draft, type: event.target.value as DictionarySearch['type'] })
              }
            >
              {dictionaryModes.map(({ value, label }) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>
          {draft.type === 'RADICAL' ? (
            <>
              <FormField htmlFor="dictionary-radical" label="Bộ thủ">
                <Select
                  value={draft.radical}
                  onChange={(event) => onChange({ ...draft, radical: event.target.value })}
                >
                  <option value="">Tất cả bộ thủ</option>
                  {draft.radical && !radicals.some(({ radical }) => radical === draft.radical) ? (
                    <option value={draft.radical}>{draft.radical}</option>
                  ) : null}
                  {radicals.map(({ radical, charCount }) => (
                    <option key={radical} value={radical}>
                      {radical} ({charCount} chữ)
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField
                htmlFor="dictionary-strokes"
                label="Số nét"
                hint="Từ 1 đến 64; có thể chỉ chọn một trong hai bộ lọc."
              >
                <Input
                  inputMode="numeric"
                  value={draft.strokes}
                  onChange={(event) => onChange({ ...draft, strokes: event.target.value })}
                />
              </FormField>
            </>
          ) : (
            <div className="sm:col-span-2">
              <FormField
                htmlFor="dictionary-query"
                label="Nội dung cần tra"
                error={formError}
                required
              >
                <Input
                  autoComplete="off"
                  value={draft.q}
                  onChange={(event) => onChange({ ...draft, q: event.target.value })}
                />
              </FormField>
            </div>
          )}
        </div>
        {draft.type === 'RADICAL' && formError ? (
          <p className="text-body-sm text-danger" role="alert">
            {formError}
          </p>
        ) : null}
        <div>
          <Button type="submit">
            <span className="inline-flex items-center gap-2">
              <Search aria-hidden="true" className="size-4 shrink-0" />
              Tra cứu
            </span>
          </Button>
        </div>
      </form>

      {draft.type === 'RADICAL' && radicalsError ? (
        <ErrorState
          title="Chưa tải được danh sách bộ thủ"
          message={radicalsError.message}
          requestId={radicalsError.requestId}
          onRetry={onRadicalsRetry}
        />
      ) : null}
      {!searched ? (
        <Card className="bg-surface-subtle p-6 text-center">
          <p className="text-body text-ink-secondary">Nhập nội dung rồi bấm Tra cứu để bắt đầu.</p>
        </Card>
      ) : null}
      {searched && state === 'loading' ? (
        <div
          aria-label="Đang tra từ điển"
          className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
          role="status"
        >
          {[0, 1, 2].map((item) => (
            <Skeleton key={item} variant="card" />
          ))}
        </div>
      ) : null}
      {searched && state === 'error' ? (
        <ErrorState
          title="Chưa thể tra từ điển"
          requestId={error?.requestId}
          onRetry={onRetry}
          message={
            <div className="space-y-2">
              <p>
                {error?.status === 429
                  ? 'Bạn đã tra cứu quá giới hạn. Vui lòng thử lại sau.'
                  : (error?.message ?? 'Vui lòng thử lại sau.')}
              </p>
              {error?.status === 429 && typeof retryAfter === 'number' ? (
                <p>Thử lại sau {retryAfter} giây.</p>
              ) : null}
              {error?.status === 429 && guest ? (
                <Button to={loginTo} variant="outline">
                  Đăng nhập để tra tiếp
                </Button>
              ) : null}
            </div>
          }
        />
      ) : null}
      {searched && (state === 'ready' || state === 'empty') && result ? (
        <section aria-label="Kết quả tra cứu" className="space-y-4" aria-live="polite">
          {result.items.length ? (
            <>
              <p className="text-body-sm font-medium text-ink-secondary">{result.total} kết quả</p>
              <ul className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {result.items.map((entry) => (
                  <li key={entry.id} className="min-w-0">
                    <Link
                      className="group block h-full min-w-0 rounded-md no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                      to={dictionaryDetailHref(entry.id, returnTo)}
                    >
                      <Card
                        className="flex h-full min-w-0 flex-col justify-between gap-3 p-5"
                        variant="interactive"
                      >
                        <div className="min-w-0 space-y-2">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <HanziText className="text-ink" size="sm">
                              {entry.text}
                            </HanziText>
                            <div className="flex flex-wrap items-center gap-2">
                              {entry.hskLevel != null ? (
                                <span className="rounded-pill border border-line bg-surface-subtle px-2.5 py-0.5 text-caption font-semibold text-ink-secondary">
                                  HSK {entry.hskLevel}
                                </span>
                              ) : null}
                              {entry.approximate ? (
                                <StatusBadge tone="warning">Số nét gần đúng</StatusBadge>
                              ) : null}
                            </div>
                          </div>
                          {entry.pinyin ? (
                            <p className="break-words text-body font-medium text-brand-700">
                              {entry.pinyin}
                            </p>
                          ) : null}
                          {entry.sinoViet ? (
                            <p className="break-words text-body-sm text-ink-secondary">
                              Hán Việt: {entry.sinoViet}
                            </p>
                          ) : null}
                          <p className="break-words text-body text-ink">{entry.meaningVi}</p>
                        </div>
                        <div className="flex items-center justify-end border-t border-line pt-2">
                          <ChevronRight
                            aria-hidden="true"
                            className="size-4 text-ink-muted transition-colors group-hover:text-brand-700"
                          />
                        </div>
                      </Card>
                    </Link>
                  </li>
                ))}
              </ul>
              <Pagination
                page={page + 1}
                size={20}
                total={result.total}
                onChange={(next) => onPageChange(next - 1)}
              />
            </>
          ) : (
            <EmptyState
              title="Không tìm thấy — thử kiểu tra khác"
              description="Kiểm tra lại chữ Hán, pinyin, nghĩa Việt hoặc đổi kiểu tra cứu."
            />
          )}
          {result.hint === 'USE_TRANSLATE' && canTranslate ? (
            <Button to="/translate" variant="outline">
              Chuyển sang dịch câu
            </Button>
          ) : null}
        </section>
      ) : null}
    </main>
  )
}
