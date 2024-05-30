package mg.p16.spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.p16.annotations.Annotation_Get;
import mg.p16.annotations.Annotation_controlleur;
import mg.p16.utile.Mapping;

public class FrontServlet extends HttpServlet {
    private String packageName; // Variable pour stocker le nom du package
    private static List<String> controllerNames = new ArrayList<>();
    HashMap<String, Mapping> urlMaping = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageName = config.getInitParameter("packageControllerName"); // Récupération du nom du package
        scanControllers(packageName);
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        StringBuffer requestURL = request.getRequestURL();
        String[] requestUrlSplitted = requestURL.toString().split("/");
        String controllerSearched = requestUrlSplitted[requestUrlSplitted.length - 1];

        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        if (!urlMaping.containsKey(controllerSearched)) {
            out.println("<p>" + "Aucune methode associee a ce chemin." + "</p>");
        } else {
            try {
                Mapping mapping = urlMaping.get(controllerSearched);
                Class<?> clazz = Class.forName(mapping.getClassName());
                Method method = clazz.getMethod(mapping.getMethodeName());
                Object object = clazz.getDeclaredConstructor().newInstance();
                Object returnValue = method.invoke(object);
                String string = (String) returnValue;
                out.println("<p> La valeur de return est : " + string + "</p>");
                out.close();

            } catch (Exception e) {
                // TODO: handle exception
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void scanControllers(String packageName) {
        try {

            // Charger le package et parcourir les classes
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);
            Path classPath = Paths.get(resource.toURI());
            Files.walk(classPath)
                    .filter(f -> f.toString().endsWith(".class"))
                    .forEach(f -> {
                        String className = packageName + "." + f.getFileName().toString().replace(".class", "");
                        try {
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(Annotation_controlleur.class)
                                    && !Modifier.isAbstract(clazz.getModifiers())) {
                                controllerNames.add(clazz.getSimpleName());
                                Method[] methods = clazz.getMethods();

                                for (Method m : methods) {
                                    if (m.isAnnotationPresent(Annotation_Get.class)) {
                                        Mapping mapping = new Mapping(className, m.getName());
                                        Annotation_Get annotation_Get = m.getAnnotation(Annotation_Get.class);
                                        String annotationValue = annotation_Get.value();
                                        urlMaping.put(annotationValue, mapping);
                                    }
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
