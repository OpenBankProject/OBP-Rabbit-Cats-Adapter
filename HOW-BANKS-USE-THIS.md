# How Banks Use This Adapter

## TL;DR

**DO NOT clone and modify this repository!**

Instead:
1. Use this adapter as a **library dependency** or **base Docker image**
2. Create your **own separate project** with just your CBS connector implementation
3. Keep your bank-specific code in your own repository
4. Get adapter updates without merge conflicts

---

## The Wrong Way ❌

```bash
# DON'T DO THIS:
git clone https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter.git
cd OBP-Rabbit-Cats-Adapter
# Edit files in src/main/scala/com/tesobe/obp/adapter/...
# Now you're stuck - can't easily get updates!
```

**Problems with this approach:**
- Your bank-specific code is mixed with generic code
- Hard to get upstream updates
- Merge conflicts when adapter is updated
- Your CBS credentials in the same repo as generic code
- Can't easily contribute improvements back

---

## The Right Way ✅

### Option 1: Maven/SBT Dependency (Recommended for Scala shops)

#### Step 1: Publish This Adapter as a Library

```bash
# In this adapter repository
mvn clean install
# Or publish to your internal Maven repository
mvn deploy
```

#### Step 2: Create Your Bank's Connector Project

```bash
mkdir my-bank-obp-connector
cd my-bank-obp-connector
```

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.mybank</groupId>
  <artifactId>mybank-obp-connector</artifactId>
  <version>1.0.0</version>

  <dependencies>
    <!-- The OBP Adapter as a dependency -->
    <dependency>
      <groupId>com.tesobe</groupId>
      <artifactId>obp-rabbit-cats-adapter</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
</project>
```

#### Step 3: Implement Your Connector

```scala
// src/main/scala/com/mybank/obp/MyBankConnector.scala
package com.mybank.obp

import com.tesobe.obp.adapter.interfaces._
import com.tesobe.obp.adapter.models._
import cats.effect.IO

class MyBankConnector(
  config: MyBankConfig,
  telemetry: Telemetry
) extends CBSConnector {
  
  override def name: String = "MyBank-Production-Connector"
  override def version: String = "1.0.0"
  
  override def getBank(
    bankId: String, 
    callContext: CallContext
  ): IO[CBSResponse[BankCommons]] = {
    // YOUR code to call YOUR CBS
    ???
  }
  
  // Implement other methods...
}
```

#### Step 4: Create Your Main Class

```scala
// src/main/scala/com/mybank/obp/MyBankAdapterMain.scala
package com.mybank.obp

import cats.effect.{IO, IOApp}
import com.tesobe.obp.adapter.AdapterMain
import com.tesobe.obp.adapter.telemetry.ConsoleTelemetry

object MyBankAdapterMain extends IOApp.Simple {
  
  def run: IO[Unit] = {
    for {
      // Load your bank-specific config
      config <- MyBankConfig.load
      
      // Create telemetry
      telemetry = new ConsoleTelemetry()
      
      // Create YOUR connector
      connector = new MyBankConnector(config, telemetry)
      
      // Run the adapter with YOUR connector
      _ <- AdapterMain.runWithConnector(connector, telemetry)
      
    } yield ()
  }
}
```

#### Step 5: Build and Run

```bash
mvn clean package
java -jar target/mybank-obp-connector.jar
```

---

### Option 2: Docker Base Image (Recommended for Docker shops)

#### Step 1: Publish This Adapter as Base Image

```dockerfile
# In this adapter repository
FROM openjdk:11-jre-slim
COPY target/obp-rabbit-cats-adapter.jar /app/adapter-base.jar
# This image contains all generic adapter code
```

```bash
docker build -t obp-adapter-base:1.0.0 .
docker push your-registry/obp-adapter-base:1.0.0
```

#### Step 2: Create Your Bank's Project

Your bank's repository structure:

```
my-bank-obp-connector/
├── src/main/scala/com/mybank/obp/
│   ├── MyBankConnector.scala          # Your CBS implementation
│   ├── MyBankConfig.scala             # Your config
│   └── MyBankMain.scala               # Your entry point
├── Dockerfile                          # Extends base image
├── pom.xml                            # References adapter as dependency
└── .env                               # Your credentials (not committed!)
```

Your `Dockerfile`:

```dockerfile
FROM obp-adapter-base:1.0.0

# Add your bank-specific connector
COPY target/mybank-connector.jar /app/mybank-connector.jar

# Set classpath to include both
ENV CLASSPATH=/app/adapter-base.jar:/app/mybank-connector.jar

# Run with your main class
CMD ["java", "-cp", "$CLASSPATH", "com.mybank.obp.MyBankMain"]
```

#### Step 3: Deploy

```bash
# Build your bank's image
docker build -t mybank-obp-connector:1.0.0 .

# Run with your config
docker run --env-file .env mybank-obp-connector:1.0.0
```

---

### Option 3: Git Submodule (For active development)

Use this if you're actively contributing to the adapter or need to modify it frequently.

```bash
# Your bank's repository
mkdir my-bank-obp-project
cd my-bank-obp-project
git init

# Add adapter as submodule
git submodule add https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter.git adapter

# Your structure:
my-bank-obp-project/
├── adapter/                           # Git submodule (this adapter)
│   └── src/main/scala/...            # Generic code - don't modify!
├── mybank-connector/                  # Your code
│   └── src/main/scala/com/mybank/
│       ├── MyBankConnector.scala     # Your implementation
│       └── MyBankMain.scala
├── pom.xml                           # Parent POM
└── README.md
```

Parent `pom.xml`:

```xml
<project>
  <groupId>com.mybank</groupId>
  <artifactId>mybank-obp-parent</artifactId>
  
  <modules>
    <module>adapter</module>              <!-- Generic adapter -->
    <module>mybank-connector</module>     <!-- Your connector -->
  </modules>
</project>
```

**Update the adapter:**

```bash
cd adapter
git pull origin main
cd ..
git add adapter
git commit -m "Update adapter to latest version"
```

---

## Repository Structure Examples

### Your Bank's Repository (Separate from Adapter)

```
mybank-obp-connector/                  # YOUR repository
├── src/main/scala/com/mybank/obp/
│   ├── MyBankConnector.scala          # Implements CBSConnector
│   ├── MyBankConfig.scala             # Your config loading
│   ├── MyBankHttpClient.scala         # Your HTTP client setup
│   ├── MyBankAuthHandler.scala        # Your auth logic
│   └── MyBankMain.scala               # Entry point
├── src/test/scala/com/mybank/obp/
│   └── MyBankConnectorSpec.scala      # Your tests
├── pom.xml                            # Depends on obp-adapter
├── Dockerfile                         # Builds your image
├── .env.example                       # Config template
├── .gitignore                         # Excludes .env with secrets
└── README.md                          # Your bank's docs
```

### What You Check Into YOUR Repository

✅ **DO commit:**
- Your `MyBankConnector.scala` implementation
- Your `MyBankMain.scala` entry point
- Your tests
- Your `pom.xml` / `build.sbt`
- Your `Dockerfile`
- `.env.example` (template without secrets)
- Your documentation

❌ **DON'T commit:**
- Generic adapter code (it's a dependency)
- `.env` file with real credentials
- Compiled `.class` files
- `target/` directory

---

## Workflow Examples

### Scenario 1: Implementing a New CBS Operation

```scala
// In YOUR repository: mybank-connector/src/main/scala/com/mybank/obp/MyBankConnector.scala

class MyBankConnector(...) extends CBSConnector {
  
  // Add implementation for new operation
  override def makePayment(
    bankId: String,
    accountId: String,
    paymentData: PaymentData,
    callContext: CallContext
  ): IO[CBSResponse[TransactionCommons]] = {
    
    for {
      // 1. Validate with YOUR business rules
      _ <- validatePayment(paymentData)
      
      // 2. Call YOUR CBS API
      response <- myBankHttpClient.post(
        url = s"${config.cbsUrl}/payments",
        body = mapToMyBankFormat(paymentData),
        headers = myBankAuthHeaders(callContext)
      )
      
      // 3. Map YOUR response to OBP format
      transaction <- IO.pure(mapToOBPTransaction(response))
      
      // 4. Return success
      result = CBSResponse.success(transaction, callContext)
      
    } yield result
  }
  
  // Helper methods specific to YOUR bank
  private def validatePayment(data: PaymentData): IO[Unit] = ???
  private def mapToMyBankFormat(data: PaymentData): MyBankPaymentRequest = ???
  private def mapToOBPTransaction(response: MyBankResponse): TransactionCommons = ???
}
```

**You only modify YOUR repository!**

### Scenario 2: Updating the Adapter

When a new version of the adapter is released:

```bash
# Update dependency in YOUR pom.xml
<dependency>
  <groupId>com.tesobe</groupId>
  <artifactId>obp-rabbit-cats-adapter</artifactId>
  <version>1.1.0</version>  <!-- Updated version -->
</dependency>

# Rebuild
mvn clean package

# Test
mvn test

# Deploy
docker build -t mybank-obp-connector:1.1.0 .
```

**No merge conflicts!** Your code stays separate.

### Scenario 3: Contributing Back

If you improve the generic adapter code:

```bash
# Fork the adapter repository
git clone https://github.com/YourBank/OBP-Rabbit-Cats-Adapter.git

# Make improvements to GENERIC code
# e.g., better error handling in MessageRouter

# Submit pull request to upstream
# Your improvements benefit all banks!
```

---

## Configuration Management

### Development

```bash
# Your repository
cat > .env <<EOF
RABBITMQ_HOST=localhost
CBS_BASE_URL=http://localhost:8080/mock-cbs
CBS_AUTH_TYPE=none
TELEMETRY_TYPE=console
EOF

java -jar target/mybank-obp-connector.jar
```

### Production (Kubernetes)

```yaml
# mybank-connector-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mybank-obp-connector
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: connector
        image: mybank-obp-connector:1.0.0
        env:
        - name: RABBITMQ_HOST
          valueFrom:
            configMapKeyRef:
              name: obp-config
              key: rabbitmq.host
        - name: CBS_BEARER_TOKEN
          valueFrom:
            secretKeyRef:
              name: mybank-cbs-credentials
              key: api-token
```

---

## Benefits of This Approach

### For Your Bank 🏦

✅ **Clean separation** - Your code in your repo, generic code in its repo  
✅ **Easy updates** - Update adapter version in one line  
✅ **No merge conflicts** - Your code never conflicts with adapter updates  
✅ **Secure** - Your credentials stay in your private repo  
✅ **Testable** - Test your connector independently  
✅ **Maintainable** - Clear boundary between generic and bank-specific code  

### For OBP Project 🌐

✅ **Clean contributions** - Banks can contribute generic improvements  
✅ **Reusable core** - One adapter, many banks  
✅ **Version management** - Banks can stay on stable versions  
✅ **Quality** - Generic code is well-tested once  

---

## Quick Start Guide

### Step 1: Set Up Your Project

```bash
mkdir mybank-obp-connector
cd mybank-obp-connector
git init

# Create Maven project
cat > pom.xml <<EOF
<project>
  <groupId>com.mybank</groupId>
  <artifactId>mybank-obp-connector</artifactId>
  <version>1.0.0</version>
  
  <dependencies>
    <dependency>
      <groupId>com.tesobe</groupId>
      <artifactId>obp-rabbit-cats-adapter</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
</project>
EOF
```

### Step 2: Create Your Connector

```bash
mkdir -p src/main/scala/com/mybank/obp
cat > src/main/scala/com/mybank/obp/MyBankConnector.scala <<'EOF'
package com.mybank.obp

import com.tesobe.obp.adapter.interfaces._
import com.tesobe.obp.adapter.models._
import cats.effect.IO

class MyBankConnector extends CBSConnector {
  override def name = "MyBank-Connector"
  override def version = "1.0.0"
  
  override def getBank(bankId: String, ctx: CallContext) = {
    // TODO: Implement
    IO.pure(CBSResponse.error("NOT_IMPLEMENTED", "TODO", ctx))
  }
  
  // Implement other methods...
}
EOF
```

### Step 3: Build and Run

```bash
mvn clean package
java -jar target/mybank-obp-connector.jar
```

---

## Summary

| Approach | Use Case | Pros | Cons |
|----------|----------|------|------|
| **Maven Dependency** | Production | Clean, easy updates | Need Maven repo |
| **Docker Base Image** | Production | Containerized, isolated | Need Docker registry |
| **Git Submodule** | Active development | Flexible, can modify adapter | More complex |

**Recommended: Maven Dependency or Docker Base Image**

Your bank-specific code lives in **your repository**.  
The generic adapter is a **dependency**.  
Never mix the two!

---

## Questions?

**Q: Where do I put my CBS-specific models?**  
A: In your repository, e.g., `com.mybank.obp.models.MyBankPayment`

**Q: Can I override generic adapter behavior?**  
A: Generally no need. If you do, consider if it should be configurable in the adapter instead.

**Q: How do I test my connector?**  
A: Unit test it in isolation using mock HTTP clients. See your repo's test directory.

**Q: What if I need a feature in the generic adapter?**  
A: Open an issue or PR in the adapter repository. Benefits all banks!

**Q: How do I handle multiple CBS backends?**  
A: Create multiple connector implementations in your repo, configure which to use via environment variable.