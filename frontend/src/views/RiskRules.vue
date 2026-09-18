<template>
  <div class="rules-container">
    <el-card class="rules-card">
      <template #header>
        <div class="card-header">
          <span>风险规则管理</span>
          <el-button type="primary" @click="showAddModal = true">
            <el-icon><Plus /></el-icon>
            新增规则
          </el-button>
        </div>
      </template>

      <el-row :gutter="20" style="margin-bottom: 20px;">
        <el-col :span="6">
          <el-select v-model="filterType" placeholder="按类型筛选" clearable>
            <el-option label="天气风险" value="WEATHER" />
            <el-option label="路况风险" value="TRAFFIC" />
            <el-option label="年龄风险" value="AGE" />
            <el-option label="场地风险" value="VENUE" />
            <el-option label="自定义规则" value="CUSTOM" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="filterLevel" placeholder="按风险等级筛选" clearable>
            <el-option label="高风险" value="HIGH" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="低风险" value="LOW" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="filterStatus" placeholder="按状态筛选" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-col>
      </el-row>

      <el-table :data="filteredRules" style="width: 100%">
        <el-table-column prop="ruleCode" label="规则代码" />
        <el-table-column prop="ruleName" label="规则名称" />
        <el-table-column prop="ruleType" label="规则类型">
          <template #default="scope">
            <el-tag type="info">{{ getRuleTypeText(scope.row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级">
          <template #default="scope">
            <el-tag :type="getRiskLevelType(scope.row.riskLevel)">{{ getRiskLevelText(scope.row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conditionExpression" label="条件表达式" :show-overflow-tooltip="true" />
        <el-table-column prop="warningMessage" label="预警信息" :show-overflow-tooltip="true" />
        <el-table-column prop="enabled" label="状态">
          <template #default="scope">
            <el-switch 
              :value="scope.row.enabled === 1" 
              @change="toggleRule(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button type="text" size="small" @click="editRule(scope.row)">编辑</el-button>
            <el-button type="text" size="small" danger @click="deleteRule(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAddModal" title="新增风险规则" width="700px">
      <el-form :model="formData" label-width="120px">
        <el-form-item label="规则代码" required>
          <el-input v-model="formData.ruleCode" placeholder="请输入规则代码" />
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="formData.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="formData.ruleType" placeholder="选择规则类型">
            <el-option label="天气风险" value="WEATHER" />
            <el-option label="路况风险" value="TRAFFIC" />
            <el-option label="年龄风险" value="AGE" />
            <el-option label="场地风险" value="VENUE" />
            <el-option label="自定义规则" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级" required>
          <el-select v-model="formData.riskLevel" placeholder="选择风险等级">
            <el-option label="高风险" value="HIGH" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="低风险" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件表达式" required>
          <el-input v-model="formData.conditionExpression" type="textarea" placeholder="输入条件表达式" />
          <div class="expression-tips">
            <span class="tip-title">可用表达式：</span>
            <span class="tip-item">WEATHER: RAINY_SEASON, HOT_WEATHER, COLD_WEATHER, WEEKEND, HOLIDAY</span>
            <span class="tip-item">TRAFFIC: PEAK_HOUR, LONG_DISTANCE, MOUNTAIN_ROAD, COASTAL_ROAD</span>
            <span class="tip-item">AGE: CHILDREN, ELDERLY, WIDE_AGE_RANGE, TEENAGER</span>
            <span class="tip-item">VENUE: HIGH_ALTITUDE, WATER_ACTIVITY, CROWDED_AREA, CONSTRUCTION</span>
            <span class="tip-item">CUSTOM: AGE_MIN_LESS_THAN_6, AGE_MAX_GREATER_THAN_70, PARTICIPANTS_MORE_THAN_50, PARTICIPANTS_MORE_THAN_100, LOCATION_CONTAINS_DANGER</span>
          </div>
        </el-form-item>
        <el-form-item label="预警信息" required>
          <el-input v-model="formData.warningMessage" type="textarea" placeholder="输入预警信息" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddModal = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ruleApi } from '@/api/risk'
import type { RiskRule, RiskRuleRequest } from '@/types'

const rules = ref<RiskRule[]>([])
const showAddModal = ref(false)
const currentRuleId = ref(0)

const filterType = ref('')
const filterLevel = ref('')
const filterStatus = ref<number | ''>('')

const formData = reactive<RiskRuleRequest>({
  ruleCode: '',
  ruleName: '',
  ruleType: '',
  riskLevel: '',
  conditionExpression: '',
  warningMessage: '',
  enabled: 1
})

const getRuleTypeText = (type: string) => {
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

const filteredRules = computed(() => {
  return rules.value.filter(rule => {
    if (filterType.value && rule.ruleType !== filterType.value) return false
    if (filterLevel.value && rule.riskLevel !== filterLevel.value) return false
    if (filterStatus.value !== '' && rule.enabled !== filterStatus.value) return false
    return true
  })
})

const loadRules = async () => {
  try {
    rules.value = await ruleApi.getAll()
  } catch (error) {
    console.error('加载规则失败:', error)
  }
}

const submitForm = async () => {
  if (!formData.ruleCode || !formData.ruleName || !formData.ruleType || !formData.riskLevel || !formData.conditionExpression || !formData.warningMessage) {
    alert('请填写必填项')
    return
  }
  
  try {
    if (currentRuleId.value > 0) {
      await ruleApi.update(currentRuleId.value, formData)
      alert('更新成功')
    } else {
      await ruleApi.create(formData)
      alert('创建成功')
    }
    showAddModal.value = false
    resetForm()
    loadRules()
  } catch (error) {
    console.error('提交失败:', error)
    alert('提交失败')
  }
}

const resetForm = () => {
  formData.ruleCode = ''
  formData.ruleName = ''
  formData.ruleType = ''
  formData.riskLevel = ''
  formData.conditionExpression = ''
  formData.warningMessage = ''
  formData.enabled = 1
  currentRuleId.value = 0
}

const editRule = (rule: RiskRule) => {
  currentRuleId.value = rule.id
  formData.ruleCode = rule.ruleCode
  formData.ruleName = rule.ruleName
  formData.ruleType = rule.ruleType
  formData.riskLevel = rule.riskLevel
  formData.conditionExpression = rule.conditionExpression
  formData.warningMessage = rule.warningMessage
  formData.enabled = rule.enabled
  showAddModal.value = true
}

const toggleRule = async (rule: RiskRule) => {
  try {
    await ruleApi.toggle(rule.id)
    loadRules()
  } catch (error) {
    console.error('切换状态失败:', error)
    alert('切换状态失败')
  }
}

const deleteRule = async (rule: RiskRule) => {
  if (!confirm(`确定删除规则 "${rule.ruleName}" 吗？`)) return
  
  try {
    await ruleApi.delete(rule.id)
    alert('删除成功')
    loadRules()
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

loadRules()
</script>

<style scoped>
.rules-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.expression-tips {
  margin-top: 10px;
  padding: 10px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.tip-title {
  font-weight: bold;
  display: block;
  margin-bottom: 8px;
}

.tip-item {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
</style>