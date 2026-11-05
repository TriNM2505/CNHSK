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

export const markupAnswer: AskResult = {
  answer:
    '<b>Chữ đậm mẫu</b>\n<script>window.assistantInjected = true</script>\n<img src=x onerror="window.assistantInjected=true">',
  outOfScope: false,
}

export const assistantErrors = [
  {
    status: 429,
    errorCode: 'RATE_LIMITED',
    message: 'Thông báo giới hạn từ máy chủ',
    expected: 'Bạn đã hỏi nhiều trong hôm nay, quay lại ngày mai',
  },
  {
    status: 403,
    errorCode: 'ASSISTANT_BLOCKED_DURING_TEST',
    message: 'Thông báo bài kiểm tra từ máy chủ',
    expected: 'Trợ lý tắt trong lúc làm bài',
  },
  {
    status: 403,
    errorCode: 'FORBIDDEN',
    message: 'Bạn chưa có quyền sử dụng tính năng này.',
    expected: 'Bạn chưa có quyền sử dụng tính năng này.',
  },
  {
    status: 403,
    errorCode: 'CSRF_TOKEN_INVALID',
    message: 'Vui lòng kiểm tra lại phiên đăng nhập.',
    expected: 'Vui lòng kiểm tra lại phiên đăng nhập.',
  },
  {
    status: 503,
    errorCode: 'ASSISTANT_UNAVAILABLE',
    message: 'Thông báo dịch vụ từ máy chủ',
    expected: 'Trợ lý tạm thời chưa trả lời được, bạn thử lại sau',
  },
  {
    status: 422,
    errorCode: 'VALIDATION_FAILED',
    message: 'Câu hỏi chưa hợp lệ.',
    expected: 'Câu hỏi chưa hợp lệ.',
  },
  {
    status: 503,
    errorCode: 'OTHER_SERVICE_FAILURE',
    message: 'Thông báo lỗi khác từ máy chủ.',
    expected: 'Thông báo lỗi khác từ máy chủ.',
  },
] as const
