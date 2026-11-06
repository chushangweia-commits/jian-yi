package com.jianyi.yixue.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import org.json.JSONArray;
import org.json.JSONObject;

/** Independent six-line casting tool. Existing Meihua calculation and AI flows remain untouched. */
public final class LiuYaoActivity extends Activity {
    private static final String[] VALUES = {"尚未录入", "6 · 老阴（动）", "7 · 少阳", "8 · 少阴", "9 · 老阳（动）"};
    private final Spinner[] selectors = new Spinner[6];
    private final int[] lines = new int[6];
    private final SecureRandom random = new SecureRandom();
    private EditText question, followup;
    private LinearLayout content, result, ai;
    private Button aiButton;
    private Button coinsButton;
    private TextView castingProgress;
    private NotebookRecord record;
    private AiReadingStore chatStore;
    private AiReadingStore.Session session;
    private final LiuYaoRequestState requestState = new LiuYaoRequestState();
    private boolean busy, savedView, changingLines;
    private String chart = "", answer = "";
    private DeepSeekClient.Cancellation cancellation;
    private final Calendar castTime=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
    private Button timeButton;

    public static void open(Activity activity,String recordId,String sessionId) {
        activity.startActivity(new Intent(activity,LiuYaoActivity.class).putExtra("record_id",recordId).putExtra("session_id",sessionId));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); JianYiUi.applySystemBars(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        chatStore=new AiReadingStore(this);
        String recordId=state==null?getIntent().getStringExtra("record_id"):state.getString("record_id");
        if(recordId!=null&&recordId.length()>0) {
            try {
                record=NotebookStore.get(this).find(recordId);
                if(record==null||!record.method.startsWith("六爻"))throw new IllegalArgumentException();
                chart=record.info;savedView=true;
                String sessionId=state==null?getIntent().getStringExtra("session_id"):state.getString("session_id");
                if(sessionId!=null)session=chatStore.get(sessionId);
                prepareSession();showSaved();return;
            }catch(Exception e){Toast.makeText(this,"这条六爻卦例未能读取，请返回卦例本重试。",Toast.LENGTH_LONG).show();finish();return;}
        }
        if(state!=null&&state.containsKey("casting_time"))castTime.setTimeInMillis(state.getLong("casting_time"));
        showPage();
        if(state!=null){changingLines=true;question.setText(state.getString("casting_question",""));int[] saved=state.getIntArray("casting_lines");if(saved!=null&&saved.length==6){for(int i=0;i<6;i++){lines[i]=saved[i]>=6&&saved[i]<=9?saved[i]:0;selectors[i].setSelection(lines[i]==0?0:lines[i]-5);}}changingLines=false;updateProgress();}
    }

    @Override protected void onDestroy() {
        cancelRequest();
        if(chatStore!=null)chatStore.close();
        super.onDestroy();
    }

    @Override protected void onStop(){keepDraft();cancelRequest();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){keepDraft();cancelRequest();if(record!=null)out.putString("record_id",record.id);if(session!=null)out.putString("session_id",session.id);if(record==null&&question!=null){out.putString("casting_question",question.getText().toString());out.putIntArray("casting_lines",lines.clone());out.putLong("casting_time",castTime.getTimeInMillis());}super.onSaveInstanceState(out);}

    private void showSaved() {
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);JianYiUi.applyScenic(shell,JianYiUi.SCENE_DIVINATION);shell.setFitsSystemWindows(true);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(30));scroll.addView(content);shell.addView(scroll);setContentView(shell);
        content.addView(JianYiUi.hero(this,"六爻解读","固定这条卦例 · 完整保留解答与追问"));
        content.addView(button("返回卦例",false,new View.OnClickListener(){public void onClick(View v){finish();}}));
        LinearLayout c=card();c.addView(title("所问事项"));c.addView(text(record.question,17,JianYiUi.INK,false));
        c.addView(text("以下沿用此卦例保存的六爻排盘。",14,JianYiUi.MUTED,false));
        c.addView(button("查看完整六爻排盘",false,new View.OnClickListener(){public void onClick(View v){ScrollView sc=new ScrollView(LiuYaoActivity.this);TextView t=text(chart,15,JianYiUi.INK,false);t.setPadding(dp(18),dp(12),dp(18),dp(12));sc.addView(t);new AlertDialog.Builder(LiuYaoActivity.this).setTitle("保存的六爻排盘").setView(sc).setPositiveButton("关闭",null).show();}}));content.addView(c);
        ai=new LinearLayout(this);ai.setOrientation(LinearLayout.VERTICAL);content.addView(ai);showAi();
    }

    private void showPage() {
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); JianYiUi.applyScenic(shell,JianYiUi.SCENE_DIVINATION); shell.setFitsSystemWindows(true);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(30)); scroll.addView(content); shell.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); setContentView(shell);
        content.addView(JianYiUi.hero(this, "六爻起卦", "静心定题 · 起卦排盘 · 本机判断 · AI 解读"));
        content.addView(button("返回首页", false, new View.OnClickListener(){ public void onClick(View v){ finish(); }}));
        LinearLayout ask = card(); ask.addView(title("01 · 定问")); ask.addView(text("静心定问 · 一事一问",15,JianYiUi.GOLD,true)); ask.addView(text("写清一件事、时间范围和最关心的结果。",14,JianYiUi.MUTED,false));
        question = input("例如：未来三个月这次求职进展如何？", 3);question.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        question.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){invalidateChart();}});
        ask.addView(question); content.addView(ask);
        LinearLayout casting = card(); casting.addView(title("02 · 起卦"));
        casting.addView(text("六爻从下往上排列。可模拟三枚铜钱，也可按实际结果逐爻选择。",14,JianYiUi.MUTED,false));
        timeButton=button(timeLabel(),false,new View.OnClickListener(){public void onClick(View v){pickDate();}});casting.addView(timeButton);
        for (int visual=5; visual>=0; visual--) addLine(casting, visual);
        castingProgress=text("已录入 0 / 6 爻 · 从初爻开始",14,JianYiUi.ACCENT,true);casting.addView(castingProgress);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        coinsButton=button("投掷初爻",false,new View.OnClickListener(){public void onClick(View v){castCoins();}});
        Button calculate=button("生成排盘",true,new View.OnClickListener(){public void onClick(View v){calculate();}});
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.rightMargin=dp(5);actions.addView(coinsButton,half);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1);half2.leftMargin=dp(5);actions.addView(calculate,half2);
        casting.addView(actions);
        casting.addView(actionLink("清空六爻，重新起卦 ›",new View.OnClickListener(){public void onClick(View v){changingLines=true;Arrays.fill(lines,0);for(Spinner s:selectors)s.setSelection(0);changingLines=false;invalidateChart();updateProgress();}}));
        content.addView(casting);
        result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); content.addView(result);
        ai = new LinearLayout(this); ai.setOrientation(LinearLayout.VERTICAL); content.addView(ai);
    }

    private void addLine(LinearLayout parent, final int index) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        String[] names={"初爻","二爻","三爻","四爻","五爻","上爻"};
        TextView label=text(names[index],15,JianYiUi.INK,true); row.addView(label,new LinearLayout.LayoutParams(dp(58),-2));
        Spinner spinner=new Spinner(this); spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, VALUES));
        spinner.setSelection(0); selectors[index]=spinner;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){int value=position==0?0:position+5;if(lines[index]!=value){lines[index]=value;if(!changingLines)invalidateChart();}updateProgress();}});
        row.addView(spinner,new LinearLayout.LayoutParams(0,dp(52),1)); parent.addView(row);
    }

    private void castCoins() {
        int next=0;while(next<6&&lines[next]!=0)next++;
        if(next==6)return;
        invalidateChart();int sum=0;for(int c=0;c<3;c++)sum+=random.nextBoolean()?3:2;
        lines[next]=sum;selectors[next].setSelection(sum-5);updateProgress();
        Toast.makeText(this,positionName(next+1)+"："+sum+(next==5?"，六爻已齐，可以生成排盘。":"，继续投掷下一爻。"),Toast.LENGTH_SHORT).show();
    }

    private void updateProgress(){if(castingProgress==null||coinsButton==null)return;int count=0,next=0;for(int v:lines)if(v!=0)count++;while(next<6&&lines[next]!=0)next++;castingProgress.setText("已录入 "+count+" / 6 爻"+(count==6?" · 可以生成排盘":" · 下一次投掷"+positionName(next+1)));coinsButton.setText(count==6?"六爻已齐":"投掷"+positionName(next+1));coinsButton.setEnabled(count<6);}
    private void cancelRequest(){requestState.invalidate();if(cancellation!=null)cancellation.cancel();cancellation=null;busy=false;if(aiButton!=null){aiButton.setEnabled(true);aiButton.setText("AI 解卦（需联网）");}}
    private void invalidateChart(){if(changingLines)return;cancelRequest();chart="";answer="";record=null;session=null;if(result!=null)result.removeAllViews();if(ai!=null)ai.removeAllViews();}

    private void calculate() {
        String q=question.getText().toString().trim();
        if(q.length()==0){ question.requestFocus(); Toast.makeText(this,"请先填写所问事项。",Toast.LENGTH_SHORT).show(); return; }
        if(AiReadingContext.charCount(q)>500){Toast.makeText(this,"所问事项请控制在500字以内。",Toast.LENGTH_SHORT).show();return;}
        for(int i=0;i<6;i++)if(lines[i]==0){Toast.makeText(this,"请先录入完整六爻，当前缺少"+positionName(i+1)+"。",Toast.LENGTH_SHORT).show();return;}
        cancelRequest();answer=""; record=null;session=null;
        LiuYaoEngine.UseGodHint useGod=LiuYaoEngine.suggestUseGod(q);
        for(int i=0;i<6;i++) lines[i]=5+selectors[i].getSelectedItemPosition();
        int[] base=new int[6], changed=new int[6]; int firstMoving=0;
        StringBuilder rows=new StringBuilder(); String[] names={"初爻","二爻","三爻","四爻","五爻","上爻"};
        for(int i=0;i<6;i++){ base[i]=(lines[i]==7||lines[i]==9)?1:0; changed[i]=base[i]; if(lines[i]==6||lines[i]==9){ changed[i]^=1; if(firstMoving==0) firstMoving=i+1; }}
        int lower=MeihuaEngine.trigramId(Arrays.copyOfRange(base,0,3)), upper=MeihuaEngine.trigramId(Arrays.copyOfRange(base,3,6));
        int changedLower=MeihuaEngine.trigramId(Arrays.copyOfRange(changed,0,3)), changedUpper=MeihuaEngine.trigramId(Arrays.copyOfRange(changed,3,6));
        String baseName=hexName(base), changedName=hexName(changed);
        Lunar lunar=Solar.fromYmdHms(castTime.get(Calendar.YEAR),castTime.get(Calendar.MONTH)+1,castTime.get(Calendar.DAY_OF_MONTH),castTime.get(Calendar.HOUR_OF_DAY),castTime.get(Calendar.MINUTE),0).getLunar();
        LiuYaoEngine.Result plate=LiuYaoEngine.calculate(lines,lunar.getMonthInGanZhiExact(),lunar.getDayInGanZhiExact());
        for(int i=5;i>=0;i--){
            LiuYaoEngine.Line line=plate.lines[i];
            rows.append(names[i]).append(" ").append(line.god).append(" ").append(line.relative).append(" ").append(line.stemBranch).append(line.element).append(" ")
                .append(line.yang?"━━━━━━":"━━　━━").append(line.moving?" ○":"  ").append(line.marker.length()>0?" "+line.marker:"");
            if(line.moving)rows.append(" → ").append(line.changedRelative).append(" ").append(line.changedStemBranch);
            rows.append("\n  本机规则：").append(line.ruleFacts).append("\n");
        }
        chart="起卦："+timeLabel().replace("起卦时间 · ","")+"（北京时间）\n月建："+plate.monthGanZhi+"　日辰："+plate.dayGanZhi+"　旬空："+plate.kong+"\n"
            +"本卦："+baseName+"　"+plate.palace+" · "+LiuYaoEngine.stageName(plate.stage)+"　世"+plate.shi+"应"+plate.ying+"\n"
            +(firstMoving==0?"无动爻":"之卦："+changedName)
            +"\n\n[本机用神引导]\n"+useGod.display()+"\n\n"+rows.toString().trim()
            +"\n\n[三合支齐]\n"+plate.threeHarmonySummary
            +"\n\n[应期触发支]\n"+plate.timingSummary
            +"\n\n[本机规则口径]\n"+plate.ruleScope;
        result.removeAllViews(); LinearLayout c=card(); c.addView(title("03 · 排盘"));
        c.addView(text(baseName+"　"+plate.palace+" · "+LiuYaoEngine.stageName(plate.stage)+(firstMoving==0?"":"　→ "+changedName),18,JianYiUi.ACCENT,true));
        c.addView(text("月建 "+plate.monthGanZhi+"　日辰 "+plate.dayGanZhi+"　旬空 "+plate.kong+"\n世爻 "+plate.shi+"　应爻 "+plate.ying+"　"+timeLabel().replace("起卦时间 · ",""),14,JianYiUi.MUTED,false));
        for(int i=5;i>=0;i--) addPlateRow(c,names[i],plate.lines[i]);
        result.addView(c);
        LinearLayout core=card();core.addView(title("判断要点"));
        core.addView(text("用神",14,JianYiUi.MUTED,true));
        core.addView(text(useGod.display(),16,useGod.needsConfirmation?JianYiUi.GOLD:JianYiUi.INK,false));
        core.addView(text("世应",14,JianYiUi.MUTED,true));
        core.addView(text(coreLine("世爻",plate.lines[plate.shi-1])+"\n"+coreLine("应爻",plate.lines[plate.ying-1]),15,JianYiUi.INK,false));
        core.addView(text("主要动爻",14,JianYiUi.MUTED,true));
        core.addView(text(movingSummary(plate),15,JianYiUi.INK,false));
        core.addView(button("查看详细依据",false,new View.OnClickListener(){public void onClick(View v){showEvidence(plate,useGod);}}));
        result.addView(core);
        try {
            record=NotebookStore.draft("六爻排盘", chart, upper, lower, firstMoving==0?1:firstMoving, "");
            record.title="六爻 · "+baseName; record.question=q; record.analysis=""; record=NotebookStore.get(this).save(record);prepareSession();
            Toast.makeText(this,"排盘已保存到卦例本。",Toast.LENGTH_SHORT).show();
        } catch(Exception e){ Toast.makeText(this,"排盘已完成，卦例暂未保存。",Toast.LENGTH_LONG).show(); }
        showAi();
    }

    private String timeLabel(){return "起卦时间 · "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(castTime.getTime());}
    private void addPlateRow(LinearLayout parent,String name,LiuYaoEngine.Line line){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(10),dp(9),dp(10),dp(8));
        TextView facts=text(name+" · "+line.god+" · "+line.relative+line.stemBranch+line.element+(line.marker.length()==0?"":" · "+line.marker),14,JianYiUi.MUTED,false);facts.setPadding(0,0,0,dp(2));row.addView(facts);
        String change=line.moving?"　动 → "+line.changedRelative+line.changedStemBranch:"　静";
        TextView symbol=text((line.yang?"━━━━━━":"━━　━━")+change,18,line.moving?JianYiUi.ACCENT:JianYiUi.INK,true);symbol.setPadding(0,0,0,0);row.addView(symbol);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(line.moving?0xffeef4ed:0xfffaf9f5);bg.setCornerRadius(dp(10));row.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);parent.addView(row,p);
    }

    private String coreLine(String role,LiuYaoEngine.Line line){
        return role+" · "+positionName(line.position)+" · "+line.god+" · "+line.relative+line.stemBranch+line.element;
    }

    private String movingSummary(LiuYaoEngine.Result plate){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<6;i++){
            LiuYaoEngine.Line line=plate.lines[i];if(!line.moving)continue;
            if(out.length()>0)out.append("\n");
            out.append(positionName(line.position)).append(" · ").append(line.relative).append(line.stemBranch).append(line.element)
                .append(" → ").append(line.changedRelative).append(line.changedStemBranch);
            if(line.transformation.length()>0)out.append(" · ").append(line.transformation);
        }
        return out.length()==0?"本卦无动爻，按静卦观察世、应与用神。":out.toString();
    }

    private void showEvidence(LiuYaoEngine.Result plate,LiuYaoEngine.UseGodHint useGod){
        StringBuilder detail=new StringBuilder();
        detail.append("【用神依据】\n").append(useGod.display()).append("\n\n【逐爻规则】\n");
        for(int i=5;i>=0;i--){
            LiuYaoEngine.Line line=plate.lines[i];
            detail.append(positionName(line.position)).append(" · ").append(line.god).append(" · ")
                .append(line.relative).append(line.stemBranch).append(line.element);
            if(line.marker.length()>0)detail.append(" · ").append(line.marker);
            detail.append("\n").append(line.ruleFacts);
            if(line.timingHint.length()>0)detail.append("\n应期线索：").append(line.timingHint);
            detail.append("\n\n");
        }
        detail.append("【三合支齐】\n").append(plate.threeHarmonySummary)
            .append("\n\n【应期触发支】\n").append(plate.timingSummary)
            .append("\n\n【计算口径】\n").append(plate.ruleScope)
            .append("\n\n标为“候选”或“线索”的项目仍须结合占题，不自动作为吉凶结论。");
        ScrollView scroll=new ScrollView(this);TextView body=text(detail.toString(),15,JianYiUi.INK,false);
        body.setPadding(dp(18),dp(8),dp(18),dp(18));scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("六爻详细依据").setView(scroll).setPositiveButton("关闭",null).show();
    }

    private String positionName(int position){return new String[]{"","初爻","二爻","三爻","四爻","五爻","上爻"}[position];}
    private void pickDate(){
        new DatePickerDialog(this,new DatePickerDialog.OnDateSetListener(){public void onDateSet(android.widget.DatePicker v,final int y,final int m,final int d){
            new TimePickerDialog(LiuYaoActivity.this,new TimePickerDialog.OnTimeSetListener(){public void onTimeSet(android.widget.TimePicker v,int h,int min){castTime.set(y,m,d,h,min,0);timeButton.setText(timeLabel());invalidateChart();}},castTime.get(Calendar.HOUR_OF_DAY),castTime.get(Calendar.MINUTE),true).show();
        }},castTime.get(Calendar.YEAR),castTime.get(Calendar.MONTH),castTime.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void prepareSession() throws Exception {
        if(record==null)return;
        String reference=AiReadingStore.notebookReference(record.id);
        if(session==null)session=chatStore.latest(reference);
        if(session!=null&&(!session.reference.equals(reference)||!session.method.startsWith("六爻")
            ||!session.chart.equals(chart)||!session.subject.equals(record.question)))session=null;
        if(session==null)session=chatStore.create(reference,record.upper,record.lower,record.moving,
            "六爻纳甲",record.question,chart,record.question);
    }

    private void keepDraft(){
        if(session==null||followup==null||busy)return;
        String draft=followup.getText().toString();
        if(AiReadingContext.charCount(draft)<=500){session.draft=draft;try{chatStore.save(session);}catch(Exception ignored){}}
    }

    private void showAi() {
        ai.removeAllViews(); LinearLayout c=card(); c.addView(title("04 · 六爻解读"));
        c.addView(text("六爻纳甲 · 仅发送 AI 解读时联网 · 每条回答不超过200字。若格式不符，最多自动重试一次，可能产生额外费用。",14,JianYiUi.GOLD,true));
        if(record==null||session==null){
            c.addView(text("请先成功保存排盘，才能绑定并保留 AI 问答。",15,JianYiUi.MUTED,false));ai.addView(c);return;
        }
        c.addView(text("本次排盘及完整问答均保留在这条卦例中。",14,JianYiUi.MUTED,false));
        for(int i=0;i<session.history.length();i++){
            JSONObject message=session.history.optJSONObject(i);
            c.addView(text(i%2==0?"第 "+(i/2+1)+" 轮 · 你问":"AI · 六爻解读",14,JianYiUi.ACCENT,true));
            c.addView(text(message.optString("content"),16,JianYiUi.INK,false));
        }
        if(session.history.length()>0){
            c.addView(text("本机核验：六爻体系 · 用神、世应、月日动变依据标签",13,JianYiUi.MUTED,false));
            c.addView(actionLink("查看引用课程：六爻用神与排盘依据 ›",new View.OnClickListener(){public void onClick(View v){KnowledgeReader.show(LiuYaoActivity.this,"advanced_liuyao_05");}}));
        }
        if(session.history.length()>=16){
            c.addView(text("本次已完成8轮，完整记录已保存。可按同一排盘新建对话。",14,JianYiUi.MUTED,false));
            c.addView(button("同卦新建对话",true,new View.OnClickListener(){public void onClick(View v){try{cancelRequest();session=chatStore.create(AiReadingStore.notebookReference(record.id),record.upper,record.lower,record.moving,"六爻纳甲",record.question,chart,record.question);showAi();}catch(Exception e){Toast.makeText(LiuYaoActivity.this,"新对话未能保存，请重试。",Toast.LENGTH_LONG).show();}}}));
        }else{
            followup=input(session.history.length()==0?"所问事项或补充背景":"继续追问这条六爻卦例",2);followup.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});followup.setText(session.draft);c.addView(followup);
            aiButton=button(session.history.length()==0?"AI 解卦（需联网）":"发送追问",true,new View.OnClickListener(){public void onClick(View v){requestAi();}});c.addView(aiButton);
        }
        c.addView(actionLink("AI 联网设置 ›",new View.OnClickListener(){public void onClick(View v){keepDraft();AiReadingActivity.settings(LiuYaoActivity.this);}}));
        c.addView(actionLink("查看卦例 ›",new View.OnClickListener(){public void onClick(View v){keepDraft();NotebookActivity.open(LiuYaoActivity.this,record.id);}}));
        ai.addView(c);
    }

    private boolean accepts(LiuYaoRequestState.Snapshot snapshot,DeepSeekClient.Cancellation token){
        return !isFinishing()&&!isDestroyed()&&!token.isCancelled()&&record!=null&&session!=null
            &&requestState.accepts(snapshot,record.id,session.id,record.question,chart);
    }
    private void requestAi() {
        if(busy||chart.length()==0||record==null||session==null)return;
        if(!AiSettings.hasKey(this)){keepDraft();AiReadingActivity.settings(this);return;}
        final String current=followup.getText().toString().trim();
        if(current.length()==0||AiReadingContext.charCount(current)>500){Toast.makeText(this,"请填写1至500字的问题或追问。",Toast.LENGTH_SHORT).show();return;}
        final AiReadingStore.Session requestSession;
        final JSONArray messages;
        try{
            NotebookRecord latest=NotebookStore.get(this).find(record.id);
            if(latest==null||!latest.question.equals(record.question)||!latest.info.equals(chart))throw new IllegalStateException("卦例已改变，请返回卦例本重新打开。");
            session.draft=current;chatStore.save(session);
            // Fresh row and JSON copies are immutable inputs to this request; no view is read by the worker.
            requestSession=chatStore.get(session.id);
            messages=LiuYaoConversation.messages(requestSession.subject,requestSession.chart,current,requestSession.history.toString());
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        final LiuYaoRequestState.Snapshot snapshot=requestState.capture(record.id,requestSession.id,requestSession.subject,requestSession.chart,requestSession.history.toString());
        busy=true;aiButton.setEnabled(false);aiButton.setText("正在解读…");
        final DeepSeekClient.Cancellation token=new DeepSeekClient.Cancellation();cancellation=token;
        new Thread(new Runnable(){public void run(){
            try{
                final String got=DeepSeekClient.answer(AiSettings.readKey(getApplicationContext()),messages,token,AiAnswerPolicy.LIUYAO);
                runOnUiThread(new Runnable(){public void run(){
                    if(!accepts(snapshot,token))return;busy=false;cancellation=null;
                    try{
                        JSONArray completed=new JSONArray(snapshot.historyJson);
                        completed.put(new JSONObject().put("role","user").put("content",current));
                        completed.put(new JSONObject().put("role","assistant").put("content",got));
                        requestSession.history=completed;requestSession.draft="";
                        chatStore.save(requestSession);session=requestSession;answer=got;showAi();
                    }catch(Exception e){
                        showAi();LinearLayout unsaved=card();unsaved.addView(title("回答尚未保存"));unsaved.addView(text(got,16,JianYiUi.INK,false));ai.addView(unsaved);
                        Toast.makeText(LiuYaoActivity.this,e.getMessage()+" 请复制回答后重新打开。",Toast.LENGTH_LONG).show();
                    }
                }});
            }catch(final Exception e){runOnUiThread(new Runnable(){public void run(){if(!accepts(snapshot,token))return;busy=false;cancellation=null;showAi();Toast.makeText(LiuYaoActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}});}
        }}).start();
    }

    private String hexName(int[] bits){
        StringBuilder code=new StringBuilder();for(int b:bits)code.append(b);
        try(InputStream in=getAssets().open("appdata/hexagrams.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            JSONArray rows=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("hexagrams");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(code.toString().equals(row.getString("code")))return row.getString("full_name");}
        }catch(Exception ignored){}
        return "上"+MeihuaEngine.trigramName(MeihuaEngine.trigramId(Arrays.copyOfRange(bits,3,6)))+"下"+MeihuaEngine.trigramName(MeihuaEngine.trigramId(Arrays.copyOfRange(bits,0,3)));
    }

    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(16),dp(14),dp(16),dp(14));JianYiUi.surface(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));v.setLayoutParams(p);return v;}
    private TextView title(String s){return text(s,21,JianYiUi.INK,true);}
    private TextView text(String s,float z,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(color);v.setLineSpacing(dp(4),1f);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(0,dp(4),0,dp(8));return v;}
    private EditText input(String hint,int lines){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(JianYiUi.INK);e.setHintTextColor(JianYiUi.MUTED);e.setMinLines(lines);e.setGravity(Gravity.TOP);return e;}
    private TextView actionLink(String s,View.OnClickListener l){TextView v=text(s,15,JianYiUi.ACCENT,true);v.setPadding(0,dp(8),0,dp(8));v.setOnClickListener(l);return v;}
    private Button button(String s,boolean primary,View.OnClickListener l){Button b=new Button(this);b.setText(s);JianYiUi.skinButton(b,primary);b.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(p);return b;}
    private int dp(int v){return JianYiUi.dp(this,v);}
}
