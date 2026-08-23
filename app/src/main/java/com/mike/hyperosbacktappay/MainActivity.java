package com.mike.hyperosbacktappay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(246, 246, 248);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(30, 30, 32);
    private static final int COLOR_SUBTEXT = Color.rgb(105, 105, 112);
    private static final int COLOR_ACCENT = Color.rgb(22, 119, 255);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences preferences;
    private boolean sharedPreferencesReady;
    private boolean loadingUi;

    private TextView hookStatusText;
    private GestureViews doubleTapViews;
    private GestureViews tripleTapViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
        }

        preferences = openModulePreferences();
        setContentView(buildContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @SuppressWarnings("deprecation")
    private SharedPreferences openModulePreferences() {
        try {
            SharedPreferences prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_WORLD_READABLE);
            sharedPreferencesReady = true;
            return prefs;
        } catch (Throwable ignored) {
            sharedPreferencesReady = false;
            return getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(text("HyperOS BackTap Pay", 26, COLOR_TEXT, true));

        TextView subtitle = text("澎湃 OS 背部轻敲支付宝快捷码", 14, COLOR_SUBTEXT, false);
        LinearLayout.LayoutParams subtitleLp = wrap();
        subtitleLp.topMargin = dp(4);
        subtitleLp.bottomMargin = dp(18);
        root.addView(subtitle, subtitleLp);

        root.addView(buildStatusCard());

        doubleTapViews = buildGestureCard(
                "背部双击",
                Config.SETTING_BACK_DOUBLE,
                Config.PREF_DOUBLE_DISPLAY
        );
        root.addView(doubleTapViews.card, cardSpacing());

        tripleTapViews = buildGestureCard(
                "背部三击",
                Config.SETTING_BACK_TRIPLE,
                Config.PREF_TRIPLE_DISPLAY
        );
        root.addView(tripleTapViews.card, cardSpacing());

        TextView footer = text(
                "模块不需要 Root，也不需要“修改系统设置”权限。功能切换由已加载到系统框架的 LSPosed Hook 完成；普通配置会自动保存并即时生效。",
                13,
                COLOR_SUBTEXT,
                false
        );
        LinearLayout.LayoutParams footerLp = wrap();
        footerLp.topMargin = dp(16);
        root.addView(footer, footerLp);

        return scrollView;
    }

    private View buildStatusCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("模块状态"));

        hookStatusText = text("正在检测…", 16, COLOR_TEXT, true);
        LinearLayout.LayoutParams statusLp = wrap();
        statusLp.topMargin = dp(12);
        card.addView(hookStatusText, statusLp);

        TextView scope = text(
                "推荐作用域：系统框架 / System Framework\n模块版本：" + BuildConfig.VERSION_NAME,
                13,
                COLOR_SUBTEXT,
                false
        );
        LinearLayout.LayoutParams scopeLp = wrap();
        scopeLp.topMargin = dp(8);
        card.addView(scope, scopeLp);
        return card;
    }

    private GestureViews buildGestureCard(String title, String settingKey, String displayPrefKey) {
        GestureViews views = new GestureViews();
        views.settingKey = settingKey;
        views.displayPrefKey = displayPrefKey;
        views.card = card();
        views.card.addView(sectionTitle(title));

        TextView actionLabel = text("功能", 14, COLOR_SUBTEXT, false);
        LinearLayout.LayoutParams labelLp = wrap();
        labelLp.topMargin = dp(14);
        views.card.addView(actionLabel, labelLp);

        views.actionGroup = new RadioGroup(this);
        views.actionGroup.setOrientation(LinearLayout.VERTICAL);
        views.offId = View.generateViewId();
        views.paymentId = View.generateViewId();
        views.busId = View.generateViewId();
        views.actionGroup.addView(radio("关闭", views.offId));
        views.actionGroup.addView(radio("支付宝付款码", views.paymentId));
        views.actionGroup.addView(radio("支付宝乘车码", views.busId));
        LinearLayout.LayoutParams groupLp = matchWrap();
        groupLp.topMargin = dp(4);
        views.card.addView(views.actionGroup, groupLp);

        views.unknownActionText = text("", 12, COLOR_SUBTEXT, false);
        views.unknownActionText.setVisibility(View.GONE);
        views.card.addView(views.unknownActionText);

        TextView displayLabel = text("显示位置", 14, COLOR_SUBTEXT, false);
        LinearLayout.LayoutParams displayLabelLp = wrap();
        displayLabelLp.topMargin = dp(14);
        views.card.addView(displayLabel, displayLabelLp);

        views.displayGroup = new RadioGroup(this);
        views.displayGroup.setOrientation(LinearLayout.HORIZONTAL);
        views.mainId = View.generateViewId();
        views.rearId = View.generateViewId();
        views.displayGroup.addView(radio("主屏", views.mainId));
        views.displayGroup.addView(radio("背屏", views.rearId));
        LinearLayout.LayoutParams displayGroupLp = matchWrap();
        displayGroupLp.topMargin = dp(4);
        views.card.addView(views.displayGroup, displayGroupLp);

        views.actionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingUi || checkedId == -1) {
                return;
            }
            String function = functionForId(views, checkedId);
            if (function == null) {
                return;
            }
            requestGestureChange(views, function);
        });

        views.displayGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingUi || checkedId == -1) {
                return;
            }
            if (!sharedPreferencesReady) {
                toast("LSPosed 共享配置未就绪，请确认模块已启用后重新打开应用");
                refreshGesture(views);
                return;
            }
            String display = checkedId == views.mainId ? Config.DISPLAY_MAIN : Config.DISPLAY_REAR;
            boolean ok = preferences.edit().putString(views.displayPrefKey, display).commit();
            toast(ok ? "配置已生效" : "保存显示位置失败");
        });

        return views;
    }

    private void requestGestureChange(GestureViews views, String function) {
        if (!isCurrentHookLoaded()) {
            toast("当前新版 Hook 尚未加载，请确认 LSPosed 作用域后重启手机一次");
            refreshGesture(views);
            return;
        }

        try {
            Intent intent = new Intent(Config.ACTION_SET_GESTURE);
            intent.putExtra(Config.EXTRA_SETTING_KEY, views.settingKey);
            intent.putExtra(Config.EXTRA_FUNCTION, function);
            sendBroadcast(intent);
        } catch (Throwable t) {
            toast("发送系统框架配置请求失败");
            refreshGesture(views);
            return;
        }

        mainHandler.postDelayed(() -> {
            try {
                String actual = Settings.System.getString(getContentResolver(), views.settingKey);
                if (function.equals(actual)) {
                    toast("配置已生效");
                } else {
                    toast("配置未写入，请查看 LSPosed 日志");
                }
            } catch (Throwable t) {
                toast("无法验证系统手势配置");
            }
            refreshGesture(views);
        }, 350);
    }

    private void refreshAll() {
        refreshHookStatus();
        if (doubleTapViews != null) {
            refreshGesture(doubleTapViews);
        }
        if (tripleTapViews != null) {
            refreshGesture(tripleTapViews);
        }
    }

    private boolean isCurrentHookLoaded() {
        try {
            String hookVersion = Settings.System.getString(getContentResolver(), Config.STATUS_HOOK_VERSION);
            int hookBootCount = Settings.System.getInt(
                    getContentResolver(), Config.STATUS_HOOK_BOOT_COUNT, Integer.MIN_VALUE);
            int currentBootCount = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.BOOT_COUNT, Integer.MIN_VALUE);
            return BuildConfig.VERSION_NAME.equals(hookVersion) && hookBootCount == currentBootCount;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void refreshHookStatus() {
        String hookVersion = Settings.System.getString(getContentResolver(), Config.STATUS_HOOK_VERSION);
        int hookBootCount = Settings.System.getInt(
                getContentResolver(),
                Config.STATUS_HOOK_BOOT_COUNT,
                Integer.MIN_VALUE
        );
        int currentBootCount = Settings.Global.getInt(
                getContentResolver(),
                Settings.Global.BOOT_COUNT,
                Integer.MIN_VALUE
        );

        String prefsLine = sharedPreferencesReady
                ? "\n共享配置：正常"
                : "\n共享配置：未就绪";

        if (hookVersion != null && hookBootCount == currentBootCount) {
            if (BuildConfig.VERSION_NAME.equals(hookVersion)) {
                hookStatusText.setText("● Hook 已加载" + prefsLine);
                hookStatusText.setTextColor(Color.rgb(20, 135, 70));
            } else {
                hookStatusText.setText(
                        "⚠ 系统框架仍在运行旧版 Hook " + hookVersion
                                + "\n当前 APK：" + BuildConfig.VERSION_NAME
                                + "\n请手动重启手机加载新版 Hook"
                                + prefsLine
                );
                hookStatusText.setTextColor(Color.rgb(190, 110, 0));
            }
        } else {
            hookStatusText.setText(
                    "⚠ 本次启动尚未检测到 Hook"
                            + "\n请确认 LSPosed 已启用模块并勾选“系统框架”"
                            + "\n首次启用或更新模块后需手动重启手机一次"
                            + prefsLine
            );
            hookStatusText.setTextColor(Color.rgb(190, 110, 0));
        }
    }

    private void refreshGesture(GestureViews views) {
        loadingUi = true;
        try {
            String currentFunction = Settings.System.getString(getContentResolver(), views.settingKey);
            views.actionGroup.clearCheck();
            views.unknownActionText.setVisibility(View.GONE);

            boolean supportedAction = false;
            if (Config.FUNCTION_PAYMENT.equals(currentFunction)) {
                views.actionGroup.check(views.paymentId);
                supportedAction = true;
            } else if (Config.FUNCTION_BUS.equals(currentFunction)) {
                views.actionGroup.check(views.busId);
                supportedAction = true;
            } else if (currentFunction == null || Config.FUNCTION_NONE.equals(currentFunction)) {
                views.actionGroup.check(views.offId);
            } else {
                views.unknownActionText.setText(
                        "当前系统绑定为其他功能：" + currentFunction
                                + "。选择上方任一项后本模块才会接管。"
                );
                views.unknownActionText.setVisibility(View.VISIBLE);
            }

            String display = preferences.getString(views.displayPrefKey, Config.DISPLAY_REAR);
            if (Config.DISPLAY_MAIN.equals(display)) {
                views.displayGroup.check(views.mainId);
            } else {
                views.displayGroup.check(views.rearId);
            }
            setDisplayGroupEnabled(views.displayGroup, supportedAction);
        } finally {
            loadingUi = false;
        }
    }

    private static String functionForId(GestureViews views, int id) {
        if (id == views.offId) return Config.FUNCTION_NONE;
        if (id == views.paymentId) return Config.FUNCTION_PAYMENT;
        if (id == views.busId) return Config.FUNCTION_BUS;
        return null;
    }

    private void setDisplayGroupEnabled(RadioGroup group, boolean enabled) {
        group.setEnabled(enabled);
        group.setAlpha(enabled ? 1.0f : 0.45f);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackground(cardBackground());
        view.setElevation(dp(1));
        return view;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_CARD);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private TextView sectionTitle(String value) {
        return text(value, 18, COLOR_TEXT, true);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private RadioButton radio(String value, int id) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(COLOR_TEXT);
        button.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{COLOR_ACCENT, Color.rgb(135, 135, 142)}
        ));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(0, dp(3), dp(12), dp(3));
        return button;
    }

    private LinearLayout.LayoutParams cardSpacing() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(12);
        return lp;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private static final class GestureViews {
        LinearLayout card;
        RadioGroup actionGroup;
        RadioGroup displayGroup;
        TextView unknownActionText;
        String settingKey;
        String displayPrefKey;
        int offId;
        int paymentId;
        int busId;
        int mainId;
        int rearId;
    }
}
