package gov.nist.oar.distrib.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.service.RPACachingService;

@ExtendWith(MockitoExtension.class)
public class RPADataCachingControllerTest {

    @Mock
    RPACachingService rpaCachingService;

    RPADataCachingController controller = null;;

    MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        controller = new RPADataCachingController(rpaCachingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }


    @Test
    public void testCacheDataset() throws Exception {
        String dsid = "849E1CC6FBE2C4C7E053B3570681FE987034";
        String version = "v1";
        String expectedRandomId = "randomId123";

        when(rpaCachingService.cacheAndGenerateRandomId(eq(dsid), eq(version))).thenReturn(expectedRandomId);

        mockMvc.perform(put("/ds/rpa/cache/" + dsid)
                        .param("version", version))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedRandomId));

        verify(rpaCachingService).cacheAndGenerateRandomId(eq(dsid), eq(version));
    }

    @Test
    public void testRetrieveMetadata() throws Exception {
        String cacheId = "randomId123";
        Map<String, Object> expectedMetadata = new HashMap<>();
        expectedMetadata.put("filePath", "filePath");
        expectedMetadata.put("mediaType", "img");
        expectedMetadata.put("size", 999);

        when(rpaCachingService.retrieveMetadata(eq(cacheId))).thenReturn(expectedMetadata);

        ObjectMapper mapper = new ObjectMapper();
        String expectedJson = mapper.writeValueAsString(expectedMetadata);

        mockMvc.perform(get("/ds/rpa/dlset/" + cacheId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(expectedJson));

        verify(rpaCachingService).retrieveMetadata(eq(cacheId));
    }

    @Test
    public void testCacheDatasetViaARK() throws Exception {
        String dsid = "mds2-2909";
        String naan = "88434";
        String version = "v1";
        String arkId = "ark:/" + naan + "/" + dsid;
        String expectedRandomId = "randomId123";

        when(rpaCachingService.cacheAndGenerateRandomId(eq(dsid), eq(version))).thenReturn(expectedRandomId);

        mockMvc.perform(put("/ds/rpa/cache/" + arkId)
                        .param("version", version))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedRandomId));

        verify(rpaCachingService).cacheAndGenerateRandomId(eq(dsid), eq(version));
    }
}

