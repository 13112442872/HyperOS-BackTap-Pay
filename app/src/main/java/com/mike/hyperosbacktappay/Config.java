package com.mike.hyperosbacktappay;

public final class Config {
    private Config() {}

    public static final String MODULE_PACKAGE = "com.mike.hyperosbacktappay";
    public static final String PREFS_NAME = "config";

    public static final String PREF_DOUBLE_DISPLAY = "double_display";
    public static final String PREF_TRIPLE_DISPLAY = "triple_display";
    public static final String DISPLAY_MAIN = "main";
    public static final String DISPLAY_REAR = "rear";

    public static final String SETTING_BACK_DOUBLE = "back_double_tap";
    public static final String SETTING_BACK_TRIPLE = "back_triple_tap";

    public static final String FUNCTION_NONE = "none";
    public static final String FUNCTION_PAYMENT = "launch_alipay_payment_code";
    public static final String FUNCTION_BUS = "launch_alipay_bus_code";

    public static final String DISPLAY_BUNDLE_KEY = "show_code_display";
    public static final int DISPLAY_ID_MAIN = 0;
    public static final int DISPLAY_ID_REAR = 1;

    public static final String STATUS_HOOK_VERSION = "hyperos_backtap_pay_hook_version";
    public static final String STATUS_HOOK_BOOT_COUNT = "hyperos_backtap_pay_hook_boot_count";
}
