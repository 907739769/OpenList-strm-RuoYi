package com.ruoyi.web.controller.api.system;

import com.google.code.kaptcha.Constants;
import com.google.code.kaptcha.Producer;
import com.ruoyi.common.config.JwtConfigProperties;
import com.ruoyi.common.core.domain.JwtTokenDto;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.JwtTokenUtil;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.shiro.service.SysLoginService;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.framework.shiro.service.SysRegisterService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.common.annotation.Anonymous;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

@Tag(name = "认证管理API")
@RestController
@RequestMapping("/api/auth")
@Anonymous
@CrossOrigin(origins = "*")
public class AuthApiController extends BaseController {

    @Value("${shiro.rememberMe.enabled: false}")
    private boolean rememberMe;

    @Autowired
    private SysRegisterService registerService;

    @Autowired
    private ISysConfigService sysConfigService;

    @Autowired
    private SysPasswordService passwordService;

    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private JwtConfigProperties jwtConfig;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Resource(name = "captchaProducer")
    private Producer captchaProducer;

    @Resource(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<JwtTokenDto> login(@Validated @RequestBody LoginRequest request) {
        if (StringUtils.isNotBlank(request.getCode()) && StringUtils.isNotBlank(request.getUuid())) {
            HttpSession session = ServletUtils.getRequest().getSession();
            String capText = (String) session.getAttribute(Constants.KAPTCHA_SESSION_KEY);
            if (capText == null || !capText.equalsIgnoreCase(request.getCode())) {
                return Result.error(400, "验证码错误");
            }
            session.removeAttribute(Constants.KAPTCHA_SESSION_KEY);
        }

        SysUser user = null;
        try {
            user = loginService.login(request.getUsername(), request.getPassword());
        } catch (Exception e) {
            String msg = "用户或密码错误";
            if (StringUtils.isNotEmpty(e.getMessage())) {
                msg = e.getMessage();
            }
            return Result.error(401, msg);
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("loginName", user.getLoginName());

        Set<String> roles = roleService.selectRoleKeys(user.getUserId());
        Set<String> perms = menuService.selectPermsByUserId(user.getUserId());
        claims.put("roles", new ArrayList<>(roles));
        claims.put("permissions", new ArrayList<>(perms));

        String token = jwtTokenUtil.generateToken(user.getLoginName(), user.getUserId(), claims);

        JwtTokenDto dto = new JwtTokenDto();
        dto.setToken(token);
        dto.setUserId(user.getUserId());
        dto.setLoginName(user.getLoginName());
        dto.setUserName(user.getUserName());
        Map<String, Object> permissionMap = new HashMap<>();
        permissionMap.put("roles", roles);
        permissionMap.put("permissions", perms);
        dto.setPermissions(permissionMap);
        dto.setExpireTime(jwtConfig.getExpiration() + System.currentTimeMillis());

        return Result.success(dto);
    }

    @Operation(summary = "用户退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader, HttpServletResponse response) {
        // 清除JWT cookie
        jakarta.servlet.http.Cookie[] cookies = ServletUtils.getRequest().getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    response.addCookie(cookie);
                    break;
                }
            }
        }
        return Result.success();
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody SysUser user) {
        if (!("true".equals(sysConfigService.selectConfigByKey("sys.account.registerUser")))) {
            return Result.error(500, "当前系统没有开启注册功能！");
        }
        String msg = registerService.register(user);
        if (StringUtils.isEmpty(msg)) {
            return Result.success();
        }
        return Result.error(500, msg);
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = stripBearer(authHeader);
        String username = jwtTokenUtil.getUsernameFromToken(token);
        if (StringUtils.isEmpty(username)) {
            return Result.error(401, "未登录");
        }
        SysUser user = userService.selectUserByLoginName(username);
        if (user == null) {
            return Result.error(401, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("roles", roleService.selectRoleKeys(user.getUserId()));
        data.put("permissions", menuService.selectPermsByUserId(user.getUserId()));
        return Result.success(data);
    }

    @Operation(summary = "获取路由信息")
    @GetMapping("/routers")
    public Result<List<Map<String, Object>>> getRouters(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = stripBearer(authHeader);
        String username = jwtTokenUtil.getUsernameFromToken(token);
        if (StringUtils.isEmpty(username)) {
            return Result.error(401, "未登录");
        }
        SysUser user = userService.selectUserByLoginName(username);
        List<SysMenu> menus = menuService.selectMenusByUser(user);
        List<Map<String, Object>> routerList = buildMenus(menus);
        return Result.success(routerList);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildMenus(List<SysMenu> menus) {
        List<Map<String, Object>> routerList = new ArrayList<>();
        for (SysMenu menu : menus) {
            Map<String, Object> router = new LinkedHashMap<>();
            String path = menu.getUrl();
            if ("#".equals(path) || StringUtils.isEmpty(path)) {
                path = derivePath(menu);
            }
            router.put("path", path);
            router.put("name", menu.getMenuName());
            router.put("meta", menuMeta(menu));
            router.put("hidden", "1".equals(menu.getVisible()));

            if ("M".equals(menu.getMenuType())) {
                router.put("component", "Layout");
                List<SysMenu> children = menu.getChildren();
                if (children != null && children.size() > 0) {
                    router.put("redirect", "noRedirect");
                    router.put("children", buildMenus(children));
                }
            } else if ("C".equals(menu.getMenuType())) {
                router.put("component", menu.getComponentPath());
                List<SysMenu> children = menu.getChildren();
                if (children != null && children.size() > 0) {
                    router.put("children", buildMenus(children));
                }
            }
            routerList.add(router);
        }
        return routerList;
    }

    private String derivePath(SysMenu menu) {
        List<SysMenu> children = menu.getChildren();
        if (children != null && !children.isEmpty()) {
            String childPath = children.get(0).getUrl();
            if (StringUtils.isNotEmpty(childPath) && !"#".equals(childPath)) {
                String parentPath = childPath.substring(0, childPath.lastIndexOf("/"));
                return StringUtils.isEmpty(parentPath) ? "/" + menu.getMenuName().toLowerCase() : parentPath;
            }
        }
        return "/" + menu.getMenuName().toLowerCase();
    }

    private Map<String, Object> menuMeta(SysMenu menu) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", menu.getMenuName());
        meta.put("icon", menu.getIcon());
        meta.put("alwaysShow", "M".equals(menu.getMenuType()) && menu.getChildren() != null && menu.getChildren().size() > 0);
        return meta;
    }

    private String stripBearer(String authHeader) {
        if (StringUtils.isNotEmpty(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    @Operation(summary = "获取验证码图片")
    @GetMapping("/captchaImage")
    public void getCaptchaImage(HttpServletRequest request, HttpServletResponse response, String type) {
        ServletOutputStream out = null;
        try {
            HttpSession session = request.getSession();
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.addHeader("Cache-Control", "post-check=0, pre-check=0");
            response.setHeader("Pragma", "no-cache");
            response.setContentType("image/jpeg");

            String capStr = null;
            String code = null;
            BufferedImage bi = null;
            if ("math".equals(type)) {
                String capText = captchaProducerMath.createText();
                capStr = capText.substring(0, capText.lastIndexOf("@"));
                code = capText.substring(capText.lastIndexOf("@") + 1);
                bi = captchaProducerMath.createImage(capStr);
            } else {
                capStr = code = captchaProducer.createText();
                bi = captchaProducer.createImage(capStr);
            }
            session.setAttribute(Constants.KAPTCHA_SESSION_KEY, code);
            out = response.getOutputStream();
            ImageIO.write(bi, "jpg", out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Operation(summary = "修改密码")
    @PostMapping("/changePassword")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = stripBearer(authHeader);
        String username = jwtTokenUtil.getUsernameFromToken(token);
        if (StringUtils.isEmpty(username)) {
            return Result.error(401, "未登录");
        }
        SysUser user = userService.selectUserByLoginName(username);
        if (user == null) {
            return Result.error(401, "用户不存在");
        }

        if (!passwordService.matches(user, request.getOldPassword())) {
            return Result.error(400, "旧密码错误");
        }

        user.setPassword(passwordService.encodePassword(request.getNewPassword()));
        if (userService.updateUser(user) > 0) {
            return Result.success();
        }
        return Result.error(500, "修改密码失败");
    }

    public static class ChangePasswordRequest {
        @jakarta.validation.constraints.NotBlank(message = "旧密码不能为空")
        private String oldPassword;
        @jakarta.validation.constraints.NotBlank(message = "新密码不能为空")
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class LoginRequest {
        @jakarta.validation.constraints.NotBlank(message = "用户名不能为空")
        private String username;
        @jakarta.validation.constraints.NotBlank(message = "密码不能为空")
        private String password;
        private Boolean rememberMe;
        private String code;
        private String uuid;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Boolean getRememberMe() { return rememberMe; }
        public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
    }
}
