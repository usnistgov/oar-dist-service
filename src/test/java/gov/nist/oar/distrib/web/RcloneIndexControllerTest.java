package gov.nist.oar.distrib.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RcloneIndexController.class)
@AutoConfigureMockMvc(addFilters = false) // disable security
@TestPropertySource(properties = {
        "file.base-dir=${user.dir}/src/test/resources/datasets",
        "spring.thymeleaf.prefix=classpath:/templates/",
        "spring.thymeleaf.suffix=.html"
})
class RcloneIndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * The directory listing shows all files and subdirectories.
     */
    @Test
    void listingShowsFilesAndDirs() throws Exception {
        mockMvc.perform(get("/rclone/DSID_12345/"))
                .andExpect(status().isOk())
                .andExpect(view().name("rclone-index"))
                .andExpect(model().attribute("fileCount", 2L))
                .andExpect(model().attribute("dirCount", 2L))
                .andExpect(content().string(containsString("favicon.ico")))
                .andExpect(content().string(containsString("hello.txt")))
                .andExpect(content().string(containsString("explicit-sub/")))
                .andExpect(content().string(containsString("virtual-sub/")));
    }

    /**
     * GET /rclone/DSID_12345/file.txt forwards to GET /ds/DSID_12345/file.txt.
     * <p>
     * This makes it possible for the client to download a file from the
     * dataset by requesting the file by name at the rclone endpoint;
     * the request is forwarded to the existing /ds/{dsid}/{subPath} endpoint
     * that handles file downloads.
     */
    @Test
    void downloadForwardsTo_ds_Endpoint() throws Exception {
        mockMvc.perform(get("/rclone/DSID_12345/a.txt"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ds/DSID_12345/a.txt"));
    }

    /**
     * GET /rclone/DSID_12345/explicit-sub/ shows only the contents of that
     * subdirectory, and not the contents of the parent directory.
     */
    @Test
    void subdirectoryListingShowsOnlyThatFolder() throws Exception {
        mockMvc.perform(get("/rclone/DSID_12345/explicit-sub/"))
                .andExpect(status().isOk())
                .andExpect(view().name("rclone-index"))
                .andExpect(model().attribute("fileCount", 1L))
                .andExpect(model().attribute("dirCount", 0L))
                .andExpect(content().string(containsString("info.txt")))
                .andExpect(content().string(not(containsString("favicon.ico"))));
    }

    /**
     * GET /rclone/DSID_12345/explicit-sub/info.txt forwards to
     * GET /ds/DSID_12345/explicit-sub/info.txt.
     * <p>
     * This makes it possible for the client to download a file from a subdirectory
     * of
     * the dataset by requesting the file by name at the rclone endpoint;
     * the request is forwarded to the existing /ds/{dsid}/{subPath} endpoint
     * that handles file downloads.
     */
    @Test
    void nestedFileForward() throws Exception {
        mockMvc.perform(get("/rclone/DSID_12345/explicit-sub/info.txt"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ds/DSID_12345/explicit-sub/info.txt"));
    }

    /**
     * If the URL is missing a trailing slash, the controller should treat it
     * as a request for a file, and forward it to the existing /ds/{dsid}/{subPath}
     * endpoint that handles file downloads.
     */
    @Test
    void missingSlashTreatsAsFileAndForwards() throws Exception {
        mockMvc.perform(get("/rclone/DSID_12345/explicit-sub"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ds/DSID_12345/explicit-sub"));
    }

    /**
     * Ensures that a request to a non-existent dataset results in a 404 Not Found
     * status.
     * This test verifies that the controller correctly handles requests to datasets
     * that do not exist in the system, returning an appropriate error response.
     */
    @Test
    void nonexistentDatasetReturns404() throws Exception {
        mockMvc.perform(get("/rclone/NO_SUCH_DATASET/"))
                .andExpect(status().isNotFound());
    }

    /**
     * GET /rclone/DSID_12345/empty/ shows a directory listing for an empty folder.
     * <p>
     * This test verifies that when a request is made to an empty subdirectory
     * within a dataset, the response correctly indicates that there are no files
     * or directories present.
     */
    @Test
    void emptyFolderShowsNothing() throws Exception {
        // create an empty component folder under your test resources, e.g.
        // DSID_12345/empty/
        mockMvc.perform(get("/rclone/DSID_12345/empty/"))
                .andExpect(status().isOk())
                .andExpect(view().name("rclone-index"))
                .andExpect(model().attribute("fileCount", 0L))
                .andExpect(model().attribute("dirCount", 0L))
                .andExpect(content().string(containsString("No files")));
    }

}
