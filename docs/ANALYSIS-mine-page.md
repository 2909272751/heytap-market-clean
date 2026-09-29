# OPPO 软件商店 26.5.2_CN 「我的」页构建方式与入口控件分析

> 只读分析（未修改任何被测程序代码）。分析对象：`com.heytap.market` 26.5.2_CN，APK：`E:\hmc\orig\market-26.5.2.apk`。
> 反编译工具：jadx **1.5.6**（另有用户已有的 `E:\hmc\jadx-out`，**不完整**，见文末「证据来源与限制」）。

## 0. 结论速览

| 问题 | 结论 |
|---|---|
| 谁构建「我的」页 | `com.heytap.market.mine.MineFragment`（普通 `BaseFragment`，**不是** CDO CardFragment 卡片页），布局 `fragment_mine.xml` |
| 列表 | `CdoNestedScrollRecyclerView [mine_list_view]` + 适配器 `com.nearme.cards.adapter.RecyclerViewCardAdapter`（`CardApiRecycleViewAdapter` 的实现），内容 = header 视图 + 服务端 CDO 卡片列表（**数据驱动**） |
| 能否在"构建卡片数据那一刻"过滤 | **能**。最安全的页内点是 `MineFragment.mo881(ViewLayerWrapDto, boolean)`（`E:\hmc\jadx-mine\sources\com\heytap\market\mine\MineFragment.java:874-879`）；框架级点是 `RecyclerViewCardAdapter.exeCardDtoRules(List)`（`...\RecyclerViewCardAdapter.java:501-523`）；卡内条目级点是 `AppHealthAndCleanCard.mo45850(CardDto)`（`...\apphealth\AppHealthAndCleanCard.java:181-197`） |
| 顶栏入口点击 | 全部集中在 `MineActionBarView.onClick(View)`（`E:\hmc\jadx-mine\sources\com\heytap\market\mine\view\MineActionBarView.java:277-293`） |
| 是否存在"入口是否显示"的开关 | **部分存在**：服务端 `ViewLayerWrapDto.getExtFeature().getSwitches()`（`type`/`status`）+ 本地 `SharedPreferences` 缓存。可覆盖 `online_feedback`(反馈)、`account_nameplate`(账户铭牌)、`sign_function`(签到)、AI 搜索等；**未发现**针对 待更新/应用卸载/下载管理/存储空间清理/应用健康状态/banner/推荐卡 的"是否显示"开关 |

---

## 1. 「我的」页是哪个类/Fragment 构建的

### 1.1 页面宿主

| 项 | 值 | 证据（file:line） |
|---|---|---|
| Fragment 类 | `com.heytap.market.mine.MineFragment`（`extends com.nearme.module.ui.fragment.BaseFragment implements a.a.a.ao7`） | `E:\hmc\jadx-mine\sources\com\heytap\market\mine\MineFragment.java:105` |
| 布局 inflate | `inflater.inflate(R.layout.fragment_mine, null)` | `E:\hmc\jadx-mine\sources\com\heytap\market\mine\MineFragment.java:731-739` |
| 布局文件 | `fragment_mine.xml` = FrameLayout{ `mine_list_view`, `MineActionBarView(mine_action_bar)` } | `E:\hmc\jadx-res\resources\res\layout\fragment_mine.xml:2-17` |
| Fragment 路由注册 | `@RouterService(interfaces={dh7.class}, key=l46.f22343)` 的 `a.a.a.x3a.getFragment()` 返回 `MineFragment.class` | `E:\hmc\jadx-out\sources\a\a\a\x3a.java:8-13` |
| ViewPager 第 5 页 | 该页由主框架 `CdoViewPager` 承载，`MineFragment` 的视图被挂在无 id 的 `FrameLayout` 下（真机树） | 运行时证据：`E:\hmc\recon\mi_mine2_tree.txt:415-419` |

> 说明：它**不是** `com.heytap.cdo.client.cards.page.base.CardFragment` 那种"纯 CDO 卡片页"——`MineFragment` 自己 inflate 布局、自己建 adapter，只把"卡片列表"这一层交给 CDO 卡片框架（见第 2 节）。旁证：`CardFragment` 是另一套（`E:\hmc\jadx-all\sources\com\heytap\cdo\client\cards\page\base\CardFragment.java:91-97`），`MineFragment` 并不继承它。

### 1.2 关键控件类（按你要求逐个定位）

| 控件/类 | 全名 | 声明处 | 使用处 |
|---|---|---|---|
| `mine_list_view` `0x7f0907cf` | `com.nearme.uikit.widget.recycler.CdoNestedScrollRecyclerView` | 布局 `fragment_mine.xml:5-11` | `MineFragment.java:465-468`（findViewById + setAdapter） |
| `MineActionBarView` | `com.heytap.market.mine.view.MineActionBarView` | `E:\hmc\jadx-mine\sources\com\heytap\market\mine\view\MineActionBarView.java:42` | `MineFragment.java:449`；布局 `fragment_mine.xml:12-16`；自身 inflate `R.layout.mine_actionbar_view`（`MineActionBarView.java:141`） |
| `MyGameVipCard` `vip_layout` `0x7f090e11` | `com.nearme.platform.account.vip.MyGameVipCard`（`extends GameVipCard`） | `E:\hmc\jadx-mine\sources\com\nearme\platform\account\vip\MyGameVipCard.java:26` | 由 `a.a.a.t9` 创建：`t9.java:30` inflate `fragment_mine_heytap_vip_view`，`t9.java:105-107` 通过 `ViewStub(vip_layout).inflate()` 得到 `MyGameVipCard`；布局 `layout_mine_vip_card.xml:2-3` |
| `GameAccountInfoView` `header_layout` `0x7f0904ff` | `com.platform.sdk.center.widget.GameAccountInfoView`（`extends HeyTapAccountInfoView`） | `E:\hmc\jadx-out\sources\com\platform\sdk\center\widget\GameAccountInfoView.java:9` | 布局 `account_center_game_card.xml:6`（被 VIP 卡片内部使用，真机树中位于 `MyGameVipCard` 内） |
| `MineAppManagerIconView` | `com.nearme.cards.mine.MineAppManagerIconView`（`extends AppUpgradeIconView`） | `E:\hmc\jadx-out\sources\com\nearme\cards\mine\MineAppManagerIconView.java:17` | `E:\hmc\jadx-mine\sources\com\nearme\cards\mine\MineAppManagerCard.java:1056,1058,1060`（upgrade_icon/uninstall_icon/download_icon） |

### 1.3 列表内容构成（= 真机视图树对上号）

`mine_list_view` 的内容 = **3 类东西**：

1. **RecyclerView header 视图**（不是 card，不走 card code）
   - VIP/账户头卡：`MineFragment.java:376-383` → `h3a.m10062()`（`h3a.java:346-348` 返回 `qz6.getRootView()`）→ `t9.java:30/105-107` → `MyGameVipCard[vip_layout]`
   - 福利/任务头（`ow8`）：`MineFragment.java:611-625`、`:373-389`
   - 第三方账户头：`nc5.m18211(context)`（`MineFragment.java:385-388`，`E:\hmc\jadx-mine\sources\a\a\a\nc5.java:41-47`）
2. **服务端下发的 CDO 卡片**（有 card code，见第 2 节）
   - `CustomCardView(无id)` 内含 `ll_upgrade/ll_uninstall/ll_download_manager` → card code **100100**
   - `LinearLayout(无id)` 内含 `cl_clean/cl_health` → card code **100101**
   - `RelativeLayout[root_layout]` 内含 `scroll_banner/banner_indicator` → banner 卡（40073 或 40156，见 Q5）
   - 两个 `CustomCardView(无id)` 内含 `card_container` → `HorizontalAppCard` 家族（code 候选见 Q5）
3. **静态顶栏** `MineActionBarView`（在 `fragment_mine.xml` 里写死，覆盖在 RecyclerView 之上）

---

## 2. 数据来源 / adapter / 能否在"构建卡片数据"时过滤

### 2.1 链路

```
n2a.mo740(...)                        // 网络响应回调，E:\hmc\jadx-mine\sources\a\a\a\n2a.java:110-148
  → n2a.m17704(dto, isRealTimeData)   // n2a.java:465-470
    → ao7.mo881(ViewLayerWrapDto, boolean)         // 接口 a.a.a.ao7（E:\hmc\jadx-mine\sources\a\a\a\ao7.java）
      → MineFragment.mo881(...)       // MineFragment.java:874-879
          this.f38364.clearData();
          this.f38364.setDatas(result.getCards() == null ? new ArrayList() : result.getCards());
          this.f38364.notifyDataSetChanged();
        + n2a.java:131-146 → ao7.mo882(ExtFeatureDto) → MineFragment.java:896-930（服务端开关）
```

| 项 | 结论 | 证据 |
|---|---|---|
| adapter 的具体类 | `com.nearme.cards.adapter.RecyclerViewCardAdapter`（实现 `CardApiRecycleViewAdapter`） | 创建点 `MineFragment.java:402-411` → `h62.m7806(...)`（`E:\hmc\jadx-all\sources\a\a\a\h62.java:181-182`）→ 代理 `CardApiRecyclerViewAdapterProxy`（`E:\hmc\jadx-all\sources\com\heytap\card\api\listener\CardApiRecyclerViewAdapterProxy.java:21,26-28`）→ 真实实现类由 DI 注册（key `"recycle_view_card_adapter "`，`zb2.java:7`）；DEX 方法表定位到 `com.nearme.cards.adapter.RecyclerViewCardAdapter`（`setDatas/getDatas/getAllData/addDataAndNotifyChanged` 均在此类，`E:\hmc\jadx-mine\sources\com\nearme\cards\adapter\RecyclerViewCardAdapter.java:419,545,529,742`） |
| 数据模型 | `ViewLayerWrapDto.getCards() : List<CardDto>`；`CardDto.getCode()` 即卡片类型码，`getKey()` 为卡片实例 key | `CardDto` 字段 `code/key/ext/stat`：`E:\hmc\jadx-all\sources\com\heytap\cdo\card\domain\dto\CardDto.java:19-32`；`getItemViewType()` 直接返回 `cardDto.getCode()`：`RecyclerViewCardAdapter.java:592-596` |
| 卡片 code→类的注册表 | `a.a.a.v72`（`getCardClzName(code)`），由各 `b57.init()` 调 `registerCard(code, className, viewType)` 填充 | `E:\hmc\jadx-mine\sources\a\a\a\v72.java:16,62-98`；`E:\hmc\jadx-mine\sources\a\a\a\q42.java:264-527`（`MineAppManagerCard`=100100 @ `q42.java:527`；`AppHealthAndCleanCard`=100101 @ `q42.java:528`） |
| View 创建 | `fag.createViewInner()`：`a57.getCardClzName(code)` → `Class.forName(...).newInstance()` | `E:\hmc\jadx-mine\sources\a\a\a\fag.java:30-46` |
| 是否数据驱动 | **是**。服务端不返回某卡片 → 该卡片不会出现在列表；卡片自带 `mo45850(CardDto)` 校验，返回 false 则丢弃（`v5g.m29596` 在 `fag.java:79` 调用） | `RecyclerViewCardAdapter.java:742-747`（setDatas→exeCardDtoRules）、`Card.java:426`（abstract `mo45850`）、`fag.java:79` |

### 2.2 「构建卡片数据列表那一刻」的过滤点（按安全性排序）

| 优先级 | 注入点（方法签名） | file:line | 影响面 | 说明 |
|---|---|---|---|---|
| ★1 | `MineFragment.mo881(ViewLayerWrapDto result, boolean isRealTimeData)` — 在 `setDatas(...)` 之前改写 `result.getCards()` | `E:\hmc\jadx-mine\sources\com\heytap\market\mine\MineFragment.java:874-879` | 仅「我的」页 | 只作用于本页，其他 tab/页面零影响；是"构建列表数据"的原点。方法名在 dex 中为不可打印字符（jadx 记作 `mo881`），**hook 时按签名 `(ViewLayerWrapDto, boolean)` 匹配** |
| ★2 | `RecyclerViewCardAdapter.exeCardDtoRules(List<CardDto>)`（`protected`，`setDatas`/`addDataAndNotifyChanged` 都会过） | `E:\hmc\jadx-mine\sources\com\nearme\cards\adapter\RecyclerViewCardAdapter.java:501-523`；调用点 `:392`、`:745` | **所有使用该适配器的页面** | 框架级规则位（去掉重复卡、走 `zj3` 规则引擎）；改这里会影响首页/详情页等，需按 pageKey 收窄 |
| ★3 | `AppHealthAndCleanCard.mo45850(CardDto)` 内的条目白名单移除 | `E:\hmc\jadx-mine\sources\com\nearme\cards\widget\card\impl\apphealth\AppHealthAndCleanCard.java:181-197` + `m46664(String)` `:40-45` | 仅 100101 卡 | **卡内条目级**数据过滤（type "1"=应用健康状态 / "2"=存储空间清理）；移除后列表长度变化会自动切换单条/双条布局（`:85-144`） |
| ★4 | `Card.mo45850(CardDto)`（每张卡自己的"这张卡要不要显示"） | 抽象定义 `E:\hmc\jadx-mine\sources\com\nearme\cards\widget\card\Card.java:426`；调用 `fag.java:79`；示例实现 `MineWelfareBannerCard.java:112-116` | 单卡 | 卡级开关，语义最干净，适合 banner 这类"数据为空就不显示"的卡 |
| 5 | `zj3.m35300(list, pageInfo, ...)` 规则引擎 | `RecyclerViewCardAdapter.java:506`；`E:\hmc\jadx-mine\sources\a\a\a\zj3.java:1280-1286` | 全局 | 平台级规则（含机型/地区等），改动风险最高 |

> **推荐做法**：LSPosed 模块 hook `MineFragment.mo881`，在进入 `setDatas` 前把目标 `CardDto` 从 `List` 里 remove（按 `getCode()`），随后 `notifyDataSetChanged` 按现有逻辑执行——不产生"先创建视图再隐藏"的中间态。

---

## 3. 顶部 `MineActionBarView` 的入口与点击处理

| 入口 id（十六进制） | 绑定（findViewById/监听） | 点击分发 | 实际动作（方法 → file:line） |
|---|---|---|---|
| `iv_setting` `0x7f090662` | `MineActionBarView.java:151-154` | `onClick` `:283-287` | `m41316()` `:123-132` → 分屏/普通两条路：`m41322()` `:223-241`（FlexibleWindow 打开设置）或 `m41323()` `:244-246`（`oap://mk/settings`） |
| `iv_feedback` `0x7f090604` | `:155-159`（并在 `:159` 用 `ug5.m28545(ug5.f24951)` 决定初始可见性） | `:288-289` | `m41314()` `:108-114` → `oap://mk/fb/home`；可见性另有 `setFeedBackVisibility(boolean)` `:302-308` |
| `iv_search` `0x7f09065e` | `:144-146`（初始 `:175` 置 8=GONE） | 不在此处：`MineFragment.m40807()` `E:\hmc\jadx-mine\sources\com\heytap\market\mine\MineFragment.java:514-525` | `m40822()` `MineFragment.java:953-958` → `oap://mk/search`；搜索条模式见 `m40806()` `MineFragment.java:503-511` |
| 搜索条（`mine_search_bar_bg` `0x7f0907d2`） | `:164-168` | 由 `MineSearchBarPresenter` 处理 | `MineFragment.java:503-511` |
| `iv_all_service` `0x7f0905c9` | `:160-163`（可见性 `:174`：`m41319()`→`y8c.f32384.mo5416()`） | `:290-291` | `m41313()` `:100-105` → `oap://mk/all/service` |
| `iv_qrcode` `0x7f090651` | `:147-150` | `:279-282` | `m41315()` `:117-120` → `oap://mk/qrcode` |

拦截建议：hook `MineActionBarView.onClick(View)`（`MineActionBarView.java:277`）后按 `v.getId()` 短路，比逐个 hook `m4131x` 更省电（一次 hook 覆盖全部入口）。

---

## 4. "是否显示某个入口"的开关 / 配置 key

### 4.1 服务端开关（ExtFeature）

| 机制 | 证据 |
|---|---|
| 服务端随卡片列表下发 `ExtFeatureDto.switches[]`（字段 `type` / `status` / `name` / `id` / `stat`） | `E:\hmc\jadx-all\sources\com\heytap\cdo\card\domain\dto\extfeature\ExtFeatureDto.java:11-20`、`...\ExtFeatureSwitches.java:10-22` |
| 解析后：写入 SharedPreferences + 回调 Fragment | `E:\hmc\jadx-mine\sources\a\a\a\n2a.java:125-146`（`h1c.m9979(json)` @ `:132`；`ao7.mo882(...)` @ `:145`） |
| Fragment 消费：`online_feedback`(反馈) / `ACCOUNT_NAMEPLATE`(账户铭牌) / `SIGN_FUNCTION`(签到) | `MineFragment.java:896-930`（`setFeedBackVisibility` @ `:915`、`h3a.m10071/m10073` @ `:922,927`）；本地缓存 `h1c.m9980(type, state)`（`E:\hmc\jadx-mine\sources\a\a\a\h1c.java:314-317`）、读取 `h1c.m9955(type)`（`h1c.java:169-172`） |
| 开关类型常量表 | `E:\hmc\jadx-mine\sources\com\heytap\cdo\osp\domain\common\ExtFeatureSwitchesType.java:6-49`（含 `ACCOUNT_NAMEPLATE/SIGN_FUNCTION/AI_SEARCH/...`） |
| "服务管理"开关（通用） | `ug5.f24951 = "online_feedback"`（`E:\hmc\jadx-mine\sources\a\a\a\ug5.java:19`）、`ug5.m28545(str)` → `qs7.getServeManagerSwitchState(str)`（`ug5.java:32-34`）→ `b1c.getServeManagerSwitchState` → `h1c.m9955`（`E:\hmc\jadx-mine\sources\a\a\a\b1c.java:44-46`） |

### 4.2 本地 SharedPreferences key（`a.a.a.h1c`）

| key 字符串 | 常量 | file:line | 用途 |
|---|---|---|---|
| `p_serve_manager_data` | `f9045` | `h1c.java:44`（get `:166`，set `:309-312`） | 服务管理开关 JSON（服务端下发） |
| `pref.mine.search.bar.last.show.time` | `f9051` | `h1c.java:62` | 我的页搜索条展示节流（`MineFragment.java:492,655`） |
| `pref.mine.welfare.task.dto` | `f9054` | `h1c.java:71` | 我的页福利任务 DTO |
| `pref.mine.welfare.label.exp.count` | `f9055` | `h1c.java:74` | 福利标签曝光计数 |
| `switchType`（动态 key，用 `ExtFeatureSwitchesType.*` 作 key） | — | 读 `h1c.java:169-172`；写 `:314-317` | 每个服务端开关的本地缓存，**默认 true** |

> **未证实/不存在**：没有找到任何"让 待更新 / 应用卸载 / 下载管理 / 存储空间清理 / 应用健康状态 / banner / 推荐卡 单独不显示"的配置 key 或服务端字段。这些项的显示逻辑是：卡片是否被服务端返回（card code）+ 卡片自身 `mo45850` 校验（如 `MineAppManagerCard.mo45850` 恒 true，`MineAppManagerCard.java:1039-1041`；`MineWelfareBannerCard.mo45850` 依据 banner 列表是否为空，`MineWelfareBannerCard.java:112-116`）。

---

## 5. "隐藏某入口"的实现优先级与逐项方案

优先级总原则：**①数据层过滤 > ②按 id 隐藏 > ③按结构定位容器隐藏**（①不产生视图、无闪烁、最不易被复用逻辑覆盖；③仅在前两者不可行时用）。

### 5.1 逐项方案

| 目标入口（id / 十六进制） | ①数据层过滤 | ②按 id 隐藏 | ③按结构定位容器 | 结论/证据 |
|---|---|---|---|---|
| **待更新** `ll_upgrade` `0x7f090765` | ✖ 只能整卡过滤 code **100100**（三入口同属一卡，`q42.java:527`；卡片 bind 时不读 DTO 明细：`MineAppManagerCard.java:787-796`） | ✔ 在该卡视图上 `findViewById(R.id.ll_upgrade).setVisibility(GONE)`（绑定点 `MineAppManagerCard.java:1049`；更新态判断 `:857-875`） | △ `ll_upgrade` 的父 `ConstraintLayout` 无 id | 推荐 ②（可用 hook `Card.mo45843` 后处理，或 `RecyclerViewCardAdapter.onBindViewHolder` `:814-864` 后处理） |
| **应用卸载** `ll_uninstall` `0x7f090762` | 同上（整卡 100100） | ✔ `findViewById(R.id.ll_uninstall)`（`MineAppManagerCard.java:1050`；状态 `:910-911`） | 同上 | 推荐 ② |
| **下载管理** `ll_download_manager` `0x7f090717` | 同上（整卡 100100） | ✔ `findViewById(R.id.ll_download_manager)`（`MineAppManagerCard.java:1052`；状态 `:945-958`） | 同上 | 推荐 ② |
| **存储空间清理** `cl_clean` `0x7f090283` | ✔ **首选**：在 `AppHealthAndCleanCard.mo45850` 的过滤循环里再摘掉 `type=="2"`（`AppHealthAndCleanCard.java:189-195` + `m46664` `:40-45`），列表变 1 条时自动走单条布局 `:85-129` | ✔ `cl_clean` 只出现在双条布局（`AppHealthDoubleItemHolder.java:433`），单条布局用 `cl_content`（`StorageCleanItemHolder.java:111-112`） | △ 双条布局根为无 id `LinearLayout` | 推荐 ①；② 作为兜底（`cl_clean` 在代码里没有任何 findViewById，只能靠运行时按 id 找） |
| **应用健康状态** `cl_health` `0x7f09028c` | ✔ 同上去掉 `type=="1"`（`AppHealthAndCleanCard.java:145-148` 绑定 holder；type 分派 `AppHealthDoubleItemHolder.java:301-327`） | ✔ 布局 `AppHealthDoubleItemHolder.java:448` | △ 同上 | 推荐 ① |
| **热门好礼免费领 banner** `scroll_banner` `0x7f0909c5` / `banner_indicator` `0x7f090128` | ✔ 按 card code 过滤：候选 **40073**（`MineScrollBannerCard`，`q42.java:456`）或 **40156**（`MineWelfareBannerCard`，`q42.java:526`）；或 hook 该卡 `mo45850` 返回 false（`MineWelfareBannerCard.java:112-116`） | ✔ 二者共用一个布局，`findViewById(R.id.scroll_banner / R.id.banner_indicator)`（`ScrollBannerCard.java:430-431`） | △ 父 `RelativeLayout[root_layout]` 有 id，可直接整块隐藏 | ①可用但**具体 code 未证实**（两种卡共用 `layout_scroll_banner_card.xml`），建议运行时打印 `cardDto.getCode()` 确认后再过滤；否则用 ② |
| **"在这里，继续探索"推荐卡** `card_container` `0x7f090210` | ✔ 按 code 过滤整卡；具体 code **未证实**，候选：7035 `HorizontalAppNoScoreCard`（`q42.java:294`）、7038 `MiniHorizontalAppCard`（`q42.java:293`）、300/100206 `HorizontalSearchAppCard`（`q42.java:346,345`）、7040 `HorizontalSmallIconAppCard`（`q42.java:493`） | △ `card_container` 是卡内部容器（每张卡都有），按它隐藏会误伤 | ✔ 该 `CustomCardView` 无 id；可用 `RecyclerView` 中该 viewHolder 的 itemView 或 code 定位 | 布局与卡片类：`layout_horizontal_app_card.xml:4` ← `HorizontalAppCard.java:513`；标题（`rl_title/tv_sub_title/rl_refresh`）由 `CommonTitleHolder.java:555` inflate `layout_card_comm_title/layout_card_function_title`。**推荐先运行时确认 code 再走 ①** |
| **VIP 卡** `vip_layout` `0x7f090e11` | ✖ 不适用（它是 RecyclerView header，不由 card code 决定） | ✔/③ 隐藏 header 视图即可（`vip_layout` 的宿主是 `MyGameVipCard`，`layout_mine_vip_card.xml:2-3`） | ✔ 或不加入 header | 添加点：`MineFragment.java:373-389`（`addHeaderView(this.f38369.m10062())`）；header root：`h3a.java:346-348` → `t9.java:30/105-107`。已有细粒度开关可复用：`t9.java:92-101` `hideAccountSignInBtn()`（隐藏签到按钮） |

### 5.2 为什么"数据层优先"

1. `setDatas` 只在数据到手时执行一次，之后 `DiffUtil`/`notifyDataSetChanged` 才建视图（`RecyclerViewCardAdapter.java:388-406,742-747`），数据层删除 = 视图根本不会创建，也就没有"先显示再隐藏"的闪烁与复用回弹。
2. RecyclerView 复用会重建/重绑卡片视图，②类"设 GONE"必须在每次 `onBindViewHolder`（`RecyclerViewCardAdapter.java:814-864`）后重新施加，否则滚动回来会复现。
3. 卡片 code 是稳定契约（`q42.java` 静态注册），而视图层级（无 id 的 `CustomCardView`/`FrameLayout`/`ConstraintLayout`）随版本变化大。

---

## 6. 证据来源与限制（务必阅读）

1. **`E:\hmc\jadx-out` 不完整**：用户已有的反编译输出中**缺失** `com.heytap.market.mine.MineFragment`、`com.heytap.market.mine.view.MineActionBarView`、`com.nearme.cards.adapter.RecyclerViewCardAdapter` 等关键类（该目录下 `com\heytap\market\mine` 只有 `MineExposureScrollWrapper.java`、`view\MineSearchBarAnimHelper.java`）。因此本报告对这些类的行号引用来自我**重新反编译**的产物：
   - `E:\hmc\jadx-mine\sources`（classes5/6/7/9，含 mine 页与卡片实现）
   - `E:\hmc\jadx-all\sources`（classes/2/3/4/8/10）
   - 反编译命令：`jadx -d <dir> --no-res --show-bad-code <classesN.dex ...>`（jadx 1.5.6）
   - **行号与用户 jadx 版本可能略有差异**；R.id 名称可解析（AGP 8 非 final R，dex 中存在 `R$id` 字段），布局名来自 `E:\hmc\jadx-res\resources\res\layout\`（`jadx --no-src` 解出的资源）。
2. 我自建的只读分析工具：`E:\hmc\tools\mine_ids_xref.py`（DEX 层 `sget`/`const` 交叉引用，扫描全部 10 个 dex），输出 `E:\hmc\tmp_mine_xref.txt`。它给出 id→使用类/方法的机器证据，例如 `mine_list_view → MineFragment.ၝ`、`scroll_banner → ScrollBannerCard.ྈ`。
3. 标注 **未证实** 的项：①「继续探索」推荐卡在本页的确切 card code；②banner 在本页是 40073 还是 40156；③是否存在未公开的、"单入口不显示"的服务端字段（当前证据倾向于不存在）。
4. 本报告**未**修改任何 APK/源码/模块文件，仅新增分析文档与自用工具脚本。
