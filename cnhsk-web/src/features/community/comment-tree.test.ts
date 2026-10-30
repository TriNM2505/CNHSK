import { describe, expect, it } from 'vitest'
import { buildCommentTree } from './comment-tree'

const entry = (id: number, parentId: number | null, depth: number) => ({ id, postId: 42, parentId, depth })
describe('flat comments tree', () => {
  it('FR018_preserves roots and sibling order even when parent follows child', () => {
    const tree = buildCommentTree([entry(3, 2, 2), entry(4, 2, 2), entry(2, 1, 1), entry(1, null, 0), entry(5, 3, 3)], 42)
    expect(tree[0]?.value.id).toBe(1)
    expect(tree[0]?.children[0]?.children.map(({ value }) => value.id)).toEqual([3, 4])
    expect(tree[0]?.children[0]?.children[0]?.children[0]?.value.depth).toBe(3)
  })
  it.each([
    [entry(1, null, 0), entry(1, null, 0)], [entry(2, 99, 1)],
    [entry(1, 2, 1), entry(2, 1, 2)], [entry(1, 1, 1)],
    [entry(1, null, 1)], [entry(1, null, 0), entry(2, 1, 4)],
    [{ ...entry(1, null, 0), postId: 43 }],
  ])('FR018_rejects malformed tree %j without repairing or inventing entries', (...entries) => {
    expect(() => buildCommentTree(entries, 42)).toThrow()
  })
  it('FR022_refreshed depth-three reply is a sibling under the returned parent (AT-21)', () => {
    const tree = buildCommentTree([entry(1, null, 0), entry(2, 1, 1), entry(3, 2, 2), entry(4, 3, 3), entry(5, 3, 3)], 42)
    const siblings = tree[0]?.children[0]?.children[0]?.children
    expect(siblings?.map(({ value }) => [value.id, value.parentId, value.depth])).toEqual([[4, 3, 3], [5, 3, 3]])
    expect(siblings?.every(({ children }) => children.length === 0)).toBe(true)
  })
})
