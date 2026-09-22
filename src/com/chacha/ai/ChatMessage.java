package com.chacha.ai;

public class ChatMessage {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI = 1;
    public static final int ROLE_SYSTEM = 2;

    public int role;
    public String content;
    public String time;

    private transient CharSequence formatted = null;
    private transient java.util.List<MarkdownParser.ContentBlock> blocks = null;

    public ChatMessage(int role, String content, String time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }

    public CharSequence getFormatted() {
        if (formatted == null && content != null) {
            if (role == ROLE_AI || role == ROLE_USER) {
                formatted = MarkdownParser.parse(content);
            } else {
                formatted = content;
            }
        }
        return formatted != null ? formatted : (content != null ? content : "");
    }

    public java.util.List<MarkdownParser.ContentBlock> getBlocks() {
        if (blocks == null && content != null) {
            if (role == ROLE_AI || role == ROLE_USER) {
                blocks = MarkdownParser.parseBlocks(content);
            }
        }
        return blocks;
    }

    public void setContent(String newContent) {
        this.content = newContent;
        this.formatted = null;
        this.blocks = null;
    }

    public void invalidateFormatted() {
        this.formatted = null;
        this.blocks = null;
    }
}
