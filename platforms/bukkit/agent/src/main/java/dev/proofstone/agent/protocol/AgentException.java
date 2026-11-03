package dev.proofstone.agent.protocol;

/**
 * A failure that becomes an error envelope (spec §1–§2). Carries the HTTP status and the UPPER_SNAKE code.
 * Unchecked, so the game layer can throw it from main-thread tasks.
 */
public final class AgentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient Object detail;

    public AgentException(int status, String code, String message, Object detail) {
        super(message);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Object detail() {
        return detail;
    }
}
