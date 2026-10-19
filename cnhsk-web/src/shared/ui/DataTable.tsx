import type { KeyboardEvent, ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface DataTableColumn<Row> {
  header: ReactNode
  hideOnMobile?: boolean
  id: string
  numeric?: boolean
  render: (row: Row) => ReactNode
}

interface DataTableBaseProps<Row> {
  caption: string
  captionVisible?: boolean
  className?: string
  columns: readonly DataTableColumn<Row>[]
  rowKey: (row: Row) => string | number
  rows: readonly Row[]
}

interface InteractiveDataTableProps<Row> extends DataTableBaseProps<Row> {
  getRowLabel: (row: Row) => string
  onRowClick: (row: Row) => void
}

interface StaticDataTableProps<Row> extends DataTableBaseProps<Row> {
  getRowLabel?: never
  onRowClick?: never
}

export type DataTableProps<Row> = InteractiveDataTableProps<Row> | StaticDataTableProps<Row>

export function DataTable<Row>(props: DataTableProps<Row>) {
  const { caption, captionVisible = false, className, columns, rowKey, rows, onRowClick } = props

  const handleKeyDown = (event: KeyboardEvent, row: Row) => {
    if (!onRowClick || (event.key !== 'Enter' && event.key !== ' ')) {
      return
    }

    event.preventDefault()
    onRowClick(row)
  }

  return (
    <div className={className}>
      <div className="hidden overflow-hidden rounded-md border border-line bg-surface lg:block">
        <table className="w-full border-collapse text-body-sm">
          <caption className={cn(captionVisible ? 'p-3 text-left text-label text-ink' : 'sr-only')}>
            {caption}
          </caption>
          <thead>
            <tr className="bg-surface-subtle">
              {columns.map((column) => (
                <th
                  className={cn(
                    'border-b border-line p-3 text-left text-label text-ink',
                    column.numeric && 'text-right',
                  )}
                  key={column.id}
                  scope="col"
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                aria-label={onRowClick ? props.getRowLabel(row) : undefined}
                className={cn(
                  'border-b border-line last:border-b-0',
                  onRowClick &&
                    'cursor-pointer transition-colors hover:bg-brand-50 focus:bg-brand-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-focus',
                )}
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={onRowClick ? (event) => handleKeyDown(event, row) : undefined}
                tabIndex={onRowClick ? 0 : undefined}
              >
                {columns.map((column) => (
                  <td
                    className={cn(
                      'p-3 align-top text-ink',
                      column.numeric && 'text-right tabular-nums',
                    )}
                    key={column.id}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section aria-label={caption} className="space-y-3 lg:hidden">
        {rows.map((row) => {
          const cardProps = onRowClick
            ? {
                'aria-label': props.getRowLabel(row),
                onClick: () => onRowClick(row),
                onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => handleKeyDown(event, row),
                role: 'button',
                tabIndex: 0,
              }
            : {}

          return (
            <div
              {...cardProps}
              className={cn(
                'rounded-md border border-line bg-surface p-4',
                onRowClick &&
                  'cursor-pointer transition-colors hover:border-brand-600 focus:outline-none focus:ring-2 focus:ring-focus focus:ring-offset-2',
              )}
              key={rowKey(row)}
            >
              <dl className="space-y-2">
                {columns.map((column) =>
                  column.hideOnMobile ? null : (
                    <div
                      className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-3"
                      key={column.id}
                    >
                      <dt className="text-label text-ink-secondary">{column.header}:</dt>
                      <dd
                        className={cn(
                          'min-w-0 text-body-sm text-ink',
                          column.numeric && 'tabular-nums',
                        )}
                      >
                        {column.render(row)}
                      </dd>
                    </div>
                  ),
                )}
              </dl>
            </div>
          )
        })}
      </section>
    </div>
  )
}
