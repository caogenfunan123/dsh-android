package com.dsh.installer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView logView;
    private ScrollView scrollView;
    private Button btn;
    private EditText etApiKey, etBaseUrl, etModel;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int dp = (int) (8 * getResources().getDisplayMetrics().density);
        int pad = 16 * dp / 8;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("DSH Installer");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0B5FFF"));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("DeepSeek Harness 一键部署 (Termux + Debian + Node + dsh)\n需要 root 权限");
        sub.setTextSize(13);
        sub.setTextColor(Color.DKGRAY);
        root.addView(sub);

        TextView cfgTitle = new TextView(this);
        cfgTitle.setText("模型与中转站配置（保存到 Termux ~/.dsh-env）");
        cfgTitle.setTextSize(15);
        cfgTitle.setTypeface(null, Typeface.BOLD);
        cfgTitle.setTextColor(Color.parseColor("#0B5FFF"));
        LinearLayout.LayoutParams cfgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cfgLp.topMargin = dp;
        root.addView(cfgTitle, cfgLp);

        etApiKey = makeField(root, "API Key", "sk-...");
        etBaseUrl = makeField(root, "中转站 Base URL（可留空用官方）",
                "https://api.deepseek.com/v1");
        etModel = makeField(root, "模型名（可留空用默认）", "deepseek-v4-flash");

        LinearLayout cfgRow = new LinearLayout(this);
        cfgRow.setOrientation(LinearLayout.HORIZONTAL);
        cfgRow.setGravity(Gravity.CENTER_VERTICAL);
        Button saveCfg = new Button(this);
        saveCfg.setText("保存配置");
        saveCfg.setTextSize(15);
        cfgRow.addView(saveCfg);
        Button loadCfg = new Button(this);
        loadCfg.setText("读取当前配置");
        loadCfg.setTextSize(15);
        cfgRow.addView(loadCfg);
        LinearLayout.LayoutParams cfgRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cfgRowLp.topMargin = dp;
        root.addView(cfgRow, cfgRowLp);

        btn = new Button(this);
        btn.setText("开始安装");
        btn.setTextSize(18);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp;
        root.addView(btn, btnLp);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextColor(Color.parseColor("#D0E0E8"));
        logView.setBackgroundColor(Color.parseColor("#0E141A"));
        logView.setPadding(pad, pad, pad, pad);
        logView.setTextIsSelectable(true);

        scrollView = new ScrollView(this);
        scrollView.addView(logView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp;
        root.addView(scrollView, lp);

        setContentView(root);

        saveCfg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
            }
        });
        loadCfg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadConfig();
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn.setEnabled(false);
                btn.setText("安装中...");
                start();
            }
        });
    }

    private EditText makeField(LinearLayout parent, String label, String hint) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Color.DKGRAY);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        parent.addView(tv, tvLp);

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(14);
        et.setSingleLine(false);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        et.setTextColor(Color.parseColor("#101418"));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = (int) (2 * getResources().getDisplayMetrics().density);
        parent.addView(et, etLp);
        return et;
    }

    private void log(final String s) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                logView.append(s + "\n");
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private String cfgFilePath() {
        return "/data/data/com.termux/files/home/.dsh-env";
    }

    private void saveConfig() {
        final String apiKey = etApiKey.getText().toString().trim();
        final String baseUrl = etBaseUrl.getText().toString().trim();
        final String model = etModel.getText().toString().trim();
        exec.execute(new Runnable() {
            @Override
            public void run() {
                if (!hasRoot()) {
                    log("错误：未获得 root 权限，无法写 Termux 配置");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("# dsh config (written by DSH Installer)\n");
                if (!apiKey.isEmpty()) {
                    sb.append("export DEEPSEEK_API_KEY=").append(apiKey).append("\n");
                }
                if (!baseUrl.isEmpty()) {
                    sb.append("export DEEPSEEK_BASE_URL=").append(baseUrl).append("\n");
                }
                if (!model.isEmpty()) {
                    sb.append("export DSH_MODEL=").append(model).append("\n");
                }
                String content = sb.toString();
                try {
                    File tmp = new File(getFilesDir(), "dsh-env.tmp");
                    FileOutputStream out = new FileOutputStream(tmp);
                    out.write(content.getBytes("UTF-8"));
                    out.close();
                    String uid = termuxUid();
                    if (uid == null) {
                        log("错误：无法确定 Termux 用户 id");
                        return;
                    }
                    sh("cp '" + tmp.getAbsolutePath() + "' " + cfgFilePath());
                    sh("chown " + uid + ":" + uid + " " + cfgFilePath());
                    sh("chmod 600 " + cfgFilePath());
                    sh("su " + uid + " -c 'proot-distro login debian -- bash -c \"cp /data/data/com.termux/files/home/.dsh-env /root/.dsh-env 2>/dev/null || true\"'");
                    log("[OK] 配置已保存到 " + cfgFilePath());
                } catch (Exception e) {
                    log("保存失败: " + e);
                }
            }
        });
    }

    private void loadConfig() {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                String content = cat(cfgFilePath());
                if (content == null || content.trim().isEmpty()) {
                    log("未读取到配置（文件不存在或为空）");
                    return;
                }
                String key = extract(content, "DEEPSEEK_API_KEY");
                String base = extract(content, "DEEPSEEK_BASE_URL");
                String model = extract(content, "DSH_MODEL");
                final String fKey = key, fBase = base, fModel = model;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        etApiKey.setText(fKey);
                        etBaseUrl.setText(fBase);
                        etModel.setText(fModel);
                    }
                });
                log("[OK] 已读取现有配置");
            }
        });
    }

    private String extract(String content, String var) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("export " + var + "=")) {
                return line.substring(("export " + var + "=").length());
            }
            if (line.startsWith(var + "=")) {
                return line.substring((var + "=").length());
            }
        }
        return "";
    }

    private String cat(String path) {
        try {
            Process p = new ProcessBuilder("su", "-c", "cat '" + path + "'").start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void start() {
        exec.execute(new Runnable() {
            @Override
            public void run() {
            try {
                if (!hasRoot()) {
                    log("错误：未获得 root 权限");
                    return;
                }
                log("[OK] root 权限已获取");

                File apk = new File(getFilesDir(), "termux.apk");
                copyAsset("termux.apk", apk);
                sh("cp '" + apk.getAbsolutePath() + "' /data/local/tmp/termux.apk");
                log("[1/6] 安装 Termux ...");
                sh("pm install -r /data/local/tmp/termux.apk");

                log("[2/6] 启动 Termux 初始化 ...");
                sh("am start -n com.termux/.app.TermuxActivity");
                boolean boot = false;
                for (int i = 0; i < 90; i++) {
                    if (new File("/data/data/com.termux/files/usr/bin/bash").exists()) {
                        boot = true;
                        break;
                    }
                    Thread.sleep(2000);
                }
                if (!boot) {
                    log("错误：Termux bootstrap 初始化超时");
                    return;
                }
                log("[OK] Termux bootstrap 就绪");

                File shf = new File(getFilesDir(), "setup-dsh.sh");
                copyAsset("setup-dsh.sh", shf);
                sh("cp '" + shf.getAbsolutePath() + "' /data/data/com.termux/files/home/setup-dsh.sh");
                String uid = termuxUid();
                if (uid == null) {
                    log("错误：无法确定 Termux 用户 id");
                    return;
                }
                log("[OK] Termux uid = " + uid);
                sh("chown " + uid + ":" + uid + " /data/data/com.termux/files/home/setup-dsh.sh");
                sh("chmod 755 /data/data/com.termux/files/home/setup-dsh.sh");

                log("[3/6] 安装 Debian + Node + dsh (约 5-15 分钟，日志见下方)...");
                MainActivity.this.run("su 0 -c 'setpriv --reuid " + uid + " --regid " + uid
                        + " --clear-groups bash /data/data/com.termux/files/home/setup-dsh.sh'");

                log("");
                log("==============================================");
                log("安装流程结束。打开 Termux 执行：");
                log("  bash ~/start-dsh.sh");
                log("然后浏览器访问 http://127.0.0.1:3080");
                log("API Key 配置：nano ~/.dsh-env");
                log("==============================================");
            } catch (Exception e) {
                log("错误: " + e);
            } finally {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        btn.setEnabled(true);
                    }
                });
            }
            }
        });
    }

    private boolean hasRoot() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id").start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private String termuxUid() {
        try {
            Process p = new ProcessBuilder("su", "-c", "stat -c %u /data/data/com.termux").start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            if (line != null && line.trim().matches("\\d+")) {
                return line.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void sh(String cmd) throws Exception {
        String out = exec(cmd, true);
        if (out != null && !out.trim().isEmpty()) {
            log("  " + out.trim());
        }
    }

    private String exec(String cmd, boolean asRoot) {
        try {
            ProcessBuilder pb = asRoot ? new ProcessBuilder("su", "-c", cmd)
                    : new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "exec error: " + e.getMessage();
        }
    }

    private void run(String cmd) {
        log("$ " + cmd.replaceAll("\\s+", " "));
        String out = exec(cmd, false);
        if (out != null && !out.trim().isEmpty()) {
            log(out.trim());
        }
    }

    private void copyAsset(String name, File dest) throws IOException {
        InputStream in = getAssets().open(name);
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
    }
}
