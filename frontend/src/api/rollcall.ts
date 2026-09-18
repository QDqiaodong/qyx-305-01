import request from './request'
import type { RollCall } from '@/types'

/**
 * 回程点名：按发车时带出去的人一个个勾回；缺员先写缺人说明，再收口；收口即冻住。
 */
export const rollCallApi = {
  listAll: () => request.get<RollCall[]>('/rollcall/plans'),
  get: (planId: number) => request.get<RollCall>(`/rollcall/plan/${planId}`),
  addParticipant: (planId: number, personName: string) =>
    request.post<RollCall>(`/rollcall/plan/${planId}/participants`, { personName }),
  removeParticipant: (planId: number, participantId: number) =>
    request.delete<RollCall>(`/rollcall/plan/${planId}/participants/${participantId}`),
  mark: (planId: number, participantId: number, returned: boolean) =>
    request.post<RollCall>(`/rollcall/plan/${planId}/participants/${participantId}/mark`, { returned }),
  writeMissingNote: (planId: number, participantId: number, note: string) =>
    request.post<RollCall>(`/rollcall/plan/${planId}/participants/${participantId}/missing-note`, { note }),
  close: (planId: number) => request.post<RollCall>(`/rollcall/plan/${planId}/close`)
}
