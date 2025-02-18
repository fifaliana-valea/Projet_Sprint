package mg.p16.Spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.p16.annotations.Contraintes;
import mg.p16.models.*;
import mg.p16.utile.FileUpload;
import mg.p16.utile.Mapping;
import mg.p16.utile.MethodParamResult;
import mg.p16.utile.ResponsePage;
import mg.p16.utile.ResponseValidation;
import mg.p16.utile.StatusCode;
import mg.p16.utile.VerbAction;

public class Fonction {

    public void scanControllers(String packageName, ArrayList<Class<?>> controllerNames,
            HashMap<String, Mapping> urlMaping) throws Exception {
        try {
            // Charger le package et parcourir les classes
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);

            // Verification si le package n'existe pas
            if (resource == null) {
                throw new Exception("Le package specifie n'existe pas: " + packageName);
            }

            Path classPath = Paths.get(resource.toURI());
            Files.walk(classPath)
                    .filter(f -> f.toString().endsWith(".class"))
                    .forEach(f -> {
                        String className = packageName + "." + f.getFileName().toString().replace(".class", "");
                        try {
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(mg.p16.annotations.Annotation.Controlleur.class)
                                    && !Modifier.isAbstract(clazz.getModifiers())) {
                                controllerNames.add(clazz);

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            throw e;
        }
    }

    public static HashMap<String, Mapping> getUrlMapping(ArrayList<Class<?>> controllers)
            throws CustomException.BuildException {
        HashMap<String, Mapping> urlMapping = new HashMap<>();
        boolean classAnnotedAUth = false;
        String profil = "";
        for (Class<?> clazz : controllers) {

            if (clazz.isAnnotationPresent(mg.p16.annotations.Annotation.Auth.class)) {
                classAnnotedAUth = true;
                profil = clazz.getAnnotation(mg.p16.annotations.Annotation.Auth.class).value();
            }

            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {

                if (method.isAnnotationPresent(mg.p16.annotations.Annotation.Url.class)) {
                    String url = method.getAnnotation(mg.p16.annotations.Annotation.Url.class).value();
                    VerbAction verbAction = new VerbAction();
                    verbAction.setMethodeName(method.getName());
                    verbAction.setVerb(
                            method.isAnnotationPresent(mg.p16.annotations.Annotation.Post.class) ? "POST" : "GET");

                    if (!urlMapping.containsKey(url)) {
                        Mapping mapping = new Mapping();

                        if (classAnnotedAUth) {
                            mapping.setNeedAuth(true);
                            mapping.setProfil(profil);
                        }

                        if (method.isAnnotationPresent(mg.p16.annotations.Annotation.Auth.class)) {

                            if (classAnnotedAUth) {
                                throw new CustomException.BuildException(
                                        clazz.getName() + " is already annoted Auth , remove @Auth on method");
                            }

                            profil = method.getAnnotation(mg.p16.annotations.Annotation.Auth.class).value();
                            mapping.setNeedAuth(true);
                            mapping.setProfil(profil);
                        }

                        mapping.setClassName(clazz.getName());
                        mapping.setVerbActions(new ArrayList<>());
                        mapping.getVerbActions().add(verbAction);
                        urlMapping.put(url, mapping);
                    } else {
                        Mapping existingMapping = urlMapping.get(url);
                        existingMapping.getVerbActions().add(verbAction);
                    }

                }
            }
            classAnnotedAUth = false;
        }
        return urlMapping;
    }

    private static Object convertParameter(String value, Class<?> type) {
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
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }
        return null;
    }

    private static Object createAndPopulateObject(Class<?> paramType, String paramName,
            HttpServletRequest request) throws Exception {
        Object paramObject = paramType.getDeclaredConstructor().newInstance();
        Field[] fields = paramType.getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName();
            String fullParamName = paramName + "." + fieldName;
            if (Modifier.isFinal(field.getModifiers())) {
                continue; // Ignore les champs final
            }

            field.setAccessible(true);

            if (isSimpleType(field.getType())) {
                String fieldValue = request.getParameter(fullParamName);
                if (fieldValue != null) {
                    Object typedValue = convertParameter(fieldValue, field.getType());
                    field.set(paramObject, typedValue);
                }
            } else {
                // Vérifier si l'objet imbriqué a des paramètres dans la requête
                boolean hasNestedValues = request.getParameterMap().keySet().stream()
                        .anyMatch(key -> key.startsWith(fullParamName + "."));

                if (hasNestedValues) {
                    Object nestedObject = createAndPopulateObject(field.getType(), fullParamName, request);
                    field.set(paramObject, nestedObject);
                }
            }
        }

        return paramObject;
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                type.equals(String.class) ||
                type.equals(Integer.class) ||
                type.equals(Long.class) ||
                type.equals(Double.class) ||
                type.equals(Float.class) ||
                type.equals(Boolean.class);
    }

    public static Object getValueMethod(String methodName, HttpServletRequest req,
            HttpServletResponse res, String className, String url) throws Exception {
        Class<?> clazz = Class.forName(className);
        Object object = clazz.newInstance();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getName().equalsIgnoreCase(methodName)) {
                method.setAccessible(true);

                MethodParamResult paramResult = getMethodParameters(method, req);

                // Check for validation errors
                if (!paramResult.getErrorMap().isEmpty()) {
                    // Retrieve the previous ModelView from session if it exists
                    ModelView previousModelView = (ModelView) req.getSession().getAttribute("page_precedent");

                    if (previousModelView != null) {
                        previousModelView.mergeValidationErrors(paramResult.getErrorMap());
                        previousModelView.mergeValidationValues(paramResult.getValueMap());
                        return previousModelView;
                    }
                }

                Object[] methodParams = paramResult.getMethodParams();
                System.out.println("methode params=" + methodParams);
                Object obj = method.invoke(object, methodParams);

                // If the method returns a ModelView, store it in the session
                if (obj instanceof ModelView modelView) {
                    req.getSession().setAttribute("page_precedent", modelView);
                }

                if (method.isAnnotationPresent(mg.p16.annotations.Annotation.RestApi.class)) {
                    if (obj instanceof ModelView m) {
                        return new Gson().toJson(m.getData());
                    }
                    return new Gson().toJson(obj);
                }
                return obj;
            }
        }
        return null;
    }

    public static MethodParamResult getMethodParameters(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];
        Map<String, String> errorMap = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();

        for (int i = 0; i < parameters.length; i++) {
            String paramName = "";
            if (parameters[i].isAnnotationPresent(mg.p16.annotations.Annotation.Param.class)) {
                paramName = parameters[i].getAnnotation(mg.p16.annotations.Annotation.Param.class).value();
            } else {
                throw new Exception(
                        "Etu002635 ,le parametre " + parameters[i].getName() + " n'a pas d'annotation Param ");
            }

            Class<?> paramType = parameters[i].getType();

            if (!isSimpleType(paramType)) {
                try {
                    Object paramObject = createAndPopulateObject(paramType, paramName, request);
                    parameterValues[i] = paramObject;

                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Error de la creation du parametre d'object: " + paramName, e);
                }
            } else if (paramType.equals(CustomSession.class)) {
                parameterValues[i] = new CustomSession(request.getSession());
            } else if (paramType.equals(FileUpload.class)) {
                parameterValues[i] = FileUpload.handleFileUpload(request, paramName);
            } else {
                String paramValue = request.getParameter(paramName);
                if (paramValue == null) {
                    throw new IllegalArgumentException("Abscence du  parametre " + paramName);
                }
                parameterValues[i] = convertParameter(paramValue, paramType);
            }

            if (parameters[i].isAnnotationPresent(mg.p16.annotations.Annotation.Valid.class)) {
                List<ResponseValidation> errors = Contraintes.validateObject(parameterValues[i]);
                for (ResponseValidation responseValidation : errors) {
                    if (!responseValidation.getErrors().isEmpty()) {
                        errorMap.put("error_" + responseValidation.getInputName(),
                                String.join(", ", responseValidation.getErrors()));
                        valueMap.put("value_" + responseValidation.getInputName(),
                                responseValidation.getValue());
                    }
                }
            } else {
                List<ResponseValidation> errors = Contraintes.valider(parameterValues[i],
                        parameters[i].getAnnotations(),
                        paramName);
                if (!errors.get(0).getErrors().isEmpty()) {
                    errorMap.put("error_" + paramName, String.join(", ", errors.get(0).getErrors()));
                    valueMap.put("value_" + paramName, parameterValues[i]);
                }
            }
        }

        return new MethodParamResult(parameterValues, errorMap, valueMap);
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

    public static StatusCode processRequest(HttpServletRequest req, Mapping mapping) {
        boolean verbFound = false;

        for (VerbAction verbAction : mapping.getVerbActions()) {
            if (verbAction.getVerb().equalsIgnoreCase(req.getMethod())) {
                verbFound = true;
                return null;
            }
        }

        if (!verbFound) {
            return new StatusCode(400, "Bad Request", false, "can not access request method");
        }

        return null;
    }

    public static String removeRootSegment(String url) {
        int firstSlashIndex = url.indexOf('/');
        if (firstSlashIndex != -1) {
            int secondSlashIndex = url.indexOf('/', firstSlashIndex + 1);
            if (secondSlashIndex != -1) {
                return url.substring(secondSlashIndex);
            } else {
                return "/";
            }
        }
        return url;
    }

    public static boolean isRoot(String url) {
        return url.trim().length() == 1;
    }

    public static void checkAuthProfil(Mapping mapping,HttpServletRequest req,String hote_name)throws CustomException.RequestException{
        String hote = "hote";
        if(hote_name != null && hote_name != ""){
            hote = hote_name;
        }
        
        if (mapping.isNeedAuth()) {
            if(!mapping.getProfil().equals(req.getSession().getAttribute(hote))){
                throw new CustomException.RequestException("unauthorize");
            }
        }
    }

    public static ResponsePage processUrl(HashMap<String, Mapping> urlMapping, PrintWriter out, HttpServletRequest req, HttpServletResponse res, ArrayList<Class<?>> controleurs,String hote_name){
        Object urlValue = null;
        boolean trouve = false;
        String html = "";
        String url = Fonction.removeRootSegment(req.getRequestURI());
        
        try {
            html += Fonction.header(url, controleurs);
    
            for (Map.Entry<String, Mapping> entree : urlMapping.entrySet()) {
                String cle = entree.getKey();
                Mapping valeur = entree.getValue();
                try {
                    checkAuthProfil(valeur,req,hote_name);
                } catch (CustomException.RequestException e) {
                    return new ResponsePage(new StatusCode(401, "unauthorize", false, e.getMessage()), html);
                }

                if (cle.equals(url)) {
                    VerbAction matchingVerbe = null;
                    for (VerbAction verbAction : valeur.getVerbActions()) {
                        if (verbAction.getVerb().equalsIgnoreCase(req.getMethod())) {
                            matchingVerbe = verbAction;
                            break;
                        }
                    }
    
                    if (matchingVerbe != null) {
                        StatusCode processR = processRequest(req, valeur);
                        if (processR != null) {
                            return new ResponsePage(processR, html);
                        }
                        try {
                            urlValue = Fonction.getValueMethod(matchingVerbe.getMethodeName(), req, res, valeur.getClassName(), url);
                            
                            html += "<BIG><p>URLMAPPING:</BIG>" + valeur.getClassName() + "_" + matchingVerbe.getMethodeName() + "</p>";
                            html += "</br>";
                            html += "<BIG><p>MethodeValue:</BIG>";
                            html += urlValue;

                            if (urlValue instanceof String s) {
                                html += s;
                            } else if (urlValue instanceof ModelView m) {
                                Fonction.sendModelView(m, req, res);
                            } else if (urlValue instanceof JsonElement j) {
                                html += j;
                            } else {
                                Class<?> cls = Class.forName(valeur.getClassName());
                                return new ResponsePage(new StatusCode(500, "internal server error", false,
                                    "Impossible d'obtenir la valeur pour le type " 
                                    + urlValue.getClass() 
                                    + " dans la méthode " + matchingVerbe.getMethodeName() 
                                    + "\n à " + valeur.getClassName() + "." 
                                    + matchingVerbe.getMethodeName() + "(" + cls.getSimpleName() + ".java)"), html);
                            }
                            trouve = true;
                        } catch (Exception e) {
                            return new ResponsePage(new StatusCode(500, "internal server error", false, e.getMessage()), html);
                        }
                    }
                    break;
                }
            }
            
            if (!Fonction.isRoot(url) && !trouve) {
                return new ResponsePage(new StatusCode(404, "url not found", false, "could not find " + req.getRequestURI()), html);
            }
            
            return new ResponsePage(new StatusCode(200, true), html);
        } catch (Exception e) {
            return new ResponsePage(new StatusCode(500, "internal server error", false, e.getMessage()), html);
        }
    }

    public static void processStatus(StatusCode statusCode)
            throws CustomException.BuildException, CustomException.RequestException {
        if (!statusCode.isSuccess()) {
            if (statusCode.getStatus() == 500) {
                throw new CustomException.BuildException(statusCode.getMessage());
            } else {
                throw new CustomException.RequestException(statusCode.getMessage());
            }
        }
    }

    public static String header(String requestURI, ArrayList<Class<?>> controllers) {
        String html = "<HTML>" +
                "<HEAD><TITLE>Hello Hello</TITLE></HEAD>" +
                "<BODY>" +
                "</br>" +
                "<BIG>URL:</BIG>" +
                requestURI +
                "</br>" +
                "<BIG>CONTROLLER:</BIG>" + controllers +
                "</br>";
        return html;
    }
}
