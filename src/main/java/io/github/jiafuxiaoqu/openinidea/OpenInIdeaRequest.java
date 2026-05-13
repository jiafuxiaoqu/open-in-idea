package io.github.jiafuxiaoqu.openinidea;

/**
 * 调用入参，封装定位接口所需的四个参数。
 *
 * <pre>{@code
 * OpenInIdeaRequest request = new OpenInIdeaRequest()
 *         .requestUrl("http://localhost/dev-api/system/user/list")
 *         .method("GET")
 *         .ideaDir("C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/bin")
 *         .ideaName("idea64.exe");
 * }</pre>
 */
public class OpenInIdeaRequest {

    /** 完整请求 URL，含协议 + 域名 + 路径，允许带查询参数 */
    private String requestUrl;

    /** HTTP 方法，如 GET / POST */
    private String method;

    /**
     * IDEA 可执行文件所在目录。
     * <ul>
     *   <li>Windows 必填，用于 {@code cd /d} 切换驱动器</li>
     *   <li>macOS / Linux 可留空，只要 ideaName 在 PATH 中即可</li>
     * </ul>
     */
    private String ideaDir = "";

    /**
     * IDEA 可执行文件名。
     * <ul>
     *   <li>Windows：{@code idea64.exe}</li>
     *   <li>macOS  ：{@code idea}</li>
     *   <li>Linux  ：{@code idea.sh}</li>
     * </ul>
     */
    private String ideaName = "";

    // ---------- 链式 setter ----------

    public OpenInIdeaRequest requestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
        return this;
    }

    public OpenInIdeaRequest method(String method) {
        this.method = method;
        return this;
    }

    public OpenInIdeaRequest ideaDir(String ideaDir) {
        this.ideaDir = ideaDir == null ? "" : ideaDir;
        return this;
    }

    public OpenInIdeaRequest ideaName(String ideaName) {
        this.ideaName = ideaName == null ? "" : ideaName;
        return this;
    }

    // ---------- getter ----------

    public String getRequestUrl() { return requestUrl; }
    public String getMethod()     { return method; }
    public String getIdeaDir()    { return ideaDir; }
    public String getIdeaName()   { return ideaName; }

    // ---------- 校验 ----------

    void validate() {
        if (blank(requestUrl)) {
            throw new IllegalArgumentException("requestUrl 不能为空");
        }
        if (blank(method)) {
            throw new IllegalArgumentException("method 不能为空");
        }
        if (blank(ideaName)) {
            throw new IllegalArgumentException("ideaName 不能为空");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
