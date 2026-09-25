package com.sxw.sxwaiagent.evaluation.harness;

import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class HarnessEvalAdapterRegistry {
    private final Map<HarnessTargetCode, HarnessEvalAdapter> adapters = new EnumMap<>(HarnessTargetCode.class);

    public HarnessEvalAdapterRegistry(List<HarnessEvalAdapter> adapterList) {
        adapterList.forEach(adapter -> adapters.put(adapter.targetCode(), adapter));
    }

    public HarnessEvalAdapter require(HarnessTargetCode target) {
        HarnessEvalAdapter adapter = adapters.get(target);
        if (adapter == null) throw new IllegalArgumentException("Harness target is not registered: " + target);
        if (!adapter.capabilities().available()) {
            throw new IllegalStateException("Harness target " + target + " is unavailable: "
                + adapter.capabilities().unavailableReason());
        }
        return adapter;
    }
}
