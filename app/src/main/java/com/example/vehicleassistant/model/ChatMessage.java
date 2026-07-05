package com.example.vehicleassistant.model;

public class ChatMessage {
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_VIDEO_SEARCH = "video_search";

    public final String role;
    public String content;
    public final long timestamp;
    public String contentType;

    public ChatMessage(String role, String content) {
        this(role, content, TYPE_TEXT);
    }

    public ChatMessage(String role, String content, String contentType) {
        this.role = role;
        this.content = content;
        this.contentType = contentType;
        this.timestamp = System.currentTimeMillis();
    }
}
