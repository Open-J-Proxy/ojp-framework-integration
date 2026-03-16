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

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserResourceTest {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return DeploymentFactory.createDeployment();
    }

    @ArquillianResource
    private URL base;

    @Test
    @Order(1)
    public void testCreateUser() {
        given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"createdAt\":\"2024-01-15T10:30:00\"}")
                .when()
                .post("users")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("username", equalTo("alice"))
                .body("email", equalTo("alice@example.com"))
                .body("createdAt", notNullValue());
    }

    @Test
    @Order(2)
    public void testGetUser() {
        Long id = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"username\":\"bob\",\"email\":\"bob@example.com\",\"createdAt\":\"2024-01-16T14:20:00\"}")
                .when()
                .post("users")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .get("users/" + id)
                .then()
                .statusCode(200)
                .body("username", equalTo("bob"))
                .body("email", equalTo("bob@example.com"));
    }

    @Test
    @Order(3)
    public void testUpdateUser() {
        Long id = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"username\":\"carol\",\"email\":\"carol@example.com\"}")
                .when()
                .post("users")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"username\":\"carol_updated\",\"email\":\"carol_updated@example.com\"}")
                .when()
                .put("users/" + id)
                .then()
                .statusCode(200)
                .body("username", equalTo("carol_updated"))
                .body("email", equalTo("carol_updated@example.com"));
    }

    @Test
    @Order(4)
    public void testDeleteUser() {
        Long id = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"username\":\"dave\",\"email\":\"dave@example.com\"}")
                .when()
                .post("users")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .delete("users/" + id)
                .then()
                .statusCode(204);

        given()
                .baseUri(base.toExternalForm())
                .when()
                .get("users/" + id)
                .then()
                .statusCode(404);
    }
}
