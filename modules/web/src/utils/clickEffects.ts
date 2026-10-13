import type { AppThemeType } from '@/store/themeStore'

/**
 * 全局交互点击/触控粒子与涟漪动效引擎
 * 为 5 款主题分别定制专属微物理动效（赛博脉冲、星光星爆、柔光波纹、落樱花瓣、经典涟漪）
 */

let isInitialized = false
let container: HTMLElement | null = null

function getEffectContainer(): HTMLElement {
  if (container && document.body.contains(container)) {
    return container
  }
  let c = document.getElementById('global-click-fx-container')
  if (!c) {
    c = document.createElement('div')
    c.id = 'global-click-fx-container'
    c.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:999999;overflow:hidden;'
    document.body.appendChild(c)
  }
  container = c
  return container
}

export function triggerThemeClickEffect(x: number, y: number, theme: AppThemeType = 'classic') {
  if (typeof document === 'undefined') return
  const parent = getEffectContainer()

  switch (theme) {
    case 'obsidian':
      createCyberPulseEffect(x, y, parent)
      break
    case 'aurora':
      createAuroraSparkleEffect(x, y, parent)
      break
    case 'azure':
      createAzureWaveEffect(x, y, parent)
      break
    case 'sakura':
      createSakuraPetalEffect(x, y, parent)
      break
    case 'classic':
    default:
      createClassicRippleEffect(x, y, parent)
      break
  }
}

// 1. 赛博数码脉冲 (Obsidian)
function createCyberPulseEffect(x: number, y: number, parent: HTMLElement) {
  const ring = document.createElement('div')
  ring.className = 'fx-cyber-ring'
  ring.style.cssText = `
    position: absolute;
    left: ${x}px;
    top: ${y}px;
    width: 10px;
    height: 10px;
    margin-left: -5px;
    margin-top: -5px;
    border-radius: 50%;
    border: 2px solid #06b6d4;
    box-shadow: 0 0 12px #7c3aed, inset 0 0 8px #06b6d4;
    pointer-events: none;
    animation: fx-cyber-expand 0.5s cubic-bezier(0.2, 0.9, 0.3, 1) forwards;
  `
  parent.appendChild(ring)

  // 4 个十字准星数码光粒
  const colors = ['#06b6d4', '#7c3aed', '#a855f7', '#38bdf8']
  for (let i = 0; i < 4; i++) {
    const bit = document.createElement('div')
    const angle = (Math.PI / 2) * i
    const dist = 32 + Math.random() * 12
    const tx = Math.cos(angle) * dist
    const ty = Math.sin(angle) * dist

    bit.style.cssText = `
      position: absolute;
      left: ${x}px;
      top: ${y}px;
      width: 4px;
      height: 4px;
      background: ${colors[i % colors.length]};
      box-shadow: 0 0 6px ${colors[i % colors.length]};
      pointer-events: none;
      transform: translate(-50%, -50%);
      transition: transform 0.45s cubic-bezier(0.1, 0.9, 0.2, 1), opacity 0.45s ease;
      opacity: 1;
    `
    parent.appendChild(bit)

    requestAnimationFrame(() => {
      bit.style.transform = `translate(${tx}px, ${ty}px) scale(0.4)`
      bit.style.opacity = '0'
    })
    setTimeout(() => bit.remove(), 500)
  }

  setTimeout(() => ring.remove(), 550)
}

// 2. 星尘极光星爆 (Aurora)
function createAuroraSparkleEffect(x: number, y: number, parent: HTMLElement) {
  const glow = document.createElement('div')
  glow.style.cssText = `
    position: absolute;
    left: ${x}px;
    top: ${y}px;
    width: 20px;
    height: 20px;
    margin-left: -10px;
    margin-top: -10px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(236, 72, 153, 0.8) 0%, rgba(99, 102, 241, 0.4) 50%, transparent 100%);
    box-shadow: 0 0 20px rgba(56, 189, 248, 0.6);
    pointer-events: none;
    animation: fx-aurora-glow 0.6s cubic-bezier(0.2, 0.8, 0.3, 1) forwards;
  `
  parent.appendChild(glow)

  // 10 颗放射彩色星芒微粒
  const starColors = ['#f43f5e', '#38bdf8', '#a855f7', '#fbbf24', '#34d399', '#ec4899']
  for (let i = 0; i < 10; i++) {
    const star = document.createElement('div')
    const angle = (Math.PI * 2 * i) / 10 + (Math.random() - 0.5) * 0.4
    const dist = 28 + Math.random() * 32
    const size = 3 + Math.random() * 3
    const tx = Math.cos(angle) * dist
    const ty = Math.sin(angle) * dist
    const color = starColors[i % starColors.length]

    star.style.cssText = `
      position: absolute;
      left: ${x}px;
      top: ${y}px;
      width: ${size}px;
      height: ${size}px;
      border-radius: 50%;
      background: ${color};
      box-shadow: 0 0 8px ${color};
      transform: translate(-50%, -50%);
      pointer-events: none;
      transition: transform 0.55s cubic-bezier(0.12, 0.9, 0.25, 1), opacity 0.55s ease;
      opacity: 1;
    `
    parent.appendChild(star)

    requestAnimationFrame(() => {
      star.style.transform = `translate(${tx}px, ${ty}px) scale(0.3)`
      star.style.opacity = '0'
    })
    setTimeout(() => star.remove(), 600)
  }

  setTimeout(() => glow.remove(), 650)
}

// 3. 弥散柔光波纹 (Azure)
function createAzureWaveEffect(x: number, y: number, parent: HTMLElement) {
  const wave = document.createElement('div')
  wave.style.cssText = `
    position: absolute;
    left: ${x}px;
    top: ${y}px;
    width: 14px;
    height: 14px;
    margin-left: -7px;
    margin-top: -7px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(2, 132, 199, 0.45) 0%, rgba(56, 189, 248, 0.25) 50%, transparent 80%);
    box-shadow: 0 0 16px rgba(2, 132, 199, 0.35);
    pointer-events: none;
    animation: fx-azure-expand 0.55s cubic-bezier(0.15, 0.8, 0.25, 1) forwards;
  `
  parent.appendChild(wave)
  setTimeout(() => wave.remove(), 600)
}

// 4. 樱花花瓣散落 (Sakura)
function createSakuraPetalEffect(x: number, y: number, parent: HTMLElement) {
  const ripple = document.createElement('div')
  ripple.style.cssText = `
    position: absolute;
    left: ${x}px;
    top: ${y}px;
    width: 14px;
    height: 14px;
    margin-left: -7px;
    margin-top: -7px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(251, 113, 133, 0.5) 0%, rgba(244, 63, 94, 0.15) 70%, transparent 100%);
    pointer-events: none;
    animation: fx-sakura-ripple 0.55s ease-out forwards;
  `
  parent.appendChild(ripple)

  // 5 片微旋的花瓣
  for (let i = 0; i < 5; i++) {
    const petal = document.createElement('div')
    const angle = (Math.PI * 2 * i) / 5 + (Math.random() - 0.5) * 0.4
    const dist = 22 + Math.random() * 26
    const tx = Math.cos(angle) * dist
    const ty = Math.sin(angle) * dist + (8 + Math.random() * 10)
    const rot = Math.random() * 360

    petal.style.cssText = `
      position: absolute;
      left: ${x}px;
      top: ${y}px;
      width: 9px;
      height: 6px;
      border-radius: 80% 0 80% 0;
      background: linear-gradient(135deg, #fbcfe8 0%, #f43f5e 100%);
      box-shadow: 0 0 6px rgba(244, 63, 94, 0.3);
      transform: translate(-50%, -50%) rotate(${rot}deg);
      pointer-events: none;
      transition: transform 0.65s cubic-bezier(0.1, 0.7, 0.25, 1), opacity 0.65s ease;
      opacity: 0.95;
    `
    parent.appendChild(petal)

    requestAnimationFrame(() => {
      petal.style.transform = `translate(${tx}px, ${ty}px) rotate(${rot + 120}deg) scale(0.6)`
      petal.style.opacity = '0'
    })
    setTimeout(() => petal.remove(), 700)
  }

  setTimeout(() => ripple.remove(), 600)
}

// 5. 经典水波微涟漪 (Classic)
function createClassicRippleEffect(x: number, y: number, parent: HTMLElement) {
  const rip = document.createElement('div')
  rip.style.cssText = `
    position: absolute;
    left: ${x}px;
    top: ${y}px;
    width: 10px;
    height: 10px;
    margin-left: -5px;
    margin-top: -5px;
    border-radius: 50%;
    background: rgba(0, 150, 136, 0.35);
    pointer-events: none;
    animation: fx-classic-ripple 0.45s ease-out forwards;
  `
  parent.appendChild(rip)
  setTimeout(() => rip.remove(), 500)
}

/**
 * 挂载全局点击交互监听器
 */
export function setupGlobalClickEffects(getCurrentTheme: () => AppThemeType, getIsEnabled: () => boolean) {
  if (isInitialized || typeof window === 'undefined') return
  isInitialized = true

  const handlePointerDown = (e: PointerEvent) => {
    if (!getIsEnabled()) return
    // 忽略右键
    if (e.button !== 0 && e.button !== undefined) return
    triggerThemeClickEffect(e.clientX, e.clientY, getCurrentTheme())
  }

  window.addEventListener('pointerdown', handlePointerDown, { passive: true, capture: true })
}
