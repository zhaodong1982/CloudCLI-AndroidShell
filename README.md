# CloudCLI Android Shell

CloudCLI Android Shell 是一个面向 Android/Termux 的轻量本机外壳。它把运行在
`127.0.0.1:3001` 的 CloudCLI 服务包装成独立 Android 应用，日常使用无需打开
Chrome，也不会把服务暴露到局域网或公网。

> 非 CloudCLI 或 Anthropic 官方项目。本仓库只包含 Android 外壳，不包含
> CloudCLI 后端、模型账号、会话数据或任何 API 凭据。

## 功能

- 固定连接 `http://127.0.0.1:3001/`，不提供服务器地址编辑入口。
- 本机 WebView 内显示 CloudCLI 项目、会话和 Agent 页面。
- 支持文件选择、图片拍摄、文件下载和可选麦克风输入。
- 支持 Android 返回键、软键盘避让、渲染进程恢复和手动刷新。
- 使用高对比度自适应矢量图标，并提供 Android 主题图标资源。
- 离线时显示本机恢复说明，不清除 Cookie、草稿或会话状态。

## 安全边界

- 主页面、子资源、重定向、下载和麦克风请求都必须与
  `http://127.0.0.1:3001/` 严格同源。
- 外部 `http`、`https`、`mailto` 和 `tel` 导航在应用内直接拦截，不交给浏览器。
- 禁用 WebView 调试、文件系统访问、第三方 Cookie、混合内容和 JavaScript Bridge。
- Android 备份已关闭；应用仅申请 `INTERNET` 和可选 `RECORD_AUDIO`。
- 本仓库不提交 APK、截图、构建目录、签名文件、密码文件或本地环境配置。

更完整的威胁模型见 [SECURITY.md](SECURITY.md)。

## 要求

- Android 6.0（API 23）或更高版本。
- Android System WebView。
- 已在同一台 Android 设备的 Termux 中运行 CloudCLI，并监听
  `127.0.0.1:3001`。
- 从源码构建时需要 JDK、Android API 35 平台文件、`aapt2`、`d8` 和
  `apksigner`。

## 构建

```sh
./build.sh
./verify.sh
```

默认读取 `$HOME/.local/share/android-sdk/android-35/android.jar`，也可以显式指定：

```sh
ANDROID_JAR=/path/to/android-35/android.jar ./build.sh
```

构建结果位于 `output/CloudCLI-Shell.apk`。首次构建会在仓库外创建独立的本地
签名身份；签名私钥不会进入 Git。详细说明见 [docs/BUILD.md](docs/BUILD.md)。

## 安装与启动

```sh
adb install -r output/CloudCLI-Shell.apk
adb shell am start -n local.cloudcli.shell/.MainActivity
```

在具备相应权限的本机 Android shell 中也可以使用 `pm install -r` 和
`am start`。

## 验证后端

```sh
curl -fsS http://127.0.0.1:3001/health
```

如果使用 runit 管理 CloudCLI：

```sh
sv status cloudcli
```

## 升级兼容性

当前应用显示名为 `CloudCLI`，版本为 `1.0.1`（versionCode `2`），包名为
`local.cloudcli.shell`。升级必须继续使用同一签名证书，否则 Android 会拒绝覆盖安装。

## 卸载与回滚

```sh
adb uninstall local.cloudcli.shell
```

卸载只会删除 Android 外壳及其 WebView 本地状态，不会删除 Termux 中的 CloudCLI
服务、项目、会话或模型账号数据。

## 项目结构

```text
app/src/main/       Android Manifest、Java 源码和资源
docs/               构建与架构说明
tests/              手工验收用例
build.sh             无 Gradle 的轻量构建脚本
verify.sh            签名、权限和安全边界静态检查
```

## 已知边界

- 这是原生 Android 外壳加本机 WebView，不是对 CloudCLI 前端的原生重写。
- CloudCLI 的账号认证、Agent 执行和数据持久化由本机后端负责。
- 应用不会自动安装、配置或升级 CloudCLI 后端。

