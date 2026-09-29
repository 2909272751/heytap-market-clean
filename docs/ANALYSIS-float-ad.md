# OPPO 软件商店 (com.heytap.market 26.5.2_CN) 悬浮广告 — 控制锚点逆向分析

反编译源码根：`E:\hmc\jadx-out\sources`
原始 APK：`E:\hmc\orig\market-26.5.2.apk`
资源表 dump：`E:\hmc\orig\res_dump.txt`

> 只读分析，未修改任何 APK / 源码。/ 表示"未证实"。

---

## 0. 一句话结论

悬浮广告视图 `dx5`（`RelativeLayout` 子类）的可见性由 **`a.a.a.qx5#Ԩ`（jadx 名 `m40637`，语义 `canShow`）** 这一处布尔闸门决定：
`a.a.a.dx5#Ԭ`(jadx `m9500`) 与 `a.a.a.dx5#ԭ`(jadx `m9501`) 是**仅有的两条**会调用 `setVisibility(0)` 的路径，二者都先问 `qx5.Ԩ`。
`dx5#Ԯ`(jadx `m9502`) 虽然内部也 `setVisibility(0)`，但**只被 `dx5#Ԭ` 使用**，不是完整闸门。

---

## 1. 关键事实与验证方法（含采信度分级）

| 编号 | 事实 | 验证方式 | 采信度 |
|---|---|---|---|
| F1 | `0x7f090dea` = `id/view_id_float_ad` | `res_dump.txt:29844` | A |
| F2 | **没有任何 layout XML 声明该 id** | 对 APK 内全部 3463 个 `res/*.xml` 做 4 字节小端扫描（`EA 0D 09 7F`）→ 0 命中；对照组 `home_page_container`(0x7f090512) 命中 `res/OUw.xml`、`view_id_contentview`(0x7f090de9) 命中 `res/EgC.xml`+`res/VnS.xml`，证明方法有效 | A |
| F3 | 该 id 由**代码 `setId()`** 设置，设置点是 `a.a.a.ex5#onPageVisible` | dex 指令：`sget Lcom/heytap/cdo/client/R$id;->view_id_float_ad` → `new-instance La/a/a/dx5;` → `invoke-direct dx5.<init>(Context,String)` → `invoke-virtual View.setId(I)` → `ViewGroup.addView` | B |
| F4 | 全 APK 范围内 `0x7f090dea` 只出现在 `classes4.dex` | 全 4850 个 entry 逐条字节扫描，仅 `classes4.dex` 1 处命中（即 `R$id` 静态字段值）；无嵌套 APK（`assets` 只有 `html.zip`） | A |
| F5 | jadx **漏输出**了关键类 | `com/heytap/cdo/client/advertisement/`、`com/heytap/cdo/client/cards/page/base/floatjump/`、`a/a/a/ex5.java`、`a/a/a/we.java`、`a/a/a/cx5.java` 均 **MISSING**，但 `classes4.dex` 中 `class_defs` 有它们且方法体完整 | A |
| F6 | 混淆方法名 ↔ jadx 重命名映射 | 用 dex 内的日志字面量判定：`qx5.Ϳ`含`"addFloatShowType: "`、`qx5.Ԩ`含`"canShow: "`、`qx5.ԩ`含`"removeFloatShowType: "`；`dx5.Ԫ`/`dx5.Ԯ` 含`"translationY"`。再按虚方法顺序补齐 `dx5.ԩ/Ԫ/Ԭ/ԭ/Ԯ` → jadx `m9498/m9499/m9500/m9501/m9502`（7 个虚方法一一对应，无剩余） | A |

**方法学说明（重要）**：jadx 缺失的类无法给 `file:line`，其证据为 dex 层。dex 指令流是线性变长编码，我用两重独立手段交叉验证调用关系：(a) 按官方 opcode 宽度表做对齐遍历（自检：对 `dx5.Ԭ` 的遍历结果与 jadx 源码 `a/a/a/dx5.java:114-121` 完全一致，且能正确跳过多字节指令内的"假 invoke"）；(b) 不依赖对齐的**字节模式 + code_item 区间归属**（`insns_size` 取 `code_off+12` 的 u4）。下表凡标注【dex】者均为 (b) 方法确认过归属。

---

## 2. 锚点总表

| 锚点(类#方法) | 类型(非混淆/混淆) | 证据 file:line | 形状判据(参数/返回类型/修饰符) | 是否适合做 hook 闸门 | 风险 |
|---|---|---|---|---|---|
| `a.a.a.qx5#m40637`（dex 名 `Ԩ`，语义 canShow） | 混淆 | jadx 源码 `a/a/a/qx5.java:58-70`；日志串 `"canShow: "` 见 `a/a/a/qx5.java:61` | `public final boolean Ԩ(FloatShowType)`，Kotlin 单例 `qx5.f33900`（`qx5.java:18`） | **推荐**：返回 false 可同时封堵 `dx5.Ԭ` 与 `dx5.ԭ` 两条上屏路径 | 同时抑制 `FLOAT_JUMP_VIEW`（FloatJumpViewPresenter）与 `La/a/a/x94$r#ԩ`；参数枚举 `com.nearme.uikit.widget.floatJump.FloatShowType` 为非混淆类，判据稳定 |
| `a.a.a.qx5#m40636`（`Ϳ`，addFloatShowType） | 混淆 | `a/a/a/qx5.java:51-55` | `public final void Ϳ(FloatShowType)` | 可作辅助（标记"已展示"） | 单独 hook 无效，只是登记动作 |
| `a.a.a.qx5#m40638`（`ԩ`，removeFloatShowType） | 混淆 | `a/a/a/qx5.java:73-77` | `public final void ԩ(FloatShowType)` | 可作辅助 | 同上 |
| `a.a.a.dx5#m9500`（`Ԭ`） | 混淆 | `a/a/a/dx5.java:114-121`（canShow 判定 `:117`，add `:118`，`m9502(300L)` `:119`） | `public void Ԭ()`；dex 证据：内部只 invoke `qx5.Ԩ`+`qx5.Ϳ`+`dx5.Ԯ` | **推荐（精确）**：改为 no-op 即封堵"Handler 上屏"路径 | 需与 `m9501` 成对 hook，单独 hook 会漏 |
| `a.a.a.dx5#m9501`（`ԭ`） | 混淆 | `a/a/a/dx5.java:124-132`（canShow `:127`，add `:128`，`setVisibility(0)` `:129`，`startAnimation` `:130`） | `public void ԭ(Animation)`；dex 证据：内部只 invoke `qx5.Ԩ`+`qx5.Ϳ`（**不调用 `dx5.Ԯ`**） | **推荐（精确）**：no-op 即封堵"携带动画上屏"路径 | 同左，须成对 |
| `a.a.a.dx5#m9502`（`Ԯ`） | 混淆 | `a/a/a/dx5.java:135-155`（`setVisibility(0)` 在 `:137` 与 `:152`） | `public void Ԯ(long)` | **不完整**：仅 `m9500` 走它，`m9501` 直接 `setVisibility(0)`。单点 hook 会漏 | 若被误当成唯一闸门 → 悬浮广告在 `m9501` 路径复现 |
| `a.a.a.dx5#m9498`（`ԩ`，隐藏） | 混淆 | `a/a/a/dx5.java:84-87`（`removeFloatShowType` `:85`，`setVisibility(8)` `:86`） | `public void ԩ()` | 适合做"强制隐藏"尾部闸门 | 只隐藏不阻断；动画回调 `dx5$a#onAnimationEnd`（`dx5.java:168-171`）会再调它 |
| `a.a.a.dx5#m9499`（`Ԫ`，退场动画） | 混淆 | `a/a/a/dx5.java:90-111` | `public void Ԫ()` | 辅助 | 动画结束后仍走 `m9498` |
| **`a.a.a.ex5#onPageVisible`** | **混淆(类名混淆，方法名未混淆)** | 【dex】`classes4.dex` `code_off=1969864`；jadx **缺失**（`a/a/a/ex5.java` MISSING） | `public void onPageVisible(Activity, String) : void`（virtual）；方法表见 dex，含 `getInstance()`/`requestFloatAdConfig()`/`removeFloatView(String)` | **最上游**：置空可让视图根本不创建/不 addView | `ex5` 是 **buoy(浮标) 总控**（日志 tag `"buoy_biz"`），置空会连带关闭非广告浮标；风险高于 `qx5.Ԩ` |
| `FloatAdPresenter#ޛ` | 混淆(类名未混淆/方法名混淆) | 【dex】`classes4.dex` `code_off=4206376`；`FloatAdPresenter#ޛ(Z, La/a/a/kx5;) : V`；jadx 缺失 | `void ޛ(boolean, a.a.a.kx5)` | 可选：这是**唯一** invoke `dx5.ԭ(Animation)` 的地方 | 方法与广告业务耦合，参数为混淆类型 `kx5` |
| `FloatAdPresenter$e#handleMessage` | 混淆 | 【dex】`classes4.dex` `code_off=4202836`；签名 `handleMessage(android.os.Message) : Z`；jadx 缺失 | `boolean handleMessage(Message)`（Handler 回调） | 可选：这是**唯一** invoke `dx5.Ԭ()` 的地方 | 内部为 `payload-switch` 多分支，只做 no-op 会同时吞掉其它消息（含 `ex5.requestFloatAdConfig()` 刷新分支） |
| `FloatAdPresenter#ލ` | 混淆 | 【dex】`classes4.dex` `code_off=4204768`；`void ލ()` | `void ލ()` | 辅助（隐藏） | — |
| `FloatAdPresenter#ޒ` / `#ޖ` | 混淆 | 【dex】`code_off=4205148` / `4205856`；`ޒ(La/a/a/kx5;, View) : V`、`ޖ() : V` | 见左 | 辅助（调用 `dx5.m9499` 退场） | — |
| `FloatAdPresenter#ޔ` / `#ൕ` / `$d#onLoadingFailed` | 混淆 | 【dex】`code_off=4205380` / `4203664` / `4202664`；`ޔ(La/a/a/kx5;) : V`、`ൕ() : View`、`onLoadingFailed(String, Exception) : Z` | 见左 | 辅助（仅取 `getImageView()` 换图/埋点） | — |
| `FloatJumpViewPresenter#ޔ` / `$b#Ϳ` | 混淆 | 【dex】`classes4.dex` `code_off=4218560` / `4216564`；`ޔ(FloatJumpViewPresenter):V`、`Ϳ(boolean):V` | 见左 | 竞争方：与悬浮广告共用 `qx5.Ԩ` 闸门 | 不是悬浮广告本体 |
| `com.heytap.cdo.client.R$id#view_id_float_ad` | **非混淆**（资源名保留） | `com/heytap/cdo/client/R.java:247`；重复定义 `com/heytap/market/R.java:40792`；`res_dump.txt:29844` | `public static final int = 0x7f090dea` | 可作"id 锚点"定位视图实例 | 仅常量 |
| `com.heytap.cdo.osp.domain.common.ExtFeatureSwitchesType#BUOY_ADS` | **非混淆** | `com/heytap/cdo/osp/domain/common/ExtFeatureSwitchesType.java:13` | `public static final String = "buoy_ads"` | 配置 key，可作开关判据 | 仅字符串常量 |
| `com.heytap.cdo.osp.domain.common.ExtFeatureSwitchesType#FLOAT_ADS` | **非混淆** | `…/ExtFeatureSwitchesType.java:22` | `public static final String = "float_ads"` | 广告位配置 key | 仅字符串常量 |
| `a.a.a.ug5#m49288`（dex `Ϳ`） | 混淆 | jadx `a/a/a/ug5.java:32-34`（转调 `qs7.m40407().getServeManagerSwitchState(str)`） | `public static boolean Ϳ(String)` | 服务端开关读取器；`ex5#onPageVisible` 用 `ug5.Ϳ("buoy_ads")` 判闸 | 开关极性 / 未证实（见 §7） |
| `float_ad_is_finished`（string 0x7f12047b） | 非混淆 | `com/heytap/market/R.java:49199`；`com/heytap/market/string/common/R.java:491` | `public static final int` | 埋点/完成态文案锚点 | 与可见性无关 |
| `com.heytap.cdo.buoy.domain.dto.BuoyDto` | **非混淆** | `com/heytap/cdo/buoy/domain/dto/BuoyDto.java:14`（`isSkip()` `:241`，`getShowUrl()` `:201`，`getAdInfoDto()` `:99`，`getTimeFreqConfigDto()` `:217`） | protostuff `@Tag` DTO | 数据闸门：`BuoyDto.isSkip()` 参与是否展示 | 混淆方可能改 Tag 号 |
| `com.heytap.cdo.buoy.domain.dto.AdInfoDto` | **非混淆** | `AdInfoDto.java:7`（`adId` `:10`，`adPos` `:13`，`exposeBeginUrls` `:19`，`exposeEndUrls` `:22`，`clickUrls` `:16`，`transparent` `:25`） | protostuff `@Tag` DTO | 广告素材/曝光字段 | 同上 |
| `a.a.a.tg7#findFloatAdPresenter` | 混淆(接口) | `a/a/a/tg7.java:10`（`Object findFloatAdPresenter(String)`） | 接口方法 | 插件化取 presenter 的入口 | 未证实调用方 |
| `a.a.a.we#getFloatings` / `#getBatchFloatings` / `#preloadFloatings` | 混淆 | 【dex】`classes4.dex` `code_off=1332800` / `1332692` / `1333308`；三方法均引用字符串 `"float_ads"`；jadx **缺失**（`a/a/a/we.java` MISSING） | `getFloatings(String,String,String,WeakReference) : void` 等 | 广告**数据来源**（拉取入口） | 只读分析未确认网络字段名 |

---

## 3. 调用链：谁决定 `setVisibility(VISIBLE)` / `show()`

```
[M0] a.a.a.ex5#onPageVisible(Activity, String)                     【dex】classes4.dex code_off=1969864
     ├─ 闸门 g1: isMinorsMode()               ─ 真 → return
     ├─ 闸门 g2: Activity 非空 / isFinishing / isDestroyed
     ├─ 闸门 g3: ug5.Ϳ("buoy_ads")（ServeManager 服务端开关，tag "buoy_biz"）
     ├─ findViewById(R.id.tab_content_parent) → 目标 FrameLayout
     ├─ sget R$id->view_id_float_ad → findViewById(id) → check-cast dx5
     ├─ 若不存在: new dx5(context, pageId) + View.setId(view_id_float_ad) + setLayoutParams + addView
     └─ 已存在/新建后: dx5.ԩ()(m9498 隐藏) ; new FloatAdPresenter() ; presenter.ގ(dx5, BuoyDto) ; presenter.ޘ()
                                                                    【dex】code_off 4204796 / 4206012

[M1] FloatAdPresenter$e#handleMessage(Message)                      【dex】code_off=4202836
     └─ dx5.Ԭ()  → jadx a/a/a/dx5.java:114  ── 唯一的"Handler 上屏"
            └─ if (qx5.Ԩ canShow(MAIN_TAB_FLOAT_ICON))         a/a/a/dx5.java:117
                   ├─ qx5.Ϳ addFloatShowType(MAIN_TAB_FLOAT_ICON)  a/a/a/dx5.java:118
                   └─ dx5.Ԯ(300L) → **setVisibility(0)**           a/a/a/dx5.java:119 / :137,:152

[M2] FloatAdPresenter#ޛ(boolean, kx5)                              【dex】code_off=4206376
     ├─ 先 FloatAdPresenter#ލ() 隐藏                                 【dex】code_off=4204768
     └─ dx5.ԭ(Animation) → jadx a/a/a/dx5.java:124  ── 唯一的"带动画上屏"
            └─ if (qx5.Ԩ canShow(MAIN_TAB_FLOAT_ICON))         a/a/a/dx5.java:127
                   ├─ qx5.Ϳ addFloatShowType(MAIN_TAB_FLOAT_ICON)  a/a/a/dx5.java:128
                   ├─ **setVisibility(0)**                          a/a/a/dx5.java:129
                   └─ startAnimation(animation)                     a/a/a/dx5.java:130

[M3] 隐藏/退场
     ex5#onPageVisible            → dx5.ԩ()(m9498)   a/a/a/dx5.java:84-87
     FloatAdPresenter#ލ           → dx5.ԩ()(m9498)
     FloatAdPresenter#ޒ / #ޖ      → dx5.Ԫ()(m9499)   a/a/a/dx5.java:90-111（translationY 退场动画）
     dx5$a#onAnimationEnd         → dx5.ԩ()(m9498)   a/a/a/dx5.java:168-171

[M4] 互斥竞争（同一个 qx5 闸门）
     FloatJumpViewPresenter#ޔ → qx5.Ԩ canShow     【dex】code_off=4218560
     FloatJumpViewPresenter$b#Ϳ → qx5.Ϳ + qx5.ԩ   【dex】code_off=4216564
     La/a/a/x94$r#ԩ           → qx5.Ԩ canShow     【dex】classes4.dex
```

`qx5` 闸门本体（非混淆语义，可直接读源码）`a/a/a/qx5.java`：

* `:22` `private static final HashSet<FloatShowType> f33902` —— 进程级"已展示类型"集合
* `:58-70` `canShow(type)`：`MAIN_TAB_FLOAT_ICON` 返回 `!contains(FLOAT_JUMP_VIEW)`；`FLOAT_JUMP_VIEW` 返回 `!contains(MAIN_TAB_FLOAT_ICON)`；`APP_COMMON_RECOMMEND_DIALOG` 在含 `FLOAT_JUMP_VIEW` 时为 false
* `:51-55` `addFloatShowType` / `:73-77` `removeFloatShowType`
* 类名证据（语义）：`:14` `private static final String f33901 = "FloatJumpPriorManager"`

---

## 4. `view_id_float_ad` 的真相

1. `res_dump.txt:29844` → `0x7f090dea = id/view_id_float_ad`（另 `res_dump.txt` 中 `0x7f080638 drawable/default_float_ad`、`0x7f090e28 id/vs_float_jump_view`、`0x7f0c052c layout/uk_float_jump_view`、`0x7f130263 style/CustomCardView.floatJump`）。
2. **没有 layout 引用它**（F2）。同族 id `vs_float_jump_view` 亦无代码引用（`com/heytap/cdo/client/cards/fragment/R.java:8` 等仅常量定义）。
3. id 由 `a.a.a.ex5#onPageVisible` 用 `setId(R.id.view_id_float_ad)` 打在 **`new dx5(context, pageId)`** 上，然后 `addView` 进 `tab_content_parent`（`sget Lcom/heytap/cards/main/api/R$id;->tab_content_parent`）。
   → 与真机 dumpsys「类名 `dx5`、id `view_id_float_ad`、挂在 MainActivity 根下」完全吻合。
4. `dx5` 自身不引用该 id（`classes5.dex` 中无 `0x7f090dea`），其构造参数为 `(Context, String pageId)`。

---

## 5. 广告数据来源

| 环节 | 证据 |
|---|---|
| 拉取入口 | 【dex】`a.a.a.we#getFloatings(String,String,String,WeakReference)` `code_off=1332800`；`#getBatchFloatings()` `1332692`；`#preloadFloatings()` `1333308`；三者均引用字符串 `"float_ads"` |
| 配置刷新 | 【dex】`a.a.a.ex5#requestFloatAdConfig()` `code_off=1971080`；日志 `"buoy expired, request new data!"`（在 `FloatAdPresenter$e#handleMessage` 分支内，tag `"buoy_biz"`） |
| 接口取 presenter | `a/a/a/tg7.java:10` `Object findFloatAdPresenter(String)` |
| 服务端开关 | `ExtFeatureSwitchesType.java:13` `"buoy_ads"`、`:22` `"float_ads"`；读取器 `a/a/a/ug5.java:32` → `qs7.m40407().getServeManagerSwitchState(str)` |
| 数据模型 | `BuoyDto.java:14`（`showUrl:201`、`showType:197`、`showTime:193`、`isSkip:241`、`startTime:209`、`endTime:135`、`adId:95`、`adPos:103`、`adType:107`、`isFrequencyLimit:151`、`timeFreqConfigDto:217`）；`AdInfoDto.java:10-25`（`adId/adPos/exposeBeginUrls/exposeEndUrls/clickUrls/transparent`）；`BuoyWrapDto.java`、`com/heytap/cdo/common/domain/dto/ad/{ContractAdInfoDto,DisplayAdInfoDto}` |
| 完成态 | `float_ad_is_finished` = `0x7f12047b`（`com/heytap/market/R.java:49199`） |

---

## 6. 推荐 hook 闸门

**首选（单点、布尔、覆盖两条上屏路径）**

```
类:  a.a.a.qx5            （dex classes7.dex，Kotlin object，jadx: a/a/a/qx5.java）
方法: Ԩ                   （U+0528；jadx 重命名为 m40637；日志字面量 "canShow: "）
签名: public final boolean Ԩ(com.nearme.uikit.widget.floatJump.FloatShowType)
处置: 直接 return false
```

理由：`dx5.Ԭ`(m9500) 与 `dx5.ԭ`(m9501) 是仅有的两条 `setVisibility(0)` 路径，二者都在动作前调用 `qx5.Ԩ`（`dx5.java:117`、`:127`）；且该方法的参数类型是非混淆枚举、返回 `boolean`，形状判据稳定。
风险：会同时压制 `FLOAT_JUMP_VIEW`（`FloatJumpViewPresenter#ޔ`）与 `La/a/a/x94$r#ԩ`，属"跨控件优先级"闸门。

**精确替代（只封悬浮广告图标）**：同时 hook `a.a.a.dx5#Ԭ()` 与 `a.a.a.dx5#ԭ(Animation)`（dex 名 `Ԭ` U+052C、`ԭ` U+052D；jadx `m9500`/`m9501`）为 no-op。

**不要只用** `a.a.a.dx5#Ԯ(long)`（jadx `m9502`）：`m9501` 不经过它，会漏（见 §2 表内说明与 §3 的 [M2]）。

**最上游但风险最高**：`a.a.a.ex5#onPageVisible(Activity, String)` 置空 → 视图根本不会创建；但 `ex5` 是 buoy 总控，会影响非广告浮标。

---

## 7. 未证实 / 矛盾项（不猜测）

1. **`ug5.Ϳ("buoy_ads")` 的闸门极性未证实**：反汇编显示 `ex5#onPageVisible` 在调用后存在分支（一处落到 `new-instance dx5` 路径、一处落到 `return-void`，`pc26-34` 区间），但该方法的寄存器复用与分支目标在纯字节码层面无法可靠定论；且我的线性遍历在方法末尾存在 1 个 code unit 的漂移（`end=78 vs insns_size=77`）。**故只断言"存在该服务端开关判据"，不断言其为开或关。**
2. **`FloatAdPresenter*` / `ex5` / `we` / `cx5` 类在 jadx 输出中缺失**（F5），因此这些锚点无 `file:line`，仅有 dex 证据；若需源码级证据须换用其它反编译器（如 jadx 更高版本 / baksmali）重新反编译 `classes4.dex`。
3. **`FloatAdPresenter$e#handleMessage` 内的 switch 分支语义未逐条证实**：可见 `payload-switch n=3` 及 `const/4 #1`，但各 case 与 `what` 常量的对应关系未证实。
4. **`FloatAdPresenter#ޛ` 的线性遍历漂移**（`end=78 vs 77`）；其中 `dx5.ԭ(Animation)` 的调用由"字节模式 + 方法区间归属"独立确认（全 `classes4.dex` 内 `dx5.ԭ` 的 invoke 模式**仅出现 1 次**，位于 `code_off=4206376` 即 `#ޛ` 内）。
5. **`La/a/a/x94$r#ԩ` 为何调用 `qx5.Ԩ` 未证实**（类名混淆且不在 jadx 输出中）。
6. `dx5.ԭ` 在 `classes5.dex` 中被若干无关类（`CloudGameReceiver$b`、`BaseRecordAdapter` 等）"命中"属**假阳性**：`classes5` 中 `dx5.ԭ` 的方法索引 3856 = `0x0F10` 是常见寄存器字，字节模式扫描会撞上；这些不是真实调用（已由"真实调用方应同时具备 `dx5.Ԭ`/`qx5` 上下文"与对齐遍历双重排除）。

---

## 8. 复现命令（只读，可重跑）

```powershell
# F1
Select-String -Path 'E:\hmc\orig\res_dump.txt' -Pattern 'view_id_float_ad'

# 源码侧：id / dx5 / 闸门
cd 'E:\hmc\jadx-out\sources'
findstr /s /n /c:"view_id_float_ad" /c:"0x7f090dea" /c:"2131361770" *.java
findstr /s /n /c:"dx5" *.java
findstr /s /n /c:"FloatShowType" /c:"FloatJumpPriorManager" *.java

# F2/F4：全 APK 字节扫描（0x7f090dea 小端 = EA 0D 09 7F）
#   对照组：0x7f090512 = 12 05 09 7F  → res/OUw.xml
#           0x7f090de9 = E9 0D 09 7F  → res/EgC.xml, res/VnS.xml

# F3/F6：dex 解析（本报告使用的脚本在 %TEMP% 下：dexscan8.py / ownermap.py / mapcheck.py）
#   insns_size 取 code_off+12 的 u4（不是 +2 的 u2）
```

**已生成的过程产物**（可复核，非交付物）：`E:\hmc\docs\_final.txt`（关键方法反汇编）、`_ownermap.txt`（调用方归属）、`_dexscan7.txt`（对齐遍历调用方）、`_sigs.txt`（dex 方法签名）、`_grep_*.txt`。
