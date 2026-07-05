package com.example.vehicleassistant.ui;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
    private TextView btnSend;
    private TextView tvStatus;
    private LinearLayout llDownload;
    private ProgressBar pbDownload;
    private Button btnDownload;
    private TextView tvDownloadStatus;
    private ImageView btnMic;
    private TextView btnSettings;
    private ValueAnimator voicePulseAnimator;
    private ValueAnimator dotAnimator;
    private int dotFrame = 0;
    private static final String[] DOT_FRAMES = {"思考中", "思考中·", "思考中··", "思考中···"};

    // Critical 1: RECORD_AUDIO runtime permission launcher
    private final ActivityResultLauncher<String> requestRecordAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    viewModel.toggleListening();
                } else {
                    Toast.makeText(this, "需要录音权限，请在设置中授权", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
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
        btnSettings = binding.btnSettings;
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication()))
                .get(MainViewModel.class);
        adapter = viewModel.getAdapter();

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // 状态文本 — "思考中..." 时启动省略号动画
        viewModel.getStatusText().observe(this, status -> {
            if ("思考中...".equals(status)) {
                startDotAnimation();
            } else {
                stopDotAnimation();
                tvStatus.setText(status);
            }
        });

        // 输入启用状态 → 按钮样式
        viewModel.getInputEnabled().observe(this, enabled -> applyButtonStates());

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

        // 麦克风按钮 — 检查 RECORD_AUDIO 权限后再触发放/停录音
        btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                viewModel.toggleListening();
            } else {
                requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });

        // ASR 录音状态 → 脉冲动画 + 按钮样式
        viewModel.getAsrListening().observe(this, listening -> {
            if (listening != null && listening) {
                startPulseAnimation();
            } else {
                stopPulseAnimation();
            }
            applyButtonStates();
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

    private void applyButtonStates() {
        boolean isProcessing = !Boolean.TRUE.equals(viewModel.getInputEnabled().getValue());
        boolean isRecording = Boolean.TRUE.equals(viewModel.getAsrListening().getValue());

        // 麦克风 + 发送按钮：模型未就绪时状态一致
        btnMic.setVisibility(View.VISIBLE);
        if (!isRecording) {
            btnMic.setEnabled(!isProcessing);
            btnMic.setAlpha(isProcessing ? 0.5f : 1.0f);
        }

        btnSend.setEnabled(!isProcessing);
        btnSend.setAlpha(isProcessing ? 0.5f : 1.0f);

        // 输入框
        etInput.setEnabled(!isProcessing && !isRecording);
    }

    private void startPulseAnimation() {
        btnMic.post(() -> {
            if (btnMic.getBackground() != null) {
                btnMic.getBackground().setTint(
                        ContextCompat.getColor(ChatActivity.this, R.color.voice_recording_red));
            }
            if (voicePulseAnimator == null) {
                voicePulseAnimator = ValueAnimator.ofFloat(1f, 0.4f);
                voicePulseAnimator.setDuration(600);
                voicePulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                voicePulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                voicePulseAnimator.addUpdateListener(anim ->
                        btnMic.setAlpha((float) anim.getAnimatedValue()));
            }
            if (!voicePulseAnimator.isStarted()) {
                voicePulseAnimator.start();
            }
        });
    }

    private void stopPulseAnimation() {
        btnMic.post(() -> {
            if (voicePulseAnimator != null) {
                voicePulseAnimator.cancel();
            }
            btnMic.setAlpha(1f);
            if (btnMic.getBackground() != null) {
                btnMic.getBackground().setTint(
                        ContextCompat.getColor(ChatActivity.this, R.color.primary));
            }
        });
    }

    private void startDotAnimation() {
        if (dotAnimator != null && dotAnimator.isRunning()) return;
        dotFrame = 0;
        dotAnimator = ValueAnimator.ofInt(0, DOT_FRAMES.length - 1);
        dotAnimator.setDuration(DOT_FRAMES.length * 500L);
        dotAnimator.setRepeatCount(ValueAnimator.INFINITE);
        dotAnimator.addUpdateListener(anim -> tvStatus.setText(DOT_FRAMES[(int) anim.getAnimatedValue()]));
        dotAnimator.start();
    }

    private void stopDotAnimation() {
        if (dotAnimator != null) {
            dotAnimator.cancel();
            dotAnimator = null;
        }
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;
        etInput.setText("");
        viewModel.sendMessage(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.checkModelChange();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 场景1: 录音中切后台 → 自动停止
        viewModel.stopListeningOnPause();
        stopPulseAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voicePulseAnimator != null) {
            voicePulseAnimator.cancel();
        }
        stopDotAnimation();
        viewModel.stopTts();
        binding = null;
    }
}
