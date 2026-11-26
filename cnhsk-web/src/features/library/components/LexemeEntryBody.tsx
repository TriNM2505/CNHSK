import { ArrowRight, BookmarkPlus, BookOpenText, Quote, Sparkles, Volume2 } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { Button, Card, HanziText, LevelBadge, type HskLevel } from '@/shared/ui'

import type { LexemeDetail, LexemeRef } from '../types'
import { LexemeStrokeAnimation } from './LexemeStrokeAnimation'

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
      <audio aria-label="Âm thanh mục từ" onError={() => setFailed(true)} preload="none" ref={audio} src={url} />
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

/** Related entries either navigate (detail page) or open in place (translate screen). */
export type RelatedTarget = { href: (id: number) => string } | { onOpen: (id: number) => void }

function RelatedLexemes({
  title,
  entries,
  target,
}: {
  title: string
  entries: readonly LexemeRef[]
  target: RelatedTarget
}) {
  if (!entries.length) return null
  const itemClass =
    'relative isolate block w-full min-w-0 overflow-hidden rounded-md border border-brand-100 bg-surface p-4 text-left no-underline transition-colors hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'
  return (
    <section className="space-y-3">
      <h2 className="text-h3 text-ink">{title}</h2>
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {entries.map((entry) => {
          const body = (
            <span className="relative z-base block min-w-0 space-y-1">
              <span className="flex min-w-0 items-start justify-between gap-3">
                <HanziText className="min-w-0 break-words" size="sm">
                  {entry.text}
                </HanziText>
                <ArrowRight aria-hidden="true" className="mt-1 size-4 shrink-0 text-brand-600" />
              </span>
              {entry.pinyin ? <span className="block break-words text-body text-brand-700">{entry.pinyin}</span> : null}
              <span className="block break-words text-body-sm text-ink-secondary">{entry.meaningVi}</span>
            </span>
          )
          return (
            <li key={entry.id}>
              {'href' in target ? (
                <Link className={itemClass} to={target.href(entry.id)}>
                  {body}
                </Link>
              ) : (
                <button className={itemClass} onClick={() => target.onOpen(entry.id)} type="button">
                  {body}
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

export interface LexemeEntryBodyProps {
  entry: LexemeDetail
  related: RelatedTarget
  saveActions?: ReactNode
}

/** Meaning, pronunciation, examples and related entries of one dictionary entry. */
export function LexemeEntryBody({ entry, related, saveActions }: LexemeEntryBodyProps) {
  // Only facts the entry actually has: a word has no radical or stroke count of its own.
  const facts = [
    entry.sinoViet ? { label: 'Hán Việt', value: entry.sinoViet } : null,
    entry.hskLevel != null
      ? { label: 'Cấp HSK', value: <LevelBadge exact level={entry.hskLevel as HskLevel} /> }
      : null,
    entry.radical
      ? {
          label: 'Bộ thủ',
          value: (
            <HanziText className="break-words text-body" size="sm">
              {entry.radical}
            </HanziText>
          ),
        }
      : null,
    entry.strokeCount != null ? { label: 'Số nét', value: entry.strokeCount } : null,
  ].filter((fact) => fact !== null)
  return (
    <div className="grid min-w-0 items-start gap-6 lg:grid-cols-3">
      <div className="min-w-0 space-y-6 lg:col-span-2">
        <section
          aria-label="Thông tin mục từ"
          className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-surface p-5 sm:p-6"
        >
          <div className="relative z-base space-y-5">
            <div className="flex items-center gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600">
                <BookOpenText aria-hidden="true" className="size-5" />
              </span>
              <h2 className="text-h3 text-ink">Nghĩa và phát âm</h2>
            </div>
            {entry.pinyin ? <p className="text-h3 text-brand-700">{entry.pinyin}</p> : null}
            <p className="whitespace-pre-wrap break-words text-body text-ink">{entry.meaningVi}</p>
            {facts.length ? (
              <dl className="grid grid-cols-2 gap-4 text-body-sm sm:grid-cols-4">
                {facts.map((fact) => (
                  <div key={fact.label}>
                    <dt className="text-ink-muted">{fact.label}</dt>
                    <dd className="mt-1 break-words text-body text-ink">{fact.value}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
            {entry.audioUrl ? <LexemeAudio key={entry.audioUrl} url={entry.audioUrl} /> : null}
          </div>
        </section>
        {entry.kind === 'CHAR' && entry.strokeData != null ? (
          <LexemeStrokeAnimation character={entry.text} key={entry.id} />
        ) : null}
        {entry.structure ? (
          <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-5 sm:p-6">
            <div className="relative z-base space-y-4">
              <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
                <Sparkles aria-hidden="true" className="size-5 text-brand-600" />
                Cấu trúc
              </h2>
              <p className="whitespace-pre-wrap break-words font-hanzi text-body text-ink">{entry.structure}</p>
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
                      {example.pinyin ? <p className="break-words text-body text-brand-700">{example.pinyin}</p> : null}
                      {example.vi ? (
                        <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">{example.vi}</p>
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
        <RelatedLexemes entries={entry.components} target={related} title="Chữ thành phần" />
        <RelatedLexemes entries={entry.containedIn} target={related} title="Từ chứa chữ này" />
        <RelatedLexemes entries={entry.related} target={related} title="Mục từ liên quan" />
      </div>
      <aside aria-label="Ghi nhớ mục từ" className="min-w-0 space-y-4 lg:sticky lg:top-20">
        <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-5 sm:p-6">
          <div className="relative z-base space-y-5">
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
  )
}
