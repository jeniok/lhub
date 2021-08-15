package com.interview.intuit.lhub.persistence.impl;

import com.interview.intuit.lhub.persistence.LearningHubPersistence;
import com.interview.intuit.lhub.utils.Utils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;


public class LearningHubPersistenceImpl implements LearningHubPersistence {
  MongoClient mongoClient;

  public LearningHubPersistenceImpl(Vertx vertx) {
    mongoClient = MongoClient.createShared(vertx, vertx.getOrCreateContext().config().getJsonObject("mongo.config"));
  }

  public Future<JsonObject> fetchUserData(String uid) {
    Promise<JsonObject> promise = Promise.promise();
    JsonArray pipeline = new JsonArray();
    pipeline
      .add(new JsonObject().put("$match", new JsonObject()
        .put("user_id", uid)))
      .add(new JsonObject().put("$lookup", new JsonObject()
        .put("from", "topics")
        .put("localField", "topics")
        .put("foreignField", "_id")
        .put("as", "topics")));
    JsonObject command = new JsonObject()
      .put("aggregate", "learning_paths")
      .put("pipeline", pipeline)
      .put("cursor", new JsonObject().put("batchSize", 1000));

    Future<JsonObject> getUserLPsTopics = mongoClient.runCommand("aggregate", command);
    Future<JsonObject> getUserRatings = mongoClient.findOne("users", new JsonObject().put("_id", uid), null);
    CompositeFuture.all(getUserLPsTopics, getUserRatings)
      .compose(ar -> {
        JsonObject result = new JsonObject();
        var userLPsTopics = getUserLPsTopics.result().getJsonObject("cursor").getJsonArray("firstBatch");

        var userRatings = getUserRatings.result();
        JsonObject combinedResult = new JsonObject().put("LPsTopics", userLPsTopics)
          .put("userRatings", userRatings);
        return Future.succeededFuture(combinedResult);
      })
      .onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.fail("Not found");
        }
      });
    return promise.future();
  }

  public Future<JsonObject> rateTopic(String uid, String topicId, int rating) {
    /*
      1. updates avg_rating and users_rated in topics
      2. add {topicId:rating} under user_ratings for user=uid
      Average rating formula: new_avg_rating = (avg_rating * users_rated + new_rating) / (users_rated + 1)
     */

    Promise<JsonObject> promise = Promise.promise();
    JsonArray pipeline = new JsonArray();
    pipeline
      .add(new JsonObject().put("$set", new JsonObject()
        .put("avg_rating", new JsonObject()
          .put("$divide", new JsonArray().add(new JsonObject()
              .put("$sum", new JsonArray().add(new JsonObject()
                .put("$multiply", new JsonArray()
                  .add("$avg_rating").add("$users_rated"))).add(rating)))
            .add(new JsonObject()
              .put("$sum", new JsonArray().add("$users_rated").add(1)))))
        .put("$sum", new JsonArray().add("$users_rated").add(1))));

    JsonObject topicQuery = new JsonObject().put("_id", topicId);
    JsonObject userQuery = new JsonObject().put("_id", uid);

    JsonObject updateUserTopicRatings = new JsonObject()
      .put("$push", new JsonObject().put("topics_ratings", new JsonObject().put("$each", new JsonArray().add(new JsonObject()
        .put("topic_id", topicId).put("rating", rating)))));
    mongoClient.updateCollection("users", userQuery, updateUserTopicRatings)
      .compose(ar -> {
        return mongoClient.updateCollectionWithOptions("topics", topicQuery, pipeline, new UpdateOptions(true));
      })
      .onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(new JsonObject().put("succeeded", true));
        } else {
          promise.complete(new JsonObject().put("succeeded", false));
        }
      });
    return promise.future();
  }

  @Override
  public Future<JsonObject> searchLPsByTopic(String topic) {
    // Search learning_paths by topic "partial match"/containing a word with sorting by textScore
    // Uses index: db.topics.createIndex( { title: "text" } )
    Promise<JsonObject> promise = Promise.promise();
    JsonArray pipeline = new JsonArray();
    pipeline.add(new JsonObject().put("$match", new JsonObject()
      .put("$text", new JsonObject().put("$search", topic))));
    pipeline.add(new JsonObject().put("$lookup", new JsonObject()
      .put("from", "learning_paths")
      .put("localField", "_id")
      .put("foreignField", "topics")
      .put("as", "learning_paths")));
    pipeline.add(new JsonObject().put("$project", new JsonObject()
      .put("_id", 1)
      .put("title", 1)
      .put("url", 1)
      .put("learning_paths.title", 1)
      .put("learning_paths._id", 1)));
    pipeline.add(new JsonObject().put("$sort", new JsonObject()
      .put("score", new JsonObject()
        .put("$meta", "textScore"))));
    JsonObject command = new JsonObject()
      .put("aggregate", "topics")
      .put("pipeline", pipeline)
      .put("cursor", new JsonObject().put("batchSize", 1000));
    mongoClient.runCommand("aggregate", command)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          if (ar.result() != null) {
            promise.complete(new JsonObject().put("lps", ar.result().getJsonObject("cursor").getJsonArray("firstBatch")));
          } else {
            promise.fail("Error occurred while getting LPs by topic");
          }
        }
      });
    return promise.future();
  }

  @Override
  public Future<JsonObject> createLearningPath(String uid, String title) {
    //Adds new learning path, returns id
    Promise<JsonObject> promise = Promise.promise();

    JsonObject lp = new JsonObject().put("title", title).put("user_id", uid);
    mongoClient.insert("learning_paths", lp)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          if (ar.result() != null) {
            promise.complete(new JsonObject().put("id", ar.result()));
          } else {
            promise.fail("Error occurred while getting LPs by topic");
          }
        }
      });
    return promise.future();
  }

  @Override
  public Future<JsonObject> addTopic(String lpId, String title, String url) {
    /*
    Returns topicId, if topic already exists, returns existing topicId.
            1. if topic doesn't exist => adds topic to topics, otherwise returns existing topic
            2. if topic doesn't exist => adds topic_id under learning path
            3. returns topic_id
     */

    Promise<JsonObject> promise = Promise.promise();
    String normalizedUrl = Utils.normalizeUrl(url);

    mongoClient.findOne("topics", new JsonObject().put("url", normalizedUrl), null)
      .compose(ar -> {
        if (ar != null) {
          return Future.succeededFuture(ar.getString("_id"));
        } else {
          JsonObject topic = new JsonObject().put("title", title).put("url", normalizedUrl).put("avg_rating", 0).put("users_rated", 0);
          return mongoClient.insert("topics", topic);
        }
      })
      .compose(ar -> {
        String topicId = ar;
        JsonObject query = new JsonObject().put("_id", Integer.valueOf(lpId));
        JsonObject update = new JsonObject().put("$push", new JsonObject()
          .put("topics", new JsonObject()
            .put("$each", new JsonArray().add(topicId))));

        mongoClient.updateCollection("learning_paths", query, update);
        return Future.succeededFuture(topicId);
      })
      .onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(new JsonObject().put("id", ar.result()));
        } else {
          promise.fail("Error occurred while getting LPs by topic");
        }
      });
    return promise.future();
  }
}


