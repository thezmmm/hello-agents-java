import { useEffect, useRef } from 'react'
import type { TravelPlan } from '../types/travel'
import 'leaflet/dist/leaflet.css'

interface Props {
  plan: TravelPlan | null
  activeDay: number
}

const DAY_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6']
const HOTEL_COLOR = '#f97316'

let leafletIconsFixed = false
function fixLeafletIcons(L: typeof import('leaflet')) {
  if (leafletIconsFixed) return
  const iconUrl = 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png'
  const shadowUrl = 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png'
  L.Icon.Default.mergeOptions({ iconUrl, shadowUrl, iconRetinaUrl: iconUrl })
  leafletIconsFixed = true
}

export default function MapView({ plan, activeDay }: Props) {
  const mapRef = useRef<HTMLDivElement>(null)
  const leafletMap = useRef<import('leaflet').Map | null>(null)
  const markersLayer = useRef<import('leaflet').LayerGroup | null>(null)

  // Effect 1: 创建/销毁地图（仅随 plan 变化）
  useEffect(() => {
    if (!plan || !mapRef.current) return

    import('leaflet').then((L) => {
      fixLeafletIcons(L)

      if (leafletMap.current) {
        leafletMap.current.remove()
        leafletMap.current = null
        markersLayer.current = null
      }

      const firstDay = plan.days[0]
      const firstAttr = firstDay?.attractions[0]
      const center: [number, number] = firstAttr
        ? [firstAttr.latitude, firstAttr.longitude]
        : [39.9087, 116.3975]

      const map = L.map(mapRef.current!).setView(center, 12)
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
      }).addTo(map)

      markersLayer.current = L.layerGroup().addTo(map)
      leafletMap.current = map
    })

    return () => {
      leafletMap.current?.remove()
      leafletMap.current = null
      markersLayer.current = null
    }
  }, [plan])

  // Effect 2: 更新标记（随 plan 或 activeDay 变化）
  useEffect(() => {
    if (!plan) return

    import('leaflet').then((L) => {
      if (!leafletMap.current || !markersLayer.current) return

      markersLayer.current.clearLayers()

      const day = plan.days[activeDay]
      if (!day) return

      const color = DAY_COLORS[activeDay % DAY_COLORS.length]
      const coords: [number, number][] = []

      // 景点标记
      day.attractions.forEach((attr, i) => {
        if (!attr.latitude || !attr.longitude) return
        const pos: [number, number] = [attr.latitude, attr.longitude]
        coords.push(pos)

        const marker = L.circleMarker(pos, {
          radius: 9,
          fillColor: color,
          color: '#fff',
          weight: 2,
          opacity: 1,
          fillOpacity: 0.9,
        }).addTo(markersLayer.current!)

        marker.bindTooltip(`<span style="font-size:11px;font-weight:600">${i + 1}. ${attr.name}</span>`, {
          permanent: true,
          direction: 'top',
          offset: [0, -12],
          className: 'map-label',
        })

        marker.bindPopup(`
          <div style="min-width:160px">
            <b>${day.dayLabel} · ${attr.name}</b><br/>
            <small>${attr.description}</small><br/>
            <small>⏰ ${attr.openTime}</small><br/>
            <small>🎫 ${attr.ticketPrice}</small>
          </div>
        `)
      })

      // 游览路线连线
      if (day.attractions.length > 1) {
        const lineCoords = day.attractions
          .filter(a => a.latitude && a.longitude)
          .map(a => [a.latitude, a.longitude] as [number, number])
        L.polyline(lineCoords, {
          color,
          weight: 2,
          dashArray: '5, 5',
          opacity: 0.7,
        }).addTo(markersLayer.current!)
      }

      // 酒店标记
      const hotel = day.hotel
      if (hotel?.latitude && hotel?.longitude) {
        const hotelPos: [number, number] = [hotel.latitude, hotel.longitude]
        coords.push(hotelPos)

        const hotelMarker = L.circleMarker(hotelPos, {
          radius: 10,
          fillColor: HOTEL_COLOR,
          color: '#fff',
          weight: 2,
          opacity: 1,
          fillOpacity: 0.9,
        }).addTo(markersLayer.current!)

        hotelMarker.bindTooltip(`<span style="font-size:11px;font-weight:600">🏨 ${hotel.name}</span>`, {
          permanent: true,
          direction: 'top',
          offset: [0, -13],
          className: 'map-label',
        })

        hotelMarker.bindPopup(`
          <div style="min-width:160px">
            <b>🏨 ${hotel.name}</b><br/>
            <small>${hotel.address}</small><br/>
            <small>⭐ ${hotel.rating} · 💰 ${hotel.pricePerNight}/晚</small><br/>
            <small>🏷 ${hotel.type}</small>
          </div>
        `)
      }

      // 自适应视野
      if (coords.length > 0) {
        leafletMap.current!.fitBounds(L.latLngBounds(coords), { padding: [40, 40] })
      }
    })
  }, [plan, activeDay])

  return (
    <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
        <h3 className="text-sm font-semibold text-slate-700">行程地图</h3>
        {plan && (
          <div className="flex items-center gap-4 text-xs text-slate-500">
            <div className="flex items-center gap-1.5">
              <span
                className="w-2.5 h-2.5 rounded-full inline-block"
                style={{ backgroundColor: DAY_COLORS[activeDay % DAY_COLORS.length] }}
              />
              {plan.days[activeDay]?.dayLabel} 景点
            </div>
            <div className="flex items-center gap-1.5">
              <span
                className="w-2.5 h-2.5 rounded-full inline-block"
                style={{ backgroundColor: HOTEL_COLOR }}
              />
              住宿
            </div>
          </div>
        )}
      </div>
      <div ref={mapRef} className="h-64 w-full">
        {!plan && (
          <div className="h-full flex items-center justify-center bg-slate-50">
            <p className="text-sm text-slate-400">规划完成后将在此显示行程地图</p>
          </div>
        )}
      </div>
    </div>
  )
}
