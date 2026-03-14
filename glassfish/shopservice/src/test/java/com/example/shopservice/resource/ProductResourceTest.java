package com.example.shopservice.resource;

import com.example.shopservice.DeploymentFactory;
import io.restassured.http.ContentType;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.net.URL;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductResourceTest {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return DeploymentFactory.createDeployment();
    }

    @ArquillianResource
    private URL base;

    @Test
    @Order(1)
    public void testCreateProduct() {
        given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Widget\",\"price\":19.99}")
                .when()
                .post("products")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Widget"))
                .body("price", equalTo(19.99f));
    }

    @Test
    @Order(2)
    public void testGetProduct() {
        Long id = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Gadget\",\"price\":10.50}")
                .when()
                .post("products")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .get("products/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Gadget"))
                .body("price", equalTo(10.50f));
    }

    @Test
    @Order(3)
    public void testUpdateProduct() {
        Long id = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Thing\",\"price\":5.00}")
                .when()
                .post("products")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Thing Updated\",\"price\":7.50}")
                .when()
                .put("products/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Thing Updated"))
                .body("price", equalTo(7.50f));
    }

    @Test
    @Order(4)
    public void testDeleteProduct() {
        Long id = given()
                .baseUri(base.toExternalForm())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"ToDelete\",\"price\":" + new BigDecimal("1.00") + "}")
                .when()
                .post("products")
                .then()
                .extract().jsonPath().getLong("id");

        given()
                .baseUri(base.toExternalForm())
                .when()
                .delete("products/" + id)
                .then()
                .statusCode(204);

        given()
                .baseUri(base.toExternalForm())
                .when()
                .get("products/" + id)
                .then()
                .statusCode(404);
    }
}
