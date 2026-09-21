# ChaCha AI

**HTC ChaCha (Android 2.3.5) 实体全键盘专属极简 AI 客户端 & 云端通用网桥**

[![Android](https://img.shields.io/badge/Android-2.3.5%20Gingerbread%2B-green.svg)](https://developer.android.com)
[![Device](https://img.shields.io/badge/Device-HTC%20ChaCha%20A810e-blue.svg)](https://en.wikipedia.org/wiki/HTC_ChaCha)
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

针对经典全键盘神机 **HTC ChaCha (A810e / HTC Status)** 深度定制的原生独立 AI 客户端。彻底解决远古 Android 2.3 系统的中文输入法兼容、现代 SSL/TLS 证书不兼容以及横屏物理按键适配等难题，让老机器重获新生，随时随地享受大模型带来的智能交互。

同时提供通用的云端网桥服务与 **iPhone 15 快捷指令 (Shortcuts)** 联动方案，实现多设备共享同一个云端 AI 大脑。

---

## 📸 运行效果

* **HTC Sense 原生中文输入法支持**：全键盘敲拼音原生弹候选字栏，空格上字，行云流水。
* **硬件 Enter 回车一键发送**：打完字直接按物理 Enter 键秒发，无需抬手点屏幕。
* **物理 FB 快捷硬按键直达**：按下键盘右下角的蓝底 Facebook 按键，蓝灯呼吸闪烁，500ms 内瞬间秒级拉起 ChaCha AI！
* **480×320 横屏大字号重构**：默认采用 **22sp 特大字号**，清晰锐利不费眼；设置弹窗内置 `[16]` `[18]` `[20]` `[22]` `[24]` `[28]` 快捷字号切换按钮，支持手指自由平滑滚动。

---

## 🚀 核心特性

1. **零第三方依赖的极简原生实现**：
   * 采用纯粹的 Android 原生控件架构，内存占用极小，在 MSM7227 800MHz + 512MB RAM 的老设备上毫秒级启动、流畅运行。
2. **免证书困扰的轻量通信架构**：
   * Android 2.3 无法握手现代 Let's Encrypt / TLS 1.3 证书。本项目采用纯原生 HTTP JSON 协议对接自建轻量网桥，彻底规避 SSL Handshake 异常。
3. **隐私安全与自由配置**：
   * 应用内**不硬编码任何私有服务器地址或秘钥**。初次使用仅需在应用内点击右上角 **[设置]** 填入您自己的服务器 IP 与 Token 即可。
4. **硬件级按键重定向（FB 键变身 AI 键）**：
   * 深入 HTC Sense 框架底层，利用扫描码 `184` (`HTC_SHARE`) 机制，直接通过系统属性将冷门的 Facebook 废键改造为专属 AI 启动键。

---

## 📦 安装与使用

### 1. 下载安装包
您可以直接从本仓库根目录下载预编译好的签名 APK：
* **[ChaChaAI.apk](./ChaChaAI.apk)** (约 41 KB)

通过 ADB 安装到您的 HTC ChaCha：
```bash
adb install -r ChaChaAI.apk
```

### 2. 配置服务器
1. 打开手机上的 **ChaCha AI** 应用；
2. 点击右上角 **[设置]**；
3. 填入您的后端服务器地址（例如 `http://192.168.1.100:8088` 或您的公网 VPS IP）以及您设置的认证 Token；
4. 点击“保存”，左上角状态灯显示绿色 `●` 即代表连接成功！

---

## ⌨️ 将键盘最下方的 Facebook 硬按键设定为启动 ChaCha AI

HTC ChaCha 键盘右下角的 Facebook 按键是硬件层面的特殊按键。通过修改手机系统的 `/system/build.prop`，无需修改任何 ROM 底包即可实现硬件直达启动：

1. 手机连接电脑开启 USB 调试，在电脑端执行：
```bash
# 1. 重新挂载系统分区为可读写
adb remount

# 2. 备份原始属性文件
adb shell "cp /system/build.prop /system/build.prop.bak"
```

2. 编辑 `/system/build.prop`，在文件末尾或对应区域加入以下属性：
```properties
## HTC ChaCha ShareKey Remap to ChaCha AI
ro.htc.sharekey.spkg=com.chacha.ai
ro.htc.sharekey.spkg.1.2=com.chacha.ai
ro.htc.sharekey.sact=com.chacha.ai.MainActivity
ro.htc.sharekey.sact.1.2=com.chacha.ai.MainActivity
ro.htc.sharekey.sint=android.intent.action.MAIN
ro.htc.sharekey.sint.1.2=android.intent.action.MAIN
ro.htc.sharekey.lpkg=com.chacha.ai
ro.htc.sharekey.lpkg.1.2=com.chacha.ai
ro.htc.sharekey.lact=com.chacha.ai.MainActivity
ro.htc.sharekey.lact.1.2=com.chacha.ai.MainActivity
ro.htc.sharekey.lint=android.intent.action.MAIN
ro.htc.sharekey.lint.1.2=android.intent.action.MAIN
```

3. 恢复权限并重启手机：
```bash
adb shell "chmod 644 /system/build.prop && reboot"
```
重启后，在任意界面甚至息屏唤醒后按下右下角 FB 键，系统将瞬间直接启动 ChaCha AI！

---

## 🖥️ 云端服务端部署 (Python)

本项目在 `server/chacha_ai_server.py` 提供了一个开箱即用的轻量级 Python HTTP 服务端，可部署在任何 Linux 服务器或家庭 NAS 上。

### 运行方式：
```bash
cd server
# 可通过环境变量指定端口与安全密钥
export CHACHA_PORT=8088
export CHACHA_TOKEN="your-secure-token-here"

python3 chacha_ai_server.py
```

服务端默认通过执行命令行或对接 LLM API 实现问答，并自动维持上下文对话会话。

---

## 📱 iPhone 15 快捷指令联动 (Shortcuts)

iPhone 15 / 15 Pro / 16 用户可利用系统自带的 **快捷指令 (Shortcuts)** 接入同一个云端大脑：

1. 打开 iOS **「快捷指令」** App，新建一个快捷指令；
2. 依次添加以下动作：
   * **要求输入**：提示词填 `你想问什么？`
   * **URL**：填入您的服务器地址 `http://your-server-ip:8088/api/chat`
   * **获取 URL 的内容**：
     * 方法选 `POST`，请求体选 `JSON`
     * 字段添加：
       * `prompt`：选择变量 `[要求输入]`
       * `token`：填入您的安全密钥
       * `continue`：`true`
   * **从输入中获取词典值**：键为 `reply`
   * **快速查看**（或 **显示提醒**）：显示 `[词典值]`
3. **硬件快捷呼出**：
   * 在 iPhone 设置中开启 **「辅助功能 -> 触控 -> 轻点背面 -> 轻点两下」**，勾选此快捷指令。
   * 轻轻敲击 iPhone 背面两下，即可随叫随到呼出 AI！

---

## 🛠️ 项目自主编译构建

本项目无需庞大臃肿的现代 Android Studio Gradle 环境，采用轻量独立的脚本即可秒级构建：

* 前置需求：`JDK 8+`、`Android SDK build-tools`
* 编译命令：
```bash
python build.py
```
构建脚本将自动执行 AAPT 资源编译、Java 编译、D8 Dex 转换、Zipalign 字节对齐以及 V1 证书签名，在根目录下生成可直接安装的 `ChaChaAI.apk`。

---

## 📄 开源许可证

本项目基于 [MIT 许可证](LICENSE) 开源。
