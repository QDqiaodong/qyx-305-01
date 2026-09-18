<template>
  <div class="app-container">
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="header-icon"><Warning /></el-icon>
          <span class="header-title">研学路线出行风险多维度自动筛查预警系统</span>
        </div>
      </el-header>
      
      <el-container>
        <el-aside width="200px" class="app-aside">
          <el-menu 
            :default-active="activeMenu" 
            class="sidebar-menu"
            router
          >
            <el-menu-item index="/">
              <el-icon><HomeFilled /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/plan">
              <el-icon><MapLocation /></el-icon>
              <span>路线行程管理</span>
            </el-menu-item>
            <el-menu-item index="/rules">
              <el-icon><Setting /></el-icon>
              <span>风险规则管理</span>
            </el-menu-item>
            <el-menu-item index="/medical">
              <el-icon><User /></el-icon>
              <span>随队医护排班</span>
            </el-menu-item>
            <el-menu-item index="/release">
              <el-icon><Van /></el-icon>
              <span>发车放行</span>
            </el-menu-item>
            <el-menu-item index="/rollcall">
              <el-icon><Finished /></el-icon>
              <span>回程点名</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        
        <el-main class="app-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Warning, HomeFilled, MapLocation, Setting, User, Van, Finished } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => route.path)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.app-container {
  height: 100vh;
  width: 100%;
}

.app-header {
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 28px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.app-aside {
  background-color: #001529;
}

.sidebar-menu {
  height: 100%;
  border-right: none;
}

.app-main {
  background-color: #f5f5f5;
  padding: 20px;
}
</style>