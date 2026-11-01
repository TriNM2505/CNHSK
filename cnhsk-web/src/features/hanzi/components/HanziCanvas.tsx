import HanziWriter, { type CharacterJson, type QuizOptions } from 'hanzi-writer'
import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

const PINNED_DATA_URL = 'https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1'
const CANVAS_SIZE = 300
const MIN_CANVAS_SIZE = 240

export interface HanziCanvasProps {
  character: string
  onDataError?: (error?: Error | string) => void
  onReady?: () => void
  showCharacter?: boolean
  showOutline?: boolean
}

export interface HanziCanvasHandle {
  animateCharacter: (onComplete: (canceled: boolean) => void) => void
  cancelAnimations: () => void
  cancelQuiz: () => void
  quiz: (options: Partial<QuizOptions>) => void
  setVisibility: (showCharacter: boolean, showOutline: boolean) => void
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

export const HanziCanvas = forwardRef<HanziCanvasHandle, HanziCanvasProps>(function HanziCanvas(
  { character, onDataError, onReady, showCharacter = true, showOutline = true },
  ref,
) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onDataErrorRef = useRef(onDataError)
  const onReadyRef = useRef(onReady)
  const writerRef = useRef<HanziWriter | null>(null)
  const activeRef = useRef(false)
  const animationGenerationRef = useRef(0)
  const initialPresentationRef = useRef({ showCharacter, showOutline })
  onDataErrorRef.current = onDataError
  onReadyRef.current = onReady

  useImperativeHandle(ref, () => ({
    animateCharacter: (onComplete) => {
      const writer = writerRef.current
      if (!writer || !activeRef.current) return
      const generation = ++animationGenerationRef.current
      void writer.animateCharacter({
        onComplete: (result) => {
          if (activeRef.current && generation === animationGenerationRef.current) {
            onComplete(result.canceled)
          }
        },
      })
    },
    cancelQuiz: () => {
      animationGenerationRef.current += 1
      writerRef.current?.cancelQuiz()
    },
    cancelAnimations: () => {
      animationGenerationRef.current += 1
      writerRef.current?._renderState?.cancelAll()
    },
    quiz: (options) => {
      const writer = writerRef.current
      if (!writer || !activeRef.current) return
      writer.cancelQuiz()
      writer.quiz(options)
    },
    setVisibility: (nextShowCharacter, nextShowOutline) => {
      const writer = writerRef.current
      if (!writer || !activeRef.current) return
      if (nextShowCharacter) void writer.showCharacter({ duration: 0 })
      else void writer.hideCharacter({ duration: 0 })
      if (nextShowOutline) void writer.showOutline({ duration: 0 })
      else void writer.hideOutline({ duration: 0 })
    },
  }), [])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return undefined

    let active = true
    activeRef.current = true
    let writer: HanziWriter
    try {
      writer = HanziWriter.create(container, character, {
        charDataLoader: loadCharacterData,
        height: CANVAS_SIZE,
        onLoadCharDataError: (error) => {
          if (active) onDataErrorRef.current?.(error instanceof Error || typeof error === 'string' ? error : undefined)
        },
        onLoadCharDataSuccess: () => {
          if (active) onReadyRef.current?.()
        },
        padding: 20,
        renderer: 'canvas',
        showCharacter: initialPresentationRef.current.showCharacter,
        showOutline: initialPresentationRef.current.showOutline,
        width: CANVAS_SIZE,
      })
      writerRef.current = writer
    } catch (error) {
      activeRef.current = false
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
      activeRef.current = false
      animationGenerationRef.current += 1
      writerRef.current = null
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
})
