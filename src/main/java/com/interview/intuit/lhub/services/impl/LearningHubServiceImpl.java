package com.interview.intuit.lhub.services.impl;

import com.interview.intuit.lhub.persistence.LearningHubPersistence;
import com.interview.intuit.lhub.services.LearningHubService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class LearningHubServiceImpl implements LearningHubService {
  private final LearningHubPersistence persistence;

  public LearningHubServiceImpl(LearningHubPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public Future<JsonObject> fetchUserData(String uid) {

    return persistence.fetchUserData(uid);
  }

  @Override
  public Future<JsonObject> rateTopic(String uid, String topicId, int rating) {
    return persistence.rateTopic(uid, topicId, rating);
  }

  @Override
  public Future<JsonObject> searchLPsByTopic(String topic) {
    return persistence.searchLPsByTopic(topic);
  }

  @Override
  public Future<JsonObject> createLearningPath(String uid, String title) {
    return persistence.createLearningPath(uid, title);
  }

  @Override
  public Future<JsonObject> addTopic(String lpId, String title, String url) {
    return persistence.addTopic(lpId, title, url);
  }
}
