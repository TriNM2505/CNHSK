import type { KnowledgeNode } from '@/features/question-bank/types'

function node(
  kid: string,
  nameVi: string,
  prereqKids: string[] = [],
  supportKids: string[] = [],
): KnowledgeNode {
  return {
    domain: kid.slice(0, 2) as KnowledgeNode['domain'],
    hskLevel: Number(kid[2]),
    kid,
    nameVi,
    nameZh: '示例',
    prereqKids,
    supportKids,
  }
}

/** Small hand-written tree in the shape of the spec's 把 example; not taken from any exam data. */
export const SAMPLE_TREE: KnowledgeNode[] = [
  node('NP1-914', 'Trạng ngữ đứng trước động từ'),
  node('NP1-918', 'Trợ từ cuối câu'),
  node('NP2-906', 'Trợ từ sau động từ', ['NP1-914', 'NP1-918']),
  node('NP2-913', 'Bổ ngữ kết quả', ['NP2-906']),
  node('NP3-904', 'Câu xử lý đối tượng', ['NP2-913', 'NP2-906', 'XX9-999', 'NP3-904']),
  node('NL3-906', 'Kể lại việc đã làm', [], ['NP3-904', 'NP2-906']),
  node('TV2-907', 'Từ vựng đi lại'),
]
