const CACHE_NAME = 'osr-cache-v1';
const PRECACHE_URLS = [
    '/css/bootstrap.min.css',
    '/css/font-awesome.min.css',
    '/css/style.min.css',
    '/css/login.min.css',
    '/ruoyi/css/ry-ui.css?v=4.8.1',
    '/ruoyi/js/ry-ui.js?v=4.8.1',
    '/js/jquery.min.js',
    '/ajax/libs/validate/jquery.validate.min.js',
    '/ajax/libs/layer/layer.min.js',
    '/ajax/libs/blockUI/jquery.blockUI.js',
    '/ruoyi/login.js',
    '/img/login-background.jpg',
    '/ajax/libs/layer/css/layer.css?v=3.7.0',
    '/ajax/libs/layer/theme/moon/style.css',
    '/ruoyi.png',
    '/img/user.png',
    '/web-app-manifest-192x192.png',
    '/web-app-manifest-512x512.png',
    '/img/locked.png',
    '/css/jquery.contextMenu.min.css',
    '/css/animate.min.css',
    '/css/skins.css',
    '/img/profile.jpg',
    '/favicon.ico',
    '/manifest.json',
    '/js/plugins/metisMenu/jquery.metisMenu.js',
    '/js/plugins/slimscroll/jquery.slimscroll.min.js',
    '/js/jquery.contextMenu.min.js',
    '/ruoyi/js/common.js?v=4.8.1',
    '/ruoyi/index.js?v=20201208',
    '/ajax/libs/report/echarts/echarts-all.min.js?v=4.2.1',
    '/ajax/libs/layui/layui.min.js?v=2.8.18',
    '/ajax/libs/layer/layer.min.js?v=3.7.0',
    '/ajax/libs/iCheck/icheck.min.js?v=1.0.3',
    '/ajax/libs/validate/messages_zh.js?v=1.21.0',
    '/ajax/libs/validate/jquery.validate.extend.js?v=1.21.0',
    '/ajax/libs/validate/jquery.validate.min.js?v=1.21.0',
    '/ajax/libs/fullscreen/jquery.fullscreen.js'
];

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
