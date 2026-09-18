<template>
  <div class="report-container">
    <el-card class="report-card" v-if="report">
      <template #header>
        <div class="card-header">
          <span>路线风险筛查报告</span>
          <div>
            <el-button type="success" plain @click="goRelease">发车放行</el-button>
            <el-button type="primary" @click="printReport">
              <el-icon><Printer /></el-icon>
              打印报告
            </el-button>
          </div>
        </div>
      </template>

      <div class="report-content" id="report-content">
        <!-- 出门凭证有效性提示：旧报告不得在行程变更后继续当凭证；已发车的保持出门当时那一版 -->
        <el-alert
          v-if="report && report.departed"
          type="info"
          :closable="false"
          show-icon
          class="validity-banner"
          :title="`本车已于 ${report.departedAt} 发车，本报告保持出门当时那一版`"
          description="已发车的纸面单据封存：之后的规则调整不再改写本报告。"
        />
        <el-alert
          v-else-if="report && !report.screeningValid"
          type="error"
          :closable="false"
          show-icon
          class="validity-banner"
          title="本报告不能作为出门凭证"
          :description="report.screeningMismatchReason || '尚未筛查或筛查已失效，请按当前行程重新筛查后再生成报告。'"
        />
        <el-alert
          v-else-if="report && report.permitStatus !== 'RELEASED'"
          type="warning"
          :closable="false"
          show-icon
          class="validity-banner"
          :title="`筛查仍有效，但放行单当前为「${report.permitStatusText || '待齐件'}」，尚未放行`"
          :description="report.permitBlockReason || '需在发车放行页确认两本账齐备并提交放行。'"
        />
        <el-alert
          v-else-if="report"
          type="success"
          :closable="false"
          show-icon
          class="validity-banner"
          title="筛查有效且放行单为「已放行」，可凭本报告与放行单发车"
        />

        <div class="report-header">
          <h1>研学路线出行风险筛查报告</h1>
          <div class="report-info">
            <span>报告编号：{{ report.reportId }}</span>
            <span>生成时间：{{ report.generatedAt }}</span>
          </div>
        </div>

        <div class="plan-info">
          <h3>路线计划信息</h3>
          <el-row :gutter="20">
            <el-col :span="6">
              <el-card shadow="hover">
                <div class="info-label">计划名称</div>
                <div class="info-value">{{ report.planName }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover">
                <div class="info-label">出发地点</div>
                <div class="info-value">{{ report.startLocation }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover">
                <div class="info-label">目的地</div>
                <div class="info-value">{{ report.endLocation }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover">
                <div class="info-label">出行日期</div>
                <div class="info-value">{{ report.travelDate }}</div>
              </el-card>
            </el-col>
          </el-row>
          <el-row :gutter="20" style="margin-top: 15px;">
            <el-col :span="6">
              <el-card shadow="hover">
                <div class="info-label">年龄范围</div>
                <div class="info-value">{{ report.ageMin }} - {{ report.ageMax }} 岁</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover">
                <div class="info-label">参与人数</div>
                <div class="info-value">{{ report.participantCount }} 人</div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="hover">
                <div class="info-label">途经点位</div>
                <div class="info-value">{{ report.waypoints?.join(' → ') || '-' }}</div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div class="validity-section">
          <h3>出门凭证核对</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card shadow="hover" :class="report.screeningValid ? 'book-ok' : 'book-bad'">
                <div class="book-name">第一本账 · 筛查时效</div>
                <div class="book-result">
                  {{ report.departed ? '已发车，按出门当时那一版封存' : (report.screened ? (report.screeningValid ? '最新筛查仍对得上当前行程与规则' : '筛查已失效，需重新筛') : '尚未筛查') }}
                </div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="hover" :class="report.permitStatus === 'RELEASED' ? 'book-ok' : 'book-bad'">
                <div class="book-name">第二本账 · 放行单</div>
                <div class="book-result">{{ report.permitStatusText || '待齐件' }}</div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div class="risk-summary">
          <h3>风险概览</h3>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-card class="summary-card high-summary">
                <div class="summary-icon">
                  <el-icon><Warning /></el-icon>
                </div>
                <div class="summary-info">
                  <div class="summary-value">{{ report.riskSummary.highCount }}</div>
                  <div class="summary-label">高风险</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card class="summary-card medium-summary">
                <div class="summary-icon">
                  <el-icon><WarnTriangleFilled /></el-icon>
                </div>
                <div class="summary-info">
                  <div class="summary-value">{{ report.riskSummary.mediumCount }}</div>
                  <div class="summary-label">中风险</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card class="summary-card low-summary">
                <div class="summary-icon">
                  <el-icon><InfoFilled /></el-icon>
                </div>
                <div class="summary-info">
                  <div class="summary-value">{{ report.riskSummary.lowCount }}</div>
                  <div class="summary-label">低风险</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          <el-alert
            v-if="!report.departed && !report.screeningValid"
            type="error"
            :title="`评估结论已失效：「${getOverallStatusShort(report.overallStatus)}」是旧行程或旧规则下的结论，须重新筛查后才能作为出门参考`"
            :closable="false"
            style="margin-top: 20px"
          />
          <el-alert
            v-else
            :type="getAlertType(report.overallStatus)"
            :title="getOverallStatusText(report.overallStatus)"
            :closable="false"
            style="margin-top: 20px"
          />
        </div>

        <div class="risk-details" v-if="report.riskDetails.length > 0">
          <h3>隐患点位详情</h3>
          <el-table :data="report.riskDetails" style="width: 100%">
            <el-table-column prop="location" label="位置" />
            <el-table-column prop="riskType" label="风险类型">
              <template #default="scope">
                <el-tag type="info">{{ getRiskTypeText(scope.row.riskType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险等级">
              <template #default="scope">
                <el-tag :type="getRiskLevelType(scope.row.riskLevel)">{{ getRiskLevelText(scope.row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ruleName" label="规则名称" />
            <el-table-column prop="riskMessage" label="风险描述" :show-overflow-tooltip="true" />
          </el-table>
        </div>

        <div class="no-risk" v-else>
          <el-icon class="no-risk-icon"><CircleCheck /></el-icon>
          <h3>恭喜！该路线未检测到风险隐患</h3>
          <p>路线安全评估通过，可以正常出行</p>
        </div>

        <div class="report-footer">
          <div class="footer-info">
            <span>系统自动生成，仅供参考</span>
            <span>研学路线出行风险多维度自动筛查预警系统</span>
          </div>
        </div>
      </div>
    </el-card>

    <el-empty v-else description="报告加载中..." />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Printer, Warning, WarnTriangleFilled, InfoFilled, CircleCheck } from '@element-plus/icons-vue'
import { planApi } from '@/api/risk'
import type { ReportResponse } from '@/types'

const route = useRoute()
const report = ref<ReportResponse | null>(null)

const getAlertType = (status: string) => {
  const types: Record<string, string> = {
    HIGH_RISK: 'error',
    MEDIUM_RISK: 'warning',
    LOW_RISK: 'success'
  }
  return types[status] || 'info'
}

const getOverallStatusText = (status: string) => {
  const texts: Record<string, string> = {
    HIGH_RISK: '评估结论：高风险！该路线存在严重安全隐患，建议重新评估路线或推迟出行',
    MEDIUM_RISK: '评估结论：中风险，建议采取安全措施后再出行，加强安全管理',
    LOW_RISK: '评估结论：低风险，路线安全状况良好，可正常出行'
  }
  return texts[status] || '评估完成'
}

const getOverallStatusShort = (status: string) => {
  const texts: Record<string, string> = {
    HIGH_RISK: '高风险',
    MEDIUM_RISK: '中风险',
    LOW_RISK: '低风险',
    PENDING: '待筛查'
  }
  return texts[status] || status
}

const getRiskTypeText = (type: string) => {
  const texts: Record<string, string> = {
    WEATHER: '天气风险',
    TRAFFIC: '路况风险',
    AGE: '年龄风险',
    VENUE: '场地风险',
    CUSTOM: '自定义规则'
  }
  return texts[type] || type
}

const getRiskLevelType = (level: string) => {
  const types: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
  return types[level] || 'info'
}

const getRiskLevelText = (level: string) => {
  const texts: Record<string, string> = {
    HIGH: '高风险',
    MEDIUM: '中风险',
    LOW: '低风险'
  }
  return texts[level] || level
}

const loadReport = async () => {
  const id = parseInt(route.params.id as string)
  if (isNaN(id)) return
  
  try {
    report.value = await planApi.generateReport(id)
  } catch (error) {
    console.error('加载报告失败:', error)
    alert('加载报告失败')
  }
}

const printReport = () => {
  const printContent = document.getElementById('report-content')
  if (!printContent) return

  const originalContent = document.body.innerHTML
  document.body.innerHTML = printContent.outerHTML

  window.print()

  document.body.innerHTML = originalContent
}

const goRelease = () => {
  const id = parseInt(route.params.id as string)
  if (!isNaN(id)) {
    window.open(`/release?planId=${id}`, '_blank')
  }
}

onMounted(() => {
  loadReport()
})
</script>

<style scoped>
.report-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.report-header {
  text-align: center;
  margin-bottom: 30px;
}

.report-header h1 {
  font-size: 24px;
  margin: 0 0 10px 0;
  color: #303133;
}

.report-info {
  display: flex;
  justify-content: center;
  gap: 30px;
  color: #909399;
  font-size: 14px;
}

.plan-info, .risk-summary, .risk-details, .validity-section {
  margin-bottom: 30px;
}

.validity-banner {
  margin-bottom: 20px;
}

.validity-section h3 {
  font-size: 18px;
  margin: 0 0 15px 0;
  padding-bottom: 10px;
  border-bottom: 2px solid #1890ff;
}

.book-ok {
  border-left: 4px solid #67c23a;
}

.book-bad {
  border-left: 4px solid #f56c6c;
}

.book-name {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.book-result {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.plan-info h3, .risk-summary h3, .risk-details h3 {
  font-size: 18px;
  margin: 0 0 15px 0;
  padding-bottom: 10px;
  border-bottom: 2px solid #1890ff;
}

.info-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.info-value {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.summary-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.summary-info {
  flex: 1;
}

.summary-value {
  font-size: 36px;
  font-weight: bold;
}

.summary-label {
  font-size: 14px;
  opacity: 0.7;
}

.high-summary {
  background-color: #fef0f0;
}

.high-summary .summary-icon {
  background-color: #fbc4c4;
  color: #f56c6c;
}

.high-summary .summary-value {
  color: #f56c6c;
}

.medium-summary {
  background-color: #fdf6ec;
}

.medium-summary .summary-icon {
  background-color: #fde6d3;
  color: #e6a23c;
}

.medium-summary .summary-value {
  color: #e6a23c;
}

.low-summary {
  background-color: #f0f9eb;
}

.low-summary .summary-icon {
  background-color: #d9f7be;
  color: #67c23a;
}

.low-summary .summary-value {
  color: #67c23a;
}

.no-risk {
  text-align: center;
  padding: 60px 20px;
  background-color: #f0f9eb;
  border-radius: 8px;
}

.no-risk-icon {
  font-size: 64px;
  color: #67c23a;
  margin-bottom: 15px;
}

.no-risk h3 {
  margin: 0 0 10px 0;
  color: #67c23a;
}

.no-risk p {
  margin: 0;
  color: #909399;
}

.report-footer {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.footer-info {
  display: flex;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
}

@media print {
  .card-header {
    display: none;
  }
  
  .report-content {
    margin: 0;
    padding: 0;
  }
  
  .el-card {
    box-shadow: none;
    border: none;
  }
}
</style>