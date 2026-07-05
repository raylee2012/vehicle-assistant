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

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
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
    private LinearLayout llDownload;
    private ProgressBar pbDownload;
    private Button btnDownload;
    private TextView tvDownloadStatus;
    private Button btnMic;
    private LottieAnimationView lottieWaveform;

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
        btnMic = binding.btnMic;
        lottieWaveform = binding.lottieWaveform;

        // 初始化 Lottie 动画
        lottieWaveform.setAnimation(R.raw.lottie_waveform);
        lottieWaveform.setRepeatCount(LottieDrawable.INFINITE);
        lottieWaveform.setRepeatMode(LottieDrawable.RESTART);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication()))
                .get(MainViewModel.class);
        adapter = viewModel.getAdapter();

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // 状态文本
        viewModel.getStatusText().observe(this, status -> tvStatus.setText(status));

        // 输入启用状态（整合 VoiceKit 就绪状态控制麦克风可见性）
        viewModel.getInputEnabled().observe(this, enabled -> {
            Boolean vkReady = viewModel.getVoiceKitReady().getValue();
            btnMic.setVisibility(enabled != null && enabled && vkReady != null && vkReady ? View.VISIBLE : View.GONE);
            updateInputState();
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

        // 麦克风按钮 — 录音中再点 = 手动结束
        btnMic.setOnClickListener(v -> viewModel.toggleListening());

        // ASR 录音状态 → 驱动 麦克风/Lottie 互斥显示 + 输入禁用
        viewModel.getAsrListening().observe(this, listening -> {
            if (listening != null && listening) {
                btnMic.setVisibility(View.GONE);
                lottieWaveform.setVisibility(View.VISIBLE);
                lottieWaveform.playAnimation();
            } else {
                lottieWaveform.cancelAnimation();
                lottieWaveform.setVisibility(View.GONE);
                btnMic.setVisibility(View.VISIBLE);
            }
            updateInputState();
        });

        // ASR 识别结果 → 填入输入框
        viewModel.getAsrResult().observe(this, result -> {
            if (result != null && !result.isEmpty()) {
                etInput.setText(result);
                etInput.setSelection(result.length());
                viewModel.onAsrResultConsumed();
            }
        });

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

    // 辅助方法：输入框和发送按钮只有在"模型就绪 + 不在录音中"才可用
    private void updateInputState() {
        Boolean inputOk = viewModel.getInputEnabled().getValue();
        Boolean asrOn = viewModel.getAsrListening().getValue();
        boolean enabled = inputOk != null && inputOk && (asrOn == null || !asrOn);
        etInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;
        etInput.setText("");
        viewModel.sendMessage(text);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 场景1: 录音中切后台 → 自动停止
        viewModel.stopListeningOnPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
