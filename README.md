# 一周 · Weekday

Java + XML + Android 原生控件开发的个人课程表，Android 8.0（API 26）及以上可安装。课程、学期和学校保存在 Room / SQLite，外观保存在本机偏好设置，无需账号。学校网站浏览和翻译需要网络，课表查看、编辑及手动考试安排可离线使用。

## 安装与第一次使用

当前版本 **1.3.3**，构建后的安装文件为 [dist/weekday-release.apk](dist/weekday-release.apk)，版本清单为 [version.json](version.json)。这是使用固定 release 签名的自用 APK。构建脚本也会在 `dist/version.json` 生成包含 APK SHA-256 和 GitHub Releases 下载地址的发布清单。

1. 在手机上打开 APK，按系统提示允许该文件来源安装。
2. 打开「设置 → 当前课表 → 编辑当前课表」，填写课表名称、开学日期及 1–20 周的总周数。界面会显示开学日期所在周的周一，供确认。
3. 打开「作息方案」，设置每节课的时间，或新建夏季、冬季等方案。
4. 点击右上角「＋ 添加」录入课程；也可在「设置 → 导入课程」选择表格。

首次启动课表为空，不会把模板中的示例课程当作个人课程保存。卸载会删除 App 本地数据，换机前请从 App 导出完整备份。

以下为 Android 原生控件在主机上的渲染预览，使用隔离的示例数据；真机验收状态见 [验证记录](docs/VALIDATION.md)。

![首页示例预览](docs/previews/home-native.png)

## 首版功能

课程块文字按列宽自动换行，不限制固定行数，向下显示到当前课程块的底部边界。显示字号在外观设置值的基础上减小 1sp（最低 8sp），优先展示课程名和教室。

| 范围 | 使用方式 |
| --- | --- |
| 学期、日期和周次 | 管理多个独立课表，每个课表最多 20 周；课表之间可切换；开学日期所在周的周一为第 1 周。开学前、结束后有提示。 |
| 当前周校准 | 以本周周一为校准锚点，之后自动递增；支持恢复开学日期计算。校准后显示日期与校准周对应。 |
| 查看课表 | 左右滑动、上一周/下一周、点击顶部周次选择、「本周」返回；显示日期范围及各列日期，突出今天。 |
| 作息方案 | 多套作息，每天 1–24 节，按上午/下午/晚上配置；相邻节次之间的时间差就是休息间隔。课程关联节次，切换后时间自动更新。 |
| 课程 | 名称、教师、教室、备注、星期、起止节次、每周/单周/双周/指定周、颜色；添加、编辑、复制、删除。 |
| 临时调整 | 点击课程 → 更多操作，只对所选周取消、调课或换教室，也可恢复原安排；其他周保持原始周期。 |
| 冲突 | 手动录入、临时调整和批量导入时提示；可确认保存。重叠课块点击展开查看。 |
| 连续课块 | 名称、教师、教室、适用周次、颜色、备注相同且节次连续时合并展示。底层安排仍可在「课程」页分别编辑。 |
| 显示模式 | 所选周 / 整学期切换；整学期会反映临时调整并标注实际适用周次；支持紧凑、完整两种显示模式，以及五天/七天和教师可选显示。课程块固定显示课程名和地点，地点按列宽自动换行，不显示上课时间。紧凑模式按屏幕高度与节次数自动统一行高，当前作息可在一屏内显示。 |
| 课表缩放与收纳 | 星期/日期栏固定在课程体上方，课表区域支持双指缩放和横向/纵向滚动；主页左上角与当前周栏保持固定，课程范围仍收纳在顶部菜单中；课程显示模式与课程块大小合并到设置中的同一入口，课程块大小滑块范围为 100%–200%，紧凑、完整分别使用不可修改的 100%、135% 静态值，选择模式会同步写入滑块。周次切换动画可在设置中选择跟手并排、滚筒或淡入淡出；动画进度由手指滑动距离实时控制，跟手并排和滚筒会在拖动中同时显示前后两周内容，达到 40dp 阈值后松手自动完成剩余动画，未达到阈值回弹。课程块显示课程名和地点，教师可在设置中开启。 |
| 表格导入 | XLSX / UTF-8 CSV，字段对应、错误行检查和重复检查；导入时选择新建课表或导入当前课表。首页“切换课表”下拉框可直接切换，也可从设置管理课表。 |
| 学校管理 | 名称、简称、关键词、教务网址的新增/编辑/删除/本地搜索，App 内 WebView 登录、手动验证码、下载表格、清除登录状态。内置湖北经济学院正方教务适配，可在个人课表查询后“一键读取课程”，读取后选择新建课表或导入当前课表。 |
| 课程提醒 | 可选择是否开启、提前时间和通知权限；课程提醒会跟随取消、调课、作息和周次校准更新，系统不支持精确闹钟时使用可用的替代调度。 |
| 考试安排 | 按学期添加考试日期、时间、考场、备注和独立提醒；支持筛选、编辑、删除和时间重叠提示。 |
| 中英翻译 | 翻译页支持中文 → English、English → 中文，输入内容后在线翻译，可交换方向、清空和复制结果。默认使用 MyMemory，失败时自动尝试 Google；长文本按服务字节限制分段，每次最多 4000 个字符。提供网络检查、网络设置和应用联网设置入口。 |
| 界面与导航 | 底部导航提供课表、课程、考试、教务、翻译和设置六个入口；当前页面使用图标高亮和浅色胶囊背景，按钮提供点击反馈。课表顶部保留左上角和当前周，其他选项由菜单图标展开。翻译页区分主操作、次操作、结果区和网络设置入口。 |
| 外观 | 晨光、晴空、森林、星夜、山岚、纸页、暮色湖畔，以及春日、夏日、秋色、冬霜四季背景；主题支持跟随系统、浅色和深色。相册图片可拖动裁剪、双指缩放，并复制到本地。支持亮度、模糊、遮罩、课块透明度及文字大小。背景记录见 [docs/backgrounds](docs/backgrounds/README.md)。 |
| 备份 | 完整 JSON 导出/恢复，包含所有学期、课程、临时调整、作息、学校、外观和自定义背景。每次导入/恢复前保存自动快照，保留最近 8 份。 |

湖北经济学院的自动读取需要用户在 WebView 内自行登录并完成验证码，打开“个人课表”后选择正确学期并点击查询，再点击“一键读取课程”。读取后选择新建课表或导入当前课表。App 只读取网站返回的课程字段，不读取或保存账号密码。其他学校仍提供通用 WebView 和表格导入，自动读取需要后续单独适配。网站 Cookie、缓存不写入课表备份。中英翻译需要网络，点击翻译时将输入内容发往翻译服务，App 不将译文写入课表。Android 的 INTERNET 与 ACCESS_NETWORK_STATE 权限安装时自动授予，不存在运行时申请弹窗；手机厂商的应用联网限制需在系统设置中允许。免费翻译服务可能限流，不保证所有手机网络均可达。桌面组件和云同步暂不在首版内。

## 表格模板与规则

可直接使用 [CSV 模板](app/src/main/assets/template.csv) 或 [XLSX 模板](app/src/main/assets/template.xlsx)，App 内也能导出这两种模板。

| 字段 | 示例 | 规则 |
| --- | --- | --- |
| 课程名称 | 高等数学 | 必填 |
| 星期 | 周一 | 必填，接受 1–7 或 周一–周日 |
| 开始节次 | 1 | 必填正整数 |
| 结束节次 | 2 | 不小于开始节次，不超过当前作息的节数 |
| 周次 | 每周 / 单周 / 双周 / 1-8,10 | 可选，默认每周，不能超过当前学期周数；含逗号时 CSV 应加引号 |
| 教师、教室、备注 | 王老师、A101 | 可选 |
| 颜色 | #B9D9C1 | 可选，同名课默认沿用已有课程颜色 |

表头不同可在导入时手动对应。导入时选择新建课表或当前课表；重复按课程名称、教师、教室、备注、星期、起止节次及周次判断并跳过。新建课表沿用当前课表的开学日期、总周数和周次校准，并复制作息；可在设置中修改。名称重复时自动编号，导入完成后自动切换到新课表。导入到当前课表会追加课程。错误行不导入，时间冲突时保留提示。

支持普通 `.xlsx` 的首工作表，包含共享字符串、内联字符串和稀疏单元格；不读取旧版 `.xls` 或图片课表，不执行公式或宏。CSV 必须为 UTF-8，支持引号、引号内逗号及换行。单文件上限 12 MB，最多 5000 行课程、128 列，XLSX 解压上限 32 MB。背景原图上限 20 MB，裁剪保存为 JPEG；备份恢复上限 24 MB。

## 开发与构建

在 Android Studio 中打开**本目录**并同步 Gradle。使用内置 JDK；如果 IDE 仍显示最初的控制台模块，可选择「Link Gradle Project」并指定根 `settings.gradle`。原来的 `src/Main.java` 已保留，不参与 APK 构建。

当前配置：Gradle 9.3.1、Android Gradle Plugin 9.1.0、compileSdk 37、targetSdk 35、Build Tools 36.0.0、Java 17 源码、Room 2.8.4、Apache Commons CSV 1.14.1。需要 JDK 17–25 运行 Gradle，建议使用 Android Studio 内置 JDK。`local.properties` 是本机 SDK 路径，不应提交到版本控制。

PowerShell 一键构建：

```powershell
.\tools\build.ps1
# 其他机器可以显式指定安装位置：
.\tools\build.ps1 -JavaHome 'C:\Program Files\Android\Android Studio\jbr' -AndroidSdk 'C:\Users\你的用户名\AppData\Local\Android\Sdk'
```

如果 Windows PowerShell 的执行策略或脚本编码阻止了上述命令，可使用 ASCII 备用入口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\build-android.ps1
```

脚本执行 release APK 打包、Android Lint 和全部单元测试，并复制产物到 `dist/weekday-release.apk`。首次执行需要联网下载 Gradle 与依赖；Wrapper 使用腾讯 Gradle 镜像，校验分发包 SHA-256。测试运行库使用 Maven 镜像下载，可在 `app/build.gradle` 中调整 Robolectric 下载地址。

Release 签名密钥保存在本机的 `.android-user/weekday-release.jks`，密码配置保存在被忽略的 `keystore.properties`，两者都不能上传到 GitHub。迁移开发环境或继续发布更新时必须同时备份这两个文件；丢失密钥后无法覆盖更新已安装的 release APK。当前 debug APK 与 release APK 签名不同，首次切换需要卸载旧 debug APK，之后 release 更新可直接覆盖安装。

标准命令也可使用：

```powershell
.\gradlew.bat assembleRelease lintRelease testDebugUnitTest
```

本机 SDK 37 比 AGP 9.1 声明测试过的 36.1 新，构建可能显示版本提示。未隐藏该提示。项目未使用 Android 17 专用业务 API；支持设备以 `minSdk 26` 为准。

## 代码与验证

- [CalendarEngine.java](app/src/main/java/com/local/weekday/CalendarEngine.java)：日期、周次、临时调整、冲突、连续合并和作息校验。
- [Models.java](app/src/main/java/com/local/weekday/Models.java)、[AppDatabase.java](app/src/main/java/com/local/weekday/AppDatabase.java)：Room 实体与 DAO。
- [Repository.java](app/src/main/java/com/local/weekday/Repository.java)：后台数据库访问、完整备份验证、事务恢复和自动快照。
- [Importer.java](app/src/main/java/com/local/weekday/Importer.java)、[ImportPlanner.java](app/src/main/java/com/local/weekday/ImportPlanner.java)：CSV / XLSX 解析、去重与批量冲突检查。
- [MainActivity.java](app/src/main/java/com/local/weekday/MainActivity.java)：XML 主布局与原生课表、表单和设置界面。
- [res/drawable](app/src/main/res/drawable)：底部导航图标和原生控件的本地视觉资源。
- [CropActivity.java](app/src/main/java/com/local/weekday/CropActivity.java)、[BackdropView.java](app/src/main/java/com/local/weekday/BackdropView.java)：图片裁剪、本地背景与外观。
- [WebActivity.java](app/src/main/java/com/local/weekday/WebActivity.java)、[SchoolImporter.java](app/src/main/java/com/local/weekday/SchoolImporter.java)：教务网站浏览、湖北经济学院课程读取、字段校验和导入。
- [TranslationService.java](app/src/main/java/com/local/weekday/TranslationService.java)：中英在线翻译请求和响应解析。
- [ReminderPlanner.java](app/src/main/java/com/local/weekday/ReminderPlanner.java)、[ReminderScheduler.java](app/src/main/java/com/local/weekday/ReminderScheduler.java)：课程和考试提醒的计算、系统闹钟与通知。
- [测试目录](app/src/test/java/com/local/weekday)：日历规则、临时调整、导入边界和 Room 备份恢复的回归测试。

详细验收记录见 [docs/VALIDATION.md](docs/VALIDATION.md)。主机测试、Android 静态检查与真机验证分别记录；未将主机测试当作实际手机测试。
