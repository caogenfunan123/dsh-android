#!/data/data/com.termux/files/usr/bin/bash
# DeepSeek Harness 自动配置脚本（在 Termux 内以 termux 用户执行）
# 由 DSH Installer APK 自动调用；也可手动运行：bash ~/setup-dsh.sh

set -e

export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export TMPDIR="$PREFIX/tmp"
export PATH="$PREFIX/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib"
export TERMUX_APP_PACKAGE_NAME=com.termux

step() { echo ""; echo "========== $1 =========="; }

step "1/6 更新 Termux 软件源"
pkg update -y
pkg upgrade -y

step "2/6 安装 proot-distro"
pkg install -y proot-distro

step "3/6 安装 Debian (arm64)"
proot-distro install debian

step "4/6 Debian 内安装 Node.js 22 + 构建工具链"
proot-distro login debian -- apt-get update -y
proot-distro login debian -- apt-get install -y curl build-essential python3 git
proot-distro login debian -- bash -c 'curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && apt-get install -y nodejs'

step "5/6 安装 DeepSeek Harness (dsh)"
proot-distro login debian -- npm install -g @deepseek-ai/dsh

step "6/6 生成启动脚本与 Key 占位文件"
if [ ! -f "$HOME/.dsh-env" ]; then
cat > "$HOME/.dsh-env" << 'EOF'
export DEEPSEEK_API_KEY=sk-在此填写你的DeepSeek-API-Key
# 中转站可取消注释并修改：
# export DEEPSEEK_BASE_URL=https://你的中转站/v1
# export DSH_MODEL=deepseek-v4-flash
EOF
fi
# 同步一份到 Debian 侧，保证 proot 内 dsh 也能读到
proot-distro login debian -- bash -c 'cp /data/data/com.termux/files/home/.dsh-env /root/.dsh-env 2>/dev/null || true'
cat > "$HOME/start-dsh.sh" << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
# 读取 Termux 侧配置，注入 Debian 内的 dsh
exec proot-distro login debian -- bash -c 'set -a; [ -f "$HOME/.dsh-env" ] && source "$HOME/.dsh-env"; set +a; exec dsh web --host 0.0.0.0 --port 3080'
EOF
cat > "$HOME/dsh-run.sh" << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
# 单次任务：bash dsh-run.sh "你的任务描述"
TASK="$1"
exec proot-distro login debian -- bash -c 'set -a; [ -f "$HOME/.dsh-env" ] && source "$HOME/.dsh-env"; set +a; exec dsh --profile headless "$1"' _ "$TASK"
EOF
chmod +x "$HOME/start-dsh.sh" "$HOME/dsh-run.sh"

echo ""
echo "===================================================="
echo " DSH 安装完成！"
echo " 浏览器打开: http://127.0.0.1:3080"
echo " 配置文件:   ~/.dsh-env（填入真实 API Key / 中转站）"
echo " 命令行:     bash ~/dsh-run.sh \"任务描述\""
echo "===================================================="
echo "DSH_SETUP_DONE"
