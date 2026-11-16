import { ArrowLeft, Volume2 } from 'lucide-react'
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
              <Card className="h-full space-y-1 p-4" variant="interactive">
                <HanziText className="text-ink" size="sm">
                  {entry.text}
                </HanziText>
                {entry.pinyin ? (
                  <p className="text-body-sm font-medium text-brand-700">{entry.pinyin}</p>
                ) : null}
                <p className="text-body-sm text-ink-secondary">{entry.meaningVi}</p>
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
            className="space-y-4 rounded-md border border-line bg-surface p-5 sm:p-6"
          >
            {entry.pinyin ? <p className="text-h3 text-brand-700">{entry.pinyin}</p> : null}
            <p className="whitespace-pre-wrap break-words text-body text-ink">{entry.meaningVi}</p>
            <dl className="grid grid-cols-2 gap-3 rounded-sm bg-canvas p-4 text-body-sm sm:grid-cols-4">
              <div>
                <dt className="text-ink-muted">Hán Việt</dt>
                <dd className="mt-0.5 font-medium text-ink">{entry.sinoViet || 'Chưa có'}</dd>
              </div>
              <div>
                <dt className="text-ink-muted">Cấp HSK</dt>
                <dd className="mt-0.5 font-medium text-ink">
                  {entry.hskLevel != null ? `HSK ${entry.hskLevel}` : 'Chưa phân cấp'}
                </dd>
              </div>
              <div>
                <dt className="text-ink-muted">Bộ thủ</dt>
                <dd className="mt-0.5 font-medium text-ink">
                  {entry.radical ? (
                    <span className="font-hanzi" lang="zh-CN">
                      {entry.radical}
                    </span>
                  ) : (
                    'Chưa có'
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-ink-muted">Số nét</dt>
                <dd className="mt-0.5 font-medium tabular-nums text-ink">
                  {entry.strokeCount ?? 'Chưa có'}
                </dd>
              </div>
            </dl>
            {entry.audioUrl ? <LexemeAudio key={entry.audioUrl} url={entry.audioUrl} /> : null}
          </section>
          {entry.kind === 'CHAR' && entry.strokeData != null ? (
            <LexemeStrokeAnimation character={entry.text} key={entry.id} />
          ) : null}
          {entry.structure ? (
            <section className="space-y-2">
              <h2 className="text-h3 text-ink">Cấu trúc</h2>
              <p className="whitespace-pre-wrap break-words rounded-md border border-brand-100 bg-brand-50 p-4 font-hanzi text-body text-ink">
                {entry.structure}
              </p>
            </section>
          ) : null}
          <section className="space-y-3">
            <h2 className="text-h3 text-ink">Ví dụ</h2>
            {entry.examples.length ? (
              <ul className="space-y-3">
                {entry.examples.map((example, index) => (
                  <li
                    className="space-y-1 rounded-md border border-line bg-surface p-4 sm:p-5"
                    key={index}
                  >
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
                      <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
                        {example.vi}
                      </p>
                    ) : null}
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
