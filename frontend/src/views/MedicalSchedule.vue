<template>
  <div class="medical-container">
    <!-- 计划选择 -->
    <el-card class="plan-select-card">
      <div class="plan-select-row">
        <span class="field-label">选择路线计划：</span>
        <el-select
          v-model="selectedPlanId"
          placeholder="请选择要排班的路线计划"
          filterable
          style="width: 420px"
          @change="loadAssignment"
        >
          <el-option
            v-for="p in plans"
            :key="p.id"
            :label="`${p.planName}（${p.travelDate}）`"
            :value="p.id"
          >
            <span>{{ p.planName }}</span>
            <span class="opt-date">{{ p.travelDate }} · {{ riskText(p.status) }}</span>
          </el-option>
        </el-select>
        <el-tag v-if="selectedPlan" :type="riskTagType(selectedPlan.status)" effect="dark">
          {{ riskText(selectedPlan.status) }}
        </el-tag>
        <span class="field-hint">排班按该计划的出行日挂人；计划改期后需按新日期重新排班</span>
      </div>
    </el-card>

    <el-row :gutter="20" style="margin-top: 16px">
      <!-- 排班 + 覆盖 -->
      <el-col :span="15">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>当天医护排班与覆盖</span>
              <el-button type="primary" size="small" :disabled="!selectedPlanId" :loading="saving" @click="saveAssignment">
                保存排班
              </el-button>
            </div>
          </template>

          <el-empty v-if="!selectedPlanId" description="请先选择路线计划" />

          <div v-else>
            <el-descriptions :column="3" border size="small" class="plan-desc">
              <el-descriptions-item label="出行日期">{{ selectedPlan?.travelDate }}</el-descriptions-item>
              <el-descriptions-item label="年龄范围">{{ selectedPlan?.ageMin }} - {{ selectedPlan?.ageMax }} 岁</el-descriptions-item>
              <el-descriptions-item label="参与人数">{{ selectedPlan?.participantCount }} 人</el-descriptions-item>
            </el-descriptions>

            <div class="requirement-line">
              <el-tag :type="coverage && coverage.requiredCount >= 2 ? 'danger' : 'info'">
                需在岗 {{ coverage?.requiredCount ?? '-' }} 人{{ coverage && coverage.requiredCount >= 2 ? '（高风险双人值班）' : '' }}
              </el-tag>
              <el-tag :type="coverage?.pediatricRequired ? 'warning' : 'success'">
                儿科资质：{{ coverage?.pediatricRequired ? '必须至少 1 名（最小年龄 ≤ 8 岁）' : '不要求' }}
              </el-tag>
              <el-tag type="info">当前在岗 {{ coverage?.coveredCount ?? 0 }} 人</el-tag>
            </div>

            <div class="staff-pick-label">挂到该出行日的医护：</div>
            <el-select
              v-model="chosenStaffIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择当天随队医护"
              style="width: 100%"
            >
              <el-option
                v-for="s in staff"
                :key="s.id"
                :label="staffLabel(s)"
                :value="s.id"
                :disabled="isStaffClashed(s)"
              >
                <span>{{ s.staffName }}</span>
                <span class="opt-sub">
                  {{ s.title }}
                  <el-tag v-if="s.pediatricQualified === 1" type="success" size="small">儿科</el-tag>
                  <el-tag v-if="isStaffClashed(s)" type="danger" size="small">当天已挂其它计划</el-tag>
                </span>
              </el-option>
            </el-select>

            <!-- 覆盖结论 -->
            <el-alert
              v-if="coverage"
              class="coverage-alert"
              :type="coverage.satisfied ? 'success' : 'error'"
              :closable="false"
              show-icon
            >
              <template #title>
                {{ coverage.satisfied ? '医护覆盖已满足' : '医护覆盖未满足' }}
              </template>
              <div v-if="!coverage.satisfied" class="reason-list">
                <div v-for="(r, i) in coverage.reasons" :key="i">· {{ r }}</div>
              </div>
            </el-alert>

            <!-- 已排班明细 -->
            <el-table v-if="coverage && coverage.staff.length" :data="coverage.staff" size="small" style="margin-top: 12px">
              <el-table-column prop="staffName" label="医护" width="110" />
              <el-table-column prop="title" label="职称" />
              <el-table-column label="儿科资质" width="90">
                <template #default="scope">
                  <el-tag v-if="scope.row.pediatricQualified" type="success" size="small">是</el-tag>
                  <span v-else>否</span>
                </template>
              </el-table-column>
              <el-table-column label="排班日期" width="130">
                <template #default="scope">
                  <el-tag :type="scope.row.dateMatched ? 'success' : 'danger'" size="small">
                    {{ scope.row.assignmentDate }}{{ scope.row.dateMatched ? '' : '（日期不符）' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>

            <!-- 排班改动对放行单的连带影响 -->
            <el-alert
              v-if="permitImpact"
              class="coverage-alert"
              :type="permitImpact.type"
              :closable="false"
              show-icon
              :title="permitImpact.title"
              :description="permitImpact.desc"
            />
          </div>
        </el-card>
      </el-col>

      <!-- 医护名册 -->
      <el-col :span="9">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>随队医护名册</span>
              <el-button type="primary" size="small" @click="openStaffDialog()">新增医护</el-button>
            </div>
          </template>
          <el-table :data="staff" size="small">
            <el-table-column prop="staffName" label="姓名" width="90" />
            <el-table-column prop="title" label="职称" show-overflow-tooltip />
            <el-table-column label="儿科" width="70">
              <template #default="scope">
                <el-tag v-if="scope.row.pediatricQualified === 1" type="success" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="scope">
                <el-button type="primary" link size="small" @click="openStaffDialog(scope.row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="removeStaff(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 医护新增/编辑 -->
    <el-dialog v-model="staffDialogVisible" :title="staffForm.id ? '编辑医护' : '新增医护'" width="460px">
      <el-form :model="staffForm" label-width="90px">
        <el-form-item label="姓名" required>
          <el-input v-model="staffForm.staffName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="职称/岗位">
          <el-input v-model="staffForm.title" placeholder="如：儿科主治医师" />
        </el-form-item>
        <el-form-item label="资质编号">
          <el-input v-model="staffForm.certificateNo" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="staffForm.phone" />
        </el-form-item>
        <el-form-item label="儿科资质">
          <el-switch v-model="staffForm.pediatricQualified" />
          <span class="field-hint">最小年龄 ≤ 8 岁的队伍须至少一名儿科资质</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="staffDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStaff">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { medicalApi } from '@/api/medical'
import { planApi } from '@/api/risk'
import type { RoutePlan, MedicalStaff, AssignmentResponse } from '@/types'

const route = useRoute()
const plans = ref<RoutePlan[]>([])
const staff = ref<MedicalStaff[]>([])
const selectedPlanId = ref<number>()
const assignment = ref<AssignmentResponse | null>(null)
const chosenStaffIds = ref<number[]>([])
const saving = ref(false)
/** 其它计划的排班：{ staffId, date, planId } */
const busyEntries = ref<Array<{ staffId: number; date: string; planId: number }>>([])

const selectedPlan = computed(() => plans.value.find(p => p.id === selectedPlanId.value))
const coverage = computed(() => assignment.value?.coverage)

const staffDialogVisible = ref(false)
const staffForm = reactive<{ id?: number; staffName: string; title: string; certificateNo: string; phone: string; pediatricQualified: boolean }>({
  staffName: '', title: '', certificateNo: '', phone: '', pediatricQualified: false
})

const riskText = (s: string) =>
  ({ HIGH_RISK: '高风险', MEDIUM_RISK: '中风险', LOW_RISK: '低风险', PENDING: '待筛查' } as Record<string, string>)[s] || s
const riskTagType = (s: string) =>
  ({ HIGH_RISK: 'danger', MEDIUM_RISK: 'warning', LOW_RISK: 'success', PENDING: 'info' } as Record<string, string>)[s] || 'info'

const staffLabel = (s: MedicalStaff) =>
  `${s.staffName}${s.pediatricQualified === 1 ? '（儿科）' : ''}`

// 同一名医护在同一天已挂在“别的计划”则禁选（最终仍以后端唯一约束为准）
const isStaffClashed = (s: MedicalStaff) => {
  const plan = selectedPlan.value
  if (!plan) return false
  return busyEntries.value.some(
    e => e.staffId === s.id && e.date === plan.travelDate && e.planId !== plan.id
  )
}

const permitImpact = computed(() => {
  const st = assignment.value?.permitStatusAfterChange
  const reason = assignment.value?.permitReason
  if (st === 'STALE_RECHECK') {
    return { type: 'warning', title: '原放行单已掉到「筛查失效待重评」', desc: reason || '' }
  }
  if (st === 'VOID') {
    return { type: 'error', title: '放行单已作废', desc: reason || '' }
  }
  if (st === 'RELEASED') {
    return { type: 'success', title: '放行单仍为「已放行」，覆盖有效', desc: '' }
  }
  return null
})

const loadPlans = async () => {
  plans.value = await planApi.getAll()
}
const loadStaff = async () => {
  staff.value = await medicalApi.listStaff()
}

// 汇总所有计划的排班，用于提示“该医护当天已挂在别的计划”
const loadBusy = async () => {
  const entries: Array<{ staffId: number; date: string; planId: number }> = []
  await Promise.all(
    plans.value.map(async p => {
      try {
        const a = await medicalApi.getAssignment(p.id)
        ;(a.coverage?.staff || []).forEach(s => {
          if (s.assignmentDate) {
            entries.push({ staffId: s.staffId, date: s.assignmentDate, planId: p.id })
          }
        })
      } catch {
        /* 单个计划加载失败不阻塞页面 */
      }
    })
  )
  busyEntries.value = entries
}

const loadAssignment = async () => {
  if (!selectedPlanId.value) {
    assignment.value = null
    chosenStaffIds.value = []
    return
  }
  try {
    const a = await medicalApi.getAssignment(selectedPlanId.value)
    assignment.value = a
    chosenStaffIds.value = (a.coverage?.staff || []).map(s => s.staffId)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载排班失败')
  }
}

const saveAssignment = async () => {
  if (!selectedPlanId.value) return
  if (chosenStaffIds.value.length === 0) {
    ElMessage.warning('至少安排一名医护')
    return
  }
  saving.value = true
  try {
    const a = await medicalApi.assign({ planId: selectedPlanId.value, staffIds: chosenStaffIds.value })
    assignment.value = a
    chosenStaffIds.value = (a.coverage?.staff || []).map(s => s.staffId)
    await loadBusy()
    ElMessage.success('排班保存成功')
    if (a.coverage?.satisfied) {
      ElMessage.success('当天医护覆盖已满足')
    } else {
      ElMessage.warning('医护覆盖尚未满足：' + (a.coverage?.reasons || []).join('；'))
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '排班保存失败')
  } finally {
    saving.value = false
  }
}

const openStaffDialog = (s?: MedicalStaff) => {
  if (s) {
    staffForm.id = s.id
    staffForm.staffName = s.staffName
    staffForm.title = s.title || ''
    staffForm.certificateNo = s.certificateNo || ''
    staffForm.phone = s.phone || ''
    staffForm.pediatricQualified = s.pediatricQualified === 1
  } else {
    staffForm.id = undefined
    staffForm.staffName = ''
    staffForm.title = ''
    staffForm.certificateNo = ''
    staffForm.phone = ''
    staffForm.pediatricQualified = false
  }
  staffDialogVisible.value = true
}

const saveStaff = async () => {
  if (!staffForm.staffName.trim()) {
    ElMessage.warning('请填写医护姓名')
    return
  }
  try {
    const payload = {
      staffName: staffForm.staffName.trim(),
      title: staffForm.title || undefined,
      certificateNo: staffForm.certificateNo || undefined,
      phone: staffForm.phone || undefined,
      pediatricQualified: staffForm.pediatricQualified
    }
    if (staffForm.id) {
      await medicalApi.updateStaff(staffForm.id, payload)
    } else {
      await medicalApi.createStaff(payload)
    }
    ElMessage.success('保存成功')
    staffDialogVisible.value = false
    await loadStaff()
    await loadAssignment()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

const removeStaff = async (s: MedicalStaff) => {
  try {
    await ElMessageBox.confirm(`确定删除医护「${s.staffName}」吗？仍有排班的医护需先改挂。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await medicalApi.deleteStaff(s.id)
    ElMessage.success('删除成功')
    await loadStaff()
    await loadAssignment()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadPlans(), loadStaff()])
  await loadBusy()
  const q = Number(route.query.planId)
  if (q && plans.value.some(p => p.id === q)) {
    selectedPlanId.value = q
    await loadAssignment()
  }
})
</script>

<style scoped>
.plan-select-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.field-label {
  font-weight: 600;
}
.field-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.opt-date {
  float: right;
  color: #909399;
  font-size: 12px;
  margin-right: 10px;
}
.opt-sub {
  float: right;
  color: #909399;
  font-size: 12px;
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.plan-desc {
  margin-bottom: 12px;
}
.requirement-line {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 10px 0 14px;
}
.staff-pick-label {
  font-weight: 600;
  margin-bottom: 6px;
}
.coverage-alert {
  margin-top: 14px;
}
.reason-list {
  margin-top: 4px;
  line-height: 1.7;
}
</style>
