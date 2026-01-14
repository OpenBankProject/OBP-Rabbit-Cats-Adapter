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

package com.tesobe.obp.adapter.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import java.time.Instant

/** Base trait for all OBP adapter messages */
sealed trait OBPMessage {
  def messageId: String
  def timestamp: Instant
}

/** Request message from OBP-API to the adapter */
case class OBPRequest(
    messageId: String,
    timestamp: Instant,
    messageFormat: String, // "1.0", "2.0", etc.
    action: String, // e.g., "getBankAccount", "getTransactions", "createTransaction"
    userId: Option[String],
    username: Option[String],
    bankId: Option[String],
    accountId: Option[String],
    payload: io.circe.Json
) extends OBPMessage

/** Response message from the adapter back to OBP-API */
case class OBPResponse(
    messageId: String,
    timestamp: Instant,
    status: String, // "success", "error"
    errorCode: Option[String] = None,
    errorMessage: Option[String] = None,
    data: Option[io.circe.Json] = None
) extends OBPMessage

/** Internal adapter error representation */
case class AdapterError(
    code: String,
    message: String,
    cause: Option[Throwable] = None
)

/** Common OBP data models */

case class BankAccount(
    bankId: String,
    accountId: String,
    accountType: String,
    balance: BigDecimal,
    currency: String,
    name: String,
    label: String,
    number: String,
    owners: List[String],
    iban: Option[String] = None,
    swiftBic: Option[String] = None
)

case class Transaction(
    transactionId: String,
    accountId: String,
    amount: BigDecimal,
    currency: String,
    description: String,
    posted: Instant,
    completed: Instant,
    transactionType: String,
    balanceAfter: BigDecimal,
    counterpartyAccountId: Option[String] = None,
    counterpartyName: Option[String] = None
)

case class Customer(
    customerId: String,
    customerNumber: String,
    legalName: String,
    mobilePhoneNumber: Option[String] = None,
    email: Option[String] = None,
    dateOfBirth: Option[String] = None,
    relationshipStatus: String,
    kycStatus: Boolean
)

case class User(
    userId: String,
    username: String,
    email: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None
)

/** JSON codecs for all models */
object OBPModels {
  
  // Circe codecs for OBP messages
  implicit val obpRequestEncoder: Encoder[OBPRequest] = deriveEncoder[OBPRequest]
  implicit val obpRequestDecoder: Decoder[OBPRequest] = deriveDecoder[OBPRequest]
  
  implicit val obpResponseEncoder: Encoder[OBPResponse] = deriveEncoder[OBPResponse]
  implicit val obpResponseDecoder: Decoder[OBPResponse] = deriveDecoder[OBPResponse]
  
  implicit val adapterErrorEncoder: Encoder[AdapterError] = deriveEncoder[AdapterError]
  implicit val