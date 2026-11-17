import { BookOpen, ChevronRight, Compass, Search, Sparkles } from 'lucide-react'
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

      <Card className="space-y-4 border-brand-100 bg-surface-subtle p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="inline-flex items-center gap-1.5 rounded-pill border border-brand-100 bg-surface px-3 py-1 text-caption font-semibold text-brand-700">
            <Sparkles aria-hidden="true" className="size-3.5 shrink-0" />
            <span>Từ điển Hán – Việt · HSK 1–9</span>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-caption text-ink-secondary">
            <span className="rounded-pill border border-line bg-surface px-2.5 py-0.5">
              214 bộ thủ
            </span>
            <span className="rounded-pill border border-line bg-surface px-2.5 py-0.5">
              Âm Hán Việt & Pinyin
            </span>
          </div>
        </div>

        <form
          className="space-y-4 rounded-md border border-line bg-surface p-4 shadow-sm sm:p-5"
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
          <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
            <Button type="submit">
              <span className="inline-flex items-center gap-2">
                <Search aria-hidden="true" className="size-4 shrink-0" />
                Tra cứu
              </span>
            </Button>
            {canTranslate ? (
              <Link
                className="inline-flex items-center gap-1.5 text-body-sm font-medium text-brand-700 underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                to="/translate"
              >
                <Compass aria-hidden="true" className="size-4 shrink-0" />
                <span>Cần dịch cả câu hoặc đoạn văn?</span>
              </Link>
            ) : null}
          </div>
        </form>
      </Card>

      {draft.type === 'RADICAL' && radicalsError ? (
        <ErrorState
          title="Chưa tải được danh sách bộ thủ"
          message={radicalsError.message}
          requestId={radicalsError.requestId}
          onRetry={onRadicalsRetry}
        />
      ) : null}
      {!searched ? (
        <Card className="space-y-3 bg-surface p-6 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-pill border border-brand-100 bg-brand-50 text-brand-700">
            <BookOpen aria-hidden="true" className="size-6" />
          </div>
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
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-body-sm font-semibold text-ink-secondary">
                  {result.total} kết quả
                </p>
              </div>
              <ul className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {result.items.map((entry) => (
                  <li key={entry.id} className="min-w-0">
                    <Link
                      className="group block h-full min-w-0 rounded-md no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                      to={dictionaryDetailHref(entry.id, returnTo)}
                    >
                      <Card
                        className="flex h-full min-w-0 flex-col justify-between gap-4 p-5"
                        variant="interactive"
                      >
                        <div className="min-w-0 space-y-3">
                          <div className="flex items-start gap-3.5">
                            <div className="flex min-h-14 min-w-14 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface-subtle px-3 py-2 shadow-sm">
                              <HanziText className="text-brand-800" size="sm">
                                {entry.text}
                              </HanziText>
                            </div>
                            <div className="min-w-0 flex-1 space-y-1">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                {entry.pinyin ? (
                                  <p className="break-words text-body font-semibold text-brand-700">
                                    {entry.pinyin}
                                  </p>
                                ) : (
                                  <span />
                                )}
                                <div className="flex flex-wrap items-center gap-1.5">
                                  {entry.hskLevel != null ? (
                                    <span className="rounded-pill border border-brand-100 bg-brand-50 px-2.5 py-0.5 text-caption font-semibold text-brand-700">
                                      HSK {entry.hskLevel}
                                    </span>
                                  ) : null}
                                  {entry.approximate ? (
                                    <StatusBadge tone="warning">Số nét gần đúng</StatusBadge>
                                  ) : null}
                                </div>
                              </div>
                              {entry.sinoViet ? (
                                <p className="break-words text-caption font-medium uppercase tracking-wide text-ink-secondary">
                                  Hán Việt: {entry.sinoViet}
                                </p>
                              ) : null}
                            </div>
                          </div>

                          <p className="break-words border-l-2 border-brand-100 pl-3 text-body text-ink">
                            {entry.meaningVi}
                          </p>
                        </div>

                        <div className="flex items-center justify-between border-t border-line pt-3">
                          <span className="text-caption font-medium text-ink-muted">
                            Chi tiết mục từ
                          </span>
                          <ChevronRight
                            aria-hidden="true"
                            className="size-4 text-ink-muted transition-transform group-hover:translate-x-0.5 group-hover:text-brand-700"
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
