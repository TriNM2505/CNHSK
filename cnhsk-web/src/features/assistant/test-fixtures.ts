import type { AskResult } from './types'

export const systemAnswer: AskResult = {
  answer: 'Bạn có thể mở mục Chủ đề để chọn nội dung muốn học.',
  outOfScope: false,
}

export const knowledgeAnswer: AskResult = {
  answer:
    'Mình chỉ hỗ trợ cách dùng CNHSK. Để tra nghĩa từ hãy dùng Tra từ điển, để dịch câu hãy dùng Dịch.',
  outOfScope: true,
}
