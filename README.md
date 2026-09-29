# 软件商店净化 · heytap-market-clean

OPPO / 欢太 **软件商店**（`com.heytap.market`）的 LSPosed 去广告模块。

拦弹窗广告、筛底栏推广入口、逐项清理「我的」页的推广位。**13 项可独立开关**，
每项独立探针 + 命中计数，失效时如实上报而不是假装成功。

![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Vector v2.2](https://img.shields.io/badge/框架-Vector%20v2.2%20(API%20102)-informational)
![目标版本](https://img.shields.io/badge/验证-26.5.2-9cf)
![版本](https://img.shields.io/badge/发布-v0.3.0-success)

---

## 下载

从 [Releases](https://github.com/2909272751/heytap-market-clean/releases) 下载 **`module-v0.3.0.apk`**。

> `v0.2.0` 的标签在开发中被复用过，先后对应过两个行为不同的二进制，已标记为预发布。
> **请用 v0.3.0。**

## 安装

1. 设备已 root，并装有 LSPosed 系框架（本项目在 **Vector v2.2** 上验证，需 **libxposed API 102**）。
2. 安装 APK。
3. 在框架里启用模块，作用域**只勾** 软件商店（`com.heytap.market`）。
4. **重启设备**（Vector 需要重启才会重新加载模块 dex）。
5. 打开「软件商店净化」，顶部应显示 **LSPosed 配置服务已连接**。

> **改完开关后必须强停目标 App**：设置项走 RemotePreferences，只在新进程里读取。

---

## 功能

### 广告拦截 — 决策闸门，命中一次即不再出现

| 开关 | 对应页面 | 做法 |
|---|---|---|
| 悬浮广告 | 任意页右下角悬浮图标 | 让「是否展示」的判断恒为否 |
| AI 搜索引导气泡 | 首页顶部搜索栏的 AI 图标 | 只置空气泡展示，**AI 搜索入口本身保留可用** |
| 活动弹窗（CTA） | 任意页弹出的活动弹窗 | 置空展示调用，回调一概不碰（避免卡住弹窗流程） |
| 开机必备引导页 | 冷启动后的全屏引导页 | 引导 Intent 构造返回 null |

### 界面

| 开关 | 对应页面 | 做法 |
|---|---|---|
| 底栏推广入口 | 底部导航栏 | **按 tab 文案**筛掉推广项；底栏与其余 tab 全部保留（不会让你切不到「我的」），隐藏项由剩下的自动重排补位。名单可在设置页编辑 |

### 我的页面

| 开关 | 对应页面 | 默认 |
|---|---|---|
| 待更新 | 顶部三宫格左 | 关 |
| 应用卸载 | 顶部三宫格中 | **开** |
| 下载管理 | 顶部三宫格右 | 关 |
| 存储空间清理 | 存储空间清理卡片 | **开** |
| 应用健康状态 | 应用健康状态卡片 | **开** |
| 热门好礼横幅 | 热门好礼免费领横幅 | **开** |
| 「继续探索」推荐卡 | 推荐游戏/应用列表 | **开** |
| 游戏 VIP 卡 | 登录 / 游戏 VIP 头部卡 | **开** |

> 三宫格藏掉中间格后，剩余卡片会被**重新平分整行**（不是留个空洞），
> 孤立的 1px 分隔线也会一并收掉。

---

## 怎么看效果 / 排查

设置页点「**查看兼容结果**」，会列出 13 项的实时状态：

- `✔ 已验证拦截` — 安装期自检已确认拦截器真的吃掉了调用
- `✓ 已生效` — 规则已装上，且确实拦到过（`hit=`）
- `◐ 部分匹配` — 部分资源 id 在本版本不存在，规则仍生效
- `✕ 未匹配` — 目标版本结构变了，该项失效（不影响其它项）
- `— 已关闭` — 开关是关的

26.5.2 实测：**已验证 4 · 已触发 7 · 部分 0 · 未匹配 0 · 已关闭 2**。

命令行自查：

```sh
logcat -c
am start -n com.heytap.market/com.heytap.market.activity.MainActivity
sleep 5
logcat -d | grep HmClean
```

> logcat 缓冲区十几秒内就会被刷掉（冷启动一次能产生十几万行），
> **不要等几十秒再读**。要么按上面的顺序一步跑完，要么直接看设置页的「查看兼容结果」。

会看到四段关键输出：

```
resource ids resolved: 18/18 complete                       ← 资源 id 探针结果
anchor=float_ad method=a.a.a.qx5.Ԩ(FloatShowType)->boolean ← 四个广告闸门实际挂到哪
selftest verified=ai_bubble,cta_dialog,boot_guide,float_ad  ← 自检：确认拦得住
bottom_bar tabs seen=[首页/游戏/软件/榜单/我的/] hidden=0 kept=5
summary hits[mine_recommend=hit,...] done=13/13             ← 真的拦到了哪些
```

---

## 自行构建

```powershell
$env:ANDROID_SDK        = 'C:\Android\Sdk'      # 需 platforms\android-34 与 build-tools
$env:JAVA_HOME_OVERRIDE = '<你的 JDK 17 路径>'
powershell -ExecutionPolicy Bypass -File app\build.ps1
```

需要 **JDK 17**、Android SDK **platform 34** 与 build-tools（aapt2 / zipalign / apksigner）。
若 SDK 自带的 d8 在你的环境上出问题，可指定一个较新的 r8：

```powershell
$env:R8_JAR = 'E:\tools\r8-9.4.26.jar'
```

产物在 `app/dist/module-v<版本>.apk`。脚本会在缺失时用 `keytool` 自动生成一把本地测试密钥
（`app/debug.keystore`，**不入库**）。

> ⚠️ 自行构建出的 APK 签名与 Release 上的不同，**不能直接覆盖安装**，需先卸载旧版。

---

## 设计原则

- **fail-open**：任何一步失败都静默跳过，绝不影响目标 App 使用。
- **不常驻**：没有定时器、没有轮询；关掉开关强停 App 即完全恢复。
- **不整树遍历**：全部按资源 id / 列表子项精确命中。
- **装上了 ≠ 拦到了**：每项独立探针 + 命中计数 + 安装期自检，
  只有真被拦到过才算数；某项失效时其余项照常工作，
  且如实显示为「未匹配」，不会假装成功。

---

## 文档

- [`docs/ANALYSIS.md`](docs/ANALYSIS.md) — 逆向证据与踩坑记录（主力文档）
- [`docs/ANALYSIS-popup-ad.md`](docs/ANALYSIS-popup-ad.md) — 弹窗广告逐个定位
- [`docs/ANALYSIS-mine-page.md`](docs/ANALYSIS-mine-page.md) — 「我的」页结构
- [`docs/ANALYSIS-float-ad.md`](docs/ANALYSIS-float-ad.md) — 悬浮广告锚点
- `docs/shots/` — 真机截图

---

## 声明

- 本项目是**独立编写的第三方模块**，与 OPPO / 欢太无关，未获其认可或背书。
- 仓库**不包含**目标 App 的任何内容（无 APK、无 dex、无反编译代码），
  也不包含其他项目的代码。所有锚点均从真机与自行解包的 APK 重新推导。
- 逆向分析仅用于互操作性与个人研究。请遵守当地法律，不要用于商业目的。

## 致谢 / 参考

思路层面参考了社区中其他 LSPosed 模块的**做法**（按语义标签过滤、命中延迟汇总上报等），
但未复制任何项目的类名、方法名、资源 id 或设置键。

## 许可证

[GPL-3.0](LICENSE)
