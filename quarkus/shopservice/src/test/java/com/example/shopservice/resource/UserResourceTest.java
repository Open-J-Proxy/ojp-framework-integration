package com.example.shopservice.resource;

import com.example.shopservice.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserResourceTest {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Test
    @Order(1)
    public void testCreateUser() {
        User user = new User();
        user.username = "alice";
        user.email = "alice@example.com";
        user.createdAt = LocalDateTime.parse("2024-01-15T10:30:00");

        given()
                .contentType(ContentType.JSON)
                .body(user)
                .when()
                .post("/users")
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
        Long id =
                given()
                        .contentType(ContentType.JSON)
                        .body(new User() {{ username = "bob"; email = "bob@example.com"; createdAt = LocalDateTime.parse("2024-01-16T14:20:00"); }})
                        .when()
                        .post("/users")
                        .then()
                        .extract().jsonPath().getLong("id");

        String retrievedCreatedAt = given()
                .when()
                .get("/users/" + id)
                .then()
                .statusCode(200)
                .body("username", equalTo("bob"))
                .body("email", equalTo("bob@example.com"))
                .extract().jsonPath().getString("createdAt");
        
        // Parse and format to ensure consistent comparison
        LocalDateTime dateTime = LocalDateTime.parse(retrievedCreatedAt);
        assertEquals("2024-01-16T14:20:00", dateTime.format(DATE_TIME_FORMATTER));
    }

    @Test
    @Order(3)
    public void testUpdateUser() {
        User user = new User();
        user.username = "carol";
        user.email = "carol@example.com";
        Long id =
                given()
                        .contentType(ContentType.JSON)
                        .body(user)
                        .when()
                        .post("/users")
                        .then()
                        .extract().jsonPath().getLong("id");

        user.username = "carol_updated";
        user.email = "carol_updated@example.com";
        given()
                .contentType(ContentType.JSON)
                .body(user)
                .when()
                .put("/users/" + id)
                .then()
                .statusCode(200)
                .body("username", equalTo("carol_updated"))
                .body("email", equalTo("carol_updated@example.com"));
    }

    @Test
    @Order(4)
    public void testDeleteUser() {
        User user = new User();
        user.username = "dave";
        user.email = "dave@example.com";
        Long id =
                given()
                        .contentType(ContentType.JSON)
                        .body(user)
                        .when()
                        .post("/users")
                        .then()
                        .extract().jsonPath().getLong("id");

        given()
                .when()
                .delete("/users/" + id)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/users/" + id)
                .then()
                .statusCode(404)
                .body(is(emptyOrNullString())); // Should not be present
    }
}