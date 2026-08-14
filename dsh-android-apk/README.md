# DSH Installer - DeepSeek Harness 安卓一键安装 APK

一个 root 安装器 APK，内置 Termux v0.118.1（含 bootstrap）+ 自动配置脚本。安装后点一下按钮，自动完成 Termux → Debian(arm64) → Node.js 22 → DeepSeek Harness 的全部部署。

## 产物

```
DSH-Installer-v1.0.apk   (33MB，已签名，直接侧载安装)
```

## 工作原理

APK 内部结构：

```
DSH-Installer-v1.0.apk
├── assets/termux.apk      # Termux 0.118.1（自带 arm64 bootstrap，离线可初始化）
├── assets/setup-dsh.sh    # 自动配置脚本（root 以 termux 用户执行）
└── classes.dex             # 安装器主程序（MainActivity）
```

主程序流程（需要 root）：
1. 校验 root 权限
2. `pm install` 内置 Termux APK
3. 启动 Termux 触发 bootstrap 解压，轮询等待 `/data/data/com.termux/files/usr/bin/bash` 就绪
4. 将 `setup-dsh.sh` 放入 Termux home 并 chown 为 termux 用户
5. 以 termux 身份执行脚本：`pkg install proot-distro` → `proot-distro install debian` → Debian 内装 Node.js 22（NodeSource）+ 构建工具链 → `npm install -g @deepseek-ai/dsh`
6. 生成 `~/.dsh-env`（API Key 占位）与 `~/start-dsh.sh` 启动脚本

安装日志实时显示在应用界面。

## 使用方法

1. 安装 `DSH-Installer-v1.0.apk`（需允许"未知来源"）
2. 打开 **DSH Installer** 应用
3. **（推荐）先在应用内填写模型配置**：API Key、中转站 Base URL（留空用官方 `https://api.deepseek.com/v1`）、模型名（留空用 `deepseek-v4-flash`），点 **保存配置**。配置会写入 Termux 的 `~/.dsh-env` 并同步到 Debian 侧
4. 点 **开始安装**，等待安装完成（Debian/Node/dsh 下载约 5-15 分钟，视网速）
5. 启动工作台：
   ```bash
   bash ~/start-dsh.sh
   ```
6. 手机浏览器访问 `http://127.0.0.1:3080`，或局域网访问 `http://<手机IP>:3080`
7. 后续改模型/中转站：直接在 DSH Installer 里改配置点保存，无需重装

> 安装完成后再改配置也行：应用内"保存配置"覆盖 `~/.dsh-env`，或手动 `nano ~/.dsh-env`（支持 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DSH_MODEL` 三个变量）。

### 配置变量说明

| 变量 | 作用 | 默认值 |
|------|------|--------|
| `DEEPSEEK_API_KEY` | API Key（官方或中转站） | 必填 |
| `DEEPSEEK_BASE_URL` | 中转站地址，如 `https://your-relay/v1` | `https://api.deepseek.com/v1` |
| `DSH_MODEL` | 模型名，如 `deepseek-v4-flash` | `deepseek-v4-flash` |

### 命令行单次任务

```bash
bash ~/dsh-run.sh "帮我写一个快速排序"
```

## 约束说明

- **必须 root**：安装器通过 `su` 执行 `pm install`、写 Termux 数据目录、以 termux 用户运行脚本。无 root 无法自动安装。
- **需要网络**：Termux 初始化与 Debian/Node/dsh 下载需联网。
- **arm64**：APK 内置 Termux arm64 bootstrap，适用于 arm64 设备（绝大多数新手机）。

## 重新构建

环境需 JDK 21 + Android SDK（build-tools 35.0.0 + platform 34）：

```bash
# 生成密钥（首次）
keytool -genkeypair -keystore dsh.keystore -alias dsh -keyalg RSA \
  -keysize 2048 -validity 10000 -storepass dshinstall -keypass dshinstall \
  -dname "CN=DSH Installer, O=DSH, C=CN"

# 构建
SDK=/opt/android-sdk BT=/opt/android-sdk/build-tools/35.0.0 \
  PLATFORM=/opt/android-sdk/platforms/android-34/android.jar \
  bash build.sh
```

## 安全提示

- APK 由本环境自签密钥签名（`dsh.keystore`），非 Play 商店来源，仅限自用。
- `setup-dsh.sh` 会生成包含 API Key 占位符的 `~/.dsh-env`，请勿将真实 Key 提交到任何公共仓库。

## 目录结构

```
dsh-android-apk/
├── DSH-Installer-v1.0.apk    # 最终产物（签名 APK）
├── dsh.keystore              # 签名密钥
├── build.sh                  # 构建脚本
└── app/
    ├── AndroidManifest.xml
    ├── src/com/dsh/installer/MainActivity.java   # 安装器主程序
    └── assets/
        ├── setup-dsh.sh      # 自动配置脚本
        └── termux.apk        # 内置 Termux（构建时注入）
```
