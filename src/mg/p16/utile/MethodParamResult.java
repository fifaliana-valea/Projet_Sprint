package mg.p16.utile;

import java.util.Map;

public class MethodParamResult {
    private Object[] methodParams;
    private Map<String, String> errorMap;
    private Map<String, Object> valueMap;

    public MethodParamResult(Object[] methodParams, Map<String, String> errorMap, Map<String, Object> valueMap) {
        this.methodParams = methodParams;
        this.errorMap = errorMap;
        this.valueMap = valueMap;
    }

    public Object[] getMethodParams() {
        return methodParams;
    }

    public Map<String, String> getErrorMap() {
        return errorMap;
    }

    public Map<String, Object> getValueMap() {
        return valueMap;
    }
}