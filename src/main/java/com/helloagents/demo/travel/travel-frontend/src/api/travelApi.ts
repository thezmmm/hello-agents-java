import type { TravelRequest, SseEvent } from '../types/travel.ts'

export function streamTravelPlan(
  request: TravelRequest,
  onEvent: (event: SseEvent) => void,
  onError: (err: string) => void,
  onDone: () => void
): () => void {
  const controller = new AbortController()

  fetch('/api/travel/plan', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: controller.signal
  }).then(async (res) => {
    if (!res.ok) {
      onError(`请求失败：HTTP ${res.status}`)
      return
    }
    if (!res.body) {
      onError('响应体为空')
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const payload = line.slice(5).trim()
          if (!payload) continue
          try {
            const event: SseEvent = JSON.parse(payload)
            onEvent(event)
          } catch {
            // ignore malformed lines
          }
        }
      }
    }
    onDone()
  }).catch((err) => {
    if (err.name !== 'AbortError') onError(err.message)
  })

  return () => controller.abort()
}