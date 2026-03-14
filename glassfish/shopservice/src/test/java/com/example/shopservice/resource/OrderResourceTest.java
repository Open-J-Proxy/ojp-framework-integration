package com.example.shopservice.resource;

import com.example.shopservice.DeploymentFactory;
import io.restassured.http.ContentType;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URL;
import java.util.UUID;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderResourceTest {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return DeploymentFactory.createDeployment();
    }

    @ArquillianResource
    private URL base;

    private Long createUser() {
        String unique = UUID.randomUUID().toString();
        return given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"username\":\"orderuser_" + unique + "\","
                        + "\"email\":\"orderuser_" + unique + "@example.com\"}")
                .when()
                .post("users")
                .then()
                .extract().jsonPath().getLong("id");
    }

    @Test
    @Order(1)
    public void testCreateOrder() {
        Long userId = createUser();

        given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"user\":{\"id\":" + userId + "}}")
                .when()
                .post("orders")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("user.id", equalTo(userId.intValue()));
    }

    @Test
    @Order(2)
    public void testGetOrder() {
        Long userId = createUser();

        Long orderId = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"user\":{\"id\":" + userId + "}}")
                .when()
                .post("orders")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .get("orders/" + orderId)
                .then()
                .statusCode(200)
                .body("user.id", equalTo(userId.intValue()));
    }

    @Test
    @Order(3)
    public void testDeleteOrder() {
        Long userId = createUser();

        Long orderId = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"user\":{\"id\":" + userId + "}}")
                .when()
                .post("orders")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .delete("orders/" + orderId)
                .then()
                .statusCode(204);
    }
}
