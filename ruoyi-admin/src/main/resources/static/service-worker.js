const CACHE_NAME = 'osr-cache-v1';
const PRECACHE_URLS = [];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            return cache.addAll(PRECACHE_URLS);
        })
    );
    self.skipWaiting(); // 立即激活新 worker
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(cacheNames =>
            Promise.all(
                cacheNames.map(name => {
                    if (name !== CACHE_NAME) {
                        return caches.delete(name); // 清理旧缓存
                    }
                })
            )
        )
    );
    clients.claim(); // 立即接管页面
});

self.addEventListener('fetch', event => {
    const req = event.request;

    // 只缓存静态资源
    const url = new URL(event.request.url);
    // 正则匹配 .js/.css/.png 等静态文件后缀，不管有没有参数
    const isStatic = url.pathname.match(/\.(js|css|png|ico|json|html|jpe?g|gif|svg|woff2?|ttf|eot)$/i);

    if (isStatic) {
        event.respondWith(
            caches.match(req).then(cacheRes => {
                return cacheRes || fetch(req).then(networkRes => {
                    return caches.open(CACHE_NAME).then(cache => {
                        cache.put(req, networkRes.clone());
                        return networkRes;
                    });
                });
            })
        );
    } else {
        // 非静态资源直接走网络
        event.respondWith(fetch(req));
    }
});
