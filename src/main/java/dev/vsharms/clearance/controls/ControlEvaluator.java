package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.tenant.Tenant;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ControlEvaluator {

    private final List<Control> controls;
    private final MeterRegistry metrics;

    public ControlEvaluator(List<Control> controls, MeterRegistry metrics) {
        this.controls = controls; // Spring hands these over in @Order order
        this.metrics = metrics;
    }

    /** Runs every control (no short circuit) so the audit trail shows the full picture, not just the first failure. */
    public List<ControlResult> evaluate(Tenant tenant) {
        List<ControlResult> results = new ArrayList<>(controls.size());
        for (Control control : controls) {
            Timer.Sample sample = Timer.start(metrics);
            ControlResult result = control.evaluate(tenant);
            sample.stop(metrics.timer("clearance.control.duration", "control", control.id()));
            metrics.counter("clearance.control.results", "control", control.id(),
                    "passed", String.valueOf(result.passed())).increment();
            results.add(result);
        }
        return results;
    }
}
