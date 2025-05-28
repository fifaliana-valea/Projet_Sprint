package mg.p16.Spring;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import mg.p16.annotations.Contraintes;
import mg.p16.annotations.ResponseValidation;
import mg.p16.models.CustomException;
import mg.p16.models.CustomSession;
import mg.p16.models.ModelView;
import mg.p16.utile.FileUpload;
import mg.p16.utile.Mapping;
import mg.p16.utile.MethodParamResult;
import mg.p16.utile.VerbAction;
import java.util.Date;

public class Fonction {

    public void scanControllers(String packageName, List<String> controllerNames, HashMap<String, Mapping> urlMapping)
            throws Exception {
        try {
            // Charger toutes les ressources associees au package
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);

            List<File> directories = new ArrayList<>();

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File directory = new File(resource.toURI());
                if (directory.exists() && directory.isDirectory()) {
                    directories.add(directory);
                }
            }

            // Scanner toutes les classes dans les repertoires trouves
            for (File directory : directories) {
                scanDirectory(directory, packageName, controllerNames, urlMapping);
            }
        } catch (Exception e) {
            throw new Exception("Erreur lors du scan des contrôleurs", e);
        }
    }

    private void scanDirectory(File directory, String packageName, List<String> controllerNames,
            HashMap<String, Mapping> urlMapping) throws Exception {
        File[] files = directory.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Scan recursivement les sous-repertoires
                scanDirectory(file, packageName + "." + file.getName(), controllerNames, urlMapping);
            } else if (file.getName().endsWith(".class")) {
                // Construire le nom de la classe complete
                String className = packageName + "." + file.getName().replace(".class", "");
                processClass(className, controllerNames, urlMapping);
            }
        }
    }

    private void processClass(String className, List<String> controllerNames, HashMap<String, Mapping> urlMaping)
            throws Exception {
        boolean classAnnotedAuth = false;
        String profil = "";

        Class<?> clazz = Class.forName(className);

        if (clazz.isAnnotationPresent(mg.p16.annotations.Annotation.Controller.class)
                && !Modifier.isAbstract(clazz.getModifiers())) {
            controllerNames.add(clazz.getSimpleName());

            if (clazz.isAnnotationPresent(mg.p16.annotations.Annotation.Auth.class)) {
                classAnnotedAuth = true;
                profil = clazz.getAnnotation(mg.p16.annotations.Annotation.Auth.class).value();
            }

            Method[] methods = clazz.getDeclaredMethods(); // Prendre seulement les methodes declarees
            for (Method method : methods) {
                // Verifie si la methode a l'annotation @Url
                if (!method.isAnnotationPresent(mg.p16.annotations.Annotation.Url.class)) {
                    throw new CustomException(403,
                            "Annotation @Url manquante dans " + className,
                            "Toutes les methodes d'un contrôleur doivent avoir @Url. " +
                                    "Methode sans @Url : " + method.getName());
                }

                mg.p16.annotations.Annotation.Url urlAnnotation = method
                        .getAnnotation(mg.p16.annotations.Annotation.Url.class);
                String url = urlAnnotation.value();
                String verb = "GET";

                if (method.isAnnotationPresent(mg.p16.annotations.Annotation.Post.class)) {
                    verb = "POST";
                }

                VerbAction verbAction = new VerbAction(method.getName(), verb);
                Mapping map = new Mapping(className);

                if (classAnnotedAuth) {
                    map.setNeedAuth(true);
                    map.setProfil(profil);
                    System.out.println("Le profil est : " + map.getProfil() + " dans le controller");
                }

                if (method.isAnnotationPresent(mg.p16.annotations.Annotation.Auth.class)) {
                    if (classAnnotedAuth) {
                        throw new CustomException(401,
                                "Conflit d'annotations", clazz.getName()
                                        + " est deja annote avec @Auth, veuillez retirer @Auth de la methode.");
                    }
                    profil = method.getAnnotation(mg.p16.annotations.Annotation.Auth.class).value();
                    map.setNeedAuth(true);
                    map.setProfil(profil);
                    System.out.println("Le profil est : " + map.getProfil() + " dans la methode");
                }

                if (urlMaping.containsKey(url)) {
                    Mapping existingMap = urlMaping.get(url);
                    if (existingMap.isVerbAction(verbAction)) {
                        throw new Exception("Duplicate URL: " + url);
                    } else {
                        existingMap.setVerbActions(verbAction);
                    }
                } else {
                    map.setVerbActions(verbAction);
                    urlMaping.put(url, map);
                }
            }
        }
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class)
                return false;
            if (type == char.class)
                return '\0';
            if (type == byte.class || type == short.class || type == int.class ||
                    type == long.class || type == float.class || type == double.class) {
                return 0;
            }
        }

        // Ajout des types communs
        if (type == String.class)
            return "";
        if (type == LocalDate.class)
            return LocalDate.MIN;
        if (type == LocalDateTime.class)
            return LocalDateTime.MIN;
        if (type == Timestamp.class)
            return new Timestamp(0);

        return null;
    }

    public static Object convertParameter(String value, Class<?> type) {
        if (value == null || value.trim().isEmpty()) {
            return getDefaultValue(type);
        }

        try {
            if (type == String.class) {
                return value;
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(value);
            } else if (type == long.class || type == Long.class) {
                return Long.parseLong(value);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(value);
            } else if (type == float.class || type == Float.class) {
                return Float.parseFloat(value);
            } else if (type == byte.class || type == Byte.class) {
                return Byte.parseByte(value);
            } else if (type == short.class || type == Short.class) {
                return Short.parseShort(value);
            } else if (type == char.class || type == Character.class) {
                return value.length() > 0 ? value.charAt(0) : '\0';
            } else if (type == LocalDate.class) {
                return parseLocalDate(value);
            } else if (type == LocalDateTime.class) {
                return parseLocalDateTime(value);
            } else if (type == Timestamp.class) {
                return parseTimestamp(value);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Conversion error for value: " + value + " to type: " + type.getSimpleName(), e);
        }

        throw new UnsupportedOperationException("Type not supported: " + type.getName());
    }

    private static LocalDate parseLocalDate(String value) {
        // Essaye les formats communs dans l'ordre
        String[] patterns = {
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "yyyyMMdd"
        };

        for (String pattern : patterns) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // Passer au format suivant
            }
        }
        throw new DateTimeParseException("Failed to parse date: " + value, value, 0);
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        // Essaye les formats communs dans l'ordre
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // Passer au format suivant
            }
        }
        throw new DateTimeParseException("Failed to parse datetime: " + value, value, 0);
    }

    private static Timestamp parseTimestamp(String value) {
        try {
            // Essaye le format standard en premier
            return Timestamp.valueOf(value);
        } catch (IllegalArgumentException e) {
            try {
                // Essaye avec LocalDateTime
                LocalDateTime ldt = parseLocalDateTime(value);
                return Timestamp.valueOf(ldt);
            } catch (DateTimeParseException e2) {
                try {
                    // Essaye avec les anciens formats Date
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date date = sdf.parse(value);
                    return new Timestamp(date.getTime());
                } catch (ParseException e3) {
                    throw new IllegalArgumentException("Failed to parse timestamp: " + value, e3);
                }
            }
        }
    }

    // Check if the parameter type is an array
    private static boolean isObjectArray(Class<?> paramType) {
        return paramType.isArray();
    }

    private static Object convertToObjectArray(String paramValue, Class<?> paramType) throws Exception {
        if (paramType.isArray()) {
            // Split the values by commas
            String[] values = paramValue.split(","); // Assuming values are comma-separated in the request

            // Check if the component type is primitive (e.g., int[], double[], etc.)
            Class<?> componentType = paramType.getComponentType();

            if (componentType.isPrimitive()) {
                // If it's a primitive type, we create an array of the corresponding primitive
                // type
                if (componentType == int.class) {
                    int[] array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        array[i] = Integer.parseInt(values[i].trim());
                    }
                    return array;
                } else if (componentType == double.class) {
                    double[] array = new double[values.length];
                    for (int i = 0; i < values.length; i++) {
                        array[i] = Double.parseDouble(values[i].trim());
                    }
                    return array;
                } else if (componentType == long.class) {
                    long[] array = new long[values.length];
                    for (int i = 0; i < values.length; i++) {
                        array[i] = Long.parseLong(values[i].trim());
                    }
                    return array;
                } else if (componentType == boolean.class) {
                    boolean[] array = new boolean[values.length];
                    for (int i = 0; i < values.length; i++) {
                        array[i] = Boolean.parseBoolean(values[i].trim());
                    }
                    return array;
                }
                // Add more primitive types if necessary (e.g., float, short, etc.)
            } else {
                // If it's not a primitive, assume it's an object array (e.g., String[],
                // Integer[], etc.)
                Object[] array = (Object[]) Array.newInstance(componentType, values.length);
                for (int i = 0; i < values.length; i++) {
                    array[i] = convertParameter(values[i].trim(), componentType);
                }
                return array;
            }
        }
        return null;
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                type.equals(String.class) ||
                type.equals(Timestamp.class) ||
                type.equals(LocalDate.class) ||
                type.equals(LocalDateTime.class) ||
                type.equals(FileUpload.class) ||
                type.equals(Integer.class) ||
                type.equals(Long.class) ||
                type.equals(Double.class) ||
                type.equals(Part.class) ||
                type.equals(Float.class) ||
                type.equals(Boolean.class);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static Object createAndPopulateObject(Class<?> paramType, String paramName, HttpServletRequest request)
            throws Exception {
        Object paramObject = paramType.getDeclaredConstructor().newInstance();
        Field[] fields = paramType.getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName();
            String fullParamName = paramName + "." + fieldName;
            System.out.println("Processing field: " + fullParamName);

            try {
                field.setAccessible(true); // Permet de modifier un champ privé

                if (isSimpleType(field.getType())) {
                    // Cas des types primitifs (int, String, Date, etc.)
                    String fieldValue = request.getParameter(fullParamName);
                    Object convertedValue = (fieldValue != null)
                            ? convertParameter(fieldValue, field.getType())
                            : getDefaultValue(field.getType());

                    field.set(paramObject, convertedValue);
                    System.out.println(String.format("Valeur de %s : %s", fullParamName, convertedValue));

                } else {
                    // Cas des objets imbriqués (ex: Ville, Avion)
                    String idFieldParam = fullParamName + ".id" + capitalize(field.getType().getSimpleName());
                    String idValue = request.getParameter(idFieldParam);

                    if (idValue != null) { // Si l'ID est présent, on crée l'objet imbriqué
                        Object nestedObject = field.getType().getDeclaredConstructor().newInstance();
                        Field idField = field.getType()
                                .getDeclaredField("id" + capitalize(field.getType().getSimpleName()));
                        idField.setAccessible(true);
                        idField.set(nestedObject, convertParameter(idValue, idField.getType()));

                        field.set(paramObject, nestedObject);
                        System.out.println("Objet imbriqué " + fieldName + " créé avec ID : " + idValue);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'affectation de " + fullParamName + " : " + e.getMessage());
            }
        }

        return paramObject;
    }

    public static MethodParamResult getMethodParameters(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];
        Map<String, String> errorMap = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            String paramValue = "";

            if (paramType.equals(CustomSession.class)) {
                parameterValues[i] = new CustomSession(request.getSession());
            } else if (isSimpleType(paramType)) {
                mg.p16.annotations.Annotation.Param paramAnnotation = param
                        .getAnnotation(mg.p16.annotations.Annotation.Param.class);
                if (paramAnnotation == null) {
                    throw new Exception("Etu002635 : le parametre " + param.getName() + " dans " + method.getName()
                            + " doit avoir une annotation @Param");
                }
                paramValue = paramAnnotation.value();
                if (paramType.equals(FileUpload.class)) {
                    parameterValues[i] = FileUpload.handleFileUpload(request, paramValue);
                } else {
                    String valeurs = request.getParameter(paramValue);
                    parameterValues[i] = convertParameter(valeurs, paramType);
                }

                List<ResponseValidation> errors = Contraintes.valider(parameterValues[i], param.getAnnotations(),
                        paramAnnotation.value());
                valueMap.put("value_" + paramAnnotation.value(), parameterValues[i]);
                if (!errors.isEmpty() && !errors.get(0).getErrors().isEmpty()) {
                    errorMap.put("error_" + paramAnnotation.value(), String.join(", ", errors.get(0).getErrors()));
                }
            } else if (isObjectArray(paramType)) {
                // Récupération de l'annotation @Param
                mg.p16.annotations.Annotation.Param paramAnnotation = param
                        .getAnnotation(mg.p16.annotations.Annotation.Param.class);
                if (paramAnnotation == null) {
                    throw new Exception("Etu002635 : le paramètre " + param.getName() + " dans " + method.getName()
                            + " doit avoir une annotation @Param");
                }

                paramValue = paramAnnotation.value();
                String valeurs = request.getParameter(paramValue);
                System.out.println("le valeur de l'objet est : " + valeurs);

                // Convertir en tableau Object[] ou tableau primitif
                Object array = convertToObjectArray(valeurs, paramType);

                if (array != null) {
                    Class<?> componentType = paramType.getComponentType();

                    // Si le type est primitif (ex: double[], int[], etc.)
                    if (componentType.isPrimitive()) {
                        // On a déjà un tableau primitif (par exemple int[], double[])
                        parameterValues[i] = array;
                    } else {
                        // Si ce n'est pas un type primitif, on peut copier normalement dans un tableau
                        // typé
                        Object[] objectArray = (Object[]) array; // cast to Object[] since convertToObjectArray returns
                                                                 // Object[]
                        Object typedArray = Array.newInstance(componentType, objectArray.length);
                        System.arraycopy(objectArray, 0, typedArray, 0, objectArray.length);
                        parameterValues[i] = typedArray;
                    }
                } else {
                    throw new Exception(
                            "Impossible de convertir le paramètre " + param.getName() + " en " + paramType.getName());
                }
            } else {
                mg.p16.annotations.Annotation.Valid validAnnotation = param
                        .getAnnotation(mg.p16.annotations.Annotation.Valid.class);
                paramValue = validAnnotation.value();
                if (validAnnotation == null) {
                    throw new Exception("Etu002635 : le parametre " + param.getName() + " dans " + method.getName()
                            + " doit avoir une annotation @Valid");
                }

                try {
                    Object paramObject = createAndPopulateObject(paramType, paramValue, request);
                    List<ResponseValidation> errors = Contraintes.validateObject(paramObject);

                    for (ResponseValidation responseValidation : errors) {
                        valueMap.put("value_" + responseValidation.getInputName(), responseValidation.getValue());
                        if (!responseValidation.getErrors().isEmpty()) {
                            errorMap.put("error_" + responseValidation.getInputName(),
                                    String.join(", ", responseValidation.getErrors()));
                        }
                    }
                    parameterValues[i] = paramObject;
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Erreur lors de la création de l'objet paramètre : " + paramType.getClass().getSimpleName(),
                            e);
                }
            }
        }

        return new MethodParamResult(parameterValues, errorMap, valueMap);
    }

    public static void checkAuthProfil(Mapping mapping, HttpServletRequest request, HttpServletResponse response,
            String hoteName, String url_auth) throws Exception {
        String hote = (hoteName != null && !hoteName.isEmpty()) ? hoteName : "hote";
        if (mapping.isNeedAuth()) {
            String profil = (String) request.getSession().getAttribute(hote);
            System.out.println("le profile est " + profil);

            if (profil == null || profil.isEmpty()) {
                String errorMessage = "Vous devez etre authentifie pour acceder a cette ressource.";
                String redirectUrl = url_auth + "?errors_auth=" + URLEncoder.encode(errorMessage, "UTF-8");

                System.out.println("Tentative d'acces sans authentification. Redirection vers : " + redirectUrl);
                response.sendRedirect(redirectUrl);
                return;
            }

            System.out.println("le profil du mapping est :" + mapping.getProfil());

            if (!mapping.getProfil().equals(profil)) {
                throw new CustomException(
                        401,
                        "Acces non autorise",
                        "L'utilisateur " + profil + " ne possede pas le profil requis ('" + mapping.getProfil()
                                + "') pour acceder a cette ressource.");

            }
        }
    }

    public static String removeRootSegment(String url) {
        String[] segments = url.split("/");
        if (segments.length > 2) {
            StringBuilder newUrl = new StringBuilder();
            for (int i = 2; i < segments.length; i++) {
                newUrl.append("/").append(segments[i]);
            }
            return newUrl.toString();
        }
        return "/";
    }

    public static void getValueMethod(HttpServletRequest request,
            HttpServletResponse response, HashMap<String, Mapping> urlMapping, String hoteName, PrintWriter out,
            String url_auth)
            throws IOException {
        String url = Fonction.removeRootSegment(request.getRequestURI());
        try {
            if (!urlMapping.containsKey(url)) {
                throw new CustomException(404, "Ressource introuvable",
                        "Le chemin specifie " + url + " ne correspond a aucune ressource disponible.");
            }

            Mapping mapping = urlMapping.get(url);
            Class<?> clazz = Class.forName(mapping.getClassName());
            Object object = clazz.getDeclaredConstructor().newInstance();
            System.out.println("le nom du classe dans le getValueMethod : " + object.getClass().getSimpleName());
            Method method = null;
            if (!mapping.isVerbPresent(request.getMethod())) {
                throw new CustomException(405, "Methode non autorisee",
                        "La methode HTTP " + request.getMethod() + " n'est pas permise pour cette ressource.");
            }

            checkAuthProfil(mapping, request, response, hoteName, url_auth);
            // Recherche de la methode appropriee
            for (Method m : clazz.getDeclaredMethods()) {
                for (VerbAction action : mapping.getVerbActions()) {
                    if (m.getName().equals(action.getMethodeName()) &&
                            action.getVerb().equalsIgnoreCase(request.getMethod())) {
                        method = m;
                        break;
                    }
                }
                if (method != null) {
                    break;
                }
            }

            if (method == null) {
                throw new CustomException(404, "Methode introuvable",
                        "Aucune methode appropriee n'a ete trouvee pour traiter la requete.");
            }

            MethodParamResult paramResult = getMethodParameters(method, request);

            // Check for validation errors
            if (!paramResult.getErrorMap().isEmpty()) {
                ModelView previousModelView = (ModelView) request.getSession().getAttribute("page_precedent");

                if (previousModelView != null) {
                    previousModelView.mergeValidationErrors(paramResult.getErrorMap());
                    previousModelView.mergeValidationValues(paramResult.getValueMap());

                    request.getSession().setAttribute("page_precedent", previousModelView);
                    sendModelView(previousModelView, request, response);
                    return;
                }
            }

            // Execution de la methode trouvee
            Object[] parameters = paramResult.getMethodParams();
            Object returnValue = method.invoke(object, parameters);
            if (returnValue instanceof String s) {
                out.println(s);
            }
            if (method.isAnnotationPresent(mg.p16.annotations.Annotation.RestApi.class)) {
                sendGson(returnValue, response, out);
            } else {
                if (returnValue instanceof ModelView modelView) {
                    request.getSession().setAttribute("page_precedent", modelView);
                    sendModelView(modelView, request, response);
                }
            }

        } catch (CustomException e) {
            displayErrorPage(out, e);

        } catch (Exception e) {
            // Afficher la page d'erreurj
            response.setContentType("text/html");
            System.out.println("tena ato ilay erreur");
            displayErrorPage(out, new CustomException(500, "Erreur interne du serveur",
                    "Une erreur inattendue s'est produite : " + e.getMessage()));
        }
    }

    public static void sendGson(Object data, HttpServletResponse response, PrintWriter out) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        out.println(new Gson().toJson(data));
        out.flush();
    }

    static void displayErrorPage(PrintWriter out, CustomException e) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang='fr'>");
        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<title>Erreur " + e.getErrorCode() + "</title>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; color: #333; background-color: #f4f4f4; }");
        out.println(".container { max-width: 600px; margin: auto; padding: 20px; background-color: #fff; "
                + "border: 1px solid #ddd; border-radius: 4px; box-shadow: 2px 2px 10px rgba(0, 0, 0, 0.1); }");
        out.println("h1 { color: #e74c3c; }");
        out.println("p { line-height: 1.5; }");
        out.println("a { color: #3498db; text-decoration: none; }");
        out.println("a:hover { text-decoration: underline; }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class='container'>");
        out.println("<h1>" + e.getErrorMessage() + "</h1>");
        out.println("<p><strong>Code d'erreur :</strong> " + e.getErrorCode() + "</p>");
        out.println("<p><strong>Detail d'erreur :</strong>" + e.getErrorDetails() + "</p>");
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }

    public static void sendModelView(ModelView modelView, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getMethod() {
                return "GET"; // Forcer la méthode à "GET"
            }
        };

        System.out.println(modelView.getUrl());

        // Ajouter les données au scope de la requête
        modelView.getData().forEach((key, value) -> {
            wrappedRequest.setAttribute(key, value);
            System.out.println(key + " : " + value);
        });

        // Ajouter les erreurs de validation
        modelView.getValidationErrors().forEach((key, value) -> {
            wrappedRequest.setAttribute(key, value);
            System.out.println("Erreur - " + key + " : " + value);
        });

        // Ajouter les valeurs validées
        modelView.getValidationValues().forEach((key, value) -> {
            wrappedRequest.setAttribute(key, value);
            System.out.println("Valeur validée - " + key + " : " + value);
        });

        // Rediriger vers la vue
        RequestDispatcher dispatch = wrappedRequest.getRequestDispatcher(modelView.getUrl());
        dispatch.forward(wrappedRequest, response);
    }

}
