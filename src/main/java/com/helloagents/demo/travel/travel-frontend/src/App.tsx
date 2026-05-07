import { useState, useEffect } from 'react'
import TravelForm from './components/TravelForm'
import StreamLog from './components/StreamLog'
import ItineraryPanel from './components/ItineraryPanel'
import MapView from './components/MapView'
import BudgetPanel from './components/BudgetPanel'
import { useTravelPlan } from './hooks/useTravelPlan'

export default function App() {
  const { status, logs, plan, errorMsg, start, cancel } = useTravelPlan()
  const [activeDay, setActiveDay] = useState(0)
  useEffect(() => { setActiveDay(0) }, [plan])  // reset when a new plan arrives

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Header */}
      <header className="bg-white border-b border-slate-200 px-6 py-4 flex items-center gap-3">
        <span className="text-2xl">🗺️</span>
        <div>
          <h1 className="text-lg font-bold text-slate-800">智能旅行助手</h1>
          <p className="text-xs text-slate-400">AI-Powered Travel Planner · hello-agents-java 第 13 章</p>
        </div>
      </header>

      <div className="max-w-7xl mx-auto p-4 grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Left column: Form + Log */}
        <div className="space-y-4">
          <TravelForm
            onSubmit={start}
            loading={status === 'loading'}
            onCancel={cancel}
          />
          <StreamLog logs={logs} status={status} />

          {status === 'error' && (
            <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 text-sm text-red-600">
              {errorMsg || '请求失败，请检查后端服务是否启动'}
            </div>
          )}
        </div>

        {/* Right column: Map + Itinerary + Budget */}
        <div className="lg:col-span-2 space-y-4">
          <MapView plan={plan} activeDay={activeDay} />

          {plan ? (
            <>
              <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
                <div className="xl:col-span-2">
                  <ItineraryPanel plan={plan} activeDay={activeDay} onDayChange={setActiveDay} />
                </div>
                <div>
                  <BudgetPanel budget={plan.budget} />
                </div>
              </div>

              {plan.weatherSummary && (
                <div className="bg-sky-50 border border-sky-100 rounded-2xl px-5 py-3 text-sm text-sky-700 flex gap-2">
                  <span>☁️</span>
                  <span>{plan.weatherSummary}</span>
                </div>
              )}
            </>
          ) : (
            <div className="bg-white rounded-2xl shadow-sm p-12 text-center">
              <p className="text-4xl mb-4">✈️</p>
              <p className="text-slate-500 text-sm">填写左侧表单，AI 将自动规划完整行程</p>
              <p className="text-slate-400 text-xs mt-2">包含景点推荐、地图路线、酒店建议和预算明细</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}