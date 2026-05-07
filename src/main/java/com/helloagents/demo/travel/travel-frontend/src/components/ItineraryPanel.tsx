import type { TravelPlan } from '../types/travel'

interface Props {
  plan: TravelPlan
  activeDay: number
  onDayChange: (day: number) => void
}

export default function ItineraryPanel({ plan, activeDay, onDayChange }: Props) {
  const day = plan.days[activeDay]

  return (
    <div className="bg-white rounded-2xl shadow-sm p-5 space-y-4">
      <div>
        <h3 className="text-lg font-bold text-slate-800">{plan.destination} 行程</h3>
        <p className="text-xs text-slate-400 mt-0.5">{plan.startDate} — {plan.endDate}</p>
      </div>

      {/* Day tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {plan.days.map((d, i) => (
          <button
            key={i}
            onClick={() => onDayChange(i)}
            className={`shrink-0 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              activeDay === i
                ? 'bg-blue-500 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {d.dayLabel}
          </button>
        ))}
      </div>

      {day && (
        <div className="space-y-4">
          {/* Weather */}
          <div className="flex items-center gap-2 text-sm text-slate-600 bg-sky-50 px-3 py-2 rounded-lg">
            <span>🌤</span>
            <span>{day.weather}</span>
          </div>

          {/* Attractions */}
          <div>
            <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">景点安排</h4>
            <div className="space-y-2">
              {day.attractions.map((a, i) => (
                <div key={i} className="flex gap-3 p-3 border border-slate-100 rounded-xl hover:border-blue-200 transition-colors">
                  <div className="w-8 h-8 bg-blue-100 text-blue-600 rounded-lg flex items-center justify-center text-sm font-bold shrink-0">
                    {i + 1}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-800">{a.name}</p>
                    <p className="text-xs text-slate-400 truncate">{a.description}</p>
                    <div className="flex gap-3 mt-1 text-xs text-slate-500">
                      <span>⏰ {a.openTime}</span>
                      <span>🎫 {a.ticketPrice}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Meals */}
          <div>
            <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">餐饮推荐</h4>
            <div className="grid grid-cols-3 gap-2">
              {day.meals.map((meal, i) => (
                <div key={i} className="bg-orange-50 rounded-lg p-2 text-center">
                  <p className="text-xs text-orange-400 font-medium">{['早餐','午餐','晚餐'][i]}</p>
                  <p className="text-xs text-slate-600 mt-0.5 line-clamp-2">{meal}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Hotel */}
          {day.hotel && (
            <div>
              <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">住宿</h4>
              <div className="flex gap-3 p-3 border border-slate-100 rounded-xl">
                <span className="text-2xl">🏨</span>
                <div>
                  <p className="text-sm font-medium text-slate-800">{day.hotel.name}</p>
                  <p className="text-xs text-slate-400">{day.hotel.address}</p>
                  <div className="flex gap-3 mt-1 text-xs text-slate-500">
                    <span>⭐ {day.hotel.rating}</span>
                    <span>💰 {day.hotel.pricePerNight}/晚</span>
                    <span>🏷 {day.hotel.type}</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tips */}
      {plan.tips?.length > 0 && (
        <div className="border-t border-slate-100 pt-4">
          <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">旅行贴士</h4>
          <ul className="space-y-1">
            {plan.tips.map((tip, i) => (
              <li key={i} className="text-xs text-slate-600 flex gap-2">
                <span className="text-blue-400 shrink-0">•</span>
                <span>{tip}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}