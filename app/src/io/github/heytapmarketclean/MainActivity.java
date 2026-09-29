package io.github.heytapmarketclean;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设置页：纯代码构建（无 XML 布局）。
 * 分类页签 -> 每页一个卡片 -> 每项「标题 / 对应页面 / 说明 / 实时状态 + 开关」。
 */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(245, 247, 250);
    private static final int INK = Color.rgb(28, 40, 46);
    private static final int MUTED = Color.rgb(104, 118, 126);
    private static final int ACCENT = Color.rgb(9, 150, 100);
    private static final int SECTION = Color.rgb(18, 110, 90);
    private static final int TAB_OFF = Color.rgb(232, 237, 235);
    private static final int TAB_ON = Color.rgb(9, 150, 100);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Switch> switches = new LinkedHashMap<String, Switch>();
    private final Map<String, TextView> stateRows = new LinkedHashMap<String, TextView>();
    private final Map<String, Boolean> defaults = new LinkedHashMap<String, Boolean>();
    private TextView status;
    private TextView hostLine;
    private LinearLayout[] pages;
    private Button[] tabs;
    private boolean active;
    private boolean refreshing;
    private Bundle lastStatus;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!active) return;
            refresh();
            handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        // ── 头部 ──────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(6));
        header.addView(text("软件商店净化", 24, INK, true));
        header.addView(text("作用域 " + Config.TARGET + " · schema " + Config.SCHEMA
                + " · 改完开关请强停目标 App 再打开", 12, MUTED, false));
        LinearLayout statusCard = card();
        status = text("正在连接 LSPosed…", 14, INK, false);
        statusCard.addView(status);
        hostLine = text("等待目标 App 上报…", 12, MUTED, false);
        hostLine.setPadding(0, dp(4), 0, dp(2));
        statusCard.addView(hostLine);
        Button report = new Button(this);
        report.setAllCaps(false);
        report.setText("查看兼容结果");
        report.setTextColor(ACCENT);
        report.setBackgroundColor(Color.TRANSPARENT);
        report.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showReport(); }
        });
        statusCard.addView(report);
        header.addView(statusCard);
        root.addView(header);

        // ── 分类页签 ──────────────────────────────────────────────────────
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(tabBar);

        int pageCount = Config.CATEGORIES.length + 1;
        tabs = new Button[pageCount];
        pages = new LinearLayout[pageCount];
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (int i = 0; i < pageCount; i++) {
            final int index = i;
            Button tab = new Button(this);
            tab.setAllCaps(false);
            tab.setTextSize(13);
            tab.setText(i < Config.CATEGORIES.length ? Config.CATEGORIES[i] : "诊断");
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { select(index); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            params.setMargins(dp(3), 0, dp(3), 0);
            tabBar.addView(tab, params);
            tabs[i] = tab;

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(14), dp(6), dp(14), dp(28));
            scroll.addView(body);
            scroll.setVisibility(View.GONE);
            content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            pages[i] = body;
        }

        for (int c = 0; c < Config.CATEGORIES.length; c++) buildFeaturePage(pages[c], c);
        buildDiagnosticsPage(pages[Config.CATEGORIES.length]);
        select(0);
        setContentView(root);
    }

    private void buildFeaturePage(LinearLayout body, int category) {
        section(body, Config.CATEGORIES[category]);
        addNote(body, categoryHint(category), 12);
        LinearLayout box = card();
        for (int i = 0; i < Config.FEATURES.length; i++) {
            if (Config.FEATURE_CATEGORY[i] != category) continue;
            row(box, Config.FEATURES[i], Config.FEATURE_LABELS[i], Config.FEATURE_PAGES[i],
                    Config.FEATURE_NOTES[i], Config.FEATURE_DEFAULT[i]);
        }
        body.addView(box);
        // 底栏隐藏名单要能改：不同版本、不同账号看到的推广 tab 不一样
        if (category == 1) addTabLabelEditor(body);
    }

    /** 底栏推广项文案名单编辑器（逗号分隔）。 */
    private void addTabLabelEditor(LinearLayout body) {
        LinearLayout box = card();
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setHint("例：福利,活动,签到");
        input.setText(currentTabLabels());

        Button save = new Button(this);
        save.setAllCaps(false);
        save.setText("保存隐藏名单");
        save.setTextColor(ACCENT);
        save.setBackgroundColor(Color.TRANSPARENT);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String value = input.getText().toString().trim();
                boolean ok = App.write(Config.KEY_TAB_LABELS, value);
                Toast.makeText(MainActivity.this,
                        ok ? "已保存，强停目标 App 后生效" : "未连接到框架服务，请先打开框架管理器",
                        Toast.LENGTH_SHORT).show();
                refresh();
            }
        });

        box.addView(text("底栏推广项文案名单", 14, INK, true));
        addNote(box, "按文案（语义标签）过滤底栏，跨版本最稳。填了就不隐藏任何 tab。"
                + "当前版本底栏为：首页 / 游戏 / 软件 / 榜单 / 我的——没有推广项，"
                + "所以这一项默认不产生任何隐藏。", 12);
        box.addView(input);
        box.addView(save);
        body.addView(box);
    }

    private String currentTabLabels() {
        try { return App.preferences().getString(Config.KEY_TAB_LABELS, Config.DEFAULT_TAB_LABELS); }
        catch (Throwable ignored) { return Config.DEFAULT_TAB_LABELS; }
    }

    private String categoryHint(int category) {
        if (category == 0) return "这四项是「决策闸门」拦截：命中一次即不再出现，不驻留内存、不影响流畅度。";
        if (category == 1) return "只改界面显示，不动数据；关闭开关后强停 App 即完全恢复。";
        return "「我的」页各入口按资源 id / 结构精确隐藏，可逐项选择。三项顶栏全开时整张卡一起隐藏。";
    }

    private void buildDiagnosticsPage(LinearLayout body) {
        section(body, "诊断");
        LinearLayout box = card();
        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText("打开目标 App 的应用信息（用于强行停止）");
        open.setTextColor(ACCENT);
        open.setBackgroundColor(Color.TRANSPARENT);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + Config.TARGET));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Throwable error) {
                    Toast.makeText(MainActivity.this, "无法打开应用信息页", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(open);
        addNote(box, "LSPosed 的 RemotePreferences 只在新进程里读取，改完开关必须强停目标 App。", 12);

        Button reset = new Button(this);
        reset.setAllCaps(false);
        reset.setText("恢复默认设置");
        reset.setTextColor(ACCENT);
        reset.setBackgroundColor(Color.TRANSPARENT);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (App.reset()) {
                    refresh();
                    Toast.makeText(MainActivity.this, "已恢复默认；请强停目标 App", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "LSPosed 服务尚未连接", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(reset);
        body.addView(box);

        section(body, "本模块的适配方式");
        LinearLayout how = card();
        addNote(how, "1. 锚点优先用非混淆名；混淆名（如 a.a.a.qx5#Ԩ）带严格签名校验，签名不符即放弃该规则。", 12);
        addNote(how, "2. 逐特性独立探测：某一条失效只影响它自己，其余规则照常生效，绝不整体停用。", 12);
        addNote(how, "3. 界面类规则按资源 id 名隐藏（id 名比混淆类名稳），并对已解析到的 id 数量报 partial。", 12);
        addNote(how, "4. 全程 fail-open：任何异常都只写日志，目标 App 行为与未装模块一致。", 12);
        body.addView(how);
    }

    private void select(int index) {
        for (int i = 0; i < pages.length; i++) {
            boolean on = i == index;
            View page = (View) pages[i].getParent();
            page.setVisibility(on ? View.VISIBLE : View.GONE);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(9));
            shape.setColor(on ? TAB_ON : TAB_OFF);
            tabs[i].setBackground(shape);
            tabs[i].setTextColor(on ? Color.WHITE : INK);
        }
    }

    @Override protected void onResume() { super.onResume(); active = true; refreshTask.run(); }

    @Override protected void onPause() {
        active = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void refresh() {
        SharedPreferences prefs = App.preferences();
        if (prefs == null) {
            status.setText("● LSPosed 配置服务未连接 · 开关暂不可用");
            status.setTextColor(MUTED);
        } else {
            status.setText("● LSPosed 配置服务已连接");
            status.setTextColor(ACCENT);
        }

        refreshing = true;
        try {
            for (Map.Entry<String, Switch> entry : switches.entrySet()) {
                Switch control = entry.getValue();
                control.setEnabled(prefs != null);
                Boolean fallback = defaults.get(entry.getKey());
                boolean wanted = prefs == null ? fallback
                        : Config.readBoolean(prefs, entry.getKey(), fallback);
                if (control.isChecked() != wanted) control.setChecked(wanted);
            }
        } finally { refreshing = false; }

        lastStatus = queryStatus();
        renderStates();
    }

    private void renderStates() {
        Bundle bundle = lastStatus;
        if (bundle == null || bundle.getString("token", "").isEmpty()) {
            hostLine.setText("宿主版本：等待目标 App 上报（强停后重新打开）");
        } else {
            hostLine.setText("宿主令牌 " + bundle.getString("token", "")
                    + " · 进度 " + bundle.getInt("done", 0) + "/" + bundle.getInt("total", 0));
        }
        for (int i = 0; i < Config.FEATURES.length; i++) {
            String feature = Config.FEATURES[i];
            String key = Config.key(feature);
            TextView row = stateRows.get(key);
            if (row == null) continue;
            String state = bundle == null ? "" : bundle.getString(feature, "");
            String detail = bundle == null ? "" : bundle.getString(feature + "_detail", "");
            row.setText(stateLine(state, detail));
        }
    }

    private String stateLine(String state, String detail) {
        if (state == null || state.isEmpty()) return "状态：待上报";
        String text;
        if ("verified".equals(state)) text = "状态：已验证拦截";
        else if ("matched".equals(state)) text = "状态：已生效";
        else if ("partial".equals(state)) text = "状态：部分匹配";
        else if ("miss".equals(state)) text = "状态：未匹配";
        else if ("off".equals(state)) text = "状态：已关闭";
        else if ("ready".equals(state)) text = "状态：已找到，重启后生效";
        else text = "状态：未知";
        if (detail != null && !detail.isEmpty()) text = text + "（" + detail + "）";
        return text;
    }

    private void showReport() {
        Bundle bundle = lastStatus;
        StringBuilder out = new StringBuilder();
        if (bundle == null || bundle.getString("token", "").isEmpty()) {
            out.append("尚未收到目标 App 的适配报告。\n\n请强停并重新打开目标 App，再回到本页。");
        } else {
            int verified = 0, matched = 0, partial = 0, miss = 0, off = 0;
            StringBuilder rows = new StringBuilder();
            for (int i = 0; i < Config.FEATURES.length; i++) {
                String state = bundle.getString(Config.FEATURES[i], "");
                if ("verified".equals(state)) { verified++; rows.append("✔ "); }
                else if ("matched".equals(state)) { matched++; rows.append("✓ "); }
                else if ("partial".equals(state)) { partial++; rows.append("◐ "); }
                else if ("miss".equals(state)) { miss++; rows.append("✕ "); }
                else if ("off".equals(state)) { off++; rows.append("— "); }
                else rows.append("○ ");
                rows.append(Config.FEATURE_LABELS[i]).append("  [")
                        .append(Config.FEATURE_PAGES[i]).append("]  ")
                        .append(stateLine(state, bundle.getString(Config.FEATURES[i] + "_detail", "")))
                        .append('\n');
            }
            out.append("宿主版本 / 设置来源：" ).append(bundle.getString("token", "")).append('\n');
            out.append("已验证 ").append(verified).append(" · 已触发 ").append(matched)
                    .append(" · 部分 ").append(partial)
                    .append(" · 未匹配 ").append(miss).append(" · 已关闭 ").append(off).append("\n\n");
            out.append(rows);
            out.append("\n✔已验证拦截 ✓已触发 ◐部分 ✕未匹配 —已关闭 ○待上报");
        }
        new AlertDialog.Builder(this)
                .setTitle("兼容结果")
                .setMessage(out.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private Bundle queryStatus() {
        try { return getContentResolver().call(StatusProvider.URI, "get", null, null); }
        catch (Throwable ignored) { return null; }
    }

    // ── UI 小工具 ──────────────────────────────────────────────────────────

    private void row(LinearLayout parent, String feature, String title, String page, String note,
                     boolean initial) {
        if (parent.getChildCount() > 0) separator(parent);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, INK, true));
        TextView pageView = text("对应页面：" + page, 12, SECTION, false);
        pageView.setPadding(0, dp(2), 0, 0);
        labels.addView(pageView);
        head.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch control = new Switch(this);
        control.setContentDescription(title);
        control.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, Color.rgb(222, 226, 229)}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(168, 224, 205), Color.rgb(190, 196, 199)}));
        control.setChecked(initial);
        control.setEnabled(false);
        control.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (refreshing || App.preferences() == null) return;
                if (!App.write(Config.key(feature), checked)) {
                    button.setOnCheckedChangeListener(null);
                    button.setChecked(!checked);
                    button.setOnCheckedChangeListener(this);
                    Toast.makeText(MainActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        head.addView(control);
        box.addView(head);

        LinearLayout noteRow = new LinearLayout(this);
        noteRow.setOrientation(LinearLayout.VERTICAL);
        noteRow.setPadding(0, dp(4), 0, 0);
        noteRow.addView(text(note, 12, MUTED, false));
        TextView stateRow = text("状态：待上报", 12, INK, false);
        stateRow.setPadding(0, dp(2), 0, 0);
        noteRow.addView(stateRow);
        box.addView(noteRow);

        parent.addView(box);
        switches.put(Config.key(feature), control);
        stateRows.put(Config.key(feature), stateRow);
        defaults.put(Config.key(feature), initial);
    }

    private void section(LinearLayout body, String title) {
        TextView label = text(title, 14, SECTION, true);
        label.setPadding(dp(4), dp(16), 0, dp(6));
        body.addView(label);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.WHITE);
        shape.setCornerRadius(dp(13));
        box.setBackground(shape);
        return box;
    }

    private void addNote(LinearLayout box, String message, int size) {
        TextView note = text(message, size, MUTED, false);
        note.setPadding(0, dp(3), 0, dp(4));
        box.addView(note);
    }

    private void separator(LinearLayout box) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(239, 242, 244));
        box.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
}
