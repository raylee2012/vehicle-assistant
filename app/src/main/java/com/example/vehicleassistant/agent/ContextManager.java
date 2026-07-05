package com.example.vehicleassistant.agent;

import com.example.vehicleassistant.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口上下文管理。
 * 窗口: 4096 tokens, 预留 1200 给 prompt + 输出, 历史上限 2896 tokens / 20 轮。
 * 裁剪策略: 从最早消息开始成对删除 (user + assistant)。
 */
public class ContextManager {

    private static final int MAX_HISTORY_TOKENS = 2896;
    private static final int MAX_ROUNDS = 20;

    private final List<ChatMessage> history = new ArrayList<>();

    public void save(ChatMessage msg) {
        history.add(msg);
        trim();
    }

    public void saveUserAndAssistant(ChatMessage userMsg, ChatMessage assistantMsg) {
        history.add(userMsg);
        history.add(assistantMsg);
        trim();
    }

    public List<ChatMessage> getHistory() {
        return new ArrayList<>(history);
    }

    public void clear() {
        history.clear();
    }

    public int getCurrentTokenCount() {
        int total = 0;
        for (ChatMessage msg : history) {
            total += estimateTokens(msg.content);
        }
        return total;
    }

    /**
     * 中文粗略估算: 字符数 * 0.5 ≈ token 数。
     */
    public static int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) (text.length() * 0.5);
    }

    private void trim() {
        // 1. 按轮数裁剪
        int maxMessages = MAX_ROUNDS * 2; // user + assistant 成对
        while (history.size() > maxMessages) {
            history.remove(0);
        }

        // 2. 按 token 数裁剪
        while (getCurrentTokenCount() > MAX_HISTORY_TOKENS && history.size() >= 2) {
            // 保留 system 消息（如果存在），从最早的 user/assistant 对开始删
            if (ChatMessage.ROLE_SYSTEM.equals(history.get(0).role)) {
                // 跳过 system 消息，删后面的
                if (history.size() >= 3) {
                    history.remove(1);
                    history.remove(1);
                } else break;
            } else {
                history.remove(0);
                if (!history.isEmpty()) history.remove(0);
            }
        }
    }
}
