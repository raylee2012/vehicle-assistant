package com.example.vehicleassistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.model.ChatMessage;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatItem> items = new ArrayList<>();

    public static class ChatItem {
        public final ChatMessage message;
        public final List<ExecutionResult> execResults;

        public boolean isThinking;
        public String thinkingContent;
        public boolean isThinkingExpanded;
        public boolean isThinkingFinished;
        public long thinkingStartTime;
        public long thinkingDurationMs;

        public ChatItem(ChatMessage message, List<ExecutionResult> execResults) {
            this.message = message;
            this.execResults = execResults;
        }

        String getThinkingTitle() {
            if (!isThinkingFinished) return "正在思考…";
            long seconds = Math.max(1, Math.round(thinkingDurationMs / 1000.0));
            return "已思考 " + seconds + " 秒";
        }

        String getThinkingDisplayText() {
            if (thinkingContent != null && !thinkingContent.isEmpty()) return thinkingContent;
            return isThinkingFinished ? "本次没有返回详细思考摘要。" : "正在思考…";
        }
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = items.get(position).message;
        return ChatMessage.ROLE_USER.equals(msg.role) ? 0 : 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == 0
            ? R.layout.item_message_user
            : R.layout.item_message_assistant;
        View view = LayoutInflater.from(parent.getContext())
            .inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        holder.tvMessage.setText(item.message.content);
        holder.bindThinking(item);

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addUserMessage(String text) {
        items.add(new ChatItem(
            new ChatMessage(ChatMessage.ROLE_USER, text), null));
        notifyItemInserted(items.size() - 1);
    }

    public void addAssistantMessage(String text, List<ExecutionResult> execResults) {
        items.add(new ChatItem(
            new ChatMessage(ChatMessage.ROLE_ASSISTANT, text), execResults));
        notifyItemInserted(items.size() - 1);
    }

    /** 添加思考占位条目，返回其位置索引。 */
    public int addThinkingPlaceholder() {
        ChatItem item = new ChatItem(
            new ChatMessage(ChatMessage.ROLE_ASSISTANT, "思考中…"), new ArrayList<>());
        item.isThinking = true;
        item.isThinkingExpanded = true;
        item.isThinkingFinished = false;
        item.thinkingStartTime = System.currentTimeMillis();
        items.add(item);
        int pos = items.size() - 1;
        notifyItemInserted(pos);
        return pos;
    }

    /** 思考完成，更新占位条目为最终回复。 */
    public void finishThinking(int placeholderPos, String text, List<ExecutionResult> execResults) {
        if (placeholderPos < 0 || placeholderPos >= items.size()) return;
        ChatItem item = items.get(placeholderPos);
        item.message.content = text;
        item.execResults.clear();
        if (execResults != null && !execResults.isEmpty()) {
            item.execResults.addAll(execResults);
            StringBuilder sb = new StringBuilder();
            for (ExecutionResult r : execResults) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(r.action).append(": ")
                  .append(r.success ? "成功" : "失败");
                if (r.message != null && !r.message.isEmpty()) {
                    sb.append(" (").append(r.message).append(")");
                }
            }
            item.thinkingContent = sb.toString();
        }
        item.isThinkingFinished = true;
        item.isThinkingExpanded = false;
        item.thinkingDurationMs = System.currentTimeMillis() - item.thinkingStartTime;
        notifyItemChanged(placeholderPos);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
View reasoningHeader;
        TextView reasoningToggle;
        TextView reasoningTitle;
        TextView reasoningContent;
        View reasoningDivider;

        ViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
reasoningHeader = itemView.findViewById(R.id.reasoning_header);
            reasoningToggle = itemView.findViewById(R.id.reasoning_toggle);
            reasoningTitle = itemView.findViewById(R.id.reasoning_title);
            reasoningContent = itemView.findViewById(R.id.reasoning_content);
            reasoningDivider = itemView.findViewById(R.id.reasoning_divider);
        }

        void bindThinking(ChatItem item) {
            boolean showThinking = item.isThinking || item.isThinkingFinished;
            if (!showThinking) {
                if (reasoningHeader != null) reasoningHeader.setVisibility(View.GONE);
                if (reasoningContent != null) reasoningContent.setVisibility(View.GONE);
                if (reasoningDivider != null) reasoningDivider.setVisibility(View.GONE);
                if (reasoningHeader != null) reasoningHeader.setOnClickListener(null);
                return;
            }

            if (reasoningHeader != null) reasoningHeader.setVisibility(View.VISIBLE);
            if (reasoningToggle != null)
                reasoningToggle.setText(item.isThinkingExpanded ? "▼" : "▶");
            if (reasoningTitle != null)
                reasoningTitle.setText(item.getThinkingTitle());
            if (reasoningContent != null) {
                reasoningContent.setText(item.getThinkingDisplayText());
                reasoningContent.setVisibility(item.isThinkingExpanded ? View.VISIBLE : View.GONE);
            }
            if (reasoningDivider != null) reasoningDivider.setVisibility(View.VISIBLE);

            if (reasoningHeader != null) {
                reasoningHeader.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    item.isThinkingExpanded = !item.isThinkingExpanded;
                    RecyclerView.Adapter<?> adapter = getBindingAdapter();
                    if (adapter != null) adapter.notifyItemChanged(pos);
                });
            }
        }
    }
}
