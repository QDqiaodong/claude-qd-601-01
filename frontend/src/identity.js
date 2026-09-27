import { reactive, watch } from 'vue'

/**
 * 当前值班身份（前端仅做展示与按钮体验，真正的角色校验一律在后端）。
 * 没有登录体系，处置台 / 交接台 / 马匹页统一从这里取「操作人 + 角色 + 教练」，
 * 并持久化到 localStorage。普通教练补充事项时必须带 coachId，后端据此校验归属。
 */
const STORAGE_KEY = 'eq-operator'

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        name: typeof parsed.name === 'string' ? parsed.name : '',
        role: parsed.role === 'MANAGER' ? 'MANAGER' : 'STAFF',
        coachId: typeof parsed.coachId === 'number' ? parsed.coachId : null
      }
    }
  } catch (error) {
    // 本地存储不可用时退回默认身份
  }
  return { name: '', role: 'STAFF', coachId: null }
}

export const identity = reactive(load())

watch(
  identity,
  (value) => {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ name: value.name, role: value.role, coachId: value.coachId })
      )
    } catch (error) {
      // 忽略写入失败
    }
  },
  { deep: true }
)

/** 给写接口统一附带身份字段 */
export function withIdentity(body = {}) {
  return {
    operatorName: identity.name,
    operatorRole: identity.role,
    coachId: identity.role === 'STAFF' ? identity.coachId : null,
    ...body
  }
}
