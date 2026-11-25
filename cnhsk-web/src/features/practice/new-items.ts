/** Item numbers the server marked as new bank questions (AI drafts approved by a teacher). */
export function newItemNos(
  meta: Readonly<Record<string, unknown>> | null | undefined,
): readonly number[] {
  const value = meta?.newItems
  return Array.isArray(value) ? value.filter((no): no is number => typeof no === 'number') : []
}

export function isNewItem(meta: Readonly<Record<string, unknown>> | null | undefined, no: number) {
  return newItemNos(meta).includes(no)
}
