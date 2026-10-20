package dev.vsharms.clearance.controls;

/** Thrown when a check couldn't run (network blip, IdP down), as opposed to the tenant failing it. Worth retrying. */
public class TransientCheckException extends RuntimeException {
    public TransientCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
