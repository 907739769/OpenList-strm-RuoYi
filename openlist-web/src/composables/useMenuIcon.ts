import {
  Setting, Monitor, Document, Picture,
  Odometer, VideoCamera, Files, EditPen, Menu
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 后端菜单存的是 Font Awesome 图标类名，前端用的是 Element Plus 图标组件，
 * 这里做一层映射。未收录的图标返回 undefined，由调用方决定兜底展示。
 */
const iconMap: Record<string, Component> = {
  'fa fa-gear': Setting,
  'fa fa-cog': Setting,
  'fa fa-bookmark-o': Document,
  'fa fa-sun-o': Picture,
  'fa fa-video-camera': Monitor,
  'fa fa-tasks': Monitor,
  'fa fa-calendar': Odometer,
  'fa fa-picture-o': Picture,
  'fa fa-yen': Files,
  'fa fa-send-o': Files,
  'fa fa-diamond': EditPen,
  'fa fa-bars': Menu,
  'fa fa-list-ul': Menu,
  'fa fa-list': Menu,
  'fa fa-file-code-o': Document,
  'fa fa-folder-open-o': Files,
  'fa fa-play-circle-o': VideoCamera,
  'fa fa-video-play': VideoCamera,
  'fa fa-copy': Monitor,
  'fa fa-edit': EditPen,
  'fa fa-magic': EditPen,
}

export function getIconComponent(icon?: string): Component | undefined {
  if (!icon) return undefined
  return iconMap[icon]
}
