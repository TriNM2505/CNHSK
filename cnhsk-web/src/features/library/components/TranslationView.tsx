import {
  ArrowRight,
  ArrowRightLeft,
  BookOpenText,
  CheckCircle2,
  Languages,
  Search,
  Sparkles,
} from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageBanner,
  Select,
  Skeleton,
  Textarea,
} from '@/shared/ui'
import './LibraryView.css'

import type { TranslateRequest, TranslateResult } from '../types'

export interface TranslationViewProps {
  draft: TranslateRequest
  pending: boolean
  result?: TranslateResult
  error?: ApiError
  validation?: string
  onTextChange: (text: string) => void
  onDirectionChange: (from: TranslateRequest['from']) => void
  onSubmit: () => void
  onOpenLexeme: (id: number) => void
  popup?: ReactNode
}

export function TranslationView({
  draft,
  pending,
  result,
  error,
  validation,
  onTextChange,
  onDirectionChange,
  onSubmit,
  onOpenLexeme,
  popup,
}: TranslationViewProps) {
  const count = Array.from(draft.text).length
  const invalid = !draft.text.trim() || count > 2000
  const inputError = validation ?? (error?.status === 422 ? error.message : undefined)
  const insufficient = error?.status === 402 && error.errorCode === 'INSUFFICIENT_CREDITS'
  const unavailable = error?.status === 503 && error.errorCode === 'TRANSLATE_UNAVAILABLE'
  return (
    <div className="library-screen mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner title="Dịch câu" subtitle="Dịch giữa tiếng Trung và tiếng Việt" />
      <div className="mx-auto w-full min-w-0 max-w-reading space-y-6">
        <Card className="overflow-hidden border-brand-100 p-0">
          <div className="relative isolate overflow-hidden bg-brand-50 px-5 py-6 sm:px-6">
            <div className="relative z-base flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
                <Languages aria-hidden="true" className="size-5" />
              </span>
              <div className="min-w-0 space-y-1">
                <h2 className="text-h3 text-brand-800">Hiểu câu chữ, nối nhịp ngôn ngữ</h2>
                <p className="text-body-sm text-ink-secondary">
                  Dịch nội dung, đọc pinyin và tra từng từ trong ngữ cảnh.
                </p>
              </div>
            </div>
          </div>
          <form
            aria-label="Dịch câu"
            aria-busy={pending}
            className="min-w-0 space-y-5 p-5 sm:p-6"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              onSubmit()
            }}
          >
            <FormField
              htmlFor="translation-text"
              label="Nội dung cần dịch"
              required
              hint={`${count}/2.000 ký tự`}
              error={inputError}
            >
              <Textarea
                className={
                  draft.from === 'ZH' ? 'min-h-48 resize-y font-hanzi' : 'min-h-48 resize-y'
                }
                value={draft.text}
                disabled={pending}
                rows={6}
                onChange={(event) => onTextChange(event.target.value)}
              />
            </FormField>
            <div className="flex flex-col items-stretch gap-3 sm:flex-row sm:items-end">
              <FormField
                className="mb-0 min-w-0 flex-1"
                htmlFor="translation-direction"
                label="Hướng dịch"
              >
                <Select
                  value={draft.from}
                  disabled={pending}
                  onChange={(event) => onDirectionChange(event.target.value === 'VI' ? 'VI' : 'ZH')}
                >
                  <option value="ZH">Tiếng Trung → Tiếng Việt</option>
                  <option value="VI">Tiếng Việt → Tiếng Trung</option>
                </Select>
              </FormField>
              <Button
                disabled={pending}
                variant="outline"
                onClick={() => onDirectionChange(draft.to)}
              >
                <ArrowRightLeft
                  aria-hidden="true"
                  className="mr-2 inline-block size-4 align-middle"
                />
                Đổi hướng
              </Button>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-5">
              <p className="inline-flex items-center gap-2 text-body-sm text-ink-secondary">
                <BookOpenText aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                Tối đa 2.000 ký tự mỗi lần dịch.
              </p>
              <Button
                className="w-full rounded-pill sm:w-auto sm:min-w-40"
                type="submit"
                disabled={invalid || pending}
                loading={pending}
              >
                {pending ? 'Đang dịch…' : 'Dịch'}
                {!pending ? (
                  <ArrowRight
                    aria-hidden="true"
                    className="ml-2 inline-block size-4 align-middle"
                  />
                ) : null}
              </Button>
            </div>
          </form>
        </Card>
        {insufficient ? (
          <div
            role="alert"
            className="space-y-3 rounded-md border border-warning bg-warning-bg p-5 text-ink"
          >
            <p>Bạn đã hết lượt dịch và không đủ điểm.</p>
            <Button to="/account/credits" variant="outline">
              Xem gói dịch vụ và điểm
            </Button>
          </div>
        ) : unavailable ? (
          <p
            role="alert"
            className="rounded-md border border-danger bg-danger-bg p-5 text-body text-danger"
          >
            Dịch tạm thời lỗi — lượt đã được hoàn
          </p>
        ) : error && error.status !== 422 ? (
          <ErrorState message={error.message} requestId={error.requestId} />
        ) : null}
        {pending ? (
          <Card className="space-y-4 border-brand-100 p-5 sm:p-6">
            <p className="flex items-center gap-2 text-body text-ink" role="status">
              <Languages aria-hidden="true" className="size-5 text-brand-600" />
              Đang dịch nội dung…
            </p>
            <div aria-hidden="true" className="space-y-3">
              <Skeleton className="w-full" />
              <Skeleton className="w-4/5" />
              <Skeleton className="w-3/5" />
            </div>
          </Card>
        ) : null}
        {!pending && !result && !error ? (
          <EmptyState
            className="border-brand-100"
            icon={<Languages aria-hidden="true" className="size-5" />}
            title="Chưa có bản dịch"
            description="Nhập nội dung và bấm Dịch để xem kết quả."
          />
        ) : null}
        {result ? (
          <section
            aria-label="Kết quả dịch"
            className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-surface p-5 sm:p-6"
          >
            <div className="relative z-base space-y-5">
              <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                <Sparkles aria-hidden="true" className="size-5 text-brand-600" />
                Bản dịch
              </h2>
              <p
                className={
                  draft.to === 'ZH'
                    ? 'whitespace-pre-wrap break-words font-hanzi text-h3 text-ink'
                    : 'whitespace-pre-wrap break-words text-body text-ink'
                }
                lang={draft.to === 'ZH' ? 'zh-CN' : 'vi'}
              >
                {result.translated}
              </p>
              {result.pinyin ? (
                <div>
                  <h3 className="text-label text-ink">Pinyin của nội dung tiếng Trung</h3>
                  <p className="mt-2 whitespace-pre-wrap break-words text-body text-brand-700">
                    {result.pinyin}
                  </p>
                </div>
              ) : null}
              {result.segments.length ? (
                <div className="space-y-3 border-t border-brand-100 pt-5">
                  <h3 className="inline-flex items-center gap-2 text-label text-ink">
                    <Search aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                    {draft.from === 'ZH'
                      ? 'Tra từ trong nội dung tiếng Trung'
                      : 'Tra từ trong bản dịch tiếng Trung'}
                  </h3>
                  <p
                    className="whitespace-pre-wrap break-words font-hanzi text-h3 text-ink"
                    lang="zh-CN"
                  >
                    {result.segments.map((segment, index) =>
                      segment.lexemeId != null ? (
                        <Button
                          key={index}
                          type="button"
                          className="min-h-touch min-w-touch rounded-sm px-1 align-baseline font-hanzi text-h3 text-brand-700 underline"
                          variant="ghost"
                          aria-label={`Tra từ ${segment.text}`}
                          onClick={() => onOpenLexeme(segment.lexemeId!)}
                        >
                          {segment.text}
                        </Button>
                      ) : (
                        <span key={index}>{segment.text}</span>
                      ),
                    )}
                  </p>
                </div>
              ) : null}
              {result.cached ? (
                <p className="flex items-start gap-2 text-body-sm text-success" role="status">
                  <CheckCircle2 aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  Từ bộ nhớ đệm — không trừ lượt
                </p>
              ) : result.consumption === 'QUOTA' ? (
                <p className="text-body-sm text-ink-secondary" role="status">
                  Đã dùng 1 lượt miễn phí.
                </p>
              ) : result.consumption === 'CREDIT' ? (
                <p className="text-body-sm text-ink-secondary" role="status">
                  Đã dùng điểm.
                </p>
              ) : null}
            </div>
          </section>
        ) : null}
        {popup}
      </div>
    </div>
  )
}
