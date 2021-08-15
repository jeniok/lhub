package com.interview.intuit.lhub;

import com.interview.intuit.lhub.persistence.LearningHubPersistence;
import com.interview.intuit.lhub.services.LearningHubService;
import com.interview.intuit.lhub.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class LearningHubVerticle extends AbstractVerticle {
  private LearningHubService service;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    this.service = LearningHubService.create(LearningHubPersistence.create(vertx));

    Router router = Router.router(vertx);
    initAuth(router)
      .onComplete(ar -> {
        initRoutes(router, ar.result());
        int port = config().getInteger("http.port");
        vertx.createHttpServer().requestHandler(router).listen(port, http -> {
          if (http.succeeded()) {
            startPromise.complete();
            System.out.println(String.format("LearningHubVerticle started, connect to http://localhost:%d", port));
          } else {
            startPromise.fail(http.cause());
          }
        });
      });
  }


  private Future<OAuth2AuthHandler> initAuth(Router router) {
    Promise<OAuth2AuthHandler> promise = Promise.promise();
    // Store session information on the server side
    // When run in cluster, use ClusteredSessionStore instead of LocalSessionStore
    SessionStore sessionStore = LocalSessionStore.create(vertx);
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);
    router.route().handler(sessionHandler);

    // Expose form parameters in request
    router.route().handler(BodyHandler.create());

    // CSRF handler setup required for logout form
    router.route().handler(CSRFHandler.create(vertx, config().getString("csrfSecret")));

    String hostname = config().getString("http.host");
    int port = config().getInteger("http.port");
    String baseUrl = String.format("http://%s:%d", hostname, port);
    String oauthCallbackPath = "/callback";

// Our app is registered as a confidential OpenID Connect client with Authorization Code Flow in Keycloak,
    // thus we need to configure client_id and client_secret
    OAuth2Options clientOptions = new OAuth2Options()
      .setFlow(OAuth2FlowType.AUTH_CODE)
      .setSite(System.getProperty("oauth2.issuer", "http://localhost:8080/auth/realms/vertx"))
      .setClientId(System.getProperty("oauth2.client_id", "demo-client"))
      .setClientSecret(System.getProperty("oauth2.client_secret", "ff160ac9-6989-4940-8013-883e81b39702"));

    // We use Keycloaks OpenID Connect discovery endpoint to infer the Oauth2 / OpenID Connect endpoint URLs
    KeycloakAuth.discover(vertx, clientOptions)
      .onComplete(ar -> {
        OAuth2Auth oauth2Auth = ar.result();

        if (oauth2Auth == null) {
          throw new RuntimeException("Could not configure Keycloak integration via OpenID Connect Discovery Endpoint. Is Keycloak running?");
        }

        OAuth2AuthHandler oauth2 = OAuth2AuthHandler.create(vertx, oauth2Auth, baseUrl + oauthCallbackPath)
          .setupCallback(router.get(oauthCallbackPath))
          .withScope("openid");
        promise.complete(oauth2);
      });
    return promise.future();
  }

  private void initRoutes(Router router, OAuth2AuthHandler oauth2) {
    // protect all resources beneath /lhub/* with oauth2 handler

    router.route("/lhub/*").handler(oauth2);
    router.get("/lhub").handler(this::handleIndex);
    router.post("/lhub/api/topics/rate/:tid").handler(this::rateTopic);
    router.get("/lhub/api/users/:uid").handler(this::fetchUserData);
    router.get("/lhub/api/lps/search/:topic").handler(this::searchLPsByTopic);
    router.post("/lhub/api/lps/create").handler(this::createLearningPath);
    router.post("/lhub/api/topics/add").handler(this::addTopic);
  }

  private void searchLPsByTopic(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    String topicId = routingContext.request().getParam("topic");
    service.searchLPsByTopic(topicId)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          response.putHeader("content-type", "application/json").end(ar.result().encodePrettily());
        } else {
          sendError(400, response);
        }
      });
  }

  private void handleIndex(RoutingContext routingContext) {
    var user = routingContext.user();
    String uid = user.get("sub"); //27b3fac0-9aec-4421-81cf-d4b02428f901
    routingContext.response().end(String.format("Access allowed, user %s authenticated", uid));
  }

  private void addTopic(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    JsonObject body = routingContext.getBodyAsJson();
    String lpId = body.getString("lpId"); //learning path ID
    String title = body.getString("title");
    String url = body.getString("url");
    service.addTopic(lpId, title, url)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          response.putHeader("content-type", "application/json").end(ar.result().encodePrettily());
        } else {
          sendError(400, response);
        }
      });
  }

  private void createLearningPath(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    String uid = Utils.extractUserId(routingContext);
    JsonObject body = routingContext.getBodyAsJson();
    String title = body.getString("title");
    service.createLearningPath(uid, title)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          response.putHeader("content-type", "application/json").end(ar.result().encodePrettily());
        } else {
          sendError(400, response);
        }
      });
  }

  private void rateTopic(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    String uid = Utils.extractUserId(routingContext);
    JsonObject body = routingContext.getBodyAsJson();
    String topicId = routingContext.request().getParam("tid");
    int rating = Integer.valueOf(body.getString("rating"));
    service.rateTopic(uid, topicId, rating)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          response.putHeader("content-type", "application/json").end(ar.result().encodePrettily());
        } else {
          sendError(400, response);
        }
      });
  }

  private void fetchUserData(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    String uid = Utils.extractUserId(routingContext);
    service.fetchUserData(uid)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          response.putHeader("content-type", "application/json").end(ar.result().encodePrettily());
        } else {
          sendError(400, response);
        }
      });
  }

  private void sendError(int statusCode, HttpServerResponse response) {
    response.setStatusCode(statusCode).end();
  }
}
