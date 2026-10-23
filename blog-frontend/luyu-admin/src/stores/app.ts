import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import { theme as antdTheme } from 'ant-design-vue/es'
import type { ContentWidth, LayoutType, ThemeType } from '~@/layouts/basic-layout/typing'
import type { AnimationNameValueType } from '~@/config/default-setting'
import defaultSetting from '~@/config/default-setting'

export interface LayoutSetting {
  title?: string
  logo?: string
  theme: ThemeType
  collapsed: boolean
  drawerVisible: boolean
  colorPrimary?: string
  layout?: LayoutType
  contentWidth?: ContentWidth
  fixedHeader?: boolean
  fixedSider?: boolean
  splitMenus?: boolean
  watermark?: boolean
  header?: boolean
  footer?: boolean
  menu?: boolean
  menuHeader?: boolean
  colorWeak?: boolean
  multiTab?: boolean
  multiTabFixed?: boolean
  headerHeight?: number
  copyright?: string
  keepAlive?: boolean
  accordionMode?: boolean
  leftCollapsed?: boolean
  animationName?: AnimationNameValueType
}

const ACCENT = defaultSetting.colorPrimary || '#c45c3e'
const ACCENT_SOFT = 'rgba(196, 92, 62, 0.12)'
const ACCENT_HOVER = 'rgba(196, 92, 62, 0.06)'

export const useAppStore = defineStore('app', () => {
  const layoutSetting = reactive<LayoutSetting>({ ...defaultSetting, theme: 'light' })
  const themeConfig = reactive<ThemeConfig>({
    algorithm: antdTheme.defaultAlgorithm,
    token: {
      colorBgBase: '#ffffff',
      colorBgLayout: '#f5f4f1',
      colorBgContainer: '#ffffff',
      colorBorder: '#d9d6d0',
      colorBorderSecondary: '#e8e6e1',
      colorText: '#1c2128',
      colorTextSecondary: '#5c6570',
      colorPrimary: layoutSetting.colorPrimary || ACCENT,
      borderRadius: 8,
      borderRadiusLG: 10,
      boxShadowSecondary: '0 4px 16px rgba(28, 33, 40, 0.08)',
    },
    components: {
      Layout: {
        colorBgBody: '#f5f4f1',
        colorBgHeader: '#ffffff',
        colorBgContainer: '#ffffff',
      },
      Menu: {
        colorItemBg: 'transparent',
        colorItemText: '#5c6570',
        colorItemBgHover: ACCENT_HOVER,
        colorItemTextHover: ACCENT,
        colorItemBgSelected: ACCENT_SOFT,
        colorItemTextSelected: ACCENT,
        colorSubItemBg: 'transparent',
      },
    },
  })
  const locale = ref<string>(lsLocaleState.value)
  const toggleLocale = (locale: string) => {
    lsLocaleState.value = locale
  }

  /** 管理后台仅支持浅色；dark 请求会被降级为 light */
  const toggleTheme = (theme: ThemeType) => {
    const next: ThemeType = theme === 'dark' ? 'light' : theme
    layoutSetting.theme = next
    if (themeConfig.token)
      themeConfig.token.colorBgContainer = '#fff'
    themeConfig.algorithm = antdTheme.defaultAlgorithm
    toggleDark(false)
  }

  const toggleDrawerVisible = (visible: boolean) => {
    layoutSetting.drawerVisible = visible
  }

  const toggleColorPrimary = (color: string) => {
    layoutSetting.colorPrimary = color
    if (themeConfig.token)
      themeConfig.token.colorPrimary = color
  }

  // 启动即钉死浅色，避免 vueuse-color-scheme=auto 跟随系统
  toggleDark(false)
  toggleTheme('light')

  watch(preferredLanguages, () => {
    toggleLocale(preferredLanguages.value[0])
  })

  const toggleCollapsed = (collapsed: boolean) => {
    layoutSetting.collapsed = collapsed
  }

  const toggleLayout = (layout: LayoutType) => {
    if (layoutSetting.theme === 'inverted' && layout === 'mix')
      layoutSetting.theme = 'light'

    if (layout !== 'mix')
      layoutSetting.splitMenus = false
    else layoutSetting.leftCollapsed = true

    if (layout === 'top')
      layoutSetting.contentWidth = 'Fixed'
    else layoutSetting.contentWidth = 'Fluid'

    layoutSetting.layout = layout
  }

  const changeSettingLayout = (key: keyof LayoutSetting, value: any) => {
    if (key === 'theme')
      toggleTheme(value as ThemeType)
    else if (key === 'colorPrimary')
      toggleColorPrimary(value)
    else if (key === 'layout')
      toggleLayout(value as LayoutType)
    else if (key in layoutSetting)
      (layoutSetting as any)[key] = value
  }

  return {
    layoutSetting,
    theme: themeConfig,
    locale,
    toggleLocale,
    toggleTheme,
    toggleCollapsed,
    toggleDrawerVisible,
    changeSettingLayout,
    toggleColorPrimary,
  }
})
