package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {

    @GET
    public Response discover() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api",     "Smart Campus Sensor & Room Management API");
        body.put("version", "1.0.0");
        body.put("status",  "OPERATIONAL");
        body.put("contact", Map.of(
            "name",  "Smart Campus Admin",
            "email", "admin@smartcampus.ac.uk"
        ));
        body.put("resources", Map.of(
            "rooms",   "/api/v1/rooms",
            "sensors", "/api/v1/sensors"
        ));
        body.put("links", Map.of(
            "self",    "/api/v1/",
            "rooms",   "/api/v1/rooms",
            "sensors", "/api/v1/sensors"
        ));
        return Response.ok(body).build();
    }
}
