package com.interview.intuit.lhub.persistence;

import com.interview.intuit.lhub.persistence.impl.LearningHubPersistenceImpl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface LearningHubPersistence {
  static LearningHubPersistence create(Vertx vertx) {
    return new LearningHubPersistenceImpl(vertx);
  }

  Future<JsonObject> fetchUserData(String uid);
  Future<JsonObject> rateTopic(String uid, String topicId, int rating);
  Future<JsonObject> searchLPsByTopic(String topic);
  Future<JsonObject> createLearningPath(String uid, String title);
  Future<JsonObject> addTopic(String lpId, String title, String url);
}
