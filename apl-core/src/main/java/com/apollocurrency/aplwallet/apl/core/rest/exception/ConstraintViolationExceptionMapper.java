package com.apollocurrency.aplwallet.apl.core.rest.exception;

import com.apollocurrency.aplwallet.api.response.ResponseBase;
import com.apollocurrency.aplwallet.apl.core.rest.ApiErrors;
import org.jboss.resteasy.api.validation.ResteasyViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Iterator;

/**
 * Generic exception mapper for resteasy validation-provider.
 *
 * @author isegodin
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ResteasyViolationException> {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintViolationExceptionMapper.class);

    @SuppressWarnings("unchecked")
    @Override
    public Response toResponse(ResteasyViolationException exception) {
        ResponseBase responseEntity = new ResponseBase();
        responseEntity.errorCode = ApiErrors.CONSTRAINT_VIOLATION_ERROR_CODE;

        StringBuilder errorDescription = new StringBuilder();
        for (ConstraintViolation<?> viol : exception.getConstraintViolations()) {
            String message = viol.getMessage();
            String parameter = viol.getPropertyPath().toString();
            errorDescription.append (String.format("%s %s, got value %s\n", parameter, message, viol.getInvalidValue()));
        }
        responseEntity.errorDescription = errorDescription.toString();

        return Response.status(Response.Status.OK)
                .entity(responseEntity)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String buildMessage(ConstraintViolation<?> violation) {
        String name = null;
        for (Iterator<Path.Node> iterator = violation.getPropertyPath().iterator(); iterator.hasNext(); ) {
            name = iterator.next().getName();
        }
        if (name != null) {
            return name + ": " + violation.getMessage();
        }
        return violation.getMessage();
    }

}