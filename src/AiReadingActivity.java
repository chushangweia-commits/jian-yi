package com.jianyi.yixue.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opt-in personal DeepSeek conversation, with a fixed locally calculated hexagram. */
public final class AiReadingActivity extends Activity {
    private AiReadingStore store;
    private AiReadingStore.Session session;
    private JSONObject facts;
    private String mode="chat";
    private LinearLayout frame, content;
    private ScrollView scroll;
    private EditText input;
    private TextView status, counter;
    private Button send;
    private int generation, historyOffset;
    private boolean busy;
    private DeepSeekClient.Cancellation cancellation;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    public static void launch(Activity activity,int upper,int lower,int moving,String method,String reference,String question){
        activity.startActivity(new Intent(activity,AiReadingActivity.class).putExtra("upper",upper).putExtra("lower",lower).putExtra("moving",moving).putExtra("method",method).putExtra("reference",reference).putExtra("question",question));
    }
    public static void settings(Activity activity){activity.startActivity(new Intent(activity,AiReadingActivity.class).putExtra("settings",true));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=new AiReadingStore(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try{
            String restored=saved==null?null:saved.getString("session_id");
            if(restored!=null)session=store.get(restored);
            if(session==null&&!getIntent().getBooleanExtra("settings",false)){
                String reference=extra("reference");session=store.latest(reference);
                int upper=getIntent().getIntExtra("upper",0),lower=getIntent().getIntExtra("lower",0),moving=getIntent().getIntExtra("moving",0);
                if(session!=null&&(session.upper!=upper||session.lower!=lower||session.moving!=moving||!session.method.equals(extra("method"))))session=null;
                // A legacy notebook may have been edited after its AI answer was saved.
                // Keep its old conversation, but never attach its answer to a different question.
                if(session!=null&&reference.startsWith("note:")){
                    String original=session.subject;
                    if(original.length()==0&&session.history.length()>0){JSONObject first=session.history.optJSONObject(0);original=first==null?"":first.optString("content","");}
                    if(original.length()==0&&session.history.length()==0)original=session.draft;
                    if(original.length()>0&&!original.trim().equals(extra("question").trim()))session=null;
                }
                if(session==null){String original=extra("question");String initial=AiReadingContext.charCount(original)<=500?original:"";session=store.create(reference,upper,lower,moving,extra("method"),initial,"",reference.startsWith("note:")?original:"");}
            }
            if(session!=null&&session.method.startsWith("六爻")){
                if(!session.reference.startsWith("note:"))throw new IllegalArgumentException("六爻对话缺少对应卦例");
                LiuYaoActivity.open(this,session.reference.substring(5),session.id);finish();return;
            }
            if(session!=null)facts=AiReadingContext.facts(this,session.upper,session.lower,session.moving,session.method);
            if(getIntent().getBooleanExtra("settings",false)||(saved!=null&&"settings".equals(saved.getString("mode"))))showSettings();else showChat();
        }catch(Exception e){page("AI 解卦");content.addView(text("本次卦例未能载入，请返回重新打开。",16,false));}
    }
    private String extra(String name){String s=getIntent().getStringExtra(name);return s==null?"":s;}
    private String limit(String s,int max){return s.codePointCount(0,s.length())<=max?s:s.substring(0,s.offsetByCodePoints(0,max));}
    private int dp(float n){return JianYiUi.dp(this,n);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,boolean heading){TextView v=new TextView(this);v.setText(value);JianYiUi.textStyle(v,size,heading);v.setLineSpacing(dp(4),1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);v.setLayoutParams(p);v.setTextIsSelectable(true);return v;}
    private Button button(String label,boolean primary,View.OnClickListener click){Button b=new Button(this);b.setText(label);JianYiUi.skinButton(b,primary);b.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);b.setLayoutParams(p);return b;}
    private LinearLayout card(String title){LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(10));JianYiUi.surface(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);c.setLayoutParams(p);if(title!=null)c.addView(text(title,18,true));return c;}
    private void page(String title){
        frame=column();JianYiUi.applyScenic(frame,JianYiUi.SCENE_DIVINATION);frame.setFitsSystemWindows(true);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(6),dp(16),dp(6));
        Button back=button("‹ 返回",false,new View.OnClickListener(){public void onClick(View v){onBackPressed();}});back.setContentDescription("AI页面返回");bar.addView(back,new LinearLayout.LayoutParams(dp(94),-2));
        TextView label=text(title,20,true);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(dp(14),0,0,0);bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));frame.addView(bar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);content=column();content.setPadding(dp(18),dp(12),dp(18),dp(18));scroll.addView(content);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(frame);JianYiUi.applySystemBars(this);
    }
    private void showChat(){
        if(session==null||facts==null){showSettings();return;}mode="chat";page("AI 解卦");
        final int answerLimit="dayan".equals(facts.optString("system"))?200:1000;
        LinearLayout head=card(null);head.addView(text("解卦体系 · "+AiReadingContext.systemName(this,facts),14,true));head.addView(text(AiReadingContext.summary(facts),17,true));head.addView(text(AiReadingContext.basisSummary(this,facts),14,false));head.addView(text("DeepSeek · 每条回答不超过"+answerLimit+"字 · 追问沿用本卦 · 本机检查格式与体系关键词",13,false));
        if(session.reference.startsWith("note:")&&session.subject.length()>0){head.addView(text("绑定的卦例原问题：\n"+session.subject,14,true));head.addView(text("下方可补充或改写本次发送的问题；回答会同时记录实际提问和卦例原问题。",13,false));}
        LinearLayout actions=new LinearLayout(this);Button settings=button("API 设置",false,new View.OnClickListener(){public void onClick(View v){keepDraft();showSettings();}});Button history=button("本机对话",false,new View.OnClickListener(){public void onClick(View v){keepDraft();historyOffset=0;showHistory();}});
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(6);actions.addView(settings,left);actions.addView(history,new LinearLayout.LayoutParams(0,-2,1));head.addView(actions);
        if (session.reference.startsWith("note:") && session.reference.length() > 5) {
            head.addView(button("查看对应卦例详情",false,new View.OnClickListener(){public void onClick(View v){
                keepDraft(); cancelRequest("本次请求已停止，输入保留。");
                NotebookActivity.open(AiReadingActivity.this,session.reference.substring(5));
            }}));
        }
        content.addView(head);
        final boolean mismatch=questionMismatch();
        if(mismatch){LinearLayout warning=card("这次对话对应旧问题");warning.addView(text("当前卦例的问题与本次对话的原问题不同。旧对话完整保留，可查看；如需解答当前问题，请新建对话。",14,true));warning.addView(button("为当前问题新建对话",true,new View.OnClickListener(){public void onClick(View v){newSession();}}));content.addView(warning);}
        if(session.history.length()==0){LinearLayout intro=card("先说清楚，你想问什么");intro.addView(text("写明事项和想了解的范围，例如：这周推进合作，怎样安排更稳妥？",16,false));if(AiReadingContext.charCount(session.subject)>500)intro.addView(text("原卦例问题超过500字，原文已保留。请自行概括本次发给 AI 的问题，应用不会自动截断。",14,true));intro.addView(text("点击发送后，问题、当前卦象、精选资料及本次问答将发给 DeepSeek，按你的 API 账号计费。其他笔记不发送。若回答不符合格式，应用最多自动重试一次，可能产生额外费用。",13,false));intro.addView(button("试填：学习安排",false,new View.OnClickListener(){public void onClick(View v){input.setText("我想七天内完成三节梅花易数入门课，怎样安排更稳妥？");input.requestFocus();}}));content.addView(intro);}else{
            for(int i=0;i<session.history.length();i++){JSONObject message=session.history.optJSONObject(i);boolean user=i%2==0;LinearLayout bubble=card(user?"你问":"简易 · AI 解读");String body=message.optString("content","");bubble.addView(text(body,16,false));if(!user){bubble.addView(text(AiReadingContext.charCount(body)+" / "+answerLimit+" 字",12,false));bubble.addView(text(AiReadingContext.verificationText(this,facts,body),12,true));addCitationLinks(bubble);}content.addView(bubble);}
        }
        if(session.history.length()>0&&session.history.length()<16)content.addView(button("追问：具体怎样做？",false,new View.OnClickListener(){public void onClick(View v){input.setText("能结合这个卦，说明我下一步具体怎样做吗？");input.requestFocus();}}));
        status=text(AiSettings.hasKey(this)?(session.method.startsWith("大衍筮法:")?"回答依据大衍筮法与《周易》经文，不使用梅花体用。":"回答用于梅花易数研习，卦意仍需结合现实核实。"):"尚未配置 API Key，请先打开 API 设置。",13,false);content.addView(status);
        LinearLayout composer=column();composer.setPadding(dp(18),dp(10),dp(18),dp(12));composer.setBackgroundColor(JianYiUi.WHITE);
        input=new EditText(this);JianYiUi.styleInput(input);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMinLines(2);input.setMaxLines(4);input.setGravity(Gravity.TOP);input.setHint(session.history.length()==0?"输入所问事项（最多500字）":"继续追问这一个卦（最多500字）");input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});input.setText(session.draft);composer.addView(input,new LinearLayout.LayoutParams(-1,-2));
        counter=text("",12,false);composer.addView(counter);input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){updateCount();}public void afterTextChanged(Editable e){}});updateCount();
        send=button("发送并解卦",true,new View.OnClickListener(){public void onClick(View v){if(busy)cancelRequest("本次请求已停止，输入保留。服务器可能已产生的用量仍按平台记录计费。");else send();}});composer.addView(send);frame.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        if(mismatch){send.setEnabled(false);send.setText("请先为当前问题新建对话");}
        if(session.history.length()>=16){input.setEnabled(false);send.setText("本次已完成8轮问答");send.setEnabled(false);content.addView(button("同卦新建对话",false,new View.OnClickListener(){public void onClick(View v){newSession();}}));}
    }
    private boolean questionMismatch(){try{if(session==null||!session.reference.startsWith("note:"))return false;NotebookRecord note=NotebookStore.get(this).find(session.reference.substring(5));if(note==null)return true;String original=session.subject;if(original.length()==0&&session.history.length()>0){JSONObject first=session.history.optJSONObject(0);original=first==null?"":first.optString("content","");}return original.length()>0&&!original.trim().equals(note.question.trim());}catch(Exception e){return true;}}
    private void addCitationLinks(LinearLayout parent){
        final boolean dayan=facts!=null&&"dayan".equals(facts.optString("system"));
        final LinearLayout references=column();references.setVisibility(View.GONE);
        references.addView(text("引用入口 · 对照应用内离线原典",12,true));
        final Button toggle=button("查看引用依据",false,null);
        toggle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){boolean expand=references.getVisibility()!=View.VISIBLE;references.setVisibility(expand?View.VISIBLE:View.GONE);toggle.setText(expand?"收起引用依据":"查看引用依据");}});
        parent.addView(toggle);parent.addView(references);
        final JSONObject base=facts==null?null:facts.optJSONObject("base"),mutual=facts==null?null:facts.optJSONObject("mutual"),changed=facts==null?null:facts.optJSONObject("changed");
        if(base!=null)references.addView(button("本卦原文 · "+base.optString("name"),false,new View.OnClickListener(){public void onClick(View v){StudyModules.showClassicByCode(AiReadingActivity.this,base.optString("code"));}}));
        if(!dayan&&mutual!=null&&!mutual.optString("code").equals(base==null?"":base.optString("code")))references.addView(button("互卦原文 · "+mutual.optString("name"),false,new View.OnClickListener(){public void onClick(View v){StudyModules.showClassicByCode(AiReadingActivity.this,mutual.optString("code"));}}));
        if(changed!=null&&!changed.optString("code").equals(base==null?"":base.optString("code")))references.addView(button("变卦原文 · "+changed.optString("name"),false,new View.OnClickListener(){public void onClick(View v){StudyModules.showClassicByCode(AiReadingActivity.this,changed.optString("code"));}}));
        references.addView(button(dayan?"查看《周易》研习导读":"查看《周易》《说卦》《系辞》",false,new View.OnClickListener(){public void onClick(View v){MeihuaReferenceActivity.openTopic(AiReadingActivity.this,"P04");}}));
        if(!dayan)references.addView(button("查看《梅花易数》体用与分占",false,new View.OnClickListener(){public void onClick(View v){MeihuaReferenceActivity.openTopic(AiReadingActivity.this,"C04");}}));
    }
    private void updateCount(){if(counter==null||input==null)return;int count=AiReadingContext.charCount(input.getText().toString());counter.setText(count+" / 500 字");counter.setTextColor(count>500?0xffa33c35:JianYiUi.MUTED);}
    private void keepDraft(){if(session==null||!"chat".equals(mode)||input==null)return;String s=input.getText().toString();if(AiReadingContext.charCount(s)<=500){session.draft=s;try{store.save(session);}catch(Exception ignored){}}}
    private void send(){
        if(session==null||busy)return;if(session.history.length()>=16){status.setText("本次已完成8轮，请新建对话。");return;}final String question=input.getText().toString().trim();
        if(question.length()==0||AiReadingContext.charCount(question)>500){status.setText("请填写1至500字的问题。");return;}
        if(!AiSettings.hasKey(this)){keepDraft();showSettings();return;}
        final JSONArray messages;final AiAnswerPolicy.Policy policy;
        try{messages=AiReadingContext.messages(this,facts,question,session.history);policy=AiReadingContext.answerPolicy(this,facts,session.history.length()==0);}catch(Exception e){status.setText("本次卦象或对话未能整理，请返回重新打开。");return;}
        session.draft=question;try{store.save(session);}catch(Exception e){status.setText("本机空间不足或对话未能保存，未发送问题。");return;}
        hideKeyboard();busy=true;input.setEnabled(false);send.setText("停止本次解读");status.setText("正在结合问题解读卦象…");final AiReadingStore.Session requestSession=session;final int request=++generation;final DeepSeekClient.Cancellation token=new DeepSeekClient.Cancellation();cancellation=token;
        worker.submit(new Runnable(){public void run(){try{
            String key=AiSettings.readKey(getApplicationContext());final String answer=DeepSeekClient.answer(key,messages,token,policy);
            runOnUiThread(new Runnable(){public void run(){if(request!=generation||session!=requestSession||isFinishing()||token.isCancelled())return;busy=false;cancellation=null;
                try{JSONArray updated=new JSONArray(session.history.toString());updated.put(new JSONObject().put("role","user").put("content",question));updated.put(new JSONObject().put("role","assistant").put("content",answer));JSONArray previous=session.history;String draft=session.draft;session.history=updated;session.draft="";try{store.save(session);}catch(Exception e){session.history=previous;session.draft=draft;showChat();LinearLayout unsaved=card("AI 解读 · 尚未保存");unsaved.addView(text(answer,16,false));content.addView(unsaved);status.setText("回答已收到，但未能保存，请复制后再重试。");return;}showChat();scroll.post(new Runnable(){public void run(){scroll.fullScroll(View.FOCUS_DOWN);}});}catch(Exception e){showChat();status.setText("回答未能保存，原问题仍保留。");}
            }});
        }catch(final Exception e){runOnUiThread(new Runnable(){public void run(){if(request!=generation||session!=requestSession||isFinishing())return;busy=false;cancellation=null;showChat();status.setText(e instanceof DeepSeekClient.ApiException?e.getMessage():"解读未完成，请检查网络、API Key 或余额后重试。");}});}}});
    }
    private void cancelRequest(String message){if(!busy)return;generation++;busy=false;if(cancellation!=null)cancellation.cancel();cancellation=null;if(send!=null){send.setText("发送并解卦");send.setEnabled(true);}if(input!=null)input.setEnabled(true);if(status!=null)status.setText(message);}
    private void showSettings(){
        if(busy)cancelRequest("本次请求已停止。");mode="settings";page("AI 设置");
        LinearLayout c=card("DeepSeek · 个人测试");c.addView(text("仅点击 AI 解卦的发送按钮时联网，原有学习与起卦功能可离线使用。",16,false));c.addView(text("密钥保存在本机 Android 密钥库保护的存储中，不加入卦例备份。联网解读按你的 DeepSeek 账号计费。",13,false));c.addView(text(AiSettings.hasKey(this)?"当前：已配置密钥（不显示原文）":"当前：尚未配置密钥",15,true));
        final EditText key=new EditText(this);JianYiUi.styleInput(key);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setHint(AiSettings.hasKey(this)?"填写新 API Key 可替换现有密钥":"粘贴你的 DeepSeek API Key");key.setSaveEnabled(false);key.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});if(Build.VERSION.SDK_INT>=26)key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);c.addView(key);
        final TextView notice=text("填写密钥后点保存；保存不会自动发起 API 请求。",13,false);c.addView(notice);
        c.addView(button("保存密钥",true,new View.OnClickListener(){public void onClick(View v){try{AiSettings.saveKey(AiReadingActivity.this,key.getText().toString());key.setText("");hideKeyboard();showSettings();}catch(Exception e){notice.setText("密钥未能保存，请检查内容；需要 Android 7 或更高版本。");}}}));
        c.addView(button("清除本机密钥",false,new View.OnClickListener(){public void onClick(View v){JianYiUi.showDialog(AiReadingActivity.this,JianYiUi.dialogBuilder(AiReadingActivity.this).setTitle("清除本机密钥？").setMessage("会移除已保存密钥。已有卦例和对话保留；之后可以填写新密钥。").setNegativeButton("取消",null).setPositiveButton("清除",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){try{AiSettings.clearKey(AiReadingActivity.this);showSettings();}catch(Exception e){notice.setText("密钥未能清除，请重试。");}}}).create());}}));
        c.addView(text("安装包不包含任何 API Key。更换或清除只影响本机使用；撤销旧密钥需在 DeepSeek 控制台操作。",13,false));content.addView(c);
        LinearLayout privacy=card("发送范围与保存");privacy.addView(text("发送当前问题、本次固定排盘、对应体系的经文或规则，以及本次问答。其他卦例、复盘与标签不发送。完整问答会保存在本机，并随卦例备份一同导出和恢复；API Key 始终不加入备份。",14,false));content.addView(privacy);
        content.addView(button("查看本机对话",false,new View.OnClickListener(){public void onClick(View v){historyOffset=0;showHistory();}}));
        if(session!=null)content.addView(button("返回本次解卦",false,new View.OnClickListener(){public void onClick(View v){showChat();}}));
    }
    private void showHistory() {
        cancelRequest("本次请求已停止，输入保留。"); mode="history"; page("本机 AI 对话");
        try {
            List<AiReadingStore.Session> rows=store.list(historyOffset);
            if(rows.isEmpty())content.addView(text("还没有保存的对话。",16,false));
            for(final AiReadingStore.Session s:rows) {
                final boolean liuyao=s.method.startsWith("六爻");
                String title;
                if(liuyao)title="六爻纳甲 · "+s.subject;
                else title=AiReadingContext.summary(AiReadingContext.facts(this,s.upper,s.lower,s.moving,s.method));
                LinearLayout c=card(title);
                String q=s.history.length()>0?s.history.getJSONObject(0).getString("content"):s.draft;
                c.addView(text(q.length()==0?"尚未发送问题":q,15,false));
                c.addView(text(DateFormat.getDateTimeInstance().format(new java.util.Date(s.updatedAt))+" · "+(s.history.length()/2)+" 轮",12,false));
                c.addView(button("继续这次对话",false,new View.OnClickListener(){public void onClick(View v){
                    try {
                        if(liuyao) {
                            if(!s.reference.startsWith("note:"))throw new IllegalArgumentException();
                            LiuYaoActivity.open(AiReadingActivity.this,s.reference.substring(5),s.id);
                        } else {
                            session=store.get(s.id);
                            facts=AiReadingContext.facts(AiReadingActivity.this,session.upper,session.lower,session.moving,session.method);
                            showChat();
                        }
                    } catch(Exception e){android.widget.Toast.makeText(AiReadingActivity.this,"对话暂未能打开，请从对应卦例重试。",android.widget.Toast.LENGTH_LONG).show();}
                }}));
                c.addView(button("删除此对话",false,new View.OnClickListener(){public void onClick(View v){
                    JianYiUi.showDialog(AiReadingActivity.this,JianYiUi.dialogBuilder(AiReadingActivity.this)
                        .setTitle("删除这次 AI 对话？").setMessage("只删除本机这次 AI 问答，卦例笔记保留。此操作无法撤回。")
                        .setNegativeButton("取消",null).setPositiveButton("删除",new android.content.DialogInterface.OnClickListener(){
                            public void onClick(android.content.DialogInterface d,int which){try{store.delete(s.id);if(session!=null&&session.id.equals(s.id))session=null;showHistory();}catch(Exception e){android.widget.Toast.makeText(AiReadingActivity.this,"对话未能删除，请稍后重试。",android.widget.Toast.LENGTH_SHORT).show();}}
                        }).create());
                }})); content.addView(c);
            }
            if(historyOffset>0)content.addView(button("上一页",false,new View.OnClickListener(){public void onClick(View v){historyOffset=Math.max(0,historyOffset-20);showHistory();}}));
            if(rows.size()==20)content.addView(button("下一页",false,new View.OnClickListener(){public void onClick(View v){historyOffset+=20;showHistory();}}));
        }catch(Exception e){content.addView(text("本机对话未能载入，请返回重试。",16,false));}
    }
    private void newSession(){cancelRequest("本次请求已停止。");try{String subject=session.subject;if(session.reference.startsWith("note:")){NotebookRecord note=NotebookStore.get(this).find(session.reference.substring(5));if(note!=null)subject=note.question;}session=store.create(session.reference,session.upper,session.lower,session.moving,session.method,"","",subject);facts=AiReadingContext.facts(this,session.upper,session.lower,session.moving,session.method);showChat();}catch(Exception e){status.setText("未能新建对话，请稍后重试。");}}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    @Override public void onBackPressed(){if(busy)cancelRequest("本次请求已停止。");keepDraft();if(!"chat".equals(mode)&&session!=null){showChat();return;}super.onBackPressed();}
    @Override public void onSaveInstanceState(Bundle out){keepDraft();if(session!=null)out.putString("session_id",session.id);out.putString("mode",mode);super.onSaveInstanceState(out);}
    @Override protected void onStop(){keepDraft();cancelRequest("已停止后台请求，输入保留。");super.onStop();}
    @Override protected void onDestroy(){cancelRequest("");worker.shutdownNow();store.close();super.onDestroy();}
}
