# HyperOS BackTap Pay

一个针对澎湃 OS 4 的 LSPosed 模块，用 HyperOS 原生背部轻敲链路触发支付宝快捷码。

## 功能

- 背部双击 / 三击可分别配置：关闭、支付宝付款码、支付宝乘车码。
- 双击和三击可分别选择主屏 / 背屏显示。
- 可选开启 **触发 Tips**：触发后在主屏显示敲击次数和实际功能，不用再翻到背屏确认。
- 乘车码使用系统认可的付款码动作作为占位入口，跨重启保持。
- 不需要 Root，也不需要“修改系统设置”权限。

## Modern Xposed API 102

从 v0.3.0 起，模块已从 Legacy Xposed API 迁移到 **Modern Xposed API 102**：

- 入口：`META-INF/xposed/java_init.list`
- 作用域：`META-INF/xposed/scope.list`，目标为 `system`（system_server）
- 配置：使用官方 **Remote Preferences**，不再使用 XSharedPreferences
- `module.prop`：`targetApiVersion=102`、`autoHotReload=true`
- APK 更新后由支持 API 102 的 LSPosed 优先执行 Hot Reload，无需每次重启 system_server

从 v0.2.4 及更早的 Legacy 版本首次升级到 v0.3.0 时，旧进程中的 Legacy Hook 无法直接热切换为 Modern Hook，因此首次迁移仍需要重启手机一次。完成迁移后，后续 Modern API 102 版本更新可使用 Hot Reload；若框架热重载失败，再手动重启即可。

## 配置存储

v0.3.0 使用 Remote Preferences 保存：

```text
double_action / triple_action = launch_alipay_payment_code / launch_alipay_bus_code / none
double_display / triple_display = main / rear
show_tips = true / false
```

首次启动 v0.3.0 App 时，会尝试把旧版本地配置迁移到 Remote Preferences。

## 乘车码跨重启方案

HyperOS 的背部轻敲设置本身没有支付宝乘车码选项，因此系统设置始终只保存合法入口：

```text
back_double_tap / back_triple_tap = launch_alipay_payment_code
```

如果 Remote Preferences 中选择的是乘车码，Hook 在本次 `triggerFunction` 调用中动态把付款码改为 `launch_alipay_bus_code`，同时写入 `show_code_display=0/1` 选择主屏或背屏。

## 触发 Tips

开启“显示 Tips”后，成功触发时会显示类似：

```text
背部双击 · 支付宝乘车码 ✓
背部三击 · 支付宝付款码 ✓
```

Tips 开关、动作和显示位置都通过 Remote Preferences 即时同步，不需要重启。

## 使用

1. 安装 GitHub Release 中的 APK。
2. 在 LSPosed 2.2.0 中启用模块，作用域选择 **系统框架 / System Framework**。
3. 如果是从 v0.2.x Legacy 版本升级到 v0.3.0，首次迁移后重启手机一次。
4. 打开模块 App，确认显示“Modern Hook 已加载”，然后配置双击 / 三击功能、显示位置和 Tips。
5. 后续 v0.3.x Modern 版本更新正常情况下由 API 102 Hot Reload 自动生效。

## 日志

模块日志 Tag：`HyperOSBackTapPay`。

## 构建

推送到 `main` 后，GitHub Actions 自动编译 Release APK 并发布到对应版本的 GitHub Release。
