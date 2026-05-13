# Open In Idea

根据 HTTP 请求 URL 定位 Spring Controller 源码行，并在 IntelliJ IDEA 中自动打开。

纯 Java 库，无 Spring 或任何框架依赖，适用于所有基于注解的 Spring MVC 项目。

## 功能特性
- **配合使用Chrome 扩展：https://github.com/jiafuxiaoqu/spring-boot-open-in-ide-extension
- **URL 到源码自动定位**：输入 HTTP 请求 URL，自动扫描源码找到对应的 Controller 方法和行号
- **IDEA 一键打开**：直接在 IDEA 中打开匹配的源码文件并跳转到对应行
- **Ant 风格路径匹配**：支持 `{变量}` 和 `**` 通配符等 Ant 风格路径
- **多模块项目支持**：可指定多个源码根目录，适用于多模块 Maven/Gradle 项目
- **缓存机制**：首次扫描后缓存结果，后续请求秒级响应
- **零框架依赖**：仅依赖 JavaParser 解析 Java 源码，不依赖 Spring 或任何 Web 框架

## 安装

### Maven 两个仓库必须引入

```xml
 <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
 </repositories>
```
```xml
<dependency>
    <groupId>io.github.jiafuxiaoqu</groupId>
    <artifactId>open-in-idea</artifactId>
    <version>1.0.0</version>
</dependency>
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
### springboot 集成
```java
package com.ruoyi.gateway;

import com.ruoyi.common.core.web.domain.AjaxResult;
import io.github.jiafuxiaoqu.openinidea.OpenInIdeaRequest;
import io.github.jiafuxiaoqu.openinidea.OpenInIdeaService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class OpenInIdeController {
@PostMapping("/__open_in_idea")
public AjaxResult openInIdea(@RequestBody Map<String, String> payload) {
    String requestUrl = payload.get("requestUrl");
    String httpMethod = payload.get("method");
    String ideaDir = payload.get("ideaDir");
    String ideaName = payload.get("ideaName");
    OpenInIdeaService service = OpenInIdeaService.getInstance();
    OpenInIdeaRequest openInIdeaRequest  = new OpenInIdeaRequest();
    openInIdeaRequest.requestUrl(requestUrl)
        .method(httpMethod)
        .ideaDir(ideaDir)
        .ideaName(ideaName);
    service.open(openInIdeaRequest);
    return  AjaxResult.success();
    }
}

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



## 许可证

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)