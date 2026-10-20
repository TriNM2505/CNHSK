export const IMPORT_ERROR_PAGE_SIZE = 20

function parsePage(value: string | null): number {
  if (!value || !/^\d+$/.test(value)) return 0
  const page = Number(value)
  return Number.isSafeInteger(page) ? page : 0
}

export function toImportReportSearchParams(
  page: number,
  current?: URLSearchParams,
): URLSearchParams {
  const result = new URLSearchParams(current)
  result.delete('page')
  if (page > 0) result.set('page', String(page))
  return result
}

export function parseImportReportSearchParams(searchParams: URLSearchParams): {
  canonical: URLSearchParams
  needsCleanup: boolean
  page: number
} {
  const page = parsePage(searchParams.get('page'))
  const canonical = toImportReportSearchParams(page, searchParams)
  return {
    canonical,
    needsCleanup: canonical.toString() !== searchParams.toString(),
    page,
  }
}
