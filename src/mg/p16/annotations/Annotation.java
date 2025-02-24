package mg.p16.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface Annotation {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Controller {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Url {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface RestApi {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Param {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Get {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Post {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Auth {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface Valid {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface Size {
        int min() default 0;

        int max() default Integer.MAX_VALUE;

        String message() default "{fieldName} doit être compris entre {min} et {max}.";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface Min {
        long value();

        String message() default "{fieldName} doit être supérieur ou égal à {value}.";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface Max {
        long value();

        String message() default "{fieldName} ne peut pas dépasser {value}.";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface Email {
        String message() default "L'adresse email de {fieldName} doit être valide.";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface Pattern {
        String regexp();

        String message() default "Le format de {fieldName} ne correspond pas à l'expression régulière.";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    public @interface NotNull {
        String message() default "{fieldName} ne peut pas etre null.";
    }
}