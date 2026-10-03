package br.com.fintech.wallet;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("wallet")
                    .withUsername("wallet")
                    .withPassword("wallet");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    void transfersPixIdempotentlyAndShowsBothSidesInStatement() {
        int senderId = register("Ana Silva", "ana@example.com", "529.982.247-25");
        int recipientId = register("Bruno Lima", "bruno@example.com", "111.444.777-35");

        String token = login("ana@example.com");
        given()
                .auth().oauth2(token)
                .header("Idempotency-Key", "deposit-order-100")
                .contentType("application/json")
                .body("""
                        {"amount":100.00}
                        """)
                .when()
                .post("/api/v1/accounts/%s/deposit".formatted(senderId))
                .then()
                .statusCode(201)
                .body("type", equalTo("DEPOSIT"));

        given()
                .auth().oauth2(token)
                .header("Idempotency-Key", "deposit-order-100")
                .contentType("application/json")
                .body("""
                        {"amount":100.00}
                        """)
                .when()
                .post("/api/v1/accounts/%s/deposit".formatted(senderId))
                .then()
                .statusCode(201);

        String transferBody = """
                {
                  "from": %s,
                  "to": %s,
                  "amount": 25.50,
                  "idempotencyKey": "pix-order-100",
                  "description": "Almoço"
                }
                """.formatted(senderId, recipientId);

        int transferId = given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body(transferBody)
                .when()
                .post("/api/v1/transfers")
                .then()
                .statusCode(201)
                .body("amount", equalTo(25.5f))
                .body("type", equalTo("TRANSFER"))
                .extract()
                .path("id");

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body(transferBody)
                .when()
                .post("/api/v1/transfers")
                .then()
                .statusCode(201)
                .body("id", equalTo(transferId));

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {"from":%s,"to":%s,"amount":26.00,"idempotencyKey":"pix-order-100","description":"Almoço"}
                        """.formatted(senderId, recipientId))
                .when()
                .post("/api/v1/transfers")
                .then()
                .statusCode(409);

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/v1/wallet")
                .then()
                .statusCode(200)
                .body("balance", equalTo(74.5f));

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/v1/transfers?page=0&size=20")
                .then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("content[0].direction", equalTo("OUTGOING"))
                .body("totalElements", equalTo(2));

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/v1/accounts/%s/statement".formatted(senderId))
                .then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("content[0].type", equalTo("TRANSFER"));

        String recipientToken = login("bruno@example.com");
        given()
                .auth().oauth2(recipientToken)
                .when()
                .get("/api/v1/transfers")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].direction", equalTo("INCOMING"))
                .body("content[0].counterpartyKey", equalTo("ana@example.com"));
    }

    @Test
    void rejectsUnauthorizedRequestsAndInsufficientFunds() {
        given().when().get("/api/v1/wallet").then().statusCode(401);
        int senderId = register("Carla Souza", "carla@example.com", "935.411.347-80");
        int recipientId = register("Diego Reis", "diego@example.com", "168.995.350-09");
        String token = login("carla@example.com");

        given()
                .auth().oauth2(token)
                .header("Idempotency-Key", "pix-without-balance")
                .contentType("application/json")
                .body("""
                        {"from":%s,"to":%s,"amount":10.00,"idempotencyKey":"pix-without-balance"}
                        """.formatted(senderId, recipientId))
                .when()
                .post("/api/v1/transfers")
                .then()
                .statusCode(422)
                .body("message", notNullValue());
    }

    @Test
    void rejectsAccountsWithDuplicateCpf() {
        register("Eva Costa", "eva@example.com", "390.533.447-05");

        given()
                .contentType("application/json")
                .body("""
                        {"name":"Fabio Costa","email":"fabio@example.com",
                         "document":"39053344705","password":"senha-segura"}
                        """)
                .when()
                .post("/api/v1/accounts")
                .then()
                .statusCode(409);
    }

    private int register(String name, String email, String document) {
        return given()
                .contentType("application/json")
                .body("""
                        {"name":"%s","email":"%s","document":"%s","password":"senha-segura"}
                        """.formatted(name, email, document))
                .when()
                .post("/api/v1/accounts")
                .then()
                .statusCode(201)
                .body("balance", equalTo(0.0f))
                .extract()
                .path("id");
    }

    private String login(String email) {
        return given()
                .contentType("application/json")
                .body("""
                        {"email":"%s","password":"senha-segura"}
                        """.formatted(email))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }
}
