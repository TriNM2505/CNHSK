import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface TabItem {
  disabled?: boolean
  id: string
  label: ReactNode
  panel: ReactNode
}

export interface TabsProps {
  items: readonly TabItem[]
  label: string
  onChange: (id: string) => void
  value: string
}

export function Tabs({ items, label, onChange, value }: TabsProps) {
  const instanceId = useId()
  const tabRefs = useRef(new Map<string, HTMLButtonElement>())
  const activeItem =
    items.find((item) => item.id === value && !item.disabled) ??
    items.find((item) => !item.disabled)

  const activateTab = (id: string) => {
    onChange(id)
    tabRefs.current.get(id)?.focus()
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, currentId: string) => {
    const enabledItems = items.filter((item) => !item.disabled)
    const currentIndex = enabledItems.findIndex((item) => item.id === currentId)
    let nextItem: TabItem | undefined

    if (event.key === 'ArrowRight') {
      nextItem = enabledItems[(currentIndex + 1) % enabledItems.length]
    } else if (event.key === 'ArrowLeft') {
      nextItem = enabledItems[(currentIndex - 1 + enabledItems.length) % enabledItems.length]
    } else if (event.key === 'Home') {
      nextItem = enabledItems[0]
    } else if (event.key === 'End') {
      nextItem = enabledItems[enabledItems.length - 1]
    }

    if (nextItem) {
      event.preventDefault()
      activateTab(nextItem.id)
    }
  }

  return (
    <div>
      <div aria-label={label} className="flex overflow-x-auto border-b border-line" role="tablist">
        {items.map((item) => {
          const selected = item.id === activeItem?.id
          const tabId = `${instanceId}-${item.id}-tab`
          const panelId = `${instanceId}-${item.id}-panel`

          return (
            <button
              aria-controls={panelId}
              aria-selected={selected}
              className={cn(
                'min-h-touch shrink-0 border-b-2 border-transparent px-4 py-2 text-label text-ink-secondary transition-colors hover:bg-brand-50 hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus',
                selected && 'border-brand-600 text-brand-700',
              )}
              disabled={item.disabled}
              id={tabId}
              key={item.id}
              onClick={() => activateTab(item.id)}
              onKeyDown={(event) => handleKeyDown(event, item.id)}
              ref={(node) => {
                if (node) {
                  tabRefs.current.set(item.id, node)
                } else {
                  tabRefs.current.delete(item.id)
                }
              }}
              role="tab"
              tabIndex={selected ? 0 : -1}
              type="button"
            >
              {item.label}
            </button>
          )
        })}
      </div>
      {activeItem ? (
        <div
          aria-labelledby={`${instanceId}-${activeItem.id}-tab`}
          className="py-4"
          id={`${instanceId}-${activeItem.id}-panel`}
          role="tabpanel"
          tabIndex={0}
        >
          {activeItem.panel}
        </div>
      ) : null}
    </div>
  )
}
