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

package com.tesobe.obp.adapter.telemetry

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

/**
 * Telemetry interface for observability and monitoring.
 * 
 * This trait provides a clean separation between business logic and observability concerns.
 * Implementations can vary (console logging, Prometheus, Datadog, etc.) without affecting
 * the core adapter logic.
 * 
 * All methods return IO[Unit] to allow for async logging/metrics collection.
 */
trait Telemetry {

  // ==================== MESSAGE PROCESSING METRICS ====================

  /**
   * Record when a message is received from RabbitMQ
   */
  def recordMessageReceived(
    messageType: String,
    correlationId: String,
    queueName: String
  ): IO[Unit]

  /**
   * Record when a message is successfully processed
   */
  def recordMessageProcessed(
    messageType: String,
    correlationId: String,
    duration: FiniteDuration
  ): IO[Unit]

  /**
   * Record when message processing fails
   */
  def recordMessageFailed(
    messageType: String,
    correlationId: String,
    errorCode: String,
    errorMessage: String,
    duration: FiniteDuration
  ): IO[Unit]

  /**
   * Record when a response is sent back to RabbitMQ
   */
  def recordResponseSent(
    messageType: String,
    correlationId: String,
    success: Boolean
  ): IO[Unit]

  // ==================== CBS OPERATION METRICS ====================

  /**
   * Record CBS operation start
   */
  def recordCBSOperationStart(
    operation: String,
    correlationId: String
  ): IO[Unit]

  /**
   * Record successful CBS operation
   */
  def recordCBSOperationSuccess(
    operation: String,
    correlationId: String,
    duration: FiniteDuration
  ): IO[Unit]

  /**
   * Record failed CBS operation
   */
  def recordCBSOperationFailure(
    operation: String,
    correlationId: String,
    errorCode: String,
    errorMessage: String,
    duration: FiniteDuration
  ): IO[Unit]

  /**
   * Record CBS operation retry
   */
  def recordCBSOperationRetry(
    operation: String,
    correlationId: String,
    attemptNumber: Int,
    reason: String
  ): IO[Unit]

  // ==================== RABBITMQ METRICS ====================

  /**
   * Record RabbitMQ connection established
   */
  def recordRabbitMQConnected(host: String, port: Int): IO[Unit]

  /**
   * Record RabbitMQ connection lost
   */
  def recordRabbitMQDisconnected(reason: String): IO[Unit]

  /**
   * Record RabbitMQ connection error
   */
  def recordRabbitMQConnectionError(errorMessage: String): IO[Unit]

  /**
   * Record queue consumption started
   */
  def recordQueueConsumptionStarted(queueName: String): IO[Unit]

  /**
   * Record queue consumption stopped
   */
  def recordQueueConsumptionStopped(queueName: String, reason: String): IO[Unit]

  // ==================== PERFORMANCE METRICS ====================

  /**
   * Record message queue depth
   */
  def recordQueueDepth(queueName: String, depth: Long): IO[Unit]

  /**
   * Record message processing rate
   */
  def recordProcessingRate(messagesPerSecond: Double): IO[Unit]

  /**
   * Record memory usage
   */
  def recordMemoryUsage(usedMB: Long, totalMB: Long): IO[Unit]

  // ==================== BUSINESS METRICS ====================

  /**
   * Record successful payment
   */
  def recordPaymentSuccess(
    bankId: String,
    amount: BigDecimal,
    currency: String,
    correlationId: String
  ): IO[Unit]

  /**
   * Record failed payment
   */
  def recordPaymentFailure(
    bankId: String,
    amount: BigDecimal,
    currency: String,
    errorCode: String,
    correlationId: String
  ): IO[Unit]

  /**
   * Record account creation
   */
  def recordAccountCreated(bankId: String, accountType: String, correlationId: String): IO[Unit]

  /**
   * Record customer creation
   */
  def recordCustomerCreated(bankId: String, correlationId: String): IO[Unit]

  // ==================== ERROR TRACKING ====================

  /**
   * Record general error with context
   */
  def recordError(
    category: String,
    errorCode: String,
    errorMessage: String,
    correlationId: Option[String] = None,
    additionalContext: Map[String, String] = Map.empty
  ): IO[Unit]

  /**
   * Record warning
   */
  def recordWarning(
    category: String,
    message: String,
    correlationId: Option[String] = None
  ): IO[Unit]

  // ==================== TRACING ====================

  /**
   * Start a trace span
   * @return Span ID to be used for endSpan
   */
  def startSpan(
    operationName: String,
    correlationId: String,
    attributes: Map[String, String] = Map.empty
  ): IO[String]

  /**
   * End a trace span
   */
  def endSpan(spanId: String, success: Boolean): IO[Unit]

  /**
   * Add event to current span
   */
  def addSpanEvent(
    spanId: String,
    eventName: String,
    attributes: Map[String, String] = Map.empty
  ): IO[Unit]

  // ==================== LOGGING ====================

  /**
   * Log debug message
   */
  def debug(message: String, correlationId: Option[String] = None): IO[Unit]

  /**
   * Log info message
   */
  def info(message: String, correlationId: Option[String] = None): IO[Unit]

  /**
   * Log warning message
   */
  def warn(message: String, correlationId: Option[String] = None): IO[Unit]

  /**
   * Log error message
   */
  def error(message: String, throwable: Option[Throwable] = None, correlationId: Option[String] = None): IO[Unit]

  // ==================== HEALTH CHECKS ====================

  /**
   * Record health check result
   */
  def recordHealthCheck(
    component: String,
    healthy: Boolean,
    message: String
  ): IO[Unit]
}

/**
 * No-op telemetry implementation for testing or when telemetry is disabled
 */
object NoOpTelemetry extends Telemetry {
  override def recordMessageReceived(messageType: String, correlationId: String, queueName: String): IO[Unit] = IO.unit
  override def recordMessageProcessed(messageType: String, correlationId: String, duration: FiniteDuration): IO[Unit] = IO.unit
  override def recordMessageFailed(messageType: String, correlationId: String, errorCode: String, errorMessage: String, duration: FiniteDuration): IO[Unit] = IO.unit
  override def recordResponseSent(messageType: String, correlationId: String, success: Boolean): IO[Unit] = IO.unit
  override def recordCBSOperationStart(operation: String, correlationId: String): IO[Unit] = IO.unit
  override def recordCBSOperationSuccess(operation: String, correlationId: String, duration: FiniteDuration): IO[Unit] = IO.unit
  override def recordCBSOperationFailure(operation: String, correlationId: String, errorCode: String, errorMessage: String, duration: FiniteDuration): IO[Unit] = IO.unit
  override def recordCBSOperationRetry(operation: String, correlationId: String, attemptNumber: Int, reason: String): IO[Unit] = IO.unit
  override def recordRabbitMQConnected(host: String, port: Int): IO[Unit] = IO.unit
  override def recordRabbitMQDisconnected(reason: String): IO[Unit] = IO.unit
  override def recordRabbitMQConnectionError(errorMessage: String): IO[Unit] = IO.unit
  override def recordQueueConsumptionStarted(queueName: String): IO[Unit] = IO.unit
  override def recordQueueConsumptionStopped(queueName: String, reason: String): IO[Unit] = IO.unit
  override def recordQueueDepth(queueName: String, depth: Long): IO[Unit] = IO.unit
  override def recordProcessingRate(messagesPerSecond: Double): IO[Unit] = IO.unit
  override def recordMemoryUsage(usedMB: Long, totalMB: Long): IO[Unit] = IO.unit
  override def recordPaymentSuccess(bankId: String, amount: BigDecimal, currency: String, correlationId: String): IO[Unit] = IO.unit
  override def recordPaymentFailure(bankId: String, amount: BigDecimal, currency: String, errorCode: String, correlationId: String): IO[Unit] = IO.unit
  override def recordAccountCreated(bankId: String, accountType: String, correlationId: String): IO[Unit] = IO.unit
  override def recordCustomerCreated(bankId: String, correlationId: String): IO[Unit] = IO.unit
  override def recordError(category: String, errorCode: String, errorMessage: String, correlationId: Option[String], additionalContext: Map[String, String]): IO[Unit] = IO.unit
  override def recordWarning(category: String, message: String, correlationId: Option[String]): IO[Unit] = IO.unit
  override def startSpan(operationName: String, correlationId: String, attributes: Map[String, String]): IO[String] = IO.pure("noop-span")
  override def endSpan(spanId: String, success: Boolean): IO[Unit] = IO.unit
  override def addSpanEvent(spanId: String, eventName: String, attributes: Map[String, String]): IO[Unit] = IO.unit
  override def debug(message: String, correlationId: Option[String]): IO[Unit] = IO.unit
  override def info(message: String, correlationId: Option[String]): IO[Unit] = IO.unit
  override def warn(message: String, correlationId: Option[String]): IO[Unit] = IO.unit
  override def error(message: String, throwable: Option[Throwable], correlationId: Option[String]): IO[Unit] = IO.unit
  override def recordHealthCheck(component: String, healthy: Boolean, message: String): IO[Unit] = IO.unit
}

/**
 * Console-based telemetry implementation for development/debugging
 */
class ConsoleTelemetry extends Telemetry {
  private def log(level: String, message: String, correlationId: Option[String] = None): IO[Unit] = IO {
    val cidStr = correlationId.map(cid => s"[CID: $cid]").getOrElse("")
    println(s"[$level]$cidStr $message")
  }

  override def recordMessageReceived(messageType: String, correlationId: String, queueName: String): IO[Unit] =
    log("INFO", s"Message received: type=$messageType queue=$queueName", Some(correlationId))

  override def recordMessageProcessed(messageType: String, correlationId: String, duration: FiniteDuration): IO[Unit] =
    log("INFO", s"Message processed: type=$messageType duration=${duration.toMillis}ms", Some(correlationId))

  override def recordMessageFailed(messageType: String, correlationId: String, errorCode: String, errorMessage: String, duration: FiniteDuration): IO[Unit] =
    log("ERROR", s"Message failed: type=$messageType error=$errorCode message=$errorMessage duration=${duration.toMillis}ms", Some(correlationId))

  override def recordResponseSent(messageType: String, correlationId: String, success: Boolean): IO[Unit] =
    log("INFO", s"Response sent: type=$messageType success=$success", Some(correlationId))

  override def recordCBSOperationStart(operation: String, correlationId: String): IO[Unit] =
    log("DEBUG", s"CBS operation started: $operation", Some(correlationId))

  override def recordCBSOperationSuccess(operation: String, correlationId: String, duration: FiniteDuration): IO[Unit] =
    log("INFO", s"CBS operation success: $operation duration=${duration.toMillis}ms", Some(correlationId))

  override def recordCBSOperationFailure(operation: String, correlationId: String, errorCode: String, errorMessage: String, duration: FiniteDuration): IO[Unit] =
    log("ERROR", s"CBS operation failed: $operation error=$errorCode message=$errorMessage duration=${duration.toMillis}ms", Some(correlationId))

  override def recordCBSOperationRetry(operation: String, correlationId: String, attemptNumber: Int, reason: String): IO[Unit] =
    log("WARN", s"CBS operation retry: $operation attempt=$attemptNumber reason=$reason", Some(correlationId))

  override def recordRabbitMQConnected(host: String, port: Int): IO[Unit] =
    log("INFO", s"RabbitMQ connected: $host:$port")

  override def recordRabbitMQDisconnected(reason: String): IO[Unit] =
    log("WARN", s"RabbitMQ disconnected: $reason")

  override def recordRabbitMQConnectionError(errorMessage: String): IO[Unit] =
    log("ERROR", s"RabbitMQ connection error: $errorMessage")

  override def recordQueueConsumptionStarted(queueName: String): IO[Unit] =
    log("INFO", s"Queue consumption started: $queueName")

  override def recordQueueConsumptionStopped(queueName: String, reason: String): IO[Unit] =
    log("INFO", s"Queue consumption stopped: $queueName reason=$reason")

  override def recordQueueDepth(queueName: String, depth: Long): IO[Unit] =
    log("DEBUG", s"Queue depth: $queueName depth=$depth")

  override def recordProcessingRate(messagesPerSecond: Double): IO[Unit] =
    log("DEBUG", s"Processing rate: ${messagesPerSecond} msg/s")

  override def recordMemoryUsage(usedMB: Long, totalMB: Long): IO[Unit] =
    log("DEBUG", s"Memory usage: ${usedMB}MB / ${totalMB}MB")

  override def recordPaymentSuccess(bankId: String, amount: BigDecimal, currency: String, correlationId: String): IO[Unit] =
    log("INFO", s"Payment success: bank=$bankId amount=$amount$currency", Some(correlationId))

  override def recordPaymentFailure(bankId: String, amount: BigDecimal, currency: String, errorCode: String, correlationId: String): IO[Unit] =
    log("ERROR", s"Payment failure: bank=$bankId amount=$amount$currency error=$errorCode", Some(correlationId))

  override def recordAccountCreated(bankId: String, accountType: String, correlationId: String): IO[Unit] =
    log("INFO", s"Account created: bank=$bankId type=$accountType", Some(correlationId))

  override def recordCustomerCreated(bankId: String, correlationId: String): IO[Unit] =
    log("INFO", s"Customer created: bank=$bankId", Some(correlationId))

  override def recordError(category: String, errorCode: String, errorMessage: String, correlationId: Option[String], additionalContext: Map[String, String]): IO[Unit] =
    log("ERROR", s"Error: category=$category code=$errorCode message=$errorMessage context=$additionalContext", correlationId)

  override def recordWarning(category: String, message: String, correlationId: Option[String]): IO[Unit] =
    log("WARN", s"Warning: category=$category message=$message", correlationId)

  override def startSpan(operationName: String, correlationId: String, attributes: Map[String, String]): IO[String] =
    log("DEBUG", s"Span started: $operationName attributes=$attributes", Some(correlationId)).as(java.util.UUID.randomUUID().toString)

  override def endSpan(spanId: String, success: Boolean): IO[Unit] =
    log("DEBUG", s"Span ended: id=$spanId success=$success")

  override def addSpanEvent(spanId: String, eventName: String, attributes: Map[String, String]): IO[Unit] =
    log("DEBUG", s"Span event: span=$spanId event=$eventName attributes=$attributes")

  override def debug(message: String, correlationId: Option[String]): IO[Unit] =
    log("DEBUG", message, correlationId)

  override def info(message: String, correlationId: Option[String]): IO[Unit] =
    log("INFO", message, correlationId)

  override def warn(message: String, correlationId: Option[String]): IO[Unit] =
    log("WARN", message, correlationId)

  override def error(message: String, throwable: Option[Throwable], correlationId: Option[String]): IO[Unit] =
    log("ERROR", message + throwable.map(t => s" - ${t.getMessage}").getOrElse(""), correlationId)

  override def recordHealthCheck(component: String, healthy: Boolean, message: String): IO[Unit] =
    log("INFO", s"Health check: component=$component healthy=$healthy message=$message")
}