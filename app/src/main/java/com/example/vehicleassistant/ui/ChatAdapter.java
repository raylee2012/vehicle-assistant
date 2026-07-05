package com.example.vehicleassistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.model.ChatMessage;
import com.example.vehicleassistant.ui.widgets.ExecutionCard;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatItem> items = new ArrayList<>();

    public static class ChatItem {
        public final ChatMessage message;
        public final List<ExecutionResult> execResults;

        public ChatItem(ChatMessage message, List<ExecutionResult> execResults) {
            this.message = message;
            this.execResults = execResults;
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

        // 展示执行卡片（如果有）
        if (holder.llExecContainer != null && item.execResults != null) {
            holder.llExecContainer.setVisibility(View.VISIBLE);
            holder.llExecContainer.removeAllViews();
            for (ExecutionResult result : item.execResults) {
                ExecutionCard card = new ExecutionCard(holder.itemView.getContext());
                card.bind(result);
                holder.llExecContainer.addView(card);
            }
        }
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

    public void addItem(ChatItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        LinearLayout llExecContainer;

        ViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            llExecContainer = itemView.findViewById(R.id.ll_execution_container);
        }
    }
}
