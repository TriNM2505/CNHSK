const HAN_PATTERN = /\p{Script=Han}/u

/** Giá trị CSV có chữ Hán thì gắn ngôn ngữ và font Hán để hiển thị đúng nét. */
export function CellText({ value }: { value?: string | null }) {
  if (value === null || value === undefined || value === '') {
    return <span className="text-ink-muted">—</span>
  }
  if (HAN_PATTERN.test(value)) {
    return (
      <span className="break-words font-hanzi" lang="zh-CN">
        {value}
      </span>
    )
  }
  return <span className="break-words">{value}</span>
}
