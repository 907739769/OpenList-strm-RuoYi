# 基础镜像
FROM eclipse-temurin:8u412-b08-jre-jammy
LABEL title="openlist-strm-ruoyi"
LABEL description="将openlist的视频文件生成媒体播放设备可播放的strm文件，基于ruoyi框架升级"
LABEL authors="JackDing"
RUN apt-get update && apt-get install -y gosu && rm -rf /var/lib/apt/lists/*

# 挂载目录
VOLUME /data
# 创建目录
RUN mkdir -p /app
# 指定路径
WORKDIR /app
# 复制jar文件到路径
COPY ./ruoyi-admin/target/ruoyi-admin.jar /app/openliststrm-ruoyi.jar
COPY --chmod=755 entrypoint.sh /entrypoint.sh
ENV TZ=Asia/Shanghai
ENV PUID=0
ENV PGID=0
ENV UMASK=022
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
# 启动应用
ENTRYPOINT ["/entrypoint.sh"]