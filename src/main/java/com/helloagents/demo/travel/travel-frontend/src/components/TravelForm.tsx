import { useState } from 'react'
import type { TravelRequest } from '../types/travel.ts'

interface Props {
  onSubmit: (req: TravelRequest) => void
  loading: boolean
  onCancel: () => void
}

const HOTEL_OPTIONS = [
  { value: '经济型', label: '经济型（快捷酒店、民宿）' },
  { value: '舒适型', label: '舒适型（商务酒店、四星）' },
  { value: '豪华型', label: '豪华型（五星级、精品酒店）' },
]

const PREFERENCE_TAGS = ['历史文化', '自然风光', '美食探索', '网红打卡', '亲子游', '购物']

const today = new Date().toISOString().split('T')[0]

function nextDay(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  d.setDate(d.getDate() + 1)
  return d.toISOString().split('T')[0]
}

export default function TravelForm({ onSubmit, loading, onCancel }: Props) {
  const [form, setForm] = useState<TravelRequest>({
    destination: '',
    startDate: '',
    endDate: '',
    hotelPreference: '舒适型',
    preferences: ''
  })

  // derive active tags from form.preferences — no separate tags state needed
  const activeTags = form.preferences ? form.preferences.split('、').filter(Boolean) : []

  const toggleTag = (tag: string) => {
    const next = activeTags.includes(tag)
      ? activeTags.filter(t => t !== tag)
      : [...activeTags, tag]
    setForm(f => ({ ...f, preferences: next.join('、') }))
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.destination.trim() || !form.startDate || !form.endDate) return
    onSubmit(form)
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-2xl shadow-sm p-6 space-y-4">
      <h2 className="text-xl font-bold text-slate-800 flex items-center gap-2">
        <span>✈️</span> 智能旅行助手
      </h2>

      <div>
        <label className="block text-sm font-medium text-slate-600 mb-1">目的地</label>
        <input
          type="text"
          placeholder="例如：北京、成都、西安..."
          value={form.destination}
          onChange={e => setForm(f => ({ ...f, destination: e.target.value }))}
          className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
          required
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-sm font-medium text-slate-600 mb-1">出发日期</label>
          <input
            type="date"
            value={form.startDate}
            min={today}
            onChange={e => {
              const start = e.target.value
              setForm(f => ({
                ...f,
                startDate: start,
                endDate: f.endDate && f.endDate <= start ? '' : f.endDate
              }))
            }}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-600 mb-1">结束日期</label>
          <input
            type="date"
            value={form.endDate}
            min={form.startDate ? nextDay(form.startDate) : today}
            onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
            required
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-600 mb-1">住宿偏好</label>
        <select
          value={form.hotelPreference}
          onChange={e => setForm(f => ({ ...f, hotelPreference: e.target.value }))}
          className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        >
          {HOTEL_OPTIONS.map(o => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-600 mb-2">出行偏好</label>
        <div className="flex flex-wrap gap-2">
          {PREFERENCE_TAGS.map(tag => (
            <button
              key={tag}
              type="button"
              onClick={() => toggleTag(tag)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
                activeTags.includes(tag)
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {tag}
            </button>
          ))}
        </div>
      </div>

      <div className="flex gap-2 pt-2">
        {loading ? (
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 bg-red-50 text-red-500 border border-red-200 rounded-lg py-2.5 text-sm font-medium hover:bg-red-100 transition-colors"
          >
            取消规划
          </button>
        ) : (
          <button
            type="submit"
            className="flex-1 bg-blue-500 text-white rounded-lg py-2.5 text-sm font-medium hover:bg-blue-600 transition-colors disabled:opacity-50"
            disabled={!form.destination || !form.startDate || !form.endDate}
          >
            开始智能规划
          </button>
        )}
      </div>
    </form>
  )
}
