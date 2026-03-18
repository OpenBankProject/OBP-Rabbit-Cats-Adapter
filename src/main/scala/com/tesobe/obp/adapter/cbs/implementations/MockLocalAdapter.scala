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

package com.tesobe.obp.adapter.cbs.implementations

import cats.effect.IO
import com.tesobe.obp.adapter.interfaces._
import com.tesobe.obp.adapter.models._
import com.tesobe.obp.adapter.telemetry.Telemetry
import io.circe._
import scala.collection.mutable

/**
 * Mock Local Adapter for testing and development.
 *
 * This demonstrates the JSON-based approach where:
 * 1. We receive JSON data from OBP message
 * 2. Extract fields we need
 * 3. Call CBS (mocked here)
 * 4. Return JSON matching OBP message docs format
 *
 * Banks should implement similar logic but calling their real CBS API.
 */
class MockLocalAdapter(telemetry: Telemetry) extends LocalAdapter {

  override def name: String = "Mock-Local-Adapter"
  override def version: String = "1.0.0"

  private val transactionStorage: mutable.Map[String, mutable.ListBuffer[Json]] = mutable.Map.empty

  override def handleMessage(
    process: String,
    data: JsonObject,
    callContext: CallContext
  ): IO[LocalAdapterResult] = {

    process match {
      case "obp.getAdapterInfo"      | "obp_get_adapter_info"       => getAdapterInfo(callContext)
      case "obp.getBank"             | "obp_get_bank"               => getBank(data, callContext)
      case "obp.getBanks"            | "obp_get_banks"              => getBanks(data, callContext)
      case "obp.getBankAccount"      | "obp_get_bank_account"       => getBankAccount(data, callContext)
      case "obp.getAccounts"         | "obp_get_core_bank_accounts" => getCoreBankAccounts(data, callContext)
      case "obp.getBankAccounts"     | "obp_get_bank_accounts"      => getBankAccounts(data, callContext)
      case "obp.getBankAccountsForUser" | "obp_get_bank_accounts_for_user" => getBankAccountsForUser(data, callContext)
      case "obp.checkBankAccountExists" | "obp_check_bank_account_exists" => checkBankAccountExists(data, callContext)
      case "obp.getTransaction"      | "obp_get_transaction"        => getTransaction(data, callContext)
      case "obp.getTransactions"     | "obp_get_transactions"       => getTransactions(data, callContext)
      case "obp.checkFundsAvailable" | "obp_check_funds_available"  => checkFundsAvailable(data, callContext)
      case "obp.makePayment"         | "obp_make_payment"           => makePayment(data, callContext)
      case "obp.makePaymentv210"     | "obp_make_paymentv210"       => makePaymentv210(data, callContext)
      case _ => handleUnsupported(process, callContext)
    }
  }

  override def checkHealth(callContext: CallContext): IO[LocalAdapterResult] = {
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "status" -> Json.fromString("healthy"),
          "message" -> Json.fromString("Mock CBS is operational"),
          "timestamp" -> Json.fromLong(System.currentTimeMillis())
        )
      )
    )
  }

  override def getAdapterInfo(callContext: CallContext): IO[LocalAdapterResult] = {
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "name" -> Json.fromString("OBP-Rabbit-Cats-Adapter"),
          "version" -> Json.fromString("1.0.0-SNAPSHOT"),
          "description" -> Json.fromString("Functional RabbitMQ adapter for Open Bank Project"),
          "local_adapter" -> Json.fromString(name),
          "local_adapter_version" -> Json.fromString(version),
          "scala_version" -> Json.fromString("2.13.15"),
          "cats_effect_version" -> Json.fromString("3.5.7"),
          "rabbitmq_client" -> Json.fromString("amqp-client 5.20.0"),
          "http_server_port" -> Json.fromInt(52345),
          "repository" -> Json.fromString("https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter"),
          "license" -> Json.fromString("Apache License 2.0"),
          "built_with" -> Json.fromString("Scala, Cats Effect, fs2, http4s, Circe")
        )
      )
    )
  }

  // ==================== EXAMPLE IMPLEMENTATIONS ====================

  private def getBanks(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.debug(s"Getting banks list", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          Json.obj(
            "bankId"             -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
            "shortName"          -> Json.fromString("Workshop Bank"),
            "fullName"           -> Json.fromString("Workshop Bank for OBP Demo"),
            "logoUrl"            -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_x.png"),
            "websiteUrl"         -> Json.fromString("https://workshop.openbankproject.com"),
            "bankRoutingScheme"  -> Json.fromString("BIC"),
            "bankRoutingAddress" -> Json.fromString("WKSHBIC0"),
            "swiftBic"           -> Json.fromString("WKSHBIC0"),
            "nationalIdentifier" -> Json.fromString("WORKSHOP001")
          ),
          Json.obj(
            "bankId"             -> Json.obj("value" -> Json.fromString("test-bank-002")),
            "shortName"          -> Json.fromString("Test Bank"),
            "fullName"           -> Json.fromString("Test Bank for Integration"),
            "logoUrl"            -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_y.png"),
            "websiteUrl"         -> Json.fromString("https://testbank.example.com"),
            "bankRoutingScheme"  -> Json.fromString("BIC"),
            "bankRoutingAddress" -> Json.fromString("TSTBBIC0"),
            "swiftBic"           -> Json.fromString("TSTBBIC0"),
            "nationalIdentifier" -> Json.fromString("TESTBANK002")
          )
        ),
        Nil
      )
    )
  }

  private def getCoreBankAccounts(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val bankId = data("bankId").flatMap(_.asString).getOrElse("workshop-bank-001")

    telemetry.debug(s"Getting core bank accounts for bank: $bankId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          Json.obj(
            "id"          -> Json.fromString("acc-001"),
            "label"       -> Json.fromString("Alice Workshop Account"),
            "bankId"      -> Json.fromString(bankId),
            "accountType" -> Json.fromString("CURRENT"),
            "accountRoutings" -> Json.arr(
              Json.obj(
                "scheme"  -> Json.fromString("IBAN"),
                "address" -> Json.fromString("DE89370400440532013000")
              )
            )
          ),
          Json.obj(
            "id"          -> Json.fromString("acc-002"),
            "label"       -> Json.fromString("Bob Workshop Account"),
            "bankId"      -> Json.fromString(bankId),
            "accountType" -> Json.fromString("CURRENT"),
            "accountRoutings" -> Json.arr(
              Json.obj(
                "scheme"  -> Json.fromString("IBAN"),
                "address" -> Json.fromString("DE89370400440532013001")
              )
            )
          )
        ),
        Nil
      )
    )
  }

  private def getBankAccountsForUser(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val username = data("username").flatMap(_.asString).getOrElse("workshop.user")

    telemetry.debug(s"Getting bank accounts for user: $username", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          Json.obj(
            "bankId"             -> Json.fromString("workshop-bank-001"),
            "branchId"           -> Json.fromString("branch-001"),
            "accountId"          -> Json.fromString("acc-001"),
            "accountNumber"      -> Json.fromString("acc-001"),
            "accountType"        -> Json.fromString("CURRENT"),
            "balanceAmount"      -> Json.fromString("5000.00"),
            "balanceCurrency"    -> Json.fromString("EUR"),
            "owners"             -> Json.arr(Json.fromString(username)),
            "viewsToGenerate"    -> Json.arr(Json.fromString("owner")),
            "bankRoutingScheme"  -> Json.fromString("IBAN"),
            "bankRoutingAddress" -> Json.fromString("DE89370400440532013000"),
            "branchRoutingScheme"  -> Json.fromString("BRANCH_CODE"),
            "branchRoutingAddress" -> Json.fromString("001"),
            "accountRoutingScheme"  -> Json.fromString("IBAN"),
            "accountRoutingAddress" -> Json.fromString("DE89370400440532013000"),
            "accountRouting"     -> Json.obj(
              "scheme"  -> Json.fromString("IBAN"),
              "address" -> Json.fromString("DE89370400440532013000")
            ),
            "accountRules"       -> Json.arr()
          ),
          Json.obj(
            "bankId"             -> Json.fromString("workshop-bank-001"),
            "branchId"           -> Json.fromString("branch-001"),
            "accountId"          -> Json.fromString("acc-002"),
            "accountNumber"      -> Json.fromString("acc-002"),
            "accountType"        -> Json.fromString("CURRENT"),
            "balanceAmount"      -> Json.fromString("3000.00"),
            "balanceCurrency"    -> Json.fromString("EUR"),
            "owners"             -> Json.arr(Json.fromString(username)),
            "viewsToGenerate"    -> Json.arr(Json.fromString("owner")),
            "bankRoutingScheme"  -> Json.fromString("IBAN"),
            "bankRoutingAddress" -> Json.fromString("DE89370400440532013001"),
            "branchRoutingScheme"  -> Json.fromString("BRANCH_CODE"),
            "branchRoutingAddress" -> Json.fromString("001"),
            "accountRoutingScheme"  -> Json.fromString("IBAN"),
            "accountRoutingAddress" -> Json.fromString("DE89370400440532013001"),
            "accountRouting"     -> Json.obj(
              "scheme"  -> Json.fromString("IBAN"),
              "address" -> Json.fromString("DE89370400440532013001")
            ),
            "accountRules"       -> Json.arr()
          )
        ),
        Nil
      )
    )
  }

  private def checkBankAccountExists(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val bankId = data("bankId").flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString).getOrElse("workshop-bank-001")
    val accountId = data("accountId").flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString).getOrElse("acc-001")

    val validAccounts = Set("acc-001", "acc-002")
    val validBanks = Set("workshop-bank-001", "test-bank-002")
    
    val exists = validBanks.contains(bankId) && validAccounts.contains(accountId)

    telemetry.debug(s"Checking if account exists: bankId=$bankId, accountId=$accountId, exists=$exists", Some(callContext.correlationId)) *>
    IO.pure(
      if (exists) {
        LocalAdapterResult.success(
          Json.obj(
            "accountId" -> Json.obj("value" -> Json.fromString(accountId)),
            "accountType" -> Json.fromString("CURRENT"),
            "balance" -> Json.fromString(if (accountId == "acc-001") "5000.00" else "3000.00"),
            "currency" -> Json.fromString("EUR"),
            "name" -> Json.fromString(if (accountId == "acc-001") "Alice Workshop Account" else "Bob Workshop Account"),
            "number" -> Json.fromString(accountId),
            "bankId" -> Json.obj("value" -> Json.fromString(bankId)),
            "lastUpdate" -> Json.fromString("2024-03-18T00:00:00Z"),
            "branchId" -> Json.fromString("branch-001"),
            "accountRoutings" -> Json.arr(
              Json.obj(
                "scheme" -> Json.fromString("IBAN"),
                "address" -> Json.fromString(if (accountId == "acc-001") "DE89370400440532013000" else "DE89370400440532013001")
              )
            ),
            "attributes" -> Json.arr()
          ),
          Nil
        )
      } else {
        LocalAdapterResult.error("OBP-30018", s"Account not found: bankId=$bankId, accountId=$accountId")
      }
    )
  }

  private def makePaymentv210(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val amount = data("amount").flatMap(_.asString).getOrElse("0.00")
    val currency = data("currency").flatMap(_.asString).getOrElse("EUR")
    val description = data("description").flatMap(_.asString).getOrElse("Payment via makePaymentv210")
    val fromAccountId = data("fromAccountId").flatMap(_.asString)
      .orElse(data("from_account_id").flatMap(_.asString))
      .getOrElse("acc-001")
    val toAccountId = data("toAccountId").flatMap(_.asString)
      .orElse(data("to_account_id").flatMap(_.asString))
      .orElse(data("to").flatMap(_.asObject).flatMap(_("account_id")).flatMap(_.asString))
      .getOrElse("acc-002")
    val toBankId = data("toBankId").flatMap(_.asString)
      .orElse(data("to_bank_id").flatMap(_.asString))
      .orElse(data("to").flatMap(_.asObject).flatMap(_("bank_id")).flatMap(_.asString))
      .getOrElse("workshop-bank-001")

    val transactionId = s"tx-workshop-${System.currentTimeMillis()}"
    val timestamp = {
      val instant = java.time.Instant.now()
      val truncated = instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
      truncated.toString
    }

    val transaction = Json.obj(
      "uuid" -> Json.fromString(s"uuid-$transactionId"),
      "id" -> Json.obj("value" -> Json.fromString(transactionId)),
      "thisAccount" -> Json.obj(
        "accountId" -> Json.obj("value" -> Json.fromString(fromAccountId)),
        "accountType" -> Json.fromString("CURRENT"),
        "balance" -> Json.fromString("4950.00"),
        "currency" -> Json.fromString(currency),
        "name" -> Json.fromString("Workshop Account"),
        "label" -> Json.fromString("Workshop Account"),
        "number" -> Json.fromString(fromAccountId),
        "bankId" -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
        "lastUpdate" -> Json.fromString(timestamp),
        "branchId" -> Json.fromString("branch-001"),
        "accountRoutings" -> Json.arr(
          Json.obj(
            "scheme" -> Json.fromString("IBAN"),
            "address" -> Json.fromString("DE89370400440532013000")
          )
        ),
        "accountRules" -> Json.arr(),
        "accountHolder" -> Json.fromString("Workshop User"),
        "attributes" -> Json.arr()
      ),
      "otherAccount" -> Json.obj(
        "kind" -> Json.fromString("DEBIT"),
        "counterpartyId" -> Json.fromString(s"cp-$toAccountId"),
        "counterpartyName" -> Json.fromString("Payment Recipient"),
        "thisBankId" -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
        "thisAccountId" -> Json.obj("value" -> Json.fromString(fromAccountId)),
        "otherBankRoutingScheme" -> Json.fromString("BIC"),
        "otherBankRoutingAddress" -> Json.fromString("RECIPBIC0"),
        "otherAccountRoutingScheme" -> Json.fromString("IBAN"),
        "otherAccountRoutingAddress" -> Json.fromString("DE98765432109876543210"),
        "otherAccountProvider" -> Json.fromString("OBP"),
        "isBeneficiary" -> Json.fromBoolean(true)
      ),
      "transactionType" -> Json.fromString("DEBIT"),
      "amount" -> Json.fromString(amount),
      "currency" -> Json.fromString(currency),
      "description" -> Json.fromString(description),
      "startDate" -> Json.fromString(timestamp),
      "finishDate" -> Json.fromString(timestamp),
      "balance" -> Json.fromString("4950.00"),
      "status" -> Json.fromString("COMPLETED")
    )

    transactionStorage.synchronized {
      val accountTransactions = transactionStorage.getOrElseUpdate(fromAccountId, mutable.ListBuffer.empty)
      accountTransactions.prepend(transaction)
    }

    telemetry.recordPaymentSuccess(
      bankId = "workshop-bank-001",
      amount = BigDecimal(amount),
      currency = currency,
      correlationId = callContext.correlationId
    ) *>
    telemetry.debug(s"Processing payment: $amount $currency, stored transaction: $transactionId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "value" -> Json.fromString(transactionId)
        )
      )
    )
  }

  private def getBank(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val bankId = data("bankId")
      .flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString)
      .orElse(data("bankId").flatMap(_.asString))
      .getOrElse("unknown")

    telemetry.debug(s"Getting bank: $bankId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "bankId"             -> Json.obj("value" -> Json.fromString(bankId)),
          "shortName"          -> Json.fromString("Mock Bank"),
          "fullName"           -> Json.fromString("Mock Bank for Testing"),
          "logoUrl"            -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_x.png"),
          "websiteUrl"         -> Json.fromString("https://www.example.com"),
          "bankRoutingScheme"  -> Json.fromString("BIC"),
          "bankRoutingAddress" -> Json.fromString("MOCKBIC0")
        )
      )
    )
  }

  private def getBankAccount(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val bankId = data("bankId").flatMap(_.asString).getOrElse("unknown")
    val accountId = data("accountId").flatMap(_.asString).getOrElse("unknown")

    telemetry.debug(s"Getting account: $accountId at bank: $bankId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "bankId" -> Json.fromString(bankId),
          "accountId" -> Json.fromString(accountId),
          "accountType" -> Json.fromString("CURRENT"),
          "accountRoutings" -> Json.arr(
            Json.obj(
              "scheme" -> Json.fromString("IBAN"),
              "address" -> Json.fromString("GB33BUKB20201555555555")
            )
          ),
          "branchId" -> Json.fromString("branch-123"),
          "label" -> Json.fromString("Mock Checking Account"),
          "currency" -> Json.fromString("EUR"),
          "balance" -> Json.obj(
            "currency" -> Json.fromString("EUR"),
            "amount" -> Json.fromString("1000.50")
          )
        )
      )
    )
  }

  private def getBankAccounts(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val bankIdAccountIds = data("bankIdAccountIds").flatMap(_.asArray).getOrElse(Vector.empty)

    telemetry.debug(s"Getting ${bankIdAccountIds.size} bank accounts", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          bankIdAccountIds.map { item =>
            val bankId = item.asObject.flatMap(_("bankId")).flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString).getOrElse("workshop-bank-001")
            val accountId = item.asObject.flatMap(_("accountId")).flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString).getOrElse("acc-001")
            
            Json.obj(
              "bankId" -> Json.obj("value" -> Json.fromString(bankId)),
              "accountId" -> Json.obj("value" -> Json.fromString(accountId)),
              "accountType" -> Json.fromString("CURRENT"),
              "accountRoutings" -> Json.arr(
                Json.obj(
                  "scheme" -> Json.fromString("IBAN"),
                  "address" -> Json.fromString(if (accountId == "acc-001") "DE89370400440532013000" else "DE89370400440532013001")
                )
              ),
              "branchId" -> Json.fromString("branch-001"),
              "label" -> Json.fromString(if (accountId == "acc-001") "Alice Workshop Account" else "Bob Workshop Account"),
              "currency" -> Json.fromString("EUR"),
              "balance" -> Json.fromString(if (accountId == "acc-001") "5000.00" else "3000.00"),
              "name" -> Json.fromString(if (accountId == "acc-001") "Alice Workshop Account" else "Bob Workshop Account"),
              "number" -> Json.fromString(accountId),
              "lastUpdate" -> Json.fromString("2024-03-18T00:00:00Z"),
              "attributes" -> Json.arr()
            )
          }: _*
        ),
        Nil
      )
    )
  }

  private def getTransaction(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val transactionId = data("transactionId")
      .flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString)
      .orElse(data("transactionId").flatMap(_.asString))
      .getOrElse("tx-001")
    
    val bankId = data("bankId")
      .flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString)
      .orElse(data("bankId").flatMap(_.asString))
      .getOrElse("workshop-bank-001")
    
    val accountId = data("accountId")
      .flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString)
      .orElse(data("accountId").flatMap(_.asString))
      .getOrElse("acc-001")

    val allTransactions = transactionStorage.synchronized {
      transactionStorage.values.flatten.toList
    }

    val foundTransaction = allTransactions.find { txn =>
      txn.asObject.flatMap(_("id")).flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString).contains(transactionId)
    }

    telemetry.debug(s"Getting transaction: $transactionId for account: $accountId, found in storage: ${foundTransaction.isDefined}", Some(callContext.correlationId)) *>
    IO.pure(
      foundTransaction match {
        case Some(txn) =>
          LocalAdapterResult.success(txn.asObject.getOrElse(JsonObject.empty))
        case None =>
          LocalAdapterResult.success(
            JsonObject(
              "id" -> Json.obj("value" -> Json.fromString(transactionId)),
              "thisAccount" -> Json.obj(
                "accountId" -> Json.obj("value" -> Json.fromString(accountId)),
                "accountType" -> Json.fromString("AC"),
                "balance" -> Json.fromString("5000.00"),
                "currency" -> Json.fromString("EUR"),
                "name" -> Json.fromString("Workshop Account"),
                "label" -> Json.fromString("My Workshop Account"),
                "number" -> Json.fromString(accountId),
                "bankId" -> Json.obj("value" -> Json.fromString(bankId)),
                "lastUpdate" -> Json.fromString("2024-03-18T00:00:00Z"),
                "branchId" -> Json.fromString("branch-001"),
                "accountRoutings" -> Json.arr(
                  Json.obj(
                    "scheme" -> Json.fromString("IBAN"),
                    "address" -> Json.fromString("DE89370400440532013000")
                  )
                ),
                "accountRules" -> Json.arr(
                  Json.obj(
                    "scheme" -> Json.fromString("AccountRule scheme"),
                    "value" -> Json.fromString("AccountRule value")
                  )
                ),
                "accountHolder" -> Json.fromString("Workshop User"),
                "attributes" -> Json.arr(
                  Json.obj(
                    "name" -> Json.fromString("STATUS"),
                    "type" -> Json.fromString("STRING"),
                    "value" -> Json.fromString("active")
                  )
                )
              ),
              "otherAccount" -> Json.obj(
                "kind" -> Json.fromString("Counterparty"),
                "counterpartyId" -> Json.fromString("cp-merchant-001"),
                "counterpartyName" -> Json.fromString("Online Shop GmbH"),
                "thisBankId" -> Json.obj("value" -> Json.fromString("")),
                "thisAccountId" -> Json.obj("value" -> Json.fromString("")),
                "otherAccountRoutingScheme" -> Json.fromString("IBAN"),
                "otherAccountRoutingAddress" -> Json.fromString("DE98765432109876543210"),
                "isBeneficiary" -> Json.fromBoolean(false)
              ),
              "transactionType" -> Json.fromString("DEBIT"),
              "amount" -> Json.fromString("50.00"),
              "currency" -> Json.fromString("EUR"),
              "description" -> Json.fromString("Online purchase - Workshop transaction"),
              "startDate" -> Json.fromString("2024-01-14T10:30:00Z"),
              "finishDate" -> Json.fromString("2024-01-14T10:30:00Z"),
              "balance" -> Json.fromString("4950.00"),
              "status" -> Json.fromString("COMPLETED")
            )
          )
      }
    )
  }

  private def getTransactions(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val accountId = data("bankIdAccountId").flatMap(_.asString)
      .orElse(data("accountId").flatMap(_.asString))
      .getOrElse("acc-001")

    val storedTransactions = transactionStorage.synchronized {
      transactionStorage.getOrElse(accountId, mutable.ListBuffer.empty).toList
    }

    val staticTransactions = List(
          Json.obj(
            "uuid" -> Json.fromString("uuid-tx-001"),
            "id"   -> Json.obj("value" -> Json.fromString("tx-001")),
            "thisAccount" -> Json.obj(
              "accountId"      -> Json.obj("value" -> Json.fromString(accountId)),
              "accountType"    -> Json.fromString("CURRENT"),
              "balance"        -> Json.fromString("4950.00"),
              "currency"       -> Json.fromString("EUR"),
              "name"           -> Json.fromString("Alice Workshop Account"),
              "label"          -> Json.fromString("Alice Workshop Account"),
              "number"         -> Json.fromString(accountId),
              "bankId"         -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
              "lastUpdate"     -> Json.fromString("2025-01-14T00:00:00Z"),
              "branchId"       -> Json.fromString("branch-001"),
              "accountRoutings" -> Json.arr(
                Json.obj(
                  "scheme"  -> Json.fromString("IBAN"),
                  "address" -> Json.fromString("DE89370400440532013000")
                )
              ),
              "accountRules"   -> Json.arr(),
              "accountHolder"  -> Json.fromString("Alice"),
              "attributes"     -> Json.arr()
            ),
            "otherAccount" -> Json.obj(
              "nationalIdentifier"      -> Json.fromString("MERCHANT001"),
              "kind"                    -> Json.fromString("DEBIT"),
              "counterpartyId"          -> Json.fromString("cp-merchant-001"),
              "counterpartyName"        -> Json.fromString("Online Shop GmbH"),
              "thisBankId"              -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
              "thisAccountId"           -> Json.obj("value" -> Json.fromString(accountId)),
              "otherBankRoutingScheme"  -> Json.fromString("BIC"),
              "otherBankRoutingAddress" -> Json.fromString("SHOPBIC0"),
              "otherAccountRoutingScheme"  -> Json.fromString("IBAN"),
              "otherAccountRoutingAddress" -> Json.fromString("DE12345678901234567890"),
              "otherAccountProvider"    -> Json.fromString("OBP"),
              "isBeneficiary"           -> Json.fromBoolean(true)
            ),
            "transactionType" -> Json.fromString("DEBIT"),
            "amount"          -> Json.fromString("50.00"),
            "currency"        -> Json.fromString("EUR"),
            "description"     -> Json.fromString("Online purchase - Online Shop GmbH"),
            "startDate"       -> Json.fromString("2025-01-14T10:30:00Z"),
            "finishDate"      -> Json.fromString("2025-01-14T10:30:05Z"),
            "balance"         -> Json.fromString("4950.00"),
            "status"          -> Json.fromString("COMPLETED")
          ),
          Json.obj(
            "uuid" -> Json.fromString("uuid-tx-002"),
            "id"   -> Json.obj("value" -> Json.fromString("tx-002")),
            "thisAccount" -> Json.obj(
              "accountId"      -> Json.obj("value" -> Json.fromString(accountId)),
              "accountType"    -> Json.fromString("CURRENT"),
              "balance"        -> Json.fromString("5000.00"),
              "currency"       -> Json.fromString("EUR"),
              "name"           -> Json.fromString("Alice Workshop Account"),
              "label"          -> Json.fromString("Alice Workshop Account"),
              "number"         -> Json.fromString(accountId),
              "bankId"         -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
              "lastUpdate"     -> Json.fromString("2025-01-14T00:00:00Z"),
              "branchId"       -> Json.fromString("branch-001"),
              "accountRoutings" -> Json.arr(
                Json.obj(
                  "scheme"  -> Json.fromString("IBAN"),
                  "address" -> Json.fromString("DE89370400440532013000")
                )
              ),
              "accountRules"   -> Json.arr(),
              "accountHolder"  -> Json.fromString("Alice"),
              "attributes"     -> Json.arr()
            ),
            "otherAccount" -> Json.obj(
              "nationalIdentifier"      -> Json.fromString("EMPLOYER001"),
              "kind"                    -> Json.fromString("CREDIT"),
              "counterpartyId"          -> Json.fromString("cp-employer-001"),
              "counterpartyName"        -> Json.fromString("Employer Corp AG"),
              "thisBankId"              -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
              "thisAccountId"           -> Json.obj("value" -> Json.fromString(accountId)),
              "otherBankRoutingScheme"  -> Json.fromString("BIC"),
              "otherBankRoutingAddress" -> Json.fromString("EMPLBIC0"),
              "otherAccountRoutingScheme"  -> Json.fromString("IBAN"),
              "otherAccountRoutingAddress" -> Json.fromString("DE98765432109876543210"),
              "otherAccountProvider"    -> Json.fromString("OBP"),
              "isBeneficiary"           -> Json.fromBoolean(false)
            ),
            "transactionType" -> Json.fromString("CREDIT"),
            "amount"          -> Json.fromString("3000.00"),
            "currency"        -> Json.fromString("EUR"),
            "description"     -> Json.fromString("Monthly salary - Employer Corp AG"),
            "startDate"       -> Json.fromString("2025-01-13T09:00:00Z"),
            "finishDate"      -> Json.fromString("2025-01-13T09:00:01Z"),
            "balance"         -> Json.fromString("5000.00"),
            "status"          -> Json.fromString("COMPLETED")
          ),
          Json.obj(
            "uuid" -> Json.fromString("uuid-tx-003"),
            "id"   -> Json.obj("value" -> Json.fromString("tx-003")),
            "thisAccount" -> Json.obj(
              "accountId"      -> Json.obj("value" -> Json.fromString(accountId)),
              "accountType"    -> Json.fromString("CURRENT"),
              "balance"        -> Json.fromString("2000.00"),
              "currency"       -> Json.fromString("EUR"),
              "name"           -> Json.fromString("Alice Workshop Account"),
              "label"          -> Json.fromString("Alice Workshop Account"),
              "number"         -> Json.fromString(accountId),
              "bankId"         -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
              "lastUpdate"     -> Json.fromString("2025-01-14T00:00:00Z"),
              "branchId"       -> Json.fromString("branch-001"),
              "accountRoutings" -> Json.arr(
                Json.obj(
                  "scheme"  -> Json.fromString("IBAN"),
                  "address" -> Json.fromString("DE89370400440532013000")
                )
              ),
              "accountRules"   -> Json.arr(),
              "accountHolder"  -> Json.fromString("Alice"),
              "attributes"     -> Json.arr()
            ),
            "otherAccount" -> Json.obj(
              "nationalIdentifier"      -> Json.fromString("COFFEE001"),
              "kind"                    -> Json.fromString("DEBIT"),
              "counterpartyId"          -> Json.fromString("cp-coffee-001"),
              "counterpartyName"        -> Json.fromString("Brew & Co"),
              "thisBankId"              -> Json.obj("value" -> Json.fromString("workshop-bank-001")),
              "thisAccountId"           -> Json.obj("value" -> Json.fromString(accountId)),
              "otherBankRoutingScheme"  -> Json.fromString("BIC"),
              "otherBankRoutingAddress" -> Json.fromString("BREWBIC0"),
              "otherAccountRoutingScheme"  -> Json.fromString("IBAN"),
              "otherAccountRoutingAddress" -> Json.fromString("DE11223344556677889900"),
              "otherAccountProvider"    -> Json.fromString("OBP"),
              "isBeneficiary"           -> Json.fromBoolean(true)
            ),
            "transactionType" -> Json.fromString("DEBIT"),
            "amount"          -> Json.fromString("25.50"),
            "currency"        -> Json.fromString("EUR"),
            "description"     -> Json.fromString("Coffee subscription - Brew & Co"),
            "startDate"       -> Json.fromString("2025-01-12T08:15:00Z"),
            "finishDate"      -> Json.fromString("2025-01-12T08:15:02Z"),
            "balance"         -> Json.fromString("2000.00"),
            "status"          -> Json.fromString("COMPLETED")
          )
    )

    val allTransactions = storedTransactions ++ staticTransactions

    telemetry.debug(s"Getting transactions for account: $accountId, stored: ${storedTransactions.size}, static: ${staticTransactions.size}, total: ${allTransactions.size}", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(allTransactions: _*),
        Nil
      )
    )
  }

  private def checkFundsAvailable(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val amount = data("amount").flatMap(_.asString).getOrElse("0")
    val currency = data("currency").flatMap(_.asString).getOrElse("EUR")

    telemetry.debug(s"Checking funds: $amount $currency", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "available" -> Json.fromBoolean(true),
          "amount" -> Json.fromString(amount),
          "currency" -> Json.fromString(currency)
        )
      )
    )
  }

  private def makePayment(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val amount = data("amount").flatMap(_.asString).getOrElse("0")
    val currency = data("currency").flatMap(_.asString).getOrElse("EUR")
    val description = data("description").flatMap(_.asString).getOrElse("Payment")

    telemetry.recordPaymentSuccess(
      bankId = "mock-bank",
      amount = BigDecimal(amount),
      currency = currency,
      correlationId = callContext.correlationId
    ) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "transactionId" -> Json.fromString(s"tx-${System.currentTimeMillis()}"),
          "amount" -> Json.fromString(amount),
          "currency" -> Json.fromString(currency),
          "description" -> Json.fromString(description),
          "status" -> Json.fromString("COMPLETED"),
          "posted" -> Json.fromString(java.time.Instant.now().toString)
        ),
        List(
          BackendMessage(
            source = "MockCBS",
            status = "success",
            errorCode = "",
            text = "Payment processed successfully",
            duration = Some("0.050")
          )
        )
      )
    )
  }

  private def handleUnsupported(process: String, callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.warn(s"Unsupported message type: $process", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.error(
        code = "OBP-50000",
        message = s"Message type not implemented: $process",
        messages = List(
          BackendMessage(
            source = "MockCBS",
            status = "error",
            errorCode = "NOT_IMPLEMENTED",
            text = s"Message type $process is not implemented in MockLocalAdapter",
            duration = None
          )
        )
      )
    )
  }
}

object MockLocalAdapter {
  def apply(telemetry: Telemetry): MockLocalAdapter = new MockLocalAdapter(telemetry)
}
