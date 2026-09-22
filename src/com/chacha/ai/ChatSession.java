package com.chacha.ai;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {
    public String id;
    public String title;
    public String time;
    public List<ChatMessage> messages;

    public ChatSession() {
        this.messages = new ArrayList<ChatMessage>();
    }

    public ChatSession(String id, String title, String time) {
        this.id = id;
        this.title = title;
        this.time = time;
        this.messages = new ArrayList<ChatMessage>();
    }
}
