import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

import {
  getPreviewStates,
  previewRegistry,
  type PreviewEntry,
  type PreviewState,
} from '@/app/preview/registry'
import { Button } from '@/shared/ui'

const previewWidths = [390, 768, 1280] as const
type PreviewWidth = (typeof previewWidths)[number]

const stateLabels: Record<PreviewState, string> = {
  loading: 'Đang tải',
  empty: 'Rỗng',
  error: 'Lỗi',
  ready: 'Có dữ liệu',
}

function isPreviewWidth(value: string | null): value is `${PreviewWidth}` {
  return previewWidths.some((width) => String(width) === value)
}

function preferredState(entry: PreviewEntry, requestedState: string | null): PreviewState {
  const states = getPreviewStates(entry)
  const validState = states.find((state) => state === requestedState)

  if (validState) {
    return validState
  }

  return states.includes('ready') ? 'ready' : (states[0] ?? 'ready')
}

export function PreviewPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const firstEntry = previewRegistry[0]!
  const selectedEntry =
    previewRegistry.find(({ id }) => id === searchParams.get('view')) ?? firstEntry

  const selectedState = preferredState(selectedEntry, searchParams.get('state'))
  const widthParam = searchParams.get('w')
  const selectedWidth = (isPreviewWidth(widthParam) ? Number(widthParam) : 1280) as PreviewWidth
  const entryStates = getPreviewStates(selectedEntry)
  const currentQuery = searchParams.toString()

  useEffect(() => {
    const canonicalParams = new URLSearchParams(searchParams)
    canonicalParams.set('view', selectedEntry.id)
    canonicalParams.set('state', selectedState)
    canonicalParams.set('w', String(selectedWidth))

    if (canonicalParams.toString() !== currentQuery) {
      setSearchParams(canonicalParams, { replace: true })
    }
  }, [currentQuery, searchParams, selectedEntry.id, selectedState, selectedWidth, setSearchParams])

  const updateQuery = (updates: Record<string, string>) => {
    const nextParams = new URLSearchParams(searchParams)

    Object.entries(updates).forEach(([name, value]) => nextParams.set(name, value))
    setSearchParams(nextParams)
  }

  const selectEntry = (entry: PreviewEntry) => {
    const states = getPreviewStates(entry)
    const nextState = states.includes('ready') ? 'ready' : (states[0] ?? 'ready')
    updateQuery({ state: nextState, view: entry.id })
  }

  const componentEntries = previewRegistry.filter(({ group }) => group === 'component')
  const screenEntries = previewRegistry.filter(({ group }) => group === 'screen')
  const frameSource = `/__preview/frame/${encodeURIComponent(selectedEntry.id)}/${selectedState}`

  return (
    <main className="min-h-screen bg-canvas p-4 lg:p-6">
      <div className="mx-auto grid max-w-content gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
        <aside className="rounded-md border border-line bg-surface p-4 lg:sticky lg:top-6 lg:max-h-screen lg:overflow-y-auto">
          <h1 className="text-h3 text-ink">Xem trước giao diện</h1>
          <nav aria-label="Danh sách nội dung xem trước" className="mt-5">
            <PreviewGroup
              entries={componentEntries}
              onSelect={selectEntry}
              selectedId={selectedEntry.id}
              title="Component"
            />
            <PreviewGroup
              emptyLabel="Chưa có màn hình."
              entries={screenEntries}
              onSelect={selectEntry}
              selectedId={selectedEntry.id}
              title="Màn hình"
            />
          </nav>
        </aside>

        <section aria-labelledby="preview-title" className="min-w-0">
          <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-caption uppercase text-ink-muted">
                {selectedEntry.group === 'component'
                  ? 'Component'
                  : (selectedEntry.scr ?? 'Màn hình')}
              </p>
              <h2 className="text-h2 text-ink" id="preview-title">
                {selectedEntry.title}
              </h2>
            </div>

            <div className="flex flex-wrap gap-2" role="group" aria-label="Độ rộng khung xem">
              {previewWidths.map((width) => (
                <Button
                  aria-pressed={selectedWidth === width}
                  key={width}
                  onClick={() => updateQuery({ w: String(width) })}
                  size="sm"
                  variant={selectedWidth === width ? 'secondary' : 'outline'}
                >
                  {width} px
                </Button>
              ))}
            </div>
          </div>

          {entryStates.length > 1 ? (
            <div
              aria-label="Trạng thái khung xem"
              className="mb-4 flex flex-wrap gap-2"
              role="group"
            >
              {entryStates.map((state) => (
                <Button
                  aria-pressed={selectedState === state}
                  key={state}
                  onClick={() => updateQuery({ state })}
                  size="sm"
                  variant={selectedState === state ? 'secondary' : 'outline'}
                >
                  {stateLabels[state]}
                </Button>
              ))}
            </div>
          ) : null}

          <div className="max-w-full overflow-auto rounded-md border border-line bg-surface p-2">
            <iframe
              className="block h-96 max-w-none rounded-sm border border-line bg-canvas lg:h-screen"
              key={frameSource}
              src={frameSource}
              style={{ width: `${selectedWidth}px` }}
              title={`Xem trước ${selectedEntry.title}`}
            />
          </div>
        </section>
      </div>
    </main>
  )
}

interface PreviewGroupProps {
  emptyLabel?: string
  entries: readonly PreviewEntry[]
  onSelect: (entry: PreviewEntry) => void
  selectedId: string
  title: string
}

function PreviewGroup({ emptyLabel, entries, onSelect, selectedId, title }: PreviewGroupProps) {
  return (
    <section className="mb-6 last:mb-0">
      <h2 className="mb-2 text-label text-ink-secondary">{title}</h2>
      {entries.length ? (
        <ul className="space-y-1">
          {entries.map((entry) => (
            <li key={entry.id}>
              <button
                aria-current={selectedId === entry.id ? 'page' : undefined}
                className="min-h-touch w-full rounded-sm px-3 py-2 text-left text-body-sm text-ink transition-colors hover:bg-brand-50 hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus aria-current:bg-brand-50 aria-current:font-semibold aria-current:text-brand-700"
                onClick={() => onSelect(entry)}
                type="button"
              >
                {entry.title}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="px-3 py-2 text-body-sm text-ink-muted">{emptyLabel}</p>
      )}
    </section>
  )
}
