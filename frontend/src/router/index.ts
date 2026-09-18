import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/plan',
    name: 'Plan',
    component: () => import('../views/RoutePlan.vue')
  },
  {
    path: '/rules',
    name: 'Rules',
    component: () => import('../views/RiskRules.vue')
  },
  {
    path: '/medical',
    name: 'Medical',
    component: () => import('../views/MedicalSchedule.vue')
  },
  {
    path: '/release',
    name: 'Release',
    component: () => import('../views/ReleaseGate.vue')
  },
  {
    path: '/rollcall',
    name: 'RollCall',
    component: () => import('../views/ReturnRollCall.vue')
  },
  {
    path: '/report/:id',
    name: 'Report',
    component: () => import('../views/Report.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router