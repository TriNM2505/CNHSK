import { cleanup, render, waitFor } from '@testing-library/react'
import HanziWriter, { type HanziWriterOptions } from 'hanzi-writer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { HanziCanvas } from './HanziCanvas'

const writerMocks = vi.hoisted(() => ({ create: vi.fn() }))
vi.mock('hanzi-writer', () => ({ default: { create: writerMocks.create } }))

type WriterOptions = Partial<HanziWriterOptions>
type MockWriter = {
  cancelQuiz: ReturnType<typeof vi.fn>
  options?: WriterOptions
  rendererDestroy: ReturnType<typeof vi.fn>
  renderStateCancel: ReturnType<typeof vi.fn>
  targetRemove: ReturnType<typeof vi.fn>
  updateDimensions: ReturnType<typeof vi.fn>
}

const writers: MockWriter[] = []
const observers: ResizeObserver[] = []

function createMockWriter(options?: WriterOptions) {
  const writer: MockWriter = {
    cancelQuiz: vi.fn(),
    options,
    rendererDestroy: vi.fn(),
    renderStateCancel: vi.fn(),
    targetRemove: vi.fn(),
    updateDimensions: vi.fn(),
  }
  writers.push(writer)
  return writer
}

function capturedOptions(index = 0) {
  return writers[index]!.options!
}

beforeEach(() => {
  writers.length = 0
  observers.length = 0
  vi.stubEnv('VITE_HANZI_DATA_URL', '')
  vi.stubGlobal(
    'ResizeObserver',
    class {
      callback: ResizeObserverCallback
      disconnect = vi.fn()
      observe = vi.fn()
      unobserve = vi.fn()

      constructor(callback: ResizeObserverCallback) {
        this.callback = callback
        observers.push(this as unknown as ResizeObserver)
      }
    },
  )
  writerMocks.create.mockImplementation((_container: HTMLElement, _character: string, options: WriterOptions) => {
    const instance = createMockWriter(options)
    const target = document.createElement('canvas')
    Object.defineProperty(target, 'remove', { value: instance.targetRemove })
    Object.assign(instance, {
      _hanziWriterRenderer: { destroy: instance.rendererDestroy },
      _renderState: { cancelAll: instance.renderStateCancel },
      cancelQuiz: instance.cancelQuiz,
      target: { node: target },
      updateDimensions: instance.updateDimensions,
    })
    return instance as unknown as HanziWriter
  })
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

describe('HanziCanvas', () => {
  it('FR030_usesConfiguredPinnedDataUrlAndEncodesCharacter', async () => {
    vi.stubEnv('VITE_HANZI_DATA_URL', 'https://example.test/hanzi-data/')
    const fetchMock = vi.fn().mockResolvedValue({
      json: async () => ({ medians: [[[1, 2]]], strokes: ['M 1 2'] }),
      ok: true,
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<HanziCanvas character="好" />)
    const loader = capturedOptions().charDataLoader!
    await loader('好', vi.fn(), vi.fn())

    expect(fetchMock).toHaveBeenCalledWith('https://example.test/hanzi-data/%E5%A5%BD.json')
  })

  it('FR030_usesPinnedFallbackWhenEnvironmentUrlIsEmpty', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      json: async () => ({ medians: [[[1, 2]]], strokes: ['M 1 2'] }),
      ok: true,
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<HanziCanvas character="你" />)
    await capturedOptions().charDataLoader!('你', vi.fn(), vi.fn())

    expect(fetchMock).toHaveBeenCalledWith(
      'https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/%E4%BD%A0.json',
    )
  })

  it.each([
    ['404 response', async () => ({ json: async () => ({}), ok: false, status: 404 })],
    ['network failure', async () => Promise.reject(new Error('offline'))],
    ['invalid JSON', async () => ({ json: async () => Promise.reject(new SyntaxError('invalid json')), ok: true })],
    ['invalid data shape', async () => ({ json: async () => ({ strokes: [] }), ok: true })],
  ])('FR030_reports_%s_through_onDataError', async (_label, response) => {
    const onDataError = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockImplementation(response))
    render(<HanziCanvas character="字" onDataError={onDataError} />)

    const options = capturedOptions()
    let loaderError: unknown
    await Promise.resolve(options.charDataLoader!('字', vi.fn(), vi.fn())).catch((error: unknown) => {
      loaderError = error
    })
    expect(loaderError).toBeInstanceOf(Error)
    options.onLoadCharDataError?.(loaderError as Error)

    expect(onDataError).toHaveBeenCalledWith(loaderError)
  })

  it('FR030_ignoresOldLoadErrorAfterCharacterChangesAndDisposesOldWriter', () => {
    const onDataError = vi.fn()
    const view = render(<HanziCanvas character="一" onDataError={onDataError} />)
    const oldOptions = capturedOptions(0)

    view.rerender(<HanziCanvas character="二" onDataError={onDataError} />)
    oldOptions.onLoadCharDataError?.(new Error('late failure'))

    expect(onDataError).not.toHaveBeenCalled()
    expect(writers[0]!.cancelQuiz).toHaveBeenCalledOnce()
    expect(writers[0]!.renderStateCancel).toHaveBeenCalledOnce()
    expect(writers[0]!.rendererDestroy).toHaveBeenCalledOnce()
    expect(writers[0]!.targetRemove).toHaveBeenCalledOnce()
  })

  it('FR030_disposesWriterAndIgnoresCallbacksAfterUnmount', () => {
    const onDataError = vi.fn()
    const view = render(<HanziCanvas character="三" onDataError={onDataError} />)
    const options = capturedOptions()

    view.unmount()
    options.onLoadCharDataError?.(new Error('late failure'))

    expect(onDataError).not.toHaveBeenCalled()
    expect(writers[0]!.cancelQuiz).toHaveBeenCalledOnce()
    expect(writers[0]!.renderStateCancel).toHaveBeenCalledOnce()
    expect(writers[0]!.rendererDestroy).toHaveBeenCalledOnce()
  })

  it('FR030_resizesWithin300pxAndEnforces240pxMinimum', async () => {
    render(<HanziCanvas character="四" />)
    const observer = observers[0]! as unknown as { callback: ResizeObserverCallback }
    const container = document.querySelector('[data-testid="hanzi-canvas"]')!

    expect(container).toHaveClass('max-w-[300px]', 'min-w-[240px]')

    observer.callback([{ contentRect: { width: 280 } } as ResizeObserverEntry], observers[0]!)
    await waitFor(() => expect(writers[0]!.updateDimensions).toHaveBeenCalledWith({ height: 280, width: 280 }))

    observer.callback([{ contentRect: { width: 180 } } as ResizeObserverEntry], observers[0]!)
    await waitFor(() => expect(writers[0]!.updateDimensions).toHaveBeenLastCalledWith({ height: 240, width: 240 }))

    observer.callback([{ contentRect: { width: 420 } } as ResizeObserverEntry], observers[0]!)
    await waitFor(() => expect(writers[0]!.updateDimensions).toHaveBeenLastCalledWith({ height: 300, width: 300 }))
  })
})
