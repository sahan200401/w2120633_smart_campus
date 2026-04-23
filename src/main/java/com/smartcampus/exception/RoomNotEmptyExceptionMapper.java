package com.smartcampus.exception;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class RoomNotEmptyExceptionMapper implements ExceptionMapper<RoomNotEmptyException> {

    @Override
    public Response toResponse(RoomNotEmptyException e) {
        return Response.status(409)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "status",  409,
                    "error",   "Conflict",
                    "message", e.getMessage(),
                    "hint",    "Remove all sensors from the room before deleting it."
                ))
                .build();
    }
}
