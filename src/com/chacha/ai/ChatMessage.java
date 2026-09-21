package com.chacha.ai;

public class ChatMessage {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI = 1;
    public static final int ROLE_SYSTEM = 2;

    public int role;
    public String content;
    public String time;

    public ChatMessage(int role, String content, String time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }
}
