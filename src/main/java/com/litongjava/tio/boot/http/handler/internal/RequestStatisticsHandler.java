package com.litongjava.tio.boot.http.handler.internal;

import com.litongjava.tio.http.common.HttpRequest;

@FunctionalInterface
public interface RequestStatisticsHandler {

  void count(HttpRequest request);
}
