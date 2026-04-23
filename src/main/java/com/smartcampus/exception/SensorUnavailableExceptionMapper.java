package com.smartcampus.exception;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class SensorUnavailableExceptionMapper implements ExceptionMapper<SensorUnavailableException> {

    @Override
    public Response toResponse(SensorUnavailableException e) {
        return Response.status(403)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "status",  403,
                    "error",   "Forbidden",
                    "message", e.getMessage(),
                    "hint",    "Change the sensor status to ACTIVE before posting readings."
                ))
                .build();
    }
}
