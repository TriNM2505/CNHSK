import { useEffect, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'

import { Scene } from '../../shared/ui/arcade'

export type ReadPinyin = (lexemeId: number, signal: AbortSignal) => Promise<string | null>

/** The arcade layout of a falling-character game: the arena on one side, the controls on the other. */
export function ArenaLayout({ arena, children }: { arena: ReactNode; children: ReactNode }) {
  return (
    <div className="play-layout">
      {arena}
      <div className="play-controls">
        <div className="control-card">{children}</div>
      </div>
    </div>
  )
}

export function FallingTarget({ text, remaining, duration, hit }: {
  text: string
  remaining: number
  duration: number
  hit: boolean
}) {
  const progress = duration > 0 ? 1 - remaining / duration : 1
  return (
    <div className="arena">
      <Scene variant="brush" />
      <div className="falling-word motion-reduce:!top-[40%]"
        style={{ top: `${10 + Math.min(1, Math.max(0, progress)) * 56}%` } as CSSProperties}>
        {hit ? (
          <p aria-hidden="true" className="falling-hit">
            <span className="inline-block motion-safe:animate-[split-left_200ms_ease-out]">✦</span>
            <span className="inline-block motion-safe:animate-[split-right_200ms_ease-out]">✧</span>
          </p>
        ) : <strong className="break-words">{text}</strong>}
      </div>
      <progress aria-label="Thời gian của lượt" value={Math.ceil(remaining)} max={duration}
        className="arena-progress hidden w-full motion-reduce:block" />
      {hit && <style>{`
        @keyframes split-left { to { transform: translate(-1rem, -0.5rem) rotate(-25deg); } }
        @keyframes split-right { to { transform: translate(1rem, -0.5rem) rotate(25deg); } }
      `}</style>}
    </div>
  )
}

export function MissedTarget({ text, lexemeId, readPinyin }: {
  text: string
  lexemeId?: number | null
  readPinyin?: ReadPinyin
}) {
  const [lookup, setLookup] = useState<string | null>(null)
  useEffect(() => {
    if (lexemeId == null || !readPinyin) return
    const controller = new AbortController()
    void readPinyin(lexemeId, controller.signal).then((value) => {
      if (!controller.signal.aborted) setLookup(value)
    }).catch(() => { /* The missed item advances even if the lookup fails. */ })
    return () => controller.abort()
  }, [lexemeId, readPinyin])
  return (
    <div className="arena">
      <Scene variant="brush" />
      <p role="status" className="arena-missed">Bỏ lỡ: {text}{lookup && ' · ' + lookup}</p>
    </div>
  )
}
