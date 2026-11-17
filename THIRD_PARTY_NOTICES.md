# 第三方组件与参考来源

- **AndroidX WebKit 1.12.1**：Apache License 2.0，Gradle 依赖 `androidx.webkit:webkit`。项目与许可见 https://android.googlesource.com/platform/frameworks/support/ 。
- **Jsoup 1.18.3**：MIT，JAR 位于 `app/libs`，保留 [许可证](app/libs/jsoup-LICENSE.txt)。
- **JSON-java 20240303（仅测试）**：本项目随附的 `test/libs/json-LICENSE.txt` 标注 Public Domain；测试库不打包到应用。
- **Gradle Wrapper 8.7**：Gradle 发布组件，Apache License 2.0；保留启动脚本中的版权和许可说明，项目见 https://github.com/gradle/gradle 。
- **Playwright（仅本地浏览器测试）**：Apache License 2.0，作为开发依赖安装，不打包到应用。https://github.com/microsoft/playwright 。

## 学习通功能参考

参考 [led629263224-cmyk/xxt-assistant](https://github.com/led629263224-cmyk/xxt-assistant) 的接口行为、解析规则与提醒设计；最初核对版本为 `9db18d28bf0c8f6e365eb4cd17e5600b308f0512`。本项目使用 Java/Jsoup 实现，未嵌入上游 Python 程序。

2026-11-17 通过 GitHub 公开接口和 README 核对：仓库可访问，但许可证接口返回 404，README 未提供该仓库自身的开源许可证。其 README 提及 Xuexitong-mcp（MIT）作为更早的接口参考，这不自动构成对 xxt-assistant 代码的授权。

因此本材料没有将参考仓库标注为 MIT，也没有用统一许可证替第三方来源作授权。公开发布前应确认原创代码的授权范围；如包含需要授权的上游表达或实现，应取得相应许可或独立替换。致谢不能替代许可证。
