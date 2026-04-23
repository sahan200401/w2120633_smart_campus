package com.smartcampus.resource;

import com.smartcampus.application.DataStore;
import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.Room;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    // GET /api/v1/rooms
    @GET
    public Response getAllRooms() {
        return Response.ok(new ArrayList<>(DataStore.rooms.values())).build();
    }

    // POST /api/v1/rooms
    @POST
    public Response createRoom(Room room) {
        if (room == null || room.getId() == null || room.getId().isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "Room id is required."))
                    .build();
        }
        if (DataStore.rooms.containsKey(room.getId())) {
            return Response.status(409)
                    .entity(Map.of("error", "Room '" + room.getId() + "' already exists."))
                    .build();
        }
        DataStore.rooms.put(room.getId(), room);
        return Response.status(201)
                .entity(Map.of(
                    "message", "Room created successfully.",
                    "room",    room,
                    "link",    "/api/v1/rooms/" + room.getId()
                ))
                .build();
    }

    // GET /api/v1/rooms/{roomId}
    @GET
    @Path("/{roomId}")
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);
        if (room == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Room '" + roomId + "' not found."))
                    .build();
        }
        return Response.ok(room).build();
    }

    // PUT /api/v1/rooms/{roomId}  ← NEW
    @PUT
    @Path("/{roomId}")
    public Response updateRoom(@PathParam("roomId") String roomId, Room updated) {
        Room room = DataStore.rooms.get(roomId);
        if (room == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Room '" + roomId + "' not found."))
                    .build();
        }
        if (updated.getName() != null)   room.setName(updated.getName());
        if (updated.getCapacity() > 0)   room.setCapacity(updated.getCapacity());
        return Response.ok(Map.of(
            "message", "Room updated successfully.",
            "room",    room
        )).build();
    }

    // DELETE /api/v1/rooms/{roomId}
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);
        if (room == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Room '" + roomId + "' not found."))
                    .build();
        }
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(
                "Room '" + roomId + "' has " + room.getSensorIds().size() +
                " sensor(s) still assigned: " + room.getSensorIds()
            );
        }
        DataStore.rooms.remove(roomId);
        return Response.ok(Map.of("message", "Room '" + roomId + "' deleted successfully.")).build();
    }
}
