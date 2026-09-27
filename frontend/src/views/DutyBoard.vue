<template>
  <div class="duty">
    <!-- 身份条：前端仅做体验，角色与归属后端强制 -->
    <div class="eq-panel identity">
      <div class="identity__who">
        <span class="eq-muted">当前值班身份</span>
        <el-input v-model="identity.name" placeholder="操作人姓名" style="width: 160px" size="small" />
        <el-radio-group v-model="identity.role" size="small">
          <el-radio-button label="STAFF">普通教练</el-radio-button>
          <el-radio-button label="MANAGER">值班负责人</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="identity.role === 'STAFF'"
          v-model="identity.coachId"
          size="small"
          filterable
          placeholder="选择我是哪位教练（补充事项时校验归属）"
          style="width: 300px"
        >
          <el-option
            v-for="coach in coaches"
            :key="coach.id"
            :label="coach.coachNo + ' ' + coach.name"
            :value="coach.id"
          />
        </el-select>
      </div>
      <div class="identity__ops">
        <el-button size="small" @click="reload">刷新清单</el-button>
        <el-button size="small" @click="goHistory">历史班次查询</el-button>
      </div>
    </div>

    <el-alert
      v-if="stale"
      type="warning"
      show-icon
      :closable="false"
      title="交接清单已被其他人更新（下一班建立 / 补充 / 分派 / 承接刚发生），页面已自动重新载入，请基于最新清单操作"
      style="margin-bottom: 12px"
    />

    <div v-if="!shift" class="eq-panel">
      <div class="eq-empty">
        还没有任何值班班次。由值班负责人建立第一个班次后，系统会自动从当班课程、马匹状态、栏位安排生成交接清单。
      </div>
      <div style="text-align: center; padding-bottom: 20px">
        <el-button type="primary" :disabled="!isManager" @click="openCreate">建立值班班次</el-button>
        <span v-if="!isManager" class="eq-muted" style="margin-left: 10px">仅值班负责人可开班</span>
      </div>
    </div>

    <template v-else>
      <!-- 班次头 -->
      <div class="eq-panel head">
        <div class="head__main">
          <div class="head__title">
            <h2>{{ shift.shiftNo }} · {{ shift.businessDate }} {{ shift.periodName }}</h2>
            <el-tag :type="shift.open ? 'success' : 'info'" effect="dark" size="small">
              {{ shift.statusName }}
            </el-tag>
            <el-tag v-if="shift.open && isLatest" type="danger" size="small">当前最新班次</el-tag>
            <el-button v-if="!isLatest" size="small" type="warning" plain @click="goLatest">
              回到最新班次
            </el-button>
          </div>
          <div class="head__meta">
            <span>负责人：<b>{{ shift.managerName }}</b></span>
            <span>开班：{{ formatTime(shift.startedAt) }}</span>
            <span v-if="shift.closedAt">结束：{{ formatTime(shift.closedAt) }}（{{ shift.closedBy }}）</span>
            <span v-if="shift.prevShiftNo">
              上一班：
              <el-link type="primary" :underline="false" @click="loadShift(shift.prevShiftId)">
                {{ shift.prevShiftNo }}（{{ shift.prevShiftDate }}）
              </el-link>
            </span>
            <span>清单版本 v{{ shift.version }}</span>
          </div>
          <div class="head__note">
            <div class="head__note-text">
              <span class="eq-muted">交接总说明：</span>{{ shift.handoverNote || '（未填写）' }}
            </div>
            <el-button
              v-if="canManage"
              size="small"
              :disabled="!shift.open || !isLatest"
              @click="openNote"
            >
              编辑说明
            </el-button>
          </div>
          <div v-if="shift.delayReason" class="head__delay">
            <el-tag type="warning" size="small">关班延期说明</el-tag>
            {{ shift.delayReason }}
          </div>
        </div>

        <div class="head__stats">
          <div class="stat"><b>{{ shift.totalItems }}</b><span>事项总数</span></div>
          <div class="stat stat--open"><b>{{ shift.openCount }}</b><span>未承接</span></div>
          <div class="stat"><b>{{ shift.dispatchedCount }}</b><span>已分派</span></div>
          <div class="stat stat--ok"><b>{{ shift.acceptedCount }}</b><span>已承接</span></div>
          <div class="stat stat--inherit"><b>{{ shift.inheritedOpenCount }}</b><span>上一班滚入</span></div>
        </div>
      </div>

      <!-- 上一班滚入未结事项提示 -->
      <el-alert
        v-if="shift.inheritedOpenCount > 0"
        type="error"
        show-icon
        :closable="false"
        :title="`有 ${shift.inheritedOpenCount} 个事项由上一班延期滚入本班，必须逐项确认承接，不会因班次结束而消失`"
        style="margin-bottom: 12px"
      />

      <!-- 个人待办 -->
      <el-alert
        v-if="myOpenItems.length"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
        :title="`分派给我的未结事项 ${myOpenItems.length} 个：` + myOpenItems.map((i) => i.itemNo).join('、')"
      />

      <!-- 操作条 -->
      <div class="eq-panel opsbar">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button label="">全部状态</el-radio-button>
          <el-radio-button label="PENDING">待处理</el-radio-button>
          <el-radio-button label="DISPATCHED">已分派</el-radio-button>
          <el-radio-button label="ACCEPTED">已承接</el-radio-button>
        </el-radio-group>
        <el-select v-model="typeFilter" size="small" style="width: 120px" placeholder="全部类型" clearable>
          <el-option label="课程" value="LESSON" />
          <el-option label="马匹" value="HORSE" />
          <el-option label="栏位" value="STALL" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-checkbox v-model="mineOnly" :disabled="identity.role !== 'STAFF' || !identity.coachId">
          只看分派给我的
        </el-checkbox>
        <span class="eq-muted">共 {{ visibleItems.length }} 项</span>
        <span style="flex: 1"></span>
        <template v-if="isManager && shift.open && isLatest">
          <el-button size="small" @click="sync">同步当班课程 / 马匹 / 栏位</el-button>
          <el-button size="small" @click="openAddItem">补充事项</el-button>
          <el-button size="small" type="primary" @click="openCreateNext">建立下一班</el-button>
          <el-button size="small" type="danger" plain @click="openClose">结束班次</el-button>
        </template>
        <el-tag v-else-if="!isManager" size="small" type="info">
          普通教练可补充自己负责的事项；开班 / 分派 / 承接 / 结束班次由负责人执行
        </el-tag>
        <el-tag v-else-if="!shift.open" size="small" type="info">本班次已结束，仅可查询与追溯</el-tag>
        <el-tag v-else-if="!isLatest" size="small" type="warning">
          已有更新的班次，新变更与写操作请在最新班次进行
        </el-tag>
      </div>

      <!-- 事项清单 -->
      <div v-if="!visibleItems.length" class="eq-panel">
        <div class="eq-empty">没有符合筛选的事项</div>
      </div>
      <div v-for="item in visibleItems" :key="item.id" class="eq-panel item" :class="itemClass(item)">
        <div class="item__head">
          <div class="item__title">
            <span class="item__no">{{ item.itemNo }}</span>
            <el-tag size="small" effect="dark" :type="typeTag(item.itemType)">{{ item.itemTypeName }}</el-tag>
            <el-tag size="small" :type="priorityTag(item.priority)" effect="plain">
              {{ item.priorityName }}优先级
            </el-tag>
            <el-tag size="small" :type="statusTag(item.status)" effect="dark">{{ item.statusName }}</el-tag>
            <el-tag v-if="item.originatingShiftId !== shift.id" size="small" type="danger">
              来自 {{ item.originatingShiftNo }}
            </el-tag>
          </div>
          <div class="item__ops">
            <el-button
              v-if="canSupplement(item)"
              size="small"
              @click="openSupplement(item)"
            >
              补充说明
            </el-button>
            <el-button
              v-if="canManage && item.open && shift.open && isLatest"
              size="small"
              @click="openDispatch(item)"
            >
              优先级 / 分派
            </el-button>
            <el-button
              v-if="canAccept(item)"
              size="small"
              type="primary"
              :loading="acceptingId === item.id"
              @click="accept(item)"
            >
              逐项确认承接
            </el-button>
          </div>
        </div>

        <div class="item__body">
          <div class="item__name">{{ item.title }}</div>
          <div v-if="item.detail" class="item__detail">{{ item.detail }}</div>
          <div class="item__meta">
            <span v-if="item.refLabel">关联：{{ item.refLabel }}</span>
            <span>负责教练：{{ item.ownerCoachName ? item.ownerCoachNo + ' ' + item.ownerCoachName : '未分派' }}</span>
            <span>所属班次：{{ item.originatingShiftNo }}</span>
            <span>当前所在：{{ item.currentShiftNo }}</span>
          </div>
          <div v-if="item.delayNote" class="item__delay">
            <el-tag type="warning" size="small">延期原因</el-tag>{{ item.delayNote }}
          </div>
          <div v-if="item.acceptance" class="item__accept">
            <el-tag type="success" size="small" effect="dark">最近承接</el-tag>
            {{ formatTime(item.acceptance.acceptedAt) }} 由 {{ item.acceptance.acceptorName }}
            在 {{ item.acceptance.shiftNo }} 确认承接<template v-if="item.acceptance.note">：{{ item.acceptance.note }}</template>
          </div>
          <div v-if="item.notes.length" class="item__notes">
            <div v-for="note in item.notes" :key="note.id" class="note">
              <span class="note__at">{{ formatTime(note.createdAt) }}</span>
              <el-tag size="small" :type="note.role === 'MANAGER' ? 'warning' : 'info'">
                {{ note.roleName }}{{ note.coachName ? '·' + note.coachName : '' }}
              </el-tag>
              <span class="note__by">{{ note.operator }}</span>
              <span class="note__text">{{ note.note }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 开班 -->
    <el-dialog v-model="createDialog" :title="createForm.id ? '建立下一班' : '建立值班班次'" width="480px">
      <el-form label-width="92px">
        <el-form-item label="值班日期">
          <el-date-picker v-model="createForm.businessDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="班次时段">
          <el-radio-group v-model="createForm.period">
            <el-radio label="DAY">白班</el-radio>
            <el-radio label="NIGHT">夜班</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="交接总说明">
          <el-input v-model="createForm.handoverNote" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="开班后上一班未承接事项会自动滚动到本班清单，并同步当班受影响课程、休养马匹与维护栏位；新变更只进入最新班次。"
      />
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">建立班次</el-button>
      </template>
    </el-dialog>

    <!-- 编辑交接说明 -->
    <el-dialog v-model="noteDialog" title="班次交接总说明" width="480px">
      <el-input v-model="noteText" type="textarea" :rows="4" maxlength="1000" show-word-limit />
      <template #footer>
        <el-button @click="noteDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitNote">保存</el-button>
      </template>
    </el-dialog>

    <!-- 补充事项 -->
    <el-dialog v-model="addDialog" title="负责人手工补充交接事项" width="500px">
      <el-form label-width="92px">
        <el-form-item label="事项类型">
          <el-radio-group v-model="addForm.itemType">
            <el-radio label="LESSON">课程</el-radio>
            <el-radio label="HORSE">马匹</el-radio>
            <el-radio label="STALL">栏位</el-radio>
            <el-radio label="OTHER">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="refLabel">
          <el-select
            v-if="addForm.itemType === 'LESSON'"
            v-model="addForm.refId"
            filterable
            style="width: 100%"
            placeholder="选择课程场次"
          >
            <el-option
              v-for="s in sessions"
              :key="s.id"
              :label="`${s.sessionDate} ${s.startTime} ${s.lessonName}`"
              :value="s.id"
            />
          </el-select>
          <el-select v-else-if="addForm.itemType === 'HORSE'" v-model="addForm.refId" filterable style="width: 100%">
            <el-option
              v-for="h in horses"
              :key="h.id"
              :label="h.horseNo + ' ' + h.name + '（' + h.statusName + '）'"
              :value="h.id"
            />
          </el-select>
          <el-select v-else-if="addForm.itemType === 'STALL'" v-model="addForm.refId" filterable style="width: 100%">
            <el-option v-for="s in stalls" :key="s.id" :label="s.stallNo + ' ' + s.barnName" :value="s.id" />
          </el-select>
          <span v-else class="eq-muted">其他事项无需关联对象</span>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="addForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="addForm.detail" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="addForm.priority">
            <el-radio-button label="LOW">低</el-radio-button>
            <el-radio-button label="NORMAL">普通</el-radio-button>
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="URGENT">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分派给">
          <el-select v-model="addForm.ownerCoachId" clearable style="width: 100%" placeholder="暂不分派">
            <el-option v-for="c in coaches" :key="c.id" :label="c.coachNo + ' ' + c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdd">补充到清单</el-button>
      </template>
    </el-dialog>

    <!-- 优先级 / 分派 -->
    <el-dialog v-model="dispatchDialog" :title="'分派 ' + (dispatchItem?.itemNo || '')" width="440px">
      <el-form label-width="80px">
        <el-form-item label="优先级">
          <el-radio-group v-model="dispatchForm.priority">
            <el-radio-button label="LOW">低</el-radio-button>
            <el-radio-button label="NORMAL">普通</el-radio-button>
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="URGENT">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="负责教练">
          <el-select v-model="dispatchForm.ownerCoachId" clearable style="width: 100%" placeholder="清除分派=回到待处理">
            <el-option v-for="c in coaches" :key="c.id" :label="c.coachNo + ' ' + c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="eq-muted">分派后事项进入「已分派」，仍需下一班负责人逐项确认承接。</p>
      <template #footer>
        <el-button @click="dispatchDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDispatch">保存</el-button>
      </template>
    </el-dialog>

    <!-- 补充说明 -->
    <el-dialog v-model="supplementDialog" :title="'补充说明 · ' + (supplementItem?.itemNo || '')" width="460px">
      <el-input v-model="supplementText" type="textarea" :rows="4" maxlength="1000" show-word-limit />
      <p class="eq-muted">补充只追加历史，不覆盖任何已有内容。普通教练只能补充分派给自己的事项。</p>
      <template #footer>
        <el-button @click="supplementDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSupplement">提交补充</el-button>
      </template>
    </el-dialog>

    <!-- 结束班次 -->
    <el-dialog v-model="closeDialog" title="结束班次" width="640px">
      <template v-if="closeBlockers.length">
        <el-alert
          type="error"
          show-icon
          :closable="false"
          :title="`仍有 ${closeBlockers.length} 个事项未承接，不能直接结束班次，请逐项处理或记录延期原因`"
          style="margin-bottom: 10px"
        />
        <div class="blockers">
          <div v-for="(text, index) in closeBlockers" :key="index" class="blocker">
            <el-tag type="danger" size="small">未承接</el-tag>
            <span class="blocker__text">{{ text }}</span>
          </div>
        </div>
        <el-divider>延期交接（必须填写原因）</el-divider>
        <el-radio-group v-model="delayMode" style="margin-bottom: 10px">
          <el-radio label="global">班次统一延期原因</el-radio>
          <el-radio label="per">逐项填写延期原因</el-radio>
        </el-radio-group>
        <el-input
          v-if="delayMode === 'global'"
          v-model="globalDelay"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="例如：等次日兽医复查，统一延期至下一班跟进"
        />
        <div v-else class="perdelays">
          <div v-for="item in closeOpenItems" :key="item.id" class="perdelay">
            <span class="perdelay__label">{{ item.itemNo }} {{ item.title }}</span>
            <el-input v-model="perDelays[item.id]" maxlength="500" placeholder="该事项延期原因（必填）" />
          </div>
        </div>
      </template>
      <el-alert
        v-else
        type="success"
        show-icon
        :closable="false"
        title="全部事项均已承接，可以直接结束班次"
        style="margin-bottom: 10px"
      />
      <p class="eq-muted" style="margin-top: 10px">
        若下一班已建立，未承接事项会滚动到下一班首页；结束后本班只读，历史可追溯。
      </p>
      <template #footer>
        <el-button @click="closeDialog = false">取消</el-button>
        <el-button
          type="danger"
          :loading="saving"
          @click="submitClose"
        >
          {{ closeBlockers.length ? '记录延期原因并结束班次' : '结束班次' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { requestId } from '../api'
import { identity, withIdentity } from '../identity'
import { navigate } from '../router'

const coaches = ref([])
const sessions = ref([])
const horses = ref([])
const stalls = ref([])

const shift = ref(null)
const myOpenItems = ref([])
const latestShiftId = ref(null)

const statusFilter = ref('')
const typeFilter = ref('')
const mineOnly = ref(false)
const stale = ref(false)
const saving = ref(false)
const acceptingId = ref(null)

const createDialog = ref(false)
const noteDialog = ref(false)
const addDialog = ref(false)
const dispatchDialog = ref(false)
const supplementDialog = ref(false)
const closeDialog = ref(false)

const createForm = reactive({ id: null, businessDate: today(), period: 'DAY', handoverNote: '' })
const noteText = ref('')
const addForm = reactive(emptyAdd())
const dispatchItem = ref(null)
const dispatchForm = reactive({ priority: 'NORMAL', ownerCoachId: null })
const supplementItem = ref(null)
const supplementText = ref('')

const closeBlockers = ref([])
const closeOpenItems = ref([])
const delayMode = ref('global')
const globalDelay = ref('')
const perDelays = reactive({})

let timer = null

const isManager = computed(() => identity.role === 'MANAGER' && !!identity.name)
const isLatest = computed(() => shift.value && latestShiftId.value === shift.value.id)
const canManage = computed(() => isManager.value && shift.value
  && identity.name === shift.value.managerName)

const visibleItems = computed(() => {
  return (shift.value?.items || []).filter((item) => {
    if (statusFilter.value && item.status !== statusFilter.value) {
      return false
    }
    if (typeFilter.value && item.itemType !== typeFilter.value) {
      return false
    }
    if (mineOnly.value && item.ownerCoachId !== identity.coachId) {
      return false
    }
    return true
  })
})

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatTime(text) {
  return text ? String(text).replace('T', ' ').slice(0, 16) : '—'
}

function emptyAdd() {
  return { itemType: 'OTHER', refId: null, title: '', detail: '', priority: 'NORMAL', ownerCoachId: null }
}

const refLabel = computed(() => {
  const map = { LESSON: '关联场次', HORSE: '关联马匹', STALL: '关联栏位' }
  return map[addForm.itemType] || '关联对象'
})

function typeTag(type) {
  return { LESSON: 'primary', HORSE: 'danger', STALL: 'warning', OTHER: 'info' }[type] || 'info'
}

function priorityTag(priority) {
  return { URGENT: 'danger', HIGH: 'warning', NORMAL: 'info', LOW: '' }[priority] || ''
}

function statusTag(status) {
  return { PENDING: 'danger', DISPATCHED: 'warning', ACCEPTED: 'success' }[status] || 'info'
}

function itemClass(item) {
  return {
    'item--open': item.open,
    'item--accepted': item.status === 'ACCEPTED',
    'item--urgent': item.priority === 'URGENT'
  }
}

function canSupplement(item) {
  if (!item.open || !shift.value.open) {
    return false
  }
  if (isManager.value && identity.name === shift.value.managerName) {
    return true
  }
  return identity.role === 'STAFF' && identity.coachId && item.ownerCoachId === identity.coachId
}

function canAccept(item) {
  return canManage.value && item.open && shift.value.open && isLatest.value
}

async function loadDictionaries() {
  const [coachList, sessionList, horseList, stallList] = await Promise.all([
    api.coaches(),
    api.sessions(),
    api.horses(),
    api.stalls()
  ])
  coaches.value = coachList || []
  sessions.value = (sessionList || []).filter((s) => s.status !== 'CANCELED')
  horses.value = horseList || []
  stalls.value = stallList || []
}

async function reload() {
  await reloadInternal(true)
}

/**
 * @param {boolean} followLatest 手动刷新 / 初次进入时跟随最新班次；
 *   后台轮询只更新正在查看的班次与个人待办，避免正在查看历史班次时被自动切走。
 */
async function reloadInternal(followLatest) {
  try {
    const board = await api.dutyBoard(identity.role === 'STAFF' && identity.coachId ? identity.coachId : undefined)
    latestShiftId.value = board.current ? board.current.id : null
    myOpenItems.value = board.myOpenItems || []
    if (!shift.value || (followLatest && board.current)) {
      shift.value = board.current
    } else {
      // 保留当前查看对象（可能是历史班次），拉它自己的最新版本
      shift.value = await api.dutyShift(shift.value.id)
    }
    stale.value = false
  } catch (error) {
    ElMessage.error(error.message)
  }
}

/** 后台轮询：不打断正在查看的班次，只静默刷新数据版本 */
function backgroundRefresh() {
  reloadInternal(false)
}

async function loadShift(id) {
  try {
    shift.value = await api.dutyShift(id)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function goHistory() {
  navigate('/duty-history')
}

async function goLatest() {
  if (!latestShiftId.value) {
    await reload()
    return
  }
  await loadShift(latestShiftId.value)
}

function openCreate() {
  Object.assign(createForm, {
    id: null,
    businessDate: today(),
    period: 'DAY',
    handoverNote: ''
  })
  createDialog.value = true
}

function openCreateNext() {
  Object.assign(createForm, {
    id: shift.value.id,
    businessDate: shift.value.businessDate,
    period: shift.value.period === 'DAY' ? 'NIGHT' : 'DAY',
    handoverNote: ''
  })
  createDialog.value = true
}

async function submitCreate() {
  if (!identity.name) {
    ElMessage.warning('请先填写负责人姓名')
    return
  }
  saving.value = true
  try {
    const body = withIdentity({
      businessDate: createForm.businessDate,
      period: createForm.period,
      handoverNote: createForm.handoverNote,
      requestId: requestId('shift-open')
    })
    const created = await api.openDutyShift(body)
    createDialog.value = false
    ElMessage.success(
      createForm.id
        ? '下一班已建立，上一班未承接事项已滚动到本班'
        : '值班班次已建立，交接清单已生成'
    )
    shift.value = created
    await reload()
  } catch (error) {
    await handleError(error)
  } finally {
    saving.value = false
  }
}

function openNote() {
  noteText.value = shift.value.handoverNote || ''
  noteDialog.value = true
}

async function submitNote() {
  saving.value = true
  try {
    await api.updateHandoverNote(
      shift.value.id,
      withIdentity({ handoverNote: noteText.value, expectedVersion: shift.value.version })
    )
    noteDialog.value = false
    ElMessage.success('交接说明已更新')
    await reload()
  } catch (error) {
    await handleError(error)
  } finally {
    saving.value = false
  }
}

async function sync() {
  saving.value = true
  try {
    const view = await api.syncDutyShift(shift.value.id, withIdentity({}))
    ElMessage.success('已同步当班课程、马匹与栏位变更')
    shift.value = view
  } catch (error) {
    await handleError(error)
  } finally {
    saving.value = false
  }
}

function openAddItem() {
  Object.assign(addForm, emptyAdd())
  addDialog.value = true
}

async function submitAdd() {
  if (!addForm.title.trim()) {
    ElMessage.warning('请填写事项标题')
    return
  }
  saving.value = true
  try {
    shift.value = await api.createDutyItem(
      shift.value.id,
      withIdentity({
        itemType: addForm.itemType,
        refId: addForm.itemType === 'OTHER' ? null : addForm.refId,
        title: addForm.title,
        detail: addForm.detail,
        priority: addForm.priority,
        ownerCoachId: addForm.ownerCoachId,
        requestId: requestId('item-add')
      })
    )
    addDialog.value = false
    ElMessage.success('事项已补充到本班清单')
  } catch (error) {
    await handleError(error)
  } finally {
    saving.value = false
  }
}

function openDispatch(item) {
  dispatchItem.value = item
  dispatchForm.priority = item.priority
  dispatchForm.ownerCoachId = item.ownerCoachId
  dispatchDialog.value = true
}

async function submitDispatch() {
  saving.value = true
  try {
    const view = await api.dispatchDutyItem(
      dispatchItem.value.id,
      withIdentity({
        priority: dispatchForm.priority,
        ownerCoachId: dispatchForm.ownerCoachId ?? -1,
        expectedVersion: dispatchItem.value.version
      })
    )
    dispatchDialog.value = false
    ElMessage.success(view.status === 'DISPATCHED' ? '已分派，等待下一班逐项确认' : '已清除分派，回到待处理')
    await reload()
  } catch (error) {
    await handleError(error)
  } finally {
    saving.value = false
  }
}

function openSupplement(item) {
  supplementItem.value = item
  supplementText.value = ''
  supplementDialog.value = true
}

async function submitSupplement() {
  if (!supplementText.value.trim()) {
    ElMessage.warning('请填写补充说明')
    return
  }
  saving.value = true
  try {
    await api.supplementDutyItem(
      supplementItem.value.id,
      withIdentity({ note: supplementText.value, requestId: requestId('item-note') })
    )
    supplementDialog.value = false
    ElMessage.success('补充说明已追加')
    await reload()
  } catch (error) {
    await handleError(error)
  } finally {
    saving.value = false
  }
}

async function accept(item) {
  let note = ''
  try {
    const result = await ElMessageBox.prompt(`确认承接「${item.itemNo} ${item.title}」？承接为终态，不可改回未处理。`,
      '逐项确认承接', {
        confirmButtonText: '确认承接',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '承接备注（可空）'
      })
    note = result.value || ''
  } catch (error) {
    return
  }
  acceptingId.value = item.id
  try {
    await api.acceptDutyItem(
      item.id,
      withIdentity({ note, expectedVersion: item.version, requestId: requestId('item-accept') })
    )
    ElMessage.success('事项已承接')
    await reload()
  } catch (error) {
    await handleError(error)
  } finally {
    acceptingId.value = null
  }
}

async function openClose() {
  closeBlockers.value = []
  closeOpenItems.value = []
  globalDelay.value = ''
  Object.keys(perDelays).forEach((key) => delete perDelays[key])
  delayMode.value = 'global'
  try {
    // 预检：逐项提示未结事项
    const view = await api.dutyPrecheck(shift.value.id, withIdentity({}))
    closeBlockers.value = view.closingBlockers || []
    closeOpenItems.value = (view.items || []).filter((item) => item.open)
    closeDialog.value = true
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function submitClose() {
  if (closeBlockers.value.length) {
    let body
    if (delayMode.value === 'global') {
      if (!globalDelay.value.trim()) {
        ElMessage.warning('请填写班次统一延期原因')
        return
      }
      body = { confirmDefer: true, delayReason: globalDelay.value, expectedVersion: shift.value.version }
    } else {
      const itemDelays = {}
      for (const item of closeOpenItems.value) {
        const reason = (perDelays[item.id] || '').trim()
        if (!reason) {
          ElMessage.warning(`事项 ${item.itemNo} 缺少逐项延期原因`)
          return
        }
        itemDelays[item.id] = reason
      }
      body = { confirmDefer: true, itemDelays, expectedVersion: shift.value.version }
    }
    saving.value = true
    try {
      shift.value = await api.closeDutyShift(shift.value.id, withIdentity(body))
      closeDialog.value = false
      ElMessage.success('班次已结束，未承接事项已记录延期并滚动到下一班')
      await reload()
    } catch (error) {
      await handleError(error, true)
    } finally {
      saving.value = false
    }
  } else {
    saving.value = true
    try {
      shift.value = await api.closeDutyShift(
        shift.value.id,
        withIdentity({ confirmDefer: false, expectedVersion: shift.value.version })
      )
      closeDialog.value = false
      ElMessage.success('班次已结束，全部事项均已承接')
      await reload()
    } catch (error) {
      await handleError(error)
    } finally {
      saving.value = false
    }
  }
}

/**
 * 409：旧页面提交过期内容。不重试旧数据，自动重新载入最新清单并显式提示；
 * 关班弹窗同时关闭，避免用户继续对着过期 blockers 操作。
 */
async function handleError(error, closeDialogOnConflict = false) {
  if (error.conflict) {
    stale.value = true
    ElMessage.error(error.message)
    if (closeDialogOnConflict) {
      closeDialog.value = false
    }
    await reload()
  } else {
    ElMessage.error(error.message)
  }
}

onMounted(async () => {
  await loadDictionaries()
  await reload()
  timer = window.setInterval(backgroundRefresh, 20000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.duty {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.identity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.identity__who {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.head__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head__title h2 {
  margin: 0;
  font-size: 17px;
  color: #4a3129;
}

.head__meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #8c7c76;
  margin: 8px 0;
}

.head__note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: #5d4d47;
  background: #fbf6f3;
  border: 1px dashed #e3d3cb;
  border-radius: 8px;
  padding: 8px 10px;
}

.head__note-text {
  flex: 1;
}

.head__delay {
  margin-top: 8px;
  font-size: 12px;
  color: #9a6a3f;
}

.head__stats {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.stat {
  width: 78px;
  border-radius: 10px;
  background: #f7f2ef;
  padding: 10px 6px;
  text-align: center;
}

.stat b {
  display: block;
  font-size: 20px;
  color: #4a3129;
}

.stat span {
  font-size: 11px;
  color: #9a8b85;
}

.stat--open b {
  color: #c0563f;
}

.stat--ok b {
  color: #4f8a5b;
}

.stat--inherit {
  background: #fdeeea;
}

.stat--inherit b {
  color: #c0563f;
}

.opsbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.item {
  padding: 12px 14px;
}

.item--open {
  border-left: 4px solid #c0563f;
}

.item--urgent {
  box-shadow: 0 0 0 1px #e9c7bd;
}

.item--accepted {
  opacity: 0.82;
  border-left: 4px solid #7ba987;
}

.item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.item__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item__no {
  font-weight: 700;
  color: #4a3129;
}

.item__ops {
  display: flex;
  gap: 6px;
}

.item__name {
  font-size: 14px;
  font-weight: 600;
  color: #4a3129;
  margin: 8px 0 4px;
}

.item__detail {
  font-size: 13px;
  color: #5d4d47;
}

.item__meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 6px;
  font-size: 12px;
  color: #9a8b85;
}

.item__delay,
.item__accept {
  margin-top: 8px;
  font-size: 12px;
  color: #7a5a44;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.item__notes {
  margin-top: 8px;
  border-top: 1px dashed #ece3df;
  padding-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.note {
  font-size: 12px;
  display: flex;
  gap: 8px;
  align-items: baseline;
  flex-wrap: wrap;
}

.note__at {
  color: #a89a94;
}

.note__by {
  color: #7a6a64;
}

.note__text {
  color: #4a3129;
}

.blockers {
  max-height: 260px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.blocker {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
}

.blocker__text {
  color: #5d4d47;
}

.perdelays {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.perdelay__label {
  display: block;
  font-size: 12px;
  color: #7a6a64;
  margin-bottom: 3px;
}
</style>
