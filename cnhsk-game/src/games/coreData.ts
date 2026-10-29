import { drawQueue } from '../engine/arcade'
import { lexemesFor, type HskLevel, type Lexeme } from '../data/words'

// Local demo metadata. Production radical values must come from ContentLookup.pool.
const radicalRows: [string, string][] = [
  ['人', '人'], ['口', '口'], ['日', '日'], ['月', '月'], ['木', '木'],
  ['水', '水'], ['火', '火'], ['土', '土'], ['女', '女'], ['子', '子'],
  ['心', '心'], ['山', '山'], ['天', '大'], ['王', '王'],
  ['字', '宀'], ['茶', '艹'], ['吃', '口'], ['喝', '口'], ['看', '目'],
  ['听', '口'], ['说', '讠'], ['读', '讠'], ['写', '冖'],
  ['住', '亻'], ['好', '女'], ['多', '夕'], ['高', '高'], ['钱', '钅'],
  ['岁', '山'], ['问', '门'], ['玩', '王'], ['东', '一'], ['北', '匕'],
  ['走', '走'], ['到', '刂'], ['路', '足'], ['桥', '木'], ['河', '氵'],
  ['海', '氵'], ['湖', '氵'], ['树', '木'], ['花', '艹'], ['草', '艹'],
  ['鸟', '鸟'], ['鱼', '鱼'], ['狗', '犭'], ['猫', '犭'], ['马', '马'],
  ['牛', '牛'], ['药', '艹'], ['病', '疒'], ['头', '大'], ['手', '手'],
  ['脚', '月'], ['饿', '饣'], ['渴', '氵'], ['早', '日'], ['晚', '日'],
  ['快', '忄'], ['慢', '忄'], ['远', '辶'], ['近', '辶'], ['难', '隹'],
]

export const radicalByChar = new Map(radicalRows)
export const radicalLabels = [...new Set(radicalRows.map(([, radical]) => radical))]

export function pool(level: HskLevel): Lexeme[] { return lexemesFor(level) }

export function uniqueBy<T>(items: readonly T[], key: (item: T) => string): T[] {
  const seen = new Set<string>()
  return items.filter((item) => { const value = key(item); if (seen.has(value)) return false; seen.add(value); return true })
}

export function choices<T>(answer: T, alternatives: readonly T[], count: number, key: (item: T) => string): T[] {
  const wrong = uniqueBy(alternatives.filter((item) => key(item) !== key(answer)), key)
  return drawQueue([answer, ...drawQueue(wrong, count - 1)], count)
}

export function pinyinBoards(level: HskLevel): Lexeme[][] {
  const words = uniqueBy(pool(level).filter((word) => word.kind === 'WORD'), (word) => word.pinyinKey)
  return Array.from({ length: 3 }, (_, index) => drawQueue(words.slice(index * 6, index * 6 + 6), 6))
}

export function radicalPool(level: HskLevel): Lexeme[] {
  return pool(level).filter((word) => word.kind === 'CHAR' && radicalByChar.has(word.text))
}
