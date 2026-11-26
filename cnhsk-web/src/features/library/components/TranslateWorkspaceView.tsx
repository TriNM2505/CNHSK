import { ArrowLeftRight, Languages, Search, Shapes, Type, X } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  Input,
  LevelBadge,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  type HskLevel,
  type PageStateValue,
} from '@/shared/ui'
import './LibraryView.css'

import { MAX_TRANSLATE_LENGTH, type DictionarySearch } from '../dictionarySearch'
import type { GuestTrial, RadicalCount, SearchResult } from '../types'
import { GuestTrialNotice } from './GuestTrialNotice'

type Mode = 'TEXT' | 'PINYIN' | 'RADICAL'
const modes: readonly { value: Mode; label: string; icon: typeof Type }[] = [
  { value: 'TEXT', label: 'Văn bản', icon: Type },
  { value: 'PINYIN', label: 'Pinyin', icon: Search },
  { value: 'RADICAL', label: 'Bộ thủ', icon: Shapes },
]
const languages = [
  { type: 'HANZI', label: 'Tiếng Trung' },
  { type: 'MEANING', label: 'Tiếng Việt' },
] as const

function modeOf(type: DictionarySearch['type']): Mode {
  return type === 'PINYIN' || type === 'RADICAL' ? type : 'TEXT'
}

function LanguageChips({
  active,
  label,
  onPick,
}: {
  active: 'HANZI' | 'MEANING'
  label: string
  onPick?: (type: 'HANZI' | 'MEANING') => void
}) {
  return (
    <div aria-label={label} className="flex min-w-0 flex-wrap items-center gap-2" role="group">
      {languages.map(({ type, label: name }) =>
        onPick ? (
          <button
            aria-pressed={active === type}
            className={`min-h-touch rounded-pill border px-4 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
              active === type
                ? 'border-brand-600 bg-brand-50 text-brand-800'
                : 'border-line bg-surface text-ink-secondary hover:border-brand-600 hover:text-ink'
            }`}
            key={type}
            onClick={() => onPick(type)}
            type="button"
          >
            {name}
          </button>
        ) : (
          <span
            className={`inline-flex min-h-touch items-center rounded-pill border px-4 text-label ${
              active === type ? 'border-brand-600 bg-surface text-brand-800' : 'border-transparent text-ink-muted'
            }`}
            key={type}
          >
            {name}
          </span>
        ),
      )}
    </div>
  )
}

export interface TranslateWorkspaceViewProps {
  draft: DictionarySearch
  onChange: (search: DictionarySearch) => void
  onSubmit: () => void
  /** Swaps the translation direction, carrying a finished translation over as the new source. */
  onSwap: () => void
  formError?: string
  /** Right-hand panel: the translation or the looked-up headword. */
  output: ReactNode
  /** Everything shown under the panels: the full dictionary entry, words of a sentence, … */
  details?: ReactNode
  /** Pinyin and radical lookups list their matches; text lookups do not. */
  listed: boolean
  searched: boolean
  state: PageStateValue
  result?: SearchResult
  error?: ApiError
  onRetry: () => void
  page: number
  onPageChange: (page: number) => void
  selectedId?: number
  onSelect: (id: number) => void
  radicals: readonly RadicalCount[]
  radicalsError?: ApiError
  onRadicalsRetry: () => void
  loginTo: string
  registerTo: string
  guest: boolean
  guestTrial?: GuestTrial
}

/** The merged "Dịch" screen: source and result side by side, the full entry or sentence words below. */
export function TranslateWorkspaceView(props: TranslateWorkspaceViewProps) {
  const { draft, onChange, onSubmit, formError, error, guest, guestTrial } = props
  const mode = modeOf(draft.type)
  const textType = draft.type === 'MEANING' ? 'MEANING' : 'HANZI'
  const retryAfter = error?.extra.retryAfterSeconds
  const trialRejected = guest && error?.extra.guestTrialExhausted === true
  const trialExhausted = trialRejected || (guest && guestTrial?.remaining === 0)
  const count = Array.from(draft.q).length
  const pickMode = (next: Mode) =>
    onChange({ ...draft, type: next === 'TEXT' ? textType : next, page: 0 })

  return (
    <div className="library-screen mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <header className="flex min-w-0 flex-wrap items-end justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h1 className="inline-flex items-center gap-3 text-h1 text-ink">
            <span aria-hidden="true" className="h-8 w-1.5 rounded-pill bg-brand-600" />
            Dịch
          </h1>
          <p className="text-body-sm text-ink-secondary">
            Một từ: nghĩa, pinyin, ví dụ ngay bên dưới. Cụm từ, câu: dịch bằng AI.
          </p>
        </div>
        <div aria-label="Cách dịch" className="flex flex-wrap gap-2" role="group">
          {modes.map(({ value, label, icon: Icon }) => (
            <button
              aria-pressed={mode === value}
              className={`inline-flex min-h-touch items-center gap-2 rounded-md px-4 text-label transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
                mode === value ? 'bg-brand-600 text-surface' : 'bg-surface text-ink-secondary hover:bg-brand-50 hover:text-ink'
              }`}
              disabled={trialExhausted}
              key={value}
              onClick={() => pickMode(value)}
              type="button"
            >
              <Icon aria-hidden="true" className="size-4" />
              {label}
            </button>
          ))}
        </div>
      </header>

      <form
        aria-label="Dịch và tra từ"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
        <fieldset className="m-0 min-w-0 border-0 p-0" disabled={trialExhausted}>
          {mode === 'TEXT' ? (
            <div className="relative grid min-w-0 gap-3 lg:grid-cols-2 lg:gap-4">
              <section
                aria-label="Nội dung nguồn"
                className="flex min-h-64 min-w-0 flex-col rounded-lg border border-line bg-surface shadow-sm"
              >
                <div className="border-b border-line px-4 py-3 sm:px-5">
                  <LanguageChips
                    active={textType}
                    label="Ngôn ngữ nguồn"
                    onPick={(type) => onChange({ ...draft, type, page: 0 })}
                  />
                </div>
                <label className="sr-only" htmlFor="translate-source">
                  Nội dung cần dịch
                </label>
                <textarea
                  aria-describedby={formError ? 'translate-source-error' : undefined}
                  aria-invalid={formError ? true : undefined}
                  autoComplete="off"
                  className={`min-h-40 w-full flex-1 resize-none border-0 bg-transparent px-4 py-4 text-ink outline-none placeholder:text-ink-muted sm:px-5 ${
                    textType === 'HANZI' ? 'font-hanzi text-h3' : 'text-h3'
                  }`}
                  id="translate-source"
                  onChange={(event) => onChange({ ...draft, q: event.target.value })}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
                      event.preventDefault()
                      onSubmit()
                    }
                  }}
                  placeholder={textType === 'HANZI' ? '输入中文…' : 'Nhập tiếng Việt…'}
                  value={draft.q}
                />
                {formError ? (
                  <p className="px-4 text-body-sm text-danger sm:px-5" id="translate-source-error" role="alert">
                    {formError}
                  </p>
                ) : null}
                <div className="flex items-center justify-between gap-3 px-4 py-3 sm:px-5">
                  <div className="flex items-center gap-2">
                    {draft.q ? (
                      <Button
                        aria-label="Xoá nội dung"
                        className="min-h-touch min-w-touch"
                        onClick={() => onChange({ ...draft, q: '' })}
                        size="sm"
                        variant="ghost"
                      >
                        <X aria-hidden="true" className="size-4" />
                      </Button>
                    ) : null}
                    <span
                      className={`text-body-sm tabular-nums ${count > MAX_TRANSLATE_LENGTH ? 'text-danger' : 'text-ink-muted'}`}
                    >
                      {count.toLocaleString('vi-VN')}/2.000
                    </span>
                  </div>
                  <Button className="rounded-pill px-6" type="submit">
                    <Languages aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                    Dịch
                  </Button>
                </div>
              </section>

              <div className="flex justify-center lg:absolute lg:left-1/2 lg:top-3 lg:z-base lg:-translate-x-1/2">
                <button
                  aria-label="Đổi chiều dịch"
                  className="flex size-12 items-center justify-center rounded-pill border-4 border-canvas bg-brand-600 text-surface shadow-sm transition-colors hover:bg-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                  onClick={props.onSwap}
                  type="button"
                >
                  <ArrowLeftRight aria-hidden="true" className="size-5" />
                </button>
              </div>

              <section
                aria-label="Bản dịch"
                aria-live="polite"
                className="flex min-h-64 min-w-0 flex-col rounded-lg border border-brand-100 bg-brand-50"
              >
                <div className="border-b border-brand-100 px-4 py-3 sm:px-5 lg:pl-10">
                  <LanguageChips active={textType === 'HANZI' ? 'MEANING' : 'HANZI'} label="Ngôn ngữ đích" />
                </div>
                <div className="min-w-0 flex-1 px-4 py-4 sm:px-5 lg:pl-10">{props.output}</div>
              </section>
            </div>
          ) : (
            <div className="rounded-lg border border-line bg-surface p-4 shadow-sm sm:p-5">
              {mode === 'PINYIN' ? (
                <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start">
                  <FormField
                    className="mb-0 min-w-0 flex-1"
                    error={formError}
                    hint="Gõ có dấu (rì) hoặc số thanh (ri4)."
                    htmlFor="translate-pinyin"
                    label="Nội dung cần tra"
                    required
                  >
                    <Input
                      autoComplete="off"
                      onChange={(event) => onChange({ ...draft, q: event.target.value })}
                      value={draft.q}
                    />
                  </FormField>
                  <Button className="rounded-pill px-6 sm:mt-7" type="submit">
                    <Search aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                    Tra cứu
                  </Button>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="grid min-w-0 gap-x-4 sm:grid-cols-3">
                    <FormField htmlFor="dictionary-radical" label="Bộ thủ">
                      <Select
                        className="font-hanzi"
                        onChange={(event) => onChange({ ...draft, radical: event.target.value })}
                        value={draft.radical}
                      >
                        <option value="">Tất cả bộ thủ</option>
                        {draft.radical && !props.radicals.some(({ radical }) => radical === draft.radical) ? (
                          <option value={draft.radical}>{draft.radical}</option>
                        ) : null}
                        {props.radicals.map(({ radical, charCount }) => (
                          <option key={radical} value={radical}>
                            {radical} ({charCount} chữ)
                          </option>
                        ))}
                      </Select>
                    </FormField>
                    <FormField
                      htmlFor="dictionary-strokes"
                      hint="Từ 1 đến 64; có thể chỉ chọn một trong hai bộ lọc."
                      label="Số nét"
                    >
                      <Input
                        inputMode="numeric"
                        onChange={(event) => onChange({ ...draft, strokes: event.target.value })}
                        value={draft.strokes}
                      />
                    </FormField>
                    <div className="flex items-start sm:pt-7">
                      <Button className="w-full rounded-pill px-6 sm:w-auto" type="submit">
                        <Search aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                        Tra cứu
                      </Button>
                    </div>
                  </div>
                  {formError ? (
                    <p className="text-body-sm text-danger" role="alert">
                      {formError}
                    </p>
                  ) : null}
                </div>
              )}
            </div>
          )}
        </fieldset>
      </form>

      {guest ? (
        <GuestTrialNotice
          exhausted={trialExhausted}
          loginTo={props.loginTo}
          registerTo={props.registerTo}
          trial={guestTrial}
        />
      ) : null}
      {mode === 'RADICAL' && props.radicalsError ? (
        <ErrorState
          message={props.radicalsError.message}
          onRetry={props.onRadicalsRetry}
          requestId={props.radicalsError.requestId}
          title="Chưa tải được danh sách bộ thủ"
        />
      ) : null}
      {props.searched && props.state === 'loading' ? (
        <div aria-label="Đang tra từ điển" className="space-y-3">
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
        </div>
      ) : null}
      {props.searched && props.state === 'error' && !trialRejected ? (
        <ErrorState
          message={
            <div className="space-y-2">
              <p>
                {error?.status === 429
                  ? 'Bạn đã tra cứu quá giới hạn. Vui lòng thử lại sau.'
                  : (error?.message ?? 'Vui lòng thử lại sau.')}
              </p>
              {error?.status === 429 && typeof retryAfter === 'number' ? <p>Thử lại sau {retryAfter} giây.</p> : null}
              {error?.status === 429 && guest ? (
                <Button to={props.loginTo} variant="outline">
                  Đăng nhập để tra tiếp
                </Button>
              ) : null}
            </div>
          }
          onRetry={props.onRetry}
          requestId={error?.requestId}
          title="Chưa thể tra từ điển"
        />
      ) : null}
      {props.listed && props.searched && (props.state === 'ready' || props.state === 'empty') && props.result ? (
        <section aria-label="Kết quả tra cứu" aria-live="polite" className="space-y-3">
          {props.result.items.length ? (
            <>
              <p className="text-body-sm text-ink-secondary">{props.result.total} kết quả</p>
              <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {props.result.items.map((entry) => (
                  <li key={entry.id}>
                    <button
                      aria-pressed={props.selectedId === entry.id}
                      className={`flex w-full min-w-0 items-start gap-3 rounded-md border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
                        props.selectedId === entry.id
                          ? 'border-brand-600 bg-brand-50'
                          : 'border-line bg-surface hover:border-brand-600'
                      }`}
                      onClick={() => props.onSelect(entry.id)}
                      type="button"
                    >
                      <HanziText className="shrink-0 text-brand-800" size="sm">
                        {entry.text}
                      </HanziText>
                      <span className="min-w-0 flex-1 space-y-1">
                        {entry.pinyin ? (
                          <span className="block break-words text-body font-medium text-brand-700">{entry.pinyin}</span>
                        ) : null}
                        <span className="block break-words text-body-sm text-ink">{entry.meaningVi}</span>
                        {entry.sinoViet ? (
                          <span className="block text-body-sm text-ink-secondary">Hán Việt: {entry.sinoViet}</span>
                        ) : null}
                        <span className="flex flex-wrap gap-1">
                          {entry.hskLevel != null ? <LevelBadge exact level={entry.hskLevel as HskLevel} /> : null}
                          {entry.approximate ? <StatusBadge tone="warning">Số nét gần đúng</StatusBadge> : null}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
              <Pagination
                onChange={(next) => props.onPageChange(next - 1)}
                page={props.page + 1}
                size={20}
                total={props.result.total}
              />
            </>
          ) : (
            <EmptyState
              description="Nội dung bạn nhập vẫn được giữ lại để chỉnh và tra lại."
              icon={<Search aria-hidden="true" className="size-5" />}
              title="Không tìm thấy — thử kiểu tra khác"
            />
          )}
        </section>
      ) : null}
      {props.details}
    </div>
  )
}
