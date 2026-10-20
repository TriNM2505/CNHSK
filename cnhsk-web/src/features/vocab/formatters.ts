const topicDateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
})

export function formatTopicDateTime(value: string) {
  return topicDateTimeFormatter.format(new Date(value))
}
