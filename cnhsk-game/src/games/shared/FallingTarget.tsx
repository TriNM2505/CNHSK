import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'

export type ReadPinyin = (lexemeId: number, signal: AbortSignal) => Promise<string | null>

export function FallingTarget({ text, hint, remaining, duration, hit, projectile }: {
  text: string
  hint?: string
  remaining: number
  duration: number
  hit: boolean
  projectile?: boolean
}) {
  return (
    <div className="relative h-32 overflow-hidden rounded-md border border-line text-center motion-reduce:h-auto motion-reduce:p-2">
      <div className="absolute inset-x-0 top-0 translate-y-[var(--fall-y)] motion-reduce:static motion-reduce:transform-none"
        style={{ '--fall-y': (1 - remaining / duration) * 64 + 'px' } as CSSProperties}>
        {hit ? (
          <p aria-hidden="true" className="text-3xl font-bold">
            {projectile && <span className="inline-block motion-safe:animate-[dart-hit_200ms_ease-out]">➤</span>}
            <span className="inline-block motion-safe:animate-[split-left_200ms_ease-out]">✦</span>
            <span className="inline-block motion-safe:animate-[split-right_200ms_ease-out]">✧</span>
          </p>
        ) : <p className="break-words text-3xl font-bold">{text}</p>}
        {hint && <p className="break-words text-ink-secondary">{hint}</p>}
      </div>
      <progress aria-label="Thời gian của lượt" value={Math.ceil(remaining)} max={duration}
        className="hidden w-full motion-reduce:block" />
      {hit && <style>{`
        @keyframes dart-hit { from { transform: translateX(-4rem); } }
        @keyframes split-left { to { transform: translate(-1rem, -0.5rem) rotate(-25deg); } }
        @keyframes split-right { to { transform: translate(1rem, -0.5rem) rotate(25deg); } }
      `}</style>}
    </div>
  )
}

export function MissedTarget({ text, pinyin, lexemeId, readPinyin }: {
  text: string
  pinyin?: string
  lexemeId?: number | null
  readPinyin?: ReadPinyin
}) {
  const [lookup, setLookup] = useState<string | null>(null)
  useEffect(() => {
    if (pinyin || lexemeId == null || !readPinyin) return
    const controller = new AbortController()
    void readPinyin(lexemeId, controller.signal).then((value) => {
      if (!controller.signal.aborted) setLookup(value)
    }).catch(() => { /* The missed item advances even if the lookup fails. */ })
    return () => controller.abort()
  }, [lexemeId, pinyin, readPinyin])
  return <p role="status">Bỏ lỡ: {text}{(pinyin ?? lookup) && ' · ' + (pinyin ?? lookup)}</p>
}
