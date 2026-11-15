package top.izuna.foliamajor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Android 原生设置页。
 *
 * 它不读任何存储，也不认识网页的 store：进门前网页把 schema（分区 + 行 + 当前值）塞进 Intent，
 * 这里按 schema 画控件；改一行就 {@link FoliaSettingsPlugin#postChange} 交回网页。
 * 所以这一页永远显示网页此刻的真值，网页那边一改（命令面板、同步、别的入口），下次进来即同步。
 *
 * 之所以用代码建 UI 而不是 XML：整页只有四种行（开关 / 选择 / 数字 / 动作），
 * schema 一变行就变，写死 XML 反而要跟着改动。
 */
public class NativeSettingsActivity extends AppCompatActivity {

    private static final String EXTRA_SCHEMA = "folia.settings.schema";
    private static final String EXTRA_TITLE = "folia.settings.title";

    private static final int COLOR_BG = 0xFF09090B;
    private static final int COLOR_CARD = 0xFF141418;
    private static final int COLOR_DIVIDER = 0xFF26262B;
    private static final int COLOR_TEXT = 0xFFFAFAFA;
    private static final int COLOR_SECONDARY = 0xFFA1A1AA;
    private static final int COLOR_ACCENT = 0xFF22D3EE;

    public static void start(Activity from, String schemaJson, String title) {
        Intent intent = new Intent(from, NativeSettingsActivity.class);
        intent.putExtra(EXTRA_SCHEMA, schemaJson);
        intent.putExtra(EXTRA_TITLE, title);
        from.startActivity(intent);
        // 网页那边是音乐播放页，原生页滑进来时不该把主界面推到后台再冷启动一次。
        from.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String raw = getIntent().getStringExtra(EXTRA_SCHEMA);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) title = "设置";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        root.addView(buildHeader(title));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(32));
        scroll.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildSections(content, raw);
        content.addView(buildWebEntry());

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        // 状态栏 / 手势条给内容让位。API 30 起按真实 insets 来；更老的系统用资源里的高度，
        // 不靠 fitsSystemWindows 猜（它和高版本 insets 一起用会叠两遍留白）。
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decor.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            decor.requestApplyInsets();
        } else {
            root.setPadding(0, systemBarDimen("status_bar_height"), 0, systemBarDimen("navigation_bar_height"));
        }
    }

    private View buildHeader(String title) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(8), dp(16), dp(8));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        back.setTextColor(COLOR_TEXT);
        back.setGravity(Gravity.CENTER);
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> finish());
        header.addView(back);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(COLOR_TEXT);
        header.addView(heading);
        return header;
    }

    private void buildSections(LinearLayout content, String raw) {
        JSONObject schema;
        try {
            schema = new JSONObject(raw);
        } catch (Exception error) {
            content.addView(buildEmpty("设置数据读不出来，请回到 App 里重新打开这一页"));
            return;
        }
        JSONArray sections = schema.optJSONArray("sections");
        JSONArray settings = schema.optJSONArray("settings");
        if (sections == null || settings == null) {
            content.addView(buildEmpty("设置数据不完整，请回到 App 里重新打开这一页"));
            return;
        }

        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) continue;
            String sectionId = section.optString("id");
            List<JSONObject> rows = new ArrayList<>();
            for (int j = 0; j < settings.length(); j++) {
                JSONObject row = settings.optJSONObject(j);
                if (row != null && sectionId.equals(row.optString("section"))) rows.add(row);
            }
            if (rows.isEmpty()) continue;

            TextView title = new TextView(this);
            title.setText(section.optString("title", sectionId));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            title.setTextColor(COLOR_SECONDARY);
            title.setLetterSpacing(0.08f);
            title.setPadding(dp(4), dp(20), dp(4), dp(8));
            content.addView(title);

            content.addView(buildGroup(rows));
        }
    }

    private View buildGroup(List<JSONObject> rows) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(roundedCard());

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) group.addView(buildDivider());
            group.addView(buildRow(rows.get(i)));
        }
        return group;
    }

    private View buildDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        divider.setLayoutParams(params);
        return divider;
    }

    private View buildRow(JSONObject row) {
        String kind = row.optString("kind", "action");
        String id = row.optString("id");
        String label = row.optString("label", id);
        String hint = row.optString("hint", "");

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        line.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(COLOR_TEXT);
        labels.addView(title);

        if (!hint.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(hint);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            sub.setTextColor(COLOR_SECONDARY);
            sub.setPadding(0, dp(4), 0, 0);
            labels.addView(sub);
        }

        Object value = row.opt("value");
        switch (kind) {
            case "toggle": {
                Switch toggle = new Switch(this);
                boolean on = value instanceof Boolean ? (Boolean) value : "true".equals(String.valueOf(value));
                toggle.setChecked(on);
                toggle.setOnCheckedChangeListener((button, checked) ->
                        FoliaSettingsPlugin.postChange(id, checked));
                line.addView(toggle);
                line.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
                break;
            }
            case "select": {
                JSONArray options = row.optJSONArray("options");
                TextView current = new TextView(this);
                current.setTextColor(COLOR_SECONDARY);
                current.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                current.setText(optionLabel(options, String.valueOf(value)));
                current.setPadding(dp(8), 0, dp(8), 0);
                line.addView(current);
                line.addView(buildChevron());
                line.setOnClickListener(v -> showSelect(id, label, options, current, String.valueOf(value)));
                break;
            }
            case "number": {
                TextView current = new TextView(this);
                current.setTextColor(COLOR_SECONDARY);
                current.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                current.setText(String.valueOf(value) + row.optString("unit", ""));
                current.setPadding(dp(8), 0, dp(8), 0);
                line.addView(current);
                line.addView(buildChevron());
                line.setOnClickListener(v -> showNumber(id, row, current, String.valueOf(value)));
                break;
            }
            default: {
                line.addView(buildChevron());
                break;
            }
        }
        return line;
    }

    private void showSelect(String id, String label, JSONArray options, TextView currentView, String currentValue) {
        if (options == null || options.length() == 0) return;
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        int checked = 0;
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option == null) continue;
            values.add(option.optString("value"));
            labels.add(option.optString("label", option.optString("value")));
            if (option.optString("value").equals(currentValue)) checked = values.size() - 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    String picked = values.get(which);
                    currentView.setText(labels.get(which));
                    FoliaSettingsPlugin.postChange(id, picked);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNumber(String id, JSONObject row, TextView currentView, String currentValue) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(currentValue);
        input.setSelection(input.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(24), dp(8), dp(24), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(row.optString("label", id))
                .setView(wrap)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    double parsed;
                    try {
                        parsed = Double.parseDouble(text);
                    } catch (NumberFormatException error) {
                        return;
                    }
                    double min = row.has("min") ? row.optDouble("min") : parsed;
                    double max = row.has("max") ? row.optDouble("max") : parsed;
                    if (parsed < min || parsed > max) return;
                    boolean whole = parsed == Math.rint(parsed);
                    Object value = whole ? (Object) (int) parsed : (Object) parsed;
                    currentView.setText(text + row.optString("unit", ""));
                    FoliaSettingsPlugin.postChange(id, value);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View buildWebEntry() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(roundedCard());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(24);
        group.setLayoutParams(params);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        line.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("网页版完整设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(COLOR_ACCENT);
        labels.addView(title);

        TextView sub = new TextView(this);
        sub.setText("可视化调音、模组、集成、调试等还没搬到这一页");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(COLOR_SECONDARY);
        sub.setPadding(0, dp(4), 0, 0);
        labels.addView(sub);

        line.addView(buildChevron());
        line.setOnClickListener(v -> {
            // 回网页：网页那边自己决定打开设置弹窗的哪一页（这里只关掉原生页）。
            setResult(Activity.RESULT_OK);
            finish();
        });
        group.addView(line);
        return group;
    }

    private TextView buildChevron() {
        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        chevron.setTextColor(COLOR_SECONDARY);
        chevron.setPadding(dp(8), 0, 0, 0);
        return chevron;
    }

    private TextView buildEmpty(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(COLOR_SECONDARY);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        empty.setPadding(dp(4), dp(24), dp(4), dp(24));
        return empty;
    }

    private static String optionLabel(JSONArray options, String value) {
        if (options == null) return value;
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option != null && value.equals(option.optString("value"))) {
                return option.optString("label", value);
            }
        }
        return value;
    }

    @NonNull
    private GradientDrawable roundedCard() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        drawable.setColor(COLOR_CARD);
        return drawable;
    }

    private int systemBarDimen(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
