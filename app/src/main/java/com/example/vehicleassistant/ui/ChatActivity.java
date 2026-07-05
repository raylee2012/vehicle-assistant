package com.example.vehicleassistant.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private MainViewModel viewModel;
    private ChatAdapter adapter;
    private RecyclerView rvChat;
    private EditText etInput;
    private Button btnSend;
    private TextView tvStatus;
    private LinearLayout llDownload;
    private ProgressBar pbDownload;
    private Button btnDownload;
    private TextView tvDownloadStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rvChat = binding.rvChat;
        etInput = binding.etInput;
        btnSend = binding.btnSend;
        tvStatus = binding.tvStatus;
        llDownload = binding.llDownload;
        pbDownload = binding.pbDownload;
        btnDownload = binding.btnDownload;
        tvDownloadStatus = binding.tvDownloadStatus;

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication()))
                .get(MainViewModel.class);
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

        // 下载区域可见性
        viewModel.getDownloadVisible().observe(this, visible ->
            llDownload.setVisibility(visible ? View.VISIBLE : View.GONE));

        // 下载按钮/进度条切换
        viewModel.getDownloadActive().observe(this, active -> {
            btnDownload.setVisibility(active ? View.GONE : View.VISIBLE);
            pbDownload.setVisibility(active ? View.VISIBLE : View.GONE);
        });

        // 下载进度
        viewModel.getDownloadProgress().observe(this, progress ->
            pbDownload.setProgress(progress));

        // 下载状态文本
        viewModel.getDownloadStatus().observe(this, status ->
            tvDownloadStatus.setText(status));

        // 下载按钮
        btnDownload.setOnClickListener(v -> viewModel.startDownload());

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
