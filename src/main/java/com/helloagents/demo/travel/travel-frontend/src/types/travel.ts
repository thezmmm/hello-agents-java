export interface TravelRequest {
  destination: string
  startDate: string
  endDate: string
  hotelPreference: string
  preferences: string
}

export interface Attraction {
  name: string
  description: string
  latitude: number
  longitude: number
  openTime: string
  ticketPrice: string
  imageUrl: string
}

export interface Hotel {
  name: string
  address: string
  latitude: number
  longitude: number
  pricePerNight: string
  rating: number
  type: string
}

export interface DayItinerary {
  date: string
  dayLabel: string
  attractions: Attraction[]
  meals: string[]
  hotel: Hotel
  weather: string
}

export interface BudgetDetail {
  tickets: number
  hotel: number
  meals: number
  transport: number
  total: number
}

export interface TravelPlan {
  destination: string
  startDate: string
  endDate: string
  days: DayItinerary[]
  budget: BudgetDetail
  weatherSummary: string
  tips: string[]
}

export interface SseEvent {
  type: 'thinking' | 'tool_result' | 'token' | 'complete' | 'error'
  content: string
  data: TravelPlan | string | null
}