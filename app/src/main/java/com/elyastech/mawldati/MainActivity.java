package com.elyastech.mawldati;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String SUPABASE_URL = "https://admwtddiylyofpwtauui.supabase.co";
    private static final String SUPABASE_KEY = "sb_publishable_0CY42ztOEqpVSUEDBtJV5w_a_8W6Zg7";
    private static final String APP_VERSION = "0.5.0";
    private static final String SUPPORT_PHONE = "07722523232";

    // Dark Green UI palette — aligned with Mawldati Admin Desktop.
    private static final int C_BG = 0xFF050807;
    private static final int C_PANEL = 0xFF0A110D;
    private static final int C_PANEL_2 = 0xFF0D1711;
    private static final int C_TEXT = 0xFFF2F7F4;
    private static final int C_MUTED = 0xFF8FA69A;
    private static final int C_BORDER = 0xFF183C29;
    private static final int C_GREEN = 0xFF19F06F;
    private static final int C_GREEN_SOFT = 0xFF0B2416;
    private static final int C_RED = 0xFFFF5A67;
    private static final int C_AMBER = 0xFFFFB020;
    private static final int C_BLUE = 0xFF55B8FF;
    private static final int C_PURPLE = 0xFFB58CFF;

    private static final String PREFS = "mawldati_license";
    private static final String P_CODE = "activation_code";
    private static final String P_STATUS = "license_status";
    private static final String P_EXPIRES = "expires_at";
    private static final String P_ACTIVATED = "activated_at";
    private static final String P_OWNER = "owner_name";
    private static final String P_GENERATOR = "generator_name";
    private static final String P_GRACE = "offline_grace_days";
    private static final String P_LAST_OK = "last_ok_epoch";
    private static final String P_SUBSCRIBERS = "subscribers_json";
    private static final String P_EXPENSES = "expenses_json";
    private static final String P_RECEIPTS = "receipts_json";
    private static final String P_RECEIPT_SEQ = "receipt_sequence";
    private static final String P_RECEIPT_YEAR = "receipt_year";
    private static final String P_PRINTER_TYPE = "printer_type";
    private static final String P_PRINTER_BT_ADDRESS = "printer_bt_address";
    private static final String P_PRINTER_BT_NAME = "printer_bt_name";
    private static final String P_PRINTER_WIFI_HOST = "printer_wifi_host";
    private static final String P_PRINTER_WIFI_PORT = "printer_wifi_port";
    private static final String P_PRINTER_PAPER = "printer_paper_mm";
    private static final String P_AI_ENDPOINT = "ai_endpoint";

    private static final int REQ_CREATE_BACKUP = 501;
    private static final int REQ_RESTORE_BACKUP = 502;
    private static final int REQ_BLUETOOTH_CONNECT = 503;
    private static final int REQ_SAVE_RECEIPT = 504;

    private SharedPreferences prefs;
    private ExecutorService executor;
    private String deviceId;
    private EditText activationCode;
    private Button activationButton;

    private String currentStatus = "active";
    private boolean currentOffline = false;
    private int currentGraceDays = 3;
    private String currentScreen = "home";
    private String pendingBackupJson = null;
    private boolean pendingBluetoothPicker = false;
    private byte[] pendingReceiptPng = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(C_BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setNavigationBarColor(C_BG);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        deviceId = buildDeviceId();

        String savedCode = prefs.getString(P_CODE, "");
        if (savedCode == null || savedCode.trim().isEmpty()) {
            renderActivation("التطبيق غير مفعّل", "أدخل رمز التفعيل الصادر من لوحة الإدارة.", C_MUTED);
        } else {
            renderLoading("جارِ التحقق من الاشتراك", "يتم الاتصال بالسيرفر والتحقق من الترخيص المحفوظ...");
            checkLicense(savedCode.trim().toUpperCase(Locale.ROOT));
        }
    }

    private void checkLicense(String code) {
        executor.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("p_activation_code", code);
                request.put("p_device_id", deviceId);
                request.put("p_app_version", APP_VERSION);

                URL url = new URL(SUPABASE_URL + "/rest/v1/rpc/check_license");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setDoOutput(true);
                c.setRequestProperty("apikey", SUPABASE_KEY);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Accept", "application/json");

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }

                int http = c.getResponseCode();
                InputStream stream = http >= 200 && http < 300 ? c.getInputStream() : c.getErrorStream();
                String response = readAll(stream);
                if (http < 200 || http >= 300) {
                    throw new Exception("HTTP " + http + ": " + response);
                }

                JSONObject json = new JSONObject(response);
                boolean ok = json.optBoolean("ok", false);
                String status = json.optString("status", "unknown");
                String expiresAt = json.optString("expires_at", "");
                String activatedAt = json.optString("activated_at", "");
                String ownerName = json.optString("owner_name", "");
                String generatorName = json.optString("generator_name", "");
                int graceDays = json.optInt("offline_grace_days", 3);

                SharedPreferences.Editor ed = prefs.edit()
                        .putString(P_CODE, code)
                        .putString(P_STATUS, status)
                        .putString(P_EXPIRES, expiresAt)
                        .putInt(P_GRACE, graceDays);

                if (!activatedAt.isEmpty()) ed.putString(P_ACTIVATED, activatedAt);
                if (!ownerName.isEmpty()) ed.putString(P_OWNER, ownerName);
                if (!generatorName.isEmpty()) ed.putString(P_GENERATOR, generatorName);

                if (ok) ed.putLong(P_LAST_OK, System.currentTimeMillis());
                ed.apply();

                runOnUiThread(() -> handleServerState(ok, status, expiresAt, graceDays));
            } catch (Exception e) {
                runOnUiThread(() -> handleNetworkFailure(e));
            }
        });
    }

    private void handleServerState(boolean ok, String status, String expiresAt, int graceDays) {
        if (ok && ("trial".equals(status) || "active".equals(status))) {
            renderHome(status, false, graceDays);
            return;
        }

        String title;
        String details;
        int color = C_RED;

        switch (status) {
            case "expired":
                title = "الاشتراك منتهي";
                details = "انتهى الاشتراك في " + formatDate(expiresAt) + ". جدد الاشتراك من الإدارة ثم اضغط فحص الاشتراك.";
                break;
            case "suspended":
                title = "الترخيص موقوف";
                details = "تم إيقاف الترخيص من لوحة الإدارة. تواصل مع الدعم الفني إذا كان الإيقاف غير مقصود.";
                break;
            case "device_mismatch":
                title = "الجهاز غير مسموح";
                details = "رمز التفعيل مربوط بجهاز آخر. يجب فك ربط الجهاز من لوحة الإدارة أولًا.";
                break;
            case "not_found":
                title = "رمز التفعيل غير موجود";
                details = "تأكد من الرمز ثم حاول مرة أخرى.";
                break;
            case "invalid_code":
                title = "رمز غير صحيح";
                details = "أدخل رمز التفعيل كاملًا.";
                break;
            case "invalid_device":
                title = "تعذر التعرف على الجهاز";
                details = "أعد تشغيل التطبيق وحاول مرة أخرى.";
                break;
            default:
                title = "تعذر التفعيل";
                details = "استجابة غير متوقعة من السيرفر: " + status;
                break;
        }
        renderActivation(title, details, color);
    }

    private void handleNetworkFailure(Exception error) {
        String savedStatus = prefs.getString(P_STATUS, "");
        String expires = prefs.getString(P_EXPIRES, "");
        int graceDays = prefs.getInt(P_GRACE, 3);
        long lastOk = prefs.getLong(P_LAST_OK, 0L);
        long graceMs = graceDays * 24L * 60L * 60L * 1000L;
        boolean insideGrace = lastOk > 0 && System.currentTimeMillis() - lastOk <= graceMs;
        boolean notExpired = isNotExpired(expires);
        boolean wasAllowed = "active".equals(savedStatus) || "trial".equals(savedStatus);

        if (insideGrace && notExpired && wasAllowed) {
            renderHome(savedStatus, true, graceDays);
        } else {
            renderActivation("لا يوجد اتصال بالسيرفر",
                    "تعذر الوصول إلى السيرفر ولا توجد مهلة دون إنترنت صالحة.\n" + safeError(error),
                    C_RED);
        }
    }

    private void renderLoading(String title, String details) {
        currentScreen = "loading";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addHeader(root, false);

        LinearLayout card = card();
        TextView t = text(title, 22, C_TEXT, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t, matchWrap());

        TextView d = text(details, 15, C_MUTED, false);
        d.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dLp = matchWrap();
        dLp.setMargins(0, dp(10), 0, 0);
        card.addView(d, dLp);
        root.addView(card, matchWrap());

        TextView wait = text("●  جارِ الاتصال", 16, C_BLUE, true);
        wait.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wLp = matchWrap();
        wLp.setMargins(0, dp(18), 0, 0);
        root.addView(wait, wLp);
        addFooter(root);
        setContentView(scroll);
    }

    private void renderActivation(String stateTitle, String stateDetails, int stateColor) {
        currentScreen = "activation";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addHeader(root, false);

        LinearLayout card = card();
        TextView codeLabel = text("رمز التفعيل", 16, 0xFFD8E6DE, true);
        card.addView(codeLabel, matchWrap());

        activationCode = new EditText(this);
        activationCode.setHint("مثال: GEN-A2HQ-4PUS");
        activationCode.setSingleLine(true);
        activationCode.setTextSize(18);
        activationCode.setTextColor(C_TEXT);
        activationCode.setHintTextColor(C_MUTED);
        activationCode.setGravity(Gravity.CENTER);
        activationCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        activationCode.setPadding(dp(12), dp(13), dp(12), dp(13));
        activationCode.setBackground(makeRounded(C_PANEL_2, 12));
        String saved = prefs.getString(P_CODE, "");
        if (saved != null) activationCode.setText(saved);
        LinearLayout.LayoutParams editLp = matchWrap();
        editLp.setMargins(0, dp(9), 0, dp(14));
        card.addView(activationCode, editLp);

        activationButton = primaryButton("تفعيل / فحص الاشتراك");
        activationButton.setOnClickListener(v -> {
            String code = activationCode.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (code.length() < 6) {
                Toast.makeText(this, "أدخل رمز التفعيل كاملًا", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(P_CODE, code).apply();
            renderLoading("جارِ فحص الاشتراك", "يتم التحقق من الرمز وربطه بهذا الجهاز...");
            checkLicense(code);
        });
        card.addView(activationButton, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(card, matchWrap());

        TextView state = text("●  " + stateTitle, 20, stateColor, true);
        state.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = matchWrap();
        sLp.setMargins(0, dp(22), 0, dp(5));
        root.addView(state, sLp);

        TextView details = text(stateDetails, 15, C_MUTED, false);
        details.setGravity(Gravity.CENTER);
        root.addView(details, matchWrap());

        TextView dev = text("معرّف الجهاز: " + shortDeviceId(), 12, 0xFF71857A, false);
        dev.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams devLp = matchWrap();
        devLp.setMargins(0, dp(12), 0, dp(18));
        root.addView(dev, devLp);

        Button support = outlineButton("الدعم الفني  " + SUPPORT_PHONE);
        support.setOnClickListener(v -> callSupport());
        root.addView(support, new LinearLayout.LayoutParams(-1, dp(48)));

        addFooter(root);
        setContentView(scroll);
    }

    private void renderHome(String status, boolean offline, int graceDays) {
        currentScreen = "home";
        currentStatus = status;
        currentOffline = offline;
        currentGraceDays = graceDays;

        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addHeader(root, true);

        String owner = prefs.getString(P_OWNER, "");
        String generator = prefs.getString(P_GENERATOR, "");

        TextView welcome = text(owner == null || owner.isEmpty() ? "مرحباً بك" : "مرحباً، " + owner,
                isTablet() ? 28 : 24, C_TEXT, true);
        root.addView(welcome, matchWrap());

        TextView generatorText = text(generator == null || generator.isEmpty() ? "مولدتي" : "المولدة: " + generator,
                isTablet() ? 17 : 15, C_MUTED, false);
        LinearLayout.LayoutParams genLp = matchWrap();
        genLp.setMargins(0, dp(3), 0, dp(14));
        root.addView(generatorText, genLp);

        JSONArray subs = getSubscribers();
        int total = subs.length();
        int paid = countPaidCurrentMonth(subs);
        int unpaid = Math.max(0, total - paid);
        double collected = sumCollectedCurrentMonth(subs);

        TextView dashTitle = text("نظرة سريعة", isTablet() ? 22 : 20, C_TEXT, true);
        LinearLayout.LayoutParams dtp = matchWrap();
        dtp.setMargins(0, dp(18), 0, dp(10));
        root.addView(dashTitle, dtp);

        if (isTablet()) {
            LinearLayout stats = horizontalRow();
            addStatCard(stats, "المشتركين", String.valueOf(total), C_GREEN);
            addStatCard(stats, "الدافعين", String.valueOf(paid), C_GREEN);
            addStatCard(stats, "غير الدافعين", String.valueOf(unpaid), C_RED);
            addStatCard(stats, "المقبوض", money(collected), C_BLUE);
            root.addView(stats, matchWrap());
        } else {
            LinearLayout row1 = horizontalRow();
            addStatCard(row1, "المشتركين", String.valueOf(total), C_GREEN);
            addStatCard(row1, "الدافعين", String.valueOf(paid), C_GREEN);
            root.addView(row1, matchWrap());

            LinearLayout row2 = horizontalRow();
            addStatCard(row2, "غير الدافعين", String.valueOf(unpaid), C_RED);
            addStatCard(row2, "المقبوض", money(collected), C_BLUE);
            LinearLayout.LayoutParams r2p = matchWrap();
            r2p.setMargins(0, dp(10), 0, 0);
            root.addView(row2, r2p);
        }

        TextView servicesTitle = text("إدارة المولدة", isTablet() ? 22 : 20, C_TEXT, true);
        LinearLayout.LayoutParams stp = matchWrap();
        stp.setMargins(0, dp(20), 0, dp(10));
        root.addView(servicesTitle, stp);

        if (isTablet()) {
            LinearLayout m1 = horizontalRow();
            addMenuButton(m1, "المشتركين", "إضافة وتعديل المشتركين", C_GREEN, v -> renderSubscribers());
            addMenuButton(m1, "الجباية", "تسجيل الدفع الشهري", C_BLUE, v -> renderCollections());
            addMenuButton(m1, "المصروفات", "وقود وصيانة ومصاريف", C_AMBER, v -> renderExpenses());
            root.addView(m1, matchWrap());

            LinearLayout m2 = horizontalRow();
            addMenuButton(m2, "التقارير", "ملخص الشهر والحسابات", C_PURPLE, v -> renderReports());
            addMenuButton(m2, "المساعد الذكي", "أعطال وصيانة وحسابات المولدات", C_GREEN, v -> renderGeneratorAssistant());
            addMenuButton(m2, "الإعدادات", "الطابعة والنسخ الاحتياطي", 0xFFA8B9AF, v -> renderSettings());
            LinearLayout.LayoutParams m2p = matchWrap();
            m2p.setMargins(0, dp(10), 0, 0);
            root.addView(m2, m2p);

            LinearLayout m3 = horizontalRow();
            addMenuButton(m3, "الاشتراك", "حالة ترخيص التطبيق", C_GREEN, v -> renderLicenseDetails());
            LinearLayout.LayoutParams m3p = matchWrap();
            m3p.setMargins(0, dp(10), 0, 0);
            root.addView(m3, m3p);
        } else {
            LinearLayout m1 = horizontalRow();
            addMenuButton(m1, "المشتركين", "إضافة وتعديل المشتركين", C_GREEN, v -> renderSubscribers());
            addMenuButton(m1, "الجباية", "تسجيل الدفع الشهري", C_BLUE, v -> renderCollections());
            root.addView(m1, matchWrap());

            LinearLayout m2 = horizontalRow();
            addMenuButton(m2, "المصروفات", "وقود وصيانة ومصاريف", C_AMBER, v -> renderExpenses());
            addMenuButton(m2, "التقارير", "ملخص الشهر والحسابات", C_PURPLE, v -> renderReports());
            LinearLayout.LayoutParams m2p = matchWrap();
            m2p.setMargins(0, dp(10), 0, 0);
            root.addView(m2, m2p);

            LinearLayout m3 = horizontalRow();
            addMenuButton(m3, "المساعد الذكي", "أعطال وصيانة وحسابات", C_GREEN, v -> renderGeneratorAssistant());
            addMenuButton(m3, "الإعدادات", "الطابعة والنسخ الاحتياطي", 0xFFA8B9AF, v -> renderSettings());
            LinearLayout.LayoutParams m3p = matchWrap();
            m3p.setMargins(0, dp(10), 0, 0);
            root.addView(m3, m3p);

            LinearLayout m4 = horizontalRow();
            addMenuButton(m4, "الاشتراك", "حالة ترخيص التطبيق", C_GREEN, v -> renderLicenseDetails());
            LinearLayout.LayoutParams m4p = matchWrap();
            m4p.setMargins(0, dp(10), 0, 0);
            root.addView(m4, m4p);
        }

        addFooter(root);
        setContentView(scroll);
    }

    private void renderSubscribers() {
        currentScreen = "subscribers";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "المشتركين", "إدارة أسماء المشتركين وأسعار الاشتراك الشهري");

        EditText search = new EditText(this);
        search.setHint("ابحث عن اسم المشترك...");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        styleInput(search);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(50));
        searchLp.setMargins(0, 0, 0, dp(10));
        root.addView(search, searchLp);

        Button add = primaryButton("+ إضافة مشترك");
        add.setOnClickListener(v -> showSubscriberDialog(null));
        root.addView(add, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = matchWrap();
        listLp.setMargins(0, dp(4), 0, 0);
        root.addView(listContainer, listLp);

        renderSubscriberSearchResults(listContainer, "");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSubscriberSearchResults(listContainer, s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        addFooter(root);
        setContentView(scroll);
        search.requestFocusFromTouch();
    }

    private void renderSubscriberSearchResults(LinearLayout container, String query) {
        container.removeAllViews();
        JSONArray arr = getSubscribers();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        if (arr.length() == 0) {
            container.addView(emptyCard("لا يوجد مشتركون حتى الآن", "اضغط إضافة مشترك لبدء العمل."), spacedCardLp());
            return;
        }

        container.addView(subscriberListHeader(), subscriberRowLp());

        int matches = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            String name = s.optString("name", "");
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            container.addView(subscriberListRow(i + 1, s), subscriberRowLp());
            matches++;
        }

        if (matches == 0) {
            container.addView(emptyCard("لا توجد نتيجة", "لم يتم العثور على مشترك بهذا الاسم."), spacedCardLp());
        }
    }

    private LinearLayout subscriberListHeader() {
        LinearLayout h = horizontalRow();
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        h.setPadding(dp(10), dp(7), dp(10), dp(7));
        h.setBackground(makeRounded(C_GREEN_SOFT, 6));

        TextView num = text("#", 12, C_MUTED, true);
        num.setGravity(Gravity.CENTER);
        h.addView(num, new LinearLayout.LayoutParams(dp(34), -2));

        TextView name = text("اسم المشترك", 12, 0xFFA8B9AF, true);
        name.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        h.addView(name, new LinearLayout.LayoutParams(0, -2, 1.8f));

        TextView amps = text("الأمبير", 12, 0xFFA8B9AF, true);
        amps.setGravity(Gravity.CENTER);
        h.addView(amps, new LinearLayout.LayoutParams(0, -2, 1.0f));

        TextView paid = text("الدفع", 12, 0xFFA8B9AF, true);
        paid.setGravity(Gravity.CENTER);
        h.addView(paid, new LinearLayout.LayoutParams(0, -2, 1.15f));
        return h;
    }

    private LinearLayout subscriberListRow(int number, JSONObject s) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), dp(10), dp(10), dp(10));
        android.graphics.drawable.GradientDrawable bg = makeRounded(C_PANEL, 7);
        bg.setStroke(dp(1), C_BORDER);
        c.setBackground(bg);

        String name = s.optString("name", "بدون اسم");
        String phone = s.optString("phone", "");
        String area = s.optString("area", "");
        int amps = s.optInt("amps", 0);
        double fee = s.optDouble("fee", 0);
        boolean paid = isPaidCurrentMonth(s);

        LinearLayout main = horizontalRow();
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView num = text(String.valueOf(number), 16, 0xFFA8B9AF, true);
        num.setGravity(Gravity.CENTER);
        main.addView(num, new LinearLayout.LayoutParams(dp(34), dp(40)));

        TextView nameView = text(name, 16, C_TEXT, true);
        nameView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        main.addView(nameView, new LinearLayout.LayoutParams(0, dp(40), 1.8f));

        TextView ampsView = text((amps > 0 ? amps : 0) + " أمبير", 14, 0xFFD8E6DE, true);
        ampsView.setGravity(Gravity.CENTER);
        main.addView(ampsView, new LinearLayout.LayoutParams(0, dp(40), 1.0f));

        TextView paidView = text(paid ? "● مدفوع" : "● غير مدفوع", 14, paid ? C_GREEN : C_RED, true);
        paidView.setGravity(Gravity.CENTER);
        main.addView(paidView, new LinearLayout.LayoutParams(0, dp(40), 1.15f));

        c.addView(main, matchWrap());

        StringBuilder extra = new StringBuilder();
        if (!area.isEmpty()) extra.append(area);
        if (!phone.isEmpty()) {
            if (extra.length() > 0) extra.append("  •  ");
            extra.append(phone);
        }
        if (fee > 0) {
            if (extra.length() > 0) extra.append("  •  ");
            extra.append("الاشتراك: ").append(money(fee));
        }
        if (extra.length() > 0) {
            TextView info = text(extra.toString(), 12, C_MUTED, false);
            info.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams ip = matchWrap();
            ip.setMargins(0, dp(2), 0, dp(6));
            c.addView(info, ip);
        }

        LinearLayout actions = horizontalRow();
        Button edit = outlineButton("تعديل");
        edit.setOnClickListener(v -> showSubscriberDialog(s));
        actions.addView(edit, halfButtonLp());
        Button del = outlineButton("حذف");
        del.setTextColor(C_RED);
        del.setOnClickListener(v -> confirmDeleteSubscriber(s.optString("id")));
        LinearLayout.LayoutParams dl = halfButtonLp();
        dl.setMargins(dp(6), 0, 0, 0);
        actions.addView(del, dl);
        c.addView(actions, matchWrap());
        return c;
    }

    private LinearLayout.LayoutParams subscriberRowLp() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(6), 0, 0);
        return lp;
    }

    private void showSubscriberDialog(JSONObject existing) {
        LinearLayout box = dialogBox();
        EditText name = dialogEdit("اسم المشترك", InputType.TYPE_CLASS_TEXT);
        EditText phone = dialogEdit("رقم الهاتف", InputType.TYPE_CLASS_PHONE);
        EditText area = dialogEdit("المنطقة / المحلة", InputType.TYPE_CLASS_TEXT);
        EditText amps = dialogEdit("عدد الأمبيرات", InputType.TYPE_CLASS_NUMBER);
        EditText fee = dialogEdit("الاشتراك الشهري بالدينار", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(name); box.addView(phone); box.addView(area); box.addView(amps); box.addView(fee);

        if (existing != null) {
            name.setText(existing.optString("name", ""));
            phone.setText(existing.optString("phone", ""));
            area.setText(existing.optString("area", ""));
            int a = existing.optInt("amps", 0); if (a > 0) amps.setText(String.valueOf(a));
            double f = existing.optDouble("fee", 0); if (f > 0) fee.setText(String.format(Locale.US, "%.0f", f));
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "إضافة مشترك" : "تعديل المشترك")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { Toast.makeText(this, "اسم المشترك مطلوب", Toast.LENGTH_SHORT).show(); return; }
                    JSONObject obj = existing == null ? new JSONObject() : existing;
                    try {
                        if (existing == null) obj.put("id", UUID.randomUUID().toString());
                        obj.put("name", n);
                        obj.put("phone", phone.getText().toString().trim());
                        obj.put("area", area.getText().toString().trim());
                        obj.put("amps", safeInt(amps.getText().toString()));
                        obj.put("fee", safeDouble(fee.getText().toString()));
                        JSONArray arr = getSubscribers();
                        if (existing == null) arr.put(obj);
                        else replaceById(arr, obj);
                        saveSubscribers(arr);
                        renderSubscribers();
                    } catch (Exception e) {
                        Toast.makeText(this, "تعذر حفظ المشترك", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void confirmDeleteSubscriber(String id) {
        new AlertDialog.Builder(this)
                .setTitle("حذف المشترك")
                .setMessage("هل تريد حذف هذا المشترك نهائيًا؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, w) -> {
                    JSONArray arr = getSubscribers();
                    JSONArray out = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.optJSONObject(i);
                        if (s != null && !id.equals(s.optString("id"))) out.put(s);
                    }
                    saveSubscribers(out);
                    renderSubscribers();
                }).show();
    }

    private void renderCollections() {
        currentScreen = "collections";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        String month = currentMonth();
        addSectionHeader(root, "الجباية", "تسجيل اشتراكات شهر " + displayMonth(month));

        JSONArray arr = getSubscribers();
        double expected = sumExpected(arr);
        double collected = sumCollectedCurrentMonth(arr);
        LinearLayout summary = card();
        addInfoRow(summary, "المطلوب", money(expected));
        addInfoRow(summary, "المقبوض", money(collected));
        addInfoRow(summary, "المتبقي", money(Math.max(0, expected - collected)));
        root.addView(summary, matchWrap());

        EditText search = new EditText(this);
        search.setHint("ابحث في الجباية بالاسم أو الهاتف أو المنطقة...");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        styleInput(search);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(50));
        searchLp.setMargins(0, dp(12), 0, dp(4));
        root.addView(search, searchLp);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, matchWrap());

        renderCollectionSearchResults(listContainer, arr, "");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderCollectionSearchResults(listContainer, arr, text == null ? "" : text.toString());
            }
            @Override public void afterTextChanged(Editable editable) { }
        });

        addFooter(root);
        setContentView(scroll);
    }

    private void renderCollectionSearchResults(LinearLayout container, JSONArray arr, String query) {
        container.removeAllViews();
        if (arr == null || arr.length() == 0) {
            container.addView(emptyCard("لا يوجد مشتركون", "أضف المشتركين أولًا من صفحة المشتركين."), spacedCardLp());
            return;
        }

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int matches = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject subscriber = arr.optJSONObject(i);
            if (subscriber == null) continue;

            String haystack = (
                    subscriber.optString("name", "") + " " +
                    subscriber.optString("phone", "") + " " +
                    subscriber.optString("area", "")
            ).toLowerCase(Locale.ROOT);

            if (!q.isEmpty() && !haystack.contains(q)) continue;
            container.addView(collectionCard(subscriber), spacedCardLp());
            matches++;
        }

        if (matches == 0) {
            container.addView(emptyCard("لا توجد نتيجة", "لم يتم العثور على مشترك مطابق للبحث في الجباية."), spacedCardLp());
        }
    }

    private LinearLayout collectionCard(JSONObject s) {
        LinearLayout c = card();
        boolean paid = isPaidCurrentMonth(s);
        String name = s.optString("name", "بدون اسم");
        double fee = s.optDouble("fee", 0);
        double paidAmount = s.optDouble("paid_amount", fee);

        c.addView(text(name, 18, C_TEXT, true), matchWrap());
        TextView amount = text(paid ? "مدفوع: " + money(paidAmount) : "المطلوب: " + money(fee), 14, paid ? C_GREEN : C_MUTED, false);
        LinearLayout.LayoutParams ap = matchWrap(); ap.setMargins(0, dp(4), 0, dp(6)); c.addView(amount, ap);

        String receiptNo = s.optString("receipt_no", "");
        if (paid && !receiptNo.isEmpty()) {
            TextView rn = text("رقم الوصل: " + receiptNo, 13, C_GREEN, true);
            LinearLayout.LayoutParams rlp = matchWrap(); rlp.setMargins(0, 0, 0, dp(10)); c.addView(rn, rlp);
        } else {
            ap.setMargins(0, dp(4), 0, dp(10));
        }

        if (!paid) {
            Button pay = primaryButton("تسجيل الدفع وإصدار الوصل");
            pay.setOnClickListener(v -> showPaymentDialog(s));
            c.addView(pay, new LinearLayout.LayoutParams(-1, dp(46)));
        } else {
            LinearLayout actions = horizontalRow();
            Button receipt = outlineButton("الوصل: طباعة / حفظ");
            receipt.setOnClickListener(v -> reprintSubscriberReceipt(s));
            actions.addView(receipt, halfButtonLp());
            Button undo = outlineButton("إلغاء الدفع");
            undo.setTextColor(C_RED);
            undo.setOnClickListener(v -> undoPayment(s));
            LinearLayout.LayoutParams ul = halfButtonLp(); ul.setMargins(dp(6), 0, 0, 0); actions.addView(undo, ul);
            c.addView(actions, matchWrap());
        }
        return c;
    }

    private void showPaymentDialog(JSONObject s) {
        EditText amount = dialogEdit("المبلغ المستلم", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double fee = s.optDouble("fee", 0);
        if (fee > 0) amount.setText(String.format(Locale.US, "%.0f", fee));
        LinearLayout box = dialogBox(); box.addView(amount);
        new AlertDialog.Builder(this)
                .setTitle("تسجيل دفع - " + s.optString("name", ""))
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تسجيل الدفع", (d,w) -> {
                    try {
                        double received = safeDouble(amount.getText().toString());
                        String paidAt = Instant.now().toString();
                        s.put("paid_month", currentMonth());
                        s.put("paid_amount", received);
                        s.put("paid_at", paidAt);

                        JSONObject receipt = createLocalReceipt(s, received, paidAt);
                        s.put("receipt_no", receipt.optString("receipt_no", ""));
                        s.put("receipt_id", receipt.optString("id", ""));

                        JSONArray arr = getSubscribers();
                        replaceById(arr, s);
                        saveSubscribers(arr);

                        JSONArray receipts = getReceipts();
                        receipts.put(receipt);
                        saveReceipts(receipts);

                        renderCollections();
                        showReceiptActions(receipt, true);
                    } catch (Exception e) {
                        Toast.makeText(this, "تعذر تسجيل الدفع", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private JSONObject createLocalReceipt(JSONObject s, double amount, String paidAt) throws Exception {
        JSONObject r = new JSONObject();
        r.put("id", UUID.randomUUID().toString());
        r.put("receipt_no", nextLocalReceiptNo());
        r.put("subscriber_local_id", s.optString("id", ""));
        r.put("subscriber_name", s.optString("name", ""));
        r.put("subscriber_phone", s.optString("phone", ""));
        r.put("subscriber_area", s.optString("area", ""));
        r.put("amps", s.optInt("amps", 0));
        r.put("amount", amount);
        r.put("paid_month", currentMonth());
        r.put("paid_at", paidAt);
        r.put("status", "paid");
        r.put("created_at", Instant.now().toString());
        return r;
    }

    private String nextLocalReceiptNo() {
        int year = Year.now().getValue();
        int savedYear = prefs.getInt(P_RECEIPT_YEAR, year);
        int seq = prefs.getInt(P_RECEIPT_SEQ, 0);
        if (savedYear != year) seq = 0;
        seq++;
        prefs.edit().putInt(P_RECEIPT_YEAR, year).putInt(P_RECEIPT_SEQ, seq).apply();
        return String.format(Locale.US, "ELY-%d-%06d", year, seq);
    }

    private void undoPayment(JSONObject s) {
        String receiptNo = s.optString("receipt_no", "");
        String message = receiptNo.isEmpty()
                ? "سيعود المشترك إلى غير مدفوع لهذا الشهر."
                : "سيعود المشترك إلى غير مدفوع، وسيبقى الوصل " + receiptNo + " محفوظًا محليًا في السجل بحالة ملغي.";
        new AlertDialog.Builder(this)
                .setTitle("إلغاء الدفع")
                .setMessage(message)
                .setNegativeButton("رجوع", null)
                .setPositiveButton("متابعة", (d,w) -> clearPaymentLocally(s, true))
                .show();
    }

    private void clearPaymentLocally(JSONObject s, boolean cancelReceipt) {
        try {
            String receiptId = s.optString("receipt_id", "");
            if (cancelReceipt && !receiptId.isEmpty()) markReceiptCancelled(receiptId);
            s.remove("paid_month");
            s.remove("paid_amount");
            s.remove("paid_at");
            s.remove("receipt_no");
            s.remove("receipt_id");
            JSONArray arr = getSubscribers();
            replaceById(arr, s);
            saveSubscribers(arr);
            renderCollections();
        } catch (Exception ignored) { }
    }

    private void markReceiptCancelled(String receiptId) {
        JSONArray arr = getReceipts();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r != null && receiptId.equals(r.optString("id", ""))) {
                try {
                    r.put("status", "cancelled");
                    r.put("cancelled_at", Instant.now().toString());
                    arr.put(i, r);
                } catch (Exception ignored) { }
                break;
            }
        }
        saveReceipts(arr);
    }

    private void reprintSubscriberReceipt(JSONObject s) {
        JSONObject receipt = findReceiptByIdOrNo(s.optString("receipt_id", ""), s.optString("receipt_no", ""));
        if (receipt == null) {
            try {
                receipt = createLegacyReceiptSnapshot(s);
                JSONArray arr = getReceipts(); arr.put(receipt); saveReceipts(arr);
                s.put("receipt_id", receipt.optString("id", ""));
                JSONArray subs = getSubscribers(); replaceById(subs, s); saveSubscribers(subs);
            } catch (Exception e) {
                Toast.makeText(this, "تعذر العثور على بيانات الوصل", Toast.LENGTH_LONG).show();
                return;
            }
        }
        showReceiptActions(receipt, false);
    }

    private JSONObject createLegacyReceiptSnapshot(JSONObject s) throws Exception {
        JSONObject r = new JSONObject();
        r.put("id", UUID.randomUUID().toString());
        String no = s.optString("receipt_no", "");
        if (no.isEmpty()) no = nextLocalReceiptNo();
        r.put("receipt_no", no);
        r.put("subscriber_local_id", s.optString("id", ""));
        r.put("subscriber_name", s.optString("name", ""));
        r.put("subscriber_phone", s.optString("phone", ""));
        r.put("subscriber_area", s.optString("area", ""));
        r.put("amps", s.optInt("amps", 0));
        r.put("amount", s.optDouble("paid_amount", s.optDouble("fee", 0)));
        r.put("paid_month", s.optString("paid_month", currentMonth()));
        r.put("paid_at", s.optString("paid_at", Instant.now().toString()));
        r.put("status", "paid");
        r.put("created_at", Instant.now().toString());
        return r;
    }

    private JSONObject findReceiptByIdOrNo(String id, String no) {
        JSONArray arr = getReceipts();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            if ((!id.isEmpty() && id.equals(r.optString("id", ""))) || (!no.isEmpty() && no.equals(r.optString("receipt_no", "")))) return r;
        }
        return null;
    }

    private void showReceiptActions(JSONObject receipt, boolean afterPayment) {
        String receiptNo = receipt == null ? "-" : receipt.optString("receipt_no", "-");
        String message = afterPayment
                ? "تم تسجيل الدفع وإنشاء الوصل " + receiptNo + " محليًا. اختر طباعة الوصل أو حفظه كصورة في ذاكرة الجهاز."
                : "اختر ما تريد فعله بالوصل " + receiptNo + ".";

        new AlertDialog.Builder(this)
                .setTitle(afterPayment ? "تم تسجيل الدفع" : "خيارات الوصل")
                .setMessage(message)
                .setNegativeButton("إغلاق", null)
                .setNeutralButton("حفظ الوصل", (d, w) -> saveReceiptToFile(receipt))
                .setPositiveButton("طباعة", (d, w) -> printReceipt(receipt, afterPayment))
                .show();
    }

    private void saveReceiptToFile(JSONObject receipt) {
        if (receipt == null) return;
        try {
            // نحفظ نسخة عالية الوضوح بعرض 80mm حتى لو لم توجد طابعة.
            Bitmap bitmap = buildThermalReceiptBitmap(receipt, 80);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            pendingReceiptPng = bos.toByteArray();

            String receiptNo = receipt.optString("receipt_no", "receipt").replaceAll("[^A-Za-z0-9_-]", "_");
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_TITLE, "Mawldati_" + receiptNo + ".png");
            startActivityForResult(i, REQ_SAVE_RECEIPT);
        } catch (Exception e) {
            pendingReceiptPng = null;
            Toast.makeText(this, "تعذر تجهيز الوصل للحفظ", Toast.LENGTH_LONG).show();
        }
    }

    private void printReceipt(JSONObject receipt, boolean afterPayment) {
        String type = prefs.getString(P_PRINTER_TYPE, "");
        if (type == null || type.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("الطابعة غير مضبوطة")
                    .setMessage(afterPayment ? "تم حفظ الدفع والوصل محليًا. لا توجد طابعة مضبوطة؛ يمكنك حفظ الوصل كصورة أو ضبط الطابعة من الإعدادات." : "لا توجد طابعة مضبوطة. يمكنك حفظ الوصل كصورة أو ضبط الطابعة من الإعدادات.")
                    .setNegativeButton("لاحقًا", null)
                    .setNeutralButton("حفظ الوصل", (d,w) -> saveReceiptToFile(receipt))
                    .setPositiveButton("فتح إعدادات الطابعة", (d,w) -> renderSettings())
                    .show();
            return;
        }
        executor.execute(() -> {
            try {
                int paper = prefs.getInt(P_PRINTER_PAPER, 58);
                Bitmap bitmap = buildThermalReceiptBitmap(receipt, paper);
                byte[] data = bitmapToEscPosRaster(bitmap);
                if ("bluetooth".equals(type)) sendBluetooth(data);
                else if ("wifi".equals(type)) sendWifi(data);
                else throw new Exception("نوع الطابعة غير معروف");
                runOnUiThread(() -> Toast.makeText(this, "تم إرسال الوصل إلى الطابعة", Toast.LENGTH_LONG).show());
            } catch (SecurityException se) {
                runOnUiThread(() -> requestBluetoothPermissionIfNeeded());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل الطباعة: " + printerError(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    private Bitmap buildThermalReceiptBitmap(JSONObject r, int paperMm) {
        int width = paperMm >= 80 ? 576 : 384;
        int margin = paperMm >= 80 ? 28 : 18;
        int h = paperMm >= 80 ? 1060 : 1000;
        Bitmap bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setColor(Color.BLACK);
        p.setTextAlign(Paint.Align.CENTER);

        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.elyas_tech_logo);
        if (logo != null) {
            int logoH = paperMm >= 80 ? 115 : 88;
            Rect dst = new Rect(margin, 12, width - margin, 12 + logoH);
            canvas.drawBitmap(logo, null, dst, p);
        }

        float y = paperMm >= 80 ? 160 : 125;
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(paperMm >= 80 ? 35 : 27);
        canvas.drawText("وصل دفع اشتراك", width / 2f, y, p);
        y += paperMm >= 80 ? 52 : 42;

        p.setTextSize(paperMm >= 80 ? 27 : 21);
        canvas.drawText("رقم الوصل: " + r.optString("receipt_no", "-"), width / 2f, y, p);
        y += paperMm >= 80 ? 44 : 36;
        drawThermalLine(canvas, p, margin, width - margin, y); y += paperMm >= 80 ? 38 : 30;

        y = drawThermalRow(canvas, p, width, margin, "اسم المشترك", r.optString("subscriber_name", "-"), y, paperMm);
        y = drawThermalRow(canvas, p, width, margin, "رقم الهاتف", r.optString("subscriber_phone", "-"), y, paperMm);
        y = drawThermalRow(canvas, p, width, margin, "المنطقة", r.optString("subscriber_area", "-"), y, paperMm);
        y = drawThermalRow(canvas, p, width, margin, "الأمبير", r.optInt("amps", 0) + " أمبير", y, paperMm);
        y = drawThermalRow(canvas, p, width, margin, "المبلغ", money(r.optDouble("amount", 0)), y, paperMm);
        y = drawThermalRow(canvas, p, width, margin, "الشهر", displayMonth(r.optString("paid_month", currentMonth())), y, paperMm);
        y = drawThermalRow(canvas, p, width, margin, "التاريخ", formatReceiptDate(r.optString("paid_at", "")), y, paperMm);

        y += 10;
        drawThermalLine(canvas, p, margin, width - margin, y); y += paperMm >= 80 ? 46 : 38;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(paperMm >= 80 ? 34 : 27);
        String status = "cancelled".equals(r.optString("status", "paid")) ? "ملغي" : "مدفوع";
        canvas.drawText(status, width / 2f, y, p);
        y += paperMm >= 80 ? 55 : 45;
        p.setTextSize(paperMm >= 80 ? 22 : 18);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText("مولدتي • ELYAS-TECH", width / 2f, y, p);
        y += paperMm >= 80 ? 34 : 28;
        canvas.drawText(SUPPORT_PHONE, width / 2f, y, p);
        y += paperMm >= 80 ? 50 : 40;

        int finalH = Math.min(h, Math.max((int)y, 380));
        return Bitmap.createBitmap(bmp, 0, 0, width, finalH);
    }

    private float drawThermalRow(Canvas canvas, Paint p, int width, int margin, String label, String value, float y, int paperMm) {
        p.setTextSize(paperMm >= 80 ? 23 : 18);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        canvas.drawText(label + ":", width - margin, y, p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        String v = (value == null || value.trim().isEmpty()) ? "-" : value;
        canvas.drawText(v, margin, y, p);
        return y + (paperMm >= 80 ? 43 : 35);
    }

    private void drawThermalLine(Canvas canvas, Paint p, int x1, int x2, float y) {
        p.setStrokeWidth(2f);
        p.setColor(Color.BLACK);
        canvas.drawLine(x1, y, x2, y, p);
    }

    private byte[] bitmapToEscPosRaster(Bitmap bitmap) throws Exception {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int widthBytes = (width + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x1B, 0x40});
        out.write(new byte[]{0x1B, 0x61, 0x01});
        out.write(new byte[]{0x1D, 0x76, 0x30, 0x00,
                (byte)(widthBytes & 0xFF), (byte)((widthBytes >> 8) & 0xFF),
                (byte)(height & 0xFF), (byte)((height >> 8) & 0xFF)});
        for (int y = 0; y < height; y++) {
            for (int xb = 0; xb < widthBytes; xb++) {
                int b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xb * 8 + bit;
                    if (x >= width) continue;
                    int c = bitmap.getPixel(x, y);
                    int lum = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100;
                    if (lum < 170) b |= (1 << (7 - bit));
                }
                out.write(b);
            }
        }
        out.write(new byte[]{0x0A, 0x0A, 0x0A});
        return out.toByteArray();
    }

    private void sendBluetooth(byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Bluetooth permission required");
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new Exception("الجهاز لا يدعم Bluetooth");
        if (!adapter.isEnabled()) throw new Exception("فعّل Bluetooth أولًا");
        String address = prefs.getString(P_PRINTER_BT_ADDRESS, "");
        if (address == null || address.isEmpty()) throw new Exception("لم يتم اختيار طابعة Bluetooth");
        BluetoothDevice device = adapter.getRemoteDevice(address);
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        try {
            socket.connect();
            OutputStream os = socket.getOutputStream();
            os.write(data); os.flush();
        } finally {
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private void sendWifi(byte[] data) throws Exception {
        String host = prefs.getString(P_PRINTER_WIFI_HOST, "");
        int port = prefs.getInt(P_PRINTER_WIFI_PORT, 9100);
        if (host == null || host.trim().isEmpty()) throw new Exception("لم يتم إدخال IP للطابعة");
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host.trim(), port), 6000);
            socket.setSoTimeout(6000);
            OutputStream os = socket.getOutputStream();
            os.write(data); os.flush();
        } finally {
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private String formatReceiptDate(String value) {
        try {
            if (value == null || value.isEmpty()) return "-";
            Instant i = Instant.parse(value);
            return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.US).withZone(ZoneId.systemDefault()).format(i);
        } catch (Exception e) {
            return value == null || value.isEmpty() ? "-" : value;
        }
    }

    private String printerError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return "تحقق من اتصال الطابعة";
        return m.length() > 140 ? m.substring(0, 140) : m;
    }

    private void renderExpenses() {
        currentScreen = "expenses";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "المصروفات", "وقود، صيانة، زيوت وأي مصروف آخر");

        Button add = primaryButton("+ إضافة مصروف");
        add.setOnClickListener(v -> showExpenseDialog());
        root.addView(add, new LinearLayout.LayoutParams(-1, dp(50)));

        JSONArray arr = getExpenses();
        double totalMonth = sumExpensesCurrentMonth(arr);
        LinearLayout totalCard = card();
        totalCard.addView(text("مصروفات هذا الشهر", 15, C_MUTED, false), matchWrap());
        TextView tv = text(money(totalMonth), 24, C_AMBER, true);
        LinearLayout.LayoutParams tp = matchWrap(); tp.setMargins(0, dp(5), 0, 0); totalCard.addView(tv, tp);
        root.addView(totalCard, spacedCardLp());

        boolean any = false;
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null || !isCurrentMonthDate(e.optString("date", ""))) continue;
            any = true;
            LinearLayout c = card();
            c.addView(text(e.optString("note", "مصروف"), 17, C_TEXT, true), matchWrap());
            TextView line = text(money(e.optDouble("amount", 0)) + " • " + e.optString("date", ""), 14, C_MUTED, false);
            LinearLayout.LayoutParams lp = matchWrap(); lp.setMargins(0, dp(4), 0, dp(8)); c.addView(line, lp);
            Button del = outlineButton("حذف المصروف"); del.setTextColor(C_RED);
            String id = e.optString("id"); del.setOnClickListener(v -> deleteExpense(id)); c.addView(del, new LinearLayout.LayoutParams(-1, dp(44)));
            root.addView(c, spacedCardLp());
        }
        if (!any) root.addView(emptyCard("لا توجد مصروفات هذا الشهر", "أضف أول مصروف عند الحاجة."), spacedCardLp());
        addFooter(root);
        setContentView(scroll);
    }

    private void showExpenseDialog() {
        LinearLayout box = dialogBox();
        EditText note = dialogEdit("نوع المصروف / الملاحظة", InputType.TYPE_CLASS_TEXT);
        EditText amount = dialogEdit("المبلغ بالدينار", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(note); box.addView(amount);
        new AlertDialog.Builder(this)
                .setTitle("إضافة مصروف")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (d,w) -> {
                    try {
                        JSONObject e = new JSONObject();
                        e.put("id", UUID.randomUUID().toString());
                        e.put("note", note.getText().toString().trim().isEmpty() ? "مصروف" : note.getText().toString().trim());
                        e.put("amount", safeDouble(amount.getText().toString()));
                        e.put("date", LocalDate.now().toString());
                        JSONArray arr = getExpenses(); arr.put(e); saveExpenses(arr); renderExpenses();
                    } catch (Exception ex) { Toast.makeText(this, "تعذر حفظ المصروف", Toast.LENGTH_SHORT).show(); }
                }).show();
    }

    private void deleteExpense(String id) {
        JSONArray arr = getExpenses(); JSONArray out = new JSONArray();
        for (int i=0;i<arr.length();i++) { JSONObject e=arr.optJSONObject(i); if(e!=null && !id.equals(e.optString("id"))) out.put(e); }
        saveExpenses(out); renderExpenses();
    }

    private void renderReports() {
        currentScreen = "reports";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "تقرير الشهر", "ملخص " + displayMonth(currentMonth()));

        JSONArray subs = getSubscribers();
        JSONArray exps = getExpenses();
        int total = subs.length();
        int paid = countPaidCurrentMonth(subs);
        double expected = sumExpected(subs);
        double collected = sumCollectedCurrentMonth(subs);
        double expenses = sumExpensesCurrentMonth(exps);

        TextView collectionsTitle = text("حساب المشتركين", 19, C_TEXT, true);
        LinearLayout.LayoutParams collectionsTitleLp = matchWrap();
        collectionsTitleLp.setMargins(0, dp(8), 0, dp(8));
        root.addView(collectionsTitle, collectionsTitleLp);

        LinearLayout collectionsCard = card();
        addInfoRow(collectionsCard, "عدد المشتركين", String.valueOf(total));
        addInfoRow(collectionsCard, "الدافعين", paid + " / " + total);
        addInfoRow(collectionsCard, "إجمالي اشتراكات المشتركين", money(expected));
        addInfoRow(collectionsCard, "المبلغ المقبوض من المشتركين", money(collected));
        addInfoRow(collectionsCard, "المتبقي للجباية", money(Math.max(0, expected-collected)));
        root.addView(collectionsCard, matchWrap());

        TextView expensesTitle = text("المصروفات", 19, C_TEXT, true);
        LinearLayout.LayoutParams expensesTitleLp = matchWrap();
        expensesTitleLp.setMargins(0, dp(18), 0, dp(8));
        root.addView(expensesTitle, expensesTitleLp);

        LinearLayout expensesCard = card();
        addInfoRow(expensesCard, "إجمالي مصروفات المولدة", money(expenses));
        TextView expensesNote = text("المصروفات مستقلة عن حساب المشتركين ولا تُطرح من إجمالي الاشتراكات.", 13, C_MUTED, false);
        LinearLayout.LayoutParams expensesNoteLp = matchWrap();
        expensesNoteLp.setMargins(0, dp(8), 0, 0);
        expensesCard.addView(expensesNote, expensesNoteLp);
        root.addView(expensesCard, matchWrap());

        Button copy = primaryButton("نسخ تقرير الشهر");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(50)); cp.setMargins(0, dp(14), 0, 0); root.addView(copy, cp);
        copy.setOnClickListener(v -> copyMonthlyReport(total, paid, expected, collected, expenses));

        addFooter(root);
        setContentView(scroll);
    }

    private void copyMonthlyReport(int total, int paid, double expected, double collected, double expenses) {
        String report = "تقرير مولدتي - " + displayMonth(currentMonth()) + "\n\n" +
                "حساب المشتركين\n" +
                "عدد المشتركين: " + total + "\n" +
                "الدافعين: " + paid + "\n" +
                "إجمالي اشتراكات المشتركين: " + money(expected) + "\n" +
                "المبلغ المقبوض من المشتركين: " + money(collected) + "\n" +
                "المتبقي للجباية: " + money(Math.max(0, expected-collected)) + "\n\n" +
                "المصروفات\n" +
                "إجمالي مصروفات المولدة: " + money(expenses);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Mawldati report", report));
        Toast.makeText(this, "تم نسخ التقرير", Toast.LENGTH_SHORT).show();
    }

    private void renderLicenseDetails() {
        currentScreen = "license";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "الاشتراك والترخيص", "حالة ترخيص تطبيق مولدتي");

        String activatedAt = prefs.getString(P_ACTIVATED, "");
        String expiresAt = prefs.getString(P_EXPIRES, "");
        long daysLeft = remainingDays(expiresAt);
        int statusColor = "trial".equals(currentStatus) ? C_AMBER : C_GREEN;
        String statusLabel = "trial".equals(currentStatus) ? "تجريبي" : "فعال";
        if (currentOffline) { statusColor = C_AMBER; statusLabel += " • دون إنترنت"; }

        LinearLayout statusCard = card();
        statusCard.addView(text("●  " + statusLabel, 21, statusColor, true), matchWrap());
        TextView statusSub = text(currentOffline ? "سماح مؤقت دون إنترنت ضمن مهلة " + currentGraceDays + " أيام." : "تم التحقق من الاشتراك من السيرفر بنجاح.", 14, C_MUTED, false);
        LinearLayout.LayoutParams sp = matchWrap(); sp.setMargins(0, dp(7), 0, 0); statusCard.addView(statusSub, sp);
        root.addView(statusCard, matchWrap());

        LinearLayout c = card();
        addInfoRow(c, "من تاريخ", activatedAt == null || activatedAt.isEmpty() ? "غير محدد" : formatDate(activatedAt));
        addInfoRow(c, "إلى تاريخ", formatDate(expiresAt));
        addInfoRow(c, "الأيام المتبقية", daysLeft < 0 ? "غير محدد" : daysLeft + " يوم");
        addInfoRow(c, "حالة الاتصال", currentOffline ? "دون إنترنت - سماح مؤقت" : "السيرفر متصل");
        root.addView(c, spacedCardLp());

        Button refresh = primaryButton("فحص الاشتراك الآن");
        refresh.setOnClickListener(v -> {
            String code = prefs.getString(P_CODE, "");
            if (code == null || code.isEmpty()) { renderActivation("التطبيق غير مفعّل", "أدخل رمز التفعيل.", C_MUTED); return; }
            renderLoading("جارِ تحديث الاشتراك", "يتم الاتصال بالسيرفر للحصول على أحدث حالة...");
            checkLicense(code);
        });
        root.addView(refresh, new LinearLayout.LayoutParams(-1, dp(50)));
        addFooter(root); setContentView(scroll);
    }

    private void renderReceiptHistory() {
        currentScreen = "receipts";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "سجل الوصولات", "محفوظ محليًا داخل الجهاز فقط");

        JSONArray receipts = getReceipts();
        if (receipts.length() == 0) {
            root.addView(emptyCard("لا توجد وصولات", "تظهر الوصولات هنا بعد تسجيل الدفعات."), spacedCardLp());
        } else {
            for (int i = receipts.length() - 1; i >= 0; i--) {
                JSONObject r = receipts.optJSONObject(i);
                if (r == null) continue;
                LinearLayout c = card();
                c.addView(text(r.optString("receipt_no", "-"), 17, C_GREEN, true), matchWrap());
                addInfoRow(c, "المشترك", r.optString("subscriber_name", "-"));
                addInfoRow(c, "المبلغ", money(r.optDouble("amount", 0)));
                addInfoRow(c, "التاريخ", formatReceiptDate(r.optString("paid_at", "")));
                addInfoRow(c, "الحالة", "cancelled".equals(r.optString("status", "paid")) ? "ملغي" : "مدفوع");
                final JSONObject receipt = r;
                LinearLayout actions = horizontalRow();
                Button print = outlineButton("طباعة");
                print.setOnClickListener(v -> printReceipt(receipt, false));
                actions.addView(print, halfButtonLp());
                Button save = outlineButton("حفظ");
                save.setOnClickListener(v -> saveReceiptToFile(receipt));
                LinearLayout.LayoutParams saveLp = halfButtonLp();
                saveLp.setMargins(dp(6), 0, 0, 0);
                actions.addView(save, saveLp);
                LinearLayout.LayoutParams actionsLp = matchWrap();
                actionsLp.setMargins(0, dp(10), 0, 0);
                c.addView(actions, actionsLp);
                root.addView(c, spacedCardLp());
            }
        }
        addFooter(root); setContentView(scroll);
    }

    private void renderSettings() {
        currentScreen = "settings";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "الإعدادات", "الطابعة والنسخ الاحتياطي والترخيص");

        LinearLayout info = card();
        addInfoRow(info, "الإصدار", APP_VERSION);
        addInfoRow(info, "البيانات", "محلية على هذا الجهاز فقط");
        addInfoRow(info, "السيرفر", "للترخيص والتفعيل فقط");
        root.addView(info, matchWrap());

        TextView printerTitle = text("إعداد الطابعة", 19, C_TEXT, true);
        LinearLayout.LayoutParams pt = matchWrap(); pt.setMargins(0, dp(18), 0, dp(8)); root.addView(printerTitle, pt);
        LinearLayout printer = card();
        String type = prefs.getString(P_PRINTER_TYPE, "");
        String printerLabel = "غير مضبوطة";
        if ("bluetooth".equals(type)) printerLabel = "Bluetooth: " + prefs.getString(P_PRINTER_BT_NAME, "طابعة");
        if ("wifi".equals(type)) printerLabel = "Wi-Fi: " + prefs.getString(P_PRINTER_WIFI_HOST, "-") + ":" + prefs.getInt(P_PRINTER_WIFI_PORT, 9100);
        addInfoRow(printer, "الاتصال", printerLabel);
        addInfoRow(printer, "عرض الورق", prefs.getInt(P_PRINTER_PAPER, 58) + "mm");
        root.addView(printer, matchWrap());

        Button bt = outlineButton("اختيار طابعة Bluetooth"); bt.setOnClickListener(v -> openBluetoothPrinterPicker()); root.addView(bt, spacedButtonLp());
        Button wifi = outlineButton("إعداد طابعة Wi-Fi"); wifi.setOnClickListener(v -> showWifiPrinterDialog()); root.addView(wifi, spacedButtonLp());
        Button paper = outlineButton("اختيار عرض الورق 58 / 80mm"); paper.setOnClickListener(v -> choosePaperWidth()); root.addView(paper, spacedButtonLp());
        Button test = primaryButton("اختبار الطباعة"); test.setOnClickListener(v -> printTestReceipt()); root.addView(test, spacedButtonLp());

        TextView dataTitle = text("البيانات المحلية", 19, C_TEXT, true);
        LinearLayout.LayoutParams dt = matchWrap(); dt.setMargins(0, dp(22), 0, dp(8)); root.addView(dataTitle, dt);
        Button history = outlineButton("سجل الوصولات وإعادة الطباعة"); history.setOnClickListener(v -> renderReceiptHistory()); root.addView(history, spacedButtonLp());
        Button backup = outlineButton("إنشاء نسخة احتياطية إلى ملف"); backup.setOnClickListener(v -> exportBackup()); root.addView(backup, spacedButtonLp());
        Button restore = outlineButton("استعادة البيانات من ملف"); restore.setOnClickListener(v -> importBackup()); root.addView(restore, spacedButtonLp());

        TextView aiTitle = text("المساعد الذكي", 19, C_TEXT, true);
        LinearLayout.LayoutParams ait = matchWrap(); ait.setMargins(0, dp(22), 0, dp(8)); root.addView(aiTitle, ait);
        Button aiOpen = outlineButton("فتح مساعد المولدة الذكي"); aiOpen.setOnClickListener(v -> renderGeneratorAssistant()); root.addView(aiOpen, spacedButtonLp());
        Button aiEndpoint = outlineButton("إعداد مساعد الإنترنت (اختياري)"); aiEndpoint.setOnClickListener(v -> showAiEndpointDialog()); root.addView(aiEndpoint, spacedButtonLp());

        TextView licTitle = text("الترخيص والدعم", 19, C_TEXT, true);
        LinearLayout.LayoutParams lt = matchWrap(); lt.setMargins(0, dp(22), 0, dp(8)); root.addView(licTitle, lt);
        Button support = outlineButton("الاتصال بالدعم الفني"); support.setOnClickListener(v -> callSupport()); root.addView(support, spacedButtonLp());
        Button copyDevice = outlineButton("نسخ معرّف الجهاز"); copyDevice.setOnClickListener(v -> copyDeviceId()); root.addView(copyDevice, spacedButtonLp());
        addFooter(root); setContentView(scroll);
    }

    private void openBluetoothPrinterPicker() {
        if (!requestBluetoothPermissionIfNeeded()) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { Toast.makeText(this, "هذا الجهاز لا يدعم Bluetooth", Toast.LENGTH_LONG).show(); return; }
        if (!adapter.isEnabled()) { Toast.makeText(this, "فعّل Bluetooth واقرن الطابعة من إعدادات الجهاز أولًا", Toast.LENGTH_LONG).show(); return; }
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) {
                Toast.makeText(this, "لا توجد أجهزة Bluetooth مقترنة. قم بإقران الطابعة أولًا.", Toast.LENGTH_LONG).show(); return;
            }
            ArrayList<BluetoothDevice> devices = new ArrayList<>(bonded);
            String[] names = new String[devices.size()];
            for (int i=0; i<devices.size(); i++) {
                BluetoothDevice d = devices.get(i);
                String name = d.getName();
                names[i] = (name == null ? "Bluetooth Printer" : name) + "\n" + d.getAddress();
            }
            new AlertDialog.Builder(this)
                    .setTitle("اختر الطابعة المقترنة")
                    .setItems(names, (dialog, which) -> {
                        BluetoothDevice d = devices.get(which);
                        prefs.edit()
                                .putString(P_PRINTER_TYPE, "bluetooth")
                                .putString(P_PRINTER_BT_ADDRESS, d.getAddress())
                                .putString(P_PRINTER_BT_NAME, d.getName() == null ? "Bluetooth Printer" : d.getName())
                                .apply();
                        Toast.makeText(this, "تم حفظ طابعة Bluetooth", Toast.LENGTH_SHORT).show();
                        renderSettings();
                    }).show();
        } catch (SecurityException e) {
            requestBluetoothPermissionIfNeeded();
        }
    }

    private boolean requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            pendingBluetoothPicker = true;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLUETOOTH_CONNECT);
            return false;
        }
        return true;
    }

    private void showWifiPrinterDialog() {
        LinearLayout box = dialogBox();
        EditText host = dialogEdit("IP الطابعة مثال 192.168.1.50", InputType.TYPE_CLASS_PHONE);
        host.setInputType(InputType.TYPE_CLASS_TEXT);
        EditText port = dialogEdit("Port - الافتراضي 9100", InputType.TYPE_CLASS_NUMBER);
        host.setText(prefs.getString(P_PRINTER_WIFI_HOST, ""));
        port.setText(String.valueOf(prefs.getInt(P_PRINTER_WIFI_PORT, 9100)));
        box.addView(host); box.addView(port);
        new AlertDialog.Builder(this)
                .setTitle("إعداد طابعة Wi-Fi")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (d,w) -> {
                    String h = host.getText().toString().trim();
                    int p = safeInt(port.getText().toString()); if (p <= 0) p = 9100;
                    if (h.isEmpty()) { Toast.makeText(this, "أدخل IP الطابعة", Toast.LENGTH_LONG).show(); return; }
                    prefs.edit().putString(P_PRINTER_TYPE, "wifi").putString(P_PRINTER_WIFI_HOST, h).putInt(P_PRINTER_WIFI_PORT, p).apply();
                    Toast.makeText(this, "تم حفظ طابعة Wi-Fi", Toast.LENGTH_SHORT).show(); renderSettings();
                }).show();
    }

    private void choosePaperWidth() {
        String[] items = {"58mm", "80mm"};
        int current = prefs.getInt(P_PRINTER_PAPER, 58) >= 80 ? 1 : 0;
        new AlertDialog.Builder(this).setTitle("عرض ورق الطابعة").setSingleChoiceItems(items, current, (d, which) -> {
            prefs.edit().putInt(P_PRINTER_PAPER, which == 1 ? 80 : 58).apply(); d.dismiss(); renderSettings();
        }).show();
    }

    private void printTestReceipt() {
        try {
            JSONObject r = new JSONObject();
            r.put("receipt_no", "TEST-000001");
            r.put("subscriber_name", "اختبار الطابعة");
            r.put("subscriber_phone", "07700000000");
            r.put("subscriber_area", "اختبار");
            r.put("amps", 5);
            r.put("amount", 25000);
            r.put("paid_month", currentMonth());
            r.put("paid_at", Instant.now().toString());
            r.put("status", "paid");
            printReceipt(r, false);
        } catch (Exception ignored) { }
    }

    private void exportBackup() {
        try {
            JSONObject root = buildBackupJson();
            pendingBackupJson = root.toString(2);
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "Mawldati_Backup_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US).withZone(ZoneId.systemDefault()).format(Instant.now()) + ".json");
            startActivityForResult(i, REQ_CREATE_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر إنشاء النسخة الاحتياطية", Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject buildBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "mawldati_backup");
        root.put("schema", 1);
        root.put("created_at", Instant.now().toString());
        root.put("app_version", APP_VERSION);
        root.put("subscribers", getSubscribers());
        root.put("expenses", getExpenses());
        root.put("receipts", getReceipts());
        root.put("receipt_year", prefs.getInt(P_RECEIPT_YEAR, Year.now().getValue()));
        root.put("receipt_sequence", prefs.getInt(P_RECEIPT_SEQ, 0));
        JSONObject printer = new JSONObject();
        printer.put("type", prefs.getString(P_PRINTER_TYPE, ""));
        printer.put("bt_address", prefs.getString(P_PRINTER_BT_ADDRESS, ""));
        printer.put("bt_name", prefs.getString(P_PRINTER_BT_NAME, ""));
        printer.put("wifi_host", prefs.getString(P_PRINTER_WIFI_HOST, ""));
        printer.put("wifi_port", prefs.getInt(P_PRINTER_WIFI_PORT, 9100));
        printer.put("paper_mm", prefs.getInt(P_PRINTER_PAPER, 58));
        root.put("printer", printer);
        root.put("ai_endpoint", prefs.getString(P_AI_ENDPOINT, ""));
        return root;
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_RESTORE_BACKUP);
    }

    private void restoreBackupJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"mawldati_backup".equals(root.optString("format", ""))) throw new Exception("invalid_backup");
        JSONArray subscribers = root.optJSONArray("subscribers"); if (subscribers == null) subscribers = new JSONArray();
        JSONArray expenses = root.optJSONArray("expenses"); if (expenses == null) expenses = new JSONArray();
        JSONArray receipts = root.optJSONArray("receipts"); if (receipts == null) receipts = new JSONArray();
        SharedPreferences.Editor ed = prefs.edit()
                .putString(P_SUBSCRIBERS, subscribers.toString())
                .putString(P_EXPENSES, expenses.toString())
                .putString(P_RECEIPTS, receipts.toString())
                .putInt(P_RECEIPT_YEAR, root.optInt("receipt_year", Year.now().getValue()))
                .putInt(P_RECEIPT_SEQ, root.optInt("receipt_sequence", 0));
        JSONObject printer = root.optJSONObject("printer");
        if (printer != null) {
            ed.putString(P_PRINTER_TYPE, printer.optString("type", ""));
            ed.putString(P_PRINTER_BT_ADDRESS, printer.optString("bt_address", ""));
            ed.putString(P_PRINTER_BT_NAME, printer.optString("bt_name", ""));
            ed.putString(P_PRINTER_WIFI_HOST, printer.optString("wifi_host", ""));
            ed.putInt(P_PRINTER_WIFI_PORT, printer.optInt("wifi_port", 9100));
            ed.putInt(P_PRINTER_PAPER, printer.optInt("paper_mm", 58));
        }
        if (root.has("ai_endpoint")) ed.putString(P_AI_ENDPOINT, root.optString("ai_endpoint", ""));
        ed.apply();
    }

    private void renderGeneratorAssistant() {
        currentScreen = "assistant";
        ScrollView scroll = newBaseScroll();
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);
        addSectionHeader(root, "مساعد المولدة الذكي", "معلومات فنية عامة، تشخيص أولي وحسابات سريعة — دون الوصول إلى بيانات المشتركين");

        LinearLayout intro = card();
        TextView badge = text("●  SMART GENERATOR ASSISTANT", 12, C_GREEN, true);
        badge.setGravity(Gravity.CENTER);
        intro.addView(badge, matchWrap());
        TextView note = text("المساعد المحلي يعمل بدون إنترنت. نتائجه إرشادية وليست بديلاً عن دليل الشركة المصنعة أو فني مؤهل.", 14, C_MUTED, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = matchWrap(); np.setMargins(0, dp(8), 0, 0); intro.addView(note, np);
        root.addView(intro, matchWrap());

        TextView quickTitle = text("أسئلة سريعة", 19, C_TEXT, true);
        LinearLayout.LayoutParams qtp = matchWrap(); qtp.setMargins(0, dp(18), 0, dp(8)); root.addView(quickTitle, qtp);

        String[] quick = {"ارتفاع الحرارة", "دخان أسود", "ضعف الفولتية", "زيادة صرف الوقود", "تذبذب RPM", "جدول الصيانة"};
        for (int i = 0; i < quick.length; i += 2) {
            LinearLayout row = horizontalRow();
            for (int j = i; j < Math.min(i + 2, quick.length); j++) {
                final String q = quick[j];
                Button b = outlineButton(q);
                b.setOnClickListener(v -> showAssistantAnswer(q, answerGeneratorQuestion(q)));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                bp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(b, bp);
            }
            root.addView(row, matchWrap());
        }

        TextView askTitle = text("اسأل عن المولدة", 19, C_TEXT, true);
        LinearLayout.LayoutParams atp = matchWrap(); atp.setMargins(0, dp(18), 0, dp(8)); root.addView(askTitle, atp);

        EditText question = new EditText(this);
        question.setHint("مثال: المولدة تسخن ويظهر دخان أسود، ماذا أفحص؟");
        question.setMinLines(2);
        question.setMaxLines(4);
        question.setGravity(Gravity.RIGHT | Gravity.TOP);
        question.setPadding(dp(12), dp(12), dp(12), dp(12));
        styleInput(question);
        root.addView(question, new LinearLayout.LayoutParams(-1, dp(92)));

        TextView answer = text("اكتب سؤالك ثم اضغط «تحليل محلي».", 14, C_MUTED, false);
        answer.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable answerBg = makeRounded(C_PANEL, 8);
        answerBg.setStroke(dp(1), C_BORDER);
        answer.setBackground(answerBg);
        LinearLayout.LayoutParams alp = matchWrap(); alp.setMargins(0, dp(10), 0, 0); root.addView(answer, alp);

        Button local = primaryButton("تحليل محلي");
        local.setOnClickListener(v -> {
            String q = question.getText().toString().trim();
            if (q.isEmpty()) { Toast.makeText(this, "اكتب السؤال أولاً", Toast.LENGTH_SHORT).show(); return; }
            answer.setText(answerGeneratorQuestion(q));
            answer.setTextColor(C_TEXT);
        });
        root.addView(local, spacedButtonLp());

        Button online = outlineButton("سؤال عبر الإنترنت (اختياري)");
        online.setOnClickListener(v -> {
            String q = question.getText().toString().trim();
            if (q.isEmpty()) { Toast.makeText(this, "اكتب السؤال أولاً", Toast.LENGTH_SHORT).show(); return; }
            askOnlineAssistant(q, answer);
        });
        root.addView(online, spacedButtonLp());

        TextView calcTitle = text("حسابات سريعة", 19, C_TEXT, true);
        LinearLayout.LayoutParams ctp = matchWrap(); ctp.setMargins(0, dp(22), 0, dp(8)); root.addView(calcTitle, ctp);
        LinearLayout calcRow = horizontalRow();
        Button kw = outlineButton("kVA → kW"); kw.setOnClickListener(v -> showKvaToKwCalculator()); calcRow.addView(kw, halfButtonLp());
        Button amp = outlineButton("تيار 3 فاز"); amp.setOnClickListener(v -> showThreePhaseCurrentCalculator());
        LinearLayout.LayoutParams ampLp = halfButtonLp(); ampLp.setMargins(dp(8), 0, 0, 0); calcRow.addView(amp, ampLp);
        root.addView(calcRow, matchWrap());

        addFooter(root);
        setContentView(scroll);
    }

    private void showAssistantAnswer(String question, String answer) {
        new AlertDialog.Builder(this)
                .setTitle(question)
                .setMessage(answer)
                .setPositiveButton("حسناً", null)
                .show();
    }

    private boolean containsAny(String q, String... words) {
        for (String w : words) if (q.contains(w)) return true;
        return false;
    }

    private String answerGeneratorQuestion(String input) {
        String q = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        String safety = "\n\n⚠️ للسلامة: افصل الأحمال وأوقف المحرك قبل الفحص الميكانيكي أو الكهربائي، واتبع دليل الشركة المصنعة.";

        if (containsAny(q, "حرارة", "تسخن", "سخونة", "overheat"))
            return "ابدأ بالفحص بهذا الترتيب:\n1) مستوى سائل التبريد وعدم وجود تسريب.\n2) نظافة الرديتر وتدفق الهواء والمروحة.\n3) شد وحالة السير.\n4) مستوى الزيت ونوعه.\n5) الحمل الفعلي مقارنة بقدرة المولدة.\n6) الثرموستات ومضخة الماء إذا استمرت الحرارة." + safety;
        if (containsAny(q, "دخان اسود", "دخان أسود", "اسود", "black smoke"))
            return "الدخان الأسود غالباً يعني وقوداً أكثر من الهواء أو حملاً عالياً. افحص: فلتر الهواء، الحمل الزائد، البخاخات/منظومة الحقن، انسداد مجرى الهواء أو العادم، وجود تيربو إن وجد، وثبات RPM والتردد." + safety;
        if (containsAny(q, "دخان ابيض", "دخان أبيض", "white smoke"))
            return "الدخان الأبيض قد يرتبط بوقود غير محترق عند التشغيل البارد، ضعف ضغط/حقن، توقيت حقن غير مناسب، أو دخول ماء/تبريد إلى غرفة الاحتراق. راقب أيضاً نقص سائل التبريد وصعوبة التشغيل." + safety;
        if (containsAny(q, "دخان ازرق", "دخان أزرق", "blue smoke"))
            return "الدخان الأزرق يدل غالباً على احتراق زيت. افحص مستوى الزيت وعدم زيادته، تهوية الكارتير، حلقات المكبس، أدلة الصمامات، والتيربو إن وجد." + safety;
        if (containsAny(q, "فولت", "voltage", "avr", "جهد"))
            return "إذا كان الجهد منخفضاً أو متذبذباً: تحقق أولاً من RPM/التردد، ثم التوصيلات والقواطع، وبعدها AVR وفرش/دايودات الإثارة حسب نوع المولد. إذا كان التردد نفسه منخفضاً فابدأ بالمحرك والحاكم قبل AVR." + safety;
        if (containsAny(q, "هرتز", "تردد", "hz", "rpm", "تذبذب", "hunting"))
            return "تذبذب RPM أو Hz يرتبط غالباً بالحاكم Governor أو الوقود/الهواء أو تغير الحمل. افحص فلتر الوقود، وجود هواء بالمنظومة، ذراع الحاكم/الحساس، وثبات الحمل. في 50Hz تكون السرعة الشائعة 1500 RPM لمولد 4 أقطاب أو 3000 RPM لمولد قطبين." + safety;
        if (containsAny(q, "صرف الوقود", "استهلاك الوقود", "ديزل", "fuel"))
            return "زيادة استهلاك الوقود: قارن الحمل بالكيلوواط أولاً، ثم افحص فلتر الهواء، البخاخات، التسريب، جودة الوقود، حرارة التشغيل، ضغط الإطارات غير ذي صلة هنا، وثبات التردد. تشغيل مولدة كبيرة على حمل خفيف جداً لفترات طويلة غير اقتصادي وقد يسبب ترسبات." + safety;
        if (containsAny(q, "بطارية", "تشغيل", "سلف", "starter"))
            return "عند صعوبة التشغيل: قِس جهد البطارية في السكون وأثناء السلف، نظف الأقطاب والأرضي، افحص شحن الدينمو، ريليه/سلف التشغيل، مستوى الوقود وملء المنظومة من الهواء، ثم سخانات التشغيل إن كانت موجودة." + safety;
        if (containsAny(q, "زيت", "oil"))
            return "استخدم لزوجة ومواصفة الزيت المحددة من الشركة المصنعة. افحص المستوى يومياً قبل التشغيل وعلى أرض مستوية. فترة التغيير تختلف حسب المحرك والبيئة؛ المرجع الأساسي عداد الساعات ودليل المصنع، مع تقصير الفترة في الغبار والحرارة العالية." + safety;
        if (containsAny(q, "صيانة", "جدول", "maintenance"))
            return "جدول عام إرشادي:\n• يومياً: زيت، تبريد، تسريبات، وقود، أصوات غير طبيعية.\n• أسبوعياً: البطارية، السيور، الرديتر ونظافة الهواء.\n• حسب الساعات: زيت/فلتر زيت، فلتر وقود، فلتر هواء وفق دليل المحرك.\n• دورياً: شد الوصلات الكهربائية، اختبار الحمايات، وفحص AVR والعزل بواسطة فني." + safety;
        if (containsAny(q, "kva", "kw", "كيلو فولت", "كيلوواط", "معامل قدرة"))
            return "العلاقة الأساسية: kW = kVA × معامل القدرة (PF). مثال: 100 kVA عند PF=0.8 ≈ 80 kW. لا تستخدم القدرة الاسمية كاملة باستمرار دون الرجوع لتصنيف Prime/Standby للمولدة.";
        if (containsAny(q, "حمل", "overload", "امبير", "أمبير"))
            return "راقب تيار كل فاز وليس المجموع فقط، وتجنب عدم توازن الفازات. الحمل العالي يظهر كهبوط تردد/فولت وارتفاع حرارة ودخان. قارن kW وkVA والتيار باللوحة الاسمية وتصنيف Prime/Standby." + safety;
        if (containsAny(q, "اهتزاز", "رجة", "صوت", "vibration"))
            return "الاهتزاز غير الطبيعي يستدعي فحص قواعد التثبيت والربلات، المحاذاة بين المحرك والمولد، المروحة والسيور، البراغي، وتوازن الأجزاء الدوارة. أوقف التشغيل إذا ظهر صوت معدني قوي أو ازداد الاهتزاز فجأة." + safety;
        if (containsAny(q, "ماء", "تبريد", "رديتر", "coolant"))
            return "نظام التبريد: افحص المستوى والمحافظة على خليط التبريد الموصى به، نظافة زعانف الرديتر، غطاء الرديتر، الخراطيم، الثرموستات ومضخة الماء. لا تفتح غطاء الرديتر والمحرك ساخن." + safety;
        if (containsAny(q, "يفصل", "يتوقف", "shutdown", "trip"))
            return "إذا تفصل المولدة تلقائياً، اقرأ كود الإنذار أولاً قبل إعادة التشغيل. الأسباب الشائعة: حرارة عالية، ضغط زيت منخفض، زيادة/نقص سرعة، زيادة حمل، جهد أو تردد خارج الحدود، أو مشكلة حساس/توصيل." + safety;

        return "لم أحدد العطل بدقة من السؤال. اكتب الأعراض مع: نوع الوقود، قدرة المولدة kVA، الجهد، التردد Hz، هل العطل تحت الحمل أم بدون حمل، لون الدخان إن وجد، وأي كود إنذار. سأرتب لك خطوات الفحص.";
    }

    private void showKvaToKwCalculator() {
        LinearLayout box = dialogBox();
        EditText kva = dialogEdit("kVA", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText pf = dialogEdit("معامل القدرة PF (مثال 0.8)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        pf.setText("0.8");
        box.addView(kva); box.addView(pf);
        new AlertDialog.Builder(this).setTitle("تحويل kVA إلى kW").setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("احسب", (d,w) -> {
                    double k = safeDouble(kva.getText().toString());
                    double p = safeDouble(pf.getText().toString());
                    if (k <= 0 || p <= 0 || p > 1) { Toast.makeText(this, "تحقق من القيم", Toast.LENGTH_LONG).show(); return; }
                    new AlertDialog.Builder(this).setTitle("النتيجة").setMessage(String.format(Locale.US, "%.2f kW", k * p)).setPositiveButton("حسناً", null).show();
                }).show();
    }

    private void showThreePhaseCurrentCalculator() {
        LinearLayout box = dialogBox();
        EditText kva = dialogEdit("قدرة المولدة kVA", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText volts = dialogEdit("الجهد بين الفازات V (مثال 400)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        volts.setText("400");
        box.addView(kva); box.addView(volts);
        new AlertDialog.Builder(this).setTitle("حساب تيار 3 فاز").setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("احسب", (d,w) -> {
                    double k = safeDouble(kva.getText().toString());
                    double v = safeDouble(volts.getText().toString());
                    if (k <= 0 || v <= 0) { Toast.makeText(this, "تحقق من القيم", Toast.LENGTH_LONG).show(); return; }
                    double amps = (k * 1000.0) / (Math.sqrt(3.0) * v);
                    new AlertDialog.Builder(this).setTitle("التيار التقريبي").setMessage(String.format(Locale.US, "%.1f A لكل فاز عند حمل متوازن", amps)).setPositiveButton("حسناً", null).show();
                }).show();
    }

    private void showAiEndpointDialog() {
        LinearLayout box = dialogBox();
        EditText endpoint = dialogEdit("رابط API/Edge Function للمساعد", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setText(prefs.getString(P_AI_ENDPOINT, ""));
        box.addView(endpoint);
        TextView note = text("اختياري. لا يتم إرسال بيانات المشتركين. يرسل فقط السؤال الذي تكتبه داخل المساعد.", 12, C_MUTED, false);
        box.addView(note, matchWrap());
        new AlertDialog.Builder(this).setTitle("مساعد الإنترنت").setView(box)
                .setNegativeButton("إلغاء", null)
                .setNeutralButton("مسح", (d,w) -> prefs.edit().remove(P_AI_ENDPOINT).apply())
                .setPositiveButton("حفظ", (d,w) -> prefs.edit().putString(P_AI_ENDPOINT, endpoint.getText().toString().trim()).apply())
                .show();
    }

    private void askOnlineAssistant(String question, TextView answerView) {
        String endpoint = prefs.getString(P_AI_ENDPOINT, "");
        if (endpoint == null || endpoint.trim().isEmpty()) {
            answerView.setText("المساعد عبر الإنترنت غير مفعّل بعد. المساعد المحلي يعمل الآن بدون إنترنت. يمكن إعداد رابط API من: الإعدادات ← المساعد الذكي.");
            answerView.setTextColor(C_AMBER);
            return;
        }
        answerView.setText("جارِ الاتصال بالمساعد عبر الإنترنت...");
        answerView.setTextColor(C_MUTED);
        executor.execute(() -> {
            try {
                URL url = new URL(endpoint.trim());
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(12000);
                c.setReadTimeout(20000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                JSONObject req = new JSONObject();
                req.put("question", question);
                req.put("domain", "diesel_generator_maintenance");
                try (OutputStream os = c.getOutputStream()) { os.write(req.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = c.getResponseCode();
                String body = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                String reply;
                try {
                    JSONObject json = new JSONObject(body);
                    reply = json.optString("answer", json.optString("reply", body));
                } catch (Exception ignored) { reply = body; }
                final String finalReply = reply == null || reply.trim().isEmpty() ? "لم تصل إجابة من الخادم." : reply.trim();
                runOnUiThread(() -> { answerView.setText(finalReply); answerView.setTextColor(C_TEXT); });
            } catch (Exception e) {
                runOnUiThread(() -> { answerView.setText("تعذر الاتصال بالمساعد عبر الإنترنت. استخدم التحليل المحلي أو تحقق من إعداد رابط API."); answerView.setTextColor(C_RED); });
            }
        });
    }

    private void addSectionHeader(LinearLayout root, String title, String subtitle) {
        addHeader(root, true);
        Button back = smallLinkButton("‹ الرجوع للرئيسية");
        back.setOnClickListener(v -> renderHome(currentStatus, currentOffline, currentGraceDays));
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView t = text(title, 25, C_TEXT, true);
        LinearLayout.LayoutParams tp = matchWrap(); tp.setMargins(0, dp(14), 0, 0); root.addView(t, tp);
        TextView s = text(subtitle, 14, C_MUTED, false);
        LinearLayout.LayoutParams sp = matchWrap(); sp.setMargins(0, dp(3), 0, dp(14)); root.addView(s, sp);
    }

    private LinearLayout emptyCard(String title, String sub) {
        LinearLayout c = card();
        TextView t = text(title, 18, 0xFFD8E6DE, true); t.setGravity(Gravity.CENTER); c.addView(t, matchWrap());
        TextView s = text(sub, 14, C_MUTED, false); s.setGravity(Gravity.CENTER); LinearLayout.LayoutParams sp=matchWrap(); sp.setMargins(0,dp(5),0,0); c.addView(s,sp);
        return c;
    }

    private void addStatCard(LinearLayout row, String label, String value, int color) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(10), dp(13), dp(10), dp(13));
        TextView v = text(value, value.length() > 9 ? 15 : 22, color, true); v.setGravity(Gravity.CENTER); c.addView(v, matchWrap());
        TextView l = text(label, 12, C_MUTED, false); l.setGravity(Gravity.CENTER); LinearLayout.LayoutParams lp=matchWrap(); lp.setMargins(0,dp(3),0,0); c.addView(l,lp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.setMargins(dp(4),0,dp(4),0); row.addView(c,cp);
    }

    private void addMenuButton(LinearLayout row, String title, String sub, int color, View.OnClickListener listener) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(10), dp(15), dp(10), dp(15));
        android.graphics.drawable.GradientDrawable d = makeRounded(C_PANEL, 8);
        d.setStroke(dp(1), color == C_GREEN ? C_GREEN : C_BORDER);
        c.setBackground(d);
        c.setOnClickListener(listener);
        TextView t = text(title, 18, color, true); t.setGravity(Gravity.CENTER); c.addView(t, matchWrap());
        TextView subView = text(sub, 12, C_MUTED, false); subView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = matchWrap(); sp.setMargins(0, dp(4), 0, 0); c.addView(subView, sp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(isTablet() ? 118 : 104), 1f); cp.setMargins(dp(4), 0, dp(4), 0); row.addView(c, cp);
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return row;
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(6),dp(18),0); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return box;
    }

    private EditText dialogEdit(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        styleInput(e);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(5), 0, dp(5)); e.setLayoutParams(lp);
        return e;
    }

    private void styleInput(EditText e) {
        e.setTextColor(C_TEXT);
        e.setHintTextColor(C_MUTED);
        android.graphics.drawable.GradientDrawable d = makeRounded(C_PANEL_2, 8);
        d.setStroke(dp(1), C_BORDER);
        e.setBackground(d);
    }

    private JSONArray getSubscribers() {
        try { return new JSONArray(prefs.getString(P_SUBSCRIBERS, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private void saveSubscribers(JSONArray arr) { prefs.edit().putString(P_SUBSCRIBERS, arr.toString()).apply(); }
    private JSONArray getExpenses() { try { return new JSONArray(prefs.getString(P_EXPENSES, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private void saveExpenses(JSONArray arr) { prefs.edit().putString(P_EXPENSES, arr.toString()).apply(); }
    private JSONArray getReceipts() { try { return new JSONArray(prefs.getString(P_RECEIPTS, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private void saveReceipts(JSONArray arr) { prefs.edit().putString(P_RECEIPTS, arr.toString()).apply(); }

    private void replaceById(JSONArray arr, JSONObject obj) throws Exception {
        String id = obj.optString("id");
        for (int i=0;i<arr.length();i++) { JSONObject s=arr.optJSONObject(i); if(s!=null && id.equals(s.optString("id"))) { arr.put(i,obj); return; } }
        arr.put(obj);
    }

    private boolean isPaidCurrentMonth(JSONObject s) { return currentMonth().equals(s.optString("paid_month", "")); }
    private int countPaidCurrentMonth(JSONArray arr) { int n=0; for(int i=0;i<arr.length();i++){ JSONObject s=arr.optJSONObject(i); if(s!=null&&isPaidCurrentMonth(s))n++; } return n; }
    private double sumExpected(JSONArray arr) { double x=0; for(int i=0;i<arr.length();i++){ JSONObject s=arr.optJSONObject(i); if(s!=null)x+=s.optDouble("fee",0); } return x; }
    private double sumCollectedCurrentMonth(JSONArray arr) { double x=0; for(int i=0;i<arr.length();i++){ JSONObject s=arr.optJSONObject(i); if(s!=null&&isPaidCurrentMonth(s))x+=s.optDouble("paid_amount",s.optDouble("fee",0)); } return x; }
    private double sumExpensesCurrentMonth(JSONArray arr) { double x=0; for(int i=0;i<arr.length();i++){ JSONObject e=arr.optJSONObject(i); if(e!=null&&isCurrentMonthDate(e.optString("date","")))x+=e.optDouble("amount",0); } return x; }
    private boolean isCurrentMonthDate(String date) { return date != null && date.startsWith(currentMonth()); }
    private String currentMonth() { return YearMonth.now().toString(); }
    private String displayMonth(String ym) { try { YearMonth m=YearMonth.parse(ym); return String.format(Locale.US, "%02d/%d",m.getMonthValue(),m.getYear()); } catch(Exception e){ return ym; } }
    private String money(double v) { return String.format(Locale.US, "%,.0f د.ع", v); }
    private int safeInt(String s) { try { return Integer.parseInt(s.trim()); } catch(Exception e){ return 0; } }
    private double safeDouble(String s) { try { return Double.parseDouble(s.trim().replace(",","")); } catch(Exception e){ return 0; } }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.setMargins(0, dp(12), 0, 0);
        parent.addView(row, rowLp);

        TextView l = text(label, 14, C_MUTED, false);
        TextView v = text(value == null || value.isEmpty() ? "غير محدد" : value, 15, C_TEXT, true);
        v.setGravity(Gravity.LEFT);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private ScrollView newBaseScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int sidePadding = isTablet() ? 34 : 22;
        root.setPadding(dp(sidePadding), dp(isTablet() ? 28 : 24), dp(sidePadding), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (isTablet()) {
            int widthDp = getResources().getConfiguration().screenWidthDp;
            int contentDp = Math.min(Math.max(600, widthDp - 48), 960);
            ScrollView.LayoutParams lp = new ScrollView.LayoutParams(dp(contentDp), -1);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            scroll.addView(root, lp);
        } else {
            scroll.addView(root, new ScrollView.LayoutParams(-1, -1));
        }
        return scroll;
    }

    private boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private void addHeader(LinearLayout root, boolean compact) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.elyas_tech_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int h = compact ? 62 : 92;
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(-1, dp(h));
        logoLp.setMargins(0, 0, 0, dp(5));
        root.addView(logo, logoLp);

        TextView system = text("MAWLDATI / GENERATOR SYSTEM", compact ? 10 : 11, C_GREEN, true);
        system.setGravity(Gravity.CENTER);
        root.addView(system, matchWrap());

        if (!compact) {
            TextView title = text("أهلاً بك في مولدتي! 🎉", 25, C_TEXT, true);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = matchWrap(); tlp.setMargins(0, dp(6), 0, 0);
            root.addView(title, tlp);

            TextView subtitle = text(
                    "يسعدنا انضمامك. صُمم مولدتي لتسهيل إدارة المشتركين والجباية والمصروفات وطباعة الوصولات بسرعة وبساطة.",
                    13, C_MUTED, false);
            subtitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = matchWrap();
            subLp.setMargins(0, dp(5), 0, dp(15));
            root.addView(subtitle, subLp);
        } else {
            View line = new View(this);
            line.setBackgroundColor(C_GREEN);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(72), dp(2));
            llp.gravity = Gravity.CENTER_HORIZONTAL;
            llp.setMargins(0, dp(7), 0, dp(13));
            root.addView(line, llp);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        android.graphics.drawable.GradientDrawable d = makeRounded(C_PANEL, 9);
        d.setStroke(dp(1), C_BORDER);
        card.setBackground(d);
        return card;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(0xFF021008);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable d = makeRounded(C_GREEN, 8);
        d.setStroke(dp(1), 0xFF5CFF9C);
        b.setBackground(d);
        return b;
    }

    private Button outlineButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(C_TEXT);
        b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable d = makeRounded(C_PANEL_2, 8);
        d.setStroke(dp(1), C_BORDER);
        b.setBackground(d);
        return b;
    }

    private Button smallLinkButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(C_GREEN);
        b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable d = makeRounded(C_GREEN_SOFT, 7);
        d.setStroke(dp(1), C_BORDER);
        b.setBackground(d);
        return b;
    }

    private LinearLayout.LayoutParams spacedCardLp() { LinearLayout.LayoutParams lp=matchWrap(); lp.setMargins(0,dp(12),0,0); return lp; }
    private LinearLayout.LayoutParams spacedButtonLp() { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.setMargins(0,dp(10),0,0); return lp; }
    private LinearLayout.LayoutParams halfButtonLp() { return new LinearLayout.LayoutParams(0,dp(44),1f); }

    private void addFooter(LinearLayout root) {
        TextView version = text("مولدتي v" + APP_VERSION + "  •  ELYAS-TECH  •  " + SUPPORT_PHONE, 12, 0xFF71857A, false);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vLp = matchWrap();
        vLp.setMargins(0, dp(28), 0, dp(8));
        root.addView(version, vLp);
    }

    private void callSupport() {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + SUPPORT_PHONE));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, SUPPORT_PHONE, Toast.LENGTH_LONG).show();
        }
    }

    private void copyDeviceId() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Mawldati Device ID", deviceId));
            Toast.makeText(this, "تم نسخ معرّف الجهاز", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildDeviceId() {
        String raw = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (raw == null || raw.trim().isEmpty()) raw = "unknown-android-device";
        return "android-" + sha256(raw + "|com.elyastech.mawldati");
    }

    private String shortDeviceId() {
        return deviceId.length() > 20 ? deviceId.substring(0, 20) + "…" : deviceId;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String safeError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.length() > 120) return "تحقق من اتصال الإنترنت.";
        return m;
    }

    private static boolean isNotExpired(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return true;
        try {
            LocalDate end = parseDatePart(iso);
            if (end != null) return !end.isBefore(LocalDate.now());
            return Instant.parse(iso).isAfter(Instant.now());
        } catch (Exception e) { return true; }
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return "غير محدد";
        LocalDate date = parseDatePart(iso);
        if (date != null) return date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US));
        try {
            Instant i = Instant.parse(iso);
            return DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US).withZone(ZoneId.systemDefault()).format(i);
        } catch (Exception e) { return iso; }
    }

    private static long remainingDays(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return -1;
        LocalDate end = parseDatePart(iso);
        if (end == null) {
            try { end = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate(); }
            catch (Exception ignored) { return -1; }
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), end);
        return Math.max(0, days);
    }

    private static LocalDate parseDatePart(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.length() < 10) return null;
        try { return LocalDate.parse(s.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (Exception ignored) { return null; }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }

    private android.graphics.drawable.GradientDrawable makeRounded(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp)); return d;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SAVE_RECEIPT) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null || pendingReceiptPng == null) throw new Exception("write_failed");
                os.write(pendingReceiptPng);
                os.flush();
                Toast.makeText(this, "تم حفظ الوصل في ذاكرة الجهاز", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر حفظ الوصل", Toast.LENGTH_LONG).show();
            } finally {
                pendingReceiptPng = null;
            }
        } else if (requestCode == REQ_CREATE_BACKUP) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null || pendingBackupJson == null) throw new Exception("write_failed");
                os.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
                Toast.makeText(this, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر حفظ ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show();
            } finally {
                pendingBackupJson = null;
            }
        } else if (requestCode == REQ_RESTORE_BACKUP) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                String json = readAll(in);
                new AlertDialog.Builder(this)
                        .setTitle("استعادة البيانات")
                        .setMessage("سيتم استبدال بيانات المشتركين والمصروفات والوصولات المحلية بالنسخة المختارة. الترخيص لن يتغير.")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("استعادة", (d,w) -> {
                            try {
                                restoreBackupJson(json);
                                Toast.makeText(this, "تمت استعادة البيانات بنجاح", Toast.LENGTH_LONG).show();
                                renderHome(currentStatus, currentOffline, currentGraceDays);
                            } catch (Exception e) {
                                Toast.makeText(this, "ملف النسخة الاحتياطية غير صالح", Toast.LENGTH_LONG).show();
                            }
                        }).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر قراءة ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLUETOOTH_CONNECT) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingBluetoothPicker) {
                pendingBluetoothPicker = false;
                openBluetoothPrinterPicker();
            } else {
                pendingBluetoothPicker = false;
                Toast.makeText(this, "يجب السماح باتصال Bluetooth لاستخدام الطابعة", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if ("home".equals(currentScreen) || "activation".equals(currentScreen) || "loading".equals(currentScreen)) {
            super.onBackPressed();
        } else {
            renderHome(currentStatus, currentOffline, currentGraceDays);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
    }
}
