package mg.p16.Spring;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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

    public static Object convertParameter(String value, Class<?> type) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return value;
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        // Ajoutez d'autres conversions necessaires ici
        return null;
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                type.equals(String.class) ||
                type.equals(FileUpload.class) ||
                type.equals(Integer.class) ||
                type.equals(Long.class) ||
                type.equals(Double.class) ||
                type.equals(Part.class) ||
                type.equals(Float.class) ||
                type.equals(Boolean.class);
    }

    private static Object createAndPopulateObject(Class<?> paramType, String paramName, HttpServletRequest request)
            throws Exception {
        Object paramObject = paramType.getDeclaredConstructor().newInstance();
        Field[] fields = paramType.getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName();
            field.setAccessible(true);

            try {
                if (isSimpleType(field.getType())) {
                    String fieldValue = request.getParameter(fieldName);
                    Object convertedValue = (fieldValue != null) ? convertParameter(fieldValue, field.getType())
                            : getDefaultValue(field.getType());
                    String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    Method setter = paramType.getMethod(setterName, field.getType());
                    setter.invoke(paramObject, convertedValue);
                    String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    Method getterMethod = paramType.getMethod(getterName);
                    Object fieldValues = getterMethod.invoke(paramObject); // objetInstance doit etre l'objet dont tu
                                                                           // veux recuperer les valeurs

                    System.out.println(
                            String.format("La valeur de %s dans createAndPopulateObject : %s", fieldValue,
                                    fieldValues));

                } else {
                    Object nestedObject = createAndPopulateObject(field.getType(), fieldName, request);
                    field.set(paramObject, nestedObject);
                }
            } catch (Exception e) {
                // Continue setting even if an exception occurs
                field.set(paramObject, getDefaultValue(field.getType()));
            }
        }

        return paramObject;
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class)
                return false;
            if (type == char.class)
                return '\0';
            if (type == byte.class || type == short.class || type == int.class || type == long.class ||
                    type == float.class || type == double.class)
                return 0;
        }
        return null;
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
            } else {
                mg.p16.annotations.Annotation.Valid validAnnotation = param
                        .getAnnotation(mg.p16.annotations.Annotation.Valid.class);
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
                            "Erreur lors de la creation de l'objet parametre : " + param.getName(), e);
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
            // Gerer la reponse selon le type de retour de la methode
            if (method.isAnnotationPresent(mg.p16.annotations.Annotation.RestApi.class)) {
                response.setContentType("application/json");
                if (returnValue instanceof ModelView m) {
                    out.println(new Gson().toJson(m.getData()));
                }
                out.println(new Gson().toJson(returnValue));
            } else {
                if (returnValue instanceof ModelView modelView) {
                    request.getSession().setAttribute("page_precedent", modelView);
                    sendModelView(modelView, request, response);
                }
            }

        } catch (CustomException e) {
            displayErrorPage(out, e);

        } catch (Exception e) {
            // Afficher la page d'erreur
            response.setContentType("text/html");
            displayErrorPage(out, new CustomException(500, "Erreur interne du serveur",
                    "Une erreur inattendue s'est produite : " + e.getMessage()));
        }
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
        System.out.println(modelView.getUrl());
        for (Map.Entry<String, Object> entry : modelView.getData().entrySet()) {
            request.setAttribute(entry.getKey(), entry.getValue());
            System.out.println(entry.getKey() + "_" + entry.getValue());

        }

        for (Map.Entry<String, String> errorEntry : modelView.getValidationErrors().entrySet()) {
            request.setAttribute(errorEntry.getKey(), errorEntry.getValue());
            System.out.println(errorEntry.getKey() + " : " + errorEntry.getValue());
        }

        for (Map.Entry<String, Object> valueEntry : modelView.getValidationValues().entrySet()) {
            request.setAttribute(valueEntry.getKey(), valueEntry.getValue());
            System.out.println(valueEntry.getKey() + " : " + valueEntry.getValue());
        }

        RequestDispatcher dispatch = request.getRequestDispatcher(modelView.getUrl());
        dispatch.forward(request, response);
    }
}
