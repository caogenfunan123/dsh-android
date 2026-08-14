# DeepSeek Harness 安卓版部署指南（已 root + arm64）

在安卓手机上部署 DeepSeek Harness（dsh），使用 **Termux + proot-distro + Debian(arm64) + Node.js 22 + npm 全局安装 @deepseek-ai/dsh** 方案。

## 方案原理

dsh 是 Node.js 生态的 CLI/Web 应用，官方支持 Linux x64 / Linux arm64 / macOS，**不原生支持 Android**。方案通过 proot 在 Android 上运行完整的 Debian arm64 用户态，在 Debian 内安装 Node.js 22 与 dsh，获得与桌面 Linux 一致的运行环境。你已 root 且有较新 arm64 芯片，proot 方案无需 chroot 到系统分区，风险更低，且沙箱为本地 workspace 模式，不依赖 Landlock 内核特性。

## 前置条件

- 已 root 的安卓手机，arm64 处理器
- 从 F-Droid（https://f-droid.org/packages/com.termux/）安装 **Termux**（不要用 Play 商店版，包版本过旧）
- 手机有稳定的网络连接

## 安装步骤

把 `install.sh` 传输到手机（微信/网盘/`adb push` 均可），放入 Termux 可访问的目录（如 `~/storage/downloads`），然后执行：

```bash
# 在 Termux 中，把脚本复制到 home 并赋权运行
cp ~/storage/downloads/install.sh ~/install.sh
chmod +x ~/install.sh
~/install.sh
```

脚本会自动完成：
1. 更新 Termux，安装 `proot-distro`
2. 安装 Debian arm64 发行版
3. 在 Debian 内安装 Node.js 22.19+ 与构建工具链
4. `npm install -g @deepseek-ai/dsh` 全局安装 dsh
5. 生成 `~/.dsh-env` 环境文件（需手动填 Key）

## 配置 API Key

```bash
# 进入 Debian 环境
bash ~/.termux/tasker/dsh-env.sh

# 编辑 Key（把占位符替换为你的真实 Key）
nano ~/.dsh-env
```

`~/.dsh-env` 内容：
```bash
export DEEPSEEK_API_KEY=sk-xxxx
```

## 启动

### Web 界面（浏览器工作台）

```bash
bash ~/start-web.sh
```

启动后手机浏览器访问 `http://127.0.0.1:3080` 即可打开工作台（标准/极简/PTC/创造四种模式可选）。

脚本默认监听 `0.0.0.0:3080`，同一局域网内其他设备也可访问 `http://<手机IP>:3080`。

### 命令行模式

```bash
bash ~/enter-cli.sh
```

进入后可用 `dsh --profile headless "你的任务"` 跑单次任务，或 `dsh --profile tui` 启动交互式 TUI。

## 常见问题

| 问题 | 解决 |
|------|------|
| `proot-distro install debian` 卡住 | 检查网络；可换镜像源 `proot-distro install debian --mirror https://mirrors.ustc.edu.cn/debian` |
| Node.js 安装后版本低于 22.19 | 检查 `node -v`，不足则 `curl -fsSL https://deb.nodesource.com/setup_22.x \| bash -` 重装 |
| `dsh` 命令找不到 | 确认 `npm install -g @deepseek-ai/dsh` 成功；npm 全局 bin 目录需在 PATH 中（proot Debian 默认已包含） |
| 提示 API Key 无效 | 检查 `~/.dsh-env` 是否被 source；`echo $DEEPSEEK_API_KEY` 验证 |
| Web 界面打开慢 | dsh 首次启动会下载前端资源，属正常现象 |
| 沙箱/命令执行报错 | 本地 workspace 沙箱不依赖内核特性；如仍报错，用 `dsh --profile headless` 测试最小路径 |

## 目录结构

```
dsh-android/
├── install.sh        # 一键安装（在 Termux 中运行）
├── start-web.sh      # 启动 Web 界面
├── enter-cli.sh      # 进入命令行环境
└── README.md         # 本文档
```

## 已验证信息（2026-08-14 实测）

在隔离 Linux 用户态（chroot 模拟 proot 场景）中完整走通安装到启动全流程：

- `npm install -g @deepseek-ai/dsh` 在 Node 22.19.0 下安装成功（v0.1.0-rc.6，532 packages）
- `dsh --version`、`dsh web --help` 命令可用
- **node-pty 需要本地编译**：安装依赖中 `@deepseek-ai/dsh` 携带 `node-pty`（原生模块），缺少 `python3 + build-essential` 会报 `node-gyp rebuild` 失败。因此 `install.sh` 中提前安装构建工具链是必须步骤，不可省略
- `dsh web --host 127.0.0.1 --port 3080` 启动成功，HTTP 返回 200 与完整 Web UI 页面
- Web 模式支持 `--host` / `--port` / `--trusted-host` 参数
- 默认模型配置为 `deepseek-v4-flash`（通过 `DSH_MODEL` 可覆盖）
- 沙箱为本地 workspace 模式（`@deepseek-ai/dsh-sandbox-local`），不强制依赖 Landlock

验证环境为 x86_64；手机为 arm64，npm 包与 node-pty 均提供 arm64 编译路径，同一流程适用。
