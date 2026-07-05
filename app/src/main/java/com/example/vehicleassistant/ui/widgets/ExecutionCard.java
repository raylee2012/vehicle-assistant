package com.example.vehicleassistant.ui.widgets;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

public class ExecutionCard extends LinearLayout {

    private ImageView ivStatus;
    private TextView tvAction;
    private TextView tvDetail;
    private TextView tvStatusBadge;

    public ExecutionCard(Context context) {
        super(context);
        init(context);
    }

    public ExecutionCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_execution_card, this, true);
        ivStatus = findViewById(R.id.iv_status);
        tvAction = findViewById(R.id.tv_action);
        tvDetail = findViewById(R.id.tv_detail);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
    }

    public void bind(ExecutionResult result) {
        tvAction.setText(result.action);
        tvDetail.setText(result.message);

        if (result.success) {
            ivStatus.setImageResource(android.R.drawable.ic_dialog_info);
            tvStatusBadge.setText("成功");
            tvStatusBadge.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            ivStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            tvStatusBadge.setText("失败");
            tvStatusBadge.setTextColor(Color.parseColor("#F44336"));
        }
    }
}
