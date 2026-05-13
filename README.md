# Open In Idea

根据 HTTP 请求 URL 定位 Spring Controller 源码行，并在 IntelliJ IDEA 中自动打开。

纯 Java 库，无 Spring 或任何框架依赖，适用于所有基于注解的 Spring MVC 项目。

## 功能特性

- **URL 到源码自动定位**：输入 HTTP 请求 URL，自动扫描源码找到对应的 Controller 方法和行号
- **IDEA 一键打开**：直接在 IDEA 中打开匹配的源码文件并跳转到对应行
- **Ant 风格路径匹配**：支持 `{变量}` 和 `**` 通配符等 Ant 风格路径
- **多模块项目支持**：可指定多个源码根目录，适用于多模块 Maven/Gradle 项目
- **缓存机制**：首次扫描后缓存结果，后续请求秒级响应
- **零框架依赖**：仅依赖 JavaParser 解析 Java 源码，不依赖 Spring 或任何 Web 框架

## 安装

### Maven

```xml
<dependency>
    <groupId>io.github.jiafuxiaoqu</groupId>
    <artifactId>open-in-idea</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 从源码构建

```bash
cd open-in-idea
mvn clean install
```

## 快速开始

### 基本用法

```java
import io.github.jiafuxiaoqu.openinidea.*;

OpenInIdeaResult result = OpenInIdeaService.getInstance().open(
    new OpenInIdeaRequest()
        .requestUrl("http://localhost/dev-api/system/user/list")
        .method("GET")
        .ideaDir("C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/bin")
        .ideaName("idea64.exe")
);

if (result.isSuccess()) {
    System.out.println("定位成功: " + result.getMessage());
} else {
    System.err.println("定位失败: " + result.getMessage());
}
```

### 多模块项目

```java
OpenInIdeaService service = new OpenInIdeaService(Arrays.asList(
    "C:/work/my-project/ruoyi-system/src/main/java",
    "C:/work/my-project/ruoyi-admin/src/main/java"
));

OpenInIdeaResult result = service.open(
    new OpenInIdeaRequest()
        .requestUrl("http://localhost/dev-api/system/config/selectConfigList")
        .method("GET")
        .ideaDir("C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/bin")
        .ideaName("idea64.exe")
);
```

### 仅查询不打开

```java
String location = service.locate("http://localhost/dev-api/system/user/list");
// 返回 "C:/work/SysUserController.java:88" 或 null
```

## 支持的注解

自动识别以下 Spring MVC 映射注解：

- `@RequestMapping`
- `@GetMapping`
- `@PostMapping`
- `@PutMapping`
- `@DeleteMapping`
- `@PatchMapping`

## 配置说明

| 参数 | 说明 | 示例 |
|------|------|------|
| `requestUrl` | 完整请求 URL 或纯路径 | `http://localhost/api/users/1` |
| `method` | HTTP 方法 | `GET`、`POST` |
| `ideaDir` | IDEA 可执行文件所在目录 | Windows 必填，macOS/Linux 可留空 |
| `ideaName` | IDEA 可执行文件名 | Windows: `idea64.exe`，macOS: `idea`，Linux: `idea.sh` |

### macOS / Linux 示例

```java
OpenInIdeaResult result = OpenInIdeaService.getInstance().open(
    new OpenInIdeaRequest()
        .requestUrl("http://localhost:8080/api/users")
        .method("GET")
        .ideaName("idea") // macOS 需在 PATH 中
);
```

## 工作原理

1. **扫描源码**：遍历指定的源码目录，查找包含 `Controller` 的 Java 文件
2. **解析注解**：使用 JavaParser 解析 Java 源码，提取 `@RequestMapping` 等注解的映射路径
3. **构建缓存**：将映射关系（标准化路径 -> 文件路径:行号）存入内存缓存
4. **路径匹配**：使用自定义算法匹配请求路径与映射路径，支持精确匹配、后缀匹配和 Ant 风格通配符
5. **打开 IDEA**：通过系统命令调用 IDEA 的 `--line` 参数打开文件并跳转到指定行

## 路径匹配优先级

| 优先级 | 匹配类型 | 示例 |
|--------|----------|------|
| 100000+ | 完全匹配 | `/system/user/{userId}` 匹配 `/system/user/123` |
| 90000+ | 后缀匹配 | `/api/**` 匹配 `/api/users/list` |
| 80000+ | Ant 单段通配符 | `/system/user/*` 匹配 `/system/user/123` |
| 70000+ | Ant 多段通配符 | `/api/**` 匹配 `/api/v1/users/list` |

## 许可证

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)