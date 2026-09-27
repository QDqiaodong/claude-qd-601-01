<template>
  <div class="history">
    <div class="eq-panel">
      <div class="eq-toolbar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 280px"
        />
        <el-input v-model="managerName" style="width: 180px" placeholder="负责人姓名" clearable />
        <el-select v-model="itemStatus" style="width: 150px" placeholder="含事项状态" clearable>
          <el-option label="含待处理事项" value="PENDING" />
          <el-option label="含已分派事项" value="DISPATCHED" />
          <el-option label="含已承接事项" value="ACCEPTED" />
        </el-select>
        <el-button type="primary" size="small" @click="search">查询</el-button>
        <el-button size="small" @click="goBoard">返回交接台</el-button>
        <span class="eq-muted">共 {{ shifts.length }} 个班次</span>
      </div>

      <div v-if="loading" class="eq-empty">查询中…</div>
      <div v-else-if="!shifts.length" class="eq-empty">没有符合条件的历史班次</div>

      <div v-for="shift in shifts" :key="shift.id" class="shiftcard">
        <div class="shiftcard__head" @click="toggle(shift.id)">
          <div class="shiftcard__title">
            <el-tag size="small" :type="shift.open ? 'success' : 'info'" effect="dark">
              {{ shift.statusName }}
            </el-tag>
            <b>{{ shift.shiftNo }}</b>
            <span>{{ shift.businessDate }} {{ shift.periodName }}</span>
            <span class="eq-muted">负责人 {{ shift.managerName }}</span>
          </div>
          <div class="shiftcard__stats">
            <span>共 {{ shift.totalItems }}</span>
            <span class="num num--open">未承接 {{ shift.openCount }}</span>
            <span class="num num--ok">已承接 {{ shift.acceptedCount }}</span>
            <span v-if="shift.inheritedOpenCount" class="num num--inherit">
              滚入未结 {{ shift.inheritedOpenCount }}
            </span>
            <el-button link type="primary">{{ expanded[shift.id] ? '收起' : '展开事项' }}</el-button>
          </div>
        </div>

        <template v-if="expanded[shift.id]">
          <div v-if="shift.handoverNote" class="shiftcard__note">
            <el-tag size="small" type="info">交接说明</el-tag>{{ shift.handoverNote }}
          </div>
          <div v-if="shift.delayReason" class="shiftcard__note shiftcard__note--delay">
            <el-tag size="small" type="warning">延期原因</el-tag>{{ shift.delayReason }}
          </div>

          <el-table :data="shift.items" size="small" border>
            <el-table-column label="事项" min-width="220">
              <template #default="{ row }">
                <div class="cell-title">
                  <el-tag size="small" effect="dark" :type="typeTag(row.itemType)">{{ row.itemTypeName }}</el-tag>
                  <b>{{ row.itemNo }}</b>
                  <span>{{ row.title }}</span>
                </div>
                <div class="eq-muted">{{ row.detail }}</div>
                <div v-if="row.delayNote" class="cell-delay">延期：{{ row.delayNote }}</div>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="86">
              <template #default="{ row }">
                <el-tag size="small" :type="priorityTag(row.priority)">{{ row.priorityName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="负责教练" width="100">
              <template #default="{ row }">{{ row.ownerCoachName || '未分派' }}</template>
            </el-table-column>
            <el-table-column label="班次归属" width="180">
              <template #default="{ row }">
                <div>产生：{{ row.originatingShiftNo }}</div>
                <div class="eq-muted">当前：{{ row.currentShiftNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="最近承接" min-width="220">
              <template #default="{ row }">
                <template v-if="row.acceptance">
                  {{ formatTime(row.acceptance.acceptedAt) }} · {{ row.acceptance.acceptorName }}
                  在 {{ row.acceptance.shiftNo }} 确认
                  <div v-if="row.acceptance.note" class="eq-muted">{{ row.acceptance.note }}</div>
                </template>
                <span v-else class="eq-muted">尚未承接</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { navigate } from '../router'

const shifts = ref([])
const loading = ref(false)
const expanded = reactive({})
const managerName = ref('')
const itemStatus = ref('')

function defaultRange() {
  const d = new Date()
  const fmt = (day) => `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
  const from = new Date(d)
  from.setDate(d.getDate() - 30)
  return [fmt(from), fmt(d)]
}

const dateRange = ref(defaultRange())

async function search() {
  loading.value = true
  try {
    const params = {
      from: dateRange.value ? dateRange.value[0] : null,
      to: dateRange.value ? dateRange.value[1] : null,
      managerName: managerName.value || null,
      itemStatus: itemStatus.value || null
    }
    shifts.value = (await api.dutyShifts(params)) || []
    shifts.value.forEach((shift) => {
      expanded[shift.id] = true
    })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function toggle(id) {
  expanded[id] = !expanded[id]
}

function goBoard() {
  navigate('/duty')
}

function formatTime(text) {
  return text ? String(text).replace('T', ' ').slice(0, 16) : '—'
}

function typeTag(type) {
  return { LESSON: 'primary', HORSE: 'danger', STALL: 'warning', OTHER: 'info' }[type] || 'info'
}

function priorityTag(priority) {
  return { URGENT: 'danger', HIGH: 'warning', NORMAL: 'info', LOW: '' }[priority] || ''
}

function statusTag(status) {
  return { PENDING: 'danger', DISPATCHED: 'warning', ACCEPTED: 'success' }[status] || 'info'
}

onMounted(search)
</script>

<style scoped>
.shiftcard {
  border: 1px solid #ece3df;
  border-radius: 10px;
  margin-bottom: 12px;
  overflow: hidden;
}

.shiftcard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #fbf6f3;
  cursor: pointer;
}

.shiftcard__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #4a3129;
}

.shiftcard__stats {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #8c7c76;
}

.num--open {
  color: #c0563f;
}

.num--ok {
  color: #4f8a5b;
}

.num--inherit {
  color: #b07b3d;
}

.shiftcard__note {
  padding: 8px 14px;
  font-size: 13px;
  color: #5d4d47;
  border-top: 1px dashed #ece3df;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.shiftcard__note--delay {
  color: #9a6a3f;
}

.cell-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cell-delay {
  font-size: 12px;
  color: #9a6a3f;
}
</style>
