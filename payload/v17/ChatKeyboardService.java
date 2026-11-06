package com.example.gamethaitranslator;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

/**
 * User-controlled translation keyboard.
 * The user toggles the microphone. Android speech recognition ends individual
 * sessions itself: this service automatically starts the next session without
 * changing the microphone's user-visible ON state.
 *
 * Final speech -> transcript -> serial on-device translation -> InputConnection.
 * Sending a message remains an explicit action in the destination application.
 */
public final class ChatKeyboardService extends InputMethodService {
    private static final String[][] DIRECTIONS = {
            {"th", "en", "ไทย → EN"},
            {"th", "zh", "ไทย → 中文"},
            {"en", "th", "EN → ไทย"},
            {"zh", "th", "中文 → ไทย"}
    };
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Utterance> queuedSpeech = new ArrayDeque<>();
    private boolean micEnabled = false;
    private boolean recognitionRunning = false;
    private boolean inputActive = false;
    private boolean privateField = false;
    private boolean thaiKeys = true;
    private boolean capitals = false;
    private boolean symbols = false;
    private boolean processingSpeech = false;
    private int direction = 0;
    private int translationEpoch = 0;
    private int manualEpoch = 0;
    private int consecutiveErrors = 0;
    private long lastRecognitionStart = 0L;
    private String transcriptThai = "";
    private String typedOriginal = null;
    private String typedTranslation = null;
    private String voiceTargetLanguage = "";
    private SpeechRecognizer recognizer;
    private Translator translator;
    private String translatorPair = "";
    private TextView micButton;
    private TextView directionButton;
    private TextView resultLine;
    private TextView transcriptLine;
    private TextView translateButton;
    private LinearLayout keysContainer;
    private final Runnable autoRestart = this::startRecognition;

    private static final class Utterance {
        final String text;
        final String from;
        final String to;
        final int epoch;
        Utterance(String text, String from, String to, int epoch) {
            this.text = text; this.from = from; this.to = to; this.epoch = epoch;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView button(String label, int fill) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextSize(14f);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(fill, 8));
        t.setPadding(dp(2), dp(2), dp(2), dp(2));
        return t;
    }

    private void addButton(LinearLayout parent, String title, float weight,
                           int color, Runnable action) {
        TextView b = button(title, color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(45), weight);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    private LinearLayout row(LinearLayout parent, int height) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r, new LinearLayout.LayoutParams(-1, dp(height)));
        return r;
    }

    private void notifyUser(String message) {
        if (resultLine != null) resultLine.setText(message);
        else Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onCreate() {
        super.onCreate();
        int saved = getSharedPreferences("chat_settings", MODE_PRIVATE)
                .getInt("direction", 0);
        direction = (saved >= 0 && saved < DIRECTIONS.length) ? saved : 0;
        thaiKeys = DIRECTIONS[direction][0].equals("th");
    }

    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(3), dp(4), dp(3), dp(5));
        root.setBackgroundColor(0xFF1D232E);

        LinearLayout toolbar = row(root, 52);
        directionButton = button(DIRECTIONS[direction][2], 0xFF3E5270);
        LinearLayout.LayoutParams dirP = new LinearLayout.LayoutParams(0, dp(45), 2f);
        dirP.setMargins(dp(2), dp(2), dp(2), dp(2));
        toolbar.addView(directionButton, dirP);
        directionButton.setOnClickListener(v -> chooseNextDirection());

        micButton = button("🎙 พูด", 0xFF34475C);
        LinearLayout.LayoutParams micP = new LinearLayout.LayoutParams(0, dp(45), 1.6f);
        micP.setMargins(dp(2), dp(2), dp(2), dp(2));
        toolbar.addView(micButton, micP);
        micButton.setOnClickListener(v -> toggleMicrophone());

        translateButton = button("แปล", 0xFF137C6D);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(45), 1f);
        tp.setMargins(dp(2), dp(2), dp(2), dp(2));
        toolbar.addView(translateButton, tp);
        translateButton.setOnClickListener(v -> translateTypedNow());

        addButton(toolbar, "แชร์", 0.9f, 0xFF3D526B, this::shareChat);
        addButton(toolbar, "ล้าง", 0.9f, 0xFF704353, this::clearChatAll);
        addButton(toolbar, "×", 0.55f, 0xFF543F54, () -> requestHideSelf(0));

        transcriptLine = new TextView(this);
        transcriptLine.setTextSize(13f);
        transcriptLine.setTextColor(0xFFE7F4FF);
        transcriptLine.setGravity(Gravity.CENTER_VERTICAL);
        transcriptLine.setMaxLines(2);
        transcriptLine.setBackground(bg(0xFF26384B, 8));
        transcriptLine.setPadding(dp(9), dp(3), dp(9), dp(3));
        LinearLayout.LayoutParams transP = new LinearLayout.LayoutParams(-1, dp(36));
        transP.setMargins(dp(2), dp(3), dp(2), dp(2));
        root.addView(transcriptLine, transP);

        resultLine = new TextView(this);
        resultLine.setTextSize(12f);
        resultLine.setMaxLines(2);
        resultLine.setTextColor(0xFF9BE4D4);
        resultLine.setGravity(Gravity.CENTER_VERTICAL);
        resultLine.setBackground(bg(0xFF293343, 7));
        resultLine.setPadding(dp(9), dp(1), dp(9), dp(1));
        LinearLayout.LayoutParams resP = new LinearLayout.LayoutParams(-1, dp(32));
        resP.setMargins(dp(2), dp(2), dp(2), dp(3));
        root.addView(resultLine, resP);

        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysContainer);
        renderKeyboard();
        refreshDirectionUI();
        refreshMicUI();
        transcriptLine.setText("คำพูดที่ได้ยินจะแสดงที่นี่");
        notifyUser("เลือกภาษา → กดไมค์เพื่อพูดและแปลอัตโนมัติ • กดอีกครั้งเพื่อหยุด");
        return root;
    }

    private void refreshDirectionUI() {
        if (directionButton != null) directionButton.setText(DIRECTIONS[direction][2] + " ▾");
    }

    private void chooseNextDirection() {
        direction = (direction + 1) % DIRECTIONS.length;
        getSharedPreferences("chat_settings", MODE_PRIVATE).edit()
                .putInt("direction", direction).apply();
        thaiKeys = DIRECTIONS[direction][0].equals("th");
        translationEpoch++;
        queuedSpeech.clear();
        processingSpeech = false;
        closeTranslator();
        typedTranslation = null;
        typedOriginal = null;
        refreshDirectionUI();
        renderKeyboard();
        notifyUser("ภาษาที่เลือก: " + DIRECTIONS[direction][2] + " • เสียงแปลอัตโนมัติ");
        if (micEnabled) {
            stopRecognizerSession();
            mainHandler.removeCallbacks(autoRestart);
            mainHandler.postDelayed(autoRestart, 300);
        }
    }

    private void renderKeyboard() {
        if (keysContainer == null) return;
        keysContainer.removeAllViews();
        String[] lines;
        if (symbols) {
            lines = new String[]{"1234567890", "@#$%&-+()/", "*\":;!?'"};
        } else if (thaiKeys) {
            lines = new String[]{"ๅภถุึคตจขช", "ๆไำพะัีรนยบล", "ฟหกดเ้่าสวง", "ผปแอิืทมใฝ"};
        } else {
            lines = new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        }
        for (String l : lines) {
            LinearLayout r = row(keysContainer, 51);
            for (int i = 0; i < l.length(); i++) {
                final String key = l.substring(i, i + 1);
                final String text = (capitals && !thaiKeys && !symbols) ? key.toUpperCase(Locale.ROOT) : key;
                addButton(r, text, 1f, 0xFF363D4A, () -> insertText(text));
            }
        }
        LinearLayout bottom = row(keysContainer, 51);
        addButton(bottom, symbols ? "กขค" : "?123", 1.1f, 0xFF45546F, () -> {
            symbols = !symbols;
            renderKeyboard();
        });
        addButton(bottom, "⇧", 0.8f, 0xFF45546F, () -> {
            capitals = !capitals; renderKeyboard();
        });
        addButton(bottom, thaiKeys ? "EN" : "ไทย", 1f, 0xFF45546F, () -> {
            thaiKeys = !thaiKeys; symbols = false; renderKeyboard();
        });
        addButton(bottom, ",", 0.5f, 0xFF363D4A, () -> insertText(","));
        addButton(bottom, thaiKeys ? "เว้นวรรค" : "Space", 4f, 0xFF363D4A, () -> insertText(" "));
        addButton(bottom, ".", 0.5f, 0xFF363D4A, () -> insertText("."));
        addButton(bottom, "⌫", 1.1f, 0xFF45546F, this::backspace);
        addButton(bottom, "↵", 1.1f, 0xFF45546F, this::submitEditorAction);
    }

    private void insertText(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(value, 1);
        manualEpoch++;
        typedOriginal = null;
        typedTranslation = null;
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence selection = ic.getSelectedText(0);
        if (selection != null && selection.length() > 0) ic.commitText("", 1);
        else ic.deleteSurroundingText(1, 0);
        manualEpoch++;
    }

    private void submitEditorAction() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo e = getCurrentInputEditorInfo();
        int action = e == null ? EditorInfo.IME_ACTION_NONE :
                (e.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (!ic.performEditorAction(action)) ic.commitText("\n", 1);
        } else ic.commitText("\n", 1);
    }

    private String readChat() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        try {
            ExtractedText ext = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (ext != null && ext.text != null) return ext.text.toString();
        } catch (Exception ignore) {}
        CharSequence before = ic.getTextBeforeCursor(4096, 0);
        CharSequence after = ic.getTextAfterCursor(4096, 0);
        return (before == null ? "" : before.toString()) + (after == null ? "" : after.toString());
    }

    private void clearChatAll() {
        if (privateField) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean selected = ic.performContextMenuAction(android.R.id.selectAll);
        if (selected) ic.commitText("", 1);
        else {
            CharSequence before = ic.getTextBeforeCursor(8192, 0);
            CharSequence after = ic.getTextAfterCursor(8192, 0);
            ic.deleteSurroundingText(before == null ? 0 : before.length(),
                    after == null ? 0 : after.length());
        }
        transcriptThai = "";
        if (transcriptLine != null) transcriptLine.setText("คำพูดที่ได้ยินจะแสดงที่นี่");
        translationEpoch++;
        queuedSpeech.clear();
        processingSpeech = false;
        manualEpoch++;
        notifyUser("ล้างข้อความทั้งหมดแล้ว • ไมค์ยังอยู่ในสถานะเดิม");
    }

    private void shareChat() {
        if (privateField) return;
        String text = readChat().trim();
        if (text.isEmpty()) { notifyUser("ไม่มีข้อความสำหรับแชร์"); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(share, "แชร์ข้อความที่แปลแล้ว");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(chooser); }
        catch (Exception ex) { notifyUser("แอปนี้ไม่รองรับการแชร์ข้อความ"); }
    }

    private void copyChat() {
        String text = readChat();
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Game Thai Chat", text));
    }

    @Override public void onStartInput(EditorInfo attr, boolean restarting) {
        super.onStartInput(attr, restarting);
        inputActive = true;
        int cls = attr.inputType & InputType.TYPE_MASK_CLASS;
        int var = attr.inputType & InputType.TYPE_MASK_VARIATION;
        privateField = (cls == InputType.TYPE_CLASS_TEXT &&
                (var == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                 var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER &&
                 var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        manualEpoch++;
        if (privateField) {
            mainHandler.removeCallbacks(autoRestart);
            stopRecognizerSession();
        } else if (micEnabled) {
            mainHandler.removeCallbacks(autoRestart);
            mainHandler.postDelayed(autoRestart, 300);
        }
    }

    @Override public void onFinishInput() {
        inputActive = false;
        mainHandler.removeCallbacks(autoRestart);
        stopRecognizerSession();
        manualEpoch++;
        super.onFinishInput();
        // Preserve micEnabled. When the chat editor is reopened listening
        // resumes automatically until the user explicitly toggles the mic off.
    }

    private void refreshMicUI() {
        if (micButton == null) return;
        micButton.setText(micEnabled ? "🔴 ฟังอยู่" : "🎙 พูด");
        micButton.setBackground(bg(micEnabled ? 0xFF9B394A : 0xFF34475C, 8));
    }

    private void toggleMicrophone() {
        if (micEnabled) {
            micEnabled = false;
            mainHandler.removeCallbacks(autoRestart);
            if (recognizer != null && recognitionRunning) {
                // Finishes the current utterance; onResults will not restart.
                recognizer.stopListening();
            }
            refreshMicUI();
            notifyUser("ไมค์ปิดตามคำสั่ง • ข้อความที่พิมพ์และแปลยังอยู่");
        } else {
            if (privateField) { notifyUser("ไม่เปิดไมค์ในช่องรหัสผ่าน"); return; }
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                notifyUser("ต้องอนุญาตไมโครโฟนในหน้าตั้งค่าแอปก่อน");
                return;
            }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                notifyUser("เครื่องนี้ไม่มีบริการรู้จำเสียง โปรดติดตั้งบริการเสียงของ Android");
                return;
            }
            micEnabled = true;
            consecutiveErrors = 0;
            refreshMicUI();
            notifyUser("ไมค์เปิดอยู่ • ฟังต่อเนื่องจนกว่าจะกดปิดเอง");
            mainHandler.removeCallbacks(autoRestart);
            mainHandler.post(autoRestart);
        }
    }

    private void startRecognition() {
        if (!micEnabled || !inputActive || privateField || recognitionRunning) return;
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle b) {
                        consecutiveErrors = 0;
                        notifyUser("● กำลังฟัง... พูดได้เลย (กดไมค์เพื่อหยุด)");
                    }
                    @Override public void onBeginningOfSpeech() {
                        notifyUser("● กำลังรับเสียงของคุณ...");
                    }
                    @Override public void onRmsChanged(float rmsdB) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() {
                        notifyUser("● ประมวลผลเสียง... ไมค์ยังเปิดอยู่");
                    }
                    @Override public void onError(int error) {
                        recognitionRunning = false;
                        if (micEnabled) {
                            consecutiveErrors++;
                            int delay = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 350 :
                                    Math.min(3200, 450 * consecutiveErrors);
                            scheduleRestart(delay);
                        }
                    }
                    @Override public void onResults(Bundle results) {
                        recognitionRunning = false;
                        consumeSpeech(results, false);
                        consecutiveErrors = 0;
                        scheduleRestart(250);
                    }
                    @Override public void onPartialResults(Bundle partial) {
                        consumeSpeech(partial, true);
                    }
                    @Override public void onEvent(int eventType, Bundle params) { }
                });
            }
            final String source = DIRECTIONS[direction][0];
            final String locale = "th".equals(source) ? "th-TH" :
                    "zh".equals(source) ? "zh-CN" : "en-US";
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            // API 33+: request unmasked profanity. Some providers may ignore it.
            intent.putExtra("android.speech.extra.MASK_OFFENSIVE_WORDS", false);
            recognitionRunning = true;
            lastRecognitionStart = SystemClock.elapsedRealtime();
            recognizer.startListening(intent);
        } catch (Exception ex) {
            recognitionRunning = false;
            if (micEnabled) scheduleRestart(1200);
        }
    }

    private void scheduleRestart(long delay) {
        mainHandler.removeCallbacks(autoRestart);
        if (!micEnabled || !inputActive || privateField) return;
        long elapsed = SystemClock.elapsedRealtime() - lastRecognitionStart;
        long minDelay = Math.max(delay, 250 - Math.max(0L, elapsed));
        mainHandler.postDelayed(autoRestart, minDelay);
    }

    private void stopRecognizerSession() {
        mainHandler.removeCallbacks(autoRestart);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
        recognitionRunning = false;
    }

    private void consumeSpeech(Bundle data, boolean partial) {
        if (data == null || privateField) return;
        ArrayList<String> matches = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        String phrase = matches.get(0) == null ? "" : matches.get(0).trim();
        if (phrase.isEmpty()) return;
        if (partial) {
            if (transcriptLine != null) transcriptLine.setText(
                    "ได้ยิน: " + transcriptThai + (transcriptThai.isEmpty() ? "" : " ") + phrase);
            return;
        }
        transcriptThai = (transcriptThai + " " + phrase).trim();
        if (transcriptThai.length() > 700) transcriptThai = transcriptThai.substring(transcriptThai.length() - 700);
        if (transcriptLine != null) transcriptLine.setText("พูด: " + transcriptThai);
        final String from = DIRECTIONS[direction][0];
        final String to = DIRECTIONS[direction][1];
        queuedSpeech.addLast(new Utterance(phrase, from, to, translationEpoch));
        processSpeechQueue();
    }

    private void processSpeechQueue() {
        if (processingSpeech) return;
        Utterance utterance = queuedSpeech.pollFirst();
        if (utterance == null) return;
        if (utterance.epoch != translationEpoch) { processSpeechQueue(); return; }
        processingSpeech = true;
        String local = ChatSlang.lookup(utterance.from, utterance.to, utterance.text);
        if (local != null) {
            deliverVoiceTranslation(utterance, local);
            return;
        }
        translate(utterance.from, utterance.to, utterance.text,
                output -> deliverVoiceTranslation(utterance, output),
                () -> {
                    notifyUser("แปลเสียงไม่สำเร็จ • ข้อความต้นฉบับยังอยู่: " + utterance.text);
                    processingSpeech = false;
                    processSpeechQueue();
                });
    }

    private void deliverVoiceTranslation(Utterance utterance, String translation) {
        if (utterance.epoch == translationEpoch && !privateField) {
            if (translation == null || translation.trim().isEmpty() ||
                    translation.contains("*****")) {
                notifyUser("ระบบแปลคืนผลที่ไม่สมบูรณ์ • ไม่ส่งข้อความที่ถูกแทนด้วย ***");
            } else {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    CharSequence before = ic.getTextBeforeCursor(1, 0);
                    String prefix = (before != null && before.length() > 0 &&
                            !Character.isWhitespace(before.charAt(before.length() - 1))) ? " " : "";
                    ic.commitText(prefix + translation.trim(), 1);
                    notifyUser("แปลแล้ว: " + translation.trim() + " • กดส่งแชตได้เลย");
                } else {
                    notifyUser("แปลแล้ว แต่ช่องแชตไม่ได้เปิดอยู่: " + translation.trim());
                }
            }
        }
        processingSpeech = false;
        processSpeechQueue();
    }

    private interface TranslationSuccess { void result(String text); }

    private void translate(String from, String to, String source,
                           TranslationSuccess success, Runnable failure) {
        String pair = from + ">" + to;
        if (translator == null || !pair.equals(translatorPair)) {
            closeTranslator();
            translatorPair = pair;
            try {
                translator = Translation.getClient(new TranslatorOptions.Builder()
                        .setSourceLanguage(from).setTargetLanguage(to).build());
            } catch (Exception ignored) { failure.run(); return; }
        }
        final Translator current = translator;
        current.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(unused -> current.translate(source)
                        .addOnSuccessListener(success::result)
                        .addOnFailureListener(error -> failure.run()))
                .addOnFailureListener(error -> failure.run());
    }

    private void translateTypedNow() {
        if (privateField) { notifyUser("ไม่แปลช่องรหัสผ่าน"); return; }
        final String source = readChat().trim();
        if (source.isEmpty()) { notifyUser("พิมพ์ข้อความก่อน"); return; }
        final String from = DIRECTIONS[direction][0];
        final String to = DIRECTIONS[direction][1];
        final int ticket = ++manualEpoch;
        String fixed = ChatSlang.lookup(from, to, source);
        if (fixed != null) {
            putManualTranslation(ticket, source, fixed);
            return;
        }
        notifyUser("กำลังแปลข้อความที่พิมพ์...");
        translate(from, to, source,
                text -> putManualTranslation(ticket, source, text),
                () -> { if (ticket == manualEpoch) notifyUser("แปลข้อความไม่สำเร็จ"); });
    }

    private void putManualTranslation(int ticket, String original, String translated) {
        if (ticket != manualEpoch || privateField) return;
        if (translated == null || translated.trim().isEmpty() || translated.contains("*****")) {
            notifyUser("คำแปลถูกกรองหรือไม่สมบูรณ์ • ไม่แทนข้อความเดิม");
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !original.equals(readChat().trim())) {
            notifyUser("ช่องแชตเปลี่ยนไปแล้ว กรุณาแปลใหม่"); return;
        }
        ic.beginBatchEdit();
        boolean selected = ic.performContextMenuAction(android.R.id.selectAll);
        if (selected) ic.commitText(translated.trim(), 1);
        else {
            CharSequence before = ic.getTextBeforeCursor(8192, 0);
            CharSequence after = ic.getTextAfterCursor(8192, 0);
            ic.deleteSurroundingText(before == null ? 0 : before.length(),
                    after == null ? 0 : after.length());
            ic.commitText(translated.trim(), 1);
        }
        ic.endBatchEdit();
        notifyUser("ใส่คำแปลลงช่องแชตแล้ว • กดส่งได้เลย");
    }

    private void closeTranslator() {
        Translator old = translator;
        translator = null;
        translatorPair = "";
        if (old != null) old.close();
    }

    @Override public void onDestroy() {
        micEnabled = false;
        inputActive = false;
        translationEpoch++;
        mainHandler.removeCallbacks(autoRestart);
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        closeTranslator();
        super.onDestroy();
    }
}
