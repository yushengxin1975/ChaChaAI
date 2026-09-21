package com.chacha.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

    private static final String DEFAULT_SERVER = "";
    private static final String DEFAULT_TOKEN = "";
    private static final int DEFAULT_FONT = 22;

    private TextView tvStatusDot;
    private TextView tvTitle;
    private ListView lvChat;
    private EditText etInput;
    private Button btnSend;
    private Button btnNew;
    private Button btnSettings;

    private List<ChatMessage> messageList;
    private ChatAdapter adapter;
    private SharedPreferences prefs;

    private boolean isSending = false;
    private boolean continueSession = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        tvStatusDot = (TextView) findViewById(R.id.tv_status_dot);
        tvTitle = (TextView) findViewById(R.id.tv_title);
        lvChat = (ListView) findViewById(R.id.lv_chat);
        etInput = (EditText) findViewById(R.id.et_input);
        btnSend = (Button) findViewById(R.id.btn_send);
        btnNew = (Button) findViewById(R.id.btn_new);
        btnSettings = (Button) findViewById(R.id.btn_settings);

        messageList = new ArrayList<ChatMessage>();
        adapter = new ChatAdapter(this, messageList);
        int savedFont = prefs.getInt(KEY_FONT, DEFAULT_FONT);
        if (savedFont < 18) {
            savedFont = 22;
            prefs.edit().putInt(KEY_FONT, 22).commit();
        }
        adapter.setFontSize(savedFont);
        etInput.setTextSize(Math.max(15, savedFont - 3));
        lvChat.setAdapter(adapter);

        String curServer = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
        if (curServer.length() == 0) {
            tvStatusDot.setTextColor(Color.parseColor("#FFA000"));
            tvTitle.setText("ChaCha AI (请设置服务器)");
            addSystemMessage("欢迎使用 HTC ChaCha AI 助手！请先点击右上角 [设置] 配置您的服务器地址与密钥。");
        } else {
            addSystemMessage("欢迎使用 HTC ChaCha AI 助手！输入问题按回车即可提问。");
            new PingServerTask().execute();
        }

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        etInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                    return true;
                }
                return false;
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
                continueSession = false;
                addSystemMessage("[已开启全新对话，上下文记忆已清空]");
                new ResetSessionTask().execute();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        new PingServerTask().execute();
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

        new ChatRequestTask(prompt, continueSession).execute();
        continueSession = true;
    }

    private void addUserMessage(String text) {
        messageList.add(new ChatMessage(ChatMessage.ROLE_USER, text, getTimeString()));
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void addAiMessage(String text) {
        messageList.add(new ChatMessage(ChatMessage.ROLE_AI, text, getTimeString()));
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void updateLastAiMessage(String text) {
        if (!messageList.isEmpty()) {
            ChatMessage last = messageList.get(messageList.size() - 1);
            if (last.role == ChatMessage.ROLE_AI) {
                last.content = text;
                adapter.notifyDataSetChanged();
                scrollToBottom();
                return;
            }
        }
        addAiMessage(text);
    }

    private void addSystemMessage(String text) {
        messageList.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, text, getTimeString()));
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void scrollToBottom() {
        lvChat.post(new Runnable() {
            @Override
            public void run() {
                lvChat.setSelection(adapter.getCount() - 1);
            }
        });
    }

    private String getTimeString() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("服务器设置");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        final EditText etServer = (EditText) view.findViewById(R.id.et_server_url);
        final EditText etTok = (EditText) view.findViewById(R.id.et_token);
        final EditText etFont = (EditText) view.findViewById(R.id.et_font_size);

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
        if (curFont < 18) curFont = 22;
        etServer.setText(prefs.getString(KEY_SERVER, DEFAULT_SERVER));
        etTok.setText(prefs.getString(KEY_TOKEN, DEFAULT_TOKEN));
        etFont.setText(String.valueOf(curFont));

        builder.setView(view);
        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String srv = etServer.getText().toString().trim();
                String tok = etTok.getText().toString().trim();
                int fnt = DEFAULT_FONT;
                try {
                    fnt = Integer.parseInt(etFont.getText().toString().trim());
                    if (fnt < 12) fnt = 12;
                    if (fnt > 36) fnt = 36;
                } catch (Exception ignored) {}

                prefs.edit()
                    .putString(KEY_SERVER, srv)
                    .putString(KEY_TOKEN, tok)
                    .putInt(KEY_FONT, fnt)
                    .commit();

                adapter.setFontSize(fnt);
                etInput.setTextSize(Math.max(15, fnt - 3));
                Toast.makeText(MainActivity.this, "配置已保存 (字号: " + fnt + ")", Toast.LENGTH_SHORT).show();
                new PingServerTask().execute();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private class PingServerTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            String baseUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
            if (baseUrl.length() == 0) {
                return false;
            }
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
            String baseUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
            if (baseUrl.length() == 0) {
                tvStatusDot.setTextColor(Color.parseColor("#FFA000"));
                tvTitle.setText("ChaCha AI (请设置服务器)");
                return;
            }
            if (online) {
                tvStatusDot.setTextColor(Color.parseColor("#00E676"));
                tvTitle.setText("ChaCha AI (云端就绪)");
            } else {
                tvStatusDot.setTextColor(Color.parseColor("#FF5252"));
                tvTitle.setText("ChaCha AI (离线/连接中)");
            }
        }
    }

    private class ResetSessionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            String baseUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER).trim();
            String token = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN).trim();
            if (baseUrl.length() == 0) {
                return null;
            }
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
            String token = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN).trim();
            if (baseUrl.length() == 0) {
                return "[请先点击右上角【设置】配置服务器地址与密钥]";
            }
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/api/chat");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(95000);
                conn.setDoOutput(true);

                JSONObject req = new JSONObject();
                req.put("prompt", prompt);
                req.put("continue", cont);
                req.put("token", token);

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
                    return reply.length() > 0 ? reply : "[AI 没有返回任何内容]";
                } else {
                    return "[请求失败，HTTP 状态码: " + code + "]";
                }
            } catch (Exception e) {
                return "[网络连接错误: " + e.getMessage() + "]";
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
                tvStatusDot.setTextColor(Color.parseColor("#00E676"));
            } else {
                tvStatusDot.setTextColor(Color.parseColor("#FF5252"));
            }
        }
    }
}
