# 1.4.1 验证记录

验证日期：2026-10-25（北京时间）。

- `assembleDebug lintDebug`：通过；Android Lint 0 个错误，17 个现有警告。
- 包名 `com.ruih.framedeck`，`versionCode=7`，`versionName=1.4.1`。
- 移除 `openMedia` 加载完成回调中根据视频宽高强制请求横屏的逻辑。普通视频与 ZIP 内视频（含播放列表切换）共用该入口，加载完成时不主动修改屏幕方向。
- 代码路径检查：`setRequestedOrientation` 仅保留在手动横竖屏按钮和横屏返回操作中；未新增固定竖屏限制。
- APK v2 签名与 ZIP 对齐校验：通过；签名证书 SHA-256 与 1.4.0 一致，可覆盖安装。
- 本次未连接安卓手机或模拟器，尚未完成运行时的方向切换验证。
- 构建：AGP 8.9.2 / Gradle 8.11.1 / JDK 17 / Android SDK 35。
- GitHub 仅提交源码、版本和文档更新，签名密钥及构建产物不进入源码仓库。

APK SHA-256：`3a7b93deba40a8886a8c40700b058193dd0c2baa7ea58cd936fde0e9ed89056f`

---

# 1.4.0 GitHub 源码构建检查

- 源码对应 FrameDeck 1.4.0。
- 本机 `assembleDebug` 构建通过；检查时未提供演示签名文件，使用 Android 本机调试签名。
- 上传内容排除了签名密钥、机器配置、缓存、日志和构建产物。Gradle Wrapper 保留在仓库中。
- 克隆后的默认调试签名通常与此前提供的演示 APK 不同；不能据此假定可以覆盖演示 APK。
- 下方保留原交付 APK 的历史验证记录，其中签名比对仅适用于此前交付的 APK。

---

# 1.4.0 验证记录

验证日期：2026-10-24（北京时间）。

- `assembleDebug lintDebug`：通过，Android Lint 0 个错误，17 个警告。
- APK v2 签名及 ZIP 对齐校验：通过，签名与上一版相同，可覆盖安装。
- 包名 `com.ruih.framedeck`，`versionCode=6`，`versionName=1.4.0`。
- 本次为原生界面样式修改，未新增镜像式界面单元测试；ZIP、数字排序、逐帧和缓存逻辑沿用已有实现。
- 设计检查：主页占位框由 Canvas 绘制，不含可见文字；文件菜单使用图标和说明；播放列表显示选中行与单选标记；长内容有滚动容器；按钮保留原生 AlertDialog 接口，以支持跳帧输入校验和准备任务取消。
- Android 9 模拟器在当前环境未成功启动，未取得可用运行截图，也未完成模拟器或实机界面验证。

新增组件：AppDialog、PlayerIcon、EmptyPlayerView。主页框在文件加载后隐藏，播放区域仍保持全屏。准备任务的动态提示通过自定义消息视图更新。

构建：AGP 8.9.2 / Gradle 8.11.1 / JDK 17 / Android SDK 35。

APK SHA-256：`aa33dfed4fbf41181f884acc15896f3a0a0bf2eb8b3700c09ad02285a7cd45a3`
