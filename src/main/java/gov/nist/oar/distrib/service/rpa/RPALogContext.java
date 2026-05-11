package gov.nist.oar.distrib.service.rpa;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;

import gov.nist.oar.distrib.service.rpa.model.Record;

/**
 * Helper methods for keeping RPA workflow logging concise and correlated.
 */
public final class RPALogContext {

    public static final String REQ_ID = "rpaReqId";
    public static final String DATASET_ID = "rpaDatasetId";
    public static final String RECORD_ID = "rpaRecordId";
    public static final String CASE_NUM = "rpaCaseNum";
    public static final String ACTOR = "rpaActor";

    private RPALogContext() {
    }

    public static String begin(String datasetId, String recordId) {
        String reqId = MDC.get(REQ_ID);
        if (isBlank(reqId)) {
            reqId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            MDC.put(REQ_ID, reqId);
        }
        putIfPresent(DATASET_ID, datasetId);
        putIfPresent(RECORD_ID, recordId);
        return reqId;
    }

    public static void setDatasetId(String datasetId) {
        putIfPresent(DATASET_ID, datasetId);
    }

    public static void setRecordId(String recordId) {
        putIfPresent(RECORD_ID, recordId);
    }

    public static void setCaseNum(String caseNum) {
        putIfPresent(CASE_NUM, caseNum);
    }

    public static void setActor(String actor) {
        putIfPresent(ACTOR, actor);
    }

    public static void updateFromRecord(Record record) {
        if (record == null) {
            return;
        }
        setRecordId(record.getId());
        setCaseNum(record.getCaseNum());
        if (record.getUserInfo() != null) {
            setDatasetId(record.getUserInfo().getSubject());
        }
    }

    public static String requestId() {
        String reqId = MDC.get(REQ_ID);
        return isBlank(reqId) ? begin(null, null) : reqId;
    }

    public static Map<String, String> capture() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(context);
    }

    public static void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    public static String safeUrl(String url) {
        if (isBlank(url)) {
            return "unknown";
        }
        try {
            URI uri = new URI(url);
            StringBuilder out = new StringBuilder();
            if (uri.getScheme() != null) {
                out.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                out.append(uri.getHost());
            }
            if (uri.getPort() > 0) {
                out.append(":").append(uri.getPort());
            }
            if (uri.getPath() != null) {
                out.append(uri.getPath());
            }
            if (out.length() == 0) {
                return url;
            }
            return out.toString();
        } catch (URISyntaxException ex) {
            return url;
        }
    }

    public static String maskEmail(String email) {
        if (isBlank(email)) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 1 || at == email.length() - 1) {
            return email;
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    public static String emailDomain(String email) {
        if (isBlank(email)) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return "unknown";
        }
        return email.substring(at + 1);
    }

    public static int recipientCount(String recipients) {
        if (isBlank(recipients)) {
            return 0;
        }
        String[] parts = recipients.split(";");
        int count = 0;
        for (String part : parts) {
            if (!isBlank(part)) {
                count++;
            }
        }
        return count;
    }

    public static String summarizeApprovalStatus(String approvalStatus) {
        if (isBlank(approvalStatus)) {
            return "unknown";
        }
        int idx = approvalStatus.indexOf('_');
        return idx >= 0 ? approvalStatus.substring(0, idx) : approvalStatus;
    }

    private static void putIfPresent(String key, String value) {
        if (isBlank(value)) {
            return;
        }
        MDC.put(key, value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
