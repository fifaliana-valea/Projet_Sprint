package mg.p16.annotations;

import java.util.List;

public class ResponseValidation {
    private String inputName;
    private List<String> errors;
    private Object value;
    
    public String getInputName() {
        return inputName;
    }
    public void setInputName(String inputName) {
        this.inputName = inputName;
    }
    public List<String> getErrors() {
        return errors;
    }
    public void setErrors(List<String> errors) {
        this.errors = errors; 
    }
    public ResponseValidation(String inputName, List<String> errors) {
        this.inputName = inputName;
        this.errors = errors;
    }
    public Object getValue() {
        return value;
    }
    public void setValue(Object value) {
        this.value = value;
    }
    public ResponseValidation(String inputName, List<String> errors, Object value) {
        this.inputName = inputName;
        this.errors = errors;
        this.value = value;
    }
    
    
}   
