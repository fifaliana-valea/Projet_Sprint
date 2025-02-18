package mg.p16.utile;

public class ResponsePage {
    private StatusCode statusCode;
    private String html;

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(StatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public ResponsePage(StatusCode statusCode, String html) {
        this.statusCode = statusCode;
        this.html = html;
    }

}
