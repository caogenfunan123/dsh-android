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

        btn = new Button(this);
        btn.setText("开始安装");
        btn.setTextSize(18);
        root.addView(btn);

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
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn.setEnabled(false);
                btn.setText("安装中...");
                start();
            }
        });
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
