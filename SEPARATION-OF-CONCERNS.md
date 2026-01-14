# Separation of Concerns

## Overview

This document clearly illustrates what code is **generic/reusable** vs **bank-specific/customizable** vs **cross-cutting concerns**.

---

## The Three Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                        GENERIC CODE                             │
│                   (Same for all banks)                          │
│                 ✅ YOU DON'T TOUCH THIS                         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     BANK-SPECIFIC CODE                          │
│                  (Different per bank)                           │
│                  ⚙️ YOU IMPLEMENT THIS                          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    CROSS-CUTTING CONCERNS                       │
│                  (Telemetry/Observability)                      │
│                 📊 YOU CONFIGURE THIS                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1. Generic Code (✅ Reusable - Don't Touch)

### What It Does
- Handles all OBP message protocol
- Manages RabbitMQ connections and queues
- Routes messages to correct handlers
- Tracks correlation IDs
- Builds OBP-compliant responses
- Manages message lifecycle

### Files
```
src/main/scala/com/tesobe/obp/adapter/
├── models/
│   └── OBPModels.scala              ✅ Generic - OBP message types
├── messaging/
│   ├── RabbitMQConsumer.scala       ✅ Generic - Consumes messages
│   ├── RabbitMQProducer.scala       ✅ Generic - Sends responses
│   └── MessageRouter.scala          ✅ Generic - Routes by type
├── handlers/
│   ├── BankHandlers.scala           ✅ Generic - Orchestrates CBS calls
│   ├── AccountHandlers.scala        ✅ Generic - Orchestrates CBS calls
│   ├── TransactionHandlers.scala    ✅ Generic - Orchestrates CBS calls
│   └── CustomerHandlers.scala       ✅ Generic - Orchestrates CBS calls
└── config/
    └── Config.scala                 ✅ Generic - Config structure
```

### Example: Bank Handler (Generic)

```scala
// This code is the same for ALL banks
object BankHandlers {
  
  def handleGetBank(
    message: GetBankMessage,
    connector: CBSConnector,  // ← Your implementation injected
    telemetry: Telemetry
  ): IO[InboundMessage] = {
    for {
      // Extract from OBP message
      bankId <- IO.pure(message.data.bankId)
      callContext <- IO.pure(message.outboundAdapterCallContext)
      
      // Start telemetry
      _ <- telemetry.recordMessageReceived("obp.getBank", callContext.correlationId, "obp.request")
      
      // Call YOUR CBS implementation
      result <- connector.getBank(bankId, callContext)
      
      // Build OBP response
      response <- result match {
        case CBSResponse.Success(bank, ctx, messages) =>
          IO.pure(InboundMessage.success(bank, ctx, messages))
        case CBSResponse.Error(code, msg, ctx, messages) =>
          IO.pure(InboundMessage.error(code, msg, ctx, messages))
      }
      
      // Record telemetry
      _ <- telemetry.recordMessageProcessed("obp.getBank", callContext.correlationId, duration)
      
    } yield response
  }
}
```

**You never modify this!** It just calls your `CBSConnector` implementation.

---

## 2. Bank-Specific Code (⚙️ You Implement)

### What It Does
- Calls YOUR Core Banking System API
- Handles YOUR authentication
- Maps YOUR data format to OBP models
- Implements YOUR business logic
- Handles YOUR error codes

### Files
```
src/main/scala/com/tesobe/obp/adapter/
├── interfaces/
│   └── CBSConnector.scala           📝 Interface you implement
└── cbs/implementations/
    └── YourBankConnector.scala      ⚙️ YOUR CODE - CBS integration
```

### Example: Your Bank Connector (Bank-Specific)

```scala
// THIS is where YOUR bank-specific code goes
class YourBankConnector(
  baseUrl: String,
  apiKey: String,
  httpClient: HttpClient,
  telemetry: Telemetry
) extends CBSConnector {
  
  override def name: String = "YourBank-REST-v1"
  override def version: String = "1.0.0"
  
  // YOU implement this to call YOUR CBS
  override def getBank(
    bankId: String,
    callContext: CallContext
  ): IO[CBSResponse[BankCommons]] = {
    
    // 1. Call YOUR CBS API (your protocol, your auth, your format)
    httpClient.get(
      url = s"$baseUrl/api/v2/banks/$bankId",
      headers = Map(
        "X-API-Key" -> apiKey,
        "X-Request-ID" -> callContext.correlationId
      )
    ).flatMap { response =>
      
      // 2. Parse YOUR response format
      val yourBankData = parseYourJson(response.body)
      
      // 3. Map YOUR data to OBP model
      val obpBank = BankCommons(
        bankId = yourBankData.id,
        shortName = yourBankData.name,
        fullName = yourBankData.full_name,
        logoUrl = yourBankData.logo_url,
        websiteUrl = yourBankData.website
      )
      
      // 4. Return OBP response
      IO.pure(CBSResponse.success(obpBank, callContext))
      
    }.handleErrorWith { error =>
      // 5. Handle YOUR error codes
      error match {
        case YourBankNotFoundException(_) =>
          IO.pure(CBSResponse.error("BANK_NOT_FOUND", "Bank does not exist", callContext))
        case YourBankAuthException(_) =>
          IO.pure(CBSResponse.error("CBS_AUTH_FAILED", "Authentication failed", callContext))
        case _ =>
          IO.pure(CBSResponse.error("CBS_ERROR", error.getMessage, callContext))
      }
    }
  }
  
  // Implement other operations similarly...
  override def getBankAccount(...) = ???
  override def makePayment(...) = ???
  // ... etc
}
```

**This is YOUR code!** Different for every bank.

---

## 3. Cross-Cutting Concerns (📊 You Configure)

### What It Does
- Records metrics
- Logs operations
- Traces requests
- Monitors health
- Reports errors

### Files
```
src/main/scala/com/tesobe/obp/adapter/
└── telemetry/
    ├── Telemetry.scala              📝 Interface
    ├── ConsoleTelemetry.scala       📊 Console logging
    ├── PrometheusTelemetry.scala    📊 Prometheus metrics
    ├── DatadogTelemetry.scala       📊 Datadog APM
    └── NoOpTelemetry.scala          📊 Disabled
```

### Example: Telemetry Usage

```scala
// In YourBankConnector
override def makePayment(...): IO[CBSResponse[TransactionCommons]] = {
  for {
    // Start span
    spanId <- telemetry.startSpan("makePayment", callContext.correlationId)
    
    // Record operation start
    _ <- telemetry.recordCBSOperationStart("makePayment", callContext.correlationId)
    
    // Make CBS call
    result <- callYourCBS(...)
    
    // Record metrics
    _ <- result match {
      case CBSResponse.Success(tx, _, _) =>
        telemetry.recordPaymentSuccess(bankId, tx.amount, tx.currency, callContext.correlationId)
      case CBSResponse.Error(code, msg, _, _) =>
        telemetry.recordPaymentFailure(bankId, amount, currency, code, callContext.correlationId)
    }
    
    // End span
    _ <- telemetry.endSpan(spanId, result.isSuccess)
    
  } yield result
}
```

**You choose which implementation to use** (Console, Prometheus, Datadog, etc.)

---

## Visual Separation

```
╔═══════════════════════════════════════════════════════════════╗
║                      OBP-API (RabbitMQ)                       ║
╚═══════════════════════════════════════════════════════════════╝
                              ↓ ↑
                     obp.request / obp.response
                              ↓ ↑
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                    GENERIC ADAPTER LAYER                      ┃
┃                  (messaging/ + handlers/)                     ┃
┃                    ✅ DON'T MODIFY THIS                       ┃
┃                                                               ┃
┃  1. Receive RabbitMQ message                                 ┃
┃  2. Parse OBP message format                                 ┃
┃  3. Extract correlation ID, auth context                     ┃
┃  4. Route to handler by message type                         ┃
┃  5. Call CBSConnector interface method                       ┃
┃  6. Build OBP response format                                ┃
┃  7. Send to RabbitMQ response queue                          ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                              ↓ ↑
                     trait CBSConnector {
                       def getBank(...): IO[...]
                       def makePayment(...): IO[...]
                     }
                              ↓ ↑
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                BANK-SPECIFIC IMPLEMENTATION                   ┃
┃                 (cbs/implementations/)                        ┃
┃                    ⚙️ YOU WRITE THIS                          ┃
┃                                                               ┃
┃  class YourBankConnector extends CBSConnector {              ┃
┃    override def getBank(...) = {                             ┃
┃      // Call YOUR CBS API                                    ┃
┃      // Map YOUR data to OBP models                          ┃
┃      // Handle YOUR errors                                   ┃
┃    }                                                          ┃
┃  }                                                            ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                              ↓ ↑
                      HTTP / SOAP / DB
                              ↓ ↑
╔═══════════════════════════════════════════════════════════════╗
║                    YOUR CORE BANKING SYSTEM                   ║
╚═══════════════════════════════════════════════════════════════╝

┌───────────────────────────────────────────────────────────────┐
│                  TELEMETRY (CROSS-CUTTING)                    │
│                         (telemetry/)                          │
│                      📊 YOU CONFIGURE THIS                    │
│                                                               │
│  All layers call:                                            │
│    telemetry.recordMessageProcessed(...)                     │
│    telemetry.recordCBSOperationSuccess(...)                  │
│    telemetry.recordError(...)                                │
│                                                               │
│  You choose implementation:                                   │
│    - ConsoleTelemetry (dev)                                  │
│    - PrometheusTelemetry (prod)                              │
│    - DatadogTelemetry (prod)                                 │
└───────────────────────────────────────────────────────────────┘
```

---

## What You Implement

### Minimum Implementation (Read-Only Operations)

```scala
class YourBankConnector extends CBSConnector {
  // Bank operations
  def getBank(...)                  // ⚙️ Required
  def getBanks(...)                 // ⚙️ Required
  
  // Account operations
  def getBankAccount(...)           // ⚙️ Required
  def getBankAccounts(...)          // ⚙️ Required
  def getAccountBalance(...)        // ⚙️ Required
  
  // Transaction operations (read-only)
  def getTransaction(...)           // ⚙️ Required
  def getTransactions(...)          // ⚙️ Required
  
  // Customer operations (read-only)
  def getCustomer(...)              // ⚙️ Optional
  
  // Health check
  def checkHealth(...)              // ⚙️ Required
  def getAdapterInfo(...)           // ⚙️ Required
  
  // Everything else - return error
  def makePayment(...) = IO.pure(
    CBSResponse.error("NOT_IMPLEMENTED", "Payment not supported yet", ctx)
  )
}
```

### Full Implementation (All Operations)

Add these when ready:
- `createBankAccount` - Account creation
- `updateBankAccount` - Account updates
- `makePayment` - Payments/transfers
- `createCustomer` - Customer onboarding
- `updateCustomer` - Customer updates
- `getCard` - Card information
- `getCounterparty` - Counterparty/beneficiary info
- ... and more

---

## Benefits of This Separation

### For Bank Developers 🏦

✅ **Focus on CBS integration** - That's your domain expertise  
✅ **No RabbitMQ knowledge needed** - Already handled  
✅ **No OBP protocol knowledge needed** - Already handled  
✅ **Clear interface contract** - Just implement `CBSConnector`  
✅ **Type safety** - Compiler catches mistakes  
✅ **Testable** - Unit test your connector in isolation  

### For Operations Teams 🔧

✅ **Standard deployment** - Same Docker setup for all banks  
✅ **Standard monitoring** - Same metrics for all banks  
✅ **Standard configuration** - Environment variables  
✅ **Standard logging** - Correlation IDs everywhere  
✅ **Multiple banks** - Run different connectors per instance  

### For OBP Team 🌐

✅ **Reusable core** - Generic message handling  
✅ **Consistent interface** - All adapters work the same way  
✅ **Easy updates** - Update generic code, all banks benefit  
✅ **Quality assurance** - Generic code tested once  
✅ **Documentation** - One architecture, many banks  

---

## Example: Adding a New Operation

### Generic Handler (Already Exists)
```scala
// handlers/AccountHandlers.scala
// ✅ Generic - same for all banks

def handleGetAccountBalance(
  message: GetBalanceMessage,
  connector: CBSConnector,  // Your implementation
  telemetry: Telemetry
): IO[InboundMessage] = {
  for {
    result <- connector.getAccountBalance(
      message.data.bankId,
      message.data.accountId,
      message.callContext
    )
    response <- buildInboundMessage(result)
  } yield response
}
```

### Your Implementation
```scala
// cbs/implementations/YourBankConnector.scala
// ⚙️ Bank-specific - YOUR code

override def getAccountBalance(
  bankId: String,
  accountId: String,
  callContext: CallContext
): IO[CBSResponse[AccountBalance]] = {
  
  // Call YOUR CBS API
  httpClient.get(s"$baseUrl/accounts/$accountId/balance")
    .map { response =>
      val balance = parseYourJson(response)
      
      // Map to OBP format
      CBSResponse.success(
        AccountBalance(
          currency = balance.currency,
          amount = balance.available_balance
        ),
        callContext
      )
    }
}
```

**That's it!** The generic handler routes the message to your implementation.

---

## Summary

| Component | Type | Who Modifies |
|-----------|------|--------------|
| **RabbitMQ Consumer/Producer** | Generic | ✅ Nobody |
| **OBP Message Models** | Generic | ✅ Nobody |
| **Message Handlers** | Generic | ✅ Nobody |
| **Message Router** | Generic | ✅ Nobody |
| **CBSConnector Interface** | Contract | 📝 Extend if needed |
| **Your CBS Connector** | Bank-Specific | ⚙️ You implement |
| **Telemetry Interface** | Contract | 📝 Extend if needed |
| **Telemetry Implementation** | Configurable | 📊 You choose/implement |
| **Configuration** | Deployment | 🔧 You configure |

---

## Questions?

**Q: Can I modify the generic handlers?**  
A: You shouldn't need to. If you do, consider if it's really CBS-specific logic that should be in your connector instead.

**Q: What if OBP adds a new message type?**  
A: We update the generic handler, you implement the new method in `CBSConnector`.

**Q: Can I have multiple CBS connectors?**  
A: Yes! Different instances can use different connectors for different banks.

**Q: Where do I put CBS-specific business logic?**  
A: In your `CBSConnector` implementation. That's the whole point of this separation!

**Q: How do I switch telemetry backends?**  
A: Change configuration to use different `Telemetry` implementation. No code changes needed.

---

**The key principle: Generic code handles OBP protocol, your code handles CBS integration.**