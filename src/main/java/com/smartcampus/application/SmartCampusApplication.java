package com.smartcampus.application;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import com.smartcampus.exception.GlobalExceptionMapper;
import com.smartcampus.exception.LinkedResourceNotFoundExceptionMapper;
import com.smartcampus.exception.RoomNotEmptyExceptionMapper;
import com.smartcampus.exception.SensorUnavailableExceptionMapper;
import com.smartcampus.filter.LoggingFilter;
import com.smartcampus.resource.DiscoveryResource;
import com.smartcampus.resource.RoomResource;
import com.smartcampus.resource.SensorResource;

// @ApplicationPath maps all JAX-RS resources under /api/v1
// This works together with web.xml which maps Jersey servlet to /api/v1/*
// Final URL: http://localhost:8080/api/v1/...  (ROOT.war → no context prefix)
@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        // Resources
        classes.add(DiscoveryResource.class);
        classes.add(RoomResource.class);
        classes.add(SensorResource.class);
        // Exception Mappers
        classes.add(RoomNotEmptyExceptionMapper.class);
        classes.add(LinkedResourceNotFoundExceptionMapper.class);
        classes.add(SensorUnavailableExceptionMapper.class);
        classes.add(GlobalExceptionMapper.class);
        // Filters
        classes.add(LoggingFilter.class);
        return classes;
    }
}
