package ca.amartian.quickdot.ws.protocol;

public record ErrorMessage(String type, String code, String message) {
    public static ErrorMessage of(String code, String message) {
        return new ErrorMessage("error", code, message);
    }
}
