package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.rpa.RPARequestService;
import gov.nist.oar.distrib.service.rpa.client.GetRecordResponse;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.UnauthorizedException;
import gov.nist.oar.distrib.service.rpa.external.ExternalApiException;
import gov.nist.oar.distrib.service.rpa.external.ExternalGetRecordResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ds/rpa/v2")
public class RPAController {

    private final RPARequestService rpaRequestService;

    public RPAController(RPARequestService rpaRequestService) {
        this.rpaRequestService = rpaRequestService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetRecordResponse> getRecord(@PathVariable(name = "id") String id)
            throws ExternalApiException, RecordNotFoundException, UnauthorizedException {

        try {
            ExternalGetRecordResponse externalResponse = rpaRequestService.getRecord(id);
            GetRecordResponse getRecordResponse = externalResponse.toGetRecordResponse();
            return ResponseEntity.ok(getRecordResponse);
        } catch (ExternalApiException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<?> handleExternalApiException(ExternalApiException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<?> handleNotFoundException(RecordNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorizedException(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

}
