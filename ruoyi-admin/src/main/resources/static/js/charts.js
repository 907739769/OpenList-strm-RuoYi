/**
 * 初始化图表
 */
$(function () {
    // 初始化COPY任务图表
    initPieChart('copy-pie-chart', ctx + 'openliststrm/copy/stats', 'COPY任务');

    // 初始化STRM任务图表
    initPieChart('strm-pie-chart', ctx + 'openliststrm/strm/stats', 'STRM任务');

    // 窗口大小变化时重绘图表
    $(window).on('resize', function () {
        echarts.getInstanceByDom(document.getElementById('copy-pie-chart'))?.resize();
        echarts.getInstanceByDom(document.getElementById('strm-pie-chart'))?.resize();
    });
});

/**
 * 初始化饼图
 * @param {string} elementId 图表容器ID
 * @param {string} url 数据请求URL
 * @param {string} title 图表标题
 */
function initPieChart(elementId, url, title) {
    const chartDom = document.getElementById(elementId);
    const chart = echarts.init(chartDom);

    // 预定义颜色映射表（可根据实际数据类型调整）
    const colorMap = {
        // 状态类数据
        '成功': '#49a61b',
        '失败': '#bd272f',
        '未知': '#bb8518',
        '处理中': '#1db772',
        // 默认颜色序列（当类型未匹配时使用）
        '_default': ['#466dbd', '#50c295', '#b98f15', '#b44e36', '#6DC8EC', '#9270CA', '#FF9D4D', '#269A99']
    };

    // 获取颜色的函数
    function getColorForData(name) {
        // 首先检查是否有精确匹配
        if (colorMap[name]) {
            return colorMap[name];
        }

        // 检查是否有包含关键词的匹配（不区分大小写）
        const lowerName = name.toLowerCase();
        for (const key in colorMap) {
            if (key !== '_default' && lowerName.includes(key.toLowerCase())) {
                return colorMap[key];
            }
        }

        // 使用默认颜色序列，按数据索引循环使用
        const defaultColors = colorMap._default;
        const index = Object.keys(colorMap).filter(k => k !== '_default').length;
        return defaultColors[index % defaultColors.length];
    }

    $.ajax({
        url: url,
        type: "POST",
        dataType: "json",
        success: function (data) {
            if (data.code === 0 && Object.keys(data.data).length > 0) {
                const chartData = Object.entries(data.data).map(([name, value]) => ({
                    value: value,
                    name: name,
                    itemStyle: {
                        color: getColorForData(name)
                    }
                }));

                const option = {
                    title: {
                        text: title,
                        subtext: '今日数据',
                        left: 'center',
                        textStyle: {
                            fontSize: 16,
                            fontWeight: 'bold'
                        }
                    },
                    tooltip: {
                        trigger: 'item',
                        formatter: '{b}: {c} ({d}%)'
                    },
                    legend: {
                        orient: 'vertical',
                        left: 'left',
                        formatter: function(name) {
                            // 在legend中显示名称和笔数
                            const item = chartData.find(d => d.name === name);
                            const color = item ? item.itemStyle.color : '#999';
                            const count = item ? item.value : 0;
                            return `${name}: {count|${count}}`;
                        },
                        textStyle: {
                            rich: {
                                count: {
                                    fontWeight: 'bold',
                                    color: '#333'
                                }
                            }
                        },
                        data: chartData.map(item => item.name)
                    },
                    series: [{
                        name: title,
                        type: 'pie',
                        radius: ['50%', '70%'],
                        avoidLabelOverlap: false,
                        itemStyle: {
                            borderRadius: 5,
                            borderColor: '#fff',
                            borderWidth: 2
                        },
                        label: {
                            show: false,
                            position: 'center'
                        },
                        emphasis: {
                            label: {
                                show: true,
                                fontSize: '18',
                                fontWeight: 'bold'
                            }
                        },
                        labelLine: {
                            show: false
                        },
                        data: chartData
                    }],
                    animationEasing: 'elasticOut'
                };

                chart.setOption(option);
            } else {
                // 无数据时的展示
                chart.setOption({
                    title: {
                        text: title,
                        subtext: '暂无数据',
                        left: 'center',
                        textStyle: {
                            fontSize: 16,
                            fontWeight: 'bold'
                        }
                    },
                    series: [] // 空系列
                });
            }
        },
        error: function (xhr, status, error) {
            $.modal.msgError("获取数据失败: " + (error || status));
            // 显示空状态
            chart.setOption({
                title: {
                    text: title,
                    subtext: '数据加载失败',
                    left: 'center',
                    top: 'center'
                }
            });
        }
    });

    return chart;
}
