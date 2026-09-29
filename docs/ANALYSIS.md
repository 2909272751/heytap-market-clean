# OPPO 软件商店去广告模块 — 逆向分析与实现记录

目标应用：`com.heytap.market` 26.5.2_CN（uid 10410，主 Activity `com.heytap.market.activity.MainActivity`）
被测设备：Xiaomi Mi 9（Android 16 / API 36，APatch）+ Vector v2.2（libxposed API 102）
产物：`E:\hmc\app\dist\module-v0.2.0.apk`

> 说明：本项目**借鉴**了 GitHub 上 video-lsposed / qq-music-lsposed 两个开源项目的**做法**
> （语义标签过滤、命中计数、延迟汇总、fail-open），但未复制其任何类名、方法名、
> 资源 id 或设置键。下列所有锚点均为从真机 + 解包 APK 重新推导，证据记录在下表。

---

## 1. 锚点证据表（按「形状」记录，不写混淆类名）

| 特性 | 页面 | 证据来源 | 形状特征（锚点） | 默认 |
|---|---|---|---|---|
| 悬浮广告 | 任意页 | 静态探针 | `FloatJumpPriorManager.canShow(FloatShowType)` 恒 false | 开 |
| AI 搜索引导气泡 | 首页搜索栏 | 静态探针 | `AISearchBubbleUtil` 展示方法置空（保留 AI 搜索入口） | 开 |
| CTA 活动弹窗 | 任意页 | 静态探针 | `CtaManager.showCTA` 置空（回调不动，避免卡流程） | 开 |
| 开机必备引导页 | 冷启动 | 静态探针 | 引导 Intent 构造返回 null（唯一调用方本就判空） | 开 |
| 底栏推广入口 | 首页底栏 | 真机 dumpsys | `navi_menu_tab` → `COUINavigationMenuView` → 每项 `fl_root` + `navigation_bar_item_small_label_view`（文案） | 开 |
| 待更新 | 我的·三宫格左 | 真机 dumpsys | `ll_upgrade` | 关 |
| 应用卸载 | 我的·三宫格中 | 真机 dumpsys | `ll_uninstall` | 开 |
| 下载管理 | 我的·三宫格右 | 真机 dumpsys | `ll_download_manager` | 关 |
| 存储空间清理 | 我的·卡片 | 真机 uiautomator | `cl_clean` 资源存在但**运行期不存在** → 改按 `tv_title` 文案 | 开 |
| 应用健康状态 | 我的·卡片 | 真机 uiautomator | `cl_content` + `tv_title="应用健康状态"` | 开 |
| 热门好礼横幅 | 我的·横幅 | 真机 uiautomator | `scroll_banner` + `banner_indicator`，文案兜底 `热门好礼/福利/活动` | 开 |
| 「继续探索」推荐卡 | 我的·第 2 页 | 真机 uiautomator | `card_container` + `v_app_item` + `horizontal_app_item_view_app_rating` | 开 |
| 游戏 VIP 卡 | 我的·头部 | 真机 uiautomator | `vip_layout` | 开 |

### 广告闸门的实际锚点（安装期自检打出来的签名）

目标 App 混淆较重，真实方法名如下（`anchor=` 行会随延迟汇总重发，版本一变一眼可辨）：

| 特性 | 实际方法签名 |
|---|---|
| 悬浮广告 | `a.a.a.qx5.Ԩ(FloatShowType) -> boolean` |
| AI 搜索引导气泡 | `a.a.a.p.ԯ(EffectiveAnimationView) -> void` |
| CTA 活动弹窗 | `a.a.a.hg3.showCTA(Context, dg3) -> void` |
| 开机必备引导页 | `a.a.a.ue8.Ԩ(Context) -> Intent`（static） |

四者**全部通过安装期自检**（`selftest verified=ai_bubble,cta_dialog,boot_guide,float_ad`），
即「拦截器确实吃掉了调用」，而不只是「hook 装上了」。

---

## 2. 关键结论与踩过的坑

### 2.1 底栏：**不能整条隐藏**
早期实现把 `fl_navi_menu_tab` 置 GONE，`dumpsys activity top` 确认生效（`V` → `G`），
但代价是**用户无法切到「我的」**，而「我的」页正是本模块功能最多的页面——等于自己砸自己的脚。

改为借鉴参考项目「按语义标签筛选」的思路：底栏每个 tab 项的 id 完全相同
（都是 `fl_root` / `navigation_bar_item_icon_view` / `navigation_bar_item_small_label_view`），
无法用 id 区分；下标又会随服务端下发顺序变化。**文案是唯一跨混淆、跨版本稳定的锚点**，
因此改成读每项的 label 文案、命中名单才置 GONE。

真机运行时发现的真实底栏为：**首页 / 游戏 / 软件 / 榜单 / 我的**——**不含任何推广 tab**，
所以该规则在当前版本 `hidden=0 / kept=5`，属正确的 fail-open（不产生任何隐藏，也不破坏切页）。
设置页提供了可编辑的隐藏名单，将来服务端下发推广 tab 时可直接填入。

### 2.2 「我的」页卡片：**资源 id 不可靠，文案才可靠**
`aapt2 dump resources` 显示 `cl_clean` / `cl_health` 都在资源表里，所以逐特性探针报 `matched`，
但真机 uiautomator dump 里**根本没有这两个 view**——卡片实际用的是通用布局
`cl_content` + `tv_title` / `tv_subtitle` / `tv_button`。
这就是「状态 matched 但界面上没隐藏」的根因：**探针只能证明 id 能解析，不能证明 view 存在**。

因此健康/清理/横幅三项都增加了**标题文案兜底**（`应用健康状态` / `存储空间清理` / `热门好礼` 等）。

### 2.3 RecyclerView 重新绑定会推翻一次性隐藏
修好文案识别后，日志显示 `hit=mine_recommend`、健康卡也消失了，
但**滚动之后推荐卡又冒出来**：`uiautomator dump` 又能看到「灵妖劫 / 三国志·战略版 / 驾证科目一」。

原因是一次性的 `setVisibility(GONE)` 挡不住 RecyclerView 的重新绑定。
参考项目对这类列表的正解是**拦数据源**，但本页文案（「继续探索」等）实测**不在任何 dex 里**
（`dexgrep` 全量搜索 0 命中），是服务端下发的，没有可锚定的数据源。

最终采用**事件驱动**的守卫：在 `mine_list_view` 上挂 `OnHierarchyChangeListener`，
子项被加进来时套一次卡片规则。相比定时轮询，它每次滑动只触发寥寥数次；
不 hook 任何全局高频方法，不做整树遍历（只看 RecyclerView 的直接子项）。

> 编译用的 `android.jar` 里**没有** `ViewGroup.OnChildAttachStateChangeListener`
> （只有 `OnHierarchyChangeListener`），改用后者，语义等价。
> 另外 `setOnHierarchyChangeListener` 会**覆盖**宿主自己的监听器，所以先反射读出原来的
> （`getOnHierarchyChangeListener()` 也不在 stub jar 里，只能读私有字段 `mOnHierarchyChangeListener`），
> 在自己的回调里转发过去——不能因为去广告把 App 弄坏。

### 2.4 用户反馈的「点一下闪一下」与「格子不居中」

**闪烁**：`APPLY_DELAYS` 原本排了 `{0, 600, 2000, 5000, 12000}` 五个点反复补跑。
列表重新绑定后卡片先以可见状态出现，要等到下一个延迟点才被再次隐藏，于是每点一次闪一次。
子项重挂已经由层级守卫确定性处理，所以长尾补跑纯属多余，删到 `{0, 400, 1500}`。

**格子不居中**：三宫格是等宽并排的（`48-376 / 377-704 / 704-1032`），
隐藏中间的「应用卸载」后左右两张各占 1/3 贴住两边，中间空一大块，
两个 1px 分隔线（`line1` / `line2`）还杵在那里。

真实层级（`dumpsys activity top`）：

```
com.nearme.uikit.widget.cardview.CustomCardView
  androidx.constraintlayout.widget.ConstraintLayout      ← 父容器
    ll_upgrade          (0,0-1640,395)
    ll_uninstall        (328,0-656,392)   ← 隐藏的这个
    ll_download_manager (0,0-1640,395)
```

子项宽 **1640** 远大于行宽 984 —— 它们靠 ConstraintLayout 的**约束 + 百分比宽度**定位。
于是连踩两个坑：

1. 改 `width` / 加 `weight` **都不管用**，改完直接重叠成 `48-1032` 与 `212-1032`；
2. 正确解法是把可见格子**接成一条 chain**，由 ConstraintLayout 自己平分。
   实现要反射 `ConstraintLayout$LayoutParams`，这里有三个必须记住的坑：
   - 模块自身的 classloader **没有** androidx 的类，必须用**目标 App 的 classloader** 加载，
     否则 `ClassNotFoundException`；
   - `widthPercent` **不是 public 字段**（是私有字段 + `setWidthPercent(float)` 方法），
     用 `getField` 会抛异常，得走 `getMethod`；
   - `startToStart/endToEnd/startToEnd/endToStart/width/horizontalBias` 是 public 字段，可直接读写。

修复后实测：两张卡精确平分整行（各 492px），无空洞、无孤立分隔线、无重叠。

### 2.5 广告闸门：从「装上了」到「确认拦得住」
广告有投放条件与频控，实测整轮测试下来**一条都没弹**，
于是「hook 装上了」和「拦截真的生效」无法区分，状态永远停在 `matched`。
参考项目对这条也很强调：必须能证明规则真的命中过。

做法是**安装期自检**：装完闸门立刻反射调用一次。因为四个拦截器都**不调用 `chain.proceed()`**，
自检调过去必然被直接拦下，不可能触发真实广告逻辑。真机结果：

```
anchor=float_ad  method=a.a.a.qx5.Ԩ(FloatShowType)->boolean
anchor=ai_bubble method=a.a.a.p.ԯ(EffectiveAnimationView)->void
anchor=cta_dialog method=a.a.a.hg3.showCTA(Context,dg3)->boolean
anchor=boot_guide method=a.a.a.ue8.Ԩ(Context)->Intent
selftest verified=ai_bubble,cta_dialog,boot_guide,float_ad
```

自检的可行性来自三个类各自的形状：`hg3.getInstance()` 是单例、`ue8.Ԩ` 是 **static**、
`qx5` 有无参构造。注意 `qx5` 的构造**不是 public**（acc=0x10002 实为包可见，
直接 `newInstance()` 抛 `IllegalAccessException`），必须 `setAccessible(true)`。
自检期间用 `selfTest` 标志把调用与真实命中区分开，否则会把「模块自己调的」算成「用户遇到了广告」。

状态语义由此多出一级：`✔已验证拦截`（自检通过）> `✓已生效`（装上了）。

### 2.6 底栏过滤：怎么证明「没东西可藏」不等于「机制是坏的」

26.5.2 的底栏只有 `首页 / 游戏 / 软件 / 榜单 / 我的`，**一个推广 tab 都没有**，
所以默认名单（`福利,活动,签到,福利中心`）下 `hidden=0`。
这看起来像「功能装上了但从没生效过」——和广告闸门当初遇到的正是同一个问题：
**装上了 ≠ 拦到了**。

于是把默认名单临时改成真实存在的 `游戏`，重新装机验证：

```
bottom_bar tabs seen=[首页/游戏/软件/榜单/我的/] hidden=1 kept=4 filter=[游戏]
可见项: 首页(x=207..267) 软件(x=409..469) 榜单(x=611..671) 我的(x=812..872)
```

`游戏` 消失，剩下 4 个等距重排（间距约 202px）。**机制确实工作**，
只是这一版恰好没有推广 tab。随后恢复默认名单。

> 顺带说明「底栏隐藏」的做法：不整条隐藏底栏（那样连「我的」都去不了，
> 而拦截「我的」页正是本模块的目标之一），而是**按 tab 文案逐个置 GONE**，
> 底栏与其余 tab 保留，隐藏项由剩下的自动重排补位。名单可在设置页编辑。

### 2.7 日志会被刷掉，「装上了」≠「拦到了」
冷启动十几秒内 logcat 就能产生十几万行，安装期写出的 `feature=` / `anchor=` 行会被冲掉，
而稍后写的 `hit=` 行还在——于是「模块没跑」和「日志被刷掉」无法区分。
（这一点参考项目也踩过并做了处理。）

因此：
- 引入 `hit()` 命中计数（`ConcurrentHashMap` + `AtomicBoolean` CAS，重复命中只记一次）；
- 首次命中后延迟 2 秒，**把锚点表和状态表重新打一遍**；
- 汇总行 `summary hits[...] done=13/13` 一次说明「哪些真的拦到了」；
- 汇总**不能只在有命中时才排**——那正好是「一条都没拦到」最需要被看见的情况，
  所以安装完成时也排一次（`postSummary(6000)`）。

**读日志的正确姿势**（别等几十秒再读缓冲区）：
```sh
logcat -c; am start -n com.heytap.market/com.heytap.market.activity.MainActivity; sleep 4; logcat -d | grep HmClean
```

### 2.8 框架相关（Vector v2.2 / API 102）
- `log(...)` 在注入接口里是 **final**，override 直接 `LinkageError` → 模块里不覆写 `log`。
- Vector 用 `getDeclaredConstructor()`（无参）实例化入口类，旧 LSPosed 用 `(XposedInterface, ModuleLoadedParam)`
  → **同时提供两个构造器**。
- `PackageReadyParam` 没有 `getPackageName()`，包名从 `onPackageLoaded` 记下来。
- `PackageLoadedParam` 没有 `getClassLoader()`，只有 `getDefaultClassLoader()` → 反射取。
- DB schema 与旧版不同（`modules(mid, module_pkg_name, apk_path, enabled, auto_include)` + `scope(mid, app_pkg_name, user_id)`），
  旧 `modules_state` 表不存在。**每次 `install -r` 都会改变 APK 路径，必须回写 `apk_path` 并重启。**

---

## 3. 性能取舍（遵循「简洁 / 不影响流畅度 / 更省电」）

| 约束 | 做法 |
|---|---|
| 不做整树遍历 | 全部按 `findViewById` 精确命中；卡片规则只看 RecyclerView 直接子项 |
| 不 hook 高频方法 | 4 个广告闸门都是「决策点」方法，命中即返回；列表守卫靠事件而非轮询 |
| `intercept` 内零分配/零反射/零日志 | 闸门内只做一次 `ConcurrentHashMap.get` + CAS，返回值用缓存装箱 |
| 不常驻 | 闸门命中一次即记录，UI 规则无定时器；关掉开关强停 App 即完全恢复 |
| fail-open | 任何一步失败都静默跳过，绝不影响目标 App；`exceptionMode=protective` |

---

## 4. 真机验证结果（截图级证据用 dumpsys + uiautomator 替代）

```
feature=float_ad        result=matched    feature=mine_upgrade   result=off
feature=ai_bubble       result=matched    feature=mine_uninstall result=matched
feature=cta_dialog      result=matched    feature=mine_download  result=off
feature=boot_guide      result=matched    feature=mine_clean     result=matched
feature=bottom_bar      result=matched    feature=mine_health    result=matched
feature=mine_banner     result=matched    feature=mine_recommend result=matched
feature=mine_vip        result=matched
summary hits[mine_recommend=hit, mine_uninstall=hit, mine_health=hit,
             mine_banner=hit, mine_vip=hit] done=13/13
bottom_bar tabs seen=[首页/游戏/软件/榜单/我的/] hidden=0 kept=5
```

「我的」页 UI dump 前后对比：
- 修复前：`应用健康状态 | 当前 88 分… | 立即优化` + 第二页 `灵妖劫（宋轶代言）| 4.6 | 691 MB …`
- 修复后：两处**均已消失**；底栏 `首页/游戏/软件/榜单/我的` 完好可切；
  未勾选的 `待更新 / 下载管理` 按预期保留。
