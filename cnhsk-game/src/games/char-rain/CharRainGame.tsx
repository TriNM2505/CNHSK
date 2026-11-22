import { useEffect, useRef, useState } from 'react'

import { normalizePoints, polylineLength, resample } from '../../engine/path'
import type { Point } from '../../engine/path'
import { loadStrokeData } from '../../engine/strokeData'
import type { StrokeData } from '../../engine/strokeData'
import { gradeStroke } from '../../engine/strokeScore'
import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { StrokeCanvas } from './StrokeCanvas'

/** A shorter trace (in the 0..1000 box) is a stray tap or a resting palm, not a stroke. */
const MIN_STROKE_LENGTH = 30

/** Ink in the jar: each wrong stroke spends one; the server sends how many a round starts with (0 = unlimited). */
function startingInk(config: GameProps['uiConfig']) {
  return typeof config.inkLives === 'number' && config.inkLives > 0 ? config.inkLives : 0
}

export function CharRainGame({ items, ...controls }: GameProps) {
  const [position, setPosition] = useState(0)
  const [stars, setStars] = useState<number>()
  const maxInk = startingInk(controls.uiConfig)
  const [ink, setInk] = useState(maxInk)
  const item = items[position]
  const { endRound } = controls

  function spendInk() {
    if (maxInk === 0) return
    const left = Math.max(0, ink - 1)
    setInk(left)
    // An empty jar ends the round; the characters already written keep their score.
    if (left === 0) endRound?.()
  }

  return (
    <section aria-label="Lọ Mực" className="min-w-0">
      {stars !== undefined && (
        <p role="status" className="core-feedback">
          <span aria-hidden="true">{'✦'.repeat(stars)} </span>{stars}/3 sao cho chữ vừa tô
        </p>
      )}
      {item ? (
        <Character key={item.no} {...controls} item={item} position={position} total={items.length}
          ink={ink} maxInk={maxInk} onMistake={spendInk} guide={controls.uiConfig.startGuide !== false}
          onNext={(award) => {
            if (award !== undefined) setStars(award)
            setPosition(position + 1)
          }} />
      ) : <p role="status" className="core-feedback">Đã đi qua tất cả các chữ.</p>}
    </section>
  )
}

function Character({ item, position, total, ink, maxInk, guide, disabled, submitAnswer, onMissed, onItemShown,
  onMistake, onNext }: Omit<GameProps, 'items'> & {
  item: ServedItem
  position: number
  total: number
  ink: number
  maxInk: number
  guide: boolean
  onMistake: () => void
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
    if (polylineLength(points) < MIN_STROKE_LENGTH) return
    const target = median.map(([x, y]) => ({ x, y: 900 - y }))
    const result = gradeStroke(normalizePoints(resample(target, 32)), normalizePoints(resample(points, 32)))
    if (!result.ok) {
      status.current.retries += 1
      setHint(maxInk ? 'Sai nét, lọ mực vơi đi một phần. Viết lại nét này.' : result.hint ?? 'Hãy tô lại nét này.')
      onMistake()
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
  const inkLevel = maxInk ? ink / maxInk * 100 : filled
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
          {data ? `Chữ này có ${data.strokes.length} nét. ` : ''}
          {guide
            ? 'Tô đúng từng nét từ điểm tròn theo hướng mũi tên. Viết sai có thể thử lại ngay.'
            : 'Viết từng nét theo đúng thứ tự. Mỗi lần sai, lọ mực vơi một phần ba.'}
        </p>
        <div className="jar" role="img" aria-label={maxInk ? `Còn ${ink}/${maxInk} mực` : 'Lọ mực'}>
          <span aria-hidden="true">✦</span><div className="jar-fill" style={{ height: `${inkLevel}%` }} />
        </div>
        <span className="jar-caption" aria-hidden="true">LỌ MỰC CỦA BẠN</span>
      </div>
      <div className="board-shell">
        {data ? (
          <>
            <div className="board-top">
              <p>Nét {Math.min(accepted.length + 1, data.strokes.length)}/{data.strokes.length}</p>
              <span aria-hidden="true">✦ Theo thứ tự nét</span>
            </div>
            <StrokeCanvas data={data} index={accepted.length} accepted={accepted} guide={guide}
              disabled={disabled || pending} onStroke={(points) => void checkStroke(points)} />
          </>
        ) : <p role="status">Đang tải dữ liệu nét…</p>}
        <div className="board-hint">
          {hint && <p role="status">{hint}</p>}
          {pending && <p role="status">Đã tô xong, đang gửi câu trả lời…</p>}
          {!hint && !pending && (
            <p>{guide ? 'Đi theo nét đậm, bắt đầu từ điểm tròn.' : 'Viết nét tiếp theo trên khung chữ mờ.'}</p>
          )}
        </div>
      </div>
    </div>
  )
}
