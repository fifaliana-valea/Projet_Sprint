package mg.p16.utile;

public class VerbAction {
    private String methodeName;
    private String verb;

    public VerbAction(String action, String verb) {
        this.methodeName = action;
        this.verb = verb;
    }

    public String getMethodeName() {
        return methodeName;
    }

    public void setMethodeName(String methodeName) {
        this.methodeName = methodeName;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    
   
}
