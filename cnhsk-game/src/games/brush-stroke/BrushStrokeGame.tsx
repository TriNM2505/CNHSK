import { useCallback, useEffect, useRef, useState } from 'react'

import { normalizePoints, resample } from '../../engine/path'
import type { Point } from '../../engine/path'
import { loadStrokeData } from '../../engine/strokeData'
import type { StrokeData } from '../../engine/strokeData'
import { gradeStroke } from '../../engine/strokeScore'
import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { StrokeCanvas } from '../char-rain/StrokeCanvas'
import { ArenaLayout, FallingTarget, MissedTarget } from '../shared/FallingTarget'
import type { ReadPinyin } from '../shared/FallingTarget'
import { itemFallDuration, useFallTimer } from '../shared/useFallTimer'

export function BrushStrokeGame({ items, uiConfig, readPinyin, ...controls }: GameProps & { readPinyin?: ReadPinyin }) {
  const [index, setIndex] = useState(0)
  const [hits, setHits] = useState(0)
  const next = useCallback((hit: boolean) => {
    if (hit) setHits((count) => count + 1)
    setIndex((position) => position + 1)
  }, [])
  const item = items[index]
  return (
    <section aria-label="Thần Bút" className="min-w-0">
      {item ? (
        <BrushItem key={item.no} {...controls} item={item} duration={itemFallDuration(index, uiConfig)}
          readPinyin={readPinyin} onAdvance={next} hits={hits} />
      ) : (
        <>
          <p className="core-feedback">Số chữ đã hạ: {hits}</p>
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
  const [hint, setHint] = useState<string>()
  const [feedback, setFeedback] = useState<'submitting' | 'hit' | 'miss'>()
  const [stroke, setStroke] = useState<Point[]>()
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
    onMissed(item.no)
    setFeedback('miss')
  }

  async function checkStroke(points: Point[]) {
    const median = data?.medians[0]
    if (!median || disabled || handled.current) return
    if (fall.expired()) { ground(); return }
    const target = median.map(([x, y]) => ({ x, y: 900 - y }))
    const grade = gradeStroke(normalizePoints(resample(target, 32)), normalizePoints(resample(points, 32)))
    if (!grade.ok) {
      setHint(grade.hint ?? 'Hãy tô lại nét đầu tiên.')
      return
    }
    handled.current = true
    setHint(undefined)
    setStroke(points)
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
      <div className="prompt-label">TÔ NÉT ĐẦU CỦA CHỮ ĐANG RƠI</div>
      <p className="brush-hits">Số chữ đã hạ: {hits}</p>
      {feedback !== 'miss' && (
        <div className="board-shell">
          {data ? <StrokeCanvas data={data} index={0} accepted={stroke ? [stroke] : []}
            disabled={disabled || Boolean(feedback)} onStroke={(points) => void checkStroke(points)} />
            : <p role="status">Đang tải dữ liệu nét…</p>}
          <div className="board-hint">
            {hint && <p role="status">{hint}</p>}
            {feedback === 'submitting' && <p role="status">Đang chấm…</p>}
            {feedback === 'hit' && <p role="status">Đã hạ chữ {text}</p>}
            {!hint && !feedback && <p>Tô nét đầu tiên từ điểm tròn để hạ chữ.</p>}
          </div>
        </div>
      )}
    </ArenaLayout>
  )
}
