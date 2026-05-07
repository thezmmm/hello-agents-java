import { useEffect, useRef } from 'react'
import type { LogEntry } from '../hooks/useTravelPlan'

interface Props {
  logs: LogEntry[]
  status: string
}

const EVENT_CONFIG = {
  thinking:    { icon: '🤔', color: 'text-blue-600',  bg: 'bg-blue-50',  label: '思考' },
  tool_result: { icon: '🔧', color: 'text-orange-600', bg: 'bg-orange-50', label: '工具' },
  token:       { icon: '✏️', color: 'text-slate-600',  bg: 'bg-slate-50',  label: '输出' },
  complete:    { icon: '✅', color: 'text-green-600',  bg: 'bg-green-50',  label: '完成' },
  error:       { icon: '❌', color: 'text-red-600',    bg: 'bg-red-50',    label: '错误' }
}

export default function StreamLog({ logs, status }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs.length])

  return (
    <div className="bg-white rounded-2xl shadow-sm p-4 flex flex-col h-64">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-slate-700">Agent 执行日志</h3>
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
          status === 'loading' ? 'bg-blue-100 text-blue-600' :
          status === 'done'    ? 'bg-green-100 text-green-600' :
          status === 'error'   ? 'bg-red-100 text-red-600' :
          'bg-slate-100 text-slate-500'
        }`}>
          {status === 'loading' ? '规划中...' :
           status === 'done'    ? '已完成' :
           status === 'error'   ? '出错' : '等待中'}
        </span>
      </div>

      <div className="flex-1 overflow-y-auto stream-log space-y-1.5 pr-1">
        {logs.length === 0 && (
          <p className="text-xs text-slate-400 text-center mt-8">
            填写表单并点击"开始智能规划"
          </p>
        )}
        {logs.map(log => {
          const cfg = EVENT_CONFIG[log.type] ?? EVENT_CONFIG.token
          if (log.type === 'complete') {
            return (
              <div key={log.id} className={`flex items-center gap-2 px-3 py-2 rounded-lg ${cfg.bg}`}>
                <span>{cfg.icon}</span>
                <span className={`text-xs font-semibold ${cfg.color}`}>{log.content}</span>
              </div>
            )
          }
          return (
            <div key={log.id} className={`flex items-start gap-2 px-3 py-1.5 rounded-lg ${cfg.bg}`}>
              <span className="text-xs mt-0.5 shrink-0">{cfg.icon}</span>
              <span className={`text-xs leading-relaxed ${cfg.color} break-all`}>
                {log.content}
              </span>
            </div>
          )
        })}
        {status === 'loading' && (
          <div className="flex items-center gap-2 px-3 py-1.5">
            <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
            <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
            <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
          </div>
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}