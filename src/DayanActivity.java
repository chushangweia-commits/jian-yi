package com.jianyi.yixue.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Independent yarrow-stalk method: 49 stalks, three changes per line, six lines bottom-up. */
public final class DayanActivity extends Activity {
    private final SecureRandom random=new SecureRandom(); private final Handler handler=new Handler();
    private final int[] values=new int[6]; private final ArrayList<String> log=new ArrayList<String>();
    private int lineIndex,changeIndex,remaining=49; private boolean busy,auto,skipping,destroyed;
    private String savedRecordId=java.util.UUID.randomUUID().toString();
    private EditText question; private LinearLayout lines,result; private TextView narration,progress; private Button next,autoButton,skipButton; private StalkView visual;
    @Override protected void onCreate(Bundle b){super.onCreate(b);JianYiUi.applySystemBars(this);if(b!=null){int[] restored=b.getIntArray("dayan_values");if(restored!=null&&restored.length==6)System.arraycopy(restored,0,values,0,6);lineIndex=b.getInt("dayan_line",0);changeIndex=b.getInt("dayan_change",0);remaining=b.getInt("dayan_remaining",49);ArrayList<String> savedLog=b.getStringArrayList("dayan_log");if(savedLog!=null)log.addAll(savedLog);savedRecordId=b.getString("dayan_note",savedRecordId);}build();if(b!=null)restoreProgress(b);}
    @Override protected void onSaveInstanceState(Bundle out){out.putIntArray("dayan_values",values.clone());out.putInt("dayan_line",lineIndex);out.putInt("dayan_change",changeIndex);out.putInt("dayan_remaining",remaining);out.putStringArrayList("dayan_log",new ArrayList<String>(log));out.putString("dayan_question",question.getText().toString());out.putString("dayan_note",savedRecordId);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){destroyed=true;auto=false;handler.removeCallbacksAndMessages(null);if(visual!=null)visual.cancelAnimation();super.onDestroy();}
    private void build(){LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);JianYiUi.applyScenic(shell,JianYiUi.SCENE_DIVINATION);ScrollView scroll=new ScrollView(this);LinearLayout root=column();root.setPadding(dp(16),dp(8),dp(16),dp(30));scroll.addView(root);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);
        root.addView(JianYiUi.hero(this,"大衍筮法","四十九策 · 三变成爻 · 十八变成卦"));root.addView(button("返回起卦中心",false,new View.OnClickListener(){public void onClick(View v){finish();}}));
        LinearLayout ask=card();ask.addView(text("静心定问 · 一事一问",16,JianYiUi.GOLD));ask.addView(title("01 定问"));ask.addView(text("先把所问之事说清楚，最多500字。大衍筮法独立成卦，不使用梅花取数或体用规则。",14,JianYiUi.MUTED));question=input("请输入问题（必填，最多500字）");question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});ask.addView(question);root.addView(ask);
        LinearLayout teaching=card();teaching.addView(title("02 起卦 · 筮法演示"));teaching.addView(text("大衍之数五十，其用四十有九。按“下一变”观察分二、挂一、揲四、归奇，也可自动完成。",16,JianYiUi.INK));progress=text("准备开始 · 初爻 · 第 1/3 变",14,JianYiUi.MUTED);teaching.addView(progress);visual=new StalkView(this);teaching.addView(visual,new LinearLayout.LayoutParams(-1,dp(300)));narration=text("每三变成一爻，六爻由下至上。",15,JianYiUi.INK);teaching.addView(narration);root.addView(teaching);
        lines=card();lines.addView(title("03 排盘 · 六爻自下而上"));root.addView(lines);refreshLines();
        next=button("下一变",true,new View.OnClickListener(){public void onClick(View v){performChange();}});root.addView(next);autoButton=button("自动演示",false,new View.OnClickListener(){public void onClick(View v){if(!requireQuestion())return;if(auto){auto=false;handler.removeCallbacksAndMessages(null);autoButton.setText("继续自动演示");if(!busy)next.setEnabled(true);}else{auto=true;autoButton.setText("暂停自动演示");if(!busy)performChange();}}});root.addView(autoButton);skipButton=button("跳过动画，完成排盘",false,new View.OnClickListener(){public void onClick(View v){if(!requireQuestion())return;auto=false;skipping=true;handler.removeCallbacksAndMessages(null);autoButton.setEnabled(false);skipButton.setEnabled(false);if(busy)visual.finishNow();else performChange();}});root.addView(skipButton);result=column();root.addView(result);
    }
    private boolean requireQuestion(){String value=question.getText().toString().trim();if(value.length()==0){question.setError("请先填写问题");question.requestFocus();return false;}if(value.codePointCount(0,value.length())>500){question.setError("问题最多500字，请缩短后再起卦");question.requestFocus();return false;}return true;}
    private boolean animationEnabled(){return getSharedPreferences("guest_visual_settings_v1",MODE_PRIVATE).getBoolean("animations_enabled",true)&&(android.os.Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled());}
    private void restoreProgress(Bundle state){question.setText(state.getString("dayan_question",""));if(!log.isEmpty()){question.setEnabled(false);narration.setText("已恢复上次进度，可继续起卦。\n"+log.get(log.size()-1));}refreshLines();if(lineIndex>=6){finishHexagram();return;}progress.setText("已完成 "+lineIndex+" 爻 · 当前第 "+(changeIndex+1)+" / 3 变");auto=false;busy=false;next.setEnabled(true);autoButton.setText("继续自动演示");}
    private void performChange(){if(busy||lineIndex>=6||!requireQuestion())return;if(lineIndex==0&&changeIndex==0)question.setEnabled(false);busy=true;next.setEnabled(false);int before=remaining,left=1+random.nextInt(before-2),right=before-left;int rightAfter=right-1;int lr=left%4==0?4:left%4,rr=rightAfter%4==0?4:rightAfter%4,removed=1+lr+rr,newRemaining=before-removed;changeIndex++;String detail="第"+(lineIndex+1)+"爻 · 第"+changeIndex+"变："+before+"策分为"+left+"与"+right+"；右取一策，左右揲四余"+lr+"、"+rr+"；归奇"+removed+"策，余"+newRemaining+"策。";narration.setText("分二 → 挂一 → 揲四 → 归奇\n"+detail);log.add(detail);progress.setText("第 "+(lineIndex+1)+" 爻 · 第 "+changeIndex+" / 3 变");remaining=newRemaining;if(changeIndex==3){values[lineIndex]=remaining/4;lineIndex++;changeIndex=0;remaining=49;refreshLines();}
        final Runnable complete=new Runnable(){public void run(){if(destroyed)return;busy=false;next.setEnabled(lineIndex<6&&!auto&&!skipping);if(lineIndex>=6){auto=false;skipping=false;autoButton.setEnabled(false);skipButton.setEnabled(false);finishHexagram();}else if(skipping)performChange();else if(auto){if(animationEnabled())handler.postDelayed(new Runnable(){public void run(){if(auto)performChange();}},650);else performChange();}else next.setEnabled(true);}};
        if(skipping||!animationEnabled()){complete.run();return;}visual.animateState(before,left,right,lr,rr,removed,newRemaining,complete);
    }
    private void refreshLines(){while(lines.getChildCount()>1)lines.removeViewAt(1);String[] names={"初爻","二爻","三爻","四爻","五爻","上爻"};for(int i=5;i>=0;i--){if(i>=lineIndex){lines.addView(text(names[i]+"　· · ·",16,JianYiUi.MUTED));continue;}int v=values[i];boolean yang=v==7||v==9;String shape=yang?"━━━━━━":"━━　━━";String kind=v==6?"老阴 · 动":v==7?"少阳 · 静":v==8?"少阴 · 静":"老阳 · 动";lines.addView(text(names[i]+"　"+shape+"　"+kind+"（"+v+"）",16,(v==6||v==9)?JianYiUi.ACCENT:JianYiUi.INK));}}
    private void finishHexagram(){
        int[] baseBits=new int[6],changedBits=new int[6];int firstMoving=0,movingCount=0;
        StringBuilder info=new StringBuilder("大衍筮法 · 十八变\n");
        for(int i=0;i<6;i++){baseBits[i]=(values[i]==7||values[i]==9)?1:0;changedBits[i]=baseBits[i];if(values[i]==6||values[i]==9){changedBits[i]^=1;movingCount++;if(firstMoving==0)firstMoving=i+1;}info.append(lineName(i)).append("：").append(values[i]).append(values[i]==6||values[i]==9?"（动）":"（静）").append('\n');}
        info.append("\n十八变记录\n");for(String row:log)info.append(row).append('\n');
        final int lower=MeihuaEngine.trigramId(java.util.Arrays.copyOfRange(baseBits,0,3)),upper=MeihuaEngine.trigramId(java.util.Arrays.copyOfRange(baseBits,3,6));
        final int moving=firstMoving==0?1:firstMoving;final String baseCode=code(baseBits),changedCode=code(changedBits);
        final ClassicInfo baseClassic=classicForCode(baseCode),changedClassic=classicForCode(changedCode);
        final String baseName=baseClassic==null?("上"+MeihuaEngine.trigramName(upper)+"下"+MeihuaEngine.trigramName(lower)):baseClassic.name;
        final String changedName=changedClassic==null?"变卦":changedClassic.name;
        final String questionText=question.getText().toString().trim();
        next.setVisibility(View.GONE);autoButton.setVisibility(View.GONE);skipButton.setVisibility(View.GONE);progress.setText("十八变完成 · 六爻已定");narration.setText("本次排盘已完成。六爻值、实际动爻和核心经文见下方。 ");
        result.removeAllViews();LinearLayout done=card();done.addView(title("04 解读"));done.addView(text("所问事项\n"+questionText,16,JianYiUi.INK));
        done.addView(text("本卦 · "+baseName,23,JianYiUi.ACCENT));done.addView(text(hexagramLines(baseBits),16,JianYiUi.INK));
        done.addView(text("实际动爻 · "+movingText(),18,movingCount==0?JianYiUi.MUTED:JianYiUi.ACCENT));
        done.addView(text("变卦 · "+changedName,23,JianYiUi.INK));done.addView(text(hexagramLines(changedBits),16,JianYiUi.INK));
        done.addView(text("核心经文",20,JianYiUi.INK));
        done.addView(text(coreReading(baseClassic,changedClassic,movingCount),16,JianYiUi.INK));result.addView(done);
        NotebookRecord saved=null;
        try{saved=NotebookStore.get(this).find(savedRecordId);if(saved==null){NotebookRecord note=NotebookStore.draft("大衍筮法",info.toString().trim(),upper,lower,moving,"");note.id=savedRecordId;note.question=questionText;note.title="大衍筮法 · "+baseName; saved=NotebookStore.get(this).save(note);}}catch(Exception ignored){}
        final NotebookRecord savedNote=saved;final String details=info.toString().trim();
        result.addView(text(savedNote==null?"排盘已完成 · 卦例保存失败":"已自动保存至卦例本",14,savedNote==null?JianYiUi.ACCENT:JianYiUi.MUTED));
        result.addView(button("离线卦解",true,new View.OnClickListener(){public void onClick(View v){showOfflineReading(questionText,baseClassic,changedClassic);}}));
        if(savedNote!=null)result.addView(button("AI 解卦（需联网）",false,new View.OnClickListener(){public void onClick(View v){AiReadingActivity.launch(DayanActivity.this,upper,lower,moving,AiReadingContext.dayanMethod(values),AiReadingStore.notebookReference(savedNote.id),questionText);}}));
        result.addView(button("查看详细依据",false,new View.OnClickListener(){public void onClick(View v){showDetailedBasis(baseName,changedName,details);}}));
        if(savedNote!=null)result.addView(button("查看卦例",false,new View.OnClickListener(){public void onClick(View v){NotebookActivity.open(DayanActivity.this,savedNote.id);}}));
        Toast.makeText(this,savedNote==null?"成卦完成，保存失败":"已自动保存至卦例本",Toast.LENGTH_SHORT).show();
    }
    private String movingText(){StringBuilder s=new StringBuilder();for(int i=0;i<6;i++)if(values[i]==6||values[i]==9){if(s.length()>0)s.append("、");s.append(lineName(i)).append(values[i]==6?"（老阴）":"（老阳）");}return s.length()==0?"无":s.toString();}
    private String lineName(int index){String[] names={"初爻","二爻","三爻","四爻","五爻","上爻"};return index>=0&&index<6?names[index]:"第"+(index+1)+"爻";}
    private String code(int[] bits){StringBuilder value=new StringBuilder(6);for(int bit:bits)value.append(bit);return value.toString();}
    private String hexagramLines(int[] bits){StringBuilder out=new StringBuilder();for(int i=5;i>=0;i--){out.append(lineName(i)).append("　").append(bits[i]==1?"━━━━━━":"━━　━━");if(i>0)out.append('\n');}return out.toString();}
    private String coreReading(ClassicInfo base,ClassicInfo changed,int movingCount){
        if(base==null)return "经文未能载入。排盘结果仍已保留，可从“离线卦解”重试。";
        StringBuilder out=new StringBuilder();out.append("本卦卦辞 · ").append(base.judgment);
        if(movingCount==0){out.append("\n\n无动爻，本卦不变；本次以本卦卦辞为主。");return out.toString();}
        for(int i=0;i<6;i++)if(values[i]==6||values[i]==9){JSONObject line=base.line(i+1);out.append("\n\n实际动爻 · ");if(line==null){out.append(lineName(i)).append("经文暂未载入");continue;}out.append(line.optString("label",lineName(i))).append("：").append(line.optString("text",""));String xiang=line.optString("xiang","").trim();if(xiang.length()>0)out.append("\n《象》").append(xiang);}
        if(changed!=null)out.append("\n\n变卦卦辞 · ").append(changed.judgment);
        out.append("\n\n阅读顺序：本卦卦辞 → 全部实际动爻 → 变卦卦辞。");return out.toString();
    }
    private void showDetailedBasis(String baseName,String changedName,String records){
        LinearLayout content=column();content.setPadding(dp(16),dp(8),dp(16),dp(18));
        LinearLayout method=card();method.addView(title("筮法依据"));method.addView(text("大衍之数五十，其用四十有九。每一变依次分二、挂一、揲四、归奇；三变成一爻，十八变成六爻。六为老阴、七为少阳、八为少阴、九为老阳，六与九为实际动爻。",16,JianYiUi.INK));content.addView(method);
        LinearLayout reading=card();reading.addView(title("本次取用"));reading.addView(text("本卦："+baseName+"\n实际动爻："+movingText()+"\n变卦："+changedName+"\n\n本页只按《周易》本卦卦辞、全部实际动爻与变卦卦辞阅读，不套用梅花体用或六爻纳甲。",16,JianYiUi.INK));content.addView(reading);
        LinearLayout process=card();process.addView(title("十八变记录"));process.addView(text(records,14,JianYiUi.MUTED));content.addView(process);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);
        JianYiUi.dialogBuilder(this).setTitle("查看详细依据").setView(scroll).setPositiveButton("返回结果",null).show();
        scroll.post(new Runnable(){public void run(){scroll.scrollTo(0,0);}});
    }
    private void showOfflineReading(String asked,ClassicInfo base,ClassicInfo changed){
        if(base==null){Toast.makeText(this,"经文未能载入，请稍后重试。",Toast.LENGTH_LONG).show();return;}
        LinearLayout content=column();content.setPadding(dp(16),dp(8),dp(16),dp(18));
        LinearLayout questionCard=card();questionCard.addView(title("所问事项"));questionCard.addView(text(asked,16,JianYiUi.INK));content.addView(questionCard);
        LinearLayout baseCard=card();baseCard.addView(title("本卦 · "+base.name));baseCard.addView(text("卦辞\n"+base.judgment,17,JianYiUi.INK));if(base.tuan.length()>0)baseCard.addView(text("彖传\n"+base.tuan,16,JianYiUi.INK));if(base.image.length()>0)baseCard.addView(text("大象\n"+base.image,16,JianYiUi.INK));baseCard.addView(text("六爻原文 · 自下而上\n"+base.allLines(),15,JianYiUi.INK));content.addView(baseCard);
        LinearLayout movingCard=card();movingCard.addView(title("本次实际动爻"));movingCard.addView(text(movingText(),17,JianYiUi.ACCENT));String movingClassics=base.movingLines(values);movingCard.addView(text(movingClassics.length()==0?"本次无动爻，以本卦卦辞为主。":movingClassics,16,JianYiUi.INK));content.addView(movingCard);
        if(changed!=null){LinearLayout changedCard=card();changedCard.addView(title("变卦 · "+changed.name));changedCard.addView(text("卦辞\n"+changed.judgment,17,JianYiUi.INK));if(changed.tuan.length()>0)changedCard.addView(text("彖传\n"+changed.tuan,16,JianYiUi.INK));if(changed.image.length()>0)changedCard.addView(text("大象\n"+changed.image,16,JianYiUi.INK));content.addView(changedCard);}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);
        JianYiUi.dialogBuilder(this).setTitle("离线卦解").setView(scroll).setPositiveButton("返回结果",null).show();
        scroll.post(new Runnable(){public void run(){scroll.scrollTo(0,0);}});
    }
    private ClassicInfo classicForCode(String target){
        InputStream stream=null;
        try{stream=getAssets().open("appdata/zhouyi-classics.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=stream.read(buffer))!=-1){if(bytes.size()+count>1024*1024)throw new IllegalArgumentException("classic data too large");bytes.write(buffer,0,count);}JSONArray entries=new JSONObject(new String(bytes.toByteArray(),"UTF-8")).getJSONArray("hexagrams");for(int i=0;i<entries.length();i++){JSONObject item=entries.optJSONObject(i);if(item!=null&&target.equals(item.optString("code")))return new ClassicInfo(item);}return null;}catch(Exception ignored){return null;}finally{if(stream!=null)try{stream.close();}catch(Exception ignored){}}
    }
    static final class ClassicInfo {final String name,judgment,tuan,image;final JSONArray lines;ClassicInfo(JSONObject source){name=source.optString("full_name",source.optString("short_name","本卦"));judgment=source.optString("judgment","");tuan=source.optString("tuan","");image=source.optString("image","");lines=source.optJSONArray("lines");}JSONObject line(int position){if(lines==null)return null;for(int i=0;i<lines.length();i++){JSONObject item=lines.optJSONObject(i);if(item!=null&&item.optInt("position",i+1)==position)return item;}return null;}String allLines(){StringBuilder out=new StringBuilder();if(lines==null)return "经文暂未载入";for(int i=0;i<lines.length();i++){JSONObject item=lines.optJSONObject(i);if(item==null)continue;if(out.length()>0)out.append("\n\n");out.append(item.optString("label","第"+(i+1)+"爻")).append("：").append(item.optString("text",""));String xiang=item.optString("xiang","").trim();if(xiang.length()>0)out.append("\n《象》").append(xiang);}return out.toString();}String movingLines(int[] values){StringBuilder out=new StringBuilder();for(int i=0;i<6;i++)if(values[i]==6||values[i]==9){JSONObject item=line(i+1);if(item==null)continue;if(out.length()>0)out.append("\n\n");out.append(item.optString("label","第"+(i+1)+"爻")).append("：").append(item.optString("text",""));String xiang=item.optString("xiang","").trim();if(xiang.length()>0)out.append("\n《象》").append(xiang);}return out.toString();}}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(16),dp(14),dp(16),dp(14));JianYiUi.surface(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);v.setLayoutParams(p);return v;}private TextView title(String s){return text(s,21,JianYiUi.INK);}private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(5),1f);v.setPadding(0,dp(3),0,dp(8));return v;}private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);JianYiUi.styleInput(e);e.setMinLines(2);return e;}private Button button(String s,boolean primary,View.OnClickListener l){Button b=new Button(this);b.setText(s);JianYiUi.skinButton(b,primary);b.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(p);return b;}private int dp(int n){return JianYiUi.dp(this,n);}
    static final class StalkView extends View {Paint paint=new Paint(1);float phase;int total=49,left=24,right=25,lr=4,rr=4,removed=9,remain=40;Runnable end;ValueAnimator animator;StalkView(Activity c){super(c);paint.setStrokeWidth(JianYiUi.dp(c,3));paint.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(0xfffaf8f1);}void animateState(int t,int l,int r,int a,int b,int x,int n,final Runnable done){total=t;left=l;right=r;lr=a;rr=b;removed=x;remain=n;end=done;ValueAnimator v=ValueAnimator.ofFloat(0,4);animator=v;v.setDuration(2800);v.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator a){phase=(Float)a.getAnimatedValue();invalidate();}});v.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){Runnable callback=end;end=null;animator=null;if(callback!=null)callback.run();}});v.start();}void finishNow(){if(animator!=null)animator.end();}void cancelAnimation(){end=null;if(animator!=null)animator.cancel();animator=null;}private void label(Canvas c,String s,float x,float y,int size,int color,Paint.Align align){paint.setStyle(Paint.Style.FILL);paint.setTextAlign(align);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(JianYiUi.dp(getContext(),size));paint.setColor(color);c.drawText(s,x,y,paint);}private void rods(Canvas c,int count,float cx,float top,int color){paint.setColor(color);paint.setStrokeWidth(JianYiUi.dp(getContext(),3));for(int i=0;i<count;i++){float x=cx+((i%10)-4.5f)*JianYiUi.dp(getContext(),5);float y=top+(i/10)*JianYiUi.dp(getContext(),18);c.drawLine(x,y,x+JianYiUi.dp(getContext(),2),y+JianYiUi.dp(getContext(),13),paint);}}protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();int stage=Math.min(3,(int)phase);String[] heads={"① 分而为二 · 象两","② 挂一 · 象三","③ 揲之以四 · 象四时","④ 归奇于扐 · 本变完成"};label(c,heads[stage],w/2,JianYiUi.dp(getContext(),30),17,JianYiUi.INK,Paint.Align.CENTER);if(stage==0){rods(c,left,w*.25f,dpv(62),0xff2f6f62);rods(c,right,w*.75f,dpv(62),0xffb07a2a);label(c,"左 "+left+" 策",w*.25f,h-dpv(24),15,0xff2f6f62,Paint.Align.CENTER);label(c,"右 "+right+" 策",w*.75f,h-dpv(24),15,0xff9a681f,Paint.Align.CENTER);}else if(stage==1){rods(c,left,w*.23f,dpv(62),0xff2f6f62);rods(c,right-1,w*.77f,dpv(62),0xffb07a2a);rods(c,1,w*.5f,dpv(100),0xffb13b35);label(c,"从右取一策挂于中间",w*.5f,h-dpv(24),15,0xffb13b35,Paint.Align.CENTER);}else if(stage==2){rods(c,left-lr,w*.2f,dpv(62),0xff2f6f62);rods(c,right-1-rr,w*.8f,dpv(62),0xffb07a2a);rods(c,lr,w*.4f,dpv(145),0xff4f8f82);rods(c,rr,w*.6f,dpv(145),0xffd19a45);label(c,"左余 "+lr+"　右余 "+rr+"　另有挂一",w*.5f,h-dpv(24),15,JianYiUi.INK,Paint.Align.CENTER);}else{rods(c,remain,w*.5f,dpv(58),0xff2f6f62);rods(c,removed,w*.5f,dpv(190),0xffb8b3a5);label(c,"归奇 "+removed+" 策",w*.5f,dpv(185),14,JianYiUi.MUTED,Paint.Align.CENTER);label(c,"本变余 "+remain+" 策",w*.5f,h-dpv(20),17,JianYiUi.ACCENT,Paint.Align.CENTER);}}private float dpv(int n){return JianYiUi.dp(getContext(),n);}}
}
