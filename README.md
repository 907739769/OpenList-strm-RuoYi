## 平台简介

基于ruoyi框架升级的openliststrm

## 内置功能

与原openliststrm功能一致，增加后管页面功能，可查看任务运行情况，手动执行任务，配置多个strm任务。

1. 生成strm文件，定时任务执行，前端页面手动执行，tg机器人执行
2. 复制同步openlist的两个文件夹，tg机器人执行，接口调用执行
3. 可支持第三方app回调，自动化处理，如qb下载完成通知，自动复制openlist挂载的本地硬盘复制到云盘然后生成strm文件
4. 可前端页面配置同步任务及strm任务
5. 前端页面查询任务记录及执行情况

## 后续功能开发

- [ ] 同步任务记录页面支持重新处理单条或多条任务记录
- [ ] STRM生成记录页面支持重新处理单条或多条任务记录
- [ ] 同步任务配置页面支持单个或多个任务执行
- [ ] strm任务配置页面支持单个或多个任务执行
- [ ] 同步任务记录页面、STRM生成记录页面支持单个或多个文件的网盘文件删除

## 安装配置

直接可用docker安装，安装完成访问http://192.x.x.x:6895   默认账号密码admin/openliststrm666

登录页面之后，在`系统管理-参数设置`菜单设置参数，如果设置了tg参数，需要重启服务。

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
      - osr_db
    links:
      - osr_db
```

## qb脚本参考
`sh /config/notify.sh "%G" "%F"`

以下脚本需要修改三个参数及ip端口

```
#!/bin/bash

# 获取传递的标签
TAG=$1
qbDlFilePath=$2
MOVIEPILOT="MOVIEPILOT"
# 下面三个参数根据实际情况调整
srcDir=/download/pt
srcDst=/115网盘/影视/pt
qbDlRootPath=/data/downloads/pt

if [[ "$TAG" =~ "$MOVIEPILOT" ]]; then
# 调用 notify 接口  ip及端口需要调整
curl -X POST -H "Content-Type: application/json" -H "X-API-KEY: xxx" -d "{\"qbDlFilePath\": \"$qbDlFilePath\",\"srcDir\": \"$srcDir\",\"srcDst\": \"$srcDst\",\"qbDlRootPath\": \"$qbDlRootPath\"}" http://192.168.31.66:6895/api/v1/notifyByDir &>/dev/null &
fi
```

## 演示图

<table>
    <tr>
        <td><img src="https://github.com/user-attachments/assets/11509f8a-607a-41b0-a087-a77dca126971"/></td>
        <td><img src="https://github.com/user-attachments/assets/64e77498-bace-432b-bc30-f948ae034fe0"/></td>
    </tr>
    <tr>
        <td><img src="https://github.com/user-attachments/assets/44ee8540-65dc-4c6a-aad7-9df093b95bd1"/></td>
        <td><img src="https://github.com/user-attachments/assets/e83e5046-ff3f-4525-a42e-b6ced6c572a2"/></td>
    </tr>
</table>
