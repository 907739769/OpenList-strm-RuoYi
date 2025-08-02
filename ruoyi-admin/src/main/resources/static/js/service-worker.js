self.addEventListener('install', event => {
    self.skipWaiting(); // 立即激活新 worker
});

self.addEventListener('activate', event => {
    clients.claim(); // 立即接管页面
});
