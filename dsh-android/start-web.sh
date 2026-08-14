#!/data/data/com.termux/files/usr/bin/bash
# 一键启动 DeepSeek Harness Web 界面
set -e

echo "==> 进入 Debian 并加载 API Key，启动 dsh web..."
proot-distro login debian -- bash -c '
  set -e
  if [ -f "$HOME/.dsh-env" ]; then
    source "$HOME/.dsh-env"
  else
    echo "未找到 ~/.dsh-env，请先运行 install.sh 完成配置"
    exit 1
  fi
  if [ -z "${DEEPSEEK_API_KEY}" ] || [ "${DEEPSEEK_API_KEY}" = "sk-在这里粘贴你的DeepSeek-API-Key" ]; then
    echo "错误：DEEPSEEK_API_KEY 未配置或仍为占位符"
    echo "请在 Debian 内执行: nano ~/.dsh-env  填入真实 Key"
    exit 1
  fi
  echo "API Key 已加载，监听 0.0.0.0:3080"
  echo "手机浏览器访问 http://127.0.0.1:3080"
  exec dsh web --host 0.0.0.0 --port 3080
'
