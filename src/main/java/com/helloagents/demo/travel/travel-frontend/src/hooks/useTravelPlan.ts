import { useState, useRef, useCallback } from 'react'
import type { TravelRequest, TravelPlan, SseEvent } from '../types/travel.ts'
import { streamTravelPlan } from '../api/travelApi.ts'

export type PlanStatus = 'idle' | 'loading' | 'done' | 'error'

export interface LogEntry {
  id: number
  type: SseEvent['type']
  content: string
  data?: unknown
}

export function useTravelPlan() {
  const [status, setStatus] = useState<PlanStatus>('idle')
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [plan, setPlan] = useState<TravelPlan | null>(null)
  const [errorMsg, setErrorMsg] = useState('')
  const cancelRef = useRef<(() => void) | null>(null)
  const idRef = useRef(0)
  const tokenBufferRef = useRef('')  // accumulator only, drives no UI — useRef not useState

  const addLog = useCallback((entry: Omit<LogEntry, 'id'>) => {
    setLogs(prev => [...prev, { ...entry, id: idRef.current++ }])
  }, [])

  const flushTokenBuffer = useCallback(() => {
    const buffered = tokenBufferRef.current.trim()
    if (buffered) addLog({ type: 'token', content: buffered })
    tokenBufferRef.current = ''
  }, [addLog])

  const start = useCallback((request: TravelRequest) => {
    if (cancelRef.current) cancelRef.current()
    setStatus('loading')
    setLogs([])
    setPlan(null)
    setErrorMsg('')
    tokenBufferRef.current = ''

    cancelRef.current = streamTravelPlan(
      request,
      (event) => {
        if (event.type === 'token') {
          tokenBufferRef.current += event.content
          if (tokenBufferRef.current.includes('\n') || tokenBufferRef.current.length > 120) {
            addLog({ type: 'token', content: tokenBufferRef.current.trim() })
            tokenBufferRef.current = ''
          }
        } else {
          flushTokenBuffer()
          if (event.type === 'complete') {
            setPlan(event.data as TravelPlan)
          }
          addLog({ type: event.type, content: event.content, data: event.data })
        }
      },
      (err) => {
        setErrorMsg(err)
        setStatus('error')
      },
      () => setStatus('done')
    )
  }, [addLog, flushTokenBuffer])

  const cancel = useCallback(() => {
    cancelRef.current?.()
    setStatus('idle')
  }, [])

  return { status, logs, plan, errorMsg, start, cancel }
}
