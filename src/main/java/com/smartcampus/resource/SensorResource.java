package com.smartcampus.resource;

import com.smartcampus.application.DataStore;
import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    // GET /api/v1/sensors  OR  GET /api/v1/sensors?type=CO2
    @GET
    public Response getSensors(@QueryParam("type") String type) {
        List<Sensor> list = new ArrayList<>(DataStore.sensors.values());
        if (type != null && !type.isBlank()) {
            list = list.stream()
                       .filter(s -> s.getType().equalsIgnoreCase(type))
                       .collect(Collectors.toList());
        }
        return Response.ok(list).build();
    }

    // POST /api/v1/sensors
    @POST
    public Response createSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "Sensor id is required."))
                    .build();
        }
        if (DataStore.sensors.containsKey(sensor.getId())) {
            return Response.status(409)
                    .entity(Map.of("error", "Sensor '" + sensor.getId() + "' already exists."))
                    .build();
        }
        if (sensor.getRoomId() == null || !DataStore.rooms.containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                "Room '" + sensor.getRoomId() + "' does not exist."
            );
        }
        DataStore.sensors.put(sensor.getId(), sensor);
        DataStore.readings.put(sensor.getId(), new CopyOnWriteArrayList<>());

        // Link sensor to room
        Room room = DataStore.rooms.get(sensor.getRoomId());
        if (!room.getSensorIds().contains(sensor.getId())) {
            room.getSensorIds().add(sensor.getId());
        }

        return Response.status(201)
                .entity(Map.of(
                    "message", "Sensor registered successfully.",
                    "sensor",  sensor,
                    "link",    "/api/v1/sensors/" + sensor.getId() + "/readings"
                ))
                .build();
    }

    // GET /api/v1/sensors/{sensorId}
    @GET
    @Path("/{sensorId}")
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' not found."))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    // PUT /api/v1/sensors/{sensorId}  ← NEW
    @PUT
    @Path("/{sensorId}")
    public Response updateSensor(@PathParam("sensorId") String sensorId, Sensor updated) {
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' not found."))
                    .build();
        }
        if (updated.getType()   != null) sensor.setType(updated.getType());
        if (updated.getStatus() != null) sensor.setStatus(updated.getStatus());
        // Always update currentValue — checking != 0 would wrongly reject a valid 0.0 reading
        sensor.setCurrentValue(updated.getCurrentValue());
        return Response.ok(Map.of(
            "message", "Sensor updated successfully.",
            "sensor",  sensor
        )).build();
    }

    // DELETE /api/v1/sensors/{sensorId}  ← NEW
    @DELETE
    @Path("/{sensorId}")
    public Response deleteSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' not found."))
                    .build();
        }
        // Remove sensor from its room's list
        Room room = DataStore.rooms.get(sensor.getRoomId());
        if (room != null) {
            room.getSensorIds().remove(sensorId);
        }
        DataStore.sensors.remove(sensorId);
        DataStore.readings.remove(sensorId);

        return Response.ok(Map.of("message", "Sensor '" + sensorId + "' deleted successfully.")).build();
    }

    // Sub-resource locator  →  /api/v1/sensors/{sensorId}/readings
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
        if (!DataStore.sensors.containsKey(sensorId)) {
            throw new WebApplicationException(
                Response.status(404)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' not found."))
                    .build()
            );
        }
        return new SensorReadingResource(sensorId);
    }
}
