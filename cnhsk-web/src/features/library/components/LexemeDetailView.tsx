import { ArrowLeft, BookOpenText, Quote, Sparkles, Volume2 } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  ErrorState,
  HanziText,
  PageHeader,
  Skeleton,
  type PageStateValue,
} from '@/shared/ui'

import { dictionaryDetailHref } from '../dictionarySearch'
import type { LexemeDetail, LexemeRef } from '../types'
import { LexemeStrokeAnimation } from './LexemeStrokeAnimation'

const kindLabels = { CHAR: 'Chữ Hán', WORD: 'Từ vựng', GRAMMAR: 'Ngữ pháp' }

function LexemeAudio({ url }: { url: string }) {
  const audio = useRef<HTMLAudioElement>(null)
  const active = useRef(true)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    active.current = true
    const element = audio.current
    return () => {
      active.current = false
      element?.pause()
    }
  }, [])
  return (
    <div className="space-y-2">
      <audio
        aria-label="Âm thanh mục từ"
        onError={() => setFailed(true)}
        preload="none"
        ref={audio}
        src={url}
      />
      <Button
        variant="outline"
        onClick={() => {
          setFailed(false)
          const element = audio.current
          if (!element) return
          element.currentTime = 0
          void element.play().catch(() => {
            if (active.current) setFailed(true)
          })
        }}
      >
        <span className="inline-flex items-center gap-2">
          <Volume2 aria-hidden="true" className="size-4 shrink-0" />
          Nghe phát âm
        </span>
      </Button>
      {failed ? (
        <p className="text-body-sm text-danger" role="alert">
          Chưa thể phát âm thanh. Vui lòng thử lại.
        </p>
      ) : null}
    </div>
  )
}

function RelatedLexemes({
  title,
  entries,
  backTo,
}: {
  title: string
  entries: readonly LexemeRef[]
  backTo: string
}) {
  if (!entries.length) return null
  return (
    <section className="space-y-3">
      <h2 className="text-h3 text-ink">{title}</h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {entries.map((entry) => (
          <li key={entry.id}>
            <Link
              className="block h-full rounded-md no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
              to={dictionaryDetailHref(entry.id, backTo)}
            >
              <Card className="flex h-full items-start gap-3 p-4" variant="interactive">
                <div className="flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-sm border border-brand-100 bg-surface-subtle px-2 py-1">
                  <HanziText className="text-brand-800" size="sm">
                    {entry.text}
                  </HanziText>
                </div>
                <div className="min-w-0 flex-1 space-y-0.5">
                  {entry.pinyin ? (
                    <p className="text-body-sm font-semibold text-brand-700">{entry.pinyin}</p>
                  ) : null}
                  <p className="text-body-sm text-ink-secondary">{entry.meaningVi}</p>
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export interface LexemeDetailViewProps {
  entry?: LexemeDetail
  state: PageStateValue
  error?: ApiError
  backTo: string
  onRetry: () => void
  saveActions?: ReactNode
}

export function LexemeDetailView({
  entry,
  state,
  error,
  backTo,
  onRetry,
  saveActions,
}: LexemeDetailViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-6 pb-20 sm:px-6">
      <div>
        <Link
          className="inline-flex min-h-touch items-center gap-1.5 text-label text-brand-700 underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          to={backTo}
        >
          <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
          Về kết quả tra cứu
        </Link>
      </div>
      <PageHeader
        className="mb-0"
        title={
          state === 'ready' && entry ? (
            <HanziText size="lg">{entry.text}</HanziText>
          ) : (
            'Chi tiết mục từ'
          )
        }
        subtitle={state === 'ready' && entry ? kindLabels[entry.kind] : undefined}
      />
      {state === 'loading' ? (
        <div aria-label="Đang tải mục từ" className="space-y-3" role="status">
          <Skeleton className="h-32" />
          <Skeleton className="h-24" />
        </div>
      ) : null}
      {state === 'error' ? (
        <ErrorState
          title={error?.status === 404 ? 'Không tìm thấy mục từ' : 'Không thể tải mục từ'}
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          requestId={error?.requestId}
          onRetry={error?.status === 404 ? undefined : onRetry}
        />
      ) : null}
      {state === 'ready' && entry ? (
        <>
          <section
            aria-label="Thông tin mục từ"
            className="space-y-4 rounded-md border border-brand-100 bg-surface-subtle p-5 shadow-sm sm:p-6"
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="space-y-1">
                {entry.pinyin ? (
                  <p className="text-h3 font-semibold text-brand-700">{entry.pinyin}</p>
                ) : null}
              </div>
              {entry.audioUrl ? <LexemeAudio key={entry.audioUrl} url={entry.audioUrl} /> : null}
            </div>

            <div className="rounded-md border border-line bg-surface p-4">
              <div className="mb-1.5 flex items-center gap-1.5 text-caption font-semibold uppercase tracking-wide text-brand-700">
                <BookOpenText aria-hidden="true" className="size-3.5 shrink-0" />
                <span>Nghĩa tiếng Việt</span>
              </div>
              <p className="whitespace-pre-wrap break-words text-body text-ink">
                {entry.meaningVi}
              </p>
            </div>

            <dl className="grid grid-cols-2 gap-3 text-body-sm sm:grid-cols-4">
              <div className="rounded-sm border border-line bg-surface p-3.5 shadow-sm">
                <dt className="text-caption text-ink-muted">Hán Việt</dt>
                <dd className="mt-1 font-semibold text-ink">{entry.sinoViet || 'Chưa có'}</dd>
              </div>
              <div className="rounded-sm border border-line bg-surface p-3.5 shadow-sm">
                <dt className="text-caption text-ink-muted">Cấp HSK</dt>
                <dd className="mt-1 font-semibold text-brand-700">
                  {entry.hskLevel != null ? `HSK ${entry.hskLevel}` : 'Chưa phân cấp'}
                </dd>
              </div>
              <div className="rounded-sm border border-line bg-surface p-3.5 shadow-sm">
                <dt className="text-caption text-ink-muted">Bộ thủ</dt>
                <dd className="mt-1 font-semibold text-ink">
                  {entry.radical ? (
                    <span className="font-hanzi" lang="zh-CN">
                      {entry.radical}
                    </span>
                  ) : (
                    'Chưa có'
                  )}
                </dd>
              </div>
              <div className="rounded-sm border border-line bg-surface p-3.5 shadow-sm">
                <dt className="text-caption text-ink-muted">Số nét</dt>
                <dd className="mt-1 font-semibold tabular-nums text-ink">
                  {entry.strokeCount ?? 'Chưa có'}
                </dd>
              </div>
            </dl>
          </section>

          {entry.kind === 'CHAR' && entry.strokeData != null ? (
            <LexemeStrokeAnimation character={entry.text} key={entry.id} />
          ) : null}

          {entry.structure ? (
            <section className="space-y-2">
              <div className="flex items-center gap-2">
                <Sparkles aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
                <h2 className="text-h3 text-ink">Cấu trúc</h2>
              </div>
              <p className="whitespace-pre-wrap break-words rounded-md border border-brand-100 bg-brand-50 p-4 font-hanzi text-body text-ink">
                {entry.structure}
              </p>
            </section>
          ) : null}

          <section className="space-y-3">
            <div className="flex items-center gap-2">
              <Quote aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
              <h2 className="text-h3 text-ink">Ví dụ</h2>
            </div>
            {entry.examples.length ? (
              <ul className="space-y-3">
                {entry.examples.map((example, index) => (
                  <li
                    className="rounded-md border border-line bg-surface p-4 shadow-sm sm:p-5"
                    key={index}
                  >
                    <div className="flex items-start gap-3">
                      <span className="inline-flex size-6 shrink-0 items-center justify-center rounded-pill border border-brand-100 bg-brand-50 text-caption font-semibold text-brand-700">
                        {index + 1}
                      </span>
                      <div className="min-w-0 flex-1 space-y-1">
                        {example.cn ? (
                          <p
                            className="whitespace-pre-wrap break-words font-hanzi text-h3 text-ink"
                            lang="zh-CN"
                          >
                            {example.cn}
                          </p>
                        ) : null}
                        {example.pinyin ? (
                          <p className="break-words text-body-sm font-medium text-brand-700">
                            {example.pinyin}
                          </p>
                        ) : null}
                        {example.vi ? (
                          <p className="whitespace-pre-wrap break-words border-l-2 border-brand-100 pl-3 text-body text-ink-secondary">
                            {example.vi}
                          </p>
                        ) : null}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-body-sm text-ink-secondary">Chưa có ví dụ cho mục từ này.</p>
            )}
          </section>

          <RelatedLexemes backTo={backTo} entries={entry.components} title="Chữ thành phần" />
          <RelatedLexemes backTo={backTo} entries={entry.containedIn} title="Từ chứa chữ này" />
          <RelatedLexemes backTo={backTo} entries={entry.related} title="Mục từ liên quan" />
          {saveActions}
        </>
      ) : null}
    </main>
  )
}
