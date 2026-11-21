import { useEffect, useRef, useState } from 'react'

import { normalizePoints, resample } from '../../engine/path'
import type { Point } from '../../engine/path'
import { loadStrokeData } from '../../engine/strokeData'
import type { StrokeData } from '../../engine/strokeData'
import { gradeStroke } from '../../engine/strokeScore'
import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { StrokeCanvas } from './StrokeCanvas'

export function CharRainGame({ items, ...controls }: GameProps) {
  const [position, setPosition] = useState(0)
  const [stars, setStars] = useState<number>()
  const item = items[position]

  return (
    <section aria-label="Mưa Chữ" className="min-w-0">
      {stars !== undefined && (
        <p role="status" className="core-feedback">
          <span aria-hidden="true">{'✦'.repeat(stars)} </span>{stars}/3 sao cho chữ vừa tô
        </p>
      )}
      {item ? (
        <Character key={item.no} {...controls} item={item} position={position} total={items.length}
          onNext={(award) => {
            if (award !== undefined) setStars(award)
            setPosition(position + 1)
          }} />
      ) : <p role="status" className="core-feedback">Đã đi qua tất cả các chữ.</p>}
    </section>
  )
}

function Character({ item, position, total, disabled, submitAnswer, onMissed, onItemShown, onNext }: Omit<GameProps, 'items'> & {
  item: ServedItem
  position: number
  total: number
  onNext: (stars?: number) => void
}) {
  const text = typeof item.prompt.text === 'string' ? item.prompt.text : ''
  const [data, setData] = useState<StrokeData | null>()
  const [accepted, setAccepted] = useState<Point[][]>([])
  const [hint, setHint] = useState<string>()
  const [pending, setPending] = useState(false)
  const status = useRef({ done: false, retries: 0 })
  const live = useRef(false)

  useEffect(() => {
    live.current = true
    void loadStrokeData(text).then((result) => {
      if (live.current) setData(result)
    })
    return () => { live.current = false }
  }, [text])

  useEffect(() => {
    if (disabled || status.current.done) return
    if (data === null) {
      status.current.done = true
      onMissed(item.no)
      onNext()
    } else if (data) {
      onItemShown?.(item.no)
    }
  }, [data, disabled, item.no, onItemShown, onMissed, onNext])

  async function checkStroke(points: Point[]) {
    const median = data?.medians[accepted.length]
    if (!data || !median || disabled || status.current.done) return
    const target = median.map(([x, y]) => ({ x, y: 900 - y }))
    const result = gradeStroke(normalizePoints(resample(target, 32)), normalizePoints(resample(points, 32)))
    if (!result.ok) {
      status.current.retries += 1
      setHint(result.hint ?? 'Hãy tô lại nét này.')
      return
    }

    const strokes = [...accepted, points]
    setAccepted(strokes)
    setHint(undefined)
    if (strokes.length !== data.strokes.length) return
    status.current.done = true
    setPending(true)
    const retries = status.current.retries
    const accuracy = 100 * strokes.length / (strokes.length + retries)
    const stars = accuracy >= 90 && retries <= 2 ? 3 : accuracy >= 70 ? 2 : 1
    try {
      await submitAnswer(item.no, 'PASS')
      if (live.current) onNext(stars)
    } catch {
      // The shell handles terminal round errors.
    }
  }

  const filled = total ? position / total * 100 : 0
  return (
    <div className="rain-layout">
      <div className="rain-side">
        <div className="rain-progress">
          <span>CHỮ {position + 1}/{total}</span>
          <div><i style={{ width: `${filled}%` }} /></div>
        </div>
        <h2 className="rain-char">{text}</h2>
        <p className="rain-meaning break-words">{String(item.prompt.hint ?? '')}</p>
        <p className="rain-tip">
          {data ? `Chữ này có ${data.strokes.length} nét. ` : ''}Tô đúng từng nét từ điểm tròn theo hướng mũi tên. Viết sai có thể thử lại ngay.
        </p>
        <div className="jar" aria-hidden="true"><span>✦</span><div className="jar-fill" style={{ height: `${filled}%` }} /></div>
        <span className="jar-caption" aria-hidden="true">LỌ MỰC CỦA BẠN</span>
      </div>
      <div className="board-shell">
        {data ? (
          <>
            <div className="board-top">
              <p>Nét {Math.min(accepted.length + 1, data.strokes.length)}/{data.strokes.length}</p>
              <span aria-hidden="true">✦ Theo thứ tự nét</span>
            </div>
            <StrokeCanvas data={data} index={accepted.length} accepted={accepted}
              disabled={disabled || pending} onStroke={(points) => void checkStroke(points)} />
          </>
        ) : <p role="status">Đang tải dữ liệu nét…</p>}
        <div className="board-hint">
          {hint && <p role="status">{hint}</p>}
          {pending && <p role="status">Đã tô xong, đang gửi câu trả lời…</p>}
          {!hint && !pending && <p>Đi theo nét đậm, bắt đầu từ điểm tròn.</p>}
        </div>
      </div>
    </div>
  )
}
