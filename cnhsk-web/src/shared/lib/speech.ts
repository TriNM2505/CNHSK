/**
 * Reads Chinese text with the browser's own voice (Web Speech API). Used where content has no recorded audio;
 * Chrome and Edge ship good Chinese voices, other browsers may not speak at all.
 * Resolves when speaking ends; rejects when the browser cannot speak.
 */
export function speakChinese(text: string, rate = 0.8): Promise<void> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
      reject(new Error('Trình duyệt không hỗ trợ đọc tiếng Trung.'))
      return
    }
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = rate
    const voice = window.speechSynthesis
      .getVoices()
      .find((candidate) => candidate.lang.toLowerCase().startsWith('zh'))
    if (voice) utterance.voice = voice
    utterance.onend = () => resolve()
    utterance.onerror = () => reject(new Error('Không đọc được câu này.'))
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utterance)
  })
}
