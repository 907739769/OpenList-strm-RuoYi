/**
 * PWA Service Worker Utilities
 * Handles service worker registration, updates, and install prompt
 */

let registration: ServiceWorkerRegistration | null = null
let promptEvent: BeforeInstallPromptEvent | null = null

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

/**
 * Register service worker and set up PWA listeners
 */
export function registerPWA(): void {
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker
        .register('/sw.js')
        .then((reg) => {
          registration = reg
          console.log('PWA Service Worker registered:', reg.scope)

          // Listen for updates
          reg.addEventListener('updatefound', () => {
            const newWorker = reg.installing
            if (newWorker) {
              newWorker.addEventListener('statechange', () => {
                if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                  console.log('New content available; please refresh.')
                  triggerUpdateNotification()
                }
              })
            }
          })
        })
        .catch((error) => {
          console.error('PWA Service Worker registration failed:', error)
        })
    })
  }

  // Capture beforeInstallPrompt event
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault()
    promptEvent = e as BeforeInstallPromptEvent
    console.log('PWA install prompt captured')
  })
}

/**
 * Show update notification to user
 */
export function triggerUpdateNotification(): void {
  if (typeof document !== 'undefined') {
    const banner = document.createElement('div')
    banner.id = 'pwa-update-banner'
    banner.style.cssText = `
      position: fixed;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #409EFF;
      color: white;
      padding: 12px 24px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      z-index: 9999;
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 14px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    `
    banner.innerHTML = `
      <span>🔄 新版本可用</span>
      <button id="pwa-update-btn" style="
        background: white;
        color: #409EFF;
        border: none;
        padding: 6px 16px;
        border-radius: 4px;
        cursor: pointer;
        font-weight: 600;
      ">立即更新</button>
      <button id="pwa-update-close" style="
        background: transparent;
        color: white;
        border: 1px solid rgba(255,255,255,0.5);
        padding: 6px 12px;
        border-radius: 4px;
        cursor: pointer;
        font-size: 13px;
      ">关闭</button>
    `

    document.body.appendChild(banner)

    document.getElementById('pwa-update-btn')?.addEventListener('click', () => {
      if (registration && registration.waiting) {
        registration.waiting.postMessage({ type: 'SKIP_WAITING' })
      }
      banner.remove()
    })

    document.getElementById('pwa-update-close')?.addEventListener('click', () => {
      banner.remove()
    })

    // Auto-hide after 10 seconds
    setTimeout(() => {
      banner.remove()
    }, 10000)
  }
}

/**
 * Show install prompt (for "Add to Desktop" button)
 * Call this when user clicks an install button
 */
export async function promptInstall(): Promise<void> {
  if (!promptEvent) {
    console.warn('PWA install prompt not available')
    return
  }

  try {
    await promptEvent.prompt()
    const { outcome } = await promptEvent.userChoice
    console.log(`PWA install ${outcome}`)
    promptEvent = null
  } catch (error) {
    console.error('PWA install failed:', error)
  }
}

/**
 * Check if the app is running in standalone/isolated mode (installed as PWA)
 */
export function isPWAInstalled(): boolean {
  if (typeof window !== 'undefined') {
    return window.matchMedia('(display-mode: standalone)').matches
      || ('standalone' in navigator && (navigator as any).standalone === true)
  }
  return false
}

/**
 * Unregister service worker (for development/debugging)
 */
export async function unregisterPWA(): Promise<void> {
  if (registration) {
    await registration.unregister()
    registration = null
    console.log('PWA Service Worker unregistered')
  }
}

// Auto-register on module load
if (typeof window !== 'undefined') {
  registerPWA()
}
