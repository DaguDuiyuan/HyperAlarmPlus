# HyperAlarmPlus

> 小米 HyperOS 时钟（`com.android.deskclock`）的 LSPosed 模块：在系统「法定工作日」重复方式的基础上，增加「**法定工作日（含周六）**」——普通周六也响铃，法定放假的周六不响。

## 功能

- 在闹钟 / 就寝（起床）闹钟的重复列表「法定工作日」后面，新增一项「法定工作日（含周六）」（内部类型 `8`，位掩码 `1024`）。
- 响铃规则完全复用系统自带的节假日数据（`HolidayHelper` / `HolidayInstance`）：

  | 日期 | 是否响铃 |
  | --- | --- |
  | 法定工作日（含调休上班的周末） | 响 |
  | 普通周六 | 响 |
  | 法定放假的周六 | 不响 |
  | 其他法定放假日 | 不响 |
  | 周日 | 不响 |

- 普通闹钟与就寝（起床）闹钟都支持；重复列表的摘要、列表项文字、勾选状态、下次响铃时间都会按增强规则显示和计算。
- 与宿主数据解耦：写回系统存储时把 `1024` 折算成系统认识的 `128`（法定工作日），模块用自己的标记（设备加密存储）记住哪些闹钟启用了增强，避免系统界面/数据错乱。

## 支持范围

- 系统：小米时钟 MIUIDeskClock（HyperOS）
- 已验证版本：
  - DeskClock `17.61.0`（versionCode `1305006100`）
  - DeskClock `18.28.0`（versionCode `1306002800`）
  - HyperOS `OS4.0.17.0.XNCCNXM` / Android 17（Xiaomi 14）
- 框架：LSPosed（libxposed API 101+，`minApiVersion=101`、`targetApiVersion=101`）
- 作用域：`com.android.deskclock`
- 模块：minSdk 28 / targetSdk 36 / compileSdk 36

## 安装

1. 安装 APK。
2. 在 LSPosed 中启用模块，作用域勾选「时钟」。
3. 重启手机（或至少强制停止时钟应用）。
4. 打开 时钟 → 闹钟 → 重复，或 就寝 → 重复周期，选择「法定工作日（含周六）」。

> 模块没有独立界面，全部功能通过 Hook 实现。

## 使用说明

- 选中「法定工作日（含周六）」并保存后，模块会记住这个闹钟；就寝/起床闹钟单独记录（ID 为 `Integer.MIN_VALUE`）。
- 想取消增强：把重复方式改回任意系统选项即可。
- 系统存储里仍然保存「法定工作日」，模块在读取时自动升格为「含周六」，在写入时自动折算回「法定工作日」。

## 构建

要求：

- JDK 21（Android Studio 自带 JBR 即可）
- Android SDK（compileSdk 36）
- Gradle 8.14.3（wrapper 已内置）

```powershell
$env:JAVA_HOME="<JDK 21 路径>"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

## 目录结构

```
app/src/main/java/com/dagu/hyperalarmplus/
  XposedInitEntry.java       模块入口，负责按包名分发与分层安装 Hook
  HookEnvironment.java       反射工具、上下文、偏好存储、节假日计算
  AppContextHook.java        记录 DeskClock Application 上下文
  AlarmRuntimeHook.java      Alarm / DaysOfWeek / AlarmHelper 的读写与下次响铃计算
  RepeatAdapterHook.java     重复列表：插入自定义项、标签、勾选位置、点击代理
  RepeatControllerHook.java  RepeatAlarmController / SetAlarmController 的选择同步
  BedtimeHook.java           就寝/起床闹钟：BedtimeUtil、引导页、设置页
  Constants.java             类型、位掩码、偏好键常量
app/src/main/resources/META-INF/xposed/
  java_init.list             入口类声明
  scope.list                 作用域（com.android.deskclock）
  module.prop                libxposed 模块元数据
```

## 实现原理

### 主要 Hook 点

| 目标 | 作用 |
| --- | --- |
| `Alarm(Cursor)` 构造 | 从数据库读出「法定工作日」时，若该闹钟已标记增强则升格为 `1024` |
| `Alarm.DaysOfWeek.getAlarmType / getNextAlarm / toString / getCoded` | 类型识别、下次响铃、摘要文字、写回系统时折算回 `128` |
| `AlarmHelper.addAlarm / setAlarm / addWakeAlarm / setWakeAlarm / deleteAlarmInDb / createContentValues` | 保存/删除时维护模块标记 |
| `AlarmRepeatAdapter.setData / bindOtherViewHolder / setRepeatItemChecked / getItemViewType` | 在重复列表插入「含周六」项、修正标签与勾选位置 |
| `RepeatAlarmController.setLastCheckedItem / getDays` | 弹窗选择同步与读取当前天数 |
| `SetAlarmController.setAlarmRepeatValue / buildAlarmFromUi` | 设置页摘要与保存时保持增强类型 |
| `BedtimeUtil.getWakeDaysOfWeek / getTempWakeRepeat / saveTempWakeAlarm / queryWakeAlarm / ...` | 就寝（起床）闹钟的读取与保存 |
| `BedtimeGuideActivity.initData / transRepeatTypeToIndex` | 就寝引导页的选择器兼容 |
| `BedtimeSettingsFragment.onCreatePreferences` + 提交方法 | 设置页打开时恢复增强显示；弹窗提交后保住增强选择 |

### 数据存储

- 模块偏好：`hyper_alarm_plus`，保存在 **设备加密存储（device-protected storage）**，开机未解锁阶段也能读取。
- 记录启用增强的闹钟 ID 集合；首次使用会自动把旧版本残留在凭据加密存储（CE）里的标记迁移过来。
- 系统自己的存储里始终是「法定工作日」（`128`），模块通过 Hook 在内存中呈现 `1024`。

### 版本兼容

- **18.28.0 就寝设置页重构**：旧版提交入口 `handleBedtimeRepeatResult()` 已被删除，模块会自动探测 `commitRepeatResult()` / `handleRepeatDialogDismiss()` / `commitAndDismissRepeatDialog()`，兼容新旧版本。
- **重复列表勾选位置**：18.28.0 列表新增了「法定节假日」项，模块插入自定义项后会导致系统按固定数组换算的勾选位置错位；模块改为直接在 `dataList` 中按类型定位真实位置，不再依赖系统映射。
- **安装健壮性**：反射初始化失败才终止；各 Hook 类、就寝各子模块分别 try/catch，单个方法缺失不会拖垮整批安装。

## 日志与排错

- LSPosed 日志中过滤 `HyperAlarmPlus`。
- 正常加载：`Hooks installed`。
- 部分失败：`Hooks installed with missing parts`，并伴随 `Failed to install ...` 明细。
- 选择不生效时依次检查：作用域是否勾选「时钟」→ 是否重启过时钟应用 → 系统时钟版本是否在支持范围内。

## 已知限制

- 仅针对小米时钟（MIUI/HyperOS 的 `com.android.deskclock`），AOSP 时钟不适用。
- 依赖系统节假日数据；数据过期时按系统的「法定工作日」行为回退显示。
- 系统时钟升级后若内部方法变动，可能需要更新 Hook 目标。

## 免责声明

本项目仅供学习与技术交流使用，请自行评估风险；因使用本模块造成的任何问题由使用者自行承担。