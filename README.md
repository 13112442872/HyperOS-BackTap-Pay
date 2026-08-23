# HyperOS BackTap Pay

一个针对澎湃 OS 4 的 LSPosed 模块，用 HyperOS 原生背部轻敲链路触发支付宝快捷码。

## V0.2.0 功能

- LSPosed 自动推荐作用域：**系统框架 / System Framework**。
- 背部双击可选：关闭 / 支付宝付款码 / 支付宝乘车码。
- 背部三击可选：关闭 / 支付宝付款码 / 支付宝乘车码。
- 双击和三击可分别选择：主屏 / 背屏。
- 配置自动保存，普通配置修改后即时生效，不需要重启系统框架或手机。
- 首页显示当前 Hook 状态，并可识别“APK 已更新但 system_server 仍运行旧版 Hook”的情况。
- **不申请 Root 权限，也没有 Root 相关功能。**

## 实现方式

手势功能继续使用 HyperOS 原生 `Settings.System`：

```text
back_double_tap
back_triple_tap
```

可写入：

```text
none
launch_alipay_payment_code
launch_alipay_bus_code
```

主屏 / 背屏选择使用 LSPosed `XSharedPreferences` 保存：

```text
double_display = main / rear
triple_display = main / rear
```

当 `ShortCutActionsUtils#triggerFunction` 收到支付宝付款码或乘车码动作时，模块在执行前写入：

```text
show_code_display = 0   # 主屏
show_code_display = 1   # 背屏
```

因此仍由 HyperOS 和支付宝原生逻辑完成真正的快捷码启动与背屏展示。

## 权限说明

模块不需要 Root。

配置界面为了直接修改 `back_double_tap` / `back_triple_tap`，需要 Android 的 **“修改系统设置”** 特殊权限（`WRITE_SETTINGS`）。这是普通 Android 特殊权限，不是 Root 权限。

## 使用

1. 安装 GitHub Actions 生成的 APK。
2. 在 LSPosed 中启用模块；推荐作用域会自动显示 **系统框架 / System Framework**。
3. 首次启用模块或更新 APK 后，手动重启手机一次，让 system_server 加载新 Hook。
4. 打开模块 App，按提示授予“修改系统设置”权限。
5. 分别配置背部双击 / 三击的功能和显示位置。
6. 后续修改普通配置均即时生效，无需再次重启。

## Hook 状态

首页会显示：

- `Hook 已加载`：当前 system_server 已加载与 APK 相同版本的 Hook。
- `系统框架仍在运行旧版 Hook`：APK 已更新，需要手动重启手机一次。
- `本次启动尚未检测到 Hook`：检查 LSPosed 是否启用模块并勾选系统框架。

## 日志

模块日志 Tag：`HyperOSBackTapPay`。

匹配成功时会出现类似：

```text
HyperOSBackTapPay: set show_code_display=1 for back_double_tap -> launch_alipay_payment_code
```

## 构建

每次推送到 `main` 都会由 GitHub Actions 自动构建 Debug APK，并上传为 Actions Artifact。
