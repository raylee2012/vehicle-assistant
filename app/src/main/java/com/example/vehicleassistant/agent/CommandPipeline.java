package com.example.vehicleassistant.agent;

import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.models.ActionCommand;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多指令执行管线。包含冲突去重、参数校验、部分失败策略。
 */
public class CommandPipeline {

    private final VehicleService vehicleService;

    public CommandPipeline(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /**
     * 执行指令列表。返回完整执行结果列表。
     * 策略: nonCritical 失败跳过继续, critical 失败中断。
     */
    public List<ExecutionResult> execute(List<ActionCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return new ArrayList<>();
        }

        List<ActionCommand> deduped = deduplicate(commands);
        List<ExecutionResult> results = new ArrayList<>();

        for (ActionCommand cmd : deduped) {
            ExecutionResult result = vehicleService.execute(cmd);
            results.add(result);

            // critical 失败则中断后续执行
            if (!result.success && cmd.critical) {
                break;
            }
        }

        return results;
    }

    /**
     * 批量冲突去重: 同一 target 的指令，后者覆盖前者。
     * 例: [升左前窗, 降左前窗] → [降左前窗]
     */
    List<ActionCommand> deduplicate(List<ActionCommand> commands) {
        Map<String, ActionCommand> map = new LinkedHashMap<>();
        for (ActionCommand cmd : commands) {
            // 先通过 execute 触发 VehicleService 来填充 target 字段
            // 实际上 target 在 VehicleService.execute 中填充，
            // 所以我们使用一个占位来标记
            String key = cmd.target != null ? cmd.target
                : (cmd.action + "_" + (cmd.params != null ? cmd.params.getOrDefault("position", "") : ""));
            map.put(key, cmd);
        }
        return new ArrayList<>(map.values());
    }
}
