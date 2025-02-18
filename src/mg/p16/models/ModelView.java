package mg.p16.models;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private String url;
    private HashMap<String, Object> data;
    private Map<String, String> validationErrors = new HashMap<>();
    private Map<String, Object> validationValues = new HashMap<>();

    public ModelView(String url) {
        this.url = url;
        this.data = new HashMap<>();
    }

    public void addObject(String name, Object value) {
        data.put(name, value);
    }

    public String getUrl() {
        return url;
    }

    public HashMap<String, Object> getData() {
        return data;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Map<String, String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public Map<String, Object> getValidationValues() {
        return validationValues;
    }

    public void setValidationValues(Map<String, Object> validationValues) {
        this.validationValues = validationValues;
    }

    public void mergeValidationErrors(Map<String, String> errors) {
        if (errors != null) {
            this.validationErrors=errors;
        }
    }

    public void mergeValidationValues(Map<String, Object> values) {
        if (values != null) {
            this.validationValues=values;
        }
    }
}
