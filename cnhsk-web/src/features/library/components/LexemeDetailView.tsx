import {
  ArrowLeft,
  ArrowRight,
  BookmarkPlus,
  BookOpenText,
  Quote,
  Sparkles,
  Volume2,
} from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
  Skeleton,
  type HskLevel,
} from '@/shared/ui'
import type { PageStateValue } from '@/shared/ui'

import { dictionaryDetailHref } from '../dictionarySearch'
import type { LexemeDetail, LexemeRef } from '../types'
import listeningPanda from '../assets/cnhsk-panda-listening.png'
import { DictionaryLandscapeArt } from './DictionaryLandscapeArt'
import { LibraryHeaderArt } from './LibraryHeaderArt'
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
        <Volume2 aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
        Nghe phát âm
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
              className="relative isolate block min-w-0 overflow-hidden rounded-md border border-brand-100 bg-surface p-5 no-underline transition-colors hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              to={dictionaryDetailHref(entry.id, backTo)}
            >
              <DictionaryLandscapeArt />
              <div className="relative z-base min-w-0 space-y-2">
                <div className="flex min-w-0 items-start justify-between gap-3">
                  <HanziText className="min-w-0 break-words" size="sm">
                    {entry.text}
                  </HanziText>
                  <ArrowRight aria-hidden="true" className="mt-1 size-4 shrink-0 text-brand-600" />
                </div>
                {entry.pinyin ? (
                  <p className="break-words text-body text-brand-700">{entry.pinyin}</p>
                ) : null}
                <p className="break-words text-body text-ink-secondary">{entry.meaningVi}</p>
              </div>
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
    <div className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex min-w-0 max-w-full items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
              {state === 'ready' && entry ? (
                <HanziText className="min-w-0 break-words text-h1-sm sm:text-h1" size="sm">
                  {entry.text}
                </HanziText>
              ) : (
                'Chi tiết mục từ'
              )}
            </span>
          }
          subtitle={
            state === 'ready' && entry
              ? kindLabels[entry.kind]
              : 'Khám phá nghĩa, cách đọc và cách dùng trong ngữ cảnh.'
          }
        />
        <LibraryHeaderArt />
      </div>
      <Button size="sm" to={backTo} variant="ghost">
        <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
        Về kết quả tra cứu
      </Button>
      {state === 'loading' ? (
        <div aria-label="Đang tải mục từ" className="grid min-w-0 items-start gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <Skeleton className="h-32" />
            <Skeleton className="h-24" />
            <Skeleton className="h-48" />
          </div>
          <Skeleton className="h-80" />
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
      {state === 'empty' || (state === 'ready' && !entry) ? (
        <EmptyState
          title="Không tìm thấy mục từ"
          description="Quay lại kết quả tra cứu để chọn một mục từ khác."
          icon={<BookOpenText aria-hidden="true" className="size-5" />}
        />
      ) : null}
      {state === 'ready' && entry ? (
        <div className="grid min-w-0 items-start gap-6 lg:grid-cols-3">
          <div className="min-w-0 max-w-reading space-y-6 lg:col-span-2">
            <section
              aria-label="Thông tin mục từ"
              className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-surface p-5 sm:p-6"
            >
              <DictionaryLandscapeArt bridge />
              <div className="relative z-base space-y-5">
                <div className="flex items-center gap-3">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600">
                    <BookOpenText aria-hidden="true" className="size-5" />
                  </span>
                  <h2 className="text-h3 text-ink">Nghĩa và phát âm</h2>
                </div>
                {entry.pinyin ? <p className="text-h3 text-brand-700">{entry.pinyin}</p> : null}
                <p className="whitespace-pre-wrap break-words text-body text-ink">
                  {entry.meaningVi}
                </p>
                <dl className="grid grid-cols-2 gap-4 text-body-sm sm:grid-cols-4">
                  <div>
                    <dt className="text-ink-muted">Hán Việt</dt>
                    <dd className="mt-1 break-words text-body text-ink">
                      {entry.sinoViet || 'Chưa có'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ink-muted">Cấp HSK</dt>
                    <dd className="mt-1 text-ink">
                      {entry.hskLevel != null ? (
                        <LevelBadge exact level={entry.hskLevel as HskLevel} />
                      ) : (
                        'Chưa phân cấp'
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ink-muted">Bộ thủ</dt>
                    <dd className="mt-1 text-ink">
                      {entry.radical ? (
                        <HanziText className="break-words text-body" size="sm">
                          {entry.radical}
                        </HanziText>
                      ) : (
                        'Chưa có'
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ink-muted">Số nét</dt>
                    <dd className="mt-1 text-body text-ink">{entry.strokeCount ?? 'Chưa có'}</dd>
                  </div>
                </dl>
                {entry.audioUrl ? <LexemeAudio key={entry.audioUrl} url={entry.audioUrl} /> : null}
              </div>
            </section>
            {entry.kind === 'CHAR' && entry.strokeData != null ? (
              <LexemeStrokeAnimation character={entry.text} key={entry.id} />
            ) : null}
            {entry.structure ? (
              <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-5 sm:p-6">
                <DictionaryLandscapeArt />
                <div className="relative z-base space-y-4">
                  <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                    <Sparkles aria-hidden="true" className="size-5 text-brand-600" />
                    Cấu trúc
                  </h2>
                  <p className="whitespace-pre-wrap break-words font-hanzi text-body text-ink">
                    {entry.structure}
                  </p>
                </div>
              </Card>
            ) : null}
            <section className="space-y-3">
              <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                <Quote aria-hidden="true" className="size-5 text-brand-600" />
                Ví dụ
              </h2>
              {entry.examples.length ? (
                <ul className="space-y-3">
                  {entry.examples.map((example, index) => (
                    <li
                      className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-surface p-5 sm:p-6"
                      key={index}
                    >
                      <DictionaryLandscapeArt bridge={index % 2 === 0} />
                      <div className="relative z-base flex min-w-0 items-start gap-4">
                        <span className="flex size-9 shrink-0 items-center justify-center rounded-pill border border-brand-100 bg-brand-50 text-label tabular-nums text-brand-700">
                          {String(index + 1).padStart(2, '0')}
                        </span>
                        <div className="min-w-0 flex-1 space-y-2">
                          {example.cn ? (
                            <HanziText className="block whitespace-pre-wrap break-words" size="sm">
                              {example.cn}
                            </HanziText>
                          ) : null}
                          {example.pinyin ? (
                            <p className="break-words text-body text-brand-700">{example.pinyin}</p>
                          ) : null}
                          {example.vi ? (
                            <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
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
          </div>
          <aside aria-label="Ghi nhớ mục từ" className="min-w-0 space-y-4 lg:sticky lg:top-20">
            <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-5 sm:p-6">
              <DictionaryLandscapeArt bridge />
              <div className="relative z-base space-y-5">
                <div
                  aria-hidden="true"
                  className="mx-auto size-32 bg-contain bg-center bg-no-repeat"
                  style={{ backgroundImage: `url(${listeningPanda})` }}
                />
                <div className="space-y-2 text-center">
                  <h2 className="inline-flex items-center gap-2 text-h3 text-brand-800">
                    <BookmarkPlus aria-hidden="true" className="size-5" />
                    Ghi nhớ từ mới
                  </h2>
                  <p className="text-body-sm text-ink-secondary">
                    Đọc ví dụ, thử đặt câu và lưu mục từ để ôn lại theo cách của bạn.
                  </p>
                </div>
                <div className="min-w-0">{saveActions}</div>
              </div>
            </Card>
          </aside>
        </div>
      ) : null}
    </div>
  )
}
