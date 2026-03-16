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
public class ReviewResourceTest {

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
                .body("{\"username\":\"reviewuser_" + unique + "\","
                        + "\"email\":\"reviewuser_" + unique + "@example.com\"}")
                .when()
                .post("users")
                .then()
                .extract().jsonPath().getLong("id");
    }

    private Long createProduct() {
        String unique = UUID.randomUUID().toString();
        return given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"reviewprod_" + unique + "\",\"price\":8.90}")
                .when()
                .post("products")
                .then()
                .extract().jsonPath().getLong("id");
    }

    @Test
    @Order(1)
    public void testCreateReview() {
        Long userId = createUser();
        Long productId = createProduct();

        given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"user\":{\"id\":" + userId + "},"
                        + "\"product\":{\"id\":" + productId + "},"
                        + "\"rating\":5,\"comment\":\"Excellent!\"}")
                .when()
                .post("reviews")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("user.id", equalTo(userId.intValue()))
                .body("product.id", equalTo(productId.intValue()))
                .body("rating", equalTo(5))
                .body("comment", equalTo("Excellent!"));
    }

    @Test
    @Order(2)
    public void testGetReview() {
        Long userId = createUser();
        Long productId = createProduct();

        Long reviewId = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"user\":{\"id\":" + userId + "},"
                        + "\"product\":{\"id\":" + productId + "},"
                        + "\"rating\":4,\"comment\":\"Good!\"}")
                .when()
                .post("reviews")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .get("reviews/" + reviewId)
                .then()
                .statusCode(200)
                .body("rating", equalTo(4))
                .body("comment", equalTo("Good!"));
    }

    @Test
    @Order(3)
    public void testDeleteReview() {
        Long userId = createUser();
        Long productId = createProduct();

        Long reviewId = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"user\":{\"id\":" + userId + "},"
                        + "\"product\":{\"id\":" + productId + "},"
                        + "\"rating\":2,\"comment\":\"Not great.\"}")
                .when()
                .post("reviews")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .delete("reviews/" + reviewId)
                .then()
                .statusCode(204);
    }
}
