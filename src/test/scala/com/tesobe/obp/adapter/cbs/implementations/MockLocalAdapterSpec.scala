package com.tesobe.obp.adapter.cbs.implementations

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.tesobe.obp.adapter.interfaces.LocalAdapterResult
import com.tesobe.obp.adapter.models.CallContext
import com.tesobe.obp.adapter.telemetry.Telemetry
import io.circe.{Json, JsonObject}
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MockLocalAdapterSpec extends AnyPropSpec with Matchers with ScalaCheckPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 100)

  val telemetry: Telemetry = new Telemetry {
    override def debug(message: String, correlationId: Option[String]): IO[Unit] = IO.unit
    override def info(message: String, correlationId: Option[String]): IO[Unit] = IO.unit
    override def warn(message: String, correlationId: Option[String]): IO[Unit] = IO.unit
    override def error(message: String, throwable: Option[Throwable], correlationId: Option[String]): IO[Unit] = IO.unit
    override def recordPaymentSuccess(bankId: String, amount: BigDecimal, currency: String, correlationId: String): IO[Unit] = IO.unit
    override def recordMessageReceived(process: String, correlationId: String, queueName: String): IO[Unit] = IO.unit
    override def recordMessageProcessed(process: String, correlationId: String, duration: scala.concurrent.duration.FiniteDuration): IO[Unit] = IO.unit
    override def recordMessageFailed(process: String, correlationId: String, errorCode: String, errorMessage: String, duration: scala.concurrent.duration.FiniteDuration): IO[Unit] = IO.unit
    override def recordResponseSent(process: String, correlationId: String, success: Boolean): IO[Unit] = IO.unit
    override def recordCBSOperationStart(operation: String, correlationId: String): IO[Unit] = IO.unit
    override def recordCBSOperationSuccess(operation: String, correlationId: String, duration: scala.concurrent.duration.FiniteDuration): IO[Unit] = IO.unit
    override def recordCBSOperationFailure(operation: String, correlationId: String, errorCode: String, errorMessage: String, duration: scala.concurrent.duration.FiniteDuration): IO[Unit] = IO.unit
    override def recordCBSOperationRetry(operation: String, correlationId: String, attemptNumber: Int, reason: String): IO[Unit] = IO.unit
    override def recordRabbitMQConnected(host: String, port: Int): IO[Unit] = IO.unit
    override def recordRabbitMQDisconnected(reason: String): IO[Unit] = IO.unit
    override def recordRabbitMQConnectionError(errorMessage: String): IO[Unit] = IO.unit
    override def recordQueueConsumptionStarted(queueName: String): IO[Unit] = IO.unit
    override def recordQueueConsumptionStopped(queueName: String, reason: String): IO[Unit] = IO.unit
    override def recordQueueDepth(queueName: String, depth: Long): IO[Unit] = IO.unit
    override def recordProcessingRate(messagesPerSecond: Double): IO[Unit] = IO.unit
    override def recordMemoryUsage(usedMB: Long, totalMB: Long): IO[Unit] = IO.unit
    override def recordPaymentFailure(bankId: String, amount: BigDecimal, currency: String, errorCode: String, correlationId: String): IO[Unit] = IO.unit
    override def recordAccountCreated(bankId: String, accountType: String, correlationId: String): IO[Unit] = IO.unit
    override def recordCustomerCreated(bankId: String, correlationId: String): IO[Unit] = IO.unit
    override def recordError(category: String, errorCode: String, errorMessage: String, correlationId: Option[String], additionalContext: Map[String, String]): IO[Unit] = IO.unit
    override def recordWarning(category: String, message: String, correlationId: Option[String]): IO[Unit] = IO.unit
    override def startSpan(operationName: String, correlationId: String, attributes: Map[String, String]): IO[String] = IO.pure("test-span")
    override def endSpan(spanId: String, success: Boolean): IO[Unit] = IO.unit
    override def addSpanEvent(spanId: String, eventName: String, attributes: Map[String, String]): IO[Unit] = IO.unit
    override def recordHealthCheck(component: String, healthy: Boolean, message: String): IO[Unit] = IO.unit
  }

  val adapter = new MockLocalAdapter(telemetry)

  def createCallContext(correlationId: String): CallContext = CallContext(
    correlationId = correlationId,
    sessionId = Some("test-session"),
    userId = Some("test-user"),
    username = Some("testuser"),
    consumerId = Some("test-consumer"),
    generalContext = Map.empty
  )

  // Feature: workshop-adapter-mock-data, Property 1: getBanks always returns non-empty list with required fields
  property("Property 1: getBanks always returns a non-empty list with required fields") {
    forAll { (correlationId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject.empty

      val result = adapter.handleMessage("obp.getBanks", data, callContext).unsafeRunSync()
      
      result shouldBe a[LocalAdapterResult.Success]
      val success = result.asInstanceOf[LocalAdapterResult.Success]
      
      success.data.asArray should not be empty
      val banks = success.data.asArray.get
      banks.size should be >= 2

      banks.foreach { bank =>
        val bankObj = bank.asObject.get
        bankObj("bankId").flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString) should not be empty
        bankObj("shortName").flatMap(_.asString) should not be empty
        bankObj("fullName").flatMap(_.asString) should not be empty
        bankObj("logoUrl").flatMap(_.asString) should not be empty
        bankObj("websiteUrl").flatMap(_.asString) should not be empty
        bankObj("bankRoutingScheme").flatMap(_.asString) should not be empty
        bankObj("bankRoutingAddress").flatMap(_.asString) should not be empty
      }
    }
  }

  // Feature: workshop-adapter-mock-data, Property 2: Workshop bank is always the first entry
  property("Property 2: Workshop bank is always the first entry") {
    forAll { (correlationId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject.empty

      val result = adapter.handleMessage("obp.getBanks", data, callContext).unsafeRunSync()
      
      result shouldBe a[LocalAdapterResult.Success]
      val success = result.asInstanceOf[LocalAdapterResult.Success]
      
      val banks = success.data.asArray.get
      val firstBank = banks.head.asObject.get
      val bankId = firstBank("bankId").flatMap(_.asObject).flatMap(_("value")).flatMap(_.asString)
      
      bankId shouldBe Some("workshop-bank-001")
    }
  }

  // Feature: workshop-adapter-mock-data, Property 3: getCoreBankAccounts returns accounts with required fields
  property("Property 3: getCoreBankAccounts returns accounts with required fields") {
    forAll { (correlationId: String, bankId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject("bankId" -> Json.fromString(bankId))

      val result = adapter.handleMessage("obp.getAccounts", data, callContext).unsafeRunSync()
      
      result shouldBe a[LocalAdapterResult.Success]
      val success = result.asInstanceOf[LocalAdapterResult.Success]
      
      success.data.asArray should not be empty
      val accounts = success.data.asArray.get
      accounts.size should be >= 2

      accounts.foreach { account =>
        val accountObj = account.asObject.get
        accountObj("id").flatMap(_.asString) should not be empty
        accountObj("label").flatMap(_.asString) should not be empty
        accountObj("bankId").flatMap(_.asString) should not be empty
        accountObj("accountType").flatMap(_.asString) should not be empty
        accountObj("accountRoutings").flatMap(_.asArray) should not be empty
      }
    }
  }

  // Feature: workshop-adapter-mock-data, Property 4: makePaymentv210 always returns a unique TransactionId
  property("Property 4: makePaymentv210 always returns a unique TransactionId") {
    forAll { (correlationId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject("amount" -> Json.fromString("10.00"), "currency" -> Json.fromString("EUR"))

      val result1 = adapter.handleMessage("obp.makePaymentv210", data, callContext).unsafeRunSync()
      Thread.sleep(2)
      val result2 = adapter.handleMessage("obp.makePaymentv210", data, callContext).unsafeRunSync()

      result1 shouldBe a[LocalAdapterResult.Success]
      result2 shouldBe a[LocalAdapterResult.Success]
      
      val success1 = result1.asInstanceOf[LocalAdapterResult.Success]
      val success2 = result2.asInstanceOf[LocalAdapterResult.Success]
      
      val txId1 = success1.data.asObject.flatMap(_("value")).flatMap(_.asString)
      val txId2 = success2.data.asObject.flatMap(_("value")).flatMap(_.asString)
      
      txId1 should not be empty
      txId2 should not be empty
      txId1 should not equal txId2
    }
  }

  // Feature: workshop-adapter-mock-data, Property 5: makePaymentv210 response contains all required fields
  property("Property 5: makePaymentv210 response contains all required fields") {
    forAll { (correlationId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject("amount" -> Json.fromString("10.00"), "currency" -> Json.fromString("EUR"))

      val result = adapter.handleMessage("obp.makePaymentv210", data, callContext).unsafeRunSync()
      
      result shouldBe a[LocalAdapterResult.Success]
      val success = result.asInstanceOf[LocalAdapterResult.Success]
      
      val txId = success.data.asObject.flatMap(_("value")).flatMap(_.asString)
      txId should not be empty
      txId.get should startWith("tx-workshop-")
    }
  }

  // Feature: workshop-adapter-mock-data, Property 6: getTransactions returns >= 3 transactions with both CREDIT and DEBIT
  property("Property 6: getTransactions returns at least three transactions with both CREDIT and DEBIT types") {
    forAll { (correlationId: String, accountId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject("accountId" -> Json.fromString(accountId))

      val result = adapter.handleMessage("obp.getTransactions", data, callContext).unsafeRunSync()
      
      result shouldBe a[LocalAdapterResult.Success]
      val success = result.asInstanceOf[LocalAdapterResult.Success]
      
      val transactions = success.data.asArray.get
      transactions.size should be >= 3

      val transactionTypes = transactions.flatMap { tx =>
        tx.asObject.flatMap(_("transactionType")).flatMap(_.asString)
      }.toSet

      transactionTypes should contain("CREDIT")
      transactionTypes should contain("DEBIT")
    }
  }

  // Feature: workshop-adapter-mock-data, Property 7: getTransactions echoes accountId into each transaction
  property("Property 7: getTransactions echoes accountId into each transaction") {
    forAll { (correlationId: String, accountId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject("accountId" -> Json.fromString(accountId))

      val result = adapter.handleMessage("obp.getTransactions", data, callContext).unsafeRunSync()
      
      result shouldBe a[LocalAdapterResult.Success]
      val success = result.asInstanceOf[LocalAdapterResult.Success]
      
      val transactions = success.data.asArray.get
      transactions.foreach { tx =>
        val txObj = tx.asObject.get
        val thisAccountId = txObj("thisAccount")
          .flatMap(_.asObject)
          .flatMap(_("accountId"))
          .flatMap(_.asObject)
          .flatMap(_("value"))
          .flatMap(_.asString)
        
        val otherAccountId = txObj("otherAccount")
          .flatMap(_.asObject)
          .flatMap(_("thisAccountId"))
          .flatMap(_.asObject)
          .flatMap(_("value"))
          .flatMap(_.asString)
        
        thisAccountId shouldBe Some(accountId)
        otherAccountId shouldBe Some(accountId)
      }
    }
  }

  // Feature: workshop-adapter-mock-data, Property 8: Both process name variants dispatch to the same handler
  property("Property 8: Both process name variants dispatch to the same handler") {
    val methods = List(
      ("obp.getBanks", "obp_get_banks"),
      ("obp.getAccounts", "obp_get_core_bank_accounts"),
      ("obp.getTransactions", "obp_get_transactions")
    )

    forAll { (correlationId: String) =>
      val callContext = createCallContext(correlationId)
      val data = JsonObject.empty

      methods.foreach { case (dotNotation, underscoreNotation) =>
        val result1 = adapter.handleMessage(dotNotation, data, callContext).unsafeRunSync()
        val result2 = adapter.handleMessage(underscoreNotation, data, callContext).unsafeRunSync()
        
        result1 shouldBe a[LocalAdapterResult.Success]
        result2 shouldBe a[LocalAdapterResult.Success]
        
        val success1 = result1.asInstanceOf[LocalAdapterResult.Success]
        val success2 = result2.asInstanceOf[LocalAdapterResult.Success]
        
        success1.data shouldBe success2.data
      }
    }
  }

  // Feature: workshop-adapter-mock-data, Property 9: Unknown process names return OBP-50000
  property("Property 9: Unknown process names return OBP-50000") {
    forAll { (correlationId: String, unknownProcess: String) =>
      whenever(!unknownProcess.startsWith("obp.get") && !unknownProcess.startsWith("obp.make") && !unknownProcess.startsWith("obp.check")) {
        val callContext = createCallContext(correlationId)
        val data = JsonObject.empty

        val result = adapter.handleMessage(unknownProcess, data, callContext).unsafeRunSync()
        
        result shouldBe a[LocalAdapterResult.Error]
        val error = result.asInstanceOf[LocalAdapterResult.Error]
        error.errorCode shouldBe "OBP-50000"
      }
    }
  }
}
