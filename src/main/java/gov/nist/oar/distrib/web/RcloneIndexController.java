package gov.nist.oar.distrib.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides HTTP endpoints under /rclone/** that expose a dataset directory
 * structure in a way compatible with rclone’s HTTP backend.
 *
 * <ul>
 * <li>GET /rclone/{dsid}/ -- list top‐level files & folders for dataset
 * {dsid}</li>
 * <li>GET /rclone/{dsid}/{path}/ -- list virtual subdirectory</li>
 * <li>GET /rclone/{dsid}/{file} -- download a file (FORWARDED to /ds/...)</li>
 * </ul>
 */
@Controller
public class RcloneIndexController {

    Logger logger = LoggerFactory.getLogger(RcloneIndexController.class);

    @Value("${file.base-dir}")
    private String baseDir;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd-MMM-yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Format a file size in bytes into a human-readable string.
     * Examples: 42 B, 1.4 KB, 12.5 MB, 1.2 GB, 1024 TB, 1.1 PB
     * 
     * @param bytes the size in bytes
     * @return a string rendering of the size in bytes
     */
    private String humanReadable(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * Handles HTTP requests under /rclone/**, which is an endpoint tree
     * exposing a dataset directory structure in a way compatible with rclone’s
     * HTTP backend.
     * <p>
     * If the request is to a file (i.e. the URL doesn't end with a slash), it is
     * forwarded to the existing /ds/{dsid}/{subPath} endpoint.
     * <p>
     * Otherwise, it returns a HTML directory listing page. The page attributes
     * include:
     * <ul>
     * <li>files: a list of FileInfo objects representing the child entries,
     * which either have a non-null <code>contents</code> attribute (for
     * files) or a non-null <code>components</code> attribute (for
     * directories)</li>
     * <li>currentPath: the relative path of the current directory, or the empty
     * string if the request is to the root of the dataset</li>
     * <li>dirCount: the number of directories in the current directory</li>
     * <li>fileCount: the number of files in the current directory</li>
     * </ul>
     */
    @GetMapping("/rclone/**")
    public Object handleRclone(HttpServletRequest req, Model model)
            throws ResourceNotFoundException, DistributionException {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        boolean isDir = uri.endsWith("/");
        String rel = extractRelativePath(uri, ctx);

        // split dsid and optional subpath
        String[] parts = rel.split("/", 2);
        String dsid = parts[0];
        String subPath = parts.length > 1 ? parts[1].replaceFirst("/$", "") : "";

        if (!isDir) {
            // file download, forward to existing /ds/{dsid}/{subPath} endpoint
            return "forward:/ds/" + dsid + "/" + subPath;
        }

        // directory listing
        Path dsDir = Paths.get(baseDir).resolve(dsid).toAbsolutePath().normalize();
        JsonNode comps = loadComponents(dsDir);

        // collect child entries
        List<FileInfo> entries = listDirectoryEntries(dsDir, comps, subPath);
        long dirs = entries.stream().filter(FileInfo::isDirectory).count();
        long files = entries.size() - dirs;

        model.addAttribute("files", entries);
        model.addAttribute("currentPath", subPath.isEmpty() ? "" : subPath + "/");
        model.addAttribute("dirCount", dirs);
        model.addAttribute("fileCount", files);
        return "rclone-index";
    }

    // ## Helper Methods ##

    /**
     * Return the part of the given URI that is relative to the context path
     * plus "/rclone/". If the URI does not start with the given context path
     * plus "/rclone/", return an empty string.
     */
    private String extractRelativePath(String uri, String ctx) {
        String prefix = ctx + "/rclone/";
        return uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
    }

    /**
     * Return the "components" array from the nerdm.json file for the given
     * dataset. If the file does not exist, throw a ResourceNotFoundException.
     * If the file exists but there is an error reading it, throw a
     * DistributionException.
     *
     * @param dsDir the directory containing the dataset
     * @return a JsonNode representing the "components" array
     * @throws ResourceNotFoundException if the file does not exist
     * @throws DistributionException     if there is an error reading the file
     */
    private JsonNode loadComponents(Path dsDir) throws ResourceNotFoundException, DistributionException {
        Path nf = dsDir.resolve("nerdm.json");
        if (!Files.exists(nf))
            throw ResourceNotFoundException.forID(dsDir.getFileName().toString());
        try {
            JsonNode doc = mapper.readTree(nf.toFile());
            JsonNode comps = doc.path("components");
            return comps.isArray()
                    ? comps
                    : mapper.createArrayNode();
        } catch (IOException e) {
            throw new DistributionException("Error reading nerdm.json for " + dsDir, e);
        }
    }

    /**
     * Returns true if the given component’s @type array contains an entry
     * whose local name (after the last “:”) equals the given shortType.
     */
    private boolean hasType(JsonNode component, String shortType) {
        for (JsonNode t : component.path("@type")) {
            String txt = t.asText();
            int idx = txt.lastIndexOf(':');
            String local = idx >= 0 ? txt.substring(idx + 1) : txt;
            if (shortType.equals(local)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSubcollection(JsonNode component) {
        return hasType(component, "Subcollection");
    }

    private boolean isRestrictedAccessPage(JsonNode component) {
        return hasType(component, "RestrictedAccessPage");
    }

    /**
     * Build a list of {@link FileInfo} objects representing the contents of the
     * given subdirectory within the dataset directory. Handles both explicit
     * Subcollections (folders marked in metadata) and “virtual” folders
     * inferred from filepaths.
     *
     * @param datasetDir   the root path of the dataset on disk
     * @param components   the “components” array from nerdm.json for this dataset
     * @param subdirectory the current folder path inside the dataset (empty for
     *                     root)
     * @return a sorted list of FileInfo entries (files and folders)
     */
    private List<FileInfo> listDirectoryEntries(Path datasetDir,
            JsonNode components,
            String subdirectory) {

        // Only consider components whose filepath starts with the current folder
        // prefix.
        String prefix = subdirectory.isEmpty() ? "" : subdirectory + "/";

        // Case‐insensitive TreeMap for more natural order, so “Apple.txt” and
        // “apple.txt” end up
        // adjacent.
        TreeMap<String, FileInfo> entriesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // Resolve the actual on‐disk folder for size and timestamp lookups.
        Path subdirectoryOnDisk = datasetDir.resolve(subdirectory);

        for (JsonNode component : components) {
            // Skip any restricted access components
            if (isRestrictedAccessPage(component))
                continue;

            JsonNode filepathNode = component.get("filepath");
            if (filepathNode == null) {
                // No filepath—skip
                continue;
            }
            String filepath = filepathNode.asText();

            // Skip everything not in our current “virtual” folder
            if (!filepath.startsWith(prefix)) {
                continue;
            }

            // Chop off the prefix so “prefix/foo/bar.txt” gives “foo/bar.txt”
            String remainder = filepath.substring(prefix.length());
            if (remainder.isEmpty()) {
                // If metadata listed the folder itself (“prefix/”), skip that
                continue;
            }

            // Detect explicit Subcollections (folders marked explicitly in metadata)
            boolean isSubcollection = isSubcollection(component);

            // Decide how to present this entry:
            // - If an explicit Subcollection, always show “name/”
            // - Else if there’s still a “/” in remainder, treat as a virtual subfolder
            // - Otherwise it’s a plain file
            String entryName;
            boolean isDirectory;
            if (isSubcollection) {
                entryName = remainder.endsWith("/") ? remainder : remainder + "/";
                isDirectory = true;
            } else if (remainder.contains("/")) {
                entryName = remainder.substring(0, remainder.indexOf("/") + 1);
                isDirectory = true;
            } else {
                entryName = remainder;
                isDirectory = false;
            }

            // computeIfAbsent() to make sure we only create one FileInfo per name
            entriesMap.computeIfAbsent(entryName, nameKey -> {
                String sizeString = "";
                String modifiedString = "";

                // For files (not directories), pull size & last‐modified from disk
                if (!isDirectory) {
                    try {
                        Path fileOnDisk = subdirectoryOnDisk.resolve(nameKey).normalize();
                        if (Files.exists(fileOnDisk) && !Files.isDirectory(fileOnDisk)) {
                            sizeString = humanReadable(Files.size(fileOnDisk));
                            modifiedString = DATE_FMT.format(
                                    Files.getLastModifiedTime(fileOnDisk).toInstant());
                        }
                    } catch (IOException e) {
                        // on error, just leave size/modified blank
                    }
                }

                return new FileInfo(nameKey, isDirectory, sizeString, modifiedString);
            });
        }

        return new ArrayList<>(entriesMap.values());
    }

    /**
     * Serves a file from the specified dataset directory and subpath.
     * 
     * This method resolves the file path and checks if the file exists
     * and is not a directory. If the file is valid, it creates a resource
     * from the file URI and determines its content type. The response
     * includes the file as an attachment in the response body.
     * 
     * @param datasetDir the directory containing the dataset
     * @param subPath    the relative path within the dataset directory to the file
     * @return a ResponseEntity containing the resource if found, or a 404 response
     *         if not found
     * @throws IOException if an I/O error occurs
     */

    // private ResponseEntity<Resource> serveFile(Path datasetDir,
    // String subPath) throws IOException {
    // Path f = datasetDir.resolve(subPath).normalize();
    // if (!f.startsWith(datasetDir) || !Files.exists(f) || Files.isDirectory(f))
    // return ResponseEntity.notFound().build();

    // Resource res = new UrlResource(f.toUri());
    // String type = Files.probeContentType(f);
    // MediaType mt = (type != null)
    // ? MediaType.parseMediaType(type)
    // : MediaType.APPLICATION_OCTET_STREAM;

    // return ResponseEntity.ok()
    // .contentType(mt)
    // .header(HttpHeaders.CONTENT_DISPOSITION,
    // "attachment; filename=\"" + f.getFileName() + "\"")
    // .body(res);
    // }

    /** DTO for Thymeleaf directory listing */
    public static class FileInfo {
        private final String name;
        private final boolean directory;
        private final String size;
        private final String modified;

        public FileInfo(String name, boolean dir, String size, String mod) {
            this.name = name;
            this.directory = dir;
            this.size = size;
            this.modified = mod;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }

        public String getSize() {
            return size;
        }

        public String getModified() {
            return modified;
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorInfo> handleDatasetNotFound(ResourceNotFoundException ex,
            HttpServletRequest req) {
        ErrorInfo info = createErrorInfo(req,
                404,
                "Dataset not found",
                "No such dataset: ",
                ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)

                .body(info);
    }

    /**
     * Create Error Information object to be returned to the client as a result of
     * failed request
     * 
     * @param req        the request object the resulted in an error
     * @param errorcode  the HTTP status code to return
     * @param pubMessage the message to return to the client
     * @param logMessage a message to record in the log
     * @param exception  the message from the original exception that motivates this
     *                   error response
     * @return ErrorInfo the object to return to the client
     */
    protected ErrorInfo createErrorInfo(HttpServletRequest req, int errorcode, String pubMessage,
            String logMessage, String exception) {
        String URI = "unknown";
        String method = "unknown";
        try {
            if (req != null) {
                URI = req.getRequestURI();
                method = req.getMethod();
            }
            logger.error(logMessage + " " + URI + " " + exception);
        } catch (Exception ex) {
            logger.error("Exception while processing error. " + ex.getMessage());
        }
        return new ErrorInfo(URI, errorcode, pubMessage, method);
    }
}
