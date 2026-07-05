package com.example.vehicleassistant.ui;

import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.engine.VideoSearchHelper;
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
            if (!isThinkingFinished) return "正在思考...";
            long seconds = Math.max(1, Math.round(thinkingDurationMs / 1000.0));
            return "已思考 " + seconds + " 秒";
        }

        String getThinkingDisplayText() {
            if (thinkingContent != null && !thinkingContent.isEmpty()) return thinkingContent;
            return isThinkingFinished ? "本次没有返回详细思考摘要。" : "正在思考...";
        }
    }

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_VIDEO_SEARCH = 2;

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = items.get(position).message;
        if (ChatMessage.TYPE_VIDEO_SEARCH.equals(msg.contentType)) return TYPE_VIDEO_SEARCH;
        return ChatMessage.ROLE_USER.equals(msg.role) ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        if (viewType == TYPE_USER) {
            layoutId = R.layout.item_message_user;
        } else if (viewType == TYPE_VIDEO_SEARCH) {
            layoutId = R.layout.item_message_video_search;
        } else {
            layoutId = R.layout.item_message_assistant;
        }
        View view = LayoutInflater.from(parent.getContext())
            .inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        int type = getItemViewType(position);

        if (type == TYPE_VIDEO_SEARCH) {
            holder.bindVideoSearch(item);
        } else {
            holder.tvMessage.setText(item.message.content);
            holder.bindThinking(item);
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.stopDotAnimation();
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

    public void addVideoSearchCard(String keyword) {
        ChatItem item = new ChatItem(
            new ChatMessage(ChatMessage.ROLE_ASSISTANT, keyword, ChatMessage.TYPE_VIDEO_SEARCH), null);
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void addAssistantMessage(String text, List<ExecutionResult> execResults) {
        items.add(new ChatItem(
            new ChatMessage(ChatMessage.ROLE_ASSISTANT, text), execResults));
        notifyItemInserted(items.size() - 1);
    }

    public int addThinkingPlaceholder() {
        ChatItem item = new ChatItem(
            new ChatMessage(ChatMessage.ROLE_ASSISTANT, "思考中..."), new ArrayList<>());
        item.isThinking = true;
        item.isThinkingExpanded = true;
        item.isThinkingFinished = false;
        item.thinkingStartTime = System.currentTimeMillis();
        items.add(item);
        int pos = items.size() - 1;
        notifyItemInserted(pos);
        return pos;
    }

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
        TextView tvVideoTitle;
        Button btnOpenVideo;
        View reasoningHeader;
        TextView reasoningToggle;
        TextView reasoningTitle;
        TextView reasoningContent;
        View reasoningDivider;
        private final Handler dotHandler = new Handler(Looper.getMainLooper());
        private Runnable dotRunnable;
        private int dotFrame;

        ViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvVideoTitle = itemView.findViewById(R.id.tv_video_title);
            btnOpenVideo = itemView.findViewById(R.id.btn_open_video);
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
                stopDotAnimation();
                return;
            }

            if (reasoningHeader != null) reasoningHeader.setVisibility(View.VISIBLE);
            if (reasoningToggle != null)
                reasoningToggle.setText(item.isThinkingExpanded ? "▼" : "▶");
            if (reasoningTitle != null) {
                if (item.isThinking && !item.isThinkingFinished) {
                    startDotAnimation();
                } else {
                    stopDotAnimation();
                    reasoningTitle.setText(item.getThinkingTitle());
                }
            }
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

        void bindVideoSearch(ChatItem item) {
            String keyword = item.message.content;
            if (tvVideoTitle != null)
                tvVideoTitle.setText("🎬 搜索：" + keyword);
            if (btnOpenVideo != null) {
                btnOpenVideo.setOnClickListener(v ->
                    VideoSearchHelper.openSearch(v.getContext(), keyword));
            }
        }

        void startDotAnimation() {
            if (dotRunnable != null) return;
            dotFrame = 0;
            dotRunnable = new Runnable() {
                @Override
                public void run() {
                    if (reasoningTitle == null) return;
                    String base = "正在思考...";
                    SpannableString sp = new SpannableString(base);
                    int visibleDots = dotFrame % 4;
                    int textColor = reasoningTitle.getCurrentTextColor();
                    int transparent = textColor & 0x00FFFFFF; // same color, alpha=0
                    for (int i = 0; i < 3; i++) {
                        if (i >= visibleDots) {
                            sp.setSpan(new ForegroundColorSpan(transparent),
                                    4 + i, 5 + i, 0);
                        }
                    }
                    reasoningTitle.setText(sp);
                    dotFrame++;
                    dotHandler.postDelayed(this, 400);
                }
            };
            dotHandler.post(dotRunnable);
        }

        void stopDotAnimation() {
            if (dotRunnable != null) {
                dotHandler.removeCallbacks(dotRunnable);
                dotRunnable = null;
            }
        }
    }
}
