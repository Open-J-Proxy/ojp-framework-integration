package com.example.shopservice;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application entry point. By extending {@link Application} and annotating
 * with {@link ApplicationPath}, no {@code web.xml} servlet mapping is required.
 * The empty path "" means resources are served at the WAR's context root.
 */
@ApplicationPath("")
public class ShopServiceApplication extends Application {
}
