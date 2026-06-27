package liltrip.worldToPng.core;

/**
 * Signals that a render could not be completed. Platform adapters deliver it as the cause of the
 * {@link java.util.concurrent.CompletionException} thrown by their render future.
 */
public class RenderException extends RuntimeException {

    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
