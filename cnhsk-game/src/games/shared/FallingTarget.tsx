import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'

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

/**
 * The character falls with a CSS transform, which the browser moves without re-rendering; pausing the round pauses the
 * animation in place.
 */
export function FallingTarget({ text, remaining, duration, falling, hit }: {
  text: string
  remaining: number
  duration: number
  falling: boolean
  hit: boolean
}) {
  return (
    <div className="arena">
      <Scene variant="rain" />
      <div className="rain-streaks" aria-hidden="true" />
      <div className="fall-lane" style={{ animationDuration: `${duration}ms`, animationPlayState: falling ? 'running' : 'paused' }}>
        <div className="falling-word">
          {hit ? (
            <>
              <strong aria-hidden="true" className="fall-burst">{text}</strong>
              <span aria-hidden="true" className="fall-ripple" />
            </>
          ) : <strong className="break-words">{text}</strong>}
        </div>
      </div>
      <progress aria-label="Thời gian của lượt" value={Math.ceil(remaining)} max={duration}
        className="arena-progress hidden w-full motion-reduce:block" />
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
      <Scene variant="rain" />
      <div className="rain-streaks" aria-hidden="true" />
      <p role="status" className="arena-missed">Bỏ lỡ: {text}{lookup && ' · ' + lookup}</p>
    </div>
  )
}