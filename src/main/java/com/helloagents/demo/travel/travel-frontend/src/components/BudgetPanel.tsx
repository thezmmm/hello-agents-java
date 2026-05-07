import type { BudgetDetail } from '../types/travel'

interface Props {
  budget: BudgetDetail
}

const ITEMS = [
  { key: 'tickets',   label: '景点门票', icon: '🎫', color: 'bg-purple-400' },
  { key: 'hotel',     label: '住宿费用', icon: '🏨', color: 'bg-blue-400' },
  { key: 'meals',     label: '餐饮费用', icon: '🍜', color: 'bg-orange-400' },
  { key: 'transport', label: '交通费用', icon: '🚌', color: 'bg-green-400' }
] as const

export default function BudgetPanel({ budget }: Props) {
  const total = budget.total || 1

  return (
    <div className="bg-white rounded-2xl shadow-sm p-5">
      <h3 className="text-sm font-semibold text-slate-700 mb-4">预算明细</h3>

      <div className="text-center mb-5">
        <p className="text-3xl font-bold text-slate-800">¥{budget.total.toLocaleString()}</p>
        <p className="text-xs text-slate-400 mt-1">预计总费用</p>
      </div>

      <div className="space-y-3">
        {ITEMS.map(({ key, label, icon, color }) => {
          const amount = budget[key]
          const pct = Math.round((amount / total) * 100)
          return (
            <div key={key}>
              <div className="flex justify-between text-xs text-slate-600 mb-1">
                <span>{icon} {label}</span>
                <span className="font-medium">¥{amount.toLocaleString()} <span className="text-slate-400">({pct}%)</span></span>
              </div>
              <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
                <div
                  className={`h-full ${color} rounded-full transition-all duration-700`}
                  style={{ width: `${pct}%` }}
                />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}