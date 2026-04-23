package com.smartcampus.resource;

import com.smartcampus.application.DataStore;
import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    // GET /api/v1/sensors/{sensorId}/readings
    @GET
    public Response getReadings() {
        List<SensorReading> history = DataStore.readings.get(sensorId);
        return Response.ok(Map.of(
            "sensorId", sensorId,
            "count",    history == null ? 0 : history.size(),
            "readings", history == null ? List.of() : history
        )).build();
    }

    // POST /api/v1/sensors/{sensorId}/readings
    @POST
    public Response addReading(SensorReading reading) {
        if (reading == null) {
            return Response.status(400)
                    .entity(Map.of("error", "Reading body is required."))
                    .build();
        }
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' not found."))
                    .build();
        }
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus()) ||
            "OFFLINE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                "Sensor '" + sensorId + "' is " + sensor.getStatus() + " and cannot accept readings."
            );
        }

        SensorReading stored = new SensorReading(reading.getValue());
        DataStore.readings.get(sensorId).add(stored);

        // Side-effect: update parent sensor's currentValue
        sensor.setCurrentValue(stored.getValue());

        return Response.status(201)
                .entity(Map.of(
                    "message",            "Reading recorded successfully.",
                    "reading",            stored,
                    "sensorCurrentValue", sensor.getCurrentValue()
                ))
                .build();
    }

    // GET /api/v1/sensors/{sensorId}/readings/{readingId}  ← NEW
    @GET
    @Path("/{readingId}")
    public Response getReading(@PathParam("readingId") String readingId) {
        List<SensorReading> history = DataStore.readings.get(sensorId);
        if (history == null) {
            return Response.status(404)
                    .entity(Map.of("error", "No readings found for sensor '" + sensorId + "'."))
                    .build();
        }
        SensorReading found = history.stream()
                .filter(r -> r.getId().equals(readingId))
                .findFirst()
                .orElse(null);
        if (found == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Reading '" + readingId + "' not found."))
                    .build();
        }
        return Response.ok(found).build();
    }

    // DELETE /api/v1/sensors/{sensorId}/readings/{readingId}  ← NEW
    @DELETE
    @Path("/{readingId}")
    public Response deleteReading(@PathParam("readingId") String readingId) {
        List<SensorReading> history = DataStore.readings.get(sensorId);
        if (history == null) {
            return Response.status(404)
                    .entity(Map.of("error", "No readings found for sensor '" + sensorId + "'."))
                    .build();
        }
        boolean removed = history.removeIf(r -> r.getId().equals(readingId));
        if (!removed) {
            return Response.status(404)
                    .entity(Map.of("error", "Reading '" + readingId + "' not found."))
                    .build();
        }
        return Response.ok(Map.of("message", "Reading '" + readingId + "' deleted successfully.")).build();
    }
}
