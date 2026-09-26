<template>
  <div>
    <div class="eq-panel">
      <div class="eq-panel__head">
        <h2>马房栏位分布</h2>
        <el-button type="primary" @click="stallDialog = true">新增栏位</el-button>
      </div>

      <div class="summary">
        <div class="summary__item">
          <span class="summary__num">{{ stalls.length }}</span>
          <span class="summary__label">栏位总数</span>
        </div>
        <div class="summary__item summary__item--idle">
          <span class="summary__num">{{ countOf('IDLE') }}</span>
          <span class="summary__label">空闲</span>
        </div>
        <div class="summary__item summary__item--occupied">
          <span class="summary__num">{{ countOf('OCCUPIED') }}</span>
          <span class="summary__label">占用</span>
        </div>
        <div class="summary__item summary__item--maintenance">
          <span class="summary__num">{{ countOf('MAINTENANCE') }}</span>
          <span class="summary__label">维护中</span>
        </div>
        <div class="summary__tip">
          规则：栏位编号唯一 · 一个栏位只放一匹马 · 一匹马只占一个栏位 · 维护中禁止入栏
        </div>
      </div>
    </div>

    <div v-for="barn in barns" :key="barn" class="eq-panel barn">
      <div class="eq-panel__head">
        <h2>{{ barn }}</h2>
        <span>{{ byBarn(barn).length }} 个栏位 · 已用 {{ byBarn(barn).filter((s) => s.horseId).length }} 个</span>
      </div>

      <div class="stalls">
        <div
          v-for="stall in byBarn(barn)"
          :key="stall.id"
          class="stall"
          :class="'stall--' + stall.status.toLowerCase()"
        >
          <div class="stall__head">
            <span class="stall__no">{{ stall.stallNo }}</span>
            <el-tag size="small" :type="tagOf(stall.status)" effect="dark">{{ stall.statusName }}</el-tag>
          </div>
          <div class="stall__area">{{ stall.areaSqm === null ? '面积未登记' : stall.areaSqm + ' ㎡' }}</div>
          <div class="stall__horse">
            <template v-if="stall.horseId">
              <b>{{ stall.horseName }}</b>
              <span>{{ stall.horseNo }}</span>
            </template>
            <span v-else class="eq-muted">空栏</span>
          </div>
          <div class="stall__ops">
            <el-button v-if="stall.status === 'IDLE'" size="small" type="primary" @click="openOccupy(stall)">
              入栏
            </el-button>
            <el-button v-if="stall.status === 'IDLE'" size="small" @click="toMaintenance(stall)">
              转维护
            </el-button>
            <el-button v-if="stall.status === 'OCCUPIED'" size="small" type="primary" @click="release(stall)">
              出栏
            </el-button>
            <el-button v-if="stall.status === 'MAINTENANCE'" size="small" type="success" @click="restore(stall)">
              维护完成
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="stallDialog" title="新增栏位" width="440px">
      <el-form label-width="88px">
        <el-form-item label="栏位编号">
          <el-input v-model="stallForm.stallNo" placeholder="如 STM-010" />
        </el-form-item>
        <el-form-item label="所属马房">
          <el-input v-model="stallForm.barnName" placeholder="如 A 区马房" />
        </el-form-item>
        <el-form-item label="面积 ㎡">
          <el-input-number v-model="stallForm.areaSqm" :min="0" :max="500" :precision="1" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stallDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitStall">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="occupyDialog" title="安排马匹入栏" width="440px">
      <p class="eq-muted">
        目标栏位：<b>{{ occupyForm.stallNo }}</b>（{{ occupyForm.barnName }}）
      </p>
      <el-select v-model="occupyForm.horseId" placeholder="选择要入栏的马匹" style="width: 100%">
        <el-option
          v-for="horse in horses"
          :key="horse.id"
          :label="horse.horseNo + ' ' + horse.name + '（' + horse.statusName + '）'"
          :value="horse.id"
        />
      </el-select>
      <p class="eq-muted" style="margin-top: 10px">
        已在别的栏位的马匹、维护中的栏位都会在提交时被后端拒绝并返回中文原因。
      </p>
      <template #footer>
        <el-button @click="occupyDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitOccupy">确认入栏</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const stalls = ref([])
const horses = ref([])
const saving = ref(false)
const stallDialog = ref(false)
const occupyDialog = ref(false)

const stallForm = reactive({ stallNo: '', barnName: '', areaSqm: null })
const occupyForm = reactive({ id: null, stallNo: '', barnName: '', horseId: null })

const barns = computed(() => [...new Set(stalls.value.map((stall) => stall.barnName))])

function byBarn(barn) {
  return stalls.value.filter((stall) => stall.barnName === barn)
}

function countOf(status) {
  return stalls.value.filter((stall) => stall.status === status).length
}

function tagOf(status) {
  if (status === 'IDLE') return 'success'
  if (status === 'OCCUPIED') return 'primary'
  return 'warning'
}

async function load() {
  try {
    const [stallList, horseList] = await Promise.all([api.stalls(), api.horses()])
    stalls.value = stallList || []
    horses.value = horseList || []
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function submitStall() {
  saving.value = true
  try {
    await api.createStall({
      stallNo: stallForm.stallNo,
      barnName: stallForm.barnName,
      areaSqm: stallForm.areaSqm
    })
    ElMessage.success('栏位已创建')
    stallDialog.value = false
    Object.assign(stallForm, { stallNo: '', barnName: '', areaSqm: null })
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function openOccupy(stall) {
  occupyForm.id = stall.id
  occupyForm.stallNo = stall.stallNo
  occupyForm.barnName = stall.barnName
  occupyForm.horseId = null
  occupyDialog.value = true
}

async function submitOccupy() {
  saving.value = true
  try {
    await api.occupyStall(occupyForm.id, occupyForm.horseId)
    ElMessage.success('入栏成功')
    occupyDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function release(stall) {
  try {
    await api.releaseStall(stall.id)
    ElMessage.success(`栏位 ${stall.stallNo} 已出栏`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function toMaintenance(stall) {
  try {
    await api.maintenanceStall(stall.id)
    ElMessage.success(`栏位 ${stall.stallNo} 已转入维护`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function restore(stall) {
  try {
    await api.restoreStall(stall.id)
    ElMessage.success(`栏位 ${stall.stallNo} 维护完成`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<style scoped>
.summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.summary__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 84px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f7f2ef;
  border: 1px solid #ece1db;
}

.summary__item--idle {
  background: #f0f7f1;
  border-color: #d6e8d9;
}

.summary__item--occupied {
  background: #f3e9e4;
  border-color: #e0cec5;
}

.summary__item--maintenance {
  background: #fdf4e7;
  border-color: #f0e0c6;
}

.summary__num {
  font-size: 21px;
  font-weight: 700;
  color: #4a3129;
}

.summary__label {
  font-size: 11px;
  color: #8c7c76;
}

.summary__tip {
  flex: 1;
  min-width: 220px;
  font-size: 12px;
  color: #9a8b85;
  line-height: 1.7;
}

.barn {
  margin-top: 16px;
}

.stalls {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}

.stall {
  border-radius: 10px;
  border: 1px solid #ece3df;
  padding: 12px;
  background: #fffdfc;
}

.stall--occupied {
  background: #fdf7f4;
  border-color: #ddc9bf;
}

.stall--maintenance {
  background: repeating-linear-gradient(45deg, #fdf8f1, #fdf8f1 8px, #f8f0e3 8px, #f8f0e3 16px);
  border-color: #e8d8be;
}

.stall__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stall__no {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #4a3129;
}

.stall__area {
  font-size: 11px;
  color: #a08d86;
  margin-top: 3px;
}

.stall__horse {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin: 9px 0;
  min-height: 22px;
  font-size: 13px;
  color: #4a3129;
}

.stall__horse span {
  font-size: 11px;
  color: #a08d86;
}

.stall__ops {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  border-top: 1px dashed #efe6e1;
  padding-top: 8px;
}
</style>
