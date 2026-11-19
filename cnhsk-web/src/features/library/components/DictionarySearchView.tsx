import { ArrowRight, BookOpenText, Info, Search, Sparkles } from 'lucide-react'
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
  LevelBadge,
  PageBanner,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  type HskLevel,
  type PageStateValue,
} from '@/shared/ui'
import './LibraryView.css'

import { dictionaryDetailHref, dictionaryModes, type DictionarySearch } from '../dictionarySearch'
import type { RadicalCount, SearchResult } from '../types'
import dictionaryPanda from './assets/cnhsk-panda-dictionary.webp'

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
    <div className="library-screen mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner
        title="Tra từ điển"
        subtitle="Tra chữ Hán, pinyin, nghĩa Việt hoặc bộ thủ và số nét."
      />

      <Card className="relative isolate overflow-hidden border-brand-100 p-0">
        <div className="relative z-base grid min-w-0 items-center gap-6 p-5 sm:p-6 lg:grid-cols-3">
          <form
            aria-label="Tra từ điển"
            className="min-w-0 space-y-5 lg:col-span-2"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              onSubmit()
            }}
          >
            <div className="flex items-center gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600">
                <Search aria-hidden="true" className="size-5" />
              </span>
              <div className="min-w-0">
                <h2 className="text-h3 text-ink">Khám phá từng từ mới</h2>
                <p className="text-body-sm text-ink-secondary">Chọn cách tra phù hợp với bạn.</p>
              </div>
            </div>
            <div className="grid min-w-0 gap-x-4 sm:grid-cols-3">
              <FormField className="sm:col-span-1" htmlFor="dictionary-type" label="Kiểu tra">
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
                <div className="grid min-w-0 gap-x-4 sm:col-span-2 sm:grid-cols-2">
                  <FormField htmlFor="dictionary-radical" label="Bộ thủ">
                    <Select
                      className="font-hanzi"
                      value={draft.radical}
                      onChange={(event) => onChange({ ...draft, radical: event.target.value })}
                    >
                      <option value="">Tất cả bộ thủ</option>
                      {draft.radical &&
                      !radicals.some(({ radical }) => radical === draft.radical) ? (
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
                </div>
              ) : (
                <FormField
                  className="sm:col-span-2"
                  htmlFor="dictionary-query"
                  label="Nội dung cần tra"
                  error={formError}
                  required
                >
                  <Input
                    autoComplete="off"
                    className={draft.type === 'HANZI' ? 'font-hanzi' : undefined}
                    value={draft.q}
                    onChange={(event) => onChange({ ...draft, q: event.target.value })}
                  />
                </FormField>
              )}
            </div>
            {draft.type === 'RADICAL' && formError ? (
              <p className="mb-3 text-body-sm text-danger" role="alert">
                {formError}
              </p>
            ) : null}
            <Button className="w-full rounded-pill sm:w-auto sm:min-w-40" type="submit">
              <Search aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
              Tra cứu
              <ArrowRight aria-hidden="true" className="ml-2 inline-block size-4 align-middle" />
            </Button>
          </form>
          <aside
            aria-label="Gợi ý tra cứu"
            className="flex min-w-0 items-center gap-4 lg:flex-col lg:text-center"
          >
            <img
              alt=""
              aria-hidden="true"
              className="size-24 shrink-0 object-contain lg:size-36"
              decoding="async"
              height={320}
              src={dictionaryPanda}
              width={320}
            />
            <div className="min-w-0 space-y-2">
              <p className="text-label text-brand-800">Tò mò một chút, hiểu thêm một từ.</p>
              <p className="text-body-sm text-ink-secondary">
                {draft.type === 'RADICAL'
                  ? 'Chưa biết cách đọc? Thử tìm qua bộ thủ hoặc số nét của chữ.'
                  : 'Xem pinyin, nghĩa tiếng Việt rồi mở mục từ để tìm hiểu thêm.'}
              </p>
            </div>
          </aside>
        </div>
      </Card>

      {guest ? (
        <div className="flex items-start gap-3 rounded-md border border-brand-100 bg-brand-50 p-4 text-body-sm text-ink-secondary">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-600" />
          <p>
            Bạn đang tra cứu với tư cách khách. Khi chạm hạn mức, bạn có thể đăng nhập để tra tiếp.
          </p>
        </div>
      ) : null}

      {draft.type === 'RADICAL' && radicalsError ? (
        <ErrorState
          title="Chưa tải được danh sách bộ thủ"
          message={radicalsError.message}
          requestId={radicalsError.requestId}
          onRetry={onRadicalsRetry}
        />
      ) : null}
      {!searched ? (
        <EmptyState
          description="Nhập nội dung rồi bấm Tra cứu để bắt đầu."
          icon={<BookOpenText aria-hidden="true" className="size-5" />}
          title="Bạn muốn khám phá từ nào hôm nay?"
        />
      ) : null}
      {searched && state === 'loading' ? (
        <div aria-label="Đang tra từ điển" className="space-y-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
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
                <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                  <Sparkles aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
                  Kết quả tra cứu
                </h2>
                <p className="text-body-sm text-ink-secondary">{result.total} kết quả</p>
              </div>
              <ul className="space-y-4">
                {result.items.map((entry) => (
                  <li key={entry.id}>
                    <Card className="relative isolate overflow-hidden border-brand-100 p-0 shadow-sm">
                      <Link
                        className="relative z-base flex min-w-0 flex-col gap-4 rounded-md p-5 no-underline transition-colors hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus sm:flex-row sm:items-center sm:gap-6 sm:px-6"
                        to={dictionaryDetailHref(entry.id, returnTo)}
                      >
                        <div className="min-w-0 space-y-2 sm:w-40 sm:shrink-0">
                          <HanziText className="block break-words text-brand-800">
                            {entry.text}
                          </HanziText>
                          {entry.pinyin ? (
                            <p className="break-words text-body font-medium text-brand-700">
                              {entry.pinyin}
                            </p>
                          ) : null}
                        </div>
                        <div className="min-w-0 flex-1 space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <StatusBadge tone="neutral">
                              {entry.kind === 'CHAR'
                                ? 'Chữ Hán'
                                : entry.kind === 'WORD'
                                  ? 'Từ vựng'
                                  : 'Ngữ pháp'}
                            </StatusBadge>
                            {entry.hskLevel != null ? (
                              <LevelBadge exact level={entry.hskLevel as HskLevel} />
                            ) : null}
                            {entry.approximate ? (
                              <StatusBadge tone="warning">Số nét gần đúng</StatusBadge>
                            ) : null}
                          </div>
                          <p className="break-words text-body font-medium text-ink">
                            {entry.meaningVi}
                          </p>
                          {entry.sinoViet ? (
                            <p className="text-body-sm text-ink-secondary">
                              Hán Việt: {entry.sinoViet}
                            </p>
                          ) : null}
                        </div>
                        <span className="inline-flex min-h-touch shrink-0 items-center gap-2 text-label text-brand-700">
                          Xem chi tiết
                          <ArrowRight aria-hidden="true" className="size-4" />
                        </span>
                      </Link>
                    </Card>
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
              description="Nội dung bạn nhập vẫn được giữ lại để chỉnh và tra lại."
              icon={<Search aria-hidden="true" className="size-5" />}
              title="Không tìm thấy — thử kiểu tra khác"
            />
          )}
          {result.hint === 'USE_TRANSLATE' && canTranslate ? (
            <Button to="/translate" variant="outline">
              Chuyển sang dịch câu
            </Button>
          ) : null}
        </section>
      ) : null}
    </div>
  )
}
