package dev.vsharms.clearance.controls;

public record ControlResult(String controlId, boolean passed, String reason) {

    public static ControlResult pass(String controlId, String reason) {
        return new ControlResult(controlId, true, reason);
    }

    public static ControlResult fail(String controlId, String reason) {
        return new ControlResult(controlId, false, reason);
    }
}
