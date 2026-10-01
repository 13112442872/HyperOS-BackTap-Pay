package com.mike.hyperosbacktappay;

import android.app.Activity;
import android.content.Context;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.libxposed.service.XposedService;

public final class MainActivity extends Activity implements ModuleApp.ServiceStateListener {
    private static final int COLOR_BG = Color.rgb(246, 246, 248);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(30, 30, 32);
    private static final int COLOR_SUBTEXT = Color.rgb(105, 105, 112);
    private static final int COLOR_ACCENT = Color.rgb(22, 119, 255);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences legacyPreferences;
    private SharedPreferences preferences;
    private XposedService xposedService;
    private boolean serviceReady;
    private boolean loadingUi;
    private String frameworkInfo = "LSPosed 服务未连接";

    private TextView hookStatusText;
    private Switch tipsSwitch;
    private Switch rearSunlight;
    private android.widget.EditText rearSeconds;
    private android.widget.SeekBar rearBrightness;
    private TextView rearBrightnessLabel;
    private android.widget.Button rearSave;
    private GestureViews doubleTapViews;
    private GestureViews tripleTapViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        window.setDecorFitsSystemWindows(false);
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);
        window.getDecorView().setBackgroundColor(COLOR_BG);
        window.getInsetsController().setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);

        legacyPreferences = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE);
        preferences = legacyPreferences;
        connectService(ModuleApp.getService(), false);
        setContentView(buildContentView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ModuleApp.addServiceStateListener(this, true);
    }

    @Override
    protected void onStop() {
        ModuleApp.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    public void onServiceStateChanged(XposedService service) {
        runOnUiThread(() -> connectService(service, true));
    }

    private void connectService(XposedService service, boolean refreshUi) {
        xposedService = service;
        serviceReady = false;
        preferences = legacyPreferences;
        frameworkInfo = "LSPosed 服务未连接";

        if (service != null) {
            try {
                SharedPreferences remote = service.getRemotePreferences(Config.PREFS_NAME);
                preferences = remote;
                serviceReady = true;
                frameworkInfo = service.getFrameworkName() + " " + service.getFrameworkVersion()
                        + " · API " + service.getApiVersion();
                migrateLegacyPreferences(remote);
            } catch (Throwable t) {
                serviceReady = false;
                preferences = legacyPreferences;
                frameworkInfo = "Modern Xposed 服务连接失败";
            }
        }

        if (refreshUi && hookStatusText != null) {
            refreshAll();
        }
    }

    private void migrateLegacyPreferences(SharedPreferences remote) {
        if (remote.getBoolean(Config.PREF_MIGRATED_MODERN, false)) {
            return;
        }

        SharedPreferences.Editor editor = remote.edit();
        copyLegacyString(remote, editor, Config.PREF_DOUBLE_ACTION);
        copyLegacyString(remote, editor, Config.PREF_TRIPLE_ACTION);
        copyLegacyString(remote, editor, Config.PREF_DOUBLE_DISPLAY);
        copyLegacyString(remote, editor, Config.PREF_TRIPLE_DISPLAY);
        if (!remote.contains(Config.PREF_SHOW_TIPS) && legacyPreferences.contains(Config.PREF_SHOW_TIPS)) {
            editor.putBoolean(
                    Config.PREF_SHOW_TIPS,
                    legacyPreferences.getBoolean(Config.PREF_SHOW_TIPS, false)
            );
        }
        editor.putBoolean(Config.PREF_MIGRATED_MODERN, true).commit();
    }

    private void copyLegacyString(
            SharedPreferences remote,
            SharedPreferences.Editor editor,
            String key
    ) {
        if (!remote.contains(key) && legacyPreferences.contains(key)) {
            String value = legacyPreferences.getString(key, null);
            if (value != null) {
                editor.putString(key, value);
            }
        }
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.setClipToPadding(true);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            android.graphics.Insets keyboard = insets.getInsets(android.view.WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, keyboard.bottom));
            return insets;
        });
        scrollView.requestApplyInsets();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(text("HYPEROS  /  BACK TAP", 11, COLOR_ACCENT, true));
        TextView title = text("轻敲 · 快捷码", 30, COLOR_TEXT, true);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(8);
        root.addView(title, titleLp);

        TextView subtitle = text("澎湃 OS 背部轻敲支付宝快捷码", 14, COLOR_SUBTEXT, false);
        LinearLayout.LayoutParams subtitleLp = wrap();
        subtitleLp.topMargin = dp(4);
        subtitleLp.bottomMargin = dp(18);
        root.addView(subtitle, subtitleLp);

        root.addView(buildStatusCard());
        root.addView(buildTipsCard(), cardSpacing());
        root.addView(buildRearCard(), cardSpacing());

        doubleTapViews = buildGestureCard(
                "背部双击",
                Config.SETTING_BACK_DOUBLE,
                Config.PREF_DOUBLE_ACTION,
                Config.PREF_DOUBLE_DISPLAY
        );
        root.addView(doubleTapViews.card, cardSpacing());

        tripleTapViews = buildGestureCard(
                "背部三击",
                Config.SETTING_BACK_TRIPLE,
                Config.PREF_TRIPLE_ACTION,
                Config.PREF_TRIPLE_DISPLAY
        );
        root.addView(tripleTapViews.card, cardSpacing());

        TextView footer = text(
                "v0.3.0 起使用 Modern Xposed API 102、Remote Preferences 与 Hot Reload。"
                        + "从旧版 Legacy 首次升级仍需让 system_server 加载一次新版模块；之后普通 APK 更新可由 LSPosed 热重载。",
                13,
                COLOR_SUBTEXT,
                false
        );
        LinearLayout.LayoutParams footerLp = wrap();
        footerLp.topMargin = dp(16);
        root.addView(footer, footerLp);

        return scrollView;
    }

    private View buildRearCard() {
        LinearLayout panel = card();
        panel.addView(sectionTitle("背屏码页显示"));
        panel.addView(text("背部双击 / 三击，以及双击电源键打开的背屏支付宝付款码 / 乘车码，共用以下设置。", 13, COLOR_SUBTEXT, false));
        TextView durationTitle = text("显示时长", 15, COLOR_TEXT, true);
        LinearLayout.LayoutParams durationLp = matchWrap();
        durationLp.topMargin = dp(20);
        panel.addView(durationTitle, durationLp);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(dp(16), dp(4), dp(16), dp(4));
        timeRow.setBackground(roundBackground(Color.rgb(244, 246, 250), 14));
        rearSeconds = new android.widget.EditText(this);
        rearSeconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        rearSeconds.setSingleLine(true);
        rearSeconds.setTextColor(COLOR_TEXT);
        rearSeconds.setTextSize(26);
        rearSeconds.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        rearSeconds.setBackgroundColor(Color.TRANSPARENT);
        rearSeconds.setPadding(0, dp(8), dp(8), dp(8));
        rearSeconds.setSelectAllOnFocus(true);
        timeRow.addView(rearSeconds, new LinearLayout.LayoutParams(0, dp(56), 1));
        timeRow.addView(text("秒  /  1–300", 13, COLOR_SUBTEXT, false));
        LinearLayout.LayoutParams timeLp = matchWrap();
        timeLp.topMargin = dp(8);
        panel.addView(timeRow, timeLp);
        LinearLayout presets = new LinearLayout(this);
        for (int seconds : new int[]{15, 30, 60, 120}) {
            TextView preset = text(seconds + " 秒", 13, COLOR_ACCENT, true);
            preset.setGravity(Gravity.CENTER);
            preset.setBackground(roundBackground(Color.rgb(235, 243, 255), 10));
            preset.setOnClickListener(v -> rearSeconds.setText(String.valueOf(seconds)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (seconds != 120) lp.rightMargin = dp(8);
            presets.addView(preset, lp);
        }
        LinearLayout.LayoutParams presetsLp = matchWrap();
        presetsLp.topMargin = dp(10);
        panel.addView(presets, presetsLp);
        rearBrightnessLabel = text("显示亮度  ·  100%", 15, COLOR_TEXT, true);
        LinearLayout.LayoutParams brightLp = matchWrap();
        brightLp.topMargin = dp(22);
        panel.addView(rearBrightnessLabel, brightLp);
        rearBrightness = new android.widget.SeekBar(this);
        rearBrightness.setMax(99);
        rearBrightness.setProgressTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        rearBrightness.setThumbTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        rearBrightness.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(224, 231, 243)));
        rearBrightness.setMinimumHeight(dp(48));
        rearBrightness.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                rearBrightnessLabel.setText("显示亮度  ·  " + (progress + 1) + "%");
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        panel.addView(rearBrightness, matchWrap());
        rearSunlight = new Switch(this);
        rearSunlight.setText("刷码时开启阳光模式");
        rearSunlight.setTextSize(15);
        rearSunlight.setTextColor(COLOR_TEXT);
        rearSunlight.setPadding(0, dp(8), 0, dp(8));
        panel.addView(rearSunlight, matchWrap());
        panel.addView(text("联动系统背屏阳光模式，退出或锁屏后恢复原设置。实际增亮由环境光及系统策略决定。", 12, COLOR_SUBTEXT, false));
        rearSave = new android.widget.Button(this);
        rearSave.setText("保存背屏设置");
        rearSave.setTextSize(15);
        rearSave.setTextColor(Color.WHITE);
        rearSave.setAllCaps(false);
        rearSave.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        rearSave.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), roundBackground(COLOR_ACCENT, 14), null));
        rearSave.setElevation(0);
        rearSave.setOnClickListener(v -> {
            if (!requireModernService()) return;
            int seconds;
            try { seconds = Integer.parseInt(rearSeconds.getText().toString().trim()); }
            catch (NumberFormatException e) { rearSeconds.setError("请输入 1～300 的整数"); return; }
            if (seconds < 1 || seconds > 300) { rearSeconds.setError("范围为 1～300 秒"); return; }
            boolean ok = preferences.edit().putInt(Config.PREF_REAR_SECONDS, seconds)
                    .putInt(Config.PREF_REAR_BRIGHTNESS, rearBrightness.getProgress() + 1)
                    .putBoolean(Config.PREF_REAR_SUNLIGHT, rearSunlight.isChecked()).commit();
            toast(ok ? "已保存，下次触发生效" : "保存失败");
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        saveLp.topMargin = dp(12);
        saveLp.bottomMargin = dp(14);
        panel.addView(rearSave, saveLp);
        panel.addView(text("锁屏即恢复系统控制 · 再次触发重新计时", 12, COLOR_SUBTEXT, false));
        refreshRear();
        return panel;
    }

    private void refreshRear() {
        if (rearSeconds == null) return;
        rearSeconds.setText(String.valueOf(preferences.getInt(Config.PREF_REAR_SECONDS, Config.DEFAULT_REAR_SECONDS)));
        rearBrightness.setProgress(Math.max(1, Math.min(100, preferences.getInt(Config.PREF_REAR_BRIGHTNESS, Config.DEFAULT_REAR_BRIGHTNESS))) - 1);
        rearSeconds.setEnabled(serviceReady);
        rearBrightness.setEnabled(serviceReady);
        rearSave.setEnabled(serviceReady);
        rearSunlight.setChecked(preferences.getBoolean(Config.PREF_REAR_SUNLIGHT, false));
        rearSunlight.setEnabled(serviceReady);
    }

    private View buildStatusCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("模块状态"));

        hookStatusText = text("正在检测…", 16, COLOR_TEXT, true);
        LinearLayout.LayoutParams statusLp = wrap();
        statusLp.topMargin = dp(12);
        card.addView(hookStatusText, statusLp);

        TextView scope = text(
                "Modern Xposed API：102\n推荐作用域：系统框架 / System Framework\n模块版本："
                        + BuildConfig.VERSION_NAME,
                13,
                COLOR_SUBTEXT,
                false
        );
        LinearLayout.LayoutParams scopeLp = wrap();
        scopeLp.topMargin = dp(8);
        card.addView(scope, scopeLp);
        return card;
    }

    @SuppressWarnings("deprecation")
    private View buildTipsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("触发 Tips"));

        tipsSwitch = new Switch(this);
        tipsSwitch.setText("显示 Tips");
        tipsSwitch.setTextSize(15);
        tipsSwitch.setTextColor(COLOR_TEXT);
        tipsSwitch.setChecked(preferences.getBoolean(Config.PREF_SHOW_TIPS, false));
        LinearLayout.LayoutParams switchLp = matchWrap();
        switchLp.topMargin = dp(10);
        card.addView(tipsSwitch, switchLp);

        TextView description = text(
                "开启后，背部双击 / 三击成功触发时在主屏显示敲击次数和实际功能。",
                13,
                COLOR_SUBTEXT,
                false
        );
        LinearLayout.LayoutParams descLp = wrap();
        descLp.topMargin = dp(6);
        card.addView(description, descLp);

        tipsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (loadingUi) {
                return;
            }
            if (!requireModernService()) {
                refreshTips();
                return;
            }
            boolean ok = preferences.edit().putBoolean(Config.PREF_SHOW_TIPS, isChecked).commit();
            if (ok) {
                toast(isChecked ? "Tips 已开启" : "Tips 已关闭");
            } else {
                toast("保存 Tips 设置失败");
                refreshTips();
            }
        });

        return card;
    }

    private GestureViews buildGestureCard(
            String title,
            String settingKey,
            String actionPrefKey,
            String displayPrefKey
    ) {
        GestureViews views = new GestureViews();
        views.settingKey = settingKey;
        views.actionPrefKey = actionPrefKey;
        views.displayPrefKey = displayPrefKey;
        views.card = card();
        views.card.addView(sectionTitle(title));

        TextView actionLabel = text("功能", 14, COLOR_SUBTEXT, false);
        LinearLayout.LayoutParams labelLp = wrap();
        labelLp.topMargin = dp(14);
        views.card.addView(actionLabel, labelLp);

        views.actionGroup = new RadioGroup(this);
        views.actionGroup.setOrientation(LinearLayout.HORIZONTAL);
        views.offId = View.generateViewId();
        views.paymentId = View.generateViewId();
        views.busId = View.generateViewId();
        addSegment(views.actionGroup, radio("关闭", views.offId), false);
        addSegment(views.actionGroup, radio("付款码", views.paymentId), false);
        addSegment(views.actionGroup, radio("乘车码", views.busId), true);
        LinearLayout.LayoutParams groupLp = matchWrap();
        groupLp.topMargin = dp(4);
        views.card.addView(views.actionGroup, groupLp);

        views.warningText = text("", 12, COLOR_SUBTEXT, false);
        views.warningText.setVisibility(View.GONE);
        views.card.addView(views.warningText);

        TextView displayLabel = text("显示位置", 14, COLOR_SUBTEXT, false);
        LinearLayout.LayoutParams displayLabelLp = wrap();
        displayLabelLp.topMargin = dp(14);
        views.card.addView(displayLabel, displayLabelLp);

        views.displayGroup = new RadioGroup(this);
        views.displayGroup.setOrientation(LinearLayout.HORIZONTAL);
        views.mainId = View.generateViewId();
        views.rearId = View.generateViewId();
        addSegment(views.displayGroup, radio("主屏显示", views.mainId), false);
        addSegment(views.displayGroup, radio("背屏显示", views.rearId), true);
        LinearLayout.LayoutParams displayGroupLp = matchWrap();
        displayGroupLp.topMargin = dp(4);
        views.card.addView(views.displayGroup, displayGroupLp);

        views.actionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingUi || checkedId == -1) {
                return;
            }
            String action = actionForId(views, checkedId);
            if (action != null) {
                requestGestureChange(views, action);
            }
        });

        views.displayGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingUi || checkedId == -1) {
                return;
            }
            if (!requireModernService()) {
                refreshGesture(views);
                return;
            }
            String display = checkedId == views.mainId ? Config.DISPLAY_MAIN : Config.DISPLAY_REAR;
            boolean ok = preferences.edit().putString(views.displayPrefKey, display).commit();
            toast(ok ? "配置已生效" : "保存显示位置失败");
        });

        return views;
    }

    private void requestGestureChange(GestureViews views, String action) {
        if (!requireModernService()) {
            refreshGesture(views);
            return;
        }
        if (!isCurrentHookLoaded()) {
            toast("新版 Modern Hook 尚未加载；从 Legacy 首次升级请重启手机一次");
            refreshGesture(views);
            return;
        }

        String currentSystem = Settings.System.getString(getContentResolver(), views.settingKey);
        String previousAction = readConfiguredAction(views, currentSystem, false);
        if (!preferences.edit().putString(views.actionPrefKey, action).commit()) {
            toast("保存功能配置失败");
            refreshGesture(views);
            return;
        }

        String expectedNative = nativeFunctionForAction(action);
        mainHandler.postDelayed(() -> {
            try {
                String actual = Settings.System.getString(getContentResolver(), views.settingKey);
                if (expectedNative.equals(actual)) {
                    toast("配置已生效");
                } else {
                    preferences.edit().putString(views.actionPrefKey, previousAction).commit();
                    toast("系统手势未同步，请查看 LSPosed 日志");
                }
            } catch (Throwable t) {
                toast("无法验证系统手势配置");
            }
            refreshGesture(views);
        }, 650);
    }

    private boolean requireModernService() {
        if (serviceReady && xposedService != null) {
            return true;
        }
        toast("Modern Xposed 服务未连接，请确认 LSPosed 2.2.0 已启用模块");
        return false;
    }

    private void refreshAll() {
        refreshRear();
        refreshHookStatus();
        refreshTips();
        if (doubleTapViews != null) {
            refreshGesture(doubleTapViews);
        }
        if (tripleTapViews != null) {
            refreshGesture(tripleTapViews);
        }
    }

    private void refreshTips() {
        if (tipsSwitch == null) {
            return;
        }
        loadingUi = true;
        try {
            tipsSwitch.setChecked(preferences.getBoolean(Config.PREF_SHOW_TIPS, false));
            tipsSwitch.setEnabled(serviceReady);
            tipsSwitch.setAlpha(serviceReady ? 1.0f : 0.5f);
        } finally {
            loadingUi = false;
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
        if (hookStatusText == null) {
            return;
        }

        String hookVersion = Settings.System.getString(getContentResolver(), Config.STATUS_HOOK_VERSION);
        int hookBootCount = Settings.System.getInt(
                getContentResolver(), Config.STATUS_HOOK_BOOT_COUNT, Integer.MIN_VALUE);
        int currentBootCount = Settings.Global.getInt(
                getContentResolver(), Settings.Global.BOOT_COUNT, Integer.MIN_VALUE);
        String serviceLine = "\n" + frameworkInfo
                + (serviceReady ? "\nRemote Preferences：正常" : "\nRemote Preferences：未连接");

        if (hookVersion != null && hookBootCount == currentBootCount) {
            if (BuildConfig.VERSION_NAME.equals(hookVersion)) {
                hookStatusText.setText("● Modern Hook 已加载" + serviceLine);
                hookStatusText.setTextColor(Color.rgb(20, 135, 70));
            } else {
                hookStatusText.setText(
                        "⚠ system_server 仍在运行旧 Hook " + hookVersion
                                + "\n当前 APK：" + BuildConfig.VERSION_NAME
                                + "\n从 Legacy 首次升级到 Modern API 102 需重启一次"
                                + "\n之后更新将优先使用 Hot Reload"
                                + serviceLine
                );
                hookStatusText.setTextColor(Color.rgb(190, 110, 0));
            }
        } else {
            hookStatusText.setText(
                    "⚠ 本次启动尚未检测到 Modern Hook"
                            + "\n请确认 LSPosed 已启用模块，作用域为“系统框架”"
                            + "\n首次从 Legacy 升级请重启手机一次"
                            + serviceLine
            );
            hookStatusText.setTextColor(Color.rgb(190, 110, 0));
        }
    }

    private void refreshGesture(GestureViews views) {
        loadingUi = true;
        try {
            String currentSystem = Settings.System.getString(getContentResolver(), views.settingKey);
            String action = readConfiguredAction(views, currentSystem, serviceReady);

            views.actionGroup.clearCheck();
            views.warningText.setVisibility(View.GONE);

            if (Config.FUNCTION_BUS.equals(action)) {
                views.actionGroup.check(views.busId);
            } else if (Config.FUNCTION_PAYMENT.equals(action)) {
                views.actionGroup.check(views.paymentId);
            } else {
                views.actionGroup.check(views.offId);
            }

            String expectedNative = nativeFunctionForAction(action);
            boolean legacyBusActive = Config.FUNCTION_BUS.equals(action)
                    && Config.FUNCTION_BUS.equals(currentSystem);
            boolean nativeActive = expectedNative.equals(currentSystem) || legacyBusActive;

            if (!nativeActive && serviceReady) {
                String shown = currentSystem == null ? Config.FUNCTION_NONE : currentSystem;
                views.warningText.setText(
                        "系统当前绑定：" + shown + "。重新选择当前功能即可恢复模块绑定。"
                );
                views.warningText.setVisibility(View.VISIBLE);
            }

            String display = preferences.getString(views.displayPrefKey, Config.DISPLAY_REAR);
            if (Config.DISPLAY_MAIN.equals(display)) {
                views.displayGroup.check(views.mainId);
            } else {
                views.displayGroup.check(views.rearId);
            }

            boolean actionEnabled = serviceReady
                    && !Config.FUNCTION_NONE.equals(action)
                    && nativeActive;
            setDisplayGroupEnabled(views.displayGroup, actionEnabled);
            setActionGroupEnabled(views.actionGroup, serviceReady);
        } finally {
            loadingUi = false;
        }
    }

    private String readConfiguredAction(GestureViews views, String currentSystem, boolean migrate) {
        String action = preferences.getString(views.actionPrefKey, null);
        if (isValidAction(action)) {
            return action;
        }

        String inferred;
        if (Config.FUNCTION_BUS.equals(currentSystem)) {
            inferred = Config.FUNCTION_BUS;
        } else if (Config.FUNCTION_PAYMENT.equals(currentSystem)) {
            inferred = Config.FUNCTION_PAYMENT;
        } else {
            inferred = Config.FUNCTION_NONE;
        }

        if (migrate) {
            preferences.edit().putString(views.actionPrefKey, inferred).commit();
        }
        return inferred;
    }

    private static boolean isValidAction(String action) {
        return Config.FUNCTION_NONE.equals(action)
                || Config.FUNCTION_PAYMENT.equals(action)
                || Config.FUNCTION_BUS.equals(action);
    }

    private static String nativeFunctionForAction(String action) {
        return Config.FUNCTION_NONE.equals(action)
                ? Config.FUNCTION_NONE
                : Config.FUNCTION_PAYMENT;
    }

    private static String actionForId(GestureViews views, int id) {
        if (id == views.offId) return Config.FUNCTION_NONE;
        if (id == views.paymentId) return Config.FUNCTION_PAYMENT;
        if (id == views.busId) return Config.FUNCTION_BUS;
        return null;
    }

    private void setActionGroupEnabled(RadioGroup group, boolean enabled) {
        group.setEnabled(enabled);
        group.setAlpha(enabled ? 1.0f : 0.5f);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
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
        button.setButtonDrawable((android.graphics.drawable.Drawable) null);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setMinimumWidth(0);
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setTextSize(14);
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{COLOR_ACCENT, COLOR_SUBTEXT}));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked}, roundBackground(Color.rgb(231, 241, 255), 12));
        states.addState(new int[]{}, roundBackground(Color.rgb(245, 247, 250), 12));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x181677FF), states, null));
        return button;
    }

    private GradientDrawable roundBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void addSegment(RadioGroup group, RadioButton button, boolean last) {
        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(0, dp(46), 1);
        if (!last) lp.rightMargin = dp(8);
        group.addView(button, lp);
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
        TextView warningText;
        String settingKey;
        String actionPrefKey;
        String displayPrefKey;
        int offId;
        int paymentId;
        int busId;
        int mainId;
        int rearId;
    }
}
