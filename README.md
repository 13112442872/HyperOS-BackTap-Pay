# HyperOS BackTap Pay

一个针对澎湃 OS 4 的 LSPosed 模块，用 HyperOS 原生背部轻敲链路触发支付宝快捷码。

## V0.2.1 功能

- LSPosed 自动推荐作用域：**系统框架 / System Framework**。
- 背部双击可选：关闭 / 支付宝付款码 / 支付宝乘车码。
- 背部三击可选：关闭 / 支付宝付款码 / 支付宝乘车码。
- 双击和三击可分别选择：主屏 / 背屏。
- 配置自动保存，普通配置修改后即时生效，不需要 Root。
- 首页显示当前 Hook 状态，并可识别“APK 已更新但 system_server 仍运行旧版 Hook”的情况。

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

V0.2.1 起，模块 App **不再直接写 Settings.System**。界面选择功能后，会向已经加载在 `system_server` 中的 Hook 发送受签名权限保护的控制广播，再由系统进程写入上述两个键，避免 HyperOS 对普通 App 写私有 System 键时发生异常。

主屏 / 背屏选择仍使用 LSPosed `XSharedPreferences` 保存：

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

- 不需要 Root。
- 不需要 Android 的“修改系统设置”特殊权限。
- 控制 system_server 的广播使用模块自定义 signature 权限限制，仅同签名模块 APK 可以发送。

## 使用

1. 安装 GitHub Actions 生成的 APK。
2. 在 LSPosed 中启用模块；推荐作用域会自动显示 **系统框架 / System Framework**。
3. 首次启用模块或更新 APK 后，手动重启手机一次，让 system_server 加载新 Hook。
4. 打开模块 App，分别配置背部双击 / 三击的功能和显示位置。
5. 后续普通配置修改即时生效，无需再次重启。

## Hook 状态

首页会显示：

- `Hook 已加载`：当前 system_server 已加载与 APK 相同版本的 Hook。
- `系统框架仍在运行旧版 Hook`：APK 已更新，需要手动重启手机一次。
- `本次启动尚未检测到 Hook`：检查 LSPosed 是否启用模块并勾选系统框架。

## 日志

模块日志 Tag：`HyperOSBackTapPay`。

功能切换成功时会看到类似：

```text
HyperOSBackTapPay: system_server write back_double_tap=launch_alipay_payment_code result=true
```

触发快捷码时会看到类似：

```text
HyperOSBackTapPay: set show_code_display=1 for back_double_tap -> launch_alipay_payment_code
```

## 构建

每次推送到 `main` 都会由 GitHub Actions 自动构建 Debug APK，并上传为 Actions Artifact。
