import { Search } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { ApiError } from '@/shared/api'
import {
  Button,
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
    <div className="mx-auto max-w-reading space-y-6">
      <PageHeader
        title="Tra từ điển"
        subtitle="Tra chữ Hán, pinyin, nghĩa Việt hoặc bộ thủ và số nét."
      />
      <form
        className="rounded-md border border-line bg-surface p-4 sm:p-6"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
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
          <div className="grid gap-4 sm:grid-cols-2">
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
          </div>
        ) : (
          <FormField htmlFor="dictionary-query" label="Nội dung cần tra" error={formError} required>
            <Input
              autoComplete="off"
              value={draft.q}
              onChange={(event) => onChange({ ...draft, q: event.target.value })}
            />
          </FormField>
        )}
        {draft.type === 'RADICAL' && formError ? (
          <p className="mb-3 text-body-sm text-danger" role="alert">
            {formError}
          </p>
        ) : null}
        <Button type="submit">
          <Search aria-hidden="true" className="size-4" />
          Tra cứu
        </Button>
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
        <p className="text-body text-ink-secondary">Nhập nội dung rồi bấm Tra cứu để bắt đầu.</p>
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
              <p className="text-body-sm text-ink-secondary">{result.total} kết quả</p>
              <ul className="divide-y divide-line rounded-md border border-line bg-surface">
                {result.items.map((entry) => (
                  <li key={entry.id}>
                    <Link
                      className="block space-y-2 p-4 no-underline hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                      to={dictionaryDetailHref(entry.id, returnTo)}
                    >
                      <div className="flex flex-wrap items-center gap-3">
                        <HanziText>{entry.text}</HanziText>
                        {entry.hskLevel != null ? (
                          <span className="text-caption text-ink-secondary">
                            HSK {entry.hskLevel}
                          </span>
                        ) : null}
                        {entry.approximate ? (
                          <StatusBadge tone="warning">Số nét gần đúng</StatusBadge>
                        ) : null}
                      </div>
                      {entry.pinyin ? (
                        <p className="text-body text-brand-700">{entry.pinyin}</p>
                      ) : null}
                      {entry.sinoViet ? (
                        <p className="text-body-sm text-ink-secondary">
                          Hán Việt: {entry.sinoViet}
                        </p>
                      ) : null}
                      <p className="text-body text-ink">{entry.meaningVi}</p>
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
            <p className="rounded-md border border-dashed border-line p-6 text-body text-ink-secondary">
              Không tìm thấy — thử kiểu tra khác
            </p>
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
