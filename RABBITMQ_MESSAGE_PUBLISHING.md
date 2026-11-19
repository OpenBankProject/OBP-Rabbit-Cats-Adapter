# RabbitMQ Connector Message Publishing in OBP-API

## Overview

This document explains how the OBP-API RabbitMQ connector publishes messages to communicate with external adapters. The RabbitMQ connector enables OBP-API to communicate with Core Banking Systems (CBS) using message queues.

## Architecture

```
OBP API (Core) → RabbitMQ Connector → RabbitMQ Server → Adapter → Core Banking System
                      ↓                                      ↓
                  Outbound Message                    Inbound Response
```

## Key Components

### 1. RabbitMQ Connector (`RabbitMQConnector_vOct2024.scala`)

The main connector trait that implements the `Connector` interface. Each method follows this pattern:

```scala
override def getAdapterInfo(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
  import com.openbankproject.commons.dto.{InBoundGetAdapterInfo => InBound, OutBoundGetAdapterInfo => OutBound}  
  val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull)
  val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_adapter_info", req, callContext)
  response.map(convertToTuple[InboundAdapterInfoInternal](callContext))        
}
```

**Pattern Explanation:**
1. Import the specific InBound/OutBound DTOs for this operation
2. Create an OutBound request object with call context and parameters
3. Call `sendRequest` with:
   - Process name (e.g., "obp_get_adapter_info")
   - The OutBound request object
   - The call context
4. Convert the response to the expected return type

### 2. Send Request Method

Located in `RabbitMQConnector_vOct2024.scala` (line ~7385):

```scala
private[this] def sendRequest[T <: InBoundTrait[_]: TypeTag : Manifest](
  process: String, 
  outBound: TopicTrait, 
  callContext: Option[CallContext]
): Future[Box[T]] = {
  // Convert accountId to accountReference and customerId to customerReference
  Helper.convertToReference(outBound)
  
  RabbitMQUtils
    .sendRequestUndGetResponseFromRabbitMQ[T](process, outBound)
    .map(Helper.convertToId(_))
    .recoverWith {
      case e: Exception => Future(Failure(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}"))
    }
}
```

**Key Features:**
- Converts IDs to references before sending
- Delegates to `RabbitMQUtils` for actual message transport
- Converts references back to IDs in the response
- Handles exceptions and wraps them in proper error messages

### 3. RabbitMQ Utils (`RabbitMQUtils.scala`)

The core message publishing logic:

```scala
def sendRequestUndGetResponseFromRabbitMQ[T: Manifest](
  messageId: String, 
  outBound: TopicTrait
): Future[Box[T]] = {

  val rabbitRequestJsonString: String = write(outBound) // Serialize to JSON
  
  val connection = RabbitMQConnectionPool.borrowConnection()
  val channel = connection.createChannel()
  
  // Declare RPC request queue
  channel.queueDeclare(
    RPC_QUEUE_NAME,     // "obp_rpc_queue"
    true,               // durable
    false,              // exclusive
    false,              // autoDelete
    rpcQueueArgs        // TTL: 60 seconds
  )

  // Declare unique reply queue for this request
  val replyQueueName = channel.queueDeclare(
    s"${RPC_REPLY_TO_QUEUE_NAME_PREFIX}_${messageId.replace("obp_","")}_${UUID.randomUUID}",
    false,              // non-durable
    true,               // exclusive
    true,               // autoDelete
    rpcReplyToQueueArgs // TTL: 60 seconds, expires: 60 seconds
  ).getQueue

  // Publish message with correlation ID and reply-to queue
  val rabbitMQCorrelationId = UUID.randomUUID().toString
  val rabbitMQProps = new BasicProperties.Builder()
    .messageId(messageId)
    .contentType("application/json")
    .correlationId(rabbitMQCorrelationId)
    .replyTo(replyQueueName)
    .build()
    
  channel.basicPublish("", RPC_QUEUE_NAME, rabbitMQProps, rabbitRequestJsonString.getBytes("UTF-8"))

  // Set up consumer for response
  val responseCallback = new ResponseCallback(rabbitMQCorrelationId, channel)
  channel.basicConsume(replyQueueName, true, responseCallback, cancelCallback)
  
  // Return future that completes when response arrives
  responseCallback.take()
}
```

### 4. Connection Pool (`RabbitMQConnectionPool.scala`)

Manages RabbitMQ connections efficiently:

```scala
object RabbitMQConnectionPool {
  private val poolConfig = new GenericObjectPoolConfig[Connection]()
  poolConfig.setMaxTotal(5)           // Maximum connections
  poolConfig.setMinIdle(2)            // Minimum idle connections
  poolConfig.setMaxIdle(5)            // Maximum idle connections
  poolConfig.setMaxWaitMillis(30000)  // 30 second wait time

  private val pool = new GenericObjectPool[Connection](new RabbitMQConnectionFactory(), poolConfig)

  def borrowConnection(): Connection = pool.borrowObject()
  def returnConnection(conn: Connection): Unit = pool.returnObject(conn)
}
```

**Configuration (from props):**
- `rabbitmq_connector.host` - RabbitMQ server hostname
- `rabbitmq_connector.port` - RabbitMQ server port
- `rabbitmq_connector.username` - Authentication username
- `rabbitmq_connector.password` - Authentication password
- `rabbitmq_connector.virtual_host` - Virtual host name
- `rabbitmq.use.ssl` - Enable SSL/TLS (optional)

## Message Flow

### 1. Request Flow

```
API Endpoint
  ↓
Connector Method (e.g., getAdapterInfo)
  ↓
sendRequest[InBound]("obp_get_adapter_info", OutBound(...), callContext)
  ↓
Helper.convertToReference(outBound)  // Convert IDs to references
  ↓
RabbitMQUtils.sendRequestUndGetResponseFromRabbitMQ
  ↓
Serialize OutBound to JSON
  ↓
Borrow connection from pool
  ↓
Create channel
  ↓
Declare RPC queue: "obp_rpc_queue"
  ↓
Declare unique reply queue: "obp_reply_queue_{method}_{uuid}"
  ↓
Publish message with:
  - Message ID: "obp_get_adapter_info"
  - Correlation ID: UUID
  - Reply-To: reply queue name
  - Body: JSON serialized OutBound
  ↓
Set up consumer on reply queue
  ↓
Return Future[Box[T]]
```

### 2. Response Flow

```
Adapter publishes response to reply queue
  ↓
ResponseCallback.handle() triggered
  ↓
Check correlation ID matches
  ↓
Extract JSON response from message body
  ↓
Close channel
  ↓
Complete Promise with response
  ↓
Future resolves
  ↓
Connector.extractAdapterResponse[T](jsonString)
  ↓
Helper.convertToId(_)  // Convert references back to IDs
  ↓
Return to API endpoint
```

## Message Structure

### Outbound Message (OBP → Adapter)

```json
{
  "outboundAdapterCallContext": {
    "correlationId": "uuid-v4",
    "sessionId": "session-uuid",
    "consumerId": "consumer-id",
    "generalContext": [...],
    "outboundAdapterAuthInfo": {
      "userId": "user-id",
      "username": "username",
      "linkedCustomers": [...],
      "userAuthContext": [...],
      "authViews": [...]
    }
  },
  // Method-specific parameters...
  "iban": "DE89370400440532013000"
}
```

### Inbound Message (Adapter → OBP)

```json
{
  "inboundAdapterCallContext": {
    "correlationId": "uuid-v4",
    "sessionId": "session-uuid",
    "generalContext": [...]
  },
  "status": {
    "errorCode": "",
    "backendMessages": []
  },
  "data": {
    // Method-specific response data...
  }
}
```

## Queue Configuration

### RPC Request Queue
- **Name**: `obp_rpc_queue`
- **Durable**: true (survives broker restart)
- **Exclusive**: false (shared across connections)
- **Auto-Delete**: false (persists when no consumers)
- **TTL**: 60,000 ms (messages expire after 60 seconds)

### RPC Reply Queue
- **Name**: `obp_reply_queue_{method}_{uuid}` (unique per request)
- **Durable**: false (non-persistent)
- **Exclusive**: true (deleted when connection closes)
- **Auto-Delete**: true (deleted when consumer is cancelled)
- **TTL**: 60,000 ms (messages expire after 60 seconds)
- **Expires**: 60,000 ms (queue deleted after 60 seconds of inactivity)

## Message Properties

Published messages include these AMQP properties:

- **messageId**: Process name (e.g., "obp_get_adapter_info")
- **contentType**: "application/json"
- **correlationId**: UUID for request/response matching
- **replyTo**: Name of the reply queue

## Error Handling

### Connection Errors
```scala
factory.setHost(host)
factory.setPort(port)
factory.setUsername(username)
factory.setPassword(password)
// If connection fails, exception is thrown during borrowConnection()
```

### Publishing Errors
```scala
sendRequest.recoverWith {
  case e: Exception => 
    Future(Failure(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}"))
}
```

### Timeout Handling
- Message TTL: 60 seconds
- Queue expiry: 60 seconds
- Connection pool wait: 30 seconds

## SSL/TLS Support

Enable SSL with these properties:
```properties
rabbitmq.use.ssl=true
keystore.path=/path/to/keystore.jks
keystore.password=password
truststore.path=/path/to/truststore.jks
truststore.password=password
```

SSL Configuration:
```scala
if (APIUtil.getPropsAsBoolValue("rabbitmq.use.ssl", false)) {
  factory.useSslProtocol(RabbitMQUtils.createSSLContext(
    keystorePath,
    keystorePassword,
    truststorePath,
    truststorePassword
  ))
}
```

## Performance Considerations

1. **Connection Pooling**: Reuses connections (max 5, min idle 2)
2. **Channel Creation**: New channel per request (channels are not thread-safe)
3. **Exclusive Reply Queues**: Each request gets its own reply queue
4. **Auto-Cleanup**: Reply queues auto-delete after 60 seconds
5. **JSON Serialization**: Uses Lift-JSON with custom null-tolerant formats

## Example: Complete Flow

```scala
// 1. API calls connector method
connector.getBank(bankId, callContext)

// 2. Connector creates outbound message
val req = OutBoundGetBank(callContext.toOutboundAdapterCallContext, bankId)

// 3. Send via RabbitMQ
val response = sendRequest[InBoundGetBank]("obp_get_bank", req, callContext)

// 4. Message published to "obp_rpc_queue" with properties:
//    - messageId: "obp_get_bank"
//    - correlationId: "550e8400-e29b-41d4-a716-446655440000"
//    - replyTo: "obp_reply_queue_get_bank_123abc..."
//    - body: {"outboundAdapterCallContext":{...},"bankId":"bank-id"}

// 5. Adapter processes and responds to reply queue

// 6. Response consumed and deserialized:
//    {"inboundAdapterCallContext":{...},"status":{...},"data":{...}}

// 7. Future completes with Box[InBoundGetBank]

// 8. Converted to Box[(BankCommons, Option[CallContext])]

// 9. Returned to API endpoint
```

## Message Documentation

Each connector method has associated message documentation:

```scala
messageDocs += MessageDoc(
  process = "obp.getAdapterInfo",
  messageFormat = "rabbitmq_vOct2024",
  description = "Get Adapter Info",
  outboundTopic = None,
  inboundTopic = None,
  exampleOutboundMessage = OutBoundGetAdapterInfo(...),
  exampleInboundMessage = InBoundGetAdapterInfo(...),
  adapterImplementation = Some(AdapterImplementation("- Core", 1))
)
```

These are exposed via:
- `GET /obp/v6.0.0/message-docs/rabbitmq_vOct2024` - JSON format
- `GET /obp/v6.0.0/message-docs/rabbitmq_vOct2024/swagger2.0` - Swagger format
- `GET /obp/v6.0.0/message-docs/rabbitmq_vOct2024/json-schema` - JSON Schema format

## Key Files

- **Connector**: `obp-api/src/main/scala/code/bankconnectors/rabbitmq/RabbitMQConnector_vOct2024.scala`
- **Utils**: `obp-api/src/main/scala/code/bankconnectors/rabbitmq/RabbitMQUtils.scala`
- **Connection Pool**: `obp-api/src/main/scala/code/bankconnectors/rabbitmq/RabbitMQConnectionPool.scala`
- **DTOs**: `obp-commons/src/main/scala/com/openbankproject/commons/dto/` (various files)

## Debugging

Enable debug logging:
```properties
logger.code.bankconnectors.rabbitmq=DEBUG
```

This will log:
- Outbound JSON messages
- Inbound JSON responses
- Connection events
- Queue operations

## References

- RabbitMQ RPC Tutorial: https://www.rabbitmq.com/tutorials/tutorial-six-java.html
- AMQP 0-9-1 Protocol: https://www.rabbitmq.com/tutorials/amqp-concepts.html
- Apache Commons Pool: https://commons.apache.org/proper/commons-pool/
