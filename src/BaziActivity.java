package com.jianyi.yixue.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.TimePicker;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** First-stage Bazi study and manual four-pillars entry. */
public final class BaziActivity extends Activity {
    private LinearLayout content;private Spinner[] pillars=new Spinner[4];private final Calendar solar=Calendar.getInstance();private TextView solarPreview;private Spinner gender,dayBoundary,solarMode;private EditText birthplace,dateInput,longitude;
    private boolean timeConfirmed,entryFromResult,resultFromArchive;private Button timeButton;private String screen="home";
    private BaziEngine.Chart shownChart;private String shownSource="",shownGender="",shownPlace="",shownFortune="";
    public static void open(Activity a){a.startActivity(new Intent(a,BaziActivity.class));}
    @Override public void onCreate(Bundle b){super.onCreate(b);showHome();}

    private void page(String title){
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);JianYiUi.applyScenic(frame,JianYiUi.SCENE_BAZI);frame.setFitsSystemWindows(true);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(8),dp(12),dp(8));
        Button back=button("返回",false,new View.OnClickListener(){public void onClick(View v){onBackPressed();}});bar.addView(back,new LinearLayout.LayoutParams(dp(82),dp(46)));
        TextView name=text(title,20,true);name.setGravity(Gravity.CENTER);bar.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));frame.addView(bar);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(24));scroll.addView(content);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(frame);JianYiUi.applySystemBars(this);
    }
    private void showHome(){screen="home";resultFromArchive=false;page("八字");content.addView(JianYiUi.hero(this,"八字","排盘 · 解读 · 专业盘 · 学堂"));
        content.addView(text("先输入出生时间，查看四柱和容易理解的观察；想了解具体问题时，可以再向 AI 追问。",15,false));
        content.addView(fourPillarGuide());
        if(hasLast()){try{final BaziEngine.Chart last=lastChart();LinearLayout recent=card("最近命盘");recent.addView(text(last.summary(),16,true));recent.addView(text(lastSource(),13,false));recent.addView(button("继续查看",true,new View.OnClickListener(){public void onClick(View v){openLast("plain");}}));content.addView(recent);}catch(Exception ignored){}}
        content.addView(button("查看已保存的命盘",false,new View.OnClickListener(){public void onClick(View v){showArchive();}}));
        LinearLayout p=card("01 · 排盘");p.addView(text("从公历时间生成四柱，或录入已经在其他工具核对过的四柱。",14,false));p.addView(button("公历时间排盘",true,new View.OnClickListener(){public void onClick(View v){showSolarEntry();}}));p.addView(button("手动录入四柱",false,new View.OnClickListener(){public void onClick(View v){showEntry();}}));content.addView(p);
        LinearLayout reading=card("02 · 解读");reading.addView(text("围绕工作、沟通或生活中的具体问题，结合四柱与《子平真诠》研习。",14,false));Button readLast=button("解读最近命盘",hasLast(),new View.OnClickListener(){public void onClick(View v){openLast("ai");}});readLast.setEnabled(hasLast());reading.addView(readLast);content.addView(reading);
        LinearLayout professional=card("03 · 专业盘");professional.addView(text("查看四柱、十神、藏干、月令与本轮规则依据；专业信息与白话结论分开呈现。",14,false));professional.addView(button("查看最近专业盘",false,new View.OnClickListener(){public void onClick(View v){openLast("pro");}}));content.addView(professional);
        LinearLayout study=card("04 · 学堂");study.addView(text("图解五行、天干阴阳、十神来源、四柱位置与月令入口。",14,false));study.addView(button("开始图解学习",false,new View.OnClickListener(){public void onClick(View v){showLessons();}}));content.addView(study);
        LinearLayout n=card("关于排盘时间");n.addView(text("可直接使用北京时间，也可选择输入出生地经度进行真太阳时校正。两种口径可能在时辰或节气交接附近得到不同结果。",14,false));content.addView(n);
    }
    private void showLessons(){screen="lessons";page("八字基础 · 图解");content.addView(new RelationView(this));
        lesson("01 · 五行关系","木生火、火生土、土生金、金生水、水生木；相克按木克土、土克水、水克火、火克金、金克木。先认关系，再谈十神。","记忆：同我为比劫，我生为食伤，我克为财，克我为官杀，生我为印。" );
        lesson("02 · 天干阴阳","甲丙戊庚壬为阳，乙丁己辛癸为阴。十神正偏由五行关系与阴阳同异共同决定。","同类阴阳相同多称偏，阴阳相异多称正；比劫、食伤另有各自名称。" );
        lesson("03 · 四柱位置","年、月、日、时四柱各由一个天干和一个地支构成。日干称日主，是计算其他干支十神的参照点。","手动排盘只接受六十甲子中的合法干支组合。" );
        lesson("04 · 地支藏干","地支内部含一个或多个天干。界面依次列出藏干和相对日主的十神，第一位作为本气。","藏干是结构信息，不代表其中每个十神都同样有力。" );
        lesson("05 · 月令格局","《子平真诠》以月令为定格入口。本版显示月支本气相对日主的十神，再筛选对应规则。","这里的‘用神’是月令定格之神，不与其他流派的扶抑用神混用。" );
        content.addView(button("进入公历排盘",true,new View.OnClickListener(){public void onClick(View v){showSolarEntry();}}));
    }
    private void lesson(String title,String body,String memory){LinearLayout c=card(title);c.addView(text(body,15,false));TextView m=text(memory,14,true);m.setTextColor(JianYiUi.ACCENT);c.addView(m);content.addView(c);}
    private void showSolarEntry(){showSolarEntry("","","");}
    private void showSolarEntry(String savedSource,String savedGender,String savedPlace){entryFromResult=savedSource!=null&&!savedSource.isEmpty()&&shownChart!=null;screen="entry";page("公历出生时间");
        java.util.regex.Matcher saved=java.util.regex.Pattern.compile("(?:北京时间|公历)\\s*(\\d{4})-(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2})").matcher(savedSource==null?"":savedSource);
        boolean restored=saved.find();timeConfirmed=restored;
        if(restored){try{solar.set(Calendar.DAY_OF_MONTH,1);solar.set(Integer.parseInt(saved.group(1)),Integer.parseInt(saved.group(2))-1,Integer.parseInt(saved.group(3)),Integer.parseInt(saved.group(4)),Integer.parseInt(saved.group(5)));}catch(Exception e){restored=false;timeConfirmed=false;}}
        if(!restored){solar.set(1990,0,1,8,0);}
        dayBoundary=null;solarMode=null;longitude=null;dateInput=null;
        content.addView(text("填写公历出生资料，先查看命盘；想看起运和大运，请在下方选择性别。默认使用北京时间。",15,false));
        LinearLayout pick=card("1 · 出生日期和时间");dateInput=new EditText(this);JianYiUi.styleInput(dateInput);dateInput.setSingleLine(true);dateInput.setSelectAllOnFocus(true);dateInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);dateInput.setHint("例如 1992-08-20");pick.addView(dateInput,new LinearLayout.LayoutParams(-1,dp(56)));pick.addView(button("确认输入日期",false,new View.OnClickListener(){public void onClick(View v){applyTypedDate();}}));
        pick.addView(button("也可以打开日历选择",false,new View.OnClickListener(){public void onClick(View v){new DatePickerDialog(BaziActivity.this,new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker d,int y,int m,int day){solar.set(Calendar.DAY_OF_MONTH,1);solar.set(y,m,day);syncDateInput();updateSolarPreview();}},solar.get(Calendar.YEAR),solar.get(Calendar.MONTH),solar.get(Calendar.DAY_OF_MONTH)).show();}}));
        timeButton=button("选择出生时分",false,new View.OnClickListener(){public void onClick(View v){new TimePickerDialog(BaziActivity.this,new TimePickerDialog.OnTimeSetListener(){public void onTimeSet(TimePicker t,int h,int m){solar.set(Calendar.HOUR_OF_DAY,h);solar.set(Calendar.MINUTE,m);timeConfirmed=true;timeButton.setText(String.format(Locale.CHINA,"出生时间 %02d:%02d · 点击修改",h,m));updateSolarPreview();}},solar.get(Calendar.HOUR_OF_DAY),solar.get(Calendar.MINUTE),true).show();}});if(restored)timeButton.setText(String.format(Locale.CHINA,"出生时间 %02d:%02d · 点击修改",solar.get(Calendar.HOUR_OF_DAY),solar.get(Calendar.MINUTE)));pick.addView(timeButton);
        pick.addView(button("不确定出生时间怎么办？",false,new View.OnClickListener(){public void onClick(View v){JianYiUi.showDialog(BaziActivity.this,JianYiUi.dialogBuilder(BaziActivity.this).setTitle("出生时间不确定").setMessage("时柱和起运时间会受到出生时分影响，目前需要确认具体时间后才能生成完整命盘。建议先向家人或出生证明核实，再回来填写。\n\n如果你已经有其他工具核对过的完整四柱，也可以在本页选择“改用手动四柱”；手动盘暂不能计算大运。").setPositiveButton("知道了",null).create());}}));content.addView(pick);
        LinearLayout genderCard=card("2 · 是否排起运和大运");genderCard.addView(text("选择性别用于按本版传统排运规则确定顺逆，不改变原局四柱。也可以暂不排大运。",14,false));gender=new Spinner(this);gender.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"性别不指定（不排大运）","男","女"}));genderCard.addView(gender,new LinearLayout.LayoutParams(-1,dp(52)));content.addView(genderCard);
        solarPreview=text("",15,true);LinearLayout preview=card("排盘前确认");preview.addView(solarPreview);content.addView(preview);
        LinearLayout profile=card("高级设置（选填）");
        profile.addView(text("一般情况保持默认即可：使用北京时间，23:00 后按当天计日。",14,false));
        final LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);
        final Button advancedToggle=button("查看时间口径和出生地",false,null);
        boolean showAdvanced=savedSource!=null&&(savedSource.contains("23:00换日")||BaziInputContext.timeBasis(savedSource).equals("apparent_solar")||(savedPlace!=null&&!savedPlace.isEmpty()));
        advanced.setVisibility(showAdvanced?View.VISIBLE:View.GONE);if(showAdvanced)advancedToggle.setText("收起高级设置");
        advancedToggle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){boolean open=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(open?View.VISIBLE:View.GONE);advancedToggle.setText(open?"收起高级设置":"查看时间口径和出生地");}});profile.addView(advancedToggle);
        advanced.addView(text("换日口径",14,true));dayBoundary=new Spinner(this);dayBoundary.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"晚子时按当天（23:00不换日）","子初换日（23:00换日）"}));dayBoundary.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){updateSolarPreview();}public void onNothingSelected(android.widget.AdapterView<?> p){}});advanced.addView(dayBoundary,new LinearLayout.LayoutParams(-1,dp(52)));
        birthplace=new EditText(this);JianYiUi.styleInput(birthplace);birthplace.setHint("出生地（选填，例如：浙江杭州）");birthplace.setSingleLine(true);advanced.addView(birthplace,new LinearLayout.LayoutParams(-1,dp(56)));
        advanced.addView(text("排盘时间",14,true));solarMode=new Spinner(this);solarMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"使用标准北京时间","按经度校正真太阳时"}));advanced.addView(solarMode,new LinearLayout.LayoutParams(-1,dp(52)));
        longitude=new EditText(this);JianYiUi.styleInput(longitude);longitude.setHint("填写出生地经度，例如杭州 120.16");longitude.setSingleLine(true);longitude.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);advanced.addView(longitude,new LinearLayout.LayoutParams(-1,dp(56)));
        final TextView solarHelp=text("选用真太阳时时，请填写出生地经度。出生地文字不会自动换算经度；接近时辰或节气交接时，两种时间口径可能影响排盘。",13,false);advanced.addView(solarHelp);advanced.addView(text("本版按输入的北京时间计算；海外出生或涉及历史夏令时时，请先确认并换算时间。",13,false));
        solarMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){longitude.setVisibility(pos==1?View.VISIBLE:View.GONE);solarHelp.setVisibility(pos==1?View.VISIBLE:View.GONE);updateSolarPreview();}public void onNothingSelected(android.widget.AdapterView<?> p){}});longitude.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updateSolarPreview();}public void afterTextChanged(android.text.Editable e){}});profile.addView(advanced);content.addView(profile);
        content.addView(button("使用此公历时间排盘",true,new View.OnClickListener(){public void onClick(View v){showSolarChart();}}));
        if(savedSource!=null&&savedSource.contains("23:00换日"))dayBoundary.setSelection(1);
        if("男".equals(savedGender))gender.setSelection(1);else if("女".equals(savedGender))gender.setSelection(2);
        if(savedPlace!=null)birthplace.setText(savedPlace);
        if(BaziInputContext.timeBasis(savedSource).equals("apparent_solar")){java.util.regex.Matcher lm=java.util.regex.Pattern.compile("经度\\s*(-?\\d+(?:\\.\\d+)?)").matcher(savedSource);if(lm.find()){longitude.setText(lm.group(1));solarMode.setSelection(1);}}
        content.addView(button("改用手动四柱",false,new View.OnClickListener(){public void onClick(View v){showEntry();}}));if(restored)syncDateInput();else dateInput.setText("");updateSolarPreview();dateInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){String current=String.format(Locale.CHINA,"%04d-%02d-%02d",solar.get(Calendar.YEAR),solar.get(Calendar.MONTH)+1,solar.get(Calendar.DAY_OF_MONTH));if(!current.equals(s.toString()))solarPreview.setText("日期已修改。点击“确认输入日期”刷新预览；点击排盘时也会自动确认。");}public void afterTextChanged(android.text.Editable e){}});
    }
    private void syncDateInput(){if(dateInput!=null)dateInput.setText(String.format(Locale.CHINA,"%04d-%02d-%02d",solar.get(Calendar.YEAR),solar.get(Calendar.MONTH)+1,solar.get(Calendar.DAY_OF_MONTH)));}
    private boolean applyDate(boolean notify){try{String raw=dateInput.getText().toString().trim();String[] p=raw.split("[-/.]");if(p.length!=3)throw new IllegalArgumentException();int y=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]),d=Integer.parseInt(p[2]);if(y<1900||y>2100)throw new IllegalArgumentException();Calendar check=(Calendar)solar.clone();check.setLenient(false);check.set(Calendar.DAY_OF_MONTH,1);check.set(Calendar.YEAR,y);check.set(Calendar.MONTH,m-1);check.set(Calendar.DAY_OF_MONTH,d);check.getTime();solar.set(Calendar.DAY_OF_MONTH,1);solar.set(y,m-1,d);syncDateInput();updateSolarPreview();if(notify)android.widget.Toast.makeText(this,"日期已应用",android.widget.Toast.LENGTH_SHORT).show();return true;}catch(Exception e){android.widget.Toast.makeText(this,"请输入1900至2100年的有效日期，例如 1992-08-20。",android.widget.Toast.LENGTH_LONG).show();return false;}}
    private void applyTypedDate(){applyDate(true);}
    private int sect(){return dayBoundary!=null&&dayBoundary.getSelectedItemPosition()==1?1:2;}
    private String boundaryLabel(){return sect()==1?"子初换日（23:00换日）":"晚子时按当天（23:00不换日）";}
    private BaziEngine.SolarAdjustment adjustment(){if(solarMode==null||solarMode.getSelectedItemPosition()==0)return null;return BaziEngine.solarTime(solar.get(Calendar.YEAR),solar.get(Calendar.MONTH)+1,solar.get(Calendar.DAY_OF_MONTH),solar.get(Calendar.HOUR_OF_DAY),solar.get(Calendar.MINUTE),Double.parseDouble(longitude.getText().toString().trim()));}
    private void updateSolarPreview(){if(solarPreview==null)return;try{int y=solar.get(Calendar.YEAR),m=solar.get(Calendar.MONTH)+1,d=solar.get(Calendar.DAY_OF_MONTH),h=solar.get(Calendar.HOUR_OF_DAY),minute=solar.get(Calendar.MINUTE);if(dateInput!=null&&!String.format(Locale.CHINA,"%04d-%02d-%02d",y,m,d).equals(dateInput.getText().toString())){solarPreview.setText("请填写出生日期并应用；点击排盘时也会自动应用。");return;}if(!timeConfirmed){solarPreview.setText("日期已选择，请确认出生时分后查看四柱预览。");return;}BaziEngine.SolarAdjustment a=adjustment();BaziEngine.Chart c=BaziEngine.fromSolarAdjusted(y,m,d,h,minute,sect(),a);String adjust=a==null?"\n使用北京时间":"\n"+a.detail();solarPreview.setText(String.format(Locale.CHINA,"北京时间 %04d-%02d-%02d %02d:%02d%s\n四柱预览　%s　%s　%s　%s\n换日　%s",y,m,d,h,minute,adjust,c.pillars[0],c.pillars[1],c.pillars[2],c.pillars[3],boundaryLabel()));}catch(Exception e){solarPreview.setText("请填写有效出生地经度（-180 至 180），再查看校时预览。");}}
    private void showSolarChart(){if(!applyDate(false))return;if(!timeConfirmed){android.widget.Toast.makeText(this,"请先选择并确认出生时分。",android.widget.Toast.LENGTH_LONG).show();return;}try{int y=solar.get(Calendar.YEAR),m=solar.get(Calendar.MONTH)+1,d=solar.get(Calendar.DAY_OF_MONTH),h=solar.get(Calendar.HOUR_OF_DAY),minute=solar.get(Calendar.MINUTE);BaziEngine.SolarAdjustment a=adjustment();String source=String.format(Locale.CHINA,"北京时间 %04d-%02d-%02d %02d:%02d · %s · 年月按北京时间节气，日时按所选时间 · %s",y,m,d,h,minute,a==null?"未校正真太阳时":a.detail(),boundaryLabel());String g=String.valueOf(gender.getSelectedItem()),fortune="";if(gender.getSelectedItemPosition()>0)fortune=BaziEngine.fortune(y,m,d,h,minute,gender.getSelectedItemPosition()==1?1:0,sect()).text();renderChart(BaziEngine.fromSolarAdjusted(y,m,d,h,minute,sect(),a),source,g,birthplace.getText().toString().trim(),fortune);}catch(Exception e){android.widget.Toast.makeText(this,"排盘未完成，请检查日期、时间和经度。",android.widget.Toast.LENGTH_LONG).show();}}
    private void showEntry(){screen="manual";page("手动四柱录入");content.addView(text("按年、月、日、时依次选择已核对的四柱；本功能用于研习，不会反推出生时间。",15,false));
        String[] labels={"年柱","月柱","日柱","时柱"};String[] cycle=BaziEngine.cycle();
        for(int i=0;i<4;i++){LinearLayout row=card(labels[i]);Spinner s=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,cycle);s.setAdapter(adapter);s.setSelection(new int[]{0,2,4,6}[i]);pillars[i]=s;row.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));content.addView(row);}
        content.addView(button("生成本机排盘",true,new View.OnClickListener(){public void onClick(View v){showChart();}}));
    }
    private void showChart(){try{final BaziEngine.Chart chart=BaziEngine.chart(value(0),value(1),value(2),value(3));String warning=BaziInputContext.manualWarnings(chart.pillars);if(!warning.isEmpty()){JianYiUi.showDialog(this,JianYiUi.dialogBuilder(this).setTitle("请核对手动四柱").setMessage(warning+"\n\n若这是你有意录入的研习盘，可以继续；也可返回修改。").setNegativeButton("返回修改",null).setPositiveButton("仍然继续",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){showManualResult(chart);}}).create());return;}showManualResult(chart);}catch(Exception e){android.widget.Toast.makeText(this,"四柱未能生成，请重新选择。",android.widget.Toast.LENGTH_LONG).show();}}
    private void showManualResult(BaziEngine.Chart chart){try{renderChart(chart,"手动四柱 · 请自行确认历法与校时口径","性别不指定","");}catch(Exception e){android.widget.Toast.makeText(this,"四柱未能生成，请重新选择。",android.widget.Toast.LENGTH_LONG).show();}}
    private void renderChart(final BaziEngine.Chart c,final String source,final String genderValue,final String place)throws Exception{renderChart(c,source,genderValue,place,"");}
    private void renderChart(final BaziEngine.Chart c,final String source,final String genderValue,final String place,final String fortune)throws Exception{shownChart=c;shownSource=source;shownGender=genderValue;shownPlace=place;shownFortune=fortune;screen="result";saveLast(c,source,genderValue,place,fortune);page("八字初步解读");content.addView(JianYiUi.hero(this,"先看懂，再深究","白话结论 · 具体问题 · 专业依据"));
        LinearLayout input=card("排盘资料");input.addView(text(source,14,false));if(BaziInputContext.needsSolarRecalculation(source))input.addView(text("这是旧版真太阳时排盘。由于节气交接算法已调整，建议重新输入出生时间和经度排盘，尤其是出生在节气交接附近时。",14,true));if(place.length()>0)input.addView(text("出生地 "+place,13,false));if(!genderValue.startsWith("性别不指定"))input.addView(text("性别 "+genderValue+" · 用于确定大运顺逆",13,false));content.addView(input);
        LinearLayout plate=card("你的四柱");
        plate.addView(text("从左到右：年、月、日、时",14,false));
        LinearLayout pillarsRow=new LinearLayout(this);pillarsRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"年柱","月柱","日柱","时柱"};
        for(int i=0;i<4;i++)pillarsRow.addView(pillarCell(names[i],c.pillars[i],i==2),pillarWeight(i));
        plate.addView(pillarsRow);
        plate.addView(text("每柱上方是天干，下方是地支。朱色标记的“"+c.dayMaster+"”是日主，观察其他字时以它为参照。",14,false));content.addView(plate);
        final org.json.JSONObject luckData=BaziLuckContext.build(c,source,genderValue,fortune);LinearLayout luck=card("起运与大运");
        if(luckData.optBoolean("available")){org.json.JSONObject current=luckData.optJSONObject("current_cycle");luck.addView(text("首次交运  "+luckData.optString("first_start_time"),15,true));if(current!=null&&"within_cycle".equals(current.optString("status")))luck.addView(text("当前大运  "+current.optString("ganzhi")+"\n"+current.optString("start_time")+" 至 "+current.optString("next_start_time")+"前",15,false));else if(current!=null)luck.addView(text(current.optString("description"),14,false));luck.addView(button("查看大运时间轴",false,new View.OnClickListener(){public void onClick(View v){showLuckTimeline(c,source,genderValue,place,luckData);}}));}
        else{luck.addView(text(luckData.optString("reason","请补充出生资料后重新排盘。"),14,false));luck.addView(button("补充出生资料",false,new View.OnClickListener(){public void onClick(View v){showSolarEntry(source,genderValue,place);}}));}content.addView(luck);
        LinearLayout plain=card("先认你的命盘");plain.addView(text("“"+c.dayMaster+"”是你的日主：它位于日柱上方，是观察其他字的参照点。",15,true));plain.addView(text("下方是命盘中的几条线索；传统解读需要结合整体，不能只凭一个字下结论。",14,false));content.addView(plain);
        content.addView(button("问 AI 一个具体问题",true,new View.OnClickListener(){public void onClick(View v){BaziAiActivity.open(BaziActivity.this,c,source,genderValue,place);}}));
        final List<BaziEngine.Insight> insights=BaziEngine.insights(c);LinearLayout clues=card("从哪里看起");for(BaziEngine.Insight insight:insights){TextView label=text(insight.title,15,true);label.setTextColor(JianYiUi.ACCENT);clues.addView(label);clues.addView(text(insight.summary,15,false));clues.addView(text(insight.meaning,13,false));}content.addView(clues);
        LinearLayout example=card("联系日常生活");example.addView(text(exampleForGod(c.monthGod),15,false));example.addView(text("这只是观察示例，请结合实际情况。",13,false));content.addView(example);
        LinearLayout needed=card("接下来怎么看");needed.addView(text("可以带着具体问题继续了解；本机核对通过的大运也会提供给 AI。",14,false));content.addView(needed);
        content.addView(button("查看四柱与专业依据",false,new View.OnClickListener(){public void onClick(View v){try{showProfessional(c,source,genderValue,place,fortune);}catch(Exception e){}}}));
        content.addView(button("重新选择公历时间",false,new View.OnClickListener(){public void onClick(View v){showSolarEntry(source,genderValue,place);}}));
        content.addView(button("手动校正四柱",false,new View.OnClickListener(){public void onClick(View v){showEntry();}}));
    }
    private void showLuckTimeline(final BaziEngine.Chart c,final String source,final String genderValue,final String place,final org.json.JSONObject data){
        screen="luck";page("大运时间轴");content.addView(JianYiUi.hero(this,"大运时间轴","按计算的交运时刻切换"));
        LinearLayout introduction=card("如何看这条时间轴");introduction.addView(text("大运是传统排盘中按约十年划分的时间段。下方只列时间和干支，不能单凭某一步断定吉凶。",14,false));introduction.addView(text("首次交运  "+data.optString("first_start_time")+" · "+data.optString("direction"),15,true));introduction.addView(text("以北京时间显示；不同排盘口径可能产生差异。点击任一时间段，可进一步提问。",13,false));content.addView(introduction);
        org.json.JSONObject current=data.optJSONObject("current_cycle");int selected=current!=null&&"within_cycle".equals(current.optString("status"))?current.optInt("index"):-1;if(current!=null&&selected<0)content.addView(text(current.optString("description"),14,false));
        org.json.JSONArray cycles=data.optJSONArray("cycles");if(cycles!=null)for(int i=0;i<cycles.length();i++){final org.json.JSONObject cycle=cycles.optJSONObject(i);if(cycle==null)continue;final String ganzhi=cycle.optString("ganzhi"),start=cycle.optString("start_time"),end=cycle.optString("next_start_time");boolean now=cycle.optInt("index")==selected;LinearLayout row=card((now?"当前 · ":"")+"第 "+cycle.optInt("index")+" 步  "+ganzhi);row.addView(text(start+"\n至 "+end+" 前",14,now));row.addView(text("点击了解这一运 ›",13,false));row.setClickable(true);row.setFocusable(true);row.setContentDescription((now?"当前大运，":"")+ganzhi+"，"+start+"至"+end+"前，点击了解");row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){JianYiUi.showDialog(BaziActivity.this,JianYiUi.dialogBuilder(BaziActivity.this).setTitle(ganzhi+"大运").setMessage("时间："+start+" 至 "+end+"前（北京时间）。\n\n这是一段传统排盘的时间标记。你可以结合自己的具体问题，请 AI 参考已提供的命盘资料讨论。").setNegativeButton("关闭",null).setPositiveButton("问 AI 这一运",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){BaziAiActivity.open(BaziActivity.this,c,source,genderValue,place,"请结合我的命盘，聊聊 "+ganzhi+" 大运（"+start+" 至 "+end+"前）。有哪些值得结合实际情况观察的方面？");}}).create());}});content.addView(row);}
    }
    private String plainTheme(String god){if("比肩".equals(god))return "自主选择、同伴关系与边界";if("劫财".equals(god))return "竞争合作与资源分配";if("食神".equals(god))return "技能输出、表达与生活节奏";if("伤官".equals(god))return "表达创新，以及如何与规则相处";if("正财".equals(god))return "稳定执行、现实安排与资源管理";if("偏财".equals(god))return "机会判断、资源调度与行动效率";if("正官".equals(god))return "责任、秩序与长期规则";if("七杀".equals(god))return "压力、行动力与约束的平衡";if("正印".equals(god))return "学习积累、支持系统与安全感";return "独立学习、信息吸收与方法选择";}
    private String exampleForGod(String god){if("比肩".equals(god)||"劫财".equals(god))return "例如和别人合作时，可以先写下：哪些事自己决定，哪些事需要共同商量；再核对任务和资源怎么分。";if("食神".equals(god)||"伤官".equals(god))return "例如准备展示一个新想法时，可以先做小样给熟悉的人看，再根据反馈调整表达和节奏。";if("正财".equals(god)||"偏财".equals(god))return "例如面对一项工作或新机会时，可以把所需时间、已有资源和最担心的问题写下来，再比较不同选择。";if("正官".equals(god)||"七杀".equals(god))return "例如同时承担几项任务时，可以先区分必须完成的事项和可以协商的事项，再安排优先级。";return "例如想学习新知识时，可以先选择一个小目标，比较独自研究和请人指导各自需要的时间。";}
    private void addInsight(final BaziEngine.Insight insight){LinearLayout c=card(insight.title);c.addView(text(insight.summary,16,true));c.addView(text(insight.meaning,14,false));final LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);detail.setVisibility(View.GONE);detail.addView(text("排盘依据　"+insight.evidence,13,true));detail.addView(text("判断边界　"+insight.boundary,13,false));final Button toggle=button("查看判断依据",false,null);toggle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){boolean open=detail.getVisibility()!=View.VISIBLE;detail.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"收起判断依据":"查看判断依据");}});c.addView(toggle);c.addView(detail);content.addView(c);}
    private void saveLast(BaziEngine.Chart c,String source,String genderValue,String place,String fortune){BaziArchive.migrateRecent(this);BaziArchive.save(this,c,source,genderValue,place,fortune);android.content.SharedPreferences.Editor e=getSharedPreferences("jianyi_bazi_last_v1",MODE_PRIVATE).edit();for(int i=0;i<4;i++)e.putString("p"+i,c.pillars[i]);e.putString("source",source).putString("gender",genderValue).putString("place",place).putString("fortune",fortune).apply();}
    private void showArchive(){screen="archive";page("我的命盘");content.addView(JianYiUi.hero(this,"我的命盘","按出生资料保存 · 可随卦例备份"));try{List<BaziArchive.Record> records=BaziArchive.list(this);if(records.isEmpty())content.addView(text("还没有保存的命盘。完成一次排盘后会自动保存在这里。",15,false));for(final BaziArchive.Record r:records){LinearLayout row=card(r.label.isEmpty()?"未命名命盘":r.label);row.addView(text("四柱　"+r.pillars[0]+"　"+r.pillars[1]+"　"+r.pillars[2]+"　"+r.pillars[3],16,true));row.addView(text(r.source,14,false));if(!r.place.isEmpty())row.addView(text("出生地　"+r.place,13,false));if(!r.gender.startsWith("性别不指定")&&!r.gender.isEmpty())row.addView(text("性别　"+r.gender,13,false));row.addView(button("查看命盘",true,new View.OnClickListener(){public void onClick(View v){try{resultFromArchive=true;renderChart(BaziEngine.chart(r.pillars[0],r.pillars[1],r.pillars[2],r.pillars[3]),r.source,r.gender,r.place,r.fortune);}catch(Exception e){android.widget.Toast.makeText(BaziActivity.this,"命盘暂时无法打开，原记录仍保留。",android.widget.Toast.LENGTH_LONG).show();}}}));row.addView(button("设置称呼",false,new View.OnClickListener(){public void onClick(View v){renameChart(r);}}));content.addView(row);}}catch(Exception e){content.addView(text("命盘列表暂时无法读取，原记录仍保留。",15,false));}}
    private void renameChart(final BaziArchive.Record r){final EditText name=new EditText(this);JianYiUi.styleInput(name);name.setSingleLine(true);name.setHint("例如：自己、家人");name.setText(r.label);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});JianYiUi.showDialog(this,JianYiUi.dialogBuilder(this).setTitle("给命盘设置称呼").setView(name).setNegativeButton("取消",null).setPositiveButton("保存",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){try{BaziArchive.rename(BaziActivity.this,r.id,name.getText().toString());showArchive();}catch(Exception e){android.widget.Toast.makeText(BaziActivity.this,"称呼未能保存，原命盘仍保留。",android.widget.Toast.LENGTH_LONG).show();}}}).create());}
    private boolean hasLast(){return getSharedPreferences("jianyi_bazi_last_v1",MODE_PRIVATE).contains("p0");}
    private String lastSource(){return getSharedPreferences("jianyi_bazi_last_v1",MODE_PRIVATE).getString("source","已保存四柱");}
    private BaziEngine.Chart lastChart(){android.content.SharedPreferences p=getSharedPreferences("jianyi_bazi_last_v1",MODE_PRIVATE);return BaziEngine.chart(p.getString("p0",""),p.getString("p1",""),p.getString("p2",""),p.getString("p3",""));}
    private void openLast(String mode){if(!hasLast()){android.widget.Toast.makeText(this,"请先完成一次排盘。",android.widget.Toast.LENGTH_SHORT).show();return;}try{android.content.SharedPreferences p=getSharedPreferences("jianyi_bazi_last_v1",MODE_PRIVATE);BaziEngine.Chart c=lastChart();String s=p.getString("source","已保存四柱"),g=p.getString("gender","性别不指定"),place=p.getString("place",""),fortune=p.getString("fortune","");if("ai".equals(mode))BaziAiActivity.open(this,c,s,g,place);else if("pro".equals(mode))showProfessional(c,s,g,place,fortune);else renderChart(c,s,g,place,fortune);}catch(Exception e){android.widget.Toast.makeText(this,"最近命盘无法读取，请重新排盘。",android.widget.Toast.LENGTH_LONG).show();}}
    private void showProfessional(final BaziEngine.Chart c,final String source,final String genderValue,final String place)throws Exception{showProfessional(c,source,genderValue,place,"");}
    private void showProfessional(final BaziEngine.Chart c,final String source,final String genderValue,final String place,final String fortune)throws Exception{shownChart=c;shownSource=source;shownGender=genderValue;shownPlace=place;shownFortune=fortune;screen="professional";page("专业依据");content.addView(JianYiUi.hero(this,"四柱与依据",c.summary()));LinearLayout table=card("十神与藏干");String[] pos={"年柱","月柱","日柱","时柱"};for(int i=0;i<4;i++)table.addView(text(pos[i]+"　"+c.pillars[i]+"　"+c.stemGods[i]+"\n藏干　"+c.hiddenGods[i],15,i==2));content.addView(table);LinearLayout structure=card("五行与干支关系");structure.addView(text("表层五行　"+c.elementSummary,16,true));structure.addView(text("只统计四个天干与四个地支主五行，不含藏干权重，也不作为旺衰评分。",13,false));for(String relation:c.relationships)structure.addView(text("• "+relation,14,false));content.addView(structure);final org.json.JSONObject verifiedLuck=BaziLuckContext.build(c,source,genderValue,fortune);if(verifiedLuck.optBoolean("available")){LinearLayout luck=card("起运与大运");luck.addView(text("首次交运："+verifiedLuck.optString("first_start_time")+" · "+verifiedLuck.optString("direction"),14,true));luck.addView(text("按本机选用的口径计算；交运时刻和完整八步大运请查看时间轴。",13,false));luck.addView(button("查看大运时间轴",false,new View.OnClickListener(){public void onClick(View v){showLuckTimeline(c,source,genderValue,place,verifiedLuck);}}));content.addView(luck);}LinearLayout basis=card("月令入口");basis.addView(text("月支 "+c.pillars[1].substring(1)+" · 本气 "+c.monthMain+" · 相对日主为「"+c.monthGod+"」",17,true));basis.addView(text("这里用于核对分析依据，不直接等同于人生结论。",14,false));content.addView(basis);List<BaziEngine.Rule> rules=BaziEngine.rules(this,c);for(BaziEngine.Rule r:rules){LinearLayout rc=card(r.id+" · "+r.title);rc.addView(text(r.text,14,false));rc.addView(text("出处："+r.source,12,false));content.addView(rc);}content.addView(button("返回白话解读",true,new View.OnClickListener(){public void onClick(View v){try{renderChart(c,source,genderValue,place,fortune);}catch(Exception e){}}}));}
    private String value(int i){return String.valueOf(pillars[i].getSelectedItem());}
    /** A small diagram uses ordinary text views so it remains selectable by accessibility services. */
    private LinearLayout fourPillarGuide(){
        LinearLayout c=card("一图认识四柱");
        c.addView(text("出生的年、月、日、时，各用一组干支表示。",14,false));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"年柱","月柱","日柱","时柱"};
        for(int i=0;i<4;i++)row.addView(pillarCell(names[i],null,i==2),pillarWeight(i));
        c.addView(row);c.addView(text("每柱上下两个字，合称八字；日柱上方的字叫日主。",13,false));return c;
    }
    private LinearLayout.LayoutParams pillarWeight(int index){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.topMargin=dp(8);p.bottomMargin=dp(8);if(index>0)p.leftMargin=dp(4);return p;
    }
    private LinearLayout pillarCell(String label,String pair,boolean day){
        LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(2),dp(8),dp(2),dp(8));
        android.graphics.drawable.GradientDrawable paper=new android.graphics.drawable.GradientDrawable();paper.setColor(day?0xfff8eee8:0xfff6f2e9);paper.setCornerRadius(dp(6));paper.setStroke(dp(1),day?0xffc89385:0xffded5c8);cell.setBackground(paper);
        TextView heading=text(label,13,true);heading.setGravity(Gravity.CENTER);heading.setTextColor(day?0xff9b463b:0xff52635a);cell.addView(heading);
        if(pair!=null&&pair.length()>=2){
            TextView stem=text(pair.substring(0,1),25,true);stem.setGravity(Gravity.CENTER);stem.setTypeface(android.graphics.Typeface.SERIF,android.graphics.Typeface.BOLD);if(day)stem.setTextColor(0xffa74436);cell.addView(stem);
            TextView branch=text(pair.substring(1,2),25,true);branch.setGravity(Gravity.CENTER);branch.setTypeface(android.graphics.Typeface.SERIF,android.graphics.Typeface.BOLD);cell.addView(branch);
            TextView caption=text(day?"日主在上":"天干 · 地支",11,false);caption.setGravity(Gravity.CENTER);caption.setTextColor(day?0xff9b463b:0xff61716a);cell.addView(caption);
            cell.setContentDescription(label+"，天干"+pair.substring(0,1)+"，地支"+pair.substring(1,2)+(day?"，天干为日主":""));
        }else{
            TextView stem=text(day?"日主":"天干",14,day);stem.setGravity(Gravity.CENTER);if(day)stem.setTextColor(0xffa74436);cell.addView(stem);
            TextView branch=text("地支",14,false);branch.setGravity(Gravity.CENTER);cell.addView(branch);
        }
        return cell;
    }
    private LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));JianYiUi.surface(c);if(title!=null)c.addView(text(title,18,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);c.setLayoutParams(p);return c;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(JianYiUi.INK);t.setLineSpacing(0,1.2f);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,1);t.setPadding(0,dp(3),0,dp(3));return t;}
    private Button button(String s,boolean primary,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setAllCaps(false);b.setOnClickListener(l);JianYiUi.skinButton(b,primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(8);b.setLayoutParams(p);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onBackPressed(){if("home".equals(screen)){finish();return;}if(("professional".equals(screen)||"luck".equals(screen)||("entry".equals(screen)&&entryFromResult))&&shownChart!=null){try{renderChart(shownChart,shownSource,shownGender,shownPlace,shownFortune);return;}catch(Exception ignored){}}if("result".equals(screen)&&resultFromArchive){showArchive();return;}showHome();}

    private static final class RelationView extends View {
        Paint p=new Paint(1);String[] names={"木","火","土","金","水"};int[] colors={0xff3d8b68,0xffc65348,0xffb78a43,0xff8a8f94,0xff3d70a8};
        RelationView(Context c){super(c);setMinimumHeight(Math.round(280*c.getResources().getDisplayMetrics().density));}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,r=Math.min(w,h)*.34f;p.setStrokeWidth(4);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.min(w,h)*.075f);
            float[] x=new float[5],y=new float[5];for(int i=0;i<5;i++){double a=-Math.PI/2+i*Math.PI*2/5;x[i]=cx+(float)Math.cos(a)*r;y[i]=cy+(float)Math.sin(a)*r;}
            p.setStyle(Paint.Style.STROKE);p.setColor(0xff789088);for(int i=0;i<5;i++)c.drawLine(x[i],y[i],x[(i+1)%5],y[(i+1)%5],p);p.setStrokeWidth(2);p.setColor(0xffb27868);for(int i=0;i<5;i++)c.drawLine(x[i],y[i],x[(i+2)%5],y[(i+2)%5],p);
            p.setStyle(Paint.Style.FILL);for(int i=0;i<5;i++){p.setColor(colors[i]);c.drawCircle(x[i],y[i],Math.min(w,h)*.09f,p);p.setColor(0xffffffff);c.drawText(names[i],x[i],y[i]-((p.ascent()+p.descent())/2),p);}p.setColor(JianYiUi.MUTED);p.setTextSize(Math.min(w,h)*.047f);c.drawText("外圈相生 · 内线相克",cx,cy-((p.ascent()+p.descent())/2),p);}
    }
}
