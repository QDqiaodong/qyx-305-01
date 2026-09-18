<template>
  <div class="release-container">
    <!-- 四档统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card class="stat-box released"><div class="num">{{ stats.releasedCount }}</div><div class="lbl">已放行（可出门）</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-box pending"><div class="num">{{ stats.pendingDocsCount }}</div><div class="lbl">待齐件</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-box stale"><div class="num">{{ stats.staleRecheckCount }}</div><div class="lbl">筛查失效待重评</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-box void"><div class="num">{{ stats.voidCount }}</div><div class="lbl">已作废</div></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 16px">
      <!-- 放行单列表 -->
      <el-col :span="11">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>发车放行看板</span>
              <el-radio-group v-model="statusFilter" size="small">
                <el-radio-button label="ALL">全部</el-radio-button>
                <el-radio-button label="PENDING">待齐件</el-radio-button>
                <el-radio-button label="RELEASED">已放行</el-radio-button>
                <el-radio-button label="STALE_RECHECK">待重评</el-radio-button>
                <el-radio-button label="VOID">已作废</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table
            :data="filteredPermits"
            size="small"
            highlight-current-row
            @row-click="selectPermit"
            :row-class-name="rowClass"
            max-height="620"
          >
            <el-table-column prop="planName" label="计划" show-overflow-tooltip />
            <el-table-column label="风险" width="80">
              <template #default="scope">
                <el-tag v-if="scope.row.screening.exists" :type="riskTagType(scope.row.screening.riskLevel)" size="small">
                  {{ riskText(scope.row.screening.riskLevel) }}
                </el-tag>
                <span v-else class="muted">未筛</span>
              </template>
            </el-table-column>
            <el-table-column label="两本账" width="120">
              <template #default="scope">
                <el-tooltip :content="`筛查账：${scope.row.screening.fresh ? '有效' : '失效/缺失'}；医护账：${scope.row.coverage.satisfied ? '满足' : '不满足'}`" placement="top">
                  <span>
                    <el-icon class="ledger-dot" :class="scope.row.screening.fresh ? 'ok' : 'bad'"><CircleCheck v-if="scope.row.screening.fresh" /><CircleClose v-else /></el-icon>
                    <el-icon class="ledger-dot" :class="scope.row.coverage.satisfied ? 'ok' : 'bad'"><CircleCheck v-if="scope.row.coverage.satisfied" /><CircleClose v-else /></el-icon>
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="放行单状态" width="120">
              <template #default="scope">
                <el-tag :type="permitTagType(scope.row.status)" size="small" effect="plain">{{ scope.row.statusText }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 放行详情 -->
      <el-col :span="13">
        <el-card v-if="!current" class="empty-card">
          <el-empty description="请选择左侧计划查看放行两本账" />
        </el-card>

        <div v-else>
          <el-card class="detail-head">
            <div class="head-row">
              <div>
                <div class="plan-title">{{ current.planName }}</div>
                <div class="plan-sub">计划 #{{ current.planId }}</div>
              </div>
              <el-tag :type="permitTagType(current.status)" size="large" effect="dark">{{ current.statusText }}</el-tag>
            </div>

            <!-- 当前单据的落档原因 -->
            <el-alert
              v-if="current.departedAt"
              type="info" :closable="false" show-icon class="reason-alert"
              :title="`已于 ${current.departedAt} 发车，出门条已封存`"
              description="已发车的纸面保持出门当时那一版：之后的规则调整、重筛、排班变动都不再改写本单；下列两本账为实时状态，仅供对照。"
            />
            <el-alert
              v-if="current.status === 'VOID' && current.voidReason"
              type="error" :closable="false" show-icon class="reason-alert"
              title="该放行单已作废" :description="current.voidReason"
            />
            <el-alert
              v-if="current.status === 'STALE_RECHECK' && current.recheckReason"
              type="warning" :closable="false" show-icon class="reason-alert"
              title="筛查失效待重评，不得按原放行单出车" :description="current.recheckReason"
            />
            <div v-if="current.status === 'RELEASED' && current.releasedAt" class="released-meta">
              已于 {{ current.releasedAt }} 放行
              <el-tag v-if="current.riskLevel === 'MEDIUM_RISK'" type="warning" size="small">中风险·知情出行</el-tag>
            </div>
            <div v-if="current.informedNote" class="informed-note">知情备注：{{ current.informedNote }}</div>
          </el-card>

          <!-- 第一本账：筛查时效 -->
          <el-card class="book-card">
            <template #header>
              <div class="book-head">
                <span>第一本账 · 风险筛查时效</span>
                <el-tag :type="current.screening.fresh ? 'success' : 'danger'" size="small">
                  {{ current.screening.fresh ? '对得上当前行程与规则' : '已失效 / 缺失' }}
                </el-tag>
              </div>
            </template>
            <template v-if="!current.screening.exists">
              <el-alert type="error" :closable="false" title="该计划还没有做过风险筛查" />
            </template>
            <template v-else>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="筛查结论">
                  <el-tag :type="riskTagType(current.screening.riskLevel)" size="small">{{ riskText(current.screening.riskLevel) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="筛查时间">{{ current.screening.screenedAt }}</el-descriptions-item>
                <el-descriptions-item label="筛查时·出行日期">
                  <span :class="diffClass(current.screening.snapshotTravelDate, current.screening.currentTravelDate)">{{ current.screening.snapshotTravelDate }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="当前·出行日期">{{ current.screening.currentTravelDate }}</el-descriptions-item>
                <el-descriptions-item label="筛查时·人数">{{ current.screening.snapshotParticipantCount }}</el-descriptions-item>
                <el-descriptions-item label="当前·人数">
                  <span :class="numDiff(current.screening.snapshotParticipantCount, current.screening.currentParticipantCount)">{{ current.screening.currentParticipantCount }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="筛查时·年龄">{{ current.screening.snapshotAgeMin }}-{{ current.screening.snapshotAgeMax }}</el-descriptions-item>
                <el-descriptions-item label="当前·年龄">
                  <span :class="ageDiff()">{{ current.screening.currentAgeMin }}-{{ current.screening.currentAgeMax }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="筛查时·途经点" :span="2">{{ current.screening.snapshotWaypoints || '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前·途经点" :span="2">
                  <span :class="diffClass(current.screening.snapshotWaypoints, current.screening.currentWaypoints)">{{ current.screening.currentWaypoints || '-' }}</span>
                </el-descriptions-item>
              </el-descriptions>
              <el-alert
                v-if="!current.screening.fresh"
                type="error" :closable="false" class="reason-alert"
                :title="current.screening.mismatchReason"
              />
            </template>
          </el-card>

          <!-- 第二本账：医护覆盖 -->
          <el-card class="book-card">
            <template #header>
              <div class="book-head">
                <span>第二本账 · 当天医护覆盖</span>
                <el-tag :type="current.coverage.satisfied ? 'success' : 'danger'" size="small">
                  {{ current.coverage.satisfied ? '覆盖满足' : '覆盖不足' }}
                </el-tag>
              </div>
            </template>
            <div class="cov-line">
              <el-tag size="small">在岗 {{ current.coverage.coveredCount }} 人</el-tag>
              <el-tag size="small" :type="current.coverage.requiredCount >= 2 ? 'danger' : 'info'">需 {{ current.coverage.requiredCount }} 人{{ current.coverage.requiredCount >= 2 ? '（高风险双人）' : '' }}</el-tag>
              <el-tag size="small" :type="current.coverage.pediatricRequired ? 'warning' : 'success'">
                儿科 {{ current.coverage.pediatricRequired ? (current.coverage.pediatricSatisfied ? '已满足' : '缺失') : '不要求' }}
              </el-tag>
            </div>
            <el-table v-if="current.coverage.staff.length" :data="current.coverage.staff" size="small" style="margin-top: 8px">
              <el-table-column prop="staffName" label="医护" width="100" />
              <el-table-column prop="title" label="职称" />
              <el-table-column label="儿科" width="70">
                <template #default="s"><el-tag v-if="s.row.pediatricQualified" type="success" size="small">是</el-tag><span v-else>-</span></template>
              </el-table-column>
              <el-table-column label="排班日期" width="150">
                <template #default="s">
                  <el-tag :type="s.row.dateMatched ? 'success' : 'danger'" size="small">{{ s.row.assignmentDate }}{{ s.row.dateMatched ? '' : '·不符' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-alert
              v-if="!current.coverage.satisfied"
              type="error" :closable="false" class="reason-alert"
              :title="'医护覆盖未满足：' + current.coverage.reasons.join('；')"
            />
          </el-card>

          <!-- 放行结论与操作 -->
          <el-card class="action-card">
            <template #header><span>放行结论</span></template>

            <el-alert
              v-if="current.departedAt"
              type="info" :closable="false" show-icon
              title="该计划已发车，单据封存，无需再放行"
            />

            <template v-else>
              <el-alert
                v-if="current.releasable"
                :type="current.screening.riskLevel === 'MEDIUM_RISK' ? 'warning' : 'success'"
                :closable="false" show-icon
                :title="current.screening.riskLevel === 'MEDIUM_RISK' ? '两本账齐备：中风险，可在填写知情备注后放行' : '两本账齐备：可放行出车'"
              />

              <div v-else class="block-box">
                <div class="block-title">不可放行，原因如下：</div>
                <div v-for="(r, i) in current.blockReasons" :key="i" class="block-item">· {{ r }}</div>
              </div>

              <!-- 知情备注：只允许中风险单 -->
              <div v-if="current.releasable && current.screening.riskLevel === 'MEDIUM_RISK'" class="note-row">
                <span class="note-label">知情备注：</span>
                <el-input v-model="informedNote" type="textarea" :rows="2" maxlength="500" show-word-limit
                          placeholder="中风险方可填写：带队老师已知悉中风险并落实相应措施" />
              </div>
            </template>

            <div class="action-btns">
              <template v-if="!current.departedAt">
                <el-button type="success" :disabled="!current.releasable || submitting" :loading="submitting" @click="doSubmit">
                  提交放行
                </el-button>
                <el-button
                  v-if="current.status === 'RELEASED'"
                  type="warning" :loading="departing" @click="doDepart"
                >
                  确认发车
                </el-button>
                <el-button @click="doRecheck" :loading="rechecking">重新筛查</el-button>
                <el-button @click="goSchedule">去排班</el-button>
                <el-button type="info" plain @click="goReport">查看报告</el-button>
                <el-button type="danger" plain :disabled="current.status === 'VOID'" @click="doVoid">作废</el-button>
              </template>
              <el-button v-else type="info" plain @click="goReport">查看报告</el-button>
              <el-button v-if="current.departedAt" type="warning" plain @click="goRollCall">回程点名</el-button>
            </div>
            <div v-if="!current.departedAt && !current.releasable" class="gate-hint">
              高风险安全岗不认知情备注：须先改线、或关闭触发该高风险的规则后重新筛查，等级降下来且两本账齐备，才会出绿单。
            </div>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { releaseApi } from '@/api/release'
import { planApi } from '@/api/risk'
import type { ReleasePermit, DashboardStats } from '@/types'

const route = useRoute()
const router = useRouter()
const permits = ref<ReleasePermit[]>([])
const current = ref<ReleasePermit | null>(null)
const statusFilter = ref('ALL')
const informedNote = ref('')
const submitting = ref(false)
const rechecking = ref(false)
const departing = ref(false)
const stats = ref<DashboardStats>({
  totalPlanCount: 0, highRiskCount: 0, mediumRiskCount: 0, lowRiskCount: 0, pendingScreeningCount: 0,
  releasedCount: 0, pendingDocsCount: 0, voidCount: 0, staleRecheckCount: 0,
  medicalStaffCount: 0, pediatricStaffCount: 0, assignmentCount: 0
})

const filteredPermits = computed(() =>
  statusFilter.value === 'ALL' ? permits.value : permits.value.filter(p => p.status === statusFilter.value)
)

const riskText = (l?: string) => ({ HIGH_RISK: '高风险', MEDIUM_RISK: '中风险', LOW_RISK: '低风险' } as Record<string, string>)[l || ''] || '-'
const riskTagType = (l?: string) => ({ HIGH_RISK: 'danger', MEDIUM_RISK: 'warning', LOW_RISK: 'success' } as Record<string, string>)[l || ''] || 'info'
const permitTagType = (s: string) =>
  ({ RELEASED: 'success', PENDING: 'info', VOID: 'info', STALE_RECHECK: 'warning' } as Record<string, string>)[s] || 'info'

const rowClass = ({ row }: { row: ReleasePermit }) =>
  current.value && row.planId === current.value.planId ? 'current-row' : ''

const diffClass = (a?: string, b?: string) => (a === b ? 'same' : 'diff')
const numDiff = (a?: number, b?: number) => (a === b ? 'same' : 'diff')
const ageDiff = () => {
  const s = current.value?.screening
  if (!s) return 'same'
  return s.snapshotAgeMin === s.currentAgeMin && s.snapshotAgeMax === s.currentAgeMax ? 'same' : 'diff'
}

const loadAll = async (keepPlanId?: number) => {
  const [list, st] = await Promise.all([releaseApi.listAll(), releaseApi.stats()])
  permits.value = list
  stats.value = st
  const targetId = keepPlanId ?? current.value?.planId
  if (targetId) {
    const fresh = list.find(p => p.planId === targetId)
    if (fresh) {
      current.value = fresh
      return
    }
  }
  current.value = null
}

const selectPermit = (p: ReleasePermit) => {
  current.value = p
  informedNote.value = p.informedNote || ''
}

const doSubmit = async () => {
  if (!current.value) return
  const level = current.value.screening.riskLevel
  const note = informedNote.value.trim()
  if (level === 'MEDIUM_RISK' && !note) {
    try {
      await ElMessageBox.confirm('中风险放行建议填写知情备注。确认不填备注直接放行？', '确认', { type: 'warning' })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const updated = await releaseApi.submit(current.value.planId, { informedNote: note || undefined })
    await loadAll(updated.planId)
    ElMessage.success('放行成功，已出具放行单')
  } catch (e: any) {
    // 撞在不可放行条件上：明确失败、逐条说清原因，绝不出绿单
    await loadAll(current.value.planId)
    ElMessageBox.alert(e?.message || '放行失败', '放行失败', { type: 'error', confirmButtonText: '知道了' })
  } finally {
    submitting.value = false
  }
}

const doRecheck = async () => {
  if (!current.value) return
  rechecking.value = true
  try {
    await planApi.checkRisk(current.value.planId)
    await loadAll(current.value.planId)
    ElMessage.success('已按当前行程重新筛查')
  } catch (e: any) {
    ElMessage.error(e?.message || '重新筛查失败')
  } finally {
    rechecking.value = false
  }
}

const doDepart = async () => {
  if (!current.value) return
  try {
    await ElMessageBox.confirm(
      '登记发车时刻后，出门条立即封存：之后的规则调整、重筛、排班变动都不再改写这张单。确认发车？',
      '确认发车',
      { type: 'warning', confirmButtonText: '确认发车', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  departing.value = true
  try {
    const updated = await releaseApi.depart(current.value.planId)
    await loadAll(updated.planId)
    ElMessage.success('已登记发车时刻，单据封存')
  } catch (e: any) {
    await loadAll(current.value.planId)
    ElMessageBox.alert(e?.message || '发车登记失败', '发车登记失败', { type: 'error', confirmButtonText: '知道了' })
  } finally {
    departing.value = false
  }
}

const doVoid = async () => {
  if (!current.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写作废原因', '作废放行单', {
      confirmButtonText: '作废', cancelButtonText: '取消', inputPlaceholder: '如：行程临时取消', inputValue: '人工作废'
    })
    await releaseApi.manualVoid(current.value.planId, value || '人工作废')
    await loadAll(current.value.planId)
    ElMessage.success('已作废')
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.message || '作废失败')
  }
}

const goSchedule = () => router.push({ path: '/medical', query: current.value ? { planId: String(current.value.planId) } : {} })
const goReport = () => current.value && window.open(`/report/${current.value.planId}`, '_blank')
const goRollCall = () => current.value && router.push({ path: '/rollcall', query: { planId: String(current.value.planId) } })

onMounted(async () => {
  await loadAll()
  const q = Number(route.query.planId)
  if (q) {
    const p = permits.value.find(x => x.planId === q)
    if (p) selectPermit(p)
  }
})
</script>

<style scoped>
.stat-row .stat-box { text-align: center; padding: 8px 0; }
.stat-box .num { font-size: 30px; font-weight: 700; }
.stat-box .lbl { color: #909399; font-size: 13px; margin-top: 4px; }
.stat-box.released .num { color: #67c23a; }
.stat-box.pending .num { color: #409eff; }
.stat-box.stale .num { color: #e6a23c; }
.stat-box.void .num { color: #909399; }
.card-header, .book-head, .head-row { display: flex; justify-content: space-between; align-items: center; }
.detail-head { margin-bottom: 12px; }
.plan-title { font-size: 17px; font-weight: 700; }
.plan-sub { color: #909399; font-size: 12px; }
.reason-alert { margin-top: 10px; }
.released-meta { margin-top: 10px; color: #67c23a; display: flex; gap: 8px; align-items: center; }
.informed-note { margin-top: 8px; color: #e6a23c; font-size: 13px; }
.book-card { margin-bottom: 12px; }
.cov-line { display: flex; gap: 8px; flex-wrap: wrap; }
.ledger-dot { font-size: 16px; vertical-align: middle; margin-right: 2px; }
.ledger-dot.ok { color: #67c23a; }
.ledger-dot.bad { color: #f56c6c; }
.muted { color: #c0c4cc; }
.same { color: #303133; }
.diff { color: #f56c6c; font-weight: 700; }
.action-card .block-box { background: #fef0f0; border-left: 4px solid #f56c6c; padding: 12px 14px; border-radius: 4px; }
.block-title { color: #f56c6c; font-weight: 700; margin-bottom: 6px; }
.block-item { color: #606266; line-height: 1.8; }
.note-row { margin: 14px 0; }
.note-label { font-weight: 600; }
.action-btns { margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap; }
.gate-hint { margin-top: 10px; color: #909399; font-size: 12px; }
.empty-card { min-height: 400px; display: flex; align-items: center; justify-content: center; }
:deep(.current-row) { background-color: #ecf5ff !important; }
</style>
