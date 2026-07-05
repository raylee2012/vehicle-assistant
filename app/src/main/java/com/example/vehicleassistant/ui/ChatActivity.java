package com.example.vehicleassistant.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private MainViewModel viewModel;
    private ChatAdapter adapter;
    private RecyclerView rvChat;
    private EditText etInput;
    private Button btnSend;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rvChat = binding.rvChat;
        etInput = binding.etInput;
        btnSend = binding.btnSend;
        tvStatus = binding.tvStatus;

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        adapter = viewModel.getAdapter();

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // 状态文本
        viewModel.getStatusText().observe(this, status -> tvStatus.setText(status));

        // 输入启用状态
        viewModel.getInputEnabled().observe(this, enabled -> {
            etInput.setEnabled(enabled);
            btnSend.setEnabled(enabled);
        });

        // 发送按钮
        btnSend.setOnClickListener(v -> sendMessage());

        // 键盘发送
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // 自动滚动到底部
        viewModel.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                rvChat.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;
        etInput.setText("");
        viewModel.sendMessage(text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
