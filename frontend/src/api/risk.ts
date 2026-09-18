import request from './request'
import type { RoutePlan, RoutePlanRequest, RiskRule, RiskRuleRequest, RiskCheckResponse, ReportResponse } from '../types'

export const planApi = {
  create: (data: RoutePlanRequest) => request.post<RoutePlan>('/plans', data),
  getAll: () => request.get<RoutePlan[]>('/plans'),
  getById: (id: number) => request.get<RoutePlan>(`/plans/${id}`),
  update: (id: number, data: RoutePlanRequest) => request.put<RoutePlan>(`/plans/${id}`, data),
  delete: (id: number) => request.delete(`/plans/${id}`),
  checkRisk: (id: number) => request.post<RiskCheckResponse>(`/plans/${id}/check`),
  generateReport: (id: number) => request.get<ReportResponse>(`/plans/${id}/report`)
}

export const ruleApi = {
  getAll: () => request.get<RiskRule[]>('/rules'),
  getEnabled: () => request.get<RiskRule[]>('/rules/enabled'),
  getGrouped: () => request.get<Record<string, RiskRule[]>>('/rules/grouped'),
  getByType: (type: string) => request.get<RiskRule[]>(`/rules/type/${type}`),
  getById: (id: number) => request.get<RiskRule>(`/rules/${id}`),
  create: (data: RiskRuleRequest) => request.post<RiskRule>('/rules', data),
  update: (id: number, data: RiskRuleRequest) => request.put<RiskRule>(`/rules/${id}`, data),
  delete: (id: number) => request.delete(`/rules/${id}`),
  toggle: (id: number) => request.post<RiskRule>(`/rules/${id}/toggle`)
}