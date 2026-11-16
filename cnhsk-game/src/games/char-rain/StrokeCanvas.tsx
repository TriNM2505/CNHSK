import { useRef, useState } from 'react'
import type { PointerEvent } from 'react'

import type { Point } from '../../engine/path'
import type { StrokeData } from '../../engine/strokeData'

export function StrokeCanvas({ data, index, accepted, disabled, onStroke }: {
  data: StrokeData
  index: number
  accepted: readonly Point[][]
  disabled: boolean
  onStroke: (points: Point[]) => void
}) {
  const active = useRef<{ id: number; points: Point[] }>()
  const [draft, setDraft] = useState<Point[]>([])
  const median = data.medians[index]
  const first = median?.[0]
  const next = median?.find(([x, y]) => first && (x !== first[0] || y !== first[1]))

  function point(event: PointerEvent<SVGSVGElement>): Point {
    const bounds = event.currentTarget.getBoundingClientRect()
    return {
      x: 1000 * (event.clientX - bounds.left) / (bounds.width || 1),
      y: 1000 * (event.clientY - bounds.top) / (bounds.height || 1),
    }
  }

  function clear() {
    active.current = undefined
    setDraft([])
  }

  function start(event: PointerEvent<SVGSVGElement>) {
    if (disabled || active.current || event.button !== 0) return
    event.currentTarget.setPointerCapture?.(event.pointerId)
    const points = [point(event)]
    active.current = { id: event.pointerId, points }
    setDraft(points)
  }

  function move(event: PointerEvent<SVGSVGElement>) {
    if (disabled || active.current?.id !== event.pointerId) return
    const points = [...active.current!.points, point(event)]
    active.current = { id: event.pointerId, points }
    setDraft(points)
  }

  function end(event: PointerEvent<SVGSVGElement>) {
    const stroke = active.current
    if (!stroke || stroke.id !== event.pointerId) return
    clear()
    if (!disabled) onStroke([...stroke.points, point(event)])
  }

  return (
    <svg aria-label="Khung tô nét" role="img" viewBox="0 0 1000 1000"
      className="mx-auto block aspect-square w-full max-w-sm touch-none rounded-md border border-line"
      onPointerDown={start} onPointerMove={move} onPointerUp={end}
      onPointerCancel={(event) => { if (active.current?.id === event.pointerId) clear() }}
      onLostPointerCapture={(event) => { if (active.current?.id === event.pointerId) clear() }}
    >
      <g transform="translate(0 900) scale(1 -1)">
        {data.strokes.map((stroke, no) => (
          <path key={no} d={stroke} className="fill-current text-ink opacity-15" />
        ))}
        {first && <circle cx={first[0]} cy={first[1]} r={20} className="fill-current text-brand-700" />}
        {first && next && (
          <path d="M 24 -12 L 48 0 L 24 12"
            transform={`translate(${first[0]} ${first[1]}) rotate(${Math.atan2(next[1] - first[1], next[0] - first[0]) * 180 / Math.PI})`}
            fill="none" stroke="currentColor" strokeWidth={12} strokeLinecap="round"
            className="text-brand-700"
          />
        )}
      </g>
      {[...accepted, draft].map((points, no) => (
        <polyline key={no} points={points.map(({ x, y }) => `${x},${y}`).join(' ')}
          fill="none" stroke="currentColor" strokeWidth={20} strokeLinecap="round" strokeLinejoin="round"
          className="text-brand-800"
        />
      ))}
    </svg>
  )
}
