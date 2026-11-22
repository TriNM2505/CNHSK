import { useCallback, useEffect, useRef, useState } from 'react'

import { matchesCharacter } from '../../engine/freeformMatch'
import type { Point } from '../../engine/path'
import { loadStrokeData } from '../../engine/strokeData'
import type { StrokeData } from '../../engine/strokeData'
import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { ArenaLayout, FallingTarget, MissedTarget } from '../shared/FallingTarget'
import type { ReadPinyin } from '../shared/FallingTarget'
import { itemFallDuration, useFallTimer } from '../shared/useFallTimer'
import { FreeWritingPad } from './FreeWritingPad'

export function BrushStrokeGame({ items, uiConfig, readPinyin, ...controls }: GameProps & { readPinyin?: ReadPinyin }) {
  const [index, setIndex] = useState(0)
  const [hits, setHits] = useState(0)
  const next = useCallback((hit: boolean) => {
    if (hit) setHits((count) => count + 1)
    setIndex((position) => position + 1)
  }, [])
  const item = items[index]
  return (
    <section aria-label="Mưa Chữ" className="min-w-0">
      {item ? (
        <BrushItem key={item.no} {...controls} item={item} duration={itemFallDuration(index, uiConfig)}
          readPinyin={readPinyin} onAdvance={next} hits={hits} />
      ) : (
        <>
          <p className="core-feedback">Số chữ đã viết: {hits}</p>
          <p role="status" className="core-feedback">Đã đi qua tất cả các chữ.</p>
        </>
      )}
    </section>
  )
}

function BrushItem({ item, duration, hits, disabled, submitAnswer, onMissed, onItemShown, readPinyin, onAdvance }: Omit<GameProps, 'items' | 'uiConfig'> & {
  hits: number
  item: ServedItem
  duration: number
  readPinyin?: ReadPinyin
  onAdvance: (hit: boolean) => void
}) {
  const text = typeof item.prompt.text === 'string' ? item.prompt.text : ''
  const [data, setData] = useState<StrokeData | null>()
  const [feedback, setFeedback] = useState<'submitting' | 'hit' | 'miss'>()
  const [strokes, setStrokes] = useState<Point[][]>([])
  const handled = useRef(false)
  const shown = useRef(false)
  const live = useRef(false)
  const fall = useFallTimer(duration, !!data && !disabled && !feedback, ground)

  useEffect(() => {
    live.current = true
    let current = true
    void loadStrokeData(text).then((result) => {
      if (current) setData(result)
    })
    return () => { current = false; live.current = false }
  }, [text])

  useEffect(() => {
    if (disabled || handled.current) return
    if (data === null) {
      handled.current = true
      onMissed(item.no)
      onAdvance(false)
    } else if (data && !shown.current) {
      shown.current = true
      onItemShown?.(item.no)
    }
  }, [data, disabled, item.no, onAdvance, onItemShown, onMissed])

  useEffect(() => {
    if (feedback !== 'hit' && feedback !== 'miss') return
    const timeout = window.setTimeout(() => onAdvance(feedback === 'hit'), feedback === 'hit' ? 200 : 600)
    return () => window.clearTimeout(timeout)
  }, [feedback, onAdvance])

  function ground() {
    if (handled.current) return
    handled.current = true
    setFeedback('miss')
    // A wrong answer on record is what costs the life; the shell owns retry and terminal errors.
    submitAnswer(item.no, 'MISS').catch(() => {})
  }

  async function check(written: Point[][]) {
    if (!data || disabled || handled.current) return
    if (fall.expired()) { ground(); return }
    setStrokes(written)
    if (!matchesCharacter(written, data.medians)) return
    handled.current = true
    setFeedback('submitting')
    try {
      const answer = await submitAnswer(item.no, 'PASS')
      if (!live.current) return
      if (answer.correct) setFeedback('hit')
      else onAdvance(false)
    } catch {
      if (live.current) onAdvance(false)
    }
  }

  const arena = feedback === 'miss'
    ? <MissedTarget text={text} lexemeId={item.lexemeId} readPinyin={readPinyin} />
    : <FallingTarget text={text} remaining={fall.remaining} duration={duration} hit={feedback === 'hit'} />
  return (
    <ArenaLayout arena={arena}>
      <div className="prompt-label">VIẾT CHỮ ĐANG RƠI</div>
      <p className="brush-hits">Số chữ đã viết: {hits}</p>
      {feedback !== 'miss' && (
        <div className="board-shell">
          {data
            ? <FreeWritingPad strokes={strokes} disabled={disabled || Boolean(feedback)} onStrokes={(all) => void check(all)} />
            : <p role="status">Đang tải dữ liệu nét…</p>}
          <div className="board-hint">
            {feedback === 'submitting' && <p role="status">Đang chấm…</p>}
            {feedback === 'hit' && <p role="status">Đã hạ chữ {text}</p>}
            {!feedback && <p>Viết cả chữ vào ô, không cần đúng thứ tự nét.</p>}
          </div>
          <button type="button" className="pad-clear" disabled={disabled || Boolean(feedback) || strokes.length === 0}
            onClick={() => setStrokes([])}>Xoá, viết lại</button>
        </div>
      )}
    </ArenaLayout>
  )
}
