import request from './request'
import type {
  MedicalStaff,
  MedicalStaffRequest,
  AssignmentRequest,
  AssignmentResponse,
  CoverageInfo
} from '@/types'

export const medicalApi = {
  listStaff: () => request.get<MedicalStaff[]>('/medical/staff'),
  createStaff: (data: MedicalStaffRequest) => request.post<MedicalStaff>('/medical/staff', data),
  updateStaff: (id: number, data: MedicalStaffRequest) =>
    request.put<MedicalStaff>(`/medical/staff/${id}`, data),
  deleteStaff: (id: number) => request.delete(`/medical/staff/${id}`),

  getAssignment: (planId: number) =>
    request.get<AssignmentResponse>(`/medical/assignments/plan/${planId}`),
  assign: (data: AssignmentRequest) => request.post<AssignmentResponse>('/medical/assignments', data),
  coverage: (planId: number) => request.get<CoverageInfo>(`/medical/coverage/${planId}`)
}
