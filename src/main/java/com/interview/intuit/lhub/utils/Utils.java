package com.interview.intuit.lhub.utils;

import io.vertx.ext.web.RoutingContext;

public class Utils {
  public static String extractUserId(RoutingContext routingContext) {
    var user = routingContext.user();
    String uid = user.get("sub");
    return uid;
  }

  public static String normalizeUrl(String url) {
    // should strip http/https, should strip ending section
    return url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", "");
  }
}
