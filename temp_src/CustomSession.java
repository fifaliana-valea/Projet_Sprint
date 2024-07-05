package mg.p16.models;

import java.util.HashMap;

import jakarta.servlet.http.HttpSession;

public class CustomSession {
    private HttpSession session;

    public CustomSession(HttpSession session) {
        this.session = session;
    }

    public Object get(String key) {
        return this.session.getAttribute(key);
    }

    public void add(String key, Object objet) {
        this.session.setAttribute(key, objet);
    }

    public void delete(String key) {
        this.session.removeAttribute(key);
    }

}