package io.github.jiafuxiaoqu.openinidea;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 核心服务：根据 HTTP 请求 URL 扫描本地源码，定位到 Controller 方法所在行，
 * 并调用系统命令在 IDEA 中打开。
 *
 * <h3>基本用法</h3>
 * <pre>{@code
 * // 使用默认实例（扫描 user.dir 下的源码）
 * OpenInIdeaResult result = OpenInIdeaService.getInstance().open(
 *         new OpenInIdeaRequest()
 *                 .requestUrl("http://localhost/dev-api/system/user/list")
 *                 .method("GET")
 *                 .ideaDir("C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/bin")
 *                 .ideaName("idea64.exe")
 * );
 * }</pre>
 *
 * <h3>多模块项目 / 自定义源码目录</h3>
 * <pre>{@code
 * OpenInIdeaService service = new OpenInIdeaService(Arrays.asList(
 *         "C:/work/my-project/ruoyi-system/src/main/java",
 *         "C:/work/my-project/ruoyi-admin/src/main/java"
 * ));
 * }</pre>
 */
public class OpenInIdeaService {

    // -------------------------------------------------------------------------
    // 单例（默认扫描 user.dir）
    // -------------------------------------------------------------------------

    private static volatile OpenInIdeaService defaultInstance;

    /**
     * 获取默认实例，扫描目录为 {@code System.getProperty("user.dir")}。
     */
    public static OpenInIdeaService getInstance() {
        if (defaultInstance == null) {
            synchronized (OpenInIdeaService.class) {
                if (defaultInstance == null) {
                    defaultInstance = new OpenInIdeaService(
                            Collections.singletonList(System.getProperty("user.dir"))
                    );
                }
            }
        }
        return defaultInstance;
    }

    // -------------------------------------------------------------------------
    // 实例字段
    // -------------------------------------------------------------------------

    /**
     * key  : 标准化后的接口路径，如 {@code /system/user/{userId}}
     * value: 绝对路径:行号，如  {@code C:/work/SysUserController.java:88}
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** 要扫描的源码根目录列表 */
    private final List<String> sourceDirs;

    private static final List<String> MAPPING_ANNOTATIONS = Arrays.asList(
            "RequestMapping", "GetMapping", "PostMapping",
            "PutMapping", "DeleteMapping", "PatchMapping"
    );

    // -------------------------------------------------------------------------
    // 构造
    // -------------------------------------------------------------------------

    /**
     * @param sourceDirs 源码根目录列表，如 {@code src/main/java} 的绝对路径
     */
    public OpenInIdeaService(List<String> sourceDirs) {
        if (sourceDirs == null || sourceDirs.isEmpty()) {
            throw new IllegalArgumentException("sourceDirs 不能为空");
        }
        this.sourceDirs = sourceDirs;
    }

    // -------------------------------------------------------------------------
    // 公开 API
    // -------------------------------------------------------------------------

    /**
     * 根据请求参数定位源码行并在 IDEA 中打开。
     *
     * @param request 四个参数的入参对象
     * @return 执行结果，通过 {@link OpenInIdeaResult#isSuccess()} 判断是否成功
     */
    public OpenInIdeaResult open(OpenInIdeaRequest request) {
        try {
            request.validate();
            return doOpen(request);
        } catch (IllegalArgumentException e) {
            return OpenInIdeaResult.failure(e.getMessage());
        } catch (Exception e) {
            return OpenInIdeaResult.failure("执行失败: " + e.getMessage());
        }
    }

    /**
     * 仅查询匹配的源码位置，不打开 IDEA。
     * 适合在接口列表页展示每个请求对应的源码位置。
     *
     * @param urlPath 请求路径（可以是完整 URL 或纯路径）
     * @return "文件路径:行号"，未找到返回 null
     */
    public String locate(String urlPath) {
        try {
            ensureCacheLoaded();
            return findBestMatch(normalizePath(extractPath(urlPath)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除扫描缓存，下次调用时重新扫描。
     * 在开发阶段修改了源码后可调用此方法刷新。
     */
    public void clearCache() {
        cache.clear();
        log("Cache cleared.");
    }

    // -------------------------------------------------------------------------
    // 核心逻辑
    // -------------------------------------------------------------------------

    private OpenInIdeaResult doOpen(OpenInIdeaRequest request) throws Exception {

        String urlPath  = extractPath(request.getRequestUrl());
        String matched  = findInCacheOrScan(urlPath);

        if (matched == null) {
            return OpenInIdeaResult.failure("未找到对应接口: " + urlPath);
        }

        int    colon     = matched.lastIndexOf(":");
        String filePath  = matched.substring(0, colon);
        int    lineNo    = Integer.parseInt(matched.substring(colon + 1));

        ProcessBuilder pb = buildCommand(
                request.getIdeaDir(),
                request.getIdeaName(),
                filePath,
                lineNo
        );
        pb.redirectErrorStream(true);
        pb.start(); // fire-and-forget

        return OpenInIdeaResult.success(filePath, lineNo);
    }

    /**
     * 从完整 URL 或纯路径中提取 path 部分（去掉 scheme、host、query）。
     */
    private String extractPath(String url) throws Exception {
        if (url == null || url.isEmpty()) throw new IllegalArgumentException("URL 不能为空");
        // 如果包含 :// 才按 URI 解析，否则直接当 path 用
        if (url.contains("://")) {
            return new URI(url).getPath();
        }
        return url;
    }

    // -------------------------------------------------------------------------
    // 缓存 & 扫描
    // -------------------------------------------------------------------------

    private synchronized String findInCacheOrScan(String urlPath) throws Exception {
        ensureCacheLoaded();
        return findBestMatch(normalizePath(urlPath));
    }

    private void ensureCacheLoaded() throws Exception {
        if (cache.isEmpty()) {
            log("Cache is empty, scanning source directories...");
            long start = System.currentTimeMillis();
            for (String dir : sourceDirs) {
                Path root = Paths.get(dir);
                if (!Files.exists(root)) {
                    log("Directory not found, skipping: " + dir);
                    continue;
                }
                Files.walk(root)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> p.getFileName().toString().contains("Controller"))
                        .forEach(this::parseFile);
            }
            log("Scan done in " + (System.currentTimeMillis() - start) + "ms, "
                    + cache.size() + " mappings found.");
        }
    }

    private String findBestMatch(String requestPath) {
        List<String> candidates = generateCandidatePaths(requestPath);

        for (String candidate : candidates) {
            candidate = normalizePath(candidate);

            String exact = cache.get(candidate);
            if (exact != null) {
                log("Matched [" + candidate + "] for [" + requestPath + "]");
                return exact;
            }

            for (Map.Entry<String, String> entry : cache.entrySet()) {
                String mappingPath = normalizePath(entry.getKey());
                if (antMatch(mappingPath, candidate)) {
                    log("Matched [" + mappingPath + "] for [" + requestPath + "]");
                    return entry.getValue();
                }
            }
        }

        return null;
    }
    /**
     * 生成多个候选路径，用于兼容 gateway 前缀
     *
     * 例如：
     * /prod-api/system/config/configKey/aaa
     *
     * 生成：
     * /prod-api/system/config/configKey/aaa
     * /system/config/configKey/aaa
     * /config/configKey/aaa
     * /configKey/aaa
     */
    private List<String> generateCandidatePaths(String path) {

        path = normalizePath(path);

        List<String> list = new java.util.ArrayList<>();

        list.add(path);

        String[] arr = path.split("/");

        for (int i = 1; i < arr.length; i++) {

            StringBuilder sb = new StringBuilder();

            for (int j = i; j < arr.length; j++) {

                if (arr[j] == null || arr[j].isEmpty()) {
                    continue;
                }

                sb.append("/").append(arr[j]);
            }

            String candidate = sb.toString();

            if (!candidate.isEmpty()) {
                list.add(candidate);
            }
        }

        return list;
    }

    // -------------------------------------------------------------------------
    // 源码解析
    // -------------------------------------------------------------------------

    private void parseFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                String classMapping = getMappingValue(clazz.getAnnotations(), false);
                clazz.getMethods().forEach(method -> {
                    String methodMapping = getMappingValue(method.getAnnotations(), true);
                    if (methodMapping == null) return;

                    String fullPath = normalizePath(classMapping + methodMapping);
                    int    line     = method.getBegin().map(p -> p.line).orElse(-1);
                    if (line == -1) return;

                    String file = path.toAbsolutePath().toString().replace("\\", "/");
                    cache.put(fullPath, file + ":" + line);
                    log("[MAPPING] " + fullPath + " -> " + file + ":" + line);
                });
            });
        } catch (Exception e) {
            log("Parse failed (skipped): " + path);
        }
    }

    private String getMappingValue(List<AnnotationExpr> annotations, boolean isMethod) {
        for (AnnotationExpr ann : annotations) {
            if (!MAPPING_ANNOTATIONS.contains(ann.getNameAsString())) continue;

            String val = "";
            if (ann instanceof SingleMemberAnnotationExpr) {
                val = ((SingleMemberAnnotationExpr) ann).getMemberValue().toString();
            } else if (ann instanceof NormalAnnotationExpr) {
                for (MemberValuePair pair : ((NormalAnnotationExpr) ann).getPairs()) {
                    if ("value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString())) {
                        val = pair.getValue().toString();
                    }
                }
            }

            val = val.replace("\"", "");

            // 处理 value={"/a", "/b"}，取第一个
            if (val.startsWith("{") && val.endsWith("}")) {
                val = val.substring(1, val.length() - 1)
                        .split(",")[0].trim().replace("\"", "");
            }

            if (!val.isEmpty() && !val.startsWith("/")) val = "/" + val;
            return val;
        }
        return isMethod ? null : "";
    }

    // -------------------------------------------------------------------------
    // 路径匹配
    // -------------------------------------------------------------------------

    /**
     * 简易 Ant 风格路径匹配（不依赖 Spring）。
     * 支持 {@code *}（单段）和 {@code **}（多段）。
     */
    private boolean antMatch(String pattern, String path) {
        pattern = normalizePath(pattern);
        path = normalizePath(path);

        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int end = pattern.indexOf('}', i);
                if (end == -1) {
                    regex.append(Pattern.quote(String.valueOf(c)));
                } else {
                    regex.append("[^/]+");
                    i = end;
                }
            } else if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append("$");

        return path.matches(regex.toString());
    }

    // -------------------------------------------------------------------------
    // 系统命令
    // -------------------------------------------------------------------------

    private ProcessBuilder buildCommand(String ideaDir, String ideaName,
                                        String filePath, int lineNo) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String cmd = String.format(
                    "cd /d \"%s\" && %s --line %d \"%s\"",
                    ideaDir, ideaName, lineNo, filePath);
            return new ProcessBuilder("cmd.exe", "/c", cmd);
        }
        // macOS / Linux
        String cmd = String.format("%s --line %d \"%s\"", ideaName, lineNo, filePath);
        return new ProcessBuilder("/bin/bash", "-c", cmd);
    }

    // -------------------------------------------------------------------------
    // 路径标准化
    // -------------------------------------------------------------------------

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        path = path.replace("\\", "/");
        int q = path.indexOf("?");
        if (q != -1) path = path.substring(0, q);
        path = path.replaceAll("/+", "/");
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        return path;
    }

    // -------------------------------------------------------------------------
    // 日志
    // -------------------------------------------------------------------------

    private static void log(String msg) {
        System.out.println("[OpenInIdea] " + msg);
    }
}
