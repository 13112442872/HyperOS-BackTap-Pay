# HyperOS BackTap Pay

一个针对澎湃 OS 4 的极简 LSPosed 模块。

## 功能

当系统快捷手势同时满足：

- `back_double_tap`
- `launch_alipay_payment_code`

模块会在 `ShortCutActionsUtils#triggerFunction` 执行前，把 Bundle 中的：

```text
show_code_display=1
```

写入为背屏显示目标，从而让“背部轻敲两下”触发支付宝付款码时走系统原生背屏链路。

## 使用

1. 安装 GitHub Actions 生成的 Debug APK。
2. 在 LSPosed 中启用模块。
3. 作用域只勾选 **系统框架 / System Framework (`android`)**。
4. 重启手机。
5. 保持系统中的 `back_double_tap=launch_alipay_payment_code`。
6. 背部轻敲两下测试。

## 日志

模块日志 Tag：`HyperOSBackTapPay`。

成功匹配时会出现：

```text
HyperOSBackTapPay: forced show_code_display=1 for back_double_tap -> launch_alipay_payment_code
```

## 构建

每次推送到 `main` 都会由 GitHub Actions 自动构建 Debug APK，并上传为 Actions Artifact。
