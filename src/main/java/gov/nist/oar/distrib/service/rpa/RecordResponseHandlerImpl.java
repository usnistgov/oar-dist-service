package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.EmailInfoWrapper;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RecordResponseHandlerImpl implements RecordResponseHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(RecordResponseHandlerImpl.class);

    /**
     * Email sender used to notify users about record creation events.
     */
    private EmailSender emailSender;

    private final RPAConfiguration rpaConfiguration;

    private RPADatasetCacher rpaDatasetCacher;

    @Autowired
    private RPACachingService rpaCachingService;

    /**
     * Constructs a new instance of the RecordResponseHandlerImpl class with the given {@link RPAConfiguration} and
     * {@link HttpURLConnection}.
     *
     * @param rpaConfiguration  the {@link RPAConfiguration} to use for email notifications
     * @param connectionFactory the {@link HttpURLConnection} factory to use for email notifications
     */
    public RecordResponseHandlerImpl(RPAConfiguration rpaConfiguration, HttpURLConnectionFactory connectionFactory) {
        this.rpaConfiguration = rpaConfiguration;
        // Set EmailSender
        this.emailSender = new EmailSender(rpaConfiguration, connectionFactory);
        // Set RPADatasetCacher
        this.rpaDatasetCacher = new DefaultRPADatasetCacher(rpaCachingService);
    }

    /**
     * Called when a record creation operation has been completed successfully.
     *
     * @param record the record that was created
     */
    @Override
    public void onRecordCreationSuccess(Record record) throws InvalidRequestException, RequestProcessingException {
        LOGGER.debug("Record with ID=" + record.getId() + " created successfully! Now sending emails...");
        // If sending email was successful
        if (this.emailSender.sendConfirmationEmailToEndUser(record)) {
            LOGGER.debug("Confirmation email sent to end user successfully! (RecordID=" + record.getId() + ")");
        } else {
            throw new RequestProcessingException("Unable to send confirmation email to end user");
        }

        // If sending email was successful
        if (this.emailSender.sendApprovalEmailToSME(record)) {
            LOGGER.debug("Request approval email sent to SME successfully! (RecordID=" + record.getId() + ")");
        } else {
            throw new RequestProcessingException("Unable to send request approval email to SME");
        }
    }

    /**
     * Called when a record creation operation has failed.
     *
     * @param statusCode the HTTP status code returned by the server
     *                   <p>
     *                   Note: This implementation does nothing for now, other than logging.
     */
    @Override
    public void onRecordCreationFailure(int statusCode) throws RequestProcessingException {
        LOGGER.debug("Failed to create record, status_code=" + statusCode);
        throw new RequestProcessingException("Failed to create record, status_code=" + statusCode);
    }

    /**
     * Called when a record status was updated to "Approved".
     * This uses {@link RPADatasetCacher} to cache the dataset.
     *
     * @param record the record that was updated
     */
    @Override
    public void onRecordUpdateApproved(Record record) throws InvalidRequestException, RequestProcessingException {
        LOGGER.info("User was approved by SME. Starting caching...");
        String datasetId = record.getUserInfo().getSubject();
        // NEW: case dataset using the RPADatasetCacher
        String randomId = this.rpaDatasetCacher.cache(datasetId);
        if (randomId == null)
            throw new RequestProcessingException("Caching process return a null randomId");
        LOGGER.info("Dataset was cached successfully. Sending email to user...");
        // Build Download URL
        String downloadUrl;
        try {
            downloadUrl = new URIBuilder(this.rpaConfiguration.getDatacartUrl())
                    .setParameter("id", randomId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        this.emailSender.sendDownloadEmailToEndUser(record, downloadUrl);
    }

    /**
     * Called when a record status was updated to "Declined".
     * <p>
     * Note: This implementation does nothing for now, other than logging.
     *
     * @param record the record that was updated
     */
    @Override
    public void onRecordUpdateDeclined(Record record) {
        LOGGER.debug("User was declined by SME");
    }

    private class EmailSender {
        /**
         * The key used to retrieve the Salesforce endpoint URL for sending emails.
         */
        private final static String SEND_EMAIL_ENDPOINT_KEY = "send-email-endpoint";

        /**
         * The JWT helper used to retrieve a valid access token for making API requests.
         */
        private final JWTHelper jwtHelper;

        /**
         * The provider used to generate email information based on record data.
         */
        EmailInfoProvider emailInfoProvider;

        /**
         * The configuration object used to retrieve endpoint URLs and other settings.
         */
        private RPAConfiguration rpaConfiguration;

        /**
         * The factory used to create HTTP connections for sending email requests.
         */
        private HttpURLConnectionFactory connectionFactory;

        /**
         * Creates a new instance of the EmailSender class.
         *
         * @param rpaConfiguration  the configuration object used to retrieve endpoint URLs and other settings
         * @param connectionFactory the factory used to create HTTP connections for sending email requests
         */
        public EmailSender(RPAConfiguration rpaConfiguration, HttpURLConnectionFactory connectionFactory) {
            this.rpaConfiguration = rpaConfiguration;
            this.emailInfoProvider = new EmailInfoProvider(rpaConfiguration);
            this.jwtHelper = JWTHelper.getInstance();
            this.connectionFactory = connectionFactory;
        }

        /**
         * Sets the factory used to create HTTP connections for sending email requests.
         *
         * @param connectionFactory the new factory to use for HTTP connections
         */
        public void setHttpURLConnectionFactory(HttpURLConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        /**
         * Sends an approval email to the SME for the given record.
         *
         * @param record the record for which to send the email
         * @return true if the email was sent successfully, false otherwise
         */
        private boolean sendApprovalEmailToSME(Record record) throws InvalidRequestException,
                RequestProcessingException {
            EmailInfo emailInfo = this.emailInfoProvider.getSMEApprovalEmailInfo(record);
            LOGGER.debug("EMAIL_INFO=" + emailInfo);
            return this.send(emailInfo) == HttpURLConnection.HTTP_OK;
        }

        /**
         * Sends a confirmation email to the end user for the given record.
         *
         * @param record the record for which to send the email
         * @return true if the email was sent successfully, false otherwise
         */

        private boolean sendConfirmationEmailToEndUser(Record record) throws InvalidRequestException,
                RequestProcessingException {
            EmailInfo emailInfo = this.emailInfoProvider.getEndUserConfirmationEmailInfo(record);
            LOGGER.debug("EMAIL_INFO=" + emailInfo);
            return this.send(emailInfo) == HttpURLConnection.HTTP_OK;
        }

        /**
         * Sends a download email to the end user for the given record and download URL.
         *
         * @param record      the record for which to send the email
         * @param downloadUrl the download URL to include in the email
         * @return true if the email was sent successfully, false otherwise
         */
        private boolean sendDownloadEmailToEndUser(Record record, String downloadUrl) throws InvalidRequestException,
                RequestProcessingException {
            EmailInfo emailInfo = this.emailInfoProvider.getEndUserApprovedEmailInfo(record, downloadUrl);
            LOGGER.debug("EMAIL_INFO=" + emailInfo);
            return this.send(emailInfo) == HttpURLConnection.HTTP_OK;
        }

        /**
         * Sends a declined email to the end user for the given record.
         *
         * @param record the record for which to send the email
         * @return true if the email was sent successfully, false otherwise
         */
        private boolean sendDeclinedEmailToEndUser(Record record) throws InvalidRequestException,
                RequestProcessingException {
            EmailInfo emailInfo = this.emailInfoProvider.getEndUserDeclinedEmailInfo(record);
            LOGGER.debug("EMAIL_INFO=" + emailInfo);
            return this.send(emailInfo) == HttpURLConnection.HTTP_OK;
        }

        /**
         * Sends an email using the given email information.
         *
         * @param emailInfo the email information to use for sending the email
         * @return the HTTP response code from the email service
         * @throws InvalidRequestException    if the email request is invalid
         * @throws RequestProcessingException if there is an error processing the email request
         */
        private int send(EmailInfo emailInfo) throws InvalidRequestException, RequestProcessingException {
            int responseCode;
            EmailInfoWrapper emailInfoWrapper = null;
            String sendEmailUri = this.rpaConfiguration.getSalesforceEndpoints().get(SEND_EMAIL_ENDPOINT_KEY);
            JWTToken token = jwtHelper.getToken();

            // Build URL
            String url;
            try {
                url = new URIBuilder(token.getInstanceUrl())
                        .setPath(sendEmailUri)
                        .build()
                        .toString();
            } catch (URISyntaxException e) {
                throw new RequestProcessingException("Error building URI: " + e.getMessage());
            }
            LOGGER.debug("SEND_EMAIL_URL=" + url);
            // Create payload
            String postPayload;
            try {
                postPayload = new ObjectMapper().writeValueAsString(emailInfo);
            } catch (JsonProcessingException e) {
                LOGGER.debug("Error while serializing user info: " + e.getMessage());
                throw new RequestProcessingException("Error while serializing user info: " + e.getMessage());
            }

            HttpURLConnection connection = null;

            try {
                URL requestUrl = new URL(url);
                connection = connectionFactory.createHttpURLConnection(requestUrl);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token.getAccessToken());
                // Set payload
                byte[] payloadBytes = postPayload.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true); // tell connection we are writing data to the output stream
                OutputStream os = connection.getOutputStream();
                os.write(payloadBytes);
                os.flush();
                os.close();
                LOGGER.debug("SEND_EMAIL_PAYLOAD=" + postPayload);
                responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) { // If created
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        LOGGER.debug("SEND_EMAIL_RESPONSE=" + response);
                        // Handle the response
                        emailInfoWrapper = new ObjectMapper().readValue(response.toString(), EmailInfoWrapper.class);
                    }
                } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) { // If bad request
                    LOGGER.error("Invalid request: " + connection.getResponseMessage());
                    throw new InvalidRequestException("Invalid request: " + connection.getResponseMessage());
                } else {
                    // Handle any other error response
                    LOGGER.error("Error response from Salesforce service: " + connection.getResponseMessage());
                    throw new RequestProcessingException("Error response from Salesforce service: " + connection.getResponseMessage());
                }

            } catch (MalformedURLException e) {
                // Handle the URL Malformed error
                LOGGER.error("Invalid URL: " + e.getMessage());
                throw new RequestProcessingException("Invalid URL: " + e.getMessage());
            } catch (IOException e) {
                // Handle the I/O error
                LOGGER.error("Error sending GET request: " + e.getMessage());
                throw new RequestProcessingException("I/O error: " + e.getMessage());
            } finally {
                // Close the connection
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return responseCode;
        }
    }
}
