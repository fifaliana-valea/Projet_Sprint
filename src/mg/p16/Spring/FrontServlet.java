package mg.p16.Spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.p16.models.CustomException;
import mg.p16.utile.Mapping;

@MultipartConfig
public class FrontServlet extends HttpServlet {
    private String packageName;
    private static final List<String> controllerNames = new ArrayList<>();
    private final HashMap<String, Mapping> urlMapping = new HashMap<>();
    private String hostName;
    private String urlAuth;
    private CustomException customException = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageName = config.getInitParameter("packageControllerName");
        hostName = config.getInitParameter("auth");
        urlAuth = config.getInitParameter("url_auth");
        Fonction utile = new Fonction();
        try {
            if (packageName == null || packageName.isEmpty()) {
                throw new Exception("Le nom du package du contrôleur n'est pas spécifié.");
            }
            utile.scanControllers(packageName, controllerNames, urlMapping);
        } catch (CustomException e) {
            customException = e;
            System.out.println("ato oooo");
        } catch (Exception e) {
            customException = (CustomException) e;
            System.out.println("ato oooo");
            throw new ServletException("Erreur lors de l'initialisation du framework", e);
        }
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        if (customException != null) {
            Fonction.displayErrorPage(out, customException);
        }
        Fonction.getValueMethod(request, response, urlMapping, hostName, out, urlAuth);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
        }
    }
}
