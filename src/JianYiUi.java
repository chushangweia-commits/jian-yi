package com.jianyi.yixue.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;

/** Small native design primitives for 简易. No assets, network or additional permissions. */
public final class JianYiUi {
    public static final int PAPER = 0xfff7f2e8;
    public static final int INK = 0xff302a25;
    public static final int ACCENT = 0xff984e40;
    public static final int GOLD = 0xffa88a5b;
    public static final int MUTED = 0xff716154;
    public static final int LINE = 0xffe4d8c6;
    public static final int WHITE = 0xffffffff;
    public static final int SOFT = 0xfff1e9dc;
    public static final int SCENE_HOME = 1;
    public static final int SCENE_READING = 2;
    public static final int SCENE_DIVINATION = 3;
    public static final int SCENE_BAZI = 4;
    private static final int[] PRESSED = {android.R.attr.state_pressed};
    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private static final int[] DEFAULT = {};

    private JianYiUi() { }

    /** These pages use normal system bars, without the original activity's immersive flags. */
    public static void applySystemBars(Activity activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().setStatusBarColor(PAPER);
            activity.getWindow().setNavigationBarColor(WHITE);
        }
        int appearance = 0;
        if (Build.VERSION.SDK_INT >= 23) appearance |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) appearance |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(appearance);
        if (Build.VERSION.SDK_INT >= 30 && activity.getWindow().getInsetsController() != null) {
            int lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            activity.getWindow().getInsetsController().setSystemBarsAppearance(lightBars, lightBars);
            activity.getWindow().getInsetsController().show(android.view.WindowInsets.Type.systemBars());
        }
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Compact, naturally measured header: left text, right decorative 92 dp emblem. */
    public static View hero(Activity activity, String title, String subtitle) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 4), dp(activity, 12), dp(activity, 2), dp(activity, 16));
        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        row.addView(textColumn, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String heading = title == null || title.trim().length() == 0 ? "简易" : title;
        TextView headingView = new TextView(activity);
        headingView.setText(heading);
        headingView.setTextSize("简易".equals(heading) ? 30f : 24f);
        headingView.setTextColor(INK);
        headingView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        headingView.setIncludeFontPadding(false);
        headingView.setMaxLines(1);
        headingView.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) headingView.setAccessibilityHeading(true);
        textColumn.addView(headingView);
        if (subtitle != null && subtitle.trim().length() != 0) {
            TextView description = new TextView(activity);
            description.setText(subtitle);
            description.setTextSize(14f);
            description.setTextColor(MUTED);
            description.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            description.setLineSpacing(dp(activity, 3), 1f);
            description.setPadding(0, dp(activity, 8), dp(activity, 8), 0);
            description.setIncludeFontPadding(false);
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            textColumn.addView(description);
        }
        View symbol;
        if (heading.contains("五行") || heading.contains("体用") || heading.contains("八字")) {
            symbol = new KnowledgeMotifView(activity, "Wuxing");
        } else if (heading.contains("进阶") || heading.contains("笔记") || heading.contains("复盘")) {
            symbol = new KnowledgeMotifView(activity, "Luoshu");
        } else if (heading.contains("学堂") || heading.contains("学习") || heading.contains("入门")) {
            symbol = new KnowledgeMotifView(activity, "Hetu");
        } else {
            symbol = new TaijiView(activity);
        }
        LinearLayout.LayoutParams symbolParams = new LinearLayout.LayoutParams(
            dp(activity, 92), dp(activity, 92));
        symbolParams.leftMargin = dp(activity, 12);
        row.addView(symbol, symbolParams);
        return row;
    }

    /** Native ripple on Android 5+, state feedback on Android 4.4. */
    public static void skinButton(Button button, boolean primary) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        button.setTextColor(new ColorStateList(new int[][] {DISABLED, DEFAULT},
            new int[] {0xff87918a, primary ? WHITE : INK}));
        StateListDrawable states = new StateListDrawable();
        states.addState(DISABLED, rounded(context, 0xffe7eae3, LINE, 15f));
        states.addState(PRESSED, rounded(context, primary ? 0xff493c34 : 0xffefe3d4,
            primary ? 0 : LINE, 15f));
        states.addState(DEFAULT, rounded(context, primary ? INK : WHITE,
            primary ? 0 : LINE, 15f));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(
                primary ? 0x30ffffff : 0x22984e40), states,
                rounded(context, WHITE, 0, 15f)));
            button.setElevation(0f);
            button.setStateListAnimator(null);
        } else {
            button.setBackground(states);
        }
        pressFeedback(button);
    }

    public static void styleInput(EditText input) {
        Context context = input.getContext();
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
            rounded(context, WHITE, ACCENT, 12f));
        states.addState(DEFAULT, rounded(context, WHITE, LINE, 12f));
        input.setBackground(states);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setTextSize(16f);
        input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        input.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        input.setMinHeight(dp(context, 50));
        input.setMinimumHeight(dp(context, 50));
    }

    public static GradientDrawable cardBackground(Context context) {
        return rounded(context, 0xfafdfaf4, LINE, 16f);
    }

    /** Original low-contrast artwork, center-cropped and washed for reliable text contrast. */
    public static void applyScenic(View view,int scene) {
        String name=scene==SCENE_READING?"scene_reading":scene==SCENE_DIVINATION?"scene_divination":scene==SCENE_BAZI?"scene_bazi":"scene_home";
        int resource=view.getResources().getIdentifier(name,"drawable",view.getContext().getPackageName());
        int wash=scene==SCENE_DIVINATION?0xc4f7f2e8:scene==SCENE_READING?0xaff7f2e8:scene==SCENE_BAZI?0xbdf7f2e8:0xaaf7f2e8;
        view.setBackground(new ScenicDrawable(view.getContext(),resource,wash));
    }

    /** Subtle physical feedback for touch without changing click or accessibility behavior. */
    public static void pressFeedback(final View view){
        view.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,android.view.MotionEvent event){
            if(!v.isEnabled())return false;
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){
                v.animate().scaleX(.985f).scaleY(.985f).setDuration(120).setInterpolator(new PathInterpolator(.32f,.72f,0f,1f)).start();
            }else if(event.getAction()==android.view.MotionEvent.ACTION_UP||event.getAction()==android.view.MotionEvent.ACTION_CANCEL){
                v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new PathInterpolator(.32f,.72f,0f,1f)).start();
            }
            return false;
        }});
    }

    private static final class ScenicDrawable extends Drawable {
        private final Bitmap bitmap;private final Paint image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final Paint wash=new Paint();
        ScenicDrawable(Context context,int resource,int washColor){bitmap=BitmapFactory.decodeResource(context.getResources(),resource);wash.setColor(washColor);}
        @Override public void draw(Canvas canvas){Rect b=getBounds();if(bitmap==null||b.width()<=0||b.height()<=0){canvas.drawColor(PAPER);return;}float scale=Math.max(b.width()/(float)bitmap.getWidth(),b.height()/(float)bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;RectF dst=new RectF(b.centerX()-w/2f,b.centerY()-h/2f,b.centerX()+w/2f,b.centerY()+h/2f);int save=canvas.save();canvas.clipRect(b);canvas.drawBitmap(bitmap,null,dst,image);canvas.drawRect(b,wash);canvas.restoreToCount(save);}
        @Override public void setAlpha(int alpha){image.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){image.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }

    /** Adds only the card background, leaving caller layout/padding intact. */
    public static void surface(View view) {
        view.setBackground(cardBackground(view.getContext()));
    }

    public static void textStyle(TextView text, float sizeSp, boolean title) {
        text.setTextSize(sizeSp);
        text.setTextColor(INK);
        text.setTypeface(Typeface.create(title ? "serif" : "sans-serif", Typeface.NORMAL));
        text.setIncludeFontPadding(false);
    }

    public static GradientDrawable rounded(Context context, int color, int stroke, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) shape.setStroke(dp(context, 1), stroke);
        return shape;
    }

    /** Keeps native AlertDialog actions and dismissal behavior, with the same reading surface as the pages. */
    public static AlertDialog.Builder dialogBuilder(Activity activity) {
        return new ReadingDialogBuilder(activity);
    }

    public static AlertDialog showDialog(Activity activity, AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        window.setBackgroundDrawable(rounded(activity, PAPER, 0, 22f));
        int width = Math.min(dp(activity, 560),
            activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32));
        window.setLayout(Math.max(dp(activity, 240), width), WindowManager.LayoutParams.WRAP_CONTENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.36f;
        window.setAttributes(params);

        // Public resource lookup is optional: different Android versions use different panel names.
        String[] panels = {"parentPanel", "topPanel", "contentPanel", "customPanel", "custom", "buttonPanel"};
        for (String name : panels) {
            View panel = dialogPart(activity, dialog, name);
            if (panel != null) panel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        String[] dividers = {"titleDivider", "titleDividerTop", "titleDividerNoCustom", "textSpacerNoButtons"};
        for (String name : dividers) {
            View divider = dialogPart(activity, dialog, name);
            if (divider != null) divider.setVisibility(View.GONE);
        }
        View title = dialogPart(activity, dialog, "alertTitle");
        if (title instanceof TextView) {
            TextView heading = (TextView) title;
            textStyle(heading, 21f, true);
            heading.setSingleLine(false);
            heading.setMaxLines(Integer.MAX_VALUE);
            heading.setEllipsize(null);
            heading.setLineSpacing(dp(activity, 4), 1f);
        }
        boolean picker = dialog instanceof DatePickerDialog || dialog instanceof TimePickerDialog;
        if (!picker) {
            String[] edgePanels = {"topPanel", "contentPanel", "customPanel"};
            for (String name : edgePanels) {
                View panel = dialogPart(activity, dialog, name);
                if (panel != null) panel.setPadding(0, 0, 0, 0);
            }
            TextView message = (TextView) dialog.findViewById(android.R.id.message);
            if (message != null) {
                textStyle(message, 16f, false);
                message.setLineSpacing(dp(activity, 5), 1.05f);
                message.setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), dp(activity, 16));
            }
            View custom = dialogPart(activity, dialog, "custom");
            if (custom != null) custom.setPadding(0, 0, 0, 0);
            ListView list = dialog.getListView();
            if (list != null) {
                list.setBackgroundColor(WHITE);
                list.setSelector(new android.graphics.drawable.ColorDrawable(0x22984e40));
                list.setDivider(new android.graphics.drawable.ColorDrawable(LINE));
                list.setDividerHeight(dp(activity, 1));
            }
        }
        arrangeDialogButtons(activity, dialog, width);
        return dialog;
    }

    /** The spinner is local and uses the same palette on Android 4.4 and newer. */
    public static AlertDialog showProgressDialog(Activity activity, CharSequence message) {
        LinearLayout content = new LinearLayout(activity);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 24));
        ProgressBar spinner = new ProgressBar(activity);
        Drawable indicator = spinner.getIndeterminateDrawable();
        if (indicator != null) indicator.mutate().setColorFilter(ACCENT, PorterDuff.Mode.SRC_IN);
        content.addView(spinner, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
        TextView label = new TextView(activity);
        label.setText(message);
        textStyle(label, 16f, false);
        label.setLineSpacing(dp(activity, 4), 1f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(activity, 16);
        content.addView(label, textParams);
        AlertDialog dialog = dialogBuilder(activity).setView(content).setCancelable(false).create();
        return showDialog(activity, dialog);
    }

    private static View dialogPart(Activity activity, AlertDialog dialog, String name) {
        int id = activity.getResources().getIdentifier(name, "id", "android");
        return id == 0 ? null : dialog.findViewById(id);
    }

    private static void arrangeDialogButtons(Activity activity, AlertDialog dialog, int width) {
        View panel = dialogPart(activity, dialog, "buttonPanel");
        if (!(panel instanceof ViewGroup)) return;
        ArrayList<Button> buttons = new ArrayList<Button>();
        int[] order = {AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE};
        float needed = 0f;
        for (int which : order) {
            Button button = dialog.getButton(which);
            if (button == null || button.getVisibility() != View.VISIBLE) continue;
            skinButton(button, which == AlertDialog.BUTTON_POSITIVE);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setSingleLine(false);
            button.setMaxLines(Integer.MAX_VALUE);
            button.setEllipsize(null);
            needed += button.getPaint().measureText(button.getText().toString()) + dp(activity, 40);
            buttons.add(button);
        }
        if (buttons.isEmpty()) return;
        boolean horizontal = buttons.size() > 1
            && needed + dp(activity, 8 * (buttons.size() - 1)) <= width - dp(activity, 40);
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            if (button.getParent() instanceof ViewGroup) ((ViewGroup) button.getParent()).removeView(button);
            LinearLayout.LayoutParams layout = horizontal
                ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) {
                if (horizontal) layout.leftMargin = dp(activity, 8);
                else layout.topMargin = dp(activity, 8);
            }
            actions.addView(button, layout);
        }
        ViewGroup container = (ViewGroup) panel;
        container.removeAllViews();
        container.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 20));
        container.addView(actions, new ViewGroup.LayoutParams(-1, -2));
        // Legacy Holo uses a 48dp ScrollView for buttonPanel. Its old fixed height
        // cannot fit our 48dp buttons plus reading-surface padding.
        actions.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width - dp(activity, 40)),
            View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int actionHeight = actions.getMeasuredHeight() + container.getPaddingTop() + container.getPaddingBottom();
        ViewGroup.LayoutParams panelLayout = container.getLayoutParams();
        if (panelLayout != null) {
            panelLayout.height = actionHeight;
            if (panelLayout instanceof LinearLayout.LayoutParams)
                ((LinearLayout.LayoutParams) panelLayout).weight = 0f;
            container.setLayoutParams(panelLayout);
        }
        container.setMinimumHeight(actionHeight);
    }

    private static Drawable choiceIndicator(Context context) {
        GradientDrawable empty = new GradientDrawable();
        empty.setShape(GradientDrawable.OVAL);
        empty.setColor(WHITE);
        empty.setStroke(dp(context, 1.5f), MUTED);
        empty.setSize(dp(context, 22), dp(context, 22));
        GradientDrawable rim = new GradientDrawable();
        rim.setShape(GradientDrawable.OVAL);
        rim.setColor(WHITE);
        rim.setStroke(dp(context, 1.5f), INK);
        rim.setSize(dp(context, 22), dp(context, 22));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(INK);
        LayerDrawable selected = new LayerDrawable(new Drawable[] {rim, dot});
        int inset = dp(context, 6);
        selected.setLayerInset(1, inset, inset, inset, inset);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_checked}, selected);
        states.addState(DEFAULT, empty);
        return states;
    }

    private static final class ReadingDialogBuilder extends AlertDialog.Builder {
        private final Activity activity;
        ReadingDialogBuilder(Activity activity) { super(activity); this.activity = activity; }

        @Override public AlertDialog.Builder setTitle(CharSequence title) {
            super.setTitle(title);
            TextView heading = new TextView(activity);
            heading.setText(title);
            textStyle(heading, 21f, true);
            heading.setLineSpacing(dp(activity, 4), 1f);
            heading.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 16));
            heading.setSingleLine(false);
            heading.setEllipsize(null);
            if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
            super.setCustomTitle(heading);
            return this;
        }

        @Override public AlertDialog.Builder setTitle(int titleId) {
            return setTitle(activity.getText(titleId));
        }

        @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items, int checked,
            DialogInterface.OnClickListener listener) {
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(activity,
                android.R.layout.simple_list_item_single_choice, items) {
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    CheckedTextView item = (CheckedTextView) super.getView(position, recycled, parent);
                    textStyle(item, 16f, false);
                    item.setCheckMarkDrawable(choiceIndicator(activity));
                    item.setMinHeight(dp(activity, 52));
                    item.setSingleLine(false);
                    item.setPadding(dp(activity, 24), dp(activity, 10), dp(activity, 24), dp(activity, 10));
                    return item;
                }
            };
            super.setSingleChoiceItems(adapter, checked, listener);
            return this;
        }

        @Override public AlertDialog show() {
            return showDialog(activity, create());
        }
    }

    /** One 250 ms entrance. Content is immediately usable and stays still after entry. */
    public static void enter(View view) {
        view.animate().cancel();
        if (!animationsEnabled(view.getContext())) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(dp(view.getContext(), 8));
        view.animate().alpha(1f).translationY(0f).setDuration(250L)
            .setInterpolator(new DecelerateInterpolator()).start();
    }

    public static boolean animationsEnabled(Context context) {
        if (!context.getSharedPreferences("guest_visual_settings_v1", Context.MODE_PRIVATE)
            .getBoolean("animations_enabled", true)) return false;
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    /** Canvas emblem. The lines are stored top first: 1 solid (yang), 2 broken (yin). */
    public static final class TaijiView extends View {
        // Later-heaven arrangement, south above: 离、坤、兑、乾、坎、艮、震、巽.
        // Each trigram remains fixed in position. Lines are top/outer first.
        private static final String[] RING_LINES = {
            "121", "222", "211", "111", "212", "122", "221", "112"
        };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Rect visibleBounds = new Rect();
        private final ContentObserver scaleObserver;
        private ViewTreeObserver watchedTree;
        private final ViewTreeObserver.OnScrollChangedListener scrollListener =
            new ViewTreeObserver.OnScrollChangedListener() {
                @Override public void onScrollChanged() { syncAnimation(); }
            };
        private final ViewTreeObserver.OnGlobalLayoutListener layoutListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() { syncAnimation(); }
            };
        private ValueAnimator orbit;
        private boolean attached;
        private boolean observing;
        private float turn;
        private long lastFrameMillis;

        public TaijiView(Context context) {
            super(context);
            setContentDescription("后天八卦方位固定，中央太极缓慢转动；南上北下");
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setFocusable(false);
            setClickable(false);
            scaleObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { syncAnimation(); }
            };
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            watchedTree = getViewTreeObserver();
            watchedTree.addOnScrollChangedListener(scrollListener);
            watchedTree.addOnGlobalLayoutListener(layoutListener);
            try {
                getContext().getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    false, scaleObserver);
                observing = true;
            } catch (RuntimeException ignored) { observing = false; }
            syncAnimation();
        }

        @Override protected void onDetachedFromWindow() {
            attached = false;
            stopAnimation();
            if (watchedTree != null && watchedTree.isAlive()) {
                watchedTree.removeOnScrollChangedListener(scrollListener);
                watchedTree.removeOnGlobalLayoutListener(layoutListener);
            }
            watchedTree = null;
            if (observing) {
                try { getContext().getContentResolver().unregisterContentObserver(scaleObserver); }
                catch (RuntimeException ignored) { }
                observing = false;
            }
            super.onDetachedFromWindow();
        }

        @Override protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            syncAnimation();
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            syncAnimation();
        }

        private void syncAnimation() {
            if (!attached || !isShown() || getWindowVisibility() != VISIBLE
                || !getGlobalVisibleRect(visibleBounds)
                || !animationsEnabled(getContext())) {
                stopAnimation();
                return;
            }
            if (orbit != null) return;
            orbit = ValueAnimator.ofFloat(0f, 1f);
            orbit.setDuration(60000L);
            orbit.setRepeatCount(ValueAnimator.INFINITE);
            orbit.setInterpolator(new LinearInterpolator());
            orbit.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastFrameMillis < 33L) return;
                    lastFrameMillis = now;
                    float fraction = ((Float) animation.getAnimatedValue()).floatValue();
                    turn = 360f * fraction;
                    invalidate();
                }
            });
            orbit.start();
        }

        private void stopAnimation() {
            if (orbit != null) {
                orbit.removeAllUpdateListeners();
                orbit.cancel();
                orbit = null;
            }
            turn = 0f;
            lastFrameMillis = 0L;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
            if (size <= 0f) return;
            float unit = size / 92f;
            float centerX = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
            float centerY = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
            canvas.save();
            canvas.translate(centerX, centerY);
            canvas.scale(unit, unit);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.8f);
            paint.setColor(GOLD);
            canvas.drawCircle(0f, 0f, 44f, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.save();
            paint.setColor(INK);
            for (int symbol = 0; symbol < RING_LINES.length; symbol++) {
                canvas.save();
                canvas.rotate(symbol * 45f);
                String lines = RING_LINES[symbol];
                for (int line = 0; line < 3; line++) {
                    float top = -39f + line * 3.5f;
                    if (lines.charAt(line) == '1') {
                        canvas.drawRect(-6.5f, top, 6.5f, top + 1.7f, paint);
                    } else {
                        canvas.drawRect(-6.5f, top, -1.8f, top + 1.7f, paint);
                        canvas.drawRect(1.8f, top, 6.5f, top + 1.7f, paint);
                    }
                }
                canvas.restore();
            }
            canvas.restore();
            canvas.save();
            // Only the central taiji rotates; the outer ring and eight trigrams stay still.
            canvas.rotate(turn);
            float radius = 22f;
            oval.set(-radius, -radius, radius, radius);
            paint.setColor(INK);
            canvas.drawCircle(0f, 0f, radius, paint);
            paint.setColor(PAPER);
            canvas.drawArc(oval, -90f, 180f, true, paint);
            canvas.drawCircle(0f, -radius / 2f, radius / 2f, paint);
            paint.setColor(INK);
            canvas.drawCircle(0f, radius / 2f, radius / 2f, paint);
            canvas.drawCircle(0f, -radius / 2f, radius / 8f, paint);
            paint.setColor(PAPER);
            canvas.drawCircle(0f, radius / 2f, radius / 8f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.8f);
            paint.setColor(GOLD);
            canvas.drawCircle(0f, 0f, radius + 2f, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
            canvas.restore();
        }
    }
}
