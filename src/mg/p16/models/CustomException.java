package mg.p16.models;

public class CustomException extends Exception {
    private final int errorCode;
    private final String errorMessage;
    private final String errorDetails;

    public CustomException(int errorCode, String errorMessage, String errorDetails) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorDetails = errorDetails;
    }

    public int getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorDetails() { return errorDetails; }
}

