#!/bin/bash

chown -R "${PUID}":"${PGID}" /app
chown -R "${PUID}":"${PGID}" /data

cd /app

umask "${UMASK}"

exec gosu "${PUID}":"${PGID}" java $JAVA_OPTS -XX:+UseG1GC -XX:+OptimizeStringConcat -XX:+PrintGCDetails -Xloggc:/data/logs/gc.log -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCApplicationConcurrentTime -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/logs -jar /app/openliststrm-ruoyi.jar
