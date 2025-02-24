package mg.p16.utile;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public class FileUpload {
    String name;
    String path;
    byte[] bytes;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 Mo
    private static final String UPLOAD_DIRECTORY = "uploads";

    public FileUpload() {
    }

    public FileUpload(String name, String path, byte[] bytes) {
        this.name = name;
        this.path = path;
        this.bytes = bytes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Extrait le nom du fichier depuis le header Content-Disposition.
     */
    private static String extractFileName(Part part) {
        if (part == null)
            return "";
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition == null)
            return "";

        for (String item : contentDisposition.split(";")) {
            if (item.trim().startsWith("filename")) {
                return item.substring(item.indexOf("=") + 2, item.length() - 1).replace("\\", "/");
            }
        }
        return "";
    }

    /**
     * Gère l'upload du fichier.
     */
    public static FileUpload handleFileUpload(HttpServletRequest request, String inputFileParam)
            throws IOException, ServletException {

        Part filePart = request.getPart(inputFileParam);
        if (filePart == null)
            return null;

        String fileName = extractFileName(filePart);
        if (fileName.isEmpty())
            return null;

        // Vérification de la taille du fichier
        if (filePart.getSize() > MAX_FILE_SIZE) {
            throw new ServletException("Fichier trop volumineux ! (Max : " + (MAX_FILE_SIZE / (1024 * 1024)) + " Mo)");
        }

        // Vérification du type MIME
        String mimeType = filePart.getContentType();
        if (!isAllowedMimeType(mimeType)) {
            throw new ServletException("Type de fichier non autorisé !");
        }

        // Définir le dossier d'upload absolu
        String uploadDir = request.getServletContext().getRealPath("") + File.separator + UPLOAD_DIRECTORY;
        File uploadFolder = new File(uploadDir);
        if (!uploadFolder.exists() && !uploadFolder.mkdirs()) {
            throw new IOException("Impossible de créer le dossier d'upload !");
        }

        // Sauvegarde sécurisée du fichier
        Path uploadPath = Path.of(uploadDir, fileName);
        try (InputStream fileContent = filePart.getInputStream()) {
            Files.copy(fileContent, uploadPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new FileUpload(fileName, uploadPath.toString(), Files.readAllBytes(uploadPath));
    }

    /**
     * Vérifie si le type MIME du fichier est autorisé.
     */
    private static boolean isAllowedMimeType(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("application/pdf"));
    }

    /**
     * Gère l'upload de plusieurs fichiers.
     */
    public static void handleMultipleFileUploads(HttpServletRequest request, String inputFileParam)
            throws IOException, ServletException {

        Collection<Part> fileParts = request.getParts();
        for (Part filePart : fileParts) {
            if (filePart.getName().equals(inputFileParam)) {
                handleFileUpload(request, inputFileParam);
            }
        }
    }
}
