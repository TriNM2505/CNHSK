export function downloadBlob(blob: Blob, fileName?: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName ?? ''
  link.hidden = true
  document.body.append(link)

  try {
    link.click()
  } finally {
    link.remove()
    URL.revokeObjectURL(objectUrl)
  }
}
