<script setup lang="ts">
import { useLayoutState } from '../../basic-layout/context'
import SubMenu from './sub-menu.vue'
import { groupAdminMenus } from '~/utils/admin-menu-groups'

const { theme, collapsed, layout, isMobile, selectedMenus, selectedKeys, openKeys, handleOpenKeys, handleSelectedKeys, handleMenuSelect } = useLayoutState()
const menuTheme = computed(() => {
  if (theme.value === 'inverted')
    return 'dark'
  return theme.value
})
const displayGroups = computed(() => groupAdminMenus(selectedMenus.value ?? []))
</script>

<template>
  <a-menu
    :selected-keys="selectedKeys"
    :open-keys="collapsed ? [] : openKeys"
    :mode="(layout === 'top' && !isMobile) ? 'horizontal' : 'inline'"
    :theme="menuTheme"
    :collapsed="collapsed"
    class="ant-pro-sider-menu admin-nav-menu"
    @update:open-keys="handleOpenKeys"
    @update:selected-keys="handleSelectedKeys"
    @select="handleMenuSelect"
  >
    <template v-if="layout === 'top' && !isMobile">
      <template v-for="item in selectedMenus" :key="item.path">
        <SubMenu v-if="!item.hideInMenu" :item="item" />
      </template>
    </template>
    <template v-else>
      <a-menu-item-group v-for="group in displayGroups" :key="group.key" :title="collapsed ? '' : group.title">
        <template v-for="item in group.items" :key="item.path">
          <SubMenu v-if="!item.hideInMenu" :item="item" />
        </template>
      </a-menu-item-group>
    </template>
  </a-menu>
</template>
