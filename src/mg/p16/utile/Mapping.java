package mg.p16.utile;

import java.util.ArrayList;
import java.util.List;

public class Mapping {
    
    private String className;
    private List<VerbAction> verbActions;
    
 
    public Mapping(String className) {
        this.className = className;
        this.verbActions =new ArrayList<>();
    }

    public String getClassName() {
        return className;
    }

    public void setVerbActions(VerbAction verbAction){
        this.verbActions.add(verbAction);
    }

    public List<VerbAction> getVerbActions() {
        return verbActions;
    }

    public void setVerbActions(List<VerbAction> verbActions) {
        this.verbActions = verbActions;
    }

    public boolean isVerbPresent(String verbToCheck) {
        for (VerbAction action : this.verbActions) {
            if (action.getVerb().equalsIgnoreCase(verbToCheck)) {
                return true;
            }
        }
        return false;
    }

}
