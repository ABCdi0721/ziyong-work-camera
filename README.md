# 自用-工作相机（WorkCamera）

一个自用的 Android 拍照工具：拍照后自动把**拍摄时间 + 可读地址**烧录进照片，相册只显示本机拍的照片，按日期区间筛选、按地点搜索，支持多选删除与分享。

- 包名：`com.workcamera.app`
- 版本：1.0.4（versionCode 5）
- 技术：Kotlin + CameraX + 原生 View 体系
- 兼容：Android 8.0（API 26）及以上，targetSdk 34；已在 Redmi（HyperOS / Android 17）真机实测

## 功能特性

### 基础相机
- 全屏取景预览，中间白色圆环为快门
- 闪光灯三态切换（自动 / 开 / 关）
- 九宫格构图辅助线开关
- 双指捏合变焦、点击画面对焦（带对焦圈反馈）
- 前后摄像头切换
- 长按快门 = 直接调起系统相机拍一张，返回后同样加水印并存入本应用目录

### 自动时间地点水印
- 拍照后自动在照片左下角烧录：`2026.11.13 周五 22:35` + `云和县小徐云亭小区`
- 地址为可读文字（县/区 + 街道/小区/POI），**不写经纬度**
- 地址获取三层兜底：系统 Geocoder → 在线 Photon（OpenStreetMap）→ 长按顶部定位胶囊手动固定地点
- 水印随照片分辨率等比缩放，不遮挡画面主体

### 工作相册
- 只显示本应用拍的照片：按 `Download/工作相机/` 目录 + `WCAM_` 文件名前缀双重过滤，不会混入系统相机、微信等图片
- 3 列网格缩略图，显示拍摄地点与时间
- **按日期区间筛选**：自绘区间日历，可分别选择开始/结束日期，支持「今日」「清除」
- **按地点搜索**：搜索框输入关键词（如「丽水」「云和」），并给出相册中出现过的地点标签一键筛选
- 点击缩略图进入大图：顶部信息卡显示时间、地点、文件路径；底部「分享 / 删除 / 确定」
- **长按缩略图进入多选**：可单选或多选，底部「已选 N 张」「分享」「删除」；批量删除前二次确认，批量分享可一次发多张
- 大图页与多选均支持分享（FileProvider + 缓存副本，不改动原图）

### 设置与缓存清理
- 相机界面顶部设置按钮：显示当前缓存占用、照片保存位置、版本号
- 「立即清理」只清 App 缓存（缩略图、拍照临时文件、分享副本），**不删除照片**

### 小米 / Redmi 机型适配
- 采集模式改用高质量单帧（`CAPTURE_MODE_MAXIMIZE_QUALITY`），规避部分小米 HAL 返回空帧
- 小米机型显式限定 4:3 采集比例
- 取景改用兼容渲染（TextureView），规避 SurfaceView 与相机 HAL 配合异常
- 拍照前等待取景真正出帧（最多 2.5s），避免首张黑图
- 黑图守卫：成片后做亮度检测，判黑则不写入相册，自动重拍一次；仍失败则提示改用系统相机

## 照片保存位置

所有照片保存在手机内部存储：

```
Download/工作相机/WCAM_20261113_223546_826.jpg
```

文件管理里即「下载 → 工作相机」，电脑连数据线是 `内部存储/Download/工作相机`。文件名格式 `WCAM_yyyyMMdd_HHmmss_xxx.jpg`。

## 构建

环境要求：JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.9。

```bash
# 1) 准备签名配置（keystore.properties 已被 .gitignore 排除，不会提交）
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入自己的 jks 路径与口令

# 2) 单元测试 + 打包 release
export JAVA_HOME=/path/to/jdk-17
gradle -p . testReleaseUnitTest assembleRelease --no-daemon

# 产物
app/build/outputs/apk/release/app-release.apk
```

未提供 `keystore.properties` 时，`release` 变体不会签名（可正常编译，但产物不能直接安装为正式包）。

单元测试覆盖地址格式化逻辑（行政区 + 细节拼装、精度打分、有效性判断），共 11 条：

```bash
gradle -p . testReleaseUnitTest
```

## 技术栈与引用的开源仓库

| 依赖 / 数据源 | 用途 | 仓库 |
| --- | --- | --- |
| CameraX（camera-core / camera-camera2 / camera-lifecycle / camera-view 1.3.4） | 相机预览、拍照、对焦、变焦 | [androidx/androidx](https://github.com/androidx/androidx) |
| AndroidX Core KTX / AppCompat / RecyclerView / ConstraintLayout / ExifInterface | 基础组件、列表、EXIF 方向读取 | [androidx/androidx](https://github.com/androidx/androidx) |
| Material Components for Android 1.12.0 | 控件与主题 | [material-components/material-components-android](https://github.com/material-components/material-components-android) |
| Photon（komoot） | 在线反向地理编码，把经纬度换成街道/小区名 | [komoot/photon](https://github.com/komoot/photon) |
| OpenStreetMap 数据 | Photon 的地理数据来源，地址信息版权归 OSM 贡献者 | [openstreetmap/openstreetmap-website](https://github.com/openstreetmap/openstreetmap-website)、[osmcode/libosmium](https://github.com/osmcode/libosmium) |
| JUnit 4.13.2 | 单元测试 | [junit-team/junit4](https://github.com/junit-team/junit4) |

> 地址查询接口为 `https://photon.komoot.io/reverse`，无需 API Key；地图数据 © OpenStreetMap contributors，遵循 ODbL 许可。

## 目录结构

```
app/src/main/java/com/workcamera/app/
├── MainActivity.kt        # 相机主界面：预览/拍照/闪光/变焦/对焦/设置/系统相机兜底
├── ImageUtils.kt          # EXIF 方向转正、亮度检测、采样
├── WatermarkRenderer.kt   # 时间 + 地址水印绘制
├── LocationHelper.kt      # 定位与系统地址库，手动固定地点
├── AddressFormatter.kt    # 地址拼接与精度打分（纯逻辑，有单测）
├── RemoteGeocoder.kt      # Photon 在线反查兜底
├── PhotoSaver.kt          # MediaStore.Downloads 写入
├── PhotoRepository.kt     # 只读本应用照片的查询/删除
├── GalleryActivity.kt     # 相册：日期筛选、地点搜索、多选删除/分享
├── PhotoAdapter.kt        # 3 列网格 + 选中态
├── PhotoSharer.kt         # 缓存副本 + FileProvider 分享
├── PhotoViewerDialog.kt   # 大图查看（顶部信息卡 / 分享 / 删除）
├── DateFilterDialog.kt / RangeCalendarView.kt  # 区间日历
├── CacheCleaner.kt        # 只清缓存目录，不碰照片
├── GlyphView.kt           # 自绘图标（闪光/网格/相册/分享/设置等）
└── ...

app/src/test/java/com/workcamera/app/
└── AddressFormatterTest.kt  # 11 条地址格式化单元测试
```

## 版本

| 版本 | 主要变化 |
| --- | --- |
| 1.0.0 | 相机基础功能、水印、相册、日期筛选、地点搜索、固定保存目录 |
| 1.0.1 | 水印改为可读地址（行政区 + 细节），抽出 `AddressFormatter` 并加单测 |
| 1.0.2 | 地址三层兜底（系统 → Photon → 手动固定地点） |
| 1.0.3 | 修复照片全黑（EXIF 旋转矩阵漏平移）、小米机型适配、黑图守卫、系统相机兜底 |
| 1.0.4 | 相册长按多选 + 批量删除/批量分享、大图页分享按钮、设置页一键清理缓存 |

## 已知限制

- 地址精度取决于系统地址库与 OSM 数据，小区级地址可能取不到，此时可用长按定位胶囊手动固定
- 在线地址兜底需要联网，且 Photon 为公共实例，偶有超时
- 未提供 Gradle Wrapper，需本机安装 Gradle 8.9
- 仓库未包含签名证书与构建产物，也不含真机截图

## 许可

本项目为个人自用项目，未声明开源许可证（默认保留所有权利）。如需转载或二次分发，请先联系作者。
