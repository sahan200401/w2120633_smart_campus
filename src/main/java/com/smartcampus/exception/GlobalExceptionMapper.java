package com.smartcampus.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable throwable) {
        if (throwable instanceof WebApplicationException) {
            Response original = ((WebApplicationException) throwable).getResponse();

            // If the response already carries a custom entity (e.g. from our sub-resource
            // locator or explicit throw new WebApplicationException(Response...)) pass
            // it through untouched — it's already formatted correctly.
            if (original.hasEntity()) {
                return original;
            }

            // Jersey-generated HTTP errors (415, 405, 406, etc.) have no entity.
            // Wrap them in a consistent JSON envelope so every error response is JSON.
            int status = original.getStatus();
            String reason = Response.Status.fromStatusCode(status) != null
                    ? Response.Status.fromStatusCode(status).getReasonPhrase()
                    : "Error";

            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                        "status",  status,
                        "error",   reason,
                        "message", "HTTP " + status + " " + reason
                    ))
                    .build();
        }

        // Log full trace server-side only — never expose to client
        LOGGER.log(Level.SEVERE, "Unexpected error: " + throwable.getMessage(), throwable);

        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "status",  500,
                    "error",   "Internal Server Error",
                    "message", "An unexpected error occurred. Please contact the administrator."
                ))
                .build();
    }
}
