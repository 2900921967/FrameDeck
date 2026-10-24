# FrameDeck · 安卓逐帧播放器

中文原生安卓应用，适合逐帧检查动作、动画和录屏。Android 9（API 28）及以上可安装。

## 1.4.2 更新

- 打开 ZIP 播放列表时，自动定位到当前播放的视频，保留选中高亮，便于从长列表中切换相邻视频。
- 使用列表原生的条目定位，不按固定行高计算滚动距离；没有当前选中项时仍从列表开头显示。
- 此次发布的 APK 沿用上一版演示签名，可覆盖安装 1.4.1 演示版。

## 1.4.1 更新

- 修复竖屏状态打开横向视频后自动切换到横屏的问题。打开普通视频、选择 ZIP 内视频或切换播放列表条目时，加载完成会保持当前屏幕方向。
- 底部「横屏 / 竖屏」按钮仍可手动切换方向，横屏时按返回仍先回到竖屏。
- 此次发布的 APK 沿用上一版演示签名，可覆盖安装 1.4.0 演示版。

## 1.4.0 更新

- 文件菜单、播放列表、空状态、缓存管理、准备进度、跳帧输入、播放速度和提示弹窗统一使用深绿色圆角卡片。
- 菜单配有线条图标和功能说明，文件列表选中项使用浅绿色突出显示，按钮统一为圆角样式。
- 长列表与长说明可滚动，弹窗宽度和内容高度会适配屏幕；跳帧输入保留原有输入校验和确定按钮行为。
- 主页中央不再显示文字说明，改为细边框播放器占位区域和播放图标。点击框内可选择文件，视频载入后隐藏边框并保持全屏播放。

1.4.0 与上一版使用相同演示签名，可以直接覆盖安装。

## 1.3.1 更新

修复 ZIP 播放列表文件名排序。列表按文件名中的数字值排序，例如 `1.mp4、2.mp4、3.mp4、10.mp4、11.mp4、100.mp4`；支持中文前缀、前导零、多段编号以及目录名中的数字。比较数字时不转成固定范围整数，因此较长编号也不会溢出。排序针对完整视频条目，选中项仍对应原本的文件位置。

## 1.3.0 更新

- 新增底部「列表」按钮：保留当前 ZIP 的视频目录，切换视频无需重新选择压缩包。列表会标出当前选中的视频。
- 新增「最近压缩包」：记住最近 5 个压缩包，重新进入应用也可直接打开记录。
- 新增「临时文件管理」：显示真实文件占用、视频缓存和压缩包暂存占用、使用中/可清理的数量，以及最近清理结果。
- 支持清理空闲文件；「全部清理」会先停止播放和关闭播放列表，再清理并重新检查剩余文件。
- 下次启动时自动清理没有被使用的遗留临时文件。

安装：1.3.1 与交付的 1.3.0、1.2.0 使用相同演示签名，可以直接覆盖安装。较早的 1.0/1.1 签名不同，如仍在使用这些版本，需先卸载。

## 已实现

- 使用系统文件选择器打开本地视频或 ZIP，无需申请整个相册或存储空间的访问权限。
- 列出 ZIP 内各层文件夹中的视频，兼容 ZIP64、UTF-8 和常见 GB18030 中文文件名。
- 正常播放带声音，支持 0.25×、0.5×、0.75×、1×、1.5×、2×。
- 暂停后上一帧、下一帧；进度条按帧拖动；输入帧号跳转。
- 显示当前帧号、总帧数和毫秒时间戳；帧号从 1 开始。
- 将当前帧导出为原始解码尺寸的 PNG，保存位置由系统选择器指定。
- 全屏沉浸播放，视频按原比例适配屏幕，控制栏悬浮在底部。
- 轻触画面显示或隐藏控制栏；播放时 3 秒后自动隐藏，暂停时保留控制栏。
- 横屏使用一行紧凑按钮，竖屏使用两行按钮；视频加载后保持当前方向，可通过底部按钮手动切换。
- 外接键盘：左右方向键逐帧、空格播放/暂停。
- 切到后台或失去音频焦点时暂停，重新进入时不自动播放。

## 使用

安装 APK 后点击底部「打开」，再选择「打开视频」或「打开 ZIP 压缩包」。ZIP 会先显示视频列表，点选其中一个即可进入播放。打开后先显示第一帧，点击播放可正常观看；暂停后点击「上一帧」「下一帧」查看相邻帧。点击「跳帧」可输入指定帧号。「截图」会导出画面中当前已完成解码的帧。

视频始终占据整个播放区域，控制栏不会挤压或缩小画面。轻触视频区域可显示或隐藏底部控制栏；播放时控制栏会在 3 秒后自动隐藏，暂停后保持显示，方便连续逐帧查看。底部「横屏 / 竖屏」按钮可切换方向，横屏时按返回先回到竖屏。系统导航栏可从屏幕边缘滑动暂时唤出。首次打开长视频需要扫描轨道，建立帧索引。

推荐使用常规 H.264 MP4。其他容器及 HEVC 等编码的支持取决于设备系统解码器以及其是否提供按帧索引读取能力。应用会拒绝缺失帧数、帧数与轨道样本数不符、重复时间戳、DRM 或无法解码的视频。当前版本不包含 FFmpeg 转码、流媒体、字幕和逐帧音频预览。

## 播放列表与清理入口

打开一个 ZIP 后，点底部「列表」即可再次打开它的视频列表，不会再弹系统文件选择器。即使切换去播放普通视频，该 ZIP 列表也会保留到打开另一个 ZIP、全部清理或退出播放界面。当前选中项有单选标记，失败或取消视频准备不会丢失列表。

「打开 → 最近压缩包」可访问最近 5 个记录。记录保存的是文件访问位置，不是额外的视频副本。应用重启后，点击记录会重新读取目录；如果原文件被移动、删除或原有访问权限失效，仍需要重新选择文件。

「打开 → 临时文件管理」会重新扫描播放器使用的临时目录：

- 「使用中」包括当前视频、准备中的文件，以及为了复用播放列表而保留的 ZIP 暂存副本。
- 「清理空闲」保留使用中内容，可以在不关闭当前列表的情况下删除遗留文件。
- 「全部清理」会提示停止播放并关闭暂存列表。执行后重新检查文件，只有确认没有剩余文件时才显示「0 B」。
- 原视频、原压缩包和已导出的 PNG 均不在临时文件清理范围内。
- 删除失败会显示失败数量和剩余占用，下次启动也会尝试清理空闲遗留文件。

## ZIP 读取方式和范围

- ZIP 中的 STORED（仅存储、未压缩）视频：对可随机读取的本地文件直接使用文件描述符、起始偏移和长度，不生成单独的视频副本。
- ZIP 中的 DEFLATE 视频：只临时展开所选视频，完成后可正常跳转和逐帧；不会展开整个压缩包。准备过程中可取消，并检查长度与 CRC。
- 某些云盘或文件提供器只能顺序读取：会先缓存 ZIP，再处理所选条目，因此可能需要额外空间和等待。
- 临时视频在切换视频或关闭播放界面后释放。为复用播放列表而需要的 ZIP 暂存副本，会保留到更换压缩包、关闭界面或执行全部清理。应用被强制终止时可能留下文件，下次启动会清理空闲遗留项，也可在临时文件管理中手动清理。
- 当前不支持 RAR、7z、分卷、带密码的 ZIP、嵌套压缩包和其他压缩算法。ZIP 内视频仍需满足手机的解码和帧索引要求，推荐 H.264 MP4。
- 超大目录限制为 10 万个条目、最多 2000 个视频；准备所选视频前检查可用临时空间。

## 用 Android Studio 构建

1. 解压后，用 Android Studio 打开包含 `settings.gradle` 的 `FrameDeck` 文件夹。
2. 使用 JDK 17，在 SDK Manager 安装 Android SDK Platform 35 和 Build Tools 35.0.0。
3. 等待 Gradle 同步，连接安卓手机运行；也可执行下面的构建命令。

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug lintDebug
```

macOS / Linux：

```bash
chmod +x gradlew
./gradlew assembleDebug lintDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。第一次同步需要联网下载 Gradle 和安卓构建插件；应用运行时不需要网络，也未声明网络权限。

构建版本固定为 Android Gradle Plugin 8.9.2、Gradle 8.11.1、Java 17。GitHub 源码不包含签名密钥。默认构建使用本机自动生成的 Android 调试签名；因此自己构建的 APK 通常不能直接覆盖此前下载的演示 APK。若你持有自己的原签名文件，可以将其保存在已被 Git 忽略的本机 `signing/` 目录，并配置本机签名。正式分发请使用自己的发布签名。

## 实现说明

`FrameSource` 使用 `MediaExtractor` 扫描视频轨道的实际显示时间戳，再按显示顺序建立 `FrameTimeline`。每次暂停定位，通过 `MediaMetadataRetriever.getFrameAtIndex()` 读取指定帧，不使用固定的 `1000 / fps` 时间跳转来替代逐帧。

按帧显示和正常播放使用两个通路：暂停时显示解码得到的 Bitmap，连续播放使用 `MediaPlayer` 和 `TextureView`。播放中的帧号由系统播放位置推算，界面每 70 ms 刷新；暂停后才重新读取指定帧，因此播放中的帧号不应作为逐帧测量依据。系统播放器恢复播放使用毫秒级定位，精确逐帧查看和导出以暂停画面为准。

扫描和取帧在单独工作线程运行。快速拖动时只显示最后一次请求的结果；更换视频和销毁界面会使旧任务失效。PNG 导出持有当时显示的 Bitmap，防止保存选择器返回后错存其他帧。

代码入口：

| 文件 | 职责 |
| --- | --- |
| `MainActivity.java` | 界面、播放、文件打开、PNG 导出和生命周期 |
| `AppDialog.java` | 圆角弹窗、图标菜单、选中行和滚动内容 |
| `PlayerIcon.java` | 统一风格的矢量图标 |
| `EmptyPlayerView.java` | 主页无文字播放器占位框 |
| `FrameSource.java` | 轨道扫描与按帧解码 |
| `ZipArchive.java` | ZIP/ZIP64 目录、视频区间和 DEFLATE 缓存 |
| `NaturalFileOrder.java` | 文件名按数字值自然排序 |
| `ArchiveSession.java` | 安卓文件提供器、可随机读取的文件描述符和临时文件 |
| `MediaInput.java` | 将普通视频或 ZIP 视频区间交给三个媒体 API |
| `SharedResource.java` | 使播放列表与各次播放安全共享同一个压缩包 |
| `TempStore.java` | 活跃临时文件保护、占用扫描和清理结果 |
| `FrameTimeline.java` | 显示时间戳排序、帧定位和时间格式化 |
| `tests/FrameTimelineTest.java` | 可变帧率、B 帧顺序、首尾边界等回归检查 |
| `tests/ZipArchiveTest.java` | ZIP 读取、中文、ZIP64、CRC、取消清理等 32 项检查 |
| `tests/CacheLifecycleTest.java` | 切换时保留 ZIP、旧缓存释放、删除失败与并发关闭等 25 项检查 |
| `tests/NaturalFileOrderTest.java` | 1—116 编号、前导零、多段编号、中文及条目关联等排序检查 |

## 验证

构建和静态检查记录见 `VALIDATION.md`。当前交付未连接安卓手机进行运行验证。

纯 Java 时间轴检查可使用 JDK 17 在项目根目录运行：

```bash
javac -encoding UTF-8 -d test-out app/src/main/java/com/ruih/framedeck/FrameTimeline.java tests/FrameTimelineTest.java
java -cp test-out com.ruih.framedeck.FrameTimelineTest
```

ZIP 回归检查（需要 Python 3 和 JDK 17）：

```bash
python tests/make_zip_fixtures.py test-fixtures
javac -encoding UTF-8 -d test-out app/src/main/java/com/ruih/framedeck/TempStore.java app/src/main/java/com/ruih/framedeck/ZipArchive.java tests/ZipArchiveTest.java
java -cp test-out com.ruih.framedeck.ZipArchiveTest test-fixtures
```

缓存与列表生命周期检查：

```bash
javac -encoding UTF-8 -d test-out app/src/main/java/com/ruih/framedeck/SharedResource.java app/src/main/java/com/ruih/framedeck/TempStore.java tests/CacheLifecycleTest.java
java -cp test-out com.ruih.framedeck.CacheLifecycleTest
```

文件名排序检查：

```bash
javac -encoding UTF-8 -d test-out app/src/main/java/com/ruih/framedeck/TempStore.java app/src/main/java/com/ruih/framedeck/ZipArchive.java app/src/main/java/com/ruih/framedeck/NaturalFileOrder.java tests/NaturalFileOrderTest.java
java -cp test-out com.ruih.framedeck.NaturalFileOrderTest
```

建议在手机上用带烧录帧号的测试片检查：连续点击下一帧，画面帧号应依次加一；点击上一帧应减一；跳到第一帧与最后一帧，检查边界；导出 PNG 并比对帧号；随后检查横竖屏、变速、接电话暂停及重新打开另一视频。

## 官方 API 参考

- [按帧序号读取视频帧](https://developer.android.com/reference/android/media/MediaMetadataRetriever#getFrameAtIndex(int))
- [视频轨道和时间戳](https://developer.android.com/reference/android/media/MediaExtractor)
- [系统播放器](https://developer.android.com/reference/android/media/MediaPlayer)
- [Android Gradle Plugin 8.9 兼容要求](https://developer.android.com/build/releases/agp-8-9-0-release-notes)

- [Android 沉浸模式](https://developer.android.com/develop/ui/views/layout/immersive)

- [安卓应用专用缓存](https://developer.android.com/training/data-storage/app-specific)
- [文件访问与持久授权](https://developer.android.com/training/data-storage/shared/documents-files)

- [安卓自定义弹窗](https://developer.android.com/develop/ui/views/components/dialogs)
