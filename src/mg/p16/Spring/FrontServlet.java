package mg.p16.Spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import jakarta.servlet.annotation.MultipartConfig; 
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.p16.models.CustomException;
import mg.p16.utile.Mapping;
import mg.p16.utile.ResponsePage;
import mg.p16.utile.StatusCode;

@MultipartConfig
public class FrontServlet extends HttpServlet {
    private String packageName; // Variable pour stocker le nom du package
    private static ArrayList<Class<?>> controllerNames = new ArrayList<>();
    private HashMap<String, Mapping> urlMaping = new HashMap<>();
    private String hote_name;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageName = config.getInitParameter("packageControllerName"); // Recuperation du nom du package
        this.hote_name = getServletConfig().getInitParameter("auth");
        Fonction utile = new Fonction();
        try {
            // Verification si le packageControllerName n'existe pas
            if (packageName == null || packageName.isEmpty()) {
                throw new Exception("Le nom du package du contrôleur n'est pas specifie.");
            }
            // Scanne les contrôleurs dans le package
            System.out.println("n of controller: " + controllerNames.size());
            utile.scanControllers(packageName, controllerNames);
            Fonction.getUrlMapping(controllerNames,urlMaping);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processRequest(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/html");
        PrintWriter out = res.getWriter();
        ResponsePage responsePage =Fonction.processUrl(urlMaping, out, req, res, controllerNames,this.hote_name); 
        if (responsePage == null) {
            return;
        }
        StatusCode statusCode = responsePage.getStatusCode();
        out.println("Http "+statusCode.getStatus()+":"+statusCode.getName());
        try {
            Fonction.processStatus(statusCode);
            out.println(responsePage.getHtml());
        } catch (CustomException.BuildException e) {
            e.printStackTrace();
        } catch (CustomException.RequestException e) {
            out.println(e.getMessage());
        } catch (Exception e) {
            out.println(e.getMessage());
        }
        req.getSession().setAttribute("page",req.getRequestURI());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
        }
    }

}
