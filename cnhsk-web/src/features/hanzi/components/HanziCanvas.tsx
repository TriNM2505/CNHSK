import HanziWriter, { type CharacterJson } from 'hanzi-writer'
import { useEffect, useRef } from 'react'

const PINNED_DATA_URL = 'https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1'
const CANVAS_SIZE = 300
const MIN_CANVAS_SIZE = 240

export interface HanziCanvasProps {
  character: string
  onDataError?: (error?: Error | string) => void
}

function dataBaseUrl() {
  const configured = import.meta.env.VITE_HANZI_DATA_URL?.trim().replace(/\/+$/, '')
  return configured || PINNED_DATA_URL
}

function isCharacterJson(value: unknown): value is CharacterJson {
  if (!value || typeof value !== 'object') return false
  const data = value as Partial<CharacterJson>
  return (
    Array.isArray(data.strokes) &&
    data.strokes.every((stroke) => typeof stroke === 'string') &&
    Array.isArray(data.medians) &&
    data.medians.every(
      (median) =>
        Array.isArray(median) &&
        median.every(
          (point) =>
            Array.isArray(point) && point.length >= 2 && point.every((coordinate) => typeof coordinate === 'number'),
        ),
    )
  )
}

async function loadCharacterData(character: string): Promise<CharacterJson> {
  const url = `${dataBaseUrl()}/${encodeURIComponent(character)}.json`
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Could not load stroke data (${response.status})`)

  const data: unknown = await response.json()
  if (!isCharacterJson(data)) throw new Error('Invalid stroke data')
  return data
}

export function HanziCanvas({ character, onDataError }: HanziCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onDataErrorRef = useRef(onDataError)
  onDataErrorRef.current = onDataError

  useEffect(() => {
    const container = containerRef.current
    if (!container) return undefined

    let active = true
    let writer: HanziWriter
    try {
      writer = HanziWriter.create(container, character, {
        charDataLoader: loadCharacterData,
        height: CANVAS_SIZE,
        onLoadCharDataError: (error) => {
          if (active) onDataErrorRef.current?.(error instanceof Error || typeof error === 'string' ? error : undefined)
        },
        padding: 20,
        renderer: 'canvas',
        showCharacter: true,
        showOutline: true,
        width: CANVAS_SIZE,
      })
    } catch (error) {
      onDataErrorRef.current?.(error instanceof Error ? error : String(error))
      return undefined
    }

    const resizeObserver = new ResizeObserver((entries) => {
      if (!active) return
      const observedWidth = entries[0]?.contentRect.width || container.clientWidth || CANVAS_SIZE
      const size = Math.max(MIN_CANVAS_SIZE, Math.min(CANVAS_SIZE, observedWidth))
      writer.updateDimensions({ height: size, width: size })
    })
    resizeObserver.observe(container)

    return () => {
      active = false
      resizeObserver.disconnect()
      writer.cancelQuiz()
      writer._renderState?.cancelAll()
      writer._hanziWriterRenderer?.destroy()
      writer.target.node.remove()
    }
  }, [character])

  return (
    <div
      aria-label={`Khung viết chữ ${character}`}
      className="mx-auto aspect-square w-full min-w-[240px] max-w-[300px] overflow-hidden rounded-md border border-line bg-surface"
      data-testid="hanzi-canvas"
      ref={containerRef}
    />
  )
}
