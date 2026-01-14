# JSON-Based Approach - Simplified Architecture

## Overview

This adapter uses a **JSON pass-through approach** that directly matches the OBP Message Docs format. No intermediate type modeling required!

## Why JSON?

The OBP Message Docs already define the complete message format with all possible fields. Creating additional typed models would be:
- ❌ Redundant (duplicating what's already in message docs)
- ❌ Rigid (hard to extend when OBP adds fields)
- ❌ More code to maintain
- ❌ Extra mapping layers

Instead, we work directly with JSON:
- ✅ Matches message docs exactly
- ✅ Flexible - handles any field from message docs
- ✅ Less code
- ✅ Easy to extend

---

## Message Flow

```
┌─────────────────────────────────────────────────────────────┐
│                        OBP-API                              │
└─────────────────────┬───────────────────────────────────────┘
                      │ Sends JSON message to RabbitMQ
                      ▼
              ┌───────────────────┐
              │  OutboundMessage  │
              ├───────────────────┤
              │ messageType       │ ← "obp.getBank"
              │ callContext       │ ← Correlation ID, auth info
              │ data: JsonObject  │ ← {"bankId": "gh.29.uk"}
              └─────────┬─────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    YOUR CBS CONNECTOR                       │
│                                                             │
│  def handleMessage(                                         │
│    messageType: String,     ← "obp.getBank"                │
│    data: JsonObject,        ← {"bankId": "gh.29.uk"}       │
│    callContext: CallContext                                 │
│  ): IO[CBSResponse]                                         │
│                                                             │
│  You extract what you need, call your CBS,                 │
│  return JSON matching message docs format                  │
└─────────────────────┬───────────────────────────────────────┘
                      │ Returns JSON response
                      ▼
              ┌───────────────────┐
              │  InboundMessage   │
              ├───────────────────┤
              │ callContext       │ ← Correlation ID
              │ status            │ ← Error code (empty = success)
              │ data: JsonObject  │ ← Response from your CBS
              └─────────┬─────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                        OBP-API                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Data Models (Minimal!)

We only model the **message envelope** (the structure), not the payloads:

```scala
// What we receive from OBP
case class OutboundMessage(
  messageType: String,                      // "obp.getBank"
  outboundAdapterCallContext: OutboundAdapterCallContext,
  data: JsonObject                          // Payload - any JSON from message docs
)

// What we send back to OBP
case class InboundMessage(
  inboundAdapterCallContext: InboundAdapterCallContext,
  status: Status,
  data: Option[JsonObject]                  // Response - any JSON from message docs
)

// Call context with essential info
case class CallContext(
  correlationId: String,
  sessionId: String,
  userId: Option[String],
  username: Option[String],
  consumerId: Option[String],
  generalContext: Map[String, String]
)
```

**That's it!** Everything else is JSON.

---

## Your CBS Connector Interface

```scala
trait CBSConnector {
  def name: String
  def version: String
  
  // Main handler - route all messages through here
  def handleMessage(
    messageType: String,
    data: JsonObject,
    callContext: CallContext
  ): IO[CBSResponse]
  
  // Health checks
  def checkHealth(callContext: CallContext): IO[CBSResponse]
  def getAdapterInfo(callContext: CallContext): IO[CBSResponse]
}

// Response is either Success with JSON or Error
sealed trait CBSResponse
case class Success(data: JsonObject, backendMessages: List[BackendMessage]) extends CBSResponse
case class Error(errorCode: String, errorMessage: String, backendMessages: List[BackendMessage]) extends CBSResponse
```

---

## Example: Implementing getBank

### Step 1: OBP sends this message

```json
{
  "messageType": "obp.getBank",
  "outboundAdapterCallContext": {
    "correlationId": "abc123",
    "sessionId": "session-xyz",
    ...
  },
  "data": {
    "bankId": "gh.29.uk"
  }
}
```

### Step 2: Your connector receives

```scala
def handleMessage(
  messageType: String,      // "obp.getBank"
  data: JsonObject,          // {"bankId": "gh.29.uk"}
  callContext: CallContext
): IO[CBSResponse] = {
  
  messageType match {
    case "obp.getBank" => getBank(data, callContext)
    case "obp.getBankAccount" => getBankAccount(data, callContext)
    case _ => IO.pure(CBSResponse.error("NOT_IMPLEMENTED", s"Unknown: $messageType"))
  }
}
```

### Step 3: You extract fields and call YOUR CBS

```scala
private def getBank(data: JsonObject, callContext: CallContext): IO[CBSResponse] = {
  // 1. Extract what you need from JSON
  val bankId = data("bankId").flatMap(_.asString).getOrElse("unknown")
  
  // 2. Call YOUR CBS API (your protocol, your format)
  yourCBSClient.get(s"https://your-cbs.com/api/banks/$bankId")
    .map { cbsResponse =>
      // 3. Map YOUR response to OBP message docs format (as JSON)
      val obpResponseData = JsonObject(
        "bankId" -> Json.fromString(cbsResponse.id),
        "shortName" -> Json.fromString(cbsResponse.name),
        "fullName" -> Json.fromString(cbsResponse.full_name),
        "logoUrl" -> Json.fromString(cbsResponse.logo),
        "websiteUrl" -> Json.fromString(cbsResponse.website)
      )
      
      // 4. Return success with JSON
      CBSResponse.success(obpResponseData)
    }
    .handleErrorWith { error =>
      // 5. Handle errors
      IO.pure(CBSResponse.error("BANK_NOT_FOUND", error.getMessage))
    }
}
```

### Step 4: Adapter wraps and sends back

The generic adapter wraps your JSON in InboundMessage and sends to RabbitMQ:

```json
{
  "inboundAdapterCallContext": {
    "correlationId": "abc123",
    "sessionId": "session-xyz"
  },
  "status": {
    "errorCode": "",
    "backendMessages": []
  },
  "data": {
    "bankId": "gh.29.uk",
    "shortName": "Mock Bank",
    "fullName": "Mock Bank for Testing",
    "logoUrl": "https://example.com/logo.png",
    "websiteUrl": "https://www.example.com"
  }
}
```

---

## Working with JSON in Scala (Circe)

### Extracting fields

```scala
// Get string field
val bankId = data("bankId").flatMap(_.asString).getOrElse("default")

// Get number field
val amount = data("amount").flatMap(_.asNumber).flatMap(_.toBigDecimal).getOrElse(BigDecimal(0))

// Get boolean field
val available = data("available").flatMap(_.asBoolean).getOrElse(false)

// Get nested object
val balance = data("balance").flatMap(_.asObject)
val currency = balance.flatMap(_("currency")).flatMap(_.asString)

// Get array
val transactions = data("transactions").flatMap(_.asArray).getOrElse(Vector.empty)
```

### Building JSON responses

```scala
import io.circe._
import io.circe.syntax._

// Simple object
val response = JsonObject(
  "bankId" -> Json.fromString("gh.29.uk"),
  "shortName" -> Json.fromString("Bank"),
  "balance" -> Json.fromBigDecimal(1000.50)
)

// With nested objects
val response = JsonObject(
  "accountId" -> Json.fromString("acc-123"),
  "balance" -> Json.obj(
    "currency" -> Json.fromString("EUR"),
    "amount" -> Json.fromString("1000.50")
  )
)

// With arrays
val response = JsonObject(
  "transactions" -> Json.arr(
    Json.obj("id" -> Json.fromString("tx-1")),
    Json.obj("id" -> Json.fromString("tx-2"))
  )
)
```

---

## Mock Connector Example

See `MockCBSConnector.scala` for a complete example:

```scala
class MockCBSConnector(telemetry: Telemetry) extends CBSConnector {
  
  override def name = "Mock-CBS-Connector"
  override def version = "1.0.0"
  
  override def handleMessage(
    messageType: String,
    data: JsonObject,
    callContext: CallContext
  ): IO[CBSResponse] = {
    messageType match {
      case "obp.getBank" => getBank(data, callContext)
      case "obp.getBankAccount" => getBankAccount(data, callContext)
      case _ => handleUnsupported(messageType, callContext)
    }
  }
  
  private def getBank(data: JsonObject, ctx: CallContext): IO[CBSResponse] = {
    val bankId = data("bankId").flatMap(_.asString).getOrElse("unknown")
    
    IO.pure(CBSResponse.success(
      JsonObject(
        "bankId" -> Json.fromString(bankId),
        "shortName" -> Json.fromString("Mock Bank"),
        "fullName" -> Json.fromString("Mock Bank for Testing"),
        "logoUrl" -> Json.fromString("https://example.com/logo.png"),
        "websiteUrl" -> Json.fromString("https://www.example.com")
      )
    ))
  }
}
```

---

## Benefits

### For Bank Developers

✅ **No type modeling** - Work directly with JSON from message docs  
✅ **Flexible** - Handle any field OBP sends  
✅ **Simple** - Just extract → call CBS → build JSON → return  
✅ **Clear** - JSON structure matches message docs exactly  
✅ **Extensible** - OBP adds fields? No code changes needed  

### For Maintenance

✅ **Less code** - No intermediate models to maintain  
✅ **Single source of truth** - OBP Message Docs  
✅ **Easy debugging** - See exact JSON at each step  
✅ **Type-safe where it matters** - Message envelope is typed, payloads are flexible  

---

## Message Docs Reference

All message formats are documented at:
```
https://your-obp-api/obp/v6.0.0/message-docs/rabbitmq_vOct2024
```

Or via API:
```bash
curl https://your-obp-api/obp/v6.0.0/message-docs/rest_vMar2019
```

Each message type shows:
- `example_outbound_message` - What OBP sends you
- `example_inbound_message` - What you should return
- `description` - What the message does
- `process` - The message type identifier

---

## Comparison: Typed vs JSON Approach

### ❌ Typed Approach (Too Complex)

```scala
// Need to define models for everything
case class BankCommons(bankId: String, shortName: String, ...)
case class AccountCommons(accountId: String, ...)
case class TransactionCommons(...)
case class CustomerCommons(...)
// ... 50+ more models

trait CBSConnector {
  def getBank(...): IO[CBSResponse[BankCommons]]
  def getBankAccount(...): IO[CBSResponse[AccountCommons]]
  // ... 50+ methods
}

// Then map JSON → Models → JSON again
```

**Problems:**
- 50+ case classes to maintain
- Rigid structure
- OBP adds field? Need to update models
- Extra mapping layers
- 10x more code

### ✅ JSON Approach (Simple)

```scala
// Only model the envelope
case class OutboundMessage(messageType: String, data: JsonObject, ...)
case class InboundMessage(data: Option[JsonObject], ...)

trait CBSConnector {
  def handleMessage(messageType: String, data: JsonObject, ...): IO[CBSResponse]
}

// Work directly with JSON from message docs
```

**Benefits:**
- 3 case classes total
- Flexible structure
- OBP adds field? Already works
- No extra mapping
- 10x less code

---

## Summary

**Key Insight**: The OBP Message Docs already define the complete data format. We don't need to redefine it in Scala types. Just work with JSON directly!

**Your Job**:
1. Receive `JsonObject` from message
2. Extract fields you need
3. Call your CBS
4. Build `JsonObject` response matching message docs
5. Return it

**The Adapter Handles**:
- RabbitMQ connection
- Message envelope parsing
- Routing by message type
- Wrapping your response
- Sending back to OBP
- Telemetry

**You Handle**:
- Extracting fields from JSON
- Calling your CBS API
- Mapping your CBS response to JSON
- Error handling

Clean separation, minimal models, maximum flexibility! 🎯