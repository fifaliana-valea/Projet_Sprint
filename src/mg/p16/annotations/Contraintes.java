package mg.p16.annotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.lang.annotation.Annotation;

public class Contraintes {

    // Méthode qui applique toutes les contraintes sur l'objet
    public static List<ResponseValidation> valider(Object obj, Annotation[] annotationsParam, String input) {
        List<ResponseValidation> result = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();

        // Vérification des contraintes sur le paramètre
        for (Annotation annotation : annotationsParam) {
            if (annotation instanceof mg.p16.annotations.Annotation.NotNull notnull) {
                if (obj == null || obj.equals("") ||
                        (obj instanceof Number && ((Number) obj).doubleValue() == 0) ||
                        (obj instanceof Character && ((Character) obj) == '\0') ||
                        (obj instanceof Byte && (Byte) obj == 0) ||
                        (obj instanceof Short && (Short) obj == 0) ||
                        (obj instanceof Integer && (Integer) obj == 0) ||
                        (obj instanceof Long && (Long) obj == 0) ||
                        (obj instanceof Float && (Float) obj == 0.0f) ||
                        (obj instanceof Double && (Double) obj == 0.0)) {
                    // Remplacer {fieldName} dans le message
                    String errorMessage = notnull.message().replace("{fieldName}", input);
                    erreurs.add(errorMessage);
                }
            }

            if (annotation instanceof mg.p16.annotations.Annotation.Size sizeConstraint) {
                if (!isValidSize(obj, sizeConstraint)) {
                    String errorMessage = sizeConstraint.message().replace("{fieldName}", input);
                    erreurs.add(errorMessage);
                }
            }

            if (annotation instanceof mg.p16.annotations.Annotation.Min minConstraint) {
                if (!isValidMin(obj, minConstraint)) {
                    String errorMessage = minConstraint.message().replace("{fieldName}", input);
                    erreurs.add(errorMessage);
                }
            }

            if (annotation instanceof mg.p16.annotations.Annotation.Max maxConstraint) {
                if (!isValidMax(obj, maxConstraint)) {
                    String errorMessage = maxConstraint.message().replace("{fieldName}", input);
                    erreurs.add(errorMessage);
                }
            }

            if (annotation instanceof mg.p16.annotations.Annotation.Email email) {
                if (!isValidEmail(obj)) {
                    String errorMessage = email.message().replace("{fieldName}", input);
                    erreurs.add(errorMessage);
                }
            }

            if (annotation instanceof mg.p16.annotations.Annotation.Pattern patternConstraint) {
                if (!isValidPattern(obj, patternConstraint)) {
                    String errorMessage = patternConstraint.message().replace("{fieldName}", input);
                    erreurs.add(errorMessage);
                }
            }

            if (annotation instanceof mg.p16.annotations.Annotation.Valid) {
                result.add(Contraintes.validerAttr(obj, input));
            }
        }

        result.add(new ResponseValidation(input, erreurs, obj));
        return result;
    }

    public static List<ResponseValidation> validateObject(Object obj) {
        List<ResponseValidation> errors = new ArrayList<>();

        try {
            Class<?> objClass = obj.getClass();
            Field[] fields = objClass.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                errors.addAll(Contraintes.valider(fieldValue, field.getAnnotations(), field.getName()));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return errors;
    }

    public static ResponseValidation validerAttr(Object obj, String inputName) {
        List<ResponseValidation> errors = validateObject(obj);

        List<String> combinedErrors = new ArrayList<>();
        Object value = null;

        for (ResponseValidation validation : errors) {
            if (!validation.getErrors().isEmpty()) {
                combinedErrors.addAll(validation.getErrors());
                if (value == null) {
                    value = validation.getValue();
                }
            }
        }

        // Créer et retourner une ResponseValidation avec les erreurs combinées
        return new ResponseValidation(inputName, combinedErrors, value);
    }

    // Méthode pour vérifier la taille (uniquement pour les String)
    private static boolean isValidSize(Object obj, mg.p16.annotations.Annotation.Size constraint) {
        if (obj instanceof String) {
            String value = (String) obj;
            int length = value != null ? value.length() : 0;
            return length >= constraint.min() && length <= constraint.max();
        }
        return false;
    }

    // Méthode pour vérifier la contrainte Min
    private static boolean isValidMin(Object obj, mg.p16.annotations.Annotation.Min constraint) {
        if (obj == null)
            return false;

        if (obj instanceof Integer) {
            return (Integer) obj >= constraint.value();
        } else if (obj instanceof Long) {
            return (Long) obj >= constraint.value();
        } else if (obj instanceof java.lang.Double) {
            return (java.lang.Double) obj >= constraint.value();
        } else if (obj instanceof Float) {
            return (Float) obj >= constraint.value();
        }
        return false;
    }

    // Méthode pour vérifier la contrainte Max
    private static boolean isValidMax(Object obj, mg.p16.annotations.Annotation.Max constraint) {
        if (obj == null)
            return false;

        if (obj instanceof Integer) {
            return (Integer) obj <= constraint.value();
        } else if (obj instanceof Long) {
            return (Long) obj <= constraint.value();
        } else if (obj instanceof java.lang.Double) {
            return (java.lang.Double) obj <= constraint.value();
        } else if (obj instanceof Float) {
            return (Float) obj <= constraint.value();
        }
        return false;
    }

    // Méthode pour vérifier la validité d'un email
    private static boolean isValidEmail(Object obj) {
        if (obj instanceof String) {
            String value = (String) obj;
            return value != null && value.matches("^[A-Za-z0-9+_.-]+@(.+)$");
        }
        return false;
    }

    // Méthode pour vérifier la conformité avec un pattern
    private static boolean isValidPattern(Object obj, mg.p16.annotations.Annotation.Pattern constraint) {
        if (obj instanceof String) {
            String value = (String) obj;
            return value != null && value.matches(constraint.regexp());
        }
        return false;
    }

}