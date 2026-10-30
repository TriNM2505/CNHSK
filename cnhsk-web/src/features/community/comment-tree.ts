export type TreeEntry = { id: number; postId: number; parentId: number | null; depth: number }
export type CommentNode<T> = { value: T; children: CommentNode<T>[] }

export function buildCommentTree<T extends TreeEntry>(entries: readonly T[], postId: number): CommentNode<T>[] {
  const nodes = new Map<number, CommentNode<T>>()
  for (const entry of entries) {
    if (!Number.isSafeInteger(entry.id) || entry.id <= 0 || nodes.has(entry.id) || entry.postId !== postId || !Number.isInteger(entry.depth) || entry.depth < 0 || entry.depth > 3) throw new Error('Dữ liệu bình luận không hợp lệ.')
    nodes.set(entry.id, { value: entry, children: [] })
  }
  const roots: CommentNode<T>[] = []
  for (const entry of entries) {
    const node = nodes.get(entry.id)!
    if (entry.parentId === null) {
      if (entry.depth !== 0) throw new Error('Cấp bình luận không hợp lệ.')
      roots.push(node)
    } else {
      const parent = nodes.get(entry.parentId)
      // Strictly increasing depth also rejects self-links and cycles.
      if (!parent || parent.value.depth + 1 !== entry.depth) throw new Error('Quan hệ bình luận không hợp lệ.')
      parent.children.push(node)
    }
  }
  return roots
}
