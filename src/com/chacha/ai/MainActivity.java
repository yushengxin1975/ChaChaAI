package com.chacha.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.widget.ImageView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import com.keanbin.pinyinime.PinyinDecoderService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "ChaChaAIPrefs";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_FONT = "font_size";
    private static final String KEY_ENABLE_BUILTIN_IME = "enable_builtin_pinyin_ime";

    // 默认预设的阿里云服务器地址与密钥（对应 htc chacha 会话标准 80 端口服务）
    private static final String DEFAULT_SERVER = "";
    private static final String DEFAULT_TOKEN = "";
    private static final int DEFAULT_FONT = 18;

    private TextView tvStatusDot;
    private TextView tvTitle;
    private ListView lvChat;
    private EinkEditText etInput;
    private Button btnSend;
    private Button btnNew;
    private Button btnHistory;
    private Button btnSettings;

    // 墨水屏物理键盘拼音候选条组件
    private LinearLayout llCandidateBar;
    private View vCandidateDivider;
    private TextView tvComposing;
    private TextView tvCand1, tvCand2, tvCand3, tvCand4, tvCand5, tvCand6, tvCand7, tvCand8, tvCand9;
    private Button btnCandPrev, btnCandNext;

    // 底部栏控制按钮
    private Button btnImeMode;
    private Button btnSoftKbd;

    // 物理键盘输入状态与流内连贯造词状态
    private final StringBuilder pinyinBuffer = new StringBuilder();
    private final StringBuilder composingSelectedWords = new StringBuilder();
    private final StringBuilder composingFullPinyin = new StringBuilder();
    private List<String> currentCandidates = new ArrayList<String>();
    private int candidatePageIndex = 0;
    private static final int CANDIDATES_PER_PAGE = 9;
    private boolean useBuiltInIme = true;
    private boolean isChineseMode = true;
    private long lastShiftDownTime = 0;
    private long lastHandledEventTime = -1;
    private int lastHandledKeyCode = -1;
    private int lastHandledAction = -1;
    private int lastHandledRepeat = -1;
    private int selectingCandidateKeyCode = -1;

    private List<ChatMessage> messageList;
    private ChatAdapter adapter;
    private SharedPreferences prefs;

    private ChatSession currentSession;

    private boolean isSending = false;
    private boolean continueSession = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 尝试从持久化备份文件恢复配置（覆盖安装/卸载重装保护）
        restoreSettingsFromBackup();

        // 预设默认服务器地址与密钥，自动纠正历史缓存中的 :8088 端口
        String curServer = prefs.getString(KEY_SERVER, "").trim();
        if (curServer.contains(":8088")) {
            curServer = curServer.replace(":8088", "");
            prefs.edit().putString(KEY_SERVER, curServer).commit();
        } else if (curServer.length() == 0 && DEFAULT_SERVER.length() > 0) {
            prefs.edit().putString(KEY_SERVER, DEFAULT_SERVER).commit();
        }
        String curToken = prefs.getString(KEY_TOKEN, "").trim();
        if (curToken.length() == 0 && DEFAULT_TOKEN.length() > 0) {
            prefs.edit().putString(KEY_TOKEN, DEFAULT_TOKEN).commit();
        }

        useBuiltInIme = prefs.getBoolean(KEY_ENABLE_BUILTIN_IME, true);

        // 检查并请求外部存储权限，立即触发多层持久化目录的会话与词库自愈同步
        checkAndRequestStoragePermissions();
        SessionManager.ensureInitialized(this);
        UserPhraseManager.getInstance().load(this);

        tvStatusDot = (TextView) findViewById(R.id.tv_status_dot);
        tvTitle = (TextView) findViewById(R.id.tv_title);
        lvChat = (ListView) findViewById(R.id.lv_chat);
        etInput = (EinkEditText) findViewById(R.id.et_input);
        etInput.setKeyPreImeListener(new EinkEditText.KeyPreImeListener() {
            @Override
            public boolean onKeyPreIme(KeyEvent event) {
                if (useBuiltInIme) {
                    return handleHardwareKey(event);
                }
                return false;
            }
        });
        btnSend = (Button) findViewById(R.id.btn_send);
        btnNew = (Button) findViewById(R.id.btn_new);
        btnHistory = (Button) findViewById(R.id.btn_history);
        btnSettings = (Button) findViewById(R.id.btn_settings);

        // 绑定拼音候选条组件
        llCandidateBar = (LinearLayout) findViewById(R.id.ll_candidate_bar);
        vCandidateDivider = findViewById(R.id.v_candidate_divider);
        tvComposing = (TextView) findViewById(R.id.tv_composing);
        tvCand1 = (TextView) findViewById(R.id.tv_cand_1);
        tvCand2 = (TextView) findViewById(R.id.tv_cand_2);
        tvCand3 = (TextView) findViewById(R.id.tv_cand_3);
        tvCand4 = (TextView) findViewById(R.id.tv_cand_4);
        tvCand5 = (TextView) findViewById(R.id.tv_cand_5);
        tvCand6 = (TextView) findViewById(R.id.tv_cand_6);
        tvCand7 = (TextView) findViewById(R.id.tv_cand_7);
        tvCand8 = (TextView) findViewById(R.id.tv_cand_8);
        tvCand9 = (TextView) findViewById(R.id.tv_cand_9);
        btnCandPrev = (Button) findViewById(R.id.btn_cand_prev);
        btnCandNext = (Button) findViewById(R.id.btn_cand_next);

        btnImeMode = (Button) findViewById(R.id.btn_ime_mode);
        btnSoftKbd = (Button) findViewById(R.id.btn_soft_kbd);

        btnCandPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (candidatePageIndex > 0) {
                    candidatePageIndex--;
                    renderCandidatePage();
                }
            }
        });

        btnCandNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((candidatePageIndex + 1) * CANDIDATES_PER_PAGE < currentCandidates.size()) {
                    candidatePageIndex++;
                    renderCandidatePage();
                }
            }
        });

        btnImeMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleImeMode();
            }
        });

        btnSoftKbd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSoftKeyboard();
            }
        });

        messageList = new ArrayList<ChatMessage>();
        adapter = new ChatAdapter(this, messageList);
        int savedFont = prefs.getInt(KEY_FONT, DEFAULT_FONT);
        if (savedFont == 22 || savedFont < 14) {
            savedFont = 18;
            prefs.edit().putInt(KEY_FONT, 18).commit();
        }
        adapter.setFontSize(savedFont);
        etInput.setTextSize(Math.max(16, savedFont - 2));
        lvChat.setAdapter(adapter);

        // 每次启动默认开启独立新会话（如需继续历史会话，点击右上角【历史】选择即可）
        initNewSessionOnLaunch();

        // 配置输入法与键盘联动行为
        updateSoftKeyboardSuppression();

        // 异步预加载 AOSP 谷歌拼音原生引擎与二进制词库
        new Thread(new Runnable() {
            @Override
            public void run() {
                AospPinyinEngine.getInstance().init(getApplicationContext());
                final String err = PinyinDecoderService.getLoadError() != null ?
                        PinyinDecoderService.getLoadError() :
                        AospPinyinEngine.getInstance().getInitError();
                if (err != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "拼音引擎状态异常: " + err, Toast.LENGTH_LONG).show();
                            addSystemMessage("【拼音引擎异常】" + err);
                        }
                    });
                }
            }
        }).start();

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });

        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewChat();
            }
        });

        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistoryDialog();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        tvTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAboutDialog();
            }
        });

        new PingServerTask().execute();
    }

    private void initNewSessionOnLaunch() {
        currentSession = SessionManager.createNewSession(this);
        SessionManager.setCurrentSessionId(this, currentSession.id);
        messageList.clear();
        adapter.notifyDataSetChanged();
        continueSession = false;
        tvTitle.setText("新会话");
        addSystemMessage("欢迎使用 ChaCha AI 墨水屏助手！已为您开启独立新会话。\n如需继续历史会话，请点击右上角【历史】选择切换。");
        new ResetSessionTask(currentSession.id).execute();
    }

    private void loadCurrentSession() {
        String activeId = SessionManager.getCurrentSessionId(this);
        if (activeId != null) {
            currentSession = SessionManager.loadSession(this, activeId);
        }

        if (currentSession == null || currentSession.id == null) {
            List<ChatSession> list = SessionManager.getSessionList(this);
            if (!list.isEmpty()) {
                currentSession = SessionManager.loadSession(this, list.get(0).id);
            } else {
                currentSession = SessionManager.createNewSession(this);
            }
        }

        messageList.clear();
        if (currentSession.messages != null && !currentSession.messages.isEmpty()) {
            messageList.addAll(currentSession.messages);
            adapter.notifyDataSetChanged();
            scrollToBottom();
            continueSession = true;
            tvTitle.setText(currentSession.title);
        } else {
            addSystemMessage("欢迎使用 ChaCha AI 墨水屏助手！外接蓝牙键盘输入回车即可提问。");
        }
    }

    private void startNewChat() {
        if (!messageList.isEmpty()) {
            persistCurrentSession();
        }

        currentSession = SessionManager.createNewSession(this);
        messageList.clear();
        adapter.notifyDataSetChanged();
        continueSession = false;
        tvTitle.setText("新会话");

        addSystemMessage("【新会话已开启，屏幕已清空，以往会话请在【历史】中查看】");
        new ResetSessionTask(currentSession.id).execute();
        Toast.makeText(this, "已开启新会话，以往对话已完整保存", Toast.LENGTH_SHORT).show();
    }

    private void persistCurrentSession() {
        if (currentSession == null) return;
        boolean hasActualMessage = false;
        for (ChatMessage m : messageList) {
            if (m.role == ChatMessage.ROLE_USER || m.role == ChatMessage.ROLE_AI) {
                hasActualMessage = true;
                break;
            }
        }
        if (!hasActualMessage) return;

        currentSession.messages = new ArrayList<ChatMessage>(messageList);

        if ("新会话".equals(currentSession.title) || currentSession.title == null || currentSession.title.length() == 0) {
            for (ChatMessage m : messageList) {
                if (m.role == ChatMessage.ROLE_USER && m.content != null && m.content.length() > 0) {
                    String t = m.content.trim();
                    if (t.length() > 16) t = t.substring(0, 16) + "...";
                    currentSession.title = t;
                    tvTitle.setText(t);
                    break;
                }
            }
        }
        SessionManager.saveSession(this, currentSession);
    }

    private void showHistoryDialog() {
        persistCurrentSession();

        final List<ChatSession> sessionList = SessionManager.getSessionList(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("历史会话记录 (点击切换 · 选中删除)");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_history, null);
        final ListView lvHistory = (ListView) view.findViewById(R.id.lv_history);
        final TextView tvEmpty = (TextView) view.findViewById(R.id.tv_history_empty);

        final AlertDialog dialog = builder.setView(view)
                .setNegativeButton("关闭", null)
                .create();

        final HistoryAdapter[] adapterHolder = new HistoryAdapter[1];

        adapterHolder[0] = new HistoryAdapter(this, sessionList, new HistoryAdapter.OnHistoryActionListener() {
            @Override
            public void onSelectSession(ChatSession session) {
                dialog.dismiss();
                if (currentSession != null && session.id.equals(currentSession.id)) {
                    return;
                }
                currentSession = SessionManager.loadSession(MainActivity.this, session.id);
                SessionManager.setCurrentSessionId(MainActivity.this, session.id);
                messageList.clear();
                messageList.addAll(currentSession.messages);
                adapter.notifyDataSetChanged();
                scrollToBottom();
                tvTitle.setText(currentSession.title);
                continueSession = true;
                Toast.makeText(MainActivity.this, "已切换至会话: " + currentSession.title, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteSession(final ChatSession session) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定删除会话「" + session.title + "」？删除后此历史记录将彻底清除。")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                SessionManager.deleteSession(MainActivity.this, session.id);

                                for (int i = 0; i < sessionList.size(); i++) {
                                    if (sessionList.get(i).id.equals(session.id)) {
                                        sessionList.remove(i);
                                        break;
                                    }
                                }
                                adapterHolder[0].notifyDataSetChanged();
                                if (sessionList.isEmpty()) {
                                    tvEmpty.setVisibility(View.VISIBLE);
                                    lvHistory.setVisibility(View.GONE);
                                }

                                if (currentSession != null && session.id.equals(currentSession.id)) {
                                    currentSession = SessionManager.createNewSession(MainActivity.this);
                                    messageList.clear();
                                    adapter.notifyDataSetChanged();
                                    tvTitle.setText("新会话");
                                    addSystemMessage("【当前会话已删除，已开启新会话】");
                                }
                                Toast.makeText(MainActivity.this, "已删除该会话", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        lvHistory.setAdapter(adapterHolder[0]);

        if (sessionList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvHistory.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvHistory.setVisibility(View.VISIBLE);
        }

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSoftKeyboardSuppression();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistCurrentSession();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AospPinyinEngine.getInstance().destroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateSoftKeyboardSuppression();
    }

    /**
     * 控制软键盘/输入法行为：
     * 如果开启了内置物理键盘拼音输入法（默认开启），彻底隐藏软键盘（setShowSoftInputOnFocus 为 false），
     * 避免系统全键盘弹出遮挡屏幕；同时外接蓝牙键盘按键通过内置 PinyinEngine 直接输入中文！
     */
    private void updateSoftKeyboardSuppression() {
        useBuiltInIme = prefs.getBoolean(KEY_ENABLE_BUILTIN_IME, true);
        boolean showVirtualKbd = !useBuiltInIme;
        try {
            Method method = EditText.class.getMethod("setShowSoftInputOnFocus", boolean.class);
            method.setAccessible(true);
            method.invoke(etInput, showVirtualKbd);
        } catch (Exception e) {
            try {
                Method method = EditText.class.getMethod("setSoftInputShownOnFocus", boolean.class);
                method.setAccessible(true);
                method.invoke(etInput, showVirtualKbd);
            } catch (Exception ignored) {}
        }
        if (btnImeMode != null) {
            btnImeMode.setText(isChineseMode ? "拼" : "英");
            btnImeMode.setVisibility(useBuiltInIme ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (useBuiltInIme && etInput != null) {
            int keyCode = event.getKeyCode();
            // 在拼音输入状态下拦截 Back 键，取消拼音输入条而不是退出应用
            if (keyCode == KeyEvent.KEYCODE_BACK && pinyinBuffer.length() > 0) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    clearPinyinBuffer();
                }
                return true;
            }
            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (handleHardwareKey(event)) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleHardwareKey(KeyEvent event) {
        if (event == null) return false;

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        long eventTime = event.getEventTime();
        int repeat = event.getRepeatCount();

        // 核心防双重派发：Android 输入管线在 ViewPreImeInputStage 与 Activity.dispatchKeyEvent
        // 会对同一个 KeyEvent 对象进行二次派发。
        // 如果时间戳、键码、动作和重复次数完全相同，直接消费，杜绝重复输入！
        if (eventTime == lastHandledEventTime && keyCode == lastHandledKeyCode &&
            action == lastHandledAction && repeat == lastHandledRepeat) {
            return true;
        }

        // 硬件防抖：同一个物理按键在 35ms 内连续触发 ACTION_DOWN（防止键盘按键物理抖动）
        if (action == KeyEvent.ACTION_DOWN && keyCode == lastHandledKeyCode &&
            (eventTime - lastHandledEventTime >= 0 && eventTime - lastHandledEventTime < 35)) {
            return true;
        }

        lastHandledEventTime = eventTime;
        lastHandledKeyCode = keyCode;
        lastHandledAction = action;
        lastHandledRepeat = repeat;

        // Shift 键单独轻点：切换中/英文模式
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (action == KeyEvent.ACTION_DOWN) {
                lastShiftDownTime = event.getEventTime();
            } else if (action == KeyEvent.ACTION_UP) {
                if (event.getEventTime() - lastShiftDownTime < 350) {
                    toggleImeMode();
                    return true;
                }
            }
            return false;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            lastShiftDownTime = 0;
        }

        // 字母键 A-Z
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (isChineseMode) {
                    char c = (char) ('a' + (keyCode - KeyEvent.KEYCODE_A));
                    pinyinBuffer.append(c);
                    composingFullPinyin.append(c);
                    updateCandidates();
                } else {
                    char c = (char) (event.isShiftPressed() ? ('A' + (keyCode - KeyEvent.KEYCODE_A)) : ('a' + (keyCode - KeyEvent.KEYCODE_A)));
                    insertTextAtCursor(String.valueOf(c));
                }
            }
            return true;
        }

        // 数字键 1-9 / 小键盘 1-9 选词
        if ((keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) ||
            (keyCode >= KeyEvent.KEYCODE_NUMPAD_1 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)) {
            if (pinyinBuffer.length() > 0 || currentCandidates.size() > 0) {
                if (action == KeyEvent.ACTION_DOWN) {
                    selectingCandidateKeyCode = keyCode;
                    int base = (keyCode >= KeyEvent.KEYCODE_NUMPAD_1) ? (keyCode - KeyEvent.KEYCODE_NUMPAD_1) : (keyCode - KeyEvent.KEYCODE_1);
                    int idx = base + candidatePageIndex * CANDIDATES_PER_PAGE;
                    selectCandidate(idx);
                } else if (action == KeyEvent.ACTION_UP && selectingCandidateKeyCode == keyCode) {
                    selectingCandidateKeyCode = -1;
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP && selectingCandidateKeyCode == keyCode) {
                selectingCandidateKeyCode = -1;
                return true;
            }
            if (event.isShiftPressed()) {
                return false;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                selectingCandidateKeyCode = -1;
                int num = (keyCode >= KeyEvent.KEYCODE_NUMPAD_1) ? (keyCode - KeyEvent.KEYCODE_NUMPAD_1 + 1) : (keyCode - KeyEvent.KEYCODE_1 + 1);
                insertTextAtCursor(String.valueOf(num));
            }
            return true;
        }

        // 空格键：拼音中默认选择第 1 个候选词；非拼音则输入空格
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (pinyinBuffer.length() > 0 || currentCandidates.size() > 0 || composingSelectedWords.length() > 0) {
                if (action == KeyEvent.ACTION_DOWN) {
                    selectingCandidateKeyCode = keyCode;
                    if (!currentCandidates.isEmpty()) {
                        int idx = candidatePageIndex * CANDIDATES_PER_PAGE;
                        selectCandidate(idx);
                    } else if (composingSelectedWords.length() > 0) {
                        insertTextAtCursor(composingSelectedWords.toString());
                        clearPinyinBuffer();
                    }
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP && selectingCandidateKeyCode == keyCode) {
                selectingCandidateKeyCode = -1;
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                selectingCandidateKeyCode = -1;
                insertTextAtCursor(" ");
            }
            return true;
        }

        // 回车键：拼音中将已选字词及拼音原始字母直接上屏；非拼音则发送消息（Shift+回车 插入换行）
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (pinyinBuffer.length() > 0 || composingSelectedWords.length() > 0) {
                if (action == KeyEvent.ACTION_DOWN) {
                    selectingCandidateKeyCode = keyCode;
                    String toCommit = composingSelectedWords.toString() + pinyinBuffer.toString();
                    insertTextAtCursor(toCommit);
                    clearPinyinBuffer();
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP && selectingCandidateKeyCode == keyCode) {
                selectingCandidateKeyCode = -1;
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                selectingCandidateKeyCode = -1;
                if (event.isShiftPressed()) {
                    insertTextAtCursor("\n");
                } else {
                    sendMessage();
                }
            }
            return true;
        }

        // 数字键 0 / 小键盘 0
        if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_NUMPAD_0) {
            if (pinyinBuffer.length() > 0) {
                return true;
            }
            if (action == KeyEvent.ACTION_UP && selectingCandidateKeyCode == keyCode) {
                selectingCandidateKeyCode = -1;
                return true;
            }
            if (event.isShiftPressed()) {
                return false;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                selectingCandidateKeyCode = -1;
                insertTextAtCursor("0");
            }
            return true;
        }

        // 退格键与删除键（全面兼容 Backspace、Delete、Clear、Ctrl+H、ASCII 8/127）
        int unicode = event.getUnicodeChar();
        boolean isDeleteKey = (keyCode == KeyEvent.KEYCODE_DEL ||
                               keyCode == KeyEvent.KEYCODE_FORWARD_DEL ||
                               keyCode == KeyEvent.KEYCODE_CLEAR ||
                               unicode == 8 ||
                               unicode == 127 ||
                               (keyCode == KeyEvent.KEYCODE_H && event.isCtrlPressed()));

        if (isDeleteKey) {
            if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_MULTIPLE) {
                if (pinyinBuffer.length() > 0) {
                    pinyinBuffer.deleteCharAt(pinyinBuffer.length() - 1);
                    if (composingFullPinyin.length() > 0) {
                        composingFullPinyin.deleteCharAt(composingFullPinyin.length() - 1);
                    }
                    if (pinyinBuffer.length() == 0) {
                        if (composingSelectedWords.length() > 0) {
                            renderCandidatePage();
                        } else {
                            clearPinyinBuffer();
                        }
                    } else {
                        updateCandidates();
                    }
                } else if (composingSelectedWords.length() > 0) {
                    composingSelectedWords.deleteCharAt(composingSelectedWords.length() - 1);
                    if (composingSelectedWords.length() > 0) {
                        renderCandidatePage();
                    } else {
                        clearPinyinBuffer();
                    }
                } else {
                    deleteTextBeforeCursor();
                }
            }
            return true;
        }

        // 左上角 ~ / ` / Esc 复合键（针对航世 HB188S 等紧凑折叠键盘特殊适配）
        if (keyCode == KeyEvent.KEYCODE_GRAVE || (keyCode == KeyEvent.KEYCODE_ESCAPE && event.isShiftPressed())) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (pinyinBuffer.length() > 0 || composingSelectedWords.length() > 0) {
                    insertTextAtCursor(composingSelectedWords.toString() + pinyinBuffer.toString());
                    clearPinyinBuffer();
                }
                if (event.isShiftPressed() || event.getUnicodeChar() == '~') {
                    insertTextAtCursor("~");
                } else {
                    insertTextAtCursor(isChineseMode ? "·" : "`");
                }
            }
            return true;
        }

        // ESC 键：取消当前拼音输入（未按 Shift 时生效）
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (pinyinBuffer.length() > 0 || composingSelectedWords.length() > 0) {
                if (action == KeyEvent.ACTION_DOWN) {
                    clearPinyinBuffer();
                }
                return true;
            }
            return false;
        }

        // 方向键与光标移动（左右键光标位移、上下/Home/End 首尾跳转）
        boolean isLeftKey = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                             keyCode == 282 /* KEYCODE_SYSTEM_NAVIGATION_LEFT */ ||
                             keyCode == 260 /* KEYCODE_NAVIGATE_PREVIOUS */);
        boolean isRightKey = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                              keyCode == 283 /* KEYCODE_SYSTEM_NAVIGATION_RIGHT */ ||
                              keyCode == 261 /* KEYCODE_NAVIGATE_NEXT */);
        boolean isUpKey = (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                           keyCode == 280 /* KEYCODE_SYSTEM_NAVIGATION_UP */ ||
                           keyCode == KeyEvent.KEYCODE_MOVE_HOME);
        boolean isDownKey = (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                             keyCode == 281 /* KEYCODE_SYSTEM_NAVIGATION_DOWN */ ||
                             keyCode == KeyEvent.KEYCODE_MOVE_END);

        if (isLeftKey) {
            if (pinyinBuffer.length() > 0) {
                clearPinyinBuffer();
            }
            if (action == KeyEvent.ACTION_DOWN && etInput != null) {
                etInput.requestFocus();
                Editable text = etInput.getText();
                int len = (text != null) ? text.length() : 0;
                if (len > 0) {
                    int start = etInput.getSelectionStart();
                    int end = etInput.getSelectionEnd();
                    if (start < 0) start = len;
                    if (end < 0) end = len;
                    if (event.isShiftPressed()) {
                        etInput.setSelection(Math.max(0, start - 1), end);
                    } else {
                        int target = (start != end) ? Math.min(start, end) : Math.max(0, start - 1);
                        etInput.setSelection(target);
                    }
                }
                etInput.invalidate();
            }
            return true;
        }

        if (isRightKey) {
            if (pinyinBuffer.length() > 0) {
                clearPinyinBuffer();
            }
            if (action == KeyEvent.ACTION_DOWN && etInput != null) {
                etInput.requestFocus();
                Editable text = etInput.getText();
                int len = (text != null) ? text.length() : 0;
                if (len > 0) {
                    int start = etInput.getSelectionStart();
                    int end = etInput.getSelectionEnd();
                    if (start < 0) start = len;
                    if (end < 0) end = len;
                    if (event.isShiftPressed()) {
                        etInput.setSelection(start, Math.min(len, end + 1));
                    } else {
                        int target = (start != end) ? Math.max(start, end) : Math.min(len, end + 1);
                        etInput.setSelection(target);
                    }
                }
                etInput.invalidate();
            }
            return true;
        }

        if (isUpKey) {
            if (action == KeyEvent.ACTION_DOWN && etInput != null) {
                etInput.requestFocus();
                etInput.setSelection(0);
                etInput.invalidate();
            }
            return true;
        }

        if (isDownKey) {
            if (action == KeyEvent.ACTION_DOWN && etInput != null) {
                etInput.requestFocus();
                Editable text = etInput.getText();
                int len = (text != null) ? text.length() : 0;
                etInput.setSelection(len);
                etInput.invalidate();
            }
            return true;
        }

        // 翻页键：- / = / [ / ] / PgUp / PgDn
        if (pinyinBuffer.length() > 0) {
            if (keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_LEFT_BRACKET || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                if (action == KeyEvent.ACTION_DOWN && candidatePageIndex > 0) {
                    candidatePageIndex--;
                    renderCandidatePage();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                if (action == KeyEvent.ACTION_DOWN && (candidatePageIndex + 1) * CANDIDATES_PER_PAGE < currentCandidates.size()) {
                    candidatePageIndex++;
                    renderCandidatePage();
                }
                return true;
            }
        }

        // 逗号与句号翻页
        if (pinyinBuffer.length() > 0) {
            if (keyCode == KeyEvent.KEYCODE_COMMA) {
                if (action == KeyEvent.ACTION_DOWN && candidatePageIndex > 0) {
                    candidatePageIndex--;
                    renderCandidatePage();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PERIOD) {
                if (action == KeyEvent.ACTION_DOWN && (candidatePageIndex + 1) * CANDIDATES_PER_PAGE < currentCandidates.size()) {
                    candidatePageIndex++;
                    renderCandidatePage();
                }
                return true;
            }
        }

        // 浏览翻页：不在拼音输入状态时，按 PgUp / PgDn 直接滚动聊天记录（墨水屏无闪烁整页滚动）
        if (pinyinBuffer.length() == 0) {
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                if (action == KeyEvent.ACTION_DOWN && lvChat != null) {
                    lvChat.scrollListBy(-lvChat.getHeight() * 3 / 4);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                if (action == KeyEvent.ACTION_DOWN && lvChat != null) {
                    lvChat.scrollListBy(lvChat.getHeight() * 3 / 4);
                }
                return true;
            }
        }

        // 中文模式下的全角标点与常用符号转换
        if (isChineseMode && pinyinBuffer.length() == 0) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.isShiftPressed()) {
                    if (keyCode == KeyEvent.KEYCODE_SLASH) { // ? -> ？
                        insertTextAtCursor("？");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_1) { // ! -> ！
                        insertTextAtCursor("！");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_SEMICOLON) { // : -> ：
                        insertTextAtCursor("：");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_9) { // ( -> （
                        insertTextAtCursor("（");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_0) { // ) -> ）
                        insertTextAtCursor("）");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_COMMA) { // < -> 《
                        insertTextAtCursor("《");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_PERIOD) { // > -> 》
                        insertTextAtCursor("》");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_6) { // ^ -> ……
                        insertTextAtCursor("……");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_MINUS) { // _ -> ——
                        insertTextAtCursor("——");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_4) { // $ -> ￥
                        insertTextAtCursor("￥");
                        return true;
                    }
                } else {
                    if (keyCode == KeyEvent.KEYCODE_COMMA) {
                        insertTextAtCursor("，");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_PERIOD) {
                        insertTextAtCursor("。");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_SEMICOLON) { // ; -> ；
                        insertTextAtCursor("；");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET) { // [ -> 【
                        insertTextAtCursor("【");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET) { // ] -> 】
                        insertTextAtCursor("】");
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACKSLASH) { // \ -> 、
                        insertTextAtCursor("、");
                        return true;
                    }
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_SLASH || keyCode == KeyEvent.KEYCODE_1 ||
                    keyCode == KeyEvent.KEYCODE_SEMICOLON || keyCode == KeyEvent.KEYCODE_9 ||
                    keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_COMMA ||
                    keyCode == KeyEvent.KEYCODE_PERIOD || keyCode == KeyEvent.KEYCODE_6 ||
                    keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_4 ||
                    keyCode == KeyEvent.KEYCODE_LEFT_BRACKET || keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET ||
                    keyCode == KeyEvent.KEYCODE_BACKSLASH) {
                    return true;
                }
            }
        }

        // 通用兜底：处理所有标准可打印 ASCII 字符与物理键盘符号（~, `, @, #, $, %, ^, &, *, +, -, =, /, etc.）
        if (pinyinBuffer.length() == 0) {
            if (action == KeyEvent.ACTION_DOWN) {
                int u = event.getUnicodeChar();
                if (u >= 32 && u < 65535 && u != 127) {
                    insertTextAtCursor(String.valueOf((char) u));
                    return true;
                }
            } else if (action == KeyEvent.ACTION_UP) {
                int u = event.getUnicodeChar();
                if (u >= 32 && u < 65535 && u != 127) {
                    return true;
                }
            }
        }

        return false;
    }

    private void insertTextAtCursor(String text) {
        if (text == null || text.length() == 0 || etInput == null) return;
        etInput.requestFocus();
        Editable editable = etInput.getText();
        int len = (editable != null) ? editable.length() : 0;
        int start = etInput.getSelectionStart();
        int end = etInput.getSelectionEnd();
        if (start < 0) start = len;
        if (end < 0) end = len;
        int s = Math.min(start, end);
        int e = Math.max(start, end);
        if (editable != null) {
            editable.replace(s, e, text, 0, text.length());
        }
        etInput.setSelection(s + text.length());
        etInput.invalidate();
    }

    private void deleteTextBeforeCursor() {
        if (etInput == null) return;
        etInput.requestFocus();
        Editable text = etInput.getText();
        if (text == null || text.length() == 0) return;
        int start = etInput.getSelectionStart();
        int end = etInput.getSelectionEnd();
        if (start < 0) start = text.length();
        if (end < 0) end = text.length();
        int s = Math.min(start, end);
        int e = Math.max(start, end);
        if (s != e) {
            text.delete(s, e);
        } else if (s > 0) {
            text.delete(s - 1, s);
        }
        etInput.invalidate();
    }

    private void selectCandidate(int index) {
        if (index >= 0 && index < currentCandidates.size()) {
            String word = currentCandidates.get(index);
            String pureWord = word.startsWith("★") ? word.substring(1) : word;
            int wordLen = pureWord.length();
            int consumedLen = PinyinSplitter.getConsumedPinyinLength(pinyinBuffer.toString(), wordLen);

            composingSelectedWords.append(pureWord);

            if (consumedLen > 0 && consumedLen < pinyinBuffer.length()) {
                // 部分音节被消费，保留剩余拼音，继续让用户选择后续音节对应的字词（流内连贯造词）
                pinyinBuffer.delete(0, consumedLen);
                updateCandidates();
                return;
            } else {
                // 全部音节已完成选择，生成最终词组并上屏
                String finalPhrase = composingSelectedWords.toString();
                insertTextAtCursor(finalPhrase);

                // 如果是用户逐字或挑选的多字词组（>=2字），自动学习记录到自造词库！
                if (finalPhrase.length() >= 2) {
                    String fullPy = composingFullPinyin.toString();
                    if (fullPy.isEmpty()) fullPy = pinyinBuffer.toString();
                    UserPhraseManager.getInstance().addPhrase(this, finalPhrase, fullPy);
                }

                // 词频记录
                AospPinyinEngine.getInstance().chooseCandidate(composingFullPinyin.toString(), index, finalPhrase);
            }
        }
        clearPinyinBuffer();
        if (etInput != null) {
            etInput.requestFocus();
        }
    }

    private void clearPinyinBuffer() {
        pinyinBuffer.setLength(0);
        composingSelectedWords.setLength(0);
        composingFullPinyin.setLength(0);
        currentCandidates.clear();
        candidatePageIndex = 0;
        AospPinyinEngine.getInstance().reset();
        if (llCandidateBar != null) {
            llCandidateBar.setVisibility(View.GONE);
            llCandidateBar.requestLayout();
        }
        if (vCandidateDivider != null) {
            vCandidateDivider.setVisibility(View.GONE);
        }
        if (etInput != null) {
            etInput.requestFocus();
        }
    }

    private void updateCandidates() {
        if (pinyinBuffer.length() == 0) {
            if (composingSelectedWords.length() > 0) {
                String finalPhrase = composingSelectedWords.toString();
                insertTextAtCursor(finalPhrase);
                if (finalPhrase.length() >= 2) {
                    String fullPy = composingFullPinyin.toString();
                    if (fullPy.isEmpty()) fullPy = pinyinBuffer.toString();
                    UserPhraseManager.getInstance().addPhrase(this, finalPhrase, fullPy);
                }
            }
            clearPinyinBuffer();
            return;
        }
        currentCandidates = AospPinyinEngine.getInstance().search(pinyinBuffer.toString(), 50);
        candidatePageIndex = 0;
        renderCandidatePage();
    }

    private void renderCandidatePage() {
        if (llCandidateBar == null) return;
        llCandidateBar.setVisibility(View.VISIBLE);
        if (vCandidateDivider != null) vCandidateDivider.setVisibility(View.VISIBLE);
        llCandidateBar.requestLayout();
        llCandidateBar.invalidate();

        if (composingSelectedWords.length() > 0) {
            String remainingFormatted = AospPinyinEngine.getInstance().getFormattedPinyin(pinyinBuffer.toString());
            String dispRemaining = (remainingFormatted != null && remainingFormatted.length() > 0) ? remainingFormatted : pinyinBuffer.toString();
            tvComposing.setText(" 【" + composingSelectedWords.toString() + "】" + (dispRemaining.isEmpty() ? "" : " " + dispRemaining) + " ");
        } else {
            String formatted = AospPinyinEngine.getInstance().getFormattedPinyin(pinyinBuffer.toString());
            tvComposing.setText(" " + (formatted != null && formatted.length() > 0 ? formatted : pinyinBuffer.toString()) + " ");
        }

        int startIdx = candidatePageIndex * CANDIDATES_PER_PAGE;
        TextView[] tvs = new TextView[]{tvCand1, tvCand2, tvCand3, tvCand4, tvCand5, tvCand6, tvCand7, tvCand8, tvCand9};
        for (int i = 0; i < 9; i++) {
            int candIdx = startIdx + i;
            if (candIdx < currentCandidates.size()) {
                final int chosenIdx = candIdx;
                String candText = currentCandidates.get(candIdx);
                tvs[i].setText((i + 1) + "." + candText);
                if (candText.startsWith("★")) {
                    tvs[i].setTextColor(Color.parseColor("#D97706"));
                } else {
                    tvs[i].setTextColor(Color.parseColor("#0F172A"));
                }
                tvs[i].setVisibility(View.VISIBLE);
                tvs[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectCandidate(chosenIdx);
                    }
                });
            } else {
                tvs[i].setVisibility(View.GONE);
            }
        }
        btnCandPrev.setEnabled(candidatePageIndex > 0);
        btnCandNext.setEnabled((candidatePageIndex + 1) * CANDIDATES_PER_PAGE < currentCandidates.size());
    }

    private void toggleImeMode() {
        isChineseMode = !isChineseMode;
        if (btnImeMode != null) {
            btnImeMode.setText(isChineseMode ? "拼" : "英");
        }
        if (!isChineseMode && pinyinBuffer.length() > 0) {
            insertTextAtCursor(pinyinBuffer.toString());
            clearPinyinBuffer();
        }
        Toast.makeText(this, isChineseMode ? "已切换至中文拼音模式" : "已切换至英文模式", Toast.LENGTH_SHORT).show();
    }

    private void toggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        try {
            Method method = EditText.class.getMethod("setShowSoftInputOnFocus", boolean.class);
            method.setAccessible(true);
            method.invoke(etInput, true);
        } catch (Exception ignored) {}

        etInput.requestFocus();
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    private void sendMessage() {
        if (isSending) {
            Toast.makeText(this, "正在等待 AI 回复，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        String prompt = etInput.getText().toString().trim();
        if (prompt.length() == 0) {
            return;
        }

        etInput.setText("");
        addUserMessage(prompt);
        addAiMessage("正在思考中...");
        scrollToBottom();

        new ChatRequestTask(prompt, continueSession).execute();
        continueSession = true;
    }

    private void addUserMessage(String text) {
        messageList.add(new ChatMessage(ChatMessage.ROLE_USER, text, getTimeString()));
        adapter.notifyDataSetChanged();
        scrollToBottom();
        persistCurrentSession();
    }

    private void addAiMessage(String text) {
        messageList.add(new ChatMessage(ChatMessage.ROLE_AI, text, getTimeString()));
        adapter.notifyDataSetChanged();
        scrollToBottom();
        persistCurrentSession();
    }

    private void updateLastAiMessage(String text) {
        if (!messageList.isEmpty()) {
            ChatMessage last = messageList.get(messageList.size() - 1);
            if (last.role == ChatMessage.ROLE_AI) {
                last.setContent(text);
                adapter.notifyDataSetChanged();
                // 新回复产生后，自动滚屏到最底部
                scrollToBottom();
                persistCurrentSession();
                return;
            }
        }
        addAiMessage(text);
        scrollToBottom();
    }

    private void addSystemMessage(String text) {
        messageList.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, text, getTimeString()));
        adapter.notifyDataSetChanged();
        scrollToBottom();
        persistCurrentSession();
    }

    private void scrollToBottom() {
        if (lvChat == null || adapter == null) return;
        lvChat.post(new Runnable() {
            @Override
            public void run() {
                int count = adapter.getCount();
                if (count > 0) {
                    lvChat.setSelection(count - 1);
                }
            }
        });
        // 延时二次校准：针对长富文本/代码块测量排版耗时，确保完全滚至最新内容底部
        lvChat.postDelayed(new Runnable() {
            @Override
            public void run() {
                int count = adapter.getCount();
                if (count > 0) {
                    lvChat.setSelection(count - 1);
                }
            }
        }, 100);
    }

    private String getTimeString() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        final EditText etServer = (EditText) view.findViewById(R.id.et_server_url);
        final EditText etTok = (EditText) view.findViewById(R.id.et_token);
        final EditText etFont = (EditText) view.findViewById(R.id.et_font_size);
        final CheckBox cbBuiltinIme = (CheckBox) view.findViewById(R.id.cb_builtin_ime);
        Button btnOpenSys = (Button) view.findViewById(R.id.btn_open_sys_settings);

        btnOpenSys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean opened = false;
                try {
                    startActivity(new Intent("android.settings.HARD_KEYBOARD_SETTINGS"));
                    opened = true;
                } catch (Exception ignored) {}
                if (!opened) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
                        opened = true;
                    } catch (Exception ignored) {}
                }
                if (!opened) {
                    Toast.makeText(MainActivity.this, "阅读器系统已移除物理键盘设置界面", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button btnManagePhrases = (Button) view.findViewById(R.id.btn_manage_user_phrases);
        if (btnManagePhrases != null) {
            btnManagePhrases.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPhraseDialog(null, null);
                }
            });
        }

        Button btn16 = (Button) view.findViewById(R.id.btn_font_16);
        Button btn18 = (Button) view.findViewById(R.id.btn_font_18);
        Button btn20 = (Button) view.findViewById(R.id.btn_font_20);
        Button btn22 = (Button) view.findViewById(R.id.btn_font_22);
        Button btn24 = (Button) view.findViewById(R.id.btn_font_24);
        Button btn28 = (Button) view.findViewById(R.id.btn_font_28);

        btn16.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etFont.setText("16"); }
        });
        btn18.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etFont.setText("18"); }
        });
        btn20.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etFont.setText("20"); }
        });
        btn22.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etFont.setText("22"); }
        });
        btn24.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etFont.setText("24"); }
        });
        btn28.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etFont.setText("28"); }
        });

        int curFont = prefs.getInt(KEY_FONT, DEFAULT_FONT);
        if (curFont < 14) curFont = DEFAULT_FONT;

        String srv = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
        if (srv.length() == 0) srv = DEFAULT_SERVER;
        String tok = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN).trim();
        if (tok.length() == 0) tok = DEFAULT_TOKEN;

        etServer.setText(srv);
        etTok.setText(tok);
        etFont.setText(String.valueOf(curFont));
        cbBuiltinIme.setChecked(prefs.getBoolean(KEY_ENABLE_BUILTIN_IME, true));

        Button btnAbout = (Button) view.findViewById(R.id.btn_about);
        TextView tvSettingsVer = (TextView) view.findViewById(R.id.tv_settings_version_info);

        String appVerInfo = "v2.5 (Build 20260925)";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVerInfo = "v" + pInfo.versionName + " (Build " + pInfo.versionCode + ")";
        } catch (Exception ignored) {}

        if (tvSettingsVer != null) {
            tvSettingsVer.setText("当前版本: " + appVerInfo);
        }

        if (btnAbout != null) {
            btnAbout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAboutDialog();
                }
            });
        }

        builder.setView(view);
        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String inputSrv = etServer.getText().toString().trim();
                if (inputSrv.length() == 0) inputSrv = DEFAULT_SERVER;
                String inputTok = etTok.getText().toString().trim();
                if (inputTok.length() == 0) inputTok = DEFAULT_TOKEN;

                int fnt = DEFAULT_FONT;
                try {
                    fnt = Integer.parseInt(etFont.getText().toString().trim());
                    if (fnt < 14) fnt = 14;
                    if (fnt > 36) fnt = 36;
                } catch (Exception ignored) {}

                boolean useBuiltIn = cbBuiltinIme.isChecked();

                prefs.edit()
                    .putString(KEY_SERVER, inputSrv)
                    .putString(KEY_TOKEN, inputTok)
                    .putInt(KEY_FONT, fnt)
                    .putBoolean(KEY_ENABLE_BUILTIN_IME, useBuiltIn)
                    .commit();

                backupSettingsToFile(inputSrv, inputTok, fnt, useBuiltIn);

                adapter.setFontSize(fnt);
                etInput.setTextSize(Math.max(16, fnt - 2));
                updateSoftKeyboardSuppression();
                Toast.makeText(MainActivity.this, "配置已保存", Toast.LENGTH_SHORT).show();
                new PingServerTask().execute();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_about, null);

        TextView tvVer = (TextView) view.findViewById(R.id.tv_about_version);
        TextView tvLog = (TextView) view.findViewById(R.id.tv_changelog_content);
        Button btnClose = (Button) view.findViewById(R.id.btn_close_about);

        String appVer = "v2.5 (Build 20260925)";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVer = "v" + pInfo.versionName + " (Build " + pInfo.versionCode + ")";
        } catch (Exception ignored) {}

        if (tvVer != null) {
            tvVer.setText("版本: " + appVer);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【v2.5】最新版本\n");
        sb.append("• 新增「关于」界面与版本号直观展示\n");
        sb.append("• 内置完整更新日志 (Changelog)，支持随时查阅演进历史\n");
        sb.append("• 设置中心与主界面顶部标题均可快捷呼出关于信息\n\n");

        sb.append("【v2.4】\n");
        sb.append("• 拼音自造词动态退火降级机制（连续2次未选中自动让位系统高频词）\n");
        sb.append("• 词库管理与频次淘汰竞争优化\n");
        sb.append("• 服务端安全护栏加固（防越权与危险命令过滤）\n");
        sb.append("• 代码库全量脱敏与零秘钥隔离\n\n");

        sb.append("【v2.3】\n");
        sb.append("• 墨水屏全键盘拼音流内连贯造词（连续选字自动学习新词组）\n");
        sb.append("• 动态规划切词算法优化与首音节单字保底\n");
        sb.append("• 自造词多级存储同步持久化\n\n");

        sb.append("【v2.2】\n");
        sb.append("• 针对起点讯飞阅读器定制高对比度墨水屏黑白视觉配色\n");
        sb.append("• 原生 Markdown 表格卡片自适应渲染\n");
        sb.append("• 服务端命令白名单与沙箱安全防护\n\n");

        sb.append("【v2.1】\n");
        sb.append("• 内置物理键盘拼音输入法，外接蓝牙键盘打字不弹软键盘\n");
        sb.append("• 拼音候选条支持数字 1-9 快速选词与翻页键\n\n");

        sb.append("【v2.0】\n");
        sb.append("• 多会话管理架构（新建会话、历史切换、自动保存）\n");
        sb.append("• 墨水屏大字号调节与快速切换\n\n");

        sb.append("【v1.0】\n");
        sb.append("• 初版发布：原生轻量 AI 交互客户端\n");
        sb.append("• 支持 HTC Sense 全键盘与基础对话\n");

        if (tvLog != null) {
            tvLog.setText(sb.toString());
        }

        builder.setView(view);
        final AlertDialog dialog = builder.create();
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
        dialog.show();
    }

    private void checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                try {
                    requestPermissions(new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }, 101);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            SessionManager.ensureInitialized(this);
            UserPhraseManager.getInstance().syncStorage(this);
        }
    }

    private void backupSettingsToFile(String srv, String tok, int font, boolean builtinIme) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(KEY_SERVER, srv);
            obj.put(KEY_TOKEN, tok);
            obj.put(KEY_FONT, font);
            obj.put(KEY_ENABLE_BUILTIN_IME, builtinIme);
            byte[] bytes = obj.toString().getBytes("UTF-8");

            File extRoot = Environment.getExternalStorageDirectory();
            if (extRoot != null) {
                File dir = new File(extRoot, "ChaChaAI");
                if (!dir.exists()) dir.mkdirs();
                File cfg = new File(dir, "config.json");
                FileOutputStream fos = new FileOutputStream(cfg);
                fos.write(bytes);
                fos.flush();
                fos.close();
            }
        } catch (Exception ignored) {}
    }

    private void restoreSettingsFromBackup() {
        try {
            File extRoot = Environment.getExternalStorageDirectory();
            if (extRoot == null) return;
            File cfg = new File(extRoot, "ChaChaAI/config.json");
            if (!cfg.exists() || cfg.length() < 5) return;

            FileInputStream fis = new FileInputStream(cfg);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();

            JSONObject obj = new JSONObject(sb.toString().trim());
            String srv = obj.optString(KEY_SERVER, "");
            String tok = obj.optString(KEY_TOKEN, "");
            int fnt = obj.optInt(KEY_FONT, 0);
            boolean builtinIme = obj.optBoolean(KEY_ENABLE_BUILTIN_IME, true);

            SharedPreferences.Editor ed = prefs.edit();
            if (srv.length() > 0 && prefs.getString(KEY_SERVER, "").isEmpty()) {
                ed.putString(KEY_SERVER, srv);
            }
            if (tok.length() > 0 && prefs.getString(KEY_TOKEN, "").isEmpty()) {
                ed.putString(KEY_TOKEN, tok);
            }
            if (fnt >= 14 && prefs.getInt(KEY_FONT, 0) == 0) {
                ed.putInt(KEY_FONT, fnt);
            }
            ed.commit();
        } catch (Exception ignored) {}
    }

    private void showPhraseDialog(final String defaultPinyin, final String defaultWord) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_phrase, null);
        builder.setView(view);

        final AlertDialog dialog = builder.create();

        final EditText etWord = (EditText) view.findViewById(R.id.et_new_phrase_word);
        final EditText etPinyin = (EditText) view.findViewById(R.id.et_new_phrase_pinyin);
        Button btnSave = (Button) view.findViewById(R.id.btn_save_phrase);
        final ListView lvPhrases = (ListView) view.findViewById(R.id.lv_user_phrases);
        Button btnClose = (Button) view.findViewById(R.id.btn_close_phrase_dialog);

        if (defaultPinyin != null && defaultPinyin.length() > 0) {
            etPinyin.setText(defaultPinyin);
        }
        if (defaultWord != null && defaultWord.length() > 0) {
            etWord.setText(defaultWord);
        }

        final List<UserPhraseManager.Phrase> phrases = new ArrayList<UserPhraseManager.Phrase>();
        phrases.addAll(UserPhraseManager.getInstance().getAllPhrases(this));

        final BaseAdapter phraseAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return phrases.size();
            }

            @Override
            public Object getItem(int position) {
                return phrases.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_phrase, parent, false);
                }
                final UserPhraseManager.Phrase item = phrases.get(position);
                TextView tvWord = (TextView) convertView.findViewById(R.id.tv_phrase_word);
                TextView tvPinyin = (TextView) convertView.findViewById(R.id.tv_phrase_pinyin);
                TextView tvCount = (TextView) convertView.findViewById(R.id.tv_phrase_count);
                Button btnDelete = (Button) convertView.findViewById(R.id.btn_phrase_delete);

                tvWord.setText("★ " + item.word);
                StringBuilder pyDesc = new StringBuilder();
                if (item.pinyin != null && item.pinyin.length() > 0) {
                    pyDesc.append("全拼: ").append(item.pinyin);
                }
                if (item.initials != null && item.initials.length() > 0 && !item.initials.equals(item.pinyin)) {
                    if (pyDesc.length() > 0) pyDesc.append(" | ");
                    pyDesc.append("简拼: ").append(item.initials);
                }
                tvPinyin.setText(pyDesc.toString());
                tvCount.setText("使用: " + item.count + "次");

                btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UserPhraseManager.getInstance().deletePhrase(MainActivity.this, item.word);
                        phrases.remove(position);
                        notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, "已删除词组: " + item.word, Toast.LENGTH_SHORT).show();
                        if (pinyinBuffer.length() > 0) {
                            updateCandidates();
                        }
                    }
                });

                return convertView;
            }
        };

        lvPhrases.setAdapter(phraseAdapter);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String word = etWord.getText().toString().trim();
                String py = etPinyin.getText().toString().trim();
                if (word.length() == 0) {
                    Toast.makeText(MainActivity.this, "请输入词组汉字", Toast.LENGTH_SHORT).show();
                    return;
                }
                UserPhraseManager.getInstance().addPhrase(MainActivity.this, word, py);
                Toast.makeText(MainActivity.this, "词组「" + word + "」已录入自造词库！", Toast.LENGTH_SHORT).show();
                etWord.setText("");
                etPinyin.setText("");
                phrases.clear();
                phrases.addAll(UserPhraseManager.getInstance().getAllPhrases(MainActivity.this));
                phraseAdapter.notifyDataSetChanged();
                if (pinyinBuffer.length() > 0) {
                    updateCandidates();
                }
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private class PingServerTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            String baseUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
            if (baseUrl.length() == 0) baseUrl = DEFAULT_SERVER;

            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/api/ping");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                return conn.getResponseCode() == 200;
            } catch (Exception e) {
                return false;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Boolean online) {
            if (online) {
                tvStatusDot.setTextColor(Color.parseColor("#16A34A"));
                if (currentSession != null && currentSession.title != null && !currentSession.title.equals("新会话")) {
                    tvTitle.setText(currentSession.title);
                } else {
                    tvTitle.setText("ChaCha AI (云端就绪)");
                }
            } else {
                tvStatusDot.setTextColor(Color.parseColor("#DC2626"));
                tvTitle.setText("ChaCha AI (离线/连接中)");
            }
        }
    }

    private class ResetSessionTask extends AsyncTask<Void, Void, Void> {
        private String sessionId;

        public ResetSessionTask() {
            this.sessionId = null;
        }

        public ResetSessionTask(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        protected Void doInBackground(Void... params) {
            String baseUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
            if (baseUrl.length() == 0) baseUrl = DEFAULT_SERVER;
            String token = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN).trim();
            if (token.length() == 0) token = DEFAULT_TOKEN;

            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/api/reset");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setDoOutput(true);
                JSONObject json = new JSONObject();
                json.put("token", token);
                if (sessionId != null) {
                    json.put("session_id", sessionId);
                }
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return null;
        }
    }

    private class ChatRequestTask extends AsyncTask<Void, Void, String> {
        private String prompt;
        private boolean cont;
        private boolean success = false;

        public ChatRequestTask(String prompt, boolean cont) {
            this.prompt = prompt;
            this.cont = cont;
        }

        @Override
        protected void onPreExecute() {
            isSending = true;
            btnSend.setEnabled(false);
        }

        @Override
        protected String doInBackground(Void... params) {
            String baseUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
            if (baseUrl.length() == 0) baseUrl = DEFAULT_SERVER;
            String token = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN).trim();
            if (token.length() == 0) token = DEFAULT_TOKEN;

            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/api/chat");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(310000);
                conn.setDoOutput(true);

                JSONObject req = new JSONObject();
                req.put("prompt", prompt);
                req.put("continue", cont);
                req.put("token", token);
                if (currentSession != null && currentSession.id != null) {
                    req.put("session_id", currentSession.id);
                }

                byte[] body = req.toString().getBytes("UTF-8");
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();

                    JSONObject res = new JSONObject(sb.toString().trim());
                    String reply = res.optString("reply", "");
                    success = true;
                    return reply.length() > 0 ? reply : "【AI 没有返回任何内容】";
                } else {
                    String errMessage = "";
                    try {
                        InputStream es = conn.getErrorStream();
                        if (es != null) {
                            BufferedReader errReader = new BufferedReader(new InputStreamReader(es, "UTF-8"));
                            StringBuilder errSb = new StringBuilder();
                            String eline;
                            while ((eline = errReader.readLine()) != null) {
                                errSb.append(eline).append("\n");
                            }
                            errReader.close();
                            JSONObject errJson = new JSONObject(errSb.toString().trim());
                            errMessage = errJson.optString("reply", errJson.optString("error", ""));
                        }
                    } catch (Exception ignored) {}

                    if (errMessage.length() > 0) {
                        return errMessage + " (HTTP " + code + ")";
                    }
                    if (code == 504) {
                        return "【请求超时 (HTTP 504)：AI 复杂任务处理超过 5 分钟，后台可能仍在执行，请稍后输入“继续”查看】";
                    }
                    return "【请求失败，HTTP 状态码: " + code + "】";
                }
            } catch (Exception e) {
                return "【网络连接错误: " + e.getMessage() + "】";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            isSending = false;
            btnSend.setEnabled(true);
            updateLastAiMessage(result);
            if (success) {
                tvStatusDot.setTextColor(Color.parseColor("#000000"));
            } else {
                tvStatusDot.setTextColor(Color.parseColor("#888888"));
            }
        }
    }
}
