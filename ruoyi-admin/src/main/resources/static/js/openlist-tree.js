/**
 * 初始化下拉目录树
 * @param inputId 输入框的jQuery ID (例如 '#sourceFolder')
 * @param type 目录类型 ('local' 或 'openlist')
 */
function initDirectoryTree(inputId, type) {
    var $input = $(inputId);
    // 生成唯一的树ID
    var treeId = "tree_" + inputId.replace('#', '') + "_" + Math.floor(Math.random() * 1000);
    var contentId = "content_" + treeId;

    // 1. 构建下拉容器 HTML
    // --- 正确写法 (ES5 兼容) ---
    var treeContent =
        '<div id="' + contentId + '" class="treeContent" style="display:none; position: absolute; z-index: 99999; background: #fff; border: 1px solid #ccc; max-height: 300px; overflow: auto; box-shadow: 0 6px 12px rgba(0,0,0,.175);">' +
        '<ul id="' + treeId + '" class="ztree" style="margin-top:0; width:100%;"></ul>' +
        '</div>';

    // 将容器追加到 body 或 input 父级（推荐 body 以避免 overflow 遮挡）
    $("body").append(treeContent);

    // 2. zTree 配置
    var setting = {
        view: {
            dblClickExpand: false,
            selectedMulti: false,
            nameIsHTML: true
        },
        data: {
            simpleData: { enable: true },
            key: { title: "title" }
        },
        async: {
            enable: true,
            // 后端接口地址，复用之前的 Controller
            url: ctx + "openliststrm/common/path/" + type,
            autoParam: ["id"], // 核心：点击节点时，自动将当前节点 id 传给后端，实现"一级一级"加载
            type: "post"
        },
        callback: {
            // 点击节点回调
            onClick: function(e, treeId, treeNode) {
                // 将路径回填到输入框
                $input.val(treeNode.id);
                // 也可以选择回填名称： $input.val(treeNode.name);
                hideTree();
            },
            onAsyncSuccess: function(event, treeId, treeNode, msg) {
                // 加载成功后的一些处理，比如没有子节点提示
            }
        }
    };

    // 3. 初始化树
    $.fn.zTree.init($("#" + treeId), setting);

    // 4. 绑定 Input 点击事件
    $input.on("click", function() {
        var inputObj = $input;
        var inputOffset = $input.offset();
        var $content = $("#" + contentId);

        // 设置下拉框位置和宽度
        $content.css({
            left: inputOffset.left + "px",
            top: (inputOffset.top + inputObj.outerHeight()) + "px",
            width: inputObj.outerWidth() + "px"
        }).slideDown("fast");

        // 绑定外部点击隐藏
        $("body").bind("mousedown", onBodyDown);
    });

    // 内部函数：隐藏树
    function hideTree() {
        $("#" + contentId).fadeOut("fast");
        $("body").unbind("mousedown", onBodyDown);
    }

    // 内部函数：检测点击外部
    function onBodyDown(event) {
        if (!(event.target.id == inputId.replace('#', '') || event.target.id == contentId || $(event.target).parents("#" + contentId).length>0)) {
            hideTree();
        }
    }
}