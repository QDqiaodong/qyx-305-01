<template>
  <div class="plan-container">
    <el-card class="plan-card">
      <template #header>
        <div class="card-header">
          <span>路线行程管理</span>
          <el-button type="primary" @click="showAddModal = true">
            <el-icon><Plus /></el-icon>
            新增路线计划
          </el-button>
        </div>
      </template>

      <el-table :data="plans" style="width: 100%" @row-click="handleRowClick">
        <el-table-column prop="planName" label="计划名称" />
        <el-table-column prop="startLocation" label="出发地点" />
        <el-table-column prop="endLocation" label="目的地" />
        <el-table-column prop="waypoints" label="途经点位" :show-overflow-tooltip="true">
          <template #default="scope">
            <span>{{ scope.row.waypoints || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="travelDate" label="出行日期" />
        <el-table-column prop="ageMin" label="最小年龄" />
        <el-table-column prop="ageMax" label="最大年龄" />
        <el-table-column prop="participantCount" label="参与人数" />
        <el-table-column prop="status" label="风险状态">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">{{ getStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="scope">
            <el-button type="text" size="small" @click="editPlan(scope.row)">编辑</el-button>
            <el-button type="text" size="small" @click="checkRisk(scope.row)">风险筛查</el-button>
            <el-button type="text" size="small" @click="goSchedule(scope.row)">医护排班</el-button>
            <el-button type="text" size="small" @click="goRelease(scope.row)">发车放行</el-button>
            <el-button type="text" size="small" @click="viewReport(scope.row)">查看报告</el-button>
            <el-button type="text" size="small" danger @click="deletePlan(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAddModal" title="新增路线计划" width="600px">
      <el-form :model="formData" label-width="120px">
        <el-form-item label="计划名称" required>
          <el-input v-model="formData.planName" placeholder="请输入计划名称" />
        </el-form-item>
        <el-form-item label="出发地点" required>
          <el-input v-model="formData.startLocation" placeholder="请输入出发地点" />
        </el-form-item>
        <el-form-item label="目的地" required>
          <el-input v-model="formData.endLocation" placeholder="请输入目的地" />
        </el-form-item>
        <el-form-item label="途经点位">
          <el-input v-model="waypointsInput" placeholder="多个点位用逗号分隔" />
        </el-form-item>
        <el-form-item label="出行日期" required>
          <el-date-picker v-model="formData.travelDate" type="date" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="年龄范围">
          <el-input-number v-model="formData.ageMin" :min="0" :max="100" style="width: 100px" />
          <span style="margin: 0 10px">-</span>
          <el-input-number v-model="formData.ageMax" :min="0" :max="100" style="width: 100px" />
        </el-form-item>
        <el-form-item label="参与人数">
          <el-input-number v-model="formData.participantCount" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddModal = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRiskModal" title="风险筛查结果" width="800px">
      <div v-if="riskResult">
        <el-alert 
          :type="getRiskAlertType(riskResult.overallStatus)" 
          :title="getOverallStatusText(riskResult.overallStatus)"
          :closable="false"
          style="margin-bottom: 20px"
        />
        
        <div v-if="riskResult.highRisks.length > 0" class="risk-section high-section">
          <h3 class="section-title">
            <el-icon><Warning /></el-icon>
            高风险预警 ({{ riskResult.highRisks.length }})
          </h3>
          <el-list>
            <el-list-item v-for="item in riskResult.highRisks" :key="item.ruleId">
              <el-tag type="danger">{{ item.location }}</el-tag>
              <span class="risk-name">{{ item.ruleName }}</span>
              <span class="risk-message">{{ item.riskMessage }}</span>
            </el-list-item>
          </el-list>
        </div>

        <div v-if="riskResult.mediumRisks.length > 0" class="risk-section medium-section">
          <h3 class="section-title">
            <el-icon><WarnTriangleFilled /></el-icon>
            中风险提示 ({{ riskResult.mediumRisks.length }})
          </h3>
          <el-list>
            <el-list-item v-for="item in riskResult.mediumRisks" :key="item.ruleId">
              <el-tag type="warning">{{ item.location }}</el-tag>
              <span class="risk-name">{{ item.ruleName }}</span>
              <span class="risk-message">{{ item.riskMessage }}</span>
            </el-list-item>
          </el-list>
        </div>

        <div v-if="riskResult.lowRisks.length > 0" class="risk-section low-section">
          <h3 class="section-title">
            <el-icon><InfoFilled /></el-icon>
            低风险提醒 ({{ riskResult.lowRisks.length }})
          </h3>
          <el-list>
            <el-list-item v-for="item in riskResult.lowRisks" :key="item.ruleId">
              <el-tag type="info">{{ item.location }}</el-tag>
              <span class="risk-name">{{ item.ruleName }}</span>
              <span class="risk-message">{{ item.riskMessage }}</span>
            </el-list-item>
          </el-list>
        </div>

        <div v-if="riskResult.totalRiskCount === 0" class="no-risk">
          <el-icon class="no-risk-icon"><CircleCheck /></el-icon>
          <span>恭喜！该路线未检测到风险隐患</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showRiskModal = false">关闭</el-button>
        <el-button type="primary" @click="generateReportFromModal">生成报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Plus, Warning, WarnTriangleFilled, InfoFilled, CircleCheck } from '@element-plus/icons-vue'
import { planApi } from '@/api/risk'
import type { RoutePlan, RoutePlanRequest, RiskCheckResponse } from '@/types'

const plans = ref<RoutePlan[]>([])
const showAddModal = ref(false)
const showRiskModal = ref(false)
const riskResult = ref<RiskCheckResponse | null>(null)
const currentPlanId = ref(0)

const formData = reactive<RoutePlanRequest>({
  planName: '',
  startLocation: '',
  endLocation: '',
  waypoints: [],
  travelDate: '',
  ageMin: 0,
  ageMax: 100,
  participantCount: 0
})

const waypointsInput = ref('')

const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    HIGH_RISK: 'danger',
    MEDIUM_RISK: 'warning',
    LOW_RISK: 'success',
    PENDING: 'info'
  }
  return types[status] || 'info'
}

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    HIGH_RISK: '高风险',
    MEDIUM_RISK: '中风险',
    LOW_RISK: '低风险',
    PENDING: '待筛查'
  }
  return texts[status] || status
}

const getRiskAlertType = (status: string) => {
  const types: Record<string, string> = {
    HIGH_RISK: 'error',
    MEDIUM_RISK: 'warning',
    LOW_RISK: 'success'
  }
  return types[status] || 'info'
}

const getOverallStatusText = (status: string) => {
  const texts: Record<string, string> = {
    HIGH_RISK: '高风险预警！该路线存在严重安全隐患，请重新评估',
    MEDIUM_RISK: '中风险提示，建议采取安全措施后再出行',
    LOW_RISK: '低风险，可正常出行'
  }
  return texts[status] || '风险评估完成'
}

const loadPlans = async () => {
  try {
    plans.value = await planApi.getAll()
  } catch (error) {
    console.error('加载路线计划失败:', error)
  }
}

const submitForm = async () => {
  if (!formData.planName || !formData.startLocation || !formData.endLocation || !formData.travelDate) {
    alert('请填写必填项')
    return
  }
  
  formData.waypoints = waypointsInput.value.split(',').map(s => s.trim()).filter(s => s)
  
  try {
    if (currentPlanId.value > 0) {
      await planApi.update(currentPlanId.value, formData)
      alert('更新成功')
    } else {
      await planApi.create(formData)
      alert('创建成功')
    }
    showAddModal.value = false
    resetForm()
    loadPlans()
  } catch (error) {
    console.error('提交失败:', error)
    alert('提交失败')
  }
}

const resetForm = () => {
  formData.planName = ''
  formData.startLocation = ''
  formData.endLocation = ''
  formData.waypoints = []
  formData.travelDate = ''
  formData.ageMin = 0
  formData.ageMax = 100
  formData.participantCount = 0
  waypointsInput.value = ''
  currentPlanId.value = 0
}

const editPlan = (plan: RoutePlan) => {
  currentPlanId.value = plan.id
  formData.planName = plan.planName
  formData.startLocation = plan.startLocation
  formData.endLocation = plan.endLocation
  waypointsInput.value = plan.waypoints || ''
  formData.travelDate = plan.travelDate
  formData.ageMin = plan.ageMin
  formData.ageMax = plan.ageMax
  formData.participantCount = plan.participantCount
  showAddModal.value = true
}

const checkRisk = async (plan: RoutePlan) => {
  try {
    riskResult.value = await planApi.checkRisk(plan.id)
    showRiskModal.value = true
  } catch (error) {
    console.error('风险筛查失败:', error)
    alert('风险筛查失败')
  }
}

const viewReport = (plan: RoutePlan) => {
  window.open(`/report/${plan.id}`, '_blank')
}

const goSchedule = (plan: RoutePlan) => {
  window.open(`/medical?planId=${plan.id}`, '_blank')
}

const goRelease = (plan: RoutePlan) => {
  window.open(`/release?planId=${plan.id}`, '_blank')
}

const generateReportFromModal = () => {
  if (riskResult.value) {
    window.open(`/report/${riskResult.value.planId}`, '_blank')
    showRiskModal.value = false
  }
}

const deletePlan = async (plan: RoutePlan) => {
  if (!confirm(`确定删除计划 "${plan.planName}" 吗？`)) return
  
  try {
    await planApi.delete(plan.id)
    alert('删除成功')
    loadPlans()
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

const handleRowClick = () => {}

loadPlans()
</script>

<style scoped>
.plan-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.risk-section {
  margin-bottom: 20px;
  padding: 15px;
  border-radius: 8px;
}

.high-section {
  background-color: #fef0f0;
  border-left: 4px solid #f56c6c;
}

.medium-section {
  background-color: #fdf6ec;
  border-left: 4px solid #e6a23c;
}

.low-section {
  background-color: #f0f9eb;
  border-left: 4px solid #67c23a;
}

.section-title {
  margin: 0 0 10px 0;
  font-size: 16px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-name {
  margin-left: 10px;
  font-weight: bold;
  color: #606266;
}

.risk-message {
  margin-left: 10px;
  color: #909399;
}

.no-risk {
  text-align: center;
  padding: 40px;
  color: #67c23a;
}

.no-risk-icon {
  font-size: 48px;
  margin-bottom: 10px;
  display: block;
}
</style>