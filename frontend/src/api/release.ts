import request from './request'
import type { ReleasePermit, ReleaseRequest, DashboardStats } from '@/types'

export const releaseApi = {
  listAll: () => request.get<ReleasePermit[]>('/release/permits'),
  stats: () => request.get<DashboardStats>('/release/stats'),
  evaluate: (planId: number) => request.get<ReleasePermit>(`/release/plan/${planId}`),
  submit: (planId: number, data?: ReleaseRequest) =>
    request.post<ReleasePermit>(`/release/plan/${planId}/submit`, data || {}),
  depart: (planId: number) => request.post<ReleasePermit>(`/release/plan/${planId}/depart`),
  manualVoid: (planId: number, reason?: string) =>
    request.post(`/release/plan/${planId}/void`, { reason })
}
