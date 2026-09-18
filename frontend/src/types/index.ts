export interface RoutePlan {
  id: number
  planName: string
  startLocation: string
  endLocation: string
  waypoints: string
  travelDate: string
  ageMin: number
  ageMax: number
  participantCount: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface RoutePlanRequest {
  planName: string
  startLocation: string
  endLocation: string
  waypoints: string[]
  travelDate: string
  ageMin?: number
  ageMax?: number
  participantCount?: number
}

export interface RiskRule {
  id: number
  ruleCode: string
  ruleName: string
  ruleType: string
  riskLevel: string
  conditionExpression: string
  warningMessage: string
  enabled: number
  createdAt: string
  updatedAt: string
}

export interface RiskRuleRequest {
  ruleCode: string
  ruleName: string
  ruleType: string
  riskLevel: string
  conditionExpression: string
  warningMessage: string
  enabled?: number
}

export interface RiskCheckResponse {
  planId: number
  planName: string
  highRisks: RiskItem[]
  mediumRisks: RiskItem[]
  lowRisks: RiskItem[]
  overallStatus: string
  totalRiskCount: number
}

export interface RiskItem {
  ruleId: number
  ruleCode: string
  ruleName: string
  ruleType: string
  riskLevel: string
  riskMessage: string
  location: string
}

export interface ReportResponse {
  reportId: string
  planId: number
  planName: string
  startLocation: string
  endLocation: string
  waypoints: string[]
  travelDate: string
  ageMin: number
  ageMax: number
  participantCount: number
  generatedAt: string
  overallStatus: string
  screened: boolean
  screeningValid: boolean
  screeningMismatchReason?: string
  permitStatus?: PermitStatus
  permitStatusText?: string
  permitBlockReason?: string
  departed?: boolean
  departedAt?: string
  riskSummary: RiskSummary
  riskDetails: RiskDetail[]
}

export interface RiskSummary {
  highCount: number
  mediumCount: number
  lowCount: number
  totalCount: number
}

export interface RiskDetail {
  location: string
  riskLevel: string
  ruleName: string
  riskMessage: string
  riskType: string
}

// ===================== 随队医护 / 排班 =====================

export interface MedicalStaff {
  id: number
  staffName: string
  certificateNo?: string
  phone?: string
  pediatricQualified: number
  title?: string
  createdAt?: string
  updatedAt?: string
}

export interface MedicalStaffRequest {
  staffName: string
  certificateNo?: string
  phone?: string
  pediatricQualified?: boolean
  title?: string
}

export interface AssignmentRequest {
  planId: number
  staffIds: number[]
}

export interface StaffView {
  assignmentId?: number
  staffId: number
  staffName: string
  title?: string
  phone?: string
  certificateNo?: string
  pediatricQualified: boolean
  dateMatched: boolean
  assignmentDate?: string
}

export interface CoverageInfo {
  staff: StaffView[]
  coveredCount: number
  requiredCount: number
  countSatisfied: boolean
  pediatricRequired: boolean
  pediatricSatisfied: boolean
  satisfied: boolean
  basedRiskLevel?: string
  reasons: string[]
}

export interface ScreeningBook {
  exists: boolean
  riskLevel?: string
  screenedAt?: string
  fresh: boolean
  tripFresh?: boolean
  rulesFresh?: boolean
  ruleVersion?: number
  currentRuleVersion?: number
  snapshotTravelDate?: string
  snapshotParticipantCount?: number
  snapshotAgeMin?: number
  snapshotAgeMax?: number
  snapshotWaypoints?: string
  currentTravelDate?: string
  currentParticipantCount?: number
  currentAgeMin?: number
  currentAgeMax?: number
  currentWaypoints?: string
  mismatchReason?: string
}

export interface AssignmentResponse {
  planId: number
  planName: string
  travelDate: string
  coverage: CoverageInfo
  permitStatusAfterChange?: string
  permitReason?: string
}

// ===================== 发车放行 =====================

export type PermitStatus = 'PENDING' | 'RELEASED' | 'VOID' | 'STALE_RECHECK'

export interface ReleasePermit {
  permitId?: number
  planId: number
  planName: string
  status: PermitStatus
  statusText: string
  riskLevel?: string
  informedNote?: string
  releasedAt?: string
  departedAt?: string
  voidReason?: string
  recheckReason?: string
  screening: ScreeningBook
  coverage: CoverageInfo
  releasable: boolean
  blockReasons: string[]
}

export interface ReleaseRequest {
  informedNote?: string
}

export interface DashboardStats {
  totalPlanCount: number
  highRiskCount: number
  mediumRiskCount: number
  lowRiskCount: number
  pendingScreeningCount: number
  releasedCount: number
  pendingDocsCount: number
  voidCount: number
  staleRecheckCount: number
  medicalStaffCount: number
  pediatricStaffCount: number
  assignmentCount: number
}

// ===================== 回程点名 =====================

/** NOT_DEPARTED 未发车 / ROLLING 回程点名中 / CLOSED 已收口（冻住） */
export type RollPhase = 'NOT_DEPARTED' | 'ROLLING' | 'CLOSED'

export interface Participant {
  id: number
  personName: string
  returned: boolean
  missingNote?: string
}

export interface RollCall {
  planId: number
  planName: string
  phase: RollPhase
  phaseText: string
  departedAt?: string
  closedAt?: string
  participants: Participant[]
  totalCount: number
  returnedCount: number
  missingCount: number
  missingWithoutNoteCount: number
  closable: boolean
  frozenMissingNote?: string
  blockReasons: string[]
}