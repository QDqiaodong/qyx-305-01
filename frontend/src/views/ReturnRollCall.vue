<template>
  <div class="roll-container">
    <el-row :gutter="20">
      <!-- 车次列表 -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>回程点名</span>
              <el-radio-group v-model="phaseFilter" size="small">
                <el-radio-button label="ALL">全部</el-radio-button>
                <el-radio-button label="ROLLING">点名中</el-radio-button>
                <el-radio-button label="NOT_DEPARTED">未发车</el-radio-button>
                <el-radio-button label="CLOSED">已收口</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table
            :data="filteredList"
            size="small"
            highlight-current-row
            @row-click="selectTrip"
            :row-class-name="rowClass"
            max-height="660"
          >
            <el-table-column prop="planName" label="车次 / 计划" show-overflow-tooltip />
            <el-table-column label="点名" width="110">
              <template #default="scope">
                <span v-if="scope.row.phase === 'NOT_DEPARTED'" class="muted">未发车</span>
                <span v-else>{{ scope.row.returnedCount }}/{{ scope.row.totalCount }} 人已回</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="scope">
                <el-tag :type="phaseTagType(scope.row.phase)" size="small" effect="plain">
                  {{ scope.row.phaseText }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 点名详情 -->
      <el-col :span="14">
        <el-card v-if="!current" class="empty-card">
          <el-empty description="请选择左侧车次进行回程点名" />
        </el-card>

        <div v-else>
          <el-card class="detail-head">
            <div class="head-row">
              <div>
                <div class="plan-title">{{ current.planName }}</div>
                <div class="plan-sub">计划 #{{ current.planId }}</div>
              </div>
              <el-tag :type="phaseTagType(current.phase)" size="large" effect="dark">{{ current.phaseText }}</el-tag>
            </div>

            <!-- 已收口：冻住提示 -->
            <el-alert
              v-if="current.phase === 'CLOSED'"
              type="info" :closable="false" show-icon class="reason-alert"
              :title="`已于 ${current.closedAt} 收口，点名结果冻住`"
              description="按安全岗办：收口后这趟的点名不能再改——不能再往车上塞人、不能改勾选，也不能把缺人说明改没；晚到的孩子只能另开一趟。"
            />
            <!-- 点名中 -->
            <el-alert
              v-else-if="current.phase === 'ROLLING'"
              type="warning" :closable="false" show-icon class="reason-alert"
              :title="`已于 ${current.departedAt} 发车，按发车时带出去的人逐个勾回`"
              description="有人没上车回校，必须先为每个缺员写下缺人说明，这趟才能收口。"
            />
            <!-- 未发车 -->
            <el-alert
              v-else
              type="info" :closable="false" show-icon class="reason-alert"
              title="该趟还没发车：先在这里把发车要带出去的人录进名册"
              description="发车前可增删名册；一登记发车时刻名册即锁定，不能再往车上塞人，晚到的只能另开一趟。"
            />

            <div class="count-line">
              <el-tag size="small" type="info">名册 {{ current.totalCount }} 人</el-tag>
              <el-tag size="small" type="success">已勾回 {{ current.returnedCount }} 人</el-tag>
              <el-tag size="small" :type="current.missingCount ? 'danger' : 'success'">
                未回 {{ current.missingCount }} 人
              </el-tag>
              <el-tag v-if="current.phase === 'ROLLING'" size="small"
                      :type="current.missingWithoutNoteCount ? 'danger' : 'warning'">
                缺说明 {{ current.missingWithoutNoteCount }} 人
              </el-tag>
            </div>
          </el-card>

          <!-- 名册 / 点名表 -->
          <el-card class="book-card">
            <template #header>
              <div class="book-head">
                <span>{{ current.phase === 'NOT_DEPARTED' ? '出行名册（发车前可维护）' : '回程点名（按发车带出去的人勾）' }}</span>
                <span v-if="current.phase === 'ROLLING'" class="muted">勾上=已上车回校</span>
              </div>
            </template>

            <!-- 发车前：加人 -->
            <div v-if="current.phase === 'NOT_DEPARTED'" class="add-row">
              <el-input v-model="newName" placeholder="录入孩子姓名，回车加入名册" clearable
                        style="width: 260px" @keyup.enter="doAdd" />
              <el-button type="primary" :loading="mutating" @click="doAdd">加入名册</el-button>
              <el-button text type="primary" @click="goRelease">去发车放行 →</el-button>
            </div>

            <el-table :data="current.participants" size="small">
              <el-table-column label="#" type="index" width="50" />
              <el-table-column prop="personName" label="姓名" width="140" />
              <el-table-column label="回程勾选" width="100">
                <template #default="scope">
                  <el-checkbox
                    v-if="current.phase === 'ROLLING'"
                    :model-value="scope.row.returned"
                    :disabled="mutating"
                    @change="(v: boolean) => doMark(scope.row, v)"
                  >
                    已回
                  </el-checkbox>
                  <el-tag v-else-if="current.phase === 'CLOSED'"
                          :type="scope.row.returned ? 'success' : 'danger'" size="small">
                    {{ scope.row.returned ? '已回' : '未回' }}
                  </el-tag>
                  <span v-else class="muted">待发车</span>
                </template>
              </el-table-column>
              <el-table-column label="缺人说明">
                <template #default="scope">
                  <template v-if="current.phase === 'ROLLING' && !scope.row.returned">
                    <el-input
                      :model-value="scope.row.missingNote || ''"
                      type="textarea" :rows="1" maxlength="500"
                      :disabled="mutating"
                      placeholder="没上车回校的原因 / 去向，收口前必须写"
                      @change="(v: string) => doWriteNote(scope.row, v)"
                    />
                  </template>
                  <span v-else-if="current.phase === 'CLOSED' && !scope.row.returned" class="frozen-note">
                    {{ scope.row.missingNote || '（收口时未留说明）' }}
                  </span>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column v-if="current.phase === 'NOT_DEPARTED'" label="操作" width="80">
                <template #default="scope">
                  <el-button type="danger" link :loading="mutating" @click="doRemove(scope.row)">删</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-if="current.participants.length === 0" description="名册还是空的" :image-size="70" />
          </el-card>

          <!-- 收口结论与操作 -->
          <el-card class="action-card">
            <template #header><span>收口</span></template>

            <template v-if="current.phase === 'CLOSED'">
              <el-alert type="success" :closable="false" show-icon title="该趟已收口，全员点名结果已冻住" />
              <div v-if="current.frozenMissingNote" class="frozen-box">
                <div class="frozen-title">收口时冻住的缺人说明：</div>
                <pre class="frozen-pre">{{ current.frozenMissingNote }}</pre>
              </div>
            </template>

            <template v-else-if="current.phase === 'ROLLING'">
              <el-alert v-if="current.closable" type="success" :closable="false" show-icon
                        :title="current.missingCount === 0
                          ? '发车带出去的人已全部勾回到校，可以收口'
                          : `缺员 ${current.missingCount} 人均已写下缺人说明，可以收口`" />
              <div v-else class="block-box">
                <div class="block-title">还不能收口：</div>
                <div v-for="(r, i) in current.blockReasons" :key="i" class="block-item">· {{ r }}</div>
              </div>
              <div class="action-btns">
                <el-button type="success" :disabled="!current.closable || closing" :loading="closing" @click="doClose">
                  收口这趟
                </el-button>
              </div>
              <div class="gate-hint">
                收口后这趟人数冻住：晚到的孩子不能再补进本趟，只能另开一趟；缺人说明也改不没。
              </div>
            </template>

            <template v-else>
              <el-alert type="info" :closable="false" show-icon
                        title="还没发车，不能回程点名：先录名册，再到发车放行看板确认发车" />
            </template>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rollCallApi } from '@/api/rollcall'
import type { RollCall, Participant } from '@/types'

const route = useRoute()
const list = ref<RollCall[]>([])
const current = ref<RollCall | null>(null)
const phaseFilter = ref('ALL')
const newName = ref('')
const mutating = ref(false)
const closing = ref(false)

const filteredList = computed(() => {
  // 默认把还没收口的车次排到前面，方便回校即点
  const sorted = [...list.value].sort((a, b) => phaseOrder(a.phase) - phaseOrder(b.phase))
  return phaseFilter.value === 'ALL' ? sorted : sorted.filter(t => t.phase === phaseFilter.value)
})

const phaseOrder = (p: string) => (p === 'ROLLING' ? 0 : p === 'NOT_DEPARTED' ? 1 : 2)

const phaseTagType = (p: string) =>
  ({ ROLLING: 'warning', NOT_DEPARTED: 'info', CLOSED: 'success' } as Record<string, string>)[p] || 'info'

const rowClass = ({ row }: { row: RollCall }) =>
  current.value && row.planId === current.value.planId ? 'current-row' : ''

const goRelease = () => {
  window.open(`/release?planId=${current.value?.planId}`, '_blank')
}

/** 每次改动后用后端返回的权威视图整体替换，关掉页面再打开读到的仍是同一份落库结果 */
const applyResponse = (resp: RollCall, keepPlanId?: number) => {
  const idx = list.value.findIndex(t => t.planId === resp.planId)
  if (idx >= 0) list.value[idx] = resp
  else list.value.push(resp)
  const target = keepPlanId ?? current.value?.planId
  if (target === resp.planId) current.value = resp
}

const refresh = async (planId?: number) => {
  const all = await rollCallApi.listAll()
  list.value = all
  const target = planId ?? current.value?.planId
  current.value = target ? all.find(t => t.planId === target) || null : current.value
}

const selectTrip = (t: RollCall) => {
  current.value = t
  newName.value = ''
}

const doAdd = async () => {
  if (!current.value) return
  const name = newName.value.trim()
  if (!name) {
    ElMessage.warning('请输入姓名')
    return
  }
  mutating.value = true
  try {
    applyResponse(await rollCallApi.addParticipant(current.value.planId, name))
    newName.value = ''
    ElMessage.success('已加入名册')
  } catch (e: any) {
    ElMessageBox.alert(e?.message || '加入失败', '无法加入', { type: 'error' })
    await refresh(current.value.planId)
  } finally {
    mutating.value = false
  }
}

const doRemove = async (p: Participant) => {
  if (!current.value) return
  mutating.value = true
  try {
    applyResponse(await rollCallApi.removeParticipant(current.value.planId, p.id))
  } catch (e: any) {
    ElMessageBox.alert(e?.message || '删除失败', '无法删除', { type: 'error' })
    await refresh(current.value.planId)
  } finally {
    mutating.value = false
  }
}

const doMark = async (p: Participant, returned: boolean) => {
  if (!current.value) return
  mutating.value = true
  try {
    applyResponse(await rollCallApi.mark(current.value.planId, p.id, returned))
  } catch (e: any) {
    // 已收口等场景后端会拒：刷新回冻住的状态，前端勾不上去也改不回来
    ElMessageBox.alert(e?.message || '点名失败', '无法修改点名', { type: 'error' })
    await refresh(current.value.planId)
  } finally {
    mutating.value = false
  }
}

const doWriteNote = async (p: Participant, note: string) => {
  if (!current.value) return
  if (!note.trim()) {
    ElMessage.warning('缺人说明不能为空')
    await refresh(current.value.planId)
    return
  }
  try {
    applyResponse(await rollCallApi.writeMissingNote(current.value.planId, p.id, note))
  } catch (e: any) {
    ElMessageBox.alert(e?.message || '保存失败', '缺人说明没记下', { type: 'error' })
    await refresh(current.value.planId)
  }
}

const doClose = async () => {
  if (!current.value) return
  const missing = current.value.missingCount
  try {
    await ElMessageBox.confirm(
      missing === 0
        ? '全员已勾回到校。收口后这趟点名结果冻住，不能再改，确认收口？'
        : `仍有 ${missing} 人没上车回校，缺人说明已写齐。收口后说明随单冻住、改不没，确认收口？`,
      '确认收口',
      { type: 'warning', confirmButtonText: '确认收口', cancelButtonText: '再点点' }
    )
  } catch {
    return
  }
  closing.value = true
  try {
    const resp = await rollCallApi.close(current.value.planId)
    applyResponse(resp)
    ElMessage.success('已收口，点名结果冻住')
  } catch (e: any) {
    ElMessageBox.alert(e?.message || '收口失败', '这趟还收不了口', { type: 'error' })
    await refresh(current.value.planId)
  } finally {
    closing.value = false
  }
}

onMounted(async () => {
  await refresh()
  const q = Number(route.query.planId)
  if (q) {
    const t = list.value.find(x => x.planId === q)
    if (t) selectTrip(t)
  }
})
</script>

<style scoped>
.card-header, .book-head, .head-row { display: flex; justify-content: space-between; align-items: center; }
.detail-head { margin-bottom: 12px; }
.plan-title { font-size: 17px; font-weight: 700; }
.plan-sub { color: #909399; font-size: 12px; }
.reason-alert { margin-top: 10px; }
.count-line { margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; }
.book-card { margin-bottom: 12px; }
.add-row { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
.muted { color: #c0c4cc; }
.frozen-note { color: #606266; }
.action-card .block-box { background: #fef0f0; border-left: 4px solid #f56c6c; padding: 12px 14px; border-radius: 4px; }
.block-title { color: #f56c6c; font-weight: 700; margin-bottom: 6px; }
.block-item { color: #606266; line-height: 1.8; }
.action-btns { margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap; }
.gate-hint { margin-top: 10px; color: #909399; font-size: 12px; }
.frozen-box { margin-top: 12px; background: #f4f4f5; border-left: 4px solid #909399; padding: 10px 14px; border-radius: 4px; }
.frozen-title { font-weight: 700; color: #606266; margin-bottom: 6px; }
.frozen-pre { white-space: pre-wrap; font-family: inherit; margin: 0; color: #606266; }
.empty-card { min-height: 400px; display: flex; align-items: center; justify-content: center; }
:deep(.current-row) { background-color: #ecf5ff !important; }
</style>
