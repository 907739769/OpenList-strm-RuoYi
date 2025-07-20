## 平台简介

基于ruoyi框架升级的openliststrm

## 内置功能

与原openliststrm功能一致，增加后管页面功能，可查看任务运行情况，手动执行任务，配置多个strm任务。

## 安装配置

直接可用docker安装，安装完成访问http://192.x.x.x:6895，账号密码admin/openliststrm666

```
version : '3'
services:
  osr_db:
    container_name: osr_db
    restart: on-failure:3
    image: mysql:5.7
    volumes:
      - ./mysql:/var/lib/mysql
    command: [
          'mysqld',
          '--innodb-buffer-pool-size=80M',
          '--character-set-server=utf8mb4',
          '--collation-server=utf8mb4_unicode_ci',
          '--default-time-zone=+8:00',
          '--lower-case-table-names=1'
        ]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "--silent"]
      interval: 3s
      retries: 5
      start_period: 30s
    environment:
      MYSQL_DATABASE: osr
      MYSQL_ROOT_PASSWORD: Ty#s9U1@L
  osr-server:
    container_name: osr-server
    restart: on-failure:3
    image: jacksaoding/openlist-strm-ruoyi:latest
    ports:
      - "6895:6895"
    volumes:
      - ./data:/data
    environment:
      DB_PASSWORD: Ty#s9U1@L
    depends_on:
      osr_db:
        condition: service_healthy
    links:
      - osr_db
```

## qb脚本参考
sh /config/notify.sh "%G" "%F"

```
#!/bin/bash

# 获取传递的标签
TAG=$1
qbDlFilePath=$2
MOVIEPILOT="MOVIEPILOT"

if [[ "$TAG" =~ "$MOVIEPILOT" ]]; then
# 调用 notify 接口
curl -X POST -H "Content-Type: application/json" -H "X-API-KEY: xxx" -d "{\"qbDlFilePath\": \"$qbDlFilePath\",\"srcDir\": \"/download/pt\",\"srcDst\": \"/115网盘/影视/pt\",\"qbDlRootPath\": \"/data/downloads/pt\"}" http://192.168.31.66:6895/api/v1/notifyByDir &>/dev/null &
fi
```

## 演示图

<table>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-42e518aa72a24d228427a1261cb3679f395.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-7f20dd0edba25e5187c5c4dd3ec7d3d9797.png"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-2dae3d87f6a8ca05057db059cd9a411d51d.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-ea4d98423471e55fba784694e45d12bd4bb.png"/></td>
    </tr>
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-7f6c6e9f5873efca09bd2870ee8468b8fce.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-c708b65f2c382a03f69fe1efa8d341e6cff.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-9ab586c47dd5c7b92bca0d727962c90e3b8.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-ef954122a2080e02013112db21754b955c6.png"/></td>
    </tr>	 
    <tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-088edb4d531e122415a1e2342bccb1a9691.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-f886fe19bd820c0efae82f680223cac196c.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-c7a2eb71fa65d6e660294b4bccca613d638.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-e60137fb0787defe613bd83331dc4755a70.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-7c51c1b5758f0a0f92ed3c60469b7526f9f.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-15181aed45bb2461aa97b594cbf2f86ea5f.png"/></td>
    </tr>
	<tr>
        <td><img src="https://oscimg.oschina.net/oscnet/up-83326ad52ea63f67233d126226738054d98.png"/></td>
        <td><img src="https://oscimg.oschina.net/oscnet/up-3bd6d31e913b70df00107db51d64ef81df7.png"/></td>
    </tr>
</table>
