package mg.p16.utile;

public class StatusCode {
    private int status;
    private String name;
    private boolean success;
    private String message;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StatusCode() {
    }

    
    public StatusCode(int status, boolean success, String name) {
        this.status = status;
        this.success = success;
        this.name = name;
    }

    public StatusCode(int status, boolean success) {
        this.status = status;
        this.success = success;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StatusCode(int status, String name, boolean success, String message) {
        this.status = status;
        this.name = name;
        this.success = success;
        this.message = message;
    }
    
}
