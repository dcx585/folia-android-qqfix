package top.izuna.foliamajor;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

/**
 * FoliaSettings：把网页设置搬进一个真正的 Android 页面。
 *
 * 这个插件只做两件事：
 * 1. `open`：网页把设置 schema（分区 + 行 + 当前值）序列化成 JSON 递进来，这里开一个原生 Activity；
 * 2. 原生页改了一行，通过 {@link #postChange} 走 evaluateJavascript 回到网页的
 *    `window.__foliaNativeSettings.set(id, value)`，由网页的 store 真正落库并即时生效。
 *
 * 网页永远是唯一的真相：原生页不存任何值，只画它拿到的那一份。
 */
@CapacitorPlugin(name = "FoliaSettings")
public class FoliaSettingsPlugin extends Plugin {

    /** 原生设置页要往网页回写时用它；null 表示桥已经没了，这时候改了也没人接手。 */
    private static FoliaSettingsPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    @Override
    protected void handleOnDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.handleOnDestroy();
    }

    @PluginMethod
    public void open(PluginCall call) {
        String schema = call.getString("schema", "{}");
        String title = call.getString("title", "设置");
        try {
            // 先校验一遍：坏掉的 schema 不该把用户送进一个空白的原生页。
            new JSONObject(schema);
        } catch (Exception error) {
            call.reject("settings schema is not valid JSON");
            return;
        }
        NativeSettingsActivity.start(getActivity(), schema, title);
        JSObject result = new JSObject();
        result.put("opened", true);
        call.resolve(result);
    }

    /**
     * 原生页调用：把一行改动交回网页。
     *
     * 值以 JSON 字面量拼接（true / "zh-CN" / 60），而不是字符串，这样网页侧拿到的就是
     * 它自己的类型，不必再猜。
     */
    static void postChange(String id, Object value) {
        final FoliaSettingsPlugin plugin = instance;
        if (plugin == null || plugin.getBridge() == null || plugin.getBridge().getWebView() == null) {
            return;
        }
        final String idLiteral;
        try {
            idLiteral = JSONObject.quote(id);
        } catch (Exception ignored) {
            return;
        }
        final String valueLiteral;
        if (value instanceof Boolean || value instanceof Number) {
            valueLiteral = String.valueOf(value);
        } else {
            String quoted;
            try {
                quoted = JSONObject.quote(String.valueOf(value));
            } catch (Exception ignored) {
                quoted = "null";
            }
            valueLiteral = quoted;
        }
        final String script = "window.__foliaNativeSettings&&window.__foliaNativeSettings.set("
                + idLiteral + "," + valueLiteral + ");";
        final android.app.Activity activity = plugin.getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                plugin.getBridge().getWebView().evaluateJavascript(script, null);
            } catch (Exception ignored) {
                // 网页不在就不写：下次打开原生页读到的仍是网页里的旧值，两边不会不一致。
            }
        });
    }
}
