/*
 * Copyright (c) 2025 TESOBE
 *
 * This file is part of OBP-Rabbit-Cats-Adapter.
 *
 * OBP-Rabbit-Cats-Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License, Version 2.0.
 *
 * OBP-Rabbit-Cats-Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License, Version 2.0
 * along with OBP-Rabbit-Cats-Adapter. If not, see <http://www.apache.org/licenses/>.
 */

package com.tesobe.obp.adapter.config

import cats.effect.IO
import scala.concurrent.duration._

/** HTTP server configuration for discovery page */
case class HttpConfig(
    host: String,
    port: Int,
    enabled: Boolean,
    apiExplorerUrl: String,
    obpApiUrl: String
)

/** RabbitMQ connection configuration */
case class RabbitMQConfig(
    host: String,
    port: Int,
    virtualHost: String,
    username: String,
    password: String,
    connectionTimeout: FiniteDuration,
    requestedHeartbeat: FiniteDuration,
    automaticRecovery: Boolean
)

/** Queue configuration for request and response queues */
case class QueueConfig(
    requestQueue: String,
    responseQueue: String,
    prefetchCount: Int,
    durable: Boolean,
    autoDelete: Boolean
)

case class RedisConfig(
    host: String,
    port: Int,
    enabled: Boolean
)

/** gRPC server configuration */
case class GrpcConfig(
    port: Int,
    enabled: Boolean
)

/** Complete adapter configuration */
case class AdapterConfig(
    http: HttpConfig,
    rabbitmq: RabbitMQConfig,
    queue: QueueConfig,
    redis: RedisConfig,
    grpc: GrpcConfig,
    logLevel: String,
    enableMetrics: Boolean
)

object Config {

  /** Read a config value: env var takes priority, then system property, then default */
  private def env(key: String, default: String): String =
    sys.env.getOrElse(key, sys.props.getOrElse(key, default))

  /** Load configuration from environment variables */
  def load: IO[AdapterConfig] = IO {
    val httpConfig = HttpConfig(
      host = env("HTTP_HOST", "0.0.0.0"),
      port = env("HTTP_PORT", "52345").toInt,
      enabled = env("HTTP_ENABLED", "true").toBoolean,
      apiExplorerUrl =
        env("API_EXPLORER_URL", "http://localhost:5173"),
      obpApiUrl = env("OBP_API_URL", "http://localhost:8080")
    )

    val rabbitmqConfig = RabbitMQConfig(
      host = env("RABBITMQ_HOST", "localhost"),
      port = env("RABBITMQ_PORT", "5672").toInt,
      virtualHost = env("RABBITMQ_VIRTUAL_HOST", "/"),
      username = env("RABBITMQ_USERNAME", "guest"),
      password = env("RABBITMQ_PASSWORD", "guest"),
      connectionTimeout =
        env("RABBITMQ_CONNECTION_TIMEOUT", "30").toInt.seconds,
      requestedHeartbeat =
        env("RABBITMQ_HEARTBEAT", "60").toInt.seconds,
      automaticRecovery =
        env("RABBITMQ_AUTOMATIC_RECOVERY", "true").toBoolean
    )

    val queueConfig = QueueConfig(
      requestQueue = env("RABBITMQ_REQUEST_QUEUE", "obp_rpc_queue"),
      responseQueue =
        env("RABBITMQ_RESPONSE_QUEUE", "obp_reply_queue"),
      prefetchCount = env("RABBITMQ_PREFETCH_COUNT", "10").toInt,
      durable = env("RABBITMQ_QUEUE_DURABLE", "true").toBoolean,
      autoDelete =
        env("RABBITMQ_QUEUE_AUTO_DELETE", "false").toBoolean
    )

    val redisConfig = RedisConfig(
      host = env("REDIS_HOST", "localhost"),
      port = env("REDIS_PORT", "6379").toInt,
      enabled = env("REDIS_ENABLED", "true").toBoolean
    )

    val grpcConfig = GrpcConfig(
      port = env("GRPC_PORT", "50051").toInt,
      enabled = env("GRPC_ENABLED", "false").toBoolean
    )

    AdapterConfig(
      http = httpConfig,
      rabbitmq = rabbitmqConfig,
      queue = queueConfig,
      redis = redisConfig,
      grpc = grpcConfig,
      logLevel = env("LOG_LEVEL", "INFO"),
      enableMetrics = env("ENABLE_METRICS", "true").toBoolean
    )
  }

  /** Validate configuration */
  def validate(config: AdapterConfig): IO[Unit] = IO {
    require(
      config.http.port > 0 && config.http.port < 65536,
      "HTTP port must be between 1 and 65535"
    )
    require(config.rabbitmq.host.nonEmpty, "RabbitMQ host must not be empty")
    require(
      config.rabbitmq.port > 0 && config.rabbitmq.port < 65536,
      "RabbitMQ port must be between 1 and 65535"
    )
    require(
      config.queue.requestQueue.nonEmpty,
      "Request queue name must not be empty"
    )
    require(
      config.queue.responseQueue.nonEmpty,
      "Response queue name must not be empty"
    )
    if (config.grpc.enabled) {
      require(
        config.grpc.port > 0 && config.grpc.port < 65536,
        "gRPC port must be between 1 and 65535"
      )
    }
  }
}
