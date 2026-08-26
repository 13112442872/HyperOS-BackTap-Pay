# HyperOS BackTap Pay

一个针对澎湃 OS 4 的 LSPosed 模块，用 HyperOS 原生背部轻敲链路触发支付宝快捷码。

## 功能

- 背部双击 / 三击可分别配置：关闭、支付宝付款码、支付宝乘车码。
- 双击和三击可分别选择主屏 / 背屏显示。
- 配置自动保存，普通配置修改后即时生效。
- LSPosed 推荐作用域：**系统框架 / System Framework**。
- 不需要 Root，也不需要“修改系统设置”权限。

## 乘车码跨重启方案

HyperOS 的背部轻敲设置并不原生提供支付宝乘车码，因此直接把 `back_double_tap` / `back_triple_tap` 写成 `launch_alipay_bus_code` 可能会在重启后被系统恢复为 `none`。

从 v0.2.2 起，付款码和乘车码都使用系统认可的付款码动作作为原生触发入口：

```text
back_double_tap / back_triple_tap = launch_alipay_payment_code
```

模块在 XSharedPreferences 中单独保存真实功能：

```text
double_action / triple_action = launch_alipay_payment_code / launch_alipay_bus_code / none
double_display / triple_display = main / rear
```

触发背部轻敲时，Hook 会按模块配置把本次 `triggerFunction` 的付款码动作动态改为乘车码，并继续写入 `show_code_display=0/1`。因此系统设置始终保留合法值，乘车码选择也能跨重启保持。

## 使用

1. 安装 GitHub Release 中的 APK。
2. 在 LSPosed 中启用模块，作用域选择 **系统框架 / System Framework**。
3. 首次启用模块或更新了 Hook 代码后，重启手机一次。
4. 打开模块 App，配置双击 / 三击的功能与显示位置。

## Hook 状态

首页会显示当前 Hook 是否已加载，并检测 APK 版本与 system_server 中实际运行的 Hook 版本是否一致。

## 日志

模块日志 Tag：`HyperOSBackTapPay`。

## 构建

推送到 `main` 后，GitHub Actions 会自动编译 Release APK 并发布到对应版本的 GitHub Release。
