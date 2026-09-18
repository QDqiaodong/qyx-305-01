<template>
  <div class="home-container">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <el-icon class="welcome-icon"><Lock /></el-icon>
            <div class="welcome-text">
              <h2>研学路线出行风险多维度自动筛查预警系统</h2>
              <p>智能识别天气、路况、人员年龄、场地安全四类风险，为研学出行保驾护航</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="6">
        <el-card class="stat-card high-card">
          <div class="stat-icon">
            <el-icon><Warning /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.highRiskCount }}</div>
            <div class="stat-label">高风险预警</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card medium-card">
          <div class="stat-icon">
            <el-icon><WarnTriangleFilled /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.mediumRiskCount }}</div>
            <div class="stat-label">中风险预警</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card low-card">
          <div class="stat-icon">
            <el-icon><InfoFilled /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.lowRiskCount }}</div>
            <div class="stat-label">低风险提示</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card plan-card">
          <div class="stat-icon">
            <el-icon><FolderOpened /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalPlanCount }}</div>
            <div class="stat-label">路线计划数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="6">
        <el-card class="stat-card released-card">
          <div class="stat-icon"><el-icon><CircleCheck /></el-icon></div>
          <div class="stat-info"><div class="stat-value">{{ stats.releasedCount }}</div><div class="stat-label">已放行（可出门）</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card pending-doc-card">
          <div class="stat-icon"><el-icon><Document /></el-icon></div>
          <div class="stat-info"><div class="stat-value">{{ stats.pendingDocsCount }}</div><div class="stat-label">待齐件</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stale-card">
          <div class="stat-icon"><el-icon><RefreshRight /></el-icon></div>
          <div class="stat-info"><div class="stat-value">{{ stats.staleRecheckCount }}</div><div class="stat-label">筛查失效待重评</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card void-card">
          <div class="stat-icon"><el-icon><CircleClose /></el-icon></div>
          <div class="stat-info"><div class="stat-value">{{ stats.voidCount }}</div><div class="stat-label">已作废</div></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="16">
        <el-card class="chart-card">
          <template #header>
            <span>风险分布统计</span>
          </template>
          <div ref="chartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="quick-actions">
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="action-list">
            <el-button type="primary" class="action-btn" @click="$router.push('/plan')">
              <el-icon><Plus /></el-icon>
              <span>新建路线计划</span>
            </el-button>
            <el-button class="action-btn" @click="$router.push('/medical')">
              <el-icon><User /></el-icon>
              <span>随队医护排班</span>
            </el-button>
            <el-button type="success" plain class="action-btn" @click="$router.push('/release')">
              <el-icon><Van /></el-icon>
              <span>发车放行看板</span>
            </el-button>
            <el-button type="warning" plain class="action-btn" @click="$router.push('/rollcall')">
              <el-icon><Finished /></el-icon>
              <span>回程点名收口</span>
            </el-button>
            <el-button class="action-btn" @click="$router.push('/rules')">
              <el-icon><Setting /></el-icon>
              <span>管理风险规则</span>
            </el-button>
            <el-button class="action-btn" @click="checkAllRisks">
              <el-icon><Search /></el-icon>
              <span>批量风险筛查</span>
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card class="recent-plans">
          <template #header>
            <span>最新路线计划</span>
            <el-button type="text" @click="$router.push('/plan')">查看全部</el-button>
          </template>
          <el-table :data="recentPlans" style="width: 100%">
            <el-table-column prop="planName" label="计划名称" />
            <el-table-column prop="startLocation" label="出发地点" />
            <el-table-column prop="endLocation" label="目的地" />
            <el-table-column prop="travelDate" label="出行日期" />
            <el-table-column prop="participantCount" label="人数" />
            <el-table-column prop="status" label="风险">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)">{{ getStatusText(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="放行单">
              <template #default="scope">
                <el-tag :type="getPermitTagType(permitMap[scope.row.id]?.status)" effect="plain">
                  {{ permitMap[scope.row.id]?.statusText || '待齐件' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button type="text" @click="$router.push(`/report/${scope.row.id}`)">查看报告</el-button>
                <el-button type="text" @click="$router.push({ path: '/release' })">放行</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import {
  Lock, Warning, WarnTriangleFilled, InfoFilled, FolderOpened,
  Plus, Setting, Search, User, Van, Finished,
  CircleCheck, CircleClose, Document, RefreshRight
} from '@element-plus/icons-vue'
import { planApi } from '@/api/risk'
import { releaseApi } from '@/api/release'
import type { RoutePlan, ReleasePermit, DashboardStats } from '@/types'

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

const stats = ref<DashboardStats>({
  totalPlanCount: 0,
  highRiskCount: 0,
  mediumRiskCount: 0,
  lowRiskCount: 0,
  pendingScreeningCount: 0,
  releasedCount: 0,
  pendingDocsCount: 0,
  voidCount: 0,
  staleRecheckCount: 0,
  medicalStaffCount: 0,
  pediatricStaffCount: 0,
  assignmentCount: 0
})

const recentPlans = ref<RoutePlan[]>([])
const permitMap = ref<Record<number, ReleasePermit>>({})

const getPermitTagType = (status?: string) =>
  ({ RELEASED: 'success', PENDING: 'info', VOID: 'info', STALE_RECHECK: 'warning' } as Record<string, string>)[status || ''] || 'info'

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

const initChart = () => {
  if (!chartRef.value) return
  
  chartInstance = echarts.init(chartRef.value)
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      top: '5%',
      left: 'center'
    },
    series: [
      {
        name: '风险等级',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: [
          { value: stats.value.highRiskCount, name: '高风险', itemStyle: { color: '#f56c6c' } },
          { value: stats.value.mediumRiskCount, name: '中风险', itemStyle: { color: '#e6a23c' } },
          { value: stats.value.lowRiskCount, name: '低风险', itemStyle: { color: '#67c23a' } }
        ]
      }
    ]
  }
  
  chartInstance.setOption(option)
}

const loadStats = async () => {
  try {
    const [plans, dashboard, permits] = await Promise.all([
      planApi.getAll(),
      releaseApi.stats(),
      releaseApi.listAll()
    ])
    stats.value = dashboard
    recentPlans.value = plans.slice(0, 5)
    permitMap.value = {}
    permits.forEach(p => { permitMap.value[p.planId] = p })

    if (chartInstance) {
      initChart()
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const checkAllRisks = async () => {
  try {
    const plans = await planApi.getAll()
    for (const plan of plans) {
      if (plan.status === 'PENDING') {
        await planApi.checkRisk(plan.id)
      }
    }
    await loadStats()
    alert('批量风险筛查完成')
  } catch (error) {
    console.error('批量筛查失败:', error)
  }
}

onMounted(() => {
  loadStats()
  initChart()
  
  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})

onUnmounted(() => {
  chartInstance?.dispose()
})
</script>

<style scoped>
.home-container {
  padding: 0;
}

.welcome-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.welcome-content {
  display: flex;
  align-items: center;
  gap: 20px;
}

.welcome-icon {
  font-size: 64px;
  color: #fff;
}

.welcome-text {
  color: #fff;
}

.welcome-text h2 {
  margin: 0 0 10px 0;
  font-size: 24px;
}

.welcome-text p {
  margin: 0;
  opacity: 0.9;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
}

.stat-label {
  font-size: 14px;
  opacity: 0.7;
}

.high-card .stat-icon {
  background-color: #fef0f0;
  color: #f56c6c;
}

.high-card .stat-value {
  color: #f56c6c;
}

.medium-card .stat-icon {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.medium-card .stat-value {
  color: #e6a23c;
}

.low-card .stat-icon {
  background-color: #f0f9eb;
  color: #67c23a;
}

.low-card .stat-value {
  color: #67c23a;
}

.plan-card .stat-icon {
  background-color: #ecf5ff;
  color: #409eff;
}

.plan-card .stat-value {
  color: #409eff;
}

.released-card .stat-icon {
  background-color: #f0f9eb;
  color: #67c23a;
}

.released-card .stat-value {
  color: #67c23a;
}

.pending-doc-card .stat-icon {
  background-color: #ecf5ff;
  color: #409eff;
}

.pending-doc-card .stat-value {
  color: #409eff;
}

.stale-card .stat-icon {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.stale-card .stat-value {
  color: #e6a23c;
}

.void-card .stat-icon {
  background-color: #f4f4f5;
  color: #909399;
}

.void-card .stat-value {
  color: #909399;
}

.chart-container {
  height: 300px;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-btn {
  width: 100%;
  justify-content: flex-start;
  padding-left: 20px;
}
</style>