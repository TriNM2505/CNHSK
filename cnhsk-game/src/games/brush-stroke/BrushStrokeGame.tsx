import { useCallback, useEffect, useRef, useState } from 'react'

import { readCharacter } from '../../engine/freeformMatch'
import type { Point } from '../../engine/path'
import { loadStrokeData } from '../../engine/strokeData'
import type { StrokeData } from '../../engine/strokeData'
import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { ArenaLayout, FallingTarget, MissedTarget } from '../shared/FallingTarget'
import type { ReadPinyin } from '../shared/FallingTarget'
import { itemFallDuration, useFallTimer } from '../shared/useFallTimer'
import { FreeWritingPad } from './FreeWritingPad'

const hitMs = 350
const missMs = 600
/** With fewer strokes than the character has, wait this long for another one before accepting joined writing. */
const joinedPauseMs = 450

const textOf = (item: ServedItem | undefined) => typeof item?.prompt.text === 'string' ? item.prompt.text : ''

export function BrushStrokeGame({ items, uiConfig, readPinyin, ...controls }: GameProps & { readPinyin?: ReadPinyin }) {
  const [index, setIndex] = useState(0)
  const [hits, setHits] = useState(0)
  const next = useCallback((hit: boolean) => {
    if (hit) setHits((count) => count + 1)
    setIndex((position) => position + 1)
  }, [])
  const item = items[index]

  const upcoming = textOf(items[index + 1])
  useEffect(() => {
    // The next character's strokes are ready before it starts to fall.
    if (upcoming) void loadStrokeData(upcoming)
  }, [upcoming])

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
  const text = textOf(item)
  const [data, setData] = useState<StrokeData | null>()
  const [feedback, setFeedback] = useState<'hit' | 'miss'>()
  const [strokes, setStrokes] = useState<Point[][]>([])
  const handled = useRef(false)
  const shown = useRef(false)
  const joined = useRef<number>()
  const falling = !!data && !disabled && !feedback
  const fall = useFallTimer(duration, falling, ground)

  useEffect(() => {
    let current = true
    void loadStrokeData(text).then((result) => {
      if (current) setData(result)
    })
    return () => { current = false }
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
    if (!feedback) return
    const timeout = window.setTimeout(() => onAdvance(feedback === 'hit'), feedback === 'hit' ? hitMs : missMs)
    return () => window.clearTimeout(timeout)
  }, [feedback, onAdvance])

  useEffect(() => {
    // A pause of the round (leave dialog, lost connection) also pauses the wait for another stroke.
    if (disabled) forgetJoined()
    return forgetJoined
  }, [disabled])

  function forgetJoined() {
    window.clearTimeout(joined.current)
    joined.current = undefined
  }

  function hit() {
    if (handled.current) return
    handled.current = true
    forgetJoined()
    setFeedback('hit')
    // The character breaks at once; the shell sends the answer in the background and owns retries and errors.
    submitAnswer(item.no, 'PASS').catch(() => {})
  }

  function ground() {
    if (handled.current) return
    if (joined.current !== undefined) { hit(); return }
    handled.current = true
    setFeedback('miss')
    onMissed(item.no)
  }

  function check(written: Point[][]) {
    if (!data || disabled || handled.current) return
    if (fall.expired()) { ground(); return }
    setStrokes(written)
    forgetJoined()
    const reading = readCharacter(written, data.medians)
    if (reading === 'complete') hit()
    else if (reading === 'joined') joined.current = window.setTimeout(hit, joinedPauseMs)
  }

  const arena = feedback === 'miss'
    ? <MissedTarget text={text} lexemeId={item.lexemeId} readPinyin={readPinyin} />
    : <FallingTarget text={text} remaining={fall.remaining} duration={duration} falling={falling} hit={feedback === 'hit'} />
  return (
    <ArenaLayout arena={arena}>
      <div className="prompt-label">VIẾT CHỮ ĐANG RƠI</div>
      <p className="brush-hits">Số chữ đã viết: {hits}</p>
      <div className="board-shell">
        <FreeWritingPad strokes={strokes} disabled={disabled || !data || Boolean(feedback)} onPenDown={forgetJoined}
          onStrokes={check} />
        <div className="board-hint">
          {feedback === 'hit' && <p role="status">Đã hạ chữ {text}</p>}
          {!feedback && <p>Viết cả chữ vào ô, không cần đúng thứ tự nét.</p>}
        </div>
        <button type="button" className="pad-clear" disabled={disabled || Boolean(feedback) || strokes.length === 0}
          onClick={() => { forgetJoined(); setStrokes([]) }}>Xoá, viết lại</button>
      </div>
    </ArenaLayout>
  )
}