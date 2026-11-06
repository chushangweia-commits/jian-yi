package com.jianyi.yixue.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native local notes and system document picker; no account or storage permission. */
public final class NotebookActivity extends Activity {
    private static final int EXPORT = 7201, IMPORT = 7202;
    private NotebookStore store;
    private NotebookRecord current;
    private String mode = "list";
    private int offset;
    private boolean busy, destroyed;
    private AlertDialog progress;
    private EditText title, question, analysis, feedback, tags;
    private CheckBox favorite;
    private LinearLayout content;
    private LinearLayout aiPanel;
    private HashMap<String, String> hexNames;
    private HashMap<String, String> caseNames;

    public static void openList(Activity activity) { activity.startActivity(new Intent(activity, NotebookActivity.class)); }
    public static void open(Activity activity, String id) {
        activity.startActivity(new Intent(activity, NotebookActivity.class).putExtra("record_id", id));
    }
    public static void launchDraft(Activity activity, String method, String info, int upper, int lower, int moving, String sourceCaseId) {
        launchCaseDraft(activity, "", "", method, info, upper, lower, moving, sourceCaseId, "");
    }
    public static void launchCaseDraft(Activity activity, String title, String question, String method, String info,
        int upper, int lower, int moving, String sourceCaseId) {
        launchCaseDraft(activity, title, question, method, info, upper, lower, moving, sourceCaseId, "");
    }
    public static void launchCaseDraft(Activity activity, String title, String question, String method, String info,
        int upper, int lower, int moving, String sourceCaseId, String priorAnalysis) {
        activity.startActivity(new Intent(activity, NotebookActivity.class).putExtra("draft", true)
            .putExtra("title", title).putExtra("question", question).putExtra("method", method).putExtra("info", info)
            .putExtra("upper", upper).putExtra("lower", lower).putExtra("moving", moving)
            .putExtra("source_case_id", sourceCaseId).putExtra("analysis", priorAnalysis));
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        JianYiUi.applySystemBars(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setTitle("卦例本");
        try {
            store = NotebookStore.get(this);
            if (saved != null && saved.containsKey("record")) {
                current = restore(saved.getBundle("record"));
                mode = saved.getString("mode", "list"); offset = saved.getInt("offset", 0);
                if ("edit".equals(mode)) {
                    edit();
                    if (saved.containsKey("editor")) fillEditor(restore(saved.getBundle("editor")));
                }
                else if ("detail".equals(mode)) showDetail();
                else showList();
            } else if (getIntent().getBooleanExtra("draft", false)) {
                current = NotebookStore.draft(getIntent().getStringExtra("method"), getIntent().getStringExtra("info"),
                    getIntent().getIntExtra("upper", 0), getIntent().getIntExtra("lower", 0), getIntent().getIntExtra("moving", 0),
                    getIntent().getStringExtra("source_case_id"));
                String draftTitle = getIntent().getStringExtra("title");
                if (draftTitle != null && draftTitle.trim().length() > 0) current.title = draftTitle;
                current.question = extra("question"); current.analysis = extra("analysis");
                BackupCodec.validate(current); edit();
            } else if (getIntent().getStringExtra("record_id") != null) {
                current = store.find(getIntent().getStringExtra("record_id"));
                if (current == null) { toast("这条记录不存在或已被删除。"); showList(); }
                else showDetail();
            } else showList();
        } catch (Exception e) { fatal(e); }
    }

    private String extra(String name) { String value = getIntent().getStringExtra(name); return value == null ? "" : value; }

    private void page(String heading, String subtitle) {
        aiPanel = null;
        LinearLayout shell = column(); JianYiUi.applyScenic(shell,JianYiUi.SCENE_DIVINATION); shell.setFitsSystemWindows(true);
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(16), dp(8), dp(16), dp(6));
        Button back = action("返回", false, new View.OnClickListener() { @Override public void onClick(View v) { onBackPressed(); } });
        nav.addView(back, new LinearLayout.LayoutParams(dp(88), -2));
        TextView label = text(heading, 18, true); label.setGravity(Gravity.CENTER);
        nav.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(nav);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = column(); content.setPadding(dp(16), 0, dp(16), dp(28));
        content.addView(JianYiUi.hero(this, heading, subtitle)); scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
    }

    private void showList() {
        hideKeyboard();
        mode = "list"; current = null;
        try {
            int count = store.count(); if (offset >= count) offset = Math.max(0, ((count - 1) / 50) * 50);
            page("卦例本", "梅花与六爻统一记录，也留下后来的观察");
            LinearLayout summary = card("本机卦例", count + " 条记录 · 收藏优先\n备份包含卦例、八字命盘、出生资料和 AI 对话草稿，不含 API 密钥。备份文件未加密，请妥善保管。");
            LinearLayout actions = new LinearLayout(this);
            Button export = action("导出备份", false, new View.OnClickListener() { @Override public void onClick(View v) { exportPicker(); } });
            Button restore = action("导入备份", false, new View.OnClickListener() { @Override public void onClick(View v) { importPicker(); } });
            actions.addView(export, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1); right.leftMargin = dp(8);
            actions.addView(restore, right); summary.addView(actions);
            List<NotebookRecord> rows = store.listPage(offset, 50);
            if (rows.isEmpty()) card("从一次起卦开始", "起卦结果会自动成为卦例。教学案例也可以保存成自己的学习笔记。");
            for (final NotebookRecord row : rows) {
                boolean liuyao = row.method != null && row.method.startsWith("六爻");
                boolean dayan = row.method != null && row.method.startsWith("大衍");
                String description = (liuyao ? "六爻排盘" : dayan ? "大衍筮法" : hexName(row) + " · 第 " + row.moving + " 爻动") + "\n" + date(row.createdAt)
                    + (row.tags.trim().length() == 0 ? "" : "\n" + row.tags);
                LinearLayout item = card((row.favorite ? "★ " : "") + row.title, description);
                item.addView(action("查看卦例", false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try { current = store.find(row.id); if (current == null) throw new IllegalStateException("记录已被删除。"); showDetail(); }
                        catch (Exception e) { error("无法读取卦例", e); }
                    }
                }));
            }
            if (count > 50) {
                TextView index = text("第 " + (offset / 50 + 1) + " 页，共 " + ((count + 49) / 50) + " 页", 14, false);
                content.addView(index);
                if (offset > 0) content.addView(action("上一页", false, new View.OnClickListener() {
                    @Override public void onClick(View v) { offset = Math.max(0, offset - 50); showList(); }
                }));
                if (offset + 50 < count) content.addView(action("下一页", false, new View.OnClickListener() {
                    @Override public void onClick(View v) { offset += 50; showList(); }
                }));
            }
        } catch (Exception e) { fatal(e); }
    }

    private void showDetail() {
        hideKeyboard();
        if (current == null) { showList(); return; }
        mode = "detail";
        page("卦例详情", current.title);
        final boolean liuyao = current.method != null && current.method.startsWith("六爻");
        final boolean dayan = current.method != null && current.method.startsWith("大衍");
        LinearLayout head = card((current.favorite ? "★ " : "") + current.title,
            (liuyao ? "六爻排盘" : dayan ? "大衍筮法 · 十八变成卦" : hexName(current) + " · 第 " + current.moving + " 爻动") + "\n记录创建：" + date(current.createdAt)
                + "\n更新：" + date(current.updatedAt) + " · 修订 " + current.revision);
        head.addView(action("编辑笔记", true, new View.OnClickListener() { @Override public void onClick(View v) { edit(); } }));
        if (!liuyao && !dayan) head.addView(action("查看本卦、互卦与变卦", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                StudyModules.showNotebookResult(NotebookActivity.this, current.upper, current.lower, current.moving,
                    current.method, current.info, current.id, current.question);
            }
        }));
        if (!liuyao && !dayan) head.addView(action("AI 解卦 · 1000字内（需联网）", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                AiReadingActivity.launch(NotebookActivity.this, current.upper, current.lower, current.moving,
                    current.method, AiReadingStore.notebookReference(current.id), current.question);
            }
        }));
        if (dayan) head.addView(action("查看离线卦解与原典", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] values = dayanValues(current.info);
                if (values == null) { toast("这条旧记录缺少完整六爻数值，无法重建大衍卦解。"); return; }
                StudyModules.showDayanResult(NotebookActivity.this, current.upper, current.lower, values, current.id, current.question);
            }
        }));
        if (dayan) head.addView(action("AI 解卦 · 200字内（需联网）", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] values = dayanValues(current.info);
                if (values == null) { toast("这条旧记录缺少完整六爻数值，无法进行大衍 AI 解读。"); return; }
                AiReadingActivity.launch(NotebookActivity.this, current.upper, current.lower, current.moving,
                    AiReadingContext.dayanMethod(values), AiReadingStore.notebookReference(current.id), current.question);
            }
        }));
        if (liuyao) head.addView(action("六爻 AI 解读与追问（需联网）", true, new View.OnClickListener() {
            @Override public void onClick(View v) { LiuYaoActivity.open(NotebookActivity.this, current.id, ""); }
        }));
        noteCard("所问事项", current.question, "尚未填写所问事项。");
        aiPanel = column(); content.addView(aiPanel); refreshAiPanel();
        noteCard("自己的事前分析", current.analysis, "可以先记下自己的分析，再查看参考解析。");
        noteCard("后续观察与复盘", current.feedback, "之后补充发生了什么、哪些理解需要调整。");
        if (current.tags.trim().length() > 0) noteCard("标签", current.tags, "");
        noteCard("取数依据 · " + current.method, current.info, "未填写取数依据。");
        if (current.sourceCaseId.length() > 0) noteCard("学习案例", caseName(current.sourceCaseId), "");
        content.addView(action("删除这一条卦例", false, new View.OnClickListener() {
            @Override public void onClick(View v) { deleteConfirm(); }
        }));
    }

    private int[] dayanValues(String info) {
        if (info == null) return null;
        int[] values = new int[6];
        for (int i = 0; i < 6; i++) {
            String marker = "第" + (i + 1) + "爻：";
            int at = info.indexOf(marker);
            if (at < 0) return null;
            at += marker.length();
            if (at >= info.length()) return null;
            char value = info.charAt(at);
            if (value < '6' || value > '9') return null;
            values[i] = value - '0';
        }
        return values;
    }

    private void refreshAiPanel() {
        if (!"detail".equals(mode) || current == null || aiPanel == null) return;
        AiReadingStore chats = new AiReadingStore(this);
        try { AiNotebookPanel.render(this, aiPanel, chats.forNotebook(current.id)); }
        catch (Exception e) { AiNotebookPanel.showUnavailable(this, aiPanel); }
        finally { chats.close(); }
    }

    private void edit() {
        mode = "edit"; page("编辑卦例", "先记分析，后记观察；取数和起卦时间保留");
        LinearLayout base = card("这次起卦", hexName(current) + " · 第 " + current.moving + " 爻动\n" + current.method + " · " + date(current.createdAt));
        title = input(base, "标题", current.title, false, 1);
        question = input(content, "所问事项", current.question, true, 3);
        if (store.isQuestionFixed(current.id)) {
            question.setKeyListener(null);
            question.setTextIsSelectable(true);
            content.addView(text("原问题已与 AI 对话绑定并保留。补充内容可写入下方分析、复盘或 AI 追问。", 14, false));
        }
        analysis = input(content, "自己的事前分析", current.analysis, true, 6);
        feedback = input(content, "后续观察与复盘", current.feedback, true, 6);
        tags = input(content, "标签（用空格或逗号分隔）", current.tags, true, 2);
        favorite = new CheckBox(this); favorite.setText("收藏这条卦例"); favorite.setTextColor(JianYiUi.INK);
        favorite.setChecked(current.favorite); favorite.setMinHeight(dp(48)); content.addView(favorite);
        content.addView(action("保存笔记", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    NotebookRecord draft = collect(); BackupCodec.validate(draft);
                    current = store.save(draft); toast("笔记已保存。"); showDetail();
                } catch (Exception e) { error("笔记未保存", e); }
            }
        }));
    }

    private NotebookRecord collect() {
        NotebookRecord row = current.copy(); row.title = title.getText().toString(); row.question = question.getText().toString();
        row.analysis = analysis.getText().toString(); row.feedback = feedback.getText().toString(); row.tags = tags.getText().toString();
        row.favorite = favorite.isChecked(); return row;
    }

    private void fillEditor(NotebookRecord row) {
        title.setText(row.title); question.setText(row.question); analysis.setText(row.analysis);
        feedback.setText(row.feedback); tags.setText(row.tags); favorite.setChecked(row.favorite);
    }

    private void deleteConfirm() {
        final String id = current.id;
        JianYiUi.dialogBuilder(this).setTitle("删除这一条卦例？")
            .setMessage("只删除“" + current.title + "”。删除后无法撤销，可先从卦例列表导出备份。")
            .setNegativeButton("保留", null).setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    try { store.delete(id); toast("已删除这一条卦例。"); showList(); }
                    catch (Exception e) { error("未能删除", e); }
                }
            }).show();
    }

    private void exportPicker() {
        work("准备备份", new Callable<byte[]>() {
            @Override public byte[] call() throws Exception { return store.exportBackup(); }
        }, new Success<byte[]>() {
            @Override public void accept(byte[] ignored) {
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json").putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        .putExtra(Intent.EXTRA_TITLE, "简易卦例-" + fileDate() + ".json");
                    startActivityForResult(intent, EXPORT);
                } catch (Exception e) { error("无法选择备份位置", e); }
            }
        });
    }
    private void importPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "text/plain", "application/octet-stream"});
            startActivityForResult(intent, IMPORT);
        } catch (Exception e) { error("无法选择备份文件", e); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT && request != EXPORT) return;
        if (result != RESULT_OK) { toast(request == IMPORT ? "已取消导入，原卦例保留。" : "已取消导出。"); return; }
        if (data == null || data.getData() == null) { error("文件选择未完成", new IllegalStateException("没有获得文件位置。")); return; }
        final Uri uri = data.getData();
        if (!LocalBackupPolicy.allows(uri.getScheme(), uri.getAuthority())
            || !DocumentsContract.isDocumentUri(this, uri)) {
            error("请选择本机文件", new IllegalArgumentException("请在系统文件管理器中选择手机的下载、文档或存储卡目录。"));
            return;
        }
        if (request == EXPORT) {
            work("写入备份", new Callable<Integer>() {
                @Override public Integer call() throws Exception {
                    byte[] bytes = store.exportBackup();
                    OutputStream stream = getContentResolver().openOutputStream(uri, "w");
                    if (stream == null) throw new IllegalStateException("备份文件无法打开。");
                    try { stream.write(bytes); stream.flush(); }
                    finally { stream.close(); }
                    return Integer.valueOf(bytes.length);
                }
            }, new Success<Integer>() {
                @Override public void accept(Integer bytes) {
                    JianYiUi.dialogBuilder(NotebookActivity.this).setTitle("备份已导出")
                        .setMessage("已写入所选文件，共 " + bytes + " 字节。请保留 JSON 文件，之后可以导入恢复卦例。")
                        .setPositiveButton("完成", null).show();
                }
            });
        } else {
            work("检查备份", new Callable<List<NotebookRecord>>() {
                @Override public List<NotebookRecord> call() throws Exception {
                    InputStream stream = getContentResolver().openInputStream(uri);
                    if (stream == null) throw new IllegalStateException("文件无法打开。");
                    try { return BackupCodec.decode(BackupCodec.readBounded(stream)); }
                    finally { stream.close(); }
                }
            }, new Success<List<NotebookRecord>>() {
                @Override public void accept(final List<NotebookRecord> rows) {
                    try {
                        NotebookStore.ImportPlan plan = store.previewImport(rows);
                        if (rows.isEmpty() && plan.sessionsAdded + plan.sessionsIdentical + plan.sessionsConflicts + plan.baziAdded + plan.baziIdentical + plan.baziConflicts == 0) { toast("备份没有卦例、AI 对话或八字命盘，当前资料未改变。"); return; }
                        JianYiUi.dialogBuilder(NotebookActivity.this).setTitle("导入 " + rows.size() + " 条卦例？")
                            .setMessage("新增 " + plan.added + " 条\n相同记录 " + plan.identical + " 条\n编号相同但内容不同 "
                                + plan.conflicts + " 条\nAI 对话：新增 " + plan.sessionsAdded + " 次，相同 " + plan.sessionsIdentical + " 次，冲突 " + plan.sessionsConflicts
                                + " 次\n八字命盘与对话：新增或补充 " + plan.baziAdded + " 项，相同 " + plan.baziIdentical + " 项，冲突 " + plan.baziConflicts
                                + " 项\n\n已有记录全部保留，不覆盖本机笔记、命盘与对话；冲突记录及其关联对话会跳过。")
                            .setNegativeButton("取消", null).setPositiveButton("导入", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int which) {
                                    work("导入卦例", new Callable<NotebookStore.ImportPlan>() {
                                        @Override public NotebookStore.ImportPlan call() { return store.importRecords(rows); }
                                    }, new Success<NotebookStore.ImportPlan>() {
                                        @Override public void accept(NotebookStore.ImportPlan imported) {
                                            showList(); JianYiUi.dialogBuilder(NotebookActivity.this).setTitle("导入完成")
                                                .setMessage("新增 " + imported.added + " 条，跳过相同记录 " + imported.identical
                                                    + " 条，保留本机冲突记录 " + imported.conflicts + " 条。\nAI 对话新增 " + imported.sessionsAdded + " 次，相同 "
                                                    + imported.sessionsIdentical + " 次，冲突保留本机 " + imported.sessionsConflicts + " 次。\n八字新增或补充 "
                                                    + imported.baziAdded + " 项，相同 " + imported.baziIdentical + " 项，冲突保留本机 " + imported.baziConflicts + " 项。")
                                                .setPositiveButton("完成", null).show();
                                        }
                                    });
                                }
                            }).show();
                    } catch (Exception e) { error("未能预览导入", e); }
                }
            });
        }
    }

    private interface Success<T> { void accept(T result); }
    private <T> void work(String label, final Callable<T> task, final Success<T> success) {
        if (busy) return;
        busy = true; progress = JianYiUi.showProgressDialog(this, label + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                T value = null; Exception failure = null;
                try { value = task.call(); } catch (Exception e) { failure = e; }
                final T done = value; final Exception error = failure;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (destroyed || isFinishing()) return;
                        if (progress != null) { progress.dismiss(); progress = null; }
                        if (error != null) NotebookActivity.this.error("操作未完成", error);
                        else success.accept(done);
                    }
                });
            }
        }, "jianyi-notebook-io").start();
    }

    @Override public void onBackPressed() {
        if (busy) return;
        if ("edit".equals(mode)) {
            final boolean exists = store.find(current.id) != null;
            if (BackupCodec.same(current, collect())) { if (exists) showDetail(); else finish(); return; }
            JianYiUi.dialogBuilder(this).setTitle("笔记尚未保存")
                .setMessage("继续编辑，或放弃这次修改？")
                .setNegativeButton("继续编辑", null).setPositiveButton("放弃修改", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { if (exists) showDetail(); else finish(); }
                }).show();
        } else if ("detail".equals(mode)) showList();
        else finish();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("mode", mode); out.putInt("offset", offset);
        if (current != null) out.putBundle("record", state(current));
        if (current != null && "edit".equals(mode)) out.putBundle("editor", state(collect()));
    }
    @Override protected void onResume() {
        super.onResume();
        if ("detail".equals(mode) && current != null) refreshAiPanel();
    }
    @Override protected void onDestroy() {
        destroyed = true; if (progress != null) { progress.dismiss(); progress = null; } super.onDestroy();
    }
    private static Bundle state(NotebookRecord r) {
        Bundle b = new Bundle(); b.putString("id", r.id); b.putString("title", r.title); b.putString("question", r.question);
        b.putString("analysis", r.analysis); b.putString("feedback", r.feedback); b.putString("tags", r.tags);
        b.putString("method", r.method); b.putString("info", r.info); b.putString("source", r.sourceCaseId);
        b.putInt("upper", r.upper); b.putInt("lower", r.lower); b.putInt("moving", r.moving); b.putInt("revision", r.revision);
        b.putLong("created", r.createdAt); b.putLong("updated", r.updatedAt); b.putBoolean("favorite", r.favorite); return b;
    }
    private static NotebookRecord restore(Bundle b) {
        NotebookRecord r = new NotebookRecord(); r.id = b.getString("id", ""); r.title = b.getString("title", "");
        r.question = b.getString("question", ""); r.analysis = b.getString("analysis", ""); r.feedback = b.getString("feedback", "");
        r.tags = b.getString("tags", ""); r.method = b.getString("method", ""); r.info = b.getString("info", "");
        r.sourceCaseId = b.getString("source", ""); r.upper = b.getInt("upper"); r.lower = b.getInt("lower"); r.moving = b.getInt("moving");
        r.revision = b.getInt("revision", 1); r.createdAt = b.getLong("created"); r.updatedAt = b.getLong("updated"); r.favorite = b.getBoolean("favorite");
        return r;
    }

    private EditText input(LinearLayout parent, String label, String value, boolean multiline, int lines) {
        parent.addView(text(label, 16, true));
        EditText input = new EditText(this); input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES : 0));
        input.setSingleLine(!multiline); input.setMinLines(lines); input.setGravity(Gravity.TOP | Gravity.START);
        input.setContentDescription(label); JianYiUi.styleInput(input);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(6); p.bottomMargin = dp(18);
        parent.addView(input, p); return input;
    }
    private LinearLayout card(String title, String description) {
        LinearLayout card = column(); JianYiUi.surface(card); card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(text(title, 19, true)); card.addView(text(description, 15, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); content.addView(card, p); return card;
    }
    private void noteCard(String label, String value, String empty) {
        LinearLayout card = column(); JianYiUi.surface(card); card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(text(label, 19, true)); final TextView body = text(value.trim().length() == 0 ? empty : value, 16, false);
        body.setTextIsSelectable(true); card.addView(body);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); content.addView(card, p);
    }
    private Button action(String label, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); JianYiUi.skinButton(b, primary); b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); b.setLayoutParams(p); return b;
    }
    private TextView text(String value, int size, boolean heading) {
        TextView t = new TextView(this); t.setText(value); JianYiUi.textStyle(t, size, heading);
        t.setLineSpacing(dp(5), 1); t.setPadding(0, 0, 0, dp(12));
        if (!heading) t.setTextColor(JianYiUi.MUTED); return t;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private int dp(int value) { return JianYiUi.dp(this, value); }
    private static String date(long at) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA); fmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return fmt.format(new Date(at));
    }
    private static String fileDate() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US); fmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return fmt.format(new Date());
    }
    private String hexName(NotebookRecord r) {
        try {
            String code = MeihuaEngine.fromTrigrams(r.upper, r.lower, r.moving).baseCode;
            if (hexNames == null) {
                InputStream stream = getAssets().open("appdata/hexagrams.json"); byte[] data;
                try { data = BackupCodec.readBounded(stream); } finally { stream.close(); }
                JSONObject root = new JSONObject(new String(data, "UTF-8")); JSONArray rows = root.getJSONArray("hexagrams");
                hexNames = new HashMap<String, String>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject entry = rows.getJSONObject(i);
                    hexNames.put(entry.getString("code"), entry.getString("full_name"));
                }
            }
            if (hexNames.containsKey(code)) return hexNames.get(code);
        } catch (Exception ignored) { }
        return "上" + MeihuaEngine.trigramName(r.upper) + "下" + MeihuaEngine.trigramName(r.lower);
    }
    private String caseName(String id) {
        try {
            if (caseNames == null) {
                InputStream stream = getAssets().open("appdata/meihua-learning.json"); byte[] data;
                try { data = BackupCodec.readBounded(stream); } finally { stream.close(); }
                JSONArray rows = new JSONObject(new String(data, "UTF-8")).getJSONArray("cases");
                HashMap<String, String> names = new HashMap<String, String>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject entry = rows.getJSONObject(i);
                    String title = entry.getString("title").trim();
                    if (title.length() > 0) names.put(entry.getString("id"), title);
                }
                caseNames = names;
            }
            String name = caseNames.get(id);
            if (name != null) return name + "\n案例编号：" + id;
        } catch (Exception ignored) { }
        return id;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }
    private void error(String label, Exception e) {
        JianYiUi.dialogBuilder(this).setTitle(label).setMessage(e.getMessage() == null ? "请重试。原卦例仍保留。" : e.getMessage())
            .setPositiveButton("知道了", null).show();
    }
    private void fatal(Exception e) {
        mode = "list"; page("卦例笔记", "记录未能打开"); card("读取遇到问题", e.getMessage() == null ? "请重试。" : e.getMessage());
        content.addView(action("返回", false, new View.OnClickListener() { @Override public void onClick(View v) { finish(); } }));
    }
}
