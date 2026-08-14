#!/data/data/com.termux/files/usr/bin/bash
# 进入 dsh 命令行环境（headless / 交互式 CLI）
set -e
proot-distro login debian -- bash -c '
  set -e
  if [ -f "$HOME/.dsh-env" ]; then
    source "$HOME/.dsh-env"
  fi
  exec bash
'
