import type { LayoutSetting } from '~@/stores/app'

export default {
  'title': '郑陆宇 · 管理后台',
  'theme': 'light',
  'logo': '/blog-icon.svg',
  'collapsed': false,
  'drawerVisible': false,
  'colorPrimary': '#d35f45',
  'layout': 'side',
  'contentWidth': 'Fluid',
  'fixedHeader': true,
  'fixedSider': true,
  'splitMenus': false,
  'header': true,
  'menu': true,
  'watermark': false,
  'menuHeader': true,
  'footer': false,
  'colorWeak': false,
  'multiTab': true,
  'multiTabFixed': false,
  'keepAlive': true,
  'accordionMode': false,
  'leftCollapsed': true,
  'headerHeight': 44,
  'copyright': 'zhengluyu blog',
  'animationName': 'none',
} as LayoutSetting

export const animationNameList = [
  {
    label: 'None',
    value: 'none',
  },
  {
    label: 'Fadein Up',
    value: 'slide-fadein-up',
  },
  {
    label: 'Fadein Right',
    value: 'slide-fadein-right',
  },
  {
    label: 'Zoom Fadein',
    value: 'zoom-fadein',
  },
  {
    label: 'Fadein',
    value: 'fadein',
  },
]
export type AnimationNameValueType = 'none' | 'slide-fadein-up' | 'slide-fadein-right' | 'zoom-fadein' | 'fadein'
