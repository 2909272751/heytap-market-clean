# OPPO 软件商店 (com.heytap.market 26.5.2_CN) —— 弹窗广告 / 推荐弹窗 / 开机必备引导页 触发闸门分析

- 反编译源码根：`E:\hmc\jadx-out\sources`（jadx，`--no-res`，故 `jadx-out\resources` 为空）
- 原始 APK：`E:\hmc\orig\market-26.5.2.apk`（10 个 dex：classes.dex ~ classes10.dex）
- 资源表 dump：`E:\hmc\orig\res_dump.txt`
- 分析方式：只读。jadx 文本检索 + **dex 字节码直读**（自写 DEX 解析器，见 §8）。未修改任何被分析文件。

---

## 0. 先决结论：jadx 输出不完整（影响全部结论的取证方式）

**这是本次分析最重要的前提，务必先读。**

`jadx-out\sources` 缺失了相当一批类：类在 `classesN.dex` 的 `class_defs` 里存在，但 jadx 没有产出对应 `.java`。已逐一验证的缺失类（均为本次目标类）：

| 缺失类（dex 描述符） | 所在 dex | 验证方式 |
|---|---|---|
| `Lcom/heytap/cdo/client/cards/page/openphone/installrequire/activity/InstallRequireActivity;` | classes4.dex | dex 字符串命中，`jadx-out` 无该文件 |
| `Lcom/heytap/cdo/client/cta/CtaDialogActivity;` | classes4.dex | 同上，且 `jadx-out\...\client\cta\` 目录不存在 |
| `La/a/a/p;` | classes5.dex | 同上 |
| `La/a/a/q09;` | classes4.dex | 同上 |
| `La/a/a/hg3;` | classes4.dex | 同上 |
| `Lcom/heytap/cdo/client/cn/external/bootreg/OpenGuideActivity;` / `OpenGuideFragment` | classes4.dex | 目录内仅存 4 个文件 |

**后果**：
1. 纯 jadx 文本检索会得出"这些字符串/类无引用"的**假阴性**。
2. 因此本文的**关键闸门结论一律以 dex 字节码为准**，并在证据列标注 `[dex]`。
3. 好消息：类在运行时真实存在，**按全限定名 hook 有效**。

> 反例校验：用同一 DEX 解析器检索 `pref.use.install.require.show` → 命中 `La/a/a/r2g;.<clinit>`、`La/a/a/s2g;.Ԩ`，与 jadx 的 `r2g.java`、`s2g.java` 一致；检索 `launch_task` → 命中 `a.a.a.xc2`，与 `xc2.java:14` 一致。解析器已验证可信。

---

## 1. 锚点字符串定位（任务 1）

### 1.1 关键事实：弹窗文案**不是代码字符串常量**

对全部 10 个 dex 做了 `const-string` / `const-string/jumbo` 指令级扫描，并对全部 `.java` 做了字面量扫描：

- `"AI 搜索全新上线，找到更适合你的应用，快试试吧"`、`"知道了"`、`"一键安装"`、`"暂不安装"`、`"开机必备"` 作为**代码常量**：**未命中**（`sources` 内 0 处，dex 指令内 0 处）。
- 它们只存在于资源表，运行期通过资源 ID 读取。

**⇒ 结论：不能按文案字符串做 hook 锚点，必须按类/方法/资源 ID。**

### 1.2 资源锚点（file:line = `E:\hmc\orig\res_dump.txt`）

| 锚点(资源) | 资源 ID | 中文值 | 证据 file:line |
|---|---|---|---|
| `string/ai_search_guide_content` | `0x7f12003e` | AI 搜索全新上线，找到更适合你的应用，快试试吧 | `res_dump.txt:35913`(声明) / `:35916`(值) |
| `string/ai_search_guide_dismiss` | `0x7f12003f` | 知道了 | `res_dump.txt:35920` / `:35923` |
| `string/ai_search_guide_title` | `0x7f120040` | AI 搜索 | `res_dump.txt:35927` / `:35930` |
| `string/install_all_download_button` | `0x7f12051b` | 一键安装 | `res_dump.txt:43640` / `:43643` |
| `string/main_not_install_download_button` | `0x7f1205e9` | 暂不安装 | `res_dump.txt:44854` / `:44857` |
| `string/title_open_phone` | `0x7f120a63` | 开机必备 | `res_dump.txt:51616` / `:51619` |

R 字段声明处：`sources\com\heytap\market\R.java`、`sources\com\heytap\market\string\common\R.java`、`sources\com\heytap\market\string\cn\R.java`。

### 1.3 唯一以代码常量形式存在的"开机必备"

| 锚点(类#方法/字段) | 类型 | 证据 file:line | 形状判据 |
|---|---|---|---|
| `CardTemplateType.OPEN_REQUIRED_CARD(149,"开机必备卡片(标题、横向4个APP、勾选框)")` | 枚举常量 | `sources\com\heytap\cdo\osp\domain\common\CardTemplateType.java:69` | 动态卡片模板类型 149 |
| `CardTemplateType.GAME_OPEN_REQUIRED_CARD(206,…)` | 枚举常量 | `…\CardTemplateType.java:123` | 游戏中心开机必备卡片 |
| `CardTemplateType.GAME_OPEN_REQUIRED_IMG_CARD(207,…)` | 枚举常量 | `…\CardTemplateType.java:124` | 同上（图片卡） |

配套资源（同页专用，佐证"开机必备"是一整套页面而非零散弹窗）：
`anim/open_guide_page_content_enter`(`res_dump.txt:252`)、`dimen/open_guide_bottom_btn_margin_middle`(`:17062`)、`color/open_guide_rec_menu_text_color`(`:11189`)。

### 1.4 三个"弹窗"各自的**真实代码锚点**（dex 级，全限定名）

| 观察到的弹窗 | 真实实现类 | 所在 dex | 证据（dex 指令级） |
|---|---|---|---|
| ① 用户须知（协议） | `a.a.a.b2g`（日志标记 `UserPrivacyLaunchTask`） | classes4.dex | `b2g.java:36` → `z1g.m59665().showPrivacyDialog(activity, callback)` |
| ② 开机必备页 | `com.heytap.cdo.client.cards.page.openphone.installrequire.activity.InstallRequireActivity` | classes4.dex | `ue8.java:25` `new Intent(context, InstallRequireActivity.class)` |
| ③ AI 搜索引导弹窗 | `a.a.a.p`（日志标记 **`AISearchBubbleUtil`**） | classes5.dex | `[dex] La/a/a/p;.ֈ`(=`\u0588`) 读取 `R$string.ai_search_guide_title/content/dismiss` 并调 `COUIToolTips.showWithDirection` |
| （补充）通用 CTA 弹窗 | `com.heytap.cdo.client.cta.CtaDialogActivity` + 管理器 `a.a.a.hg3`（日志标记 **`CtaManager`**） | classes4.dex | `[dex] La/a/a/hg3;.showCTA`，字符串 `CtaManager: needShowCtaDialog: ` |

---

## 2. `InstallRequireActivity`（开机必备）完整链路（任务 2）

### 2.1 类本身

```
com.heytap.cdo.client.cards.page.openphone.installrequire.activity.InstallRequireActivity
  extends com.nearme.module.ui.activity.BaseActivity      [dex classes4.dex]
  methods: onCreate(strs "show_type"), 一个 static 方法(strs "0|1|is_again_show|install_show_probability"),
           onCreateOptionsMenu, onLoadFailed, getStatusBarTintConfig
```
- 该文件**不在 jadx 输出中**（见 §0），以上来自 dex `class_defs` 直读。
- 页面内容为**动态卡片**：`InstallRequirePageLoader.java:12` →
  `new c.a().mo85784("/card/store/v4/jump-selector?biz=open-required")`（`sources\com\heytap\cdo\client\cards\page\openphone\installrequire\activity\InstallRequirePageLoader.java:12`）。
- 卡片模板 = `OPEN_REQUIRED_CARD(149)`（`CardTemplateType.java:69`）。

### 2.2 调用链（谁启动它）—— 逐层可验证，每层调用者唯一

```
[启动任务链 u09]  a.a.a.d4b.mo2892(Activity, bz8)
    └─ d4b.java:34   zy8.m62168(activity, this.f6361)
                        └─ zy8.java:52-53   activity.startActivity(intent)   ← ★真正的 startActivity
Intent 构造：
  d4b.m7783(Activity)                     d4b.java:20-26
    └─ d4b.java:25   zy8.m62164(activity)
        └─ zy8.java:26-27   c4b.m5362(context)
            └─ c4b.java:43  ue8.m49136(context)
                └─ ue8.java:25  new Intent(context, InstallRequireActivity.class) + putExtra("show_type", i)
```
同时存在的第二条入口：
```
a.a.a.az8.startActivity(Activity, Intent)   az8.java:26-27  → zy8.m62168(activity, intent)
  （az8 是 @RouterService(interfaces = {ol7.class}) 路由服务，az8.java:12）
```

**调用者收束性验证**（全文检索结果）：
- `m49136` / `m49135` 的调用点只有 `c4b.java:43`、`c4b.java:61/64/69`（`sources` 内）。→ `ue8` 是唯一漏斗。
- `m5362`/`m5363` 的调用点只有 `zy8.java:27`。
- `m62164` 的调用点只有 `d4b.java:25`；`m62168` 的调用点只有 `d4b.java:34`、`az8.java:27`。
- ⇒ **`ue8.m49136` 是"是否展示开机必备页"的单一收敛点。**

### 2.3 "是否展示开机必备页"的判定方法（一次性闸门）

主闸门 `a.a.a.ue8#m49136(Context)`（`ue8.java:31-45`），三分支：

| 行 | 条件 | 结果 | 语义 |
|---|---|---|---|
| `ue8.java:32` | `y8c.f53030.mo9320() && DeviceUtil.isBrandP()` | `null` | 品牌/机型抑制 |
| `ue8.java:35` | `!pd3.m37212().m37242()` | `show_type=0` | **默认首展路径（真机命中的就是这条）** |
| `ue8.java:38` | `exd.m12640()` | `show_type=1` | "再次展示"窗口 |
| `ue8.java:41` | `x6c.m55260()` | `show_type=2` | 概率展示 |
| `ue8.java:44` | 以上都不满足 | `null` | 不展示 |

上游还有一层频控：`c4b.m5362` 首行 `if (k56.m24569()) return null;`（`c4b.java:37`）。

各闸门的持久化键（**全部为 SharedPreferences**，`o2c.getSharedPreferences(...)`）：

| 闸门方法 | 键字符串 | 默认值 | 证据 file:line |
|---|---|---|---|
| `pd3#m37242()` | `"pref.use.install.require.show"` | `false` | 键：`pd3.java:25`；读：`pd3.java:382`；写：`pd3.java:511`（旁有日志 `pd3.java:513` `"sub process want to call setInstallRequireShowed"`） |
| `x6c#m55260()` 概率 | `"pref.install.require.show.probability"` | `0.0f` | `x6c.java:16`（读 `x6c.java:27-28`） |
| `x6c` 上次展示时间 | `"pref.install.require.by.probability.last.show.time"` | `-1` | `x6c.java:19`（读 `x6c.java:22-23`） |
| `exd#m12640()` 状态 | `"pref.install.require.info"`（JSON→`InstallRequireInfo`） | 空 | `exd.java:14`；判定 `exd.java:70-84`（要求 `startTime<now<endTime && now-lastShowTime>86400000 && !clickedInstall && !againShowed`） |

**闸门充分性论证（用于拦截策略 A）**：若令 `pd3#m37242()` 返回 `true`，则 `ue8.java:35` 不再命中；剩余两条为
`exd.m12640()`（需有效时间窗 + 距上次 >24h + 未点安装 + 未再次展示）与 `x6c.m55260()`（概率默认 `0.0f` → `x6c.java:35-37` 直接 `return false`），
在出厂默认配置下均为 `false` ⇒ `m49136` 返回 `null` ⇒ **不展示**。

### 2.4 谁在执行这条链（冷启动链）

- 接口：`a.a.a.u09`（`u09.java:7`），链式执行器 `a.a.a.xc2`，日志 TAG `"launch_task"`（`xc2.java:14`）。
- 链成员（全部 `implements u09`）：`b2g`(隐私) → `fva`(权限) → `d4b`(开机必备) → `ncg`(延时) → `ts6`(进主页, `ts6.java:11`)。
- 链宿主（splash/launch 控制器）：`a.a.a.q09` `[dex classes4.dex]`，方法字符串含
  `launch_task`、`no need to run launch process cause is recreating`、`getUserPrivacy(...)`、`needShowPrivacyDialog`。
- `xc2#m55721(u09)` 与 `new d4b(...)`、`new b2g(...)` 在 `sources` 内**均无调用点** ⇒ 组装发生在 `q09`（jadx 缺失）内。
- **另一套并行的"开机必备"**（ColorOS 联动，勿混淆）：
  `com.heytap.cdo.client.cn.external.bootreg.OpenGuideReceiver`
  监听 `"com.coloros.bootreg"` / `"com.oplus.bootreg.action.network"`（`OpenGuideReceiver.java:27,30,33`），
  命中后 `l57.m27002().handleBoot(context, 1)`（`OpenGuideReceiver.java:65`）；
  页面类 `…bootreg.OpenGuideActivity` / `OpenGuideFragment`（`[dex classes4.dex]`，jadx 缺失）。

---

## 3. "AI 搜索引导弹窗"类与闸门（任务 3）

### 3.1 类与形态

| 项 | 值 | 证据 |
|---|---|---|
| 实现类 | `a.a.a.p`（日志标记 **`AISearchBubbleUtil`**） | `[dex classes5.dex] La/a/a/p;.\u052f` 字符串 `"AISearchBubbleUtil"`、`"Show bubble failed"` |
| 基类 | `java.lang.Object`（**不是** PopupWindow 子类） | `[dex classes5.dex] La/a/a/p; super=Ljava/lang/Object;` |
| 实际 UI 形态 | **COUI 气泡**：`com.coui.appcompat.tooltips.COUIToolTips` ← `COUIPopupWindow` ← `android.widget.PopupWindow` | `COUIToolTips.java:47` `COUIToolTips extends COUIPopupWindow`；`COUIPopupWindow.java:17` extends PopupWindow |
| ⇒ 与真机 `mCurrentFocus=PopupWindow` | **完全吻合**（COUIToolTips 本体就是 PopupWindow） | 同上 |

### 3.2 show 调用点

| 锚点(类#方法) | 类型 | 证据 | 形状判据 |
|---|---|---|---|
| `a.a.a.p#\u0588`（dex 名；jadx 未输出该类，无 jadx 名可引） | 私有 show 实现 | `[dex classes5.dex] La/a/a/p;.\u0588`：`Context.getString` + 取 `R$string.ai_search_guide_{title,content,dismiss}` + `COUIImageBubbleStyleImpl$Builder.setTitle/setContentText/setDismissText/setMediaResourceWithEdges/loadImage/build` + `new COUIToolTips` + `setDismissOnTouchOutside` + **`showWithDirection`** | 唯一构造气泡处 |
| `a.a.a.p#\u052a` | 对外 show 入口 | `[dex] La/a/a/p;.\u052a` → invokes `\u052e`(判定) + `\u052f`(展示)。jadx 侧对应调用点：`w.java:171` `this.mBubbleUtil.m36042(this.mAISearchIconView)`（∅ 注：`\u052a` 与 jadx 名 `m36042` 的映射为**按调用形态推断**，因 `p.java` 缺失无法直接核对；hook 时请以 dex 名 `\u052a` 为准） | **show 入口** |
| `a.a.a.p#\u052e` | 可见性/一次性判定 | `[dex] La/a/a/p;.\u052e`：`View.getVisibility` + `AtomicBoolean.get`，读字段 `Ϳ`、`ԩ` | **内层闸门** |
| `a.a.a.p$a#onLoadingComplete` | 图片预载回调 → 真正显示 | `[dex] La/a/a/p$a;.onLoadingComplete -> La/a/a/p;.\u0588` | show 在图片加载成功后才发生 |
| 图片资源 | 硬编码远程 PNG ×2 | `[dex] La/a/a/p;.\u052b`：`https://store.heytapimage.com/uploadFiles/admin_appstore/202510/31/d3614ea618bbdd79e244a36c1f72a428.png` 与 `…7daaa6e49bfaab43e55c6075b6493afa.png` | 即"气泡配图"，网络失败则 `onLoadingFailed` → `\u0528` |

**jadx 侧可读调用点（`sources\a\a\a\w.java`，w = AI 搜索图标 presenter）**：

| 行 | 代码 | 作用 |
|---|---|---|
| `w.java:28` | `private p mBubbleUtil = new p();` | 持有 AISearchBubbleUtil |
| `w.java:103` | `this.mBubbleUtil.m36038();` | initView 时重置 |
| `w.java:157` | `this.mBubbleUtil.m36039(true);` | onPause 置位 |
| **`w.java:171`** | **`this.mBubbleUtil.m36042(this.mAISearchIconView);`** | **★展示气泡（onResume）** |
| `w.java:176` | `this.mBubbleUtil.m36036();` | onStop 时 dismiss |
| `w.java:165-167` | `if (this.mRealShowType == 0) return;` | 图标类型为 0 直接 return（不展示气泡） |

### 3.3 显示判定闸门

| 锚点(类#方法) | 类型 | 证据 file:line | 形状判据 | 是否可做闸门 | 风险 |
|---|---|---|---|---|---|
| **`a.a.a.q#m38674()`** | `static boolean` | `q.java:90-132`（日志 TAG `AISearchConfigHelper`，`q.java:14`） | AI 搜索总开关：SDK<28 / 折叠屏翻盖平板 / 非 CN 区域 / 未成年模式 / 用户关闭(`c1c.m5103()`) / `c1c.m4898()&&isShowAi` / H5Url 空 / 非完整模式 → `false` | **可**（返回 `false` 即整块关闭 AI 搜索入口+气泡） | 低；但会**同时隐藏入口图标**，属"关功能"非"只关广告" |
| `a.a.a.p#\u052e`（内层） | 实例判定 | `[dex classes5.dex]` | `anchorView.getVisibility()` + 内存 `AtomicBoolean` 已展示标记 | 可（成本更低、粒度最准） | 最低；仅影响气泡 |
| `a.a.a.p#\u052a`（`w.java:171` 调用者） | show 入口 | `w.java:171` + `[dex]` | 唯一对外入口，锚点传参为 `mAISearchIconView` | **可** | 最低 |
| 持久化键 | — | **未证实**：`a.a.a.p` 内 12 个方法的全部 `const-string` 中不含 `pref.*` 键；判定仅为进程内 `AtomicBoolean`（字段 `Ϳ`/`ԩ`） | — | — | 即"每次冷启动可再弹一次" |
| 相关配置 DTO | — | `q.java:76` `"ai_guide_words"`；`AiSearchGuideWordDto.java:9-12`（`aiMaterialId`,`word`）；`AiConfigDto` `q.java:81-87` | 服务端配置 | 否 | — |

---

## 4. 弹窗广告的通用形态与统一入口（任务 4）

### 4.1 形态归纳

| 形态 | 实体 | 证据 | 说明 |
|---|---|---|---|
| **PopupWindow** | `NativeAdPopupWindow`（内部 `new PopupWindow` + `showAtLocation`） | `[dex classes4.dex] …NativeAdPopupWindow;.\u0868` 含 `Landroid/widget/PopupWindow;-><init>`、`setContentView`? `setBackgroundDrawable`、`setFocusable`、`setOnDismissListener`、**`showAtLocation`**，字符串 `"showPopupWindow failed, fragment invisible."`、`"Float show success cost time:"` | **首页/浮层原生广告弹窗**（悬浮广告形态） |
| **PopupWindow（气泡）** | `COUIToolTips` ← `COUIPopupWindow` ← `PopupWindow` | `COUIToolTips.java:47`、`COUIPopupWindow.java:17` | App 内**统一的气泡/指引弹窗**（AI 搜索引导、侧滑指引、卸载回收站指引等都用它） |
| **PopupWindow（通用 tips）** | `a.a.a.ed0` | `[dex classes5.dex] La/a/a/ed0;.\u0789` → `PopupWindow.isShowing` + **`showAtLocation`**；字符串 `PIC3_APP3`、`banner_setting_param` | 图表/tips 浮窗，调用者 `com/heytap/market/appusage/view/AppUsageBarChart` |
| **Activity 当弹窗** | `CtaDialogActivity`（`extends BaseActivity`，dialog 主题） | `[dex classes4.dex]`；`onCreate` 字符串 `key.cta.type`、`Launch fail:` | **通用 CTA 弹窗广告**，由 `CtaManager`(`a.a.a.hg3`) 驱动 |
| **Activity 当整页** | `InstallRequireActivity` / `OpenGuideActivity` | `ue8.java:25`；`[dex classes4.dex]` | 开机必备引导页 |
| **Dialog** | `a.a.a.qaa`、`a.a.a.t69`、`a.a.a.oaa`、`a.a.a.x6h`、`a.a.a.eob$a$a` | `qaa.java:18`、`t69.java:12`；其余 `[dex]` | 非广告向（登录/通用提示等） |

### 4.2 有没有统一 show 入口？

| 候选统一入口 | 结论 | 证据 |
|---|---|---|
| `COUIToolTips#showWithDirection` / `#show` | **是**——App 内气泡类指引弹窗的统一入口 | 全 dex xref：调用者含 `La/a/a/p;.\u0588`(AI 搜索)、`AppContentFlowFragment`(侧滑指引)、`StorageCleanUninstallAppsFragment`、`AppRecoveryEmptyPage`、`appscan` 等 |
| `a.a.a.hg3`(=`CtaManager`)#showCTA | **是**——CTA 弹窗广告的统一入口 | `[dex classes4.dex] La/a/a/hg3;.showCTA`，字符串 `CtaManager: needShowCtaDialog: `、`launch_task`；`CtaDialogActivity.\u08c7` → `hg3.onConfirm` |
| `NativeAdPopupWindow` | 单一实现（未发现调度器） | 构造点唯一：`La/a/a/ye;.\u0588`（字符串 `requestFloatingUrl:`、`floating_layer`） |
| 全局"所有弹窗"统一 dispatcher | **未证实** | 未发现可证明的统一分发器 |

### 4.3 全部 `extends PopupWindow` 类清单（dex 权威，按 ad/popup 相关度排序）

`android.widget.PopupWindow` 的**全量传递闭包 = 8 个类**（全 10 dex 统计）：

| # | 类名（全限定） | 基类 | dex | 相关度 | 依据 |
|---|---|---|---|---|---|
| 1 | `com.coui.appcompat.tooltips.COUIToolTips` | `COUIPopupWindow` | classes3 | **高** | AI 搜索引导气泡的实体类 |
| 2 | `com.coui.appcompat.poplist.COUIPopupWindow` | `PopupWindow` | classes3 | **高** | 上述基类，App 气泡体系根 |
| 3 | `com.coui.appcompat.poplist.COUIPopupListWindow` | `COUIPopupWindow` | classes3 | 中 | 列表型浮窗 |
| 4 | `com.coui.appcompat.poplist.COUIIsolatedPopupListWindow` | `COUIPopupWindow` | classes3 | 中 | 同上（隔离版） |
| 5 | `a.a.a.ed0` | `PopupWindow` | classes5 | 中 | App 自有 tips 浮窗（`showAtLocation`） |
| 6 | `com.platform.usercenter.mctools.ui.CommonPopupWindow` | `PopupWindow` | classes9 | 低 | 权限引导浮窗 |
| 7 | `com.platform.usercenter.support.dialog.CommonPopupWindow` | `PopupWindow` | classes10 | 低 | 同上（support 版） |
| 8 | `androidx.appcompat.widget.AppCompatPopupWindow` | `PopupWindow` | classes | 低 | 三方库 |

> 非子类但**直接构造/显示 PopupWindow** 的广告类（必须单列，否则会漏）：
> `com.heytap.cdo.client.advertisement.bussiness.NativeAdPopupWindow`（`[dex classes4.dex]`，`extends Object`）。

`extends Dialog` / `DialogFragment` / `BottomSheetDialog` 闭包（共 19 / 4 / 3 个）全部为框架或支付/协议类，**无广告类**；`AlertDialog` 子类 = 0。

---

## 5. App 内"广告/推荐"相关非混淆类名（任务 5）

### 5.1 广告核心（最强相关）

| 类名 | 基类 | dex |
|---|---|---|
| `com.heytap.cdo.client.advertisement.bussiness.NativeAdPopupWindow` | Object（用 PopupWindow） | classes4 |
| `com.heytap.cdo.client.advertisement.presentation.FloatAdPresenter` | `RecyclerView$OnScrollListener` | classes4 |
| `com.heytap.cdo.client.advertisement.presentation.*`（`$a`~`$e` 内部类） | — | classes4 |
| `com.heytap.cdo.client.advertisement.bussiness.NativeAdPopupWindow$*`（`$a`~`$i`） | — | classes4 |
| `com.heytap.cdo.buoy.domain.dto.BuoyDto` / `AdInfoDto` | Object | classes4/5 |
| `com.heytap.cdo.floating.domain.v2.PopoverDto` / `LongTakeDto` / `LongTakeMaterial` | Object | classes5 |
| `com.heytap.cdo.common.domain.dto.ad.AdInfoDto` / `AdNegativeReasonDto` | Object | classes4 |
| `com.heytap.cdo.card.domain.dto.detail.AppAdSlotDto` / `subject.AdvertisementItemDto` | Object | classes4 |
| `com.heytap.card.main.api.model.CpdAdInfoSerialize` / `ContractAdInfoSerialize` | Object | classes4 |
| `com.heytap.card.main.core.cn.instant.help.InstantGameAdsLoaderHelper` | Object | classes4 |

### 5.2 弹窗/CTA

| 类名 | 基类 | dex |
|---|---|---|
| `com.heytap.cdo.client.cta.CtaDialogActivity`（+`$a`,`$b`,`$c`） | `BaseActivity` | classes4 |
| `com.heytap.cdo.client.cta.CtaUserPrivacyLaunchTask` | Object | classes4 |
| `com.heytap.cdo.client.cta.Basic2FullCtaUserPrivacyLaunchTask` | Object | classes4 |
| `a.a.a.hg3`（**CtaManager**） | Object | classes4 |
| `com.heytap.cdo.client.VerifyPopupActivity` | `ComponentActivity` | classes5 |

### 5.3 开机必备 / 引导

| 类名 | 基类 | dex |
|---|---|---|
| `com.heytap.cdo.client.cn.external.bootreg.OpenGuideActivity` | `BaseActivity` | classes4 |
| `com.heytap.cdo.client.cn.external.bootreg.OpenGuideFragment` | `BaseFragment` | classes4 |
| `…bootreg.presenter.OpenGuideAdapterPresenter` / `OpenGuideBtnInstallPresenter` | `pnf` / Object | classes4 |
| `…bootreg.adapter.OpenGuideCardAdapter` / `BaseOpenGuideCardAdapter` | `CardApiRecyclerViewAdapterProxy` | classes4 |
| `…bootreg.OpenGuideReceiver` / `…bootreg.OpenGuidePageLoader` / `OpenGuidePageStatus` | `BroadcastReceiver` / `DefaultNetworkLoader` / Object | classes4 |
| `com.heytap.cdo.client.ui.external.bootreg.OpenGuideReceiver` | `OpenGuideReceiver` | classes5 |
| `com.heytap.cdo.client.cards.page.openphone.installrequire.activity.InstallRequireActivity` | `BaseActivity` | classes4 |
| `com.heytap.card.main.api.openphone.installrequire.fragment.card.InstallRequireInfo` | Object | classes4 |
| `com.heytap.card.main.api.config.SecondFloorGuideGAConfigHelper` | Object | classes4 |
| `com.heytap.cdo.client.cards.page.main.home.util.SecondScreenGuidePresenter` | Object | classes4 |

### 5.4 悬浮窗 / 推荐 / 福利

| 类名 | 基类 | dex |
|---|---|---|
| `com.heytap.cdo.client.downnotice.FloatingWindowService` | `Service` | classes5 |
| `com.heytap.cdo.client.downnotice.DownloadFloatingManager` | Object | classes5 |
| `com.heytap.cdo.client.downnotice.NotificationGuide` | Object | classes5 |
| `com.heytap.cdo.configx.domain.dynamic.GpFloatingWindowConfig` / `FloatingWindowConfigParam` | Object | classes5 |
| `com.heytap.cdo.client.cards.page.external.recapp.ExternalRecommendAppActivity` / `…Fragement` | `DividerToolBarActivity` / `CardFragment` | classes4 |
| `com.heytap.cdo.client.ui.recommend.ManagerRecommendPageActivity` / `…Fragment` | `BaseToolbarActivity` / `BaseLoadingFragment` | classes5 |
| `com.heytap.cdo.client.cards.page.rank.RankRecommendCardActivity` / `…Fragment` | `BaseActivity` / `CardFragment` | classes4 |
| `com.heytap.card.api.view.stage.StageDailyRecommendLayout` | `RelativeLayout` | classes4 |
| `com.heytap.cdo.card.domain.dto.{WelfareCardDto,WelfareListCardDto,DailyRecommendDto,EveryDayRecommendDto,LotteryCardDto,TipsCardDto}` | Object | classes4 |
| `com.heytap.cdo.card.domain.dto.detail.{WelfareActivityDto,WelfareGiftDto,BookWelfareCardDto,GameWelfareCardDto}` | Object | classes4 |

（完整清单见 §8 的 `_evidence_dex5.txt`，其中 `##### ad/popup-related readable classes` 段共 1860 行。）

---

## 6. 锚点总表（含形状判据 / 可闸门性 / 风险）

| 锚点(类#方法) | 类型 | 证据 file:line | 形状判据 | 是否可做闸门 | 风险 |
|---|---|---|---|---|---|
| `a.a.a.ue8#m49136(Context)` | `static Intent` | `ue8.java:31-45`；调用者唯一 `c4b.java:43` | 返回 Intent 或 `null`，内部 3 分支决定 show_type | **★可（首选）** | 低。仅影响开机必备页；`ue8.m49135` 是原始构造器，勿动 |
| `a.a.a.c4b#m5362(Context)` | `static Intent` | `c4b.java:35-51` | 前置频控 `k56.m24569()`(`c4b.java:37`) 后转 `ue8.m49136` | 可 | 低。比 `ue8` 多包一层，仍只服务该页 |
| `a.a.a.zy8#m62168(Activity,Intent)` | `static void` | `zy8.java:52-59` | **唯一 `activity.startActivity(intent)` 处** | 可（最靠近副作用） | 中。`az8.java:27` 也走这里；需判 `intent.getComponent()` 是否为 InstallRequireActivity |
| `a.a.a.pd3#m37242()` | `boolean` | 定义 `pd3.java:381-383`；**唯一调用点 `ue8.java:35`** | 读 `"pref.use.install.require.show"`，默认 `false` | **★可（一次性持久闸门）** | 低。返回 `true` 即跳过默认首展分支 |
| `a.a.a.x6c#m55260()` | `static boolean` | `x6c.java:32-53` | 概率闸门，概率 `0.0f` 时 `x6c.java:35-37` 直接 false | 可（次要） | 低 |
| `a.a.a.d4b#mo2892(Activity,bz8)` | `void` | `d4b.java:30-37`；`d4b.java:34` | launch_task 链成员，`isNewBasicMode()` 时改走 `bz8Var.mo4702()` | 可 | 低。需保持链回调（`onComplete`/`mo4702`）被调用，否则卡启动 |
| `a.a.a.q#m38674()` | `static boolean` | `q.java:90-132` | AI 搜索总开关（含地区/机型/模式/H5Url） | **可** | 中。会隐藏 AI 搜索入口图标（关功能 ≠ 只关广告） |
| `a.a.a.p#\u052a`（`w.java:171` 调用） | 实例方法 | `w.java:171` + `[dex classes5.dex]` | 气泡唯一对外 show 入口 | **★可** | 最低。仅影响该气泡 |
| `a.a.a.p#\u052e` | 实例判定 | `[dex classes5.dex]` | `View.getVisibility()` + `AtomicBoolean` | 可 | 最低 |
| `a.a.a.hg3#showCTA` | 实例方法 | `[dex classes4.dex]`（`CtaManager`） | CTA 弹窗广告统一入口 | 可 | 中。勿动 `onConfirm/onCancel/showBasic2FullCta`（基础→完整模式升级流程） |
| `com…cta.CtaDialogActivity#onCreate` | Activity | `[dex classes4.dex]`，字串 `key.cta.type` | 以 Activity 承载弹窗 | 可（兜底） | 中。同 CTA 通道 |
| `com…NativeAdPopupWindow#\u0868` | 实例方法 | `[dex classes4.dex]` | `new PopupWindow` + `showAtLocation` | 可 | 低-中。仅浮层原生广告 |
| `a.a.a.b2g#mo2892` | `void` | `b2g.java:36` → `z1g.m59665().showPrivacyDialog(...)` | 用户须知协议（法律条款，**不应拦**） | 否（按要求不拦） | 拦了会违反协议同意流程 |

---

## 7. 推荐的拦截策略

> 前提：以下均为 **LSPosed/Xposed 只读 hook**，不修改 APK。所有目标类在运行时真实存在（§0 已证）。

### 方案 A（首选）：`a.a.a.ue8#m49136` 返回 `null` —— 精准摘掉"开机必备"页

```java
XposedHelpers.findAndHookMethod("a.a.a.ue8", lpparam.classLoader,
        "m49136", android.content.Context.class,
        new XC_MethodReplacement() {
            @Override protected Object replaceHookedMethod(MethodHookParam p) {
                return null;                 // 不返回 Intent ⇒ 不启动 InstallRequireActivity
            }
        });
```

**为什么不会误杀正常界面**
1. `m49136` 的**唯一调用者是 `c4b.m5362`**（`c4b.java:43`）；`c4b.m5362` 的唯一调用者是 `zy8.m62164`（`zy8.java:27`）；`zy8.m62164` 的唯一调用者是 `d4b.m7783`（`d4b.java:25`）。整条链只服务"开机必备"页。
2. 返回值语义就是"要不要给 Intent"，返回 `null` 正是**代码自身**表达"不展示"的方式（`ue8.java:44`、`c4b.java:41`），因此下游无需特殊处理。
3. 不触碰 `ue8.m49135`（原始 Intent 构造器），不触碰 `zy8.m62168`（通用 `startActivity` 通道，其他功能共用）。
4. 已核对：`c4b.m5363` 是另一条入口，但**在 `sources` 与 dex 中均无调用者**（死路径），不受影响。
5. 开销：该链由 `a.a.a.q09` 的 launch 流程在**冷启动时执行一次**，非高频路径。

**风险 / 注意**：会同时屏蔽 `show_type=1`（再次展示）与 `show_type=2`（概率）两个变体——这正是预期。
若发现"开机必备"仍出现，说明命中了 §2.4 的 **ColorOS bootreg 通道**（`OpenGuideActivity`），需另加方案 A'。

### 方案 A'（备用，覆盖 ColorOS 联动通道）

```java
// 该类的唯一作用是响应 com.coloros.bootreg 广播并拉起开机必备页
XposedBridge.hookAllMethods(XposedHelpers.findClass(
        "com.heytap.cdo.client.cn.external.bootreg.OpenGuideReceiver",
        lpparam.classLoader), "onReceive", XC_MethodReplacement.returnConstant(null));
```
依据：`OpenGuideReceiver.java:27,30,33`（`com.coloros.bootreg` / `com.oplus.bootreg.action.network`）、`OpenGuideReceiver.java:65`（`l57.m27002().handleBoot(context, 1)`）。
**不误杀**：该类是 `BroadcastReceiver`，只处理这两个动作；`onReceive` 无返回值，直接丢弃不影响其他广播。

### 方案 B：摘掉"AI 搜索引导弹窗"

提供两种粒度，**B1 副作用最小但有名称风险，B2 锚点最可靠但会连入口图标一起隐藏**。二选一即可，不要都做。

**B1（最精准）—— 短路 AISearchBubbleUtil 的 show 入口**

```java
// 目标类 a.a.a.p（日志 TAG AISearchBubbleUtil）。方法名为 dex 原始名 \u052a（无 jadx 名可用，因该类未被 jadx 输出）
XposedBridge.hookAllMethods(XposedHelpers.findClass("a.a.a.p", lpparam.classLoader),
        "\u052a", XC_MethodReplacement.returnConstant(null));
```

**B2（锚点最可靠）—— 关闭 AI 搜索总开关**

```java
// q.java 在 jadx 输出中存在，方法名 m38674 经原文核对（q.java:90）
XposedHelpers.findAndHookMethod("a.a.a.q", lpparam.classLoader, "m38674",
        XC_MethodReplacement.returnConstant(false));
```

**为什么不会误杀正常界面**
1. `a.a.a.p` 是 **AI 搜索专用**类（日志 TAG `AISearchBubbleUtil`，`[dex classes5.dex]`）；跨类引用仅 `a.a.a.w`（`w.java:28,103,157,171,176`）与其自身内部类 `p$a`（图片回调），无其他功能复用。
2. **关键：不要 hook `COUIToolTips`。** `COUIToolTips#showWithDirection` 是全 App 气泡指引的统一入口（`[dex] `xref 已列全部构造方），调用方还包括侧滑指引（`AppContentFlowFragment`）、卸载回收站指引（`StorageCleanUninstallAppsFragment`、`AppRecoveryEmptyPage`）、应用扫描等。hook 它会大面积误杀正常引导。选择 hook `a.a.a.p` 正是为了避开这一点。
3. 可读文案仍由资源 ID 提供（`res_dump.txt:35913/35920/35927`），短路后不产生任何 UI。
4. 开销：仅在 `w.onResume`（生命周期回调）触发，非绘制/滑动热路径。
5. B2 的代价：`q.m38674()`（`q.java:90-132`）同时决定 AI 搜索**入口图标**是否显示，返回 `false` 会把入口一起隐藏 —— 属"关功能"，非"只关广告"。若能接受，B2 的锚点可读性最好（方法名来自 jadx 原文，无需依赖 `\u052a` 转义）。

### 方案 C：`a.a.a.hg3#showCTA` 短路 —— 摘掉通用 CTA 弹窗广告

```java
XposedBridge.hookAllMethods(XposedHelpers.findClass("a.a.a.hg3", lpparam.classLoader),
        "showCTA", XC_MethodReplacement.returnConstant(null));
```
依据：`[dex classes4.dex] La/a/a/hg3;.showCTA`，字串 `CtaManager: needShowCtaDialog: `、`launch_task`；弹窗实体 `CtaDialogActivity`（`key.cta.type`）。

**为什么不会误杀正常界面（及必须遵守的边界）**
1. 只 hook **`showCTA`**（通用 CTA 弹窗入口）。**严禁** hook `hg3` 的 `onConfirm` / `onCancel` / `onCancelWithoutClear` / `onAlreadyPassCta`——它们被 `CtaDialogActivity.\u08c4/\u08c5/\u08c7` 回调（`[dex]`），是用户交互与隐私同意流程的必经环节，hook 会导致弹窗点不掉或协议流程卡死。
2. **不要**动 `showBasic2FullCta`：它服务于"基础模式 → 完整模式"升级（`Basic2FullCtaUserPrivacyLaunchTask`，`[dex classes4.dex]`），属功能流程而非广告。
3. 开销：事件驱动（冷启动/回前台），非高频。

### 方案 D（可选，零 hook 的持久闸门）：写 SharedPreferences

键 `pref.use.install.require.show`（`pd3.java:25`）置为 `true`：
`pd3#m37242()`（`pd3.java:381`）变 `true` ⇒ `ue8.java:35` 分支不再进入；
剩余 `exd.m12640()`（`exd.java:79` 需有效时间窗 + >24h + 未点安装 + 未再次展示）与 `x6c.m55260()`（概率默认 `0.0f`，`x6c.java:35-37` 直接 `false`）在默认配置下均为 `false` ⇒ `m49136` 返回 `null`。
优点：无需 hook、天然持久、开销为零。注意：**值必须为 `true`**（默认 `false` 恰恰会触发 `show_type=0` 展示，方向易搞反）。

### 推荐组合

1. **A + B**（互不干扰、粒度最准）：摘掉"开机必备页"+"AI 搜索引导弹窗"。
2. 若真机仍出现 CTA 弹窗，再叠加 **C**。
3. `b2g`（用户须知）按需求**不拦**。
4. 悬浮广告（`FloatAdPresenter` / `NativeAdPopupWindow`）本任务不细究；如需处理，闸门在 `NativeAdPopupWindow#\u07a0`（字串 `isCanShow == false`、`PopoverDto.getShowTime`，`[dex classes4.dex]`）。

---

## 8. 证据索引与复现方法

本次为取证自建的 DEX 解析器（只读，不依赖 apktool/jadx CLI）：

| 脚本 | 作用 |
|---|---|
| `E:\hmc\tools\dexconst3.py` | 全 dex `const-string` / `const <resid>` 指令级扫描（含已知串校验） |
| `E:\hmc\tools\dexfield.py` | 按 `R$string` 字段名反查 `sget` 引用者 |
| `E:\hmc\tools\dexanal.py` | 指定类的 super/methods/字符串转储 |
| `E:\hmc\tools\dexxref.py` | 跨类方法调用（invoke）xref |
| `E:\hmc\tools\dextree.py` | 全 dex 继承树闭包（PopupWindow/Dialog 等） |
| `E:\hmc\tools\dex6b.py` / `dex7.py` / `dex8.py` | 定向类转储与 xref |

解析产物（可复核）：

| 文件 | 内容 |
|---|---|
| `E:\hmc\docs\_evidence_dex.txt` | 解析器校验（`pref.use.install.require.show`→`r2g`/`s2g`；`launch_task`→`xc2`/`q09`/`CtaDialogActivity`/`hg3.showCTA`） |
| `E:\hmc\docs\_evidence_dex2.txt` | `ai_search_guide_{content,dismiss,title}` 的 `sget` 引用者 = `La/a/a/p;.ֈ`（classes5.dex） |
| `E:\hmc\docs\_evidence_dex3.txt` | `a.a.a.p` / `InstallRequireActivity` / `CtaDialogActivity` / `q09` / `hg3` 的类结构 |
| `E:\hmc\docs\_evidence_dex4.txt` | `AISearchBubbleUtil` 全量转储 + `CtaManager`/`CtaDialogActivity`/`InstallRequireActivity` 的 xref |
| `E:\hmc\docs\_evidence_dex5.txt` | 全 dex 继承树闭包（PopupWindow 8 个）与广告相关可读类名（1860 行） |
| `E:\hmc\docs\_evidence_dex6.txt` | `a.a.a.ed0`（PopupWindow）全量转储 |
| `E:\hmc\docs\_evidence_dex7.txt` | `ed0` 调用者、`COUIToolTips` 全部构造方、AI 气泡入口 xref |
| `E:\hmc\docs\_evidence_dex8.txt` | `NativeAdPopupWindow` / `FloatAdPresenter` 全量转储 |

### 未证实项（明确列出，勿当结论）

- AI 搜索引导气泡的**持久化**"已展示"标记：`a.a.a.p` 12 个方法内无 `pref.*` 键，判定仅为进程内 `AtomicBoolean`（字段 `Ϳ`/`ԩ`）→ **未证实**存在落盘一次性闸门。
- 全 App "所有弹窗"的**统一 dispatcher**：**未证实**（仅证实了 `COUIToolTips#showWithDirection` 与 `hg3#showCTA` 两个**分域**统一入口）。
- `a.a.a.w`（AI 搜索 presenter）的实例化点：`new a.a.a.w` 在全 dex 无 xref → 推断为 `@RouterService(interfaces={iz6.class})`（`w.java:16`）反射创建，**未证实**具体创建者。
- 用户须知弹窗的**精确触发条件链**（`b2g` 之外的上游判定）未展开（按要求不拦截，未深挖）。
