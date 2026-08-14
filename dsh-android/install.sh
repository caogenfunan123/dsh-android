#!/data/data/com.termux/files/usr/bin/bash
set -e

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$1"; }

if [ "$(uname -o)" != "Android" ]; then
  echo "此脚本必须在安卓手机的 Termux 中运行"
  exit 1
fi

log "1/5 更新 Termux 并安装基础工具"
pkg update -y
pkg upgrade -y
pkg install -y proot-distro curl git nano

log "2/5 安装 Debian (arm64) 发行版"
proot-distro install debian

log "3/5 在 Debian 内安装 Node.js 22 + 构建工具"
proot-distro login debian -- apt-get update -y
proot-distro login debian -- apt-get install -y curl build-essential python3 git
proot-distro login debian -- bash -c '
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
'
proot-distro login debian -- node -v
proot-distro login debian -- npm -v

log "4/5 全局安装 DeepSeek Harness (dsh)"
proot-distro login debian -- npm install -g @deepseek-ai/dsh
proot-distro login debian -- dsh --version

log "5/5 配置 API Key 与启动脚本"
proot-distro login debian -- bash -c '
  if [ ! -f "$HOME/.dsh-env" ]; then
    cat > "$HOME/.dsh-env" << EOF
export DEEPSEEK_API_KEY=sk-在这里粘贴你的DeepSeek-API-Key
EOF
  fi
'
mkdir -p "$HOME/.termux/tasker"
cat > "$HOME/.termux/tasker/dsh-env.sh" << EOF
#!/data/data/com.termux/files/usr/bin/bash
exec proot-distro login debian -- bash -c 'source \$HOME/.dsh-env && exec bash'
EOF
chmod +x "$HOME/.termux/tasker/dsh-env.sh"

echo ""
echo "安装完成！下一步："
echo "1. 编辑 API Key:   nano $HOME/.termux/tasker/dsh-env.sh  (或在 Debian 里编辑 \$HOME/.dsh-env)"
echo "2. 进入环境:       bash $HOME/.termux/tasker/dsh-env.sh"
echo "3. 启动 Web 界面:  dsh web --host 0.0.0.0 --port 3080"
echo "4. 浏览器打开:     http://127.0.0.1:3080"
