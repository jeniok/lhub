package com.interview.intuit.lhub.services;

import com.interview.intuit.lhub.persistence.LearningHubPersistence;
import com.interview.intuit.lhub.services.impl.LearningHubServiceImpl;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface LearningHubService {
  static LearningHubService create(LearningHubPersistence persistence) {
    return new LearningHubServiceImpl(persistence);
  }

  Future<JsonObject> fetchUserData(String uid);

  Future<JsonObject> rateTopic(String uid, String topicId, int rating);

  Future<JsonObject> searchLPsByTopic(String topic);

  Future<JsonObject> createLearningPath(String uid, String title);

  Future<JsonObject> addTopic(String lpId, String title, String url);
}
