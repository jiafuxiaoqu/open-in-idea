package io.github.jiafuxiaoqu.openinidea;

/**
 * 执行结果。
 *
 * <pre>{@code
 * OpenInIdeaResult result = OpenInIdeaService.open(request);
 * if (result.isSuccess()) {
 *     System.out.println(result.getMessage());
 * } else {
 *     System.err.println(result.getMessage());
 * }
 * }</pre>
 */
public class OpenInIdeaResult {

    private final boolean success;
    private final String  message;
    /** 匹配到的源文件绝对路径，失败时为 null */
    private final String  filePath;
    /** 匹配到的行号，失败时为 -1 */
    private final int     lineNumber;

    private OpenInIdeaResult(boolean success, String message, String filePath, int lineNumber) {
        this.success    = success;
        this.message    = message;
        this.filePath   = filePath;
        this.lineNumber = lineNumber;
    }

    static OpenInIdeaResult success(String filePath, int lineNumber) {
        return new OpenInIdeaResult(true,
                "已打开: " + filePath + ":" + lineNumber,
                filePath, lineNumber);
    }

    static OpenInIdeaResult failure(String message) {
        return new OpenInIdeaResult(false, message, null, -1);
    }

    public boolean isSuccess()   { return success; }
    public String  getMessage()  { return message; }
    public String  getFilePath() { return filePath; }
    public int     getLineNumber(){ return lineNumber; }

    @Override
    public String toString() {
        return (success ? "[OK] " : "[FAIL] ") + message;
    }
}
