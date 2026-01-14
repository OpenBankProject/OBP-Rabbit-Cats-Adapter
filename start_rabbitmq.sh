#!/bin/bash

set -e

echo "======================================"
echo "Starting RabbitMQ in Docker"
echo "======================================"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker is not installed"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo "[ERROR] Docker is not running"
    echo "Please start Docker and try again"
    exit 1
fi

# Check if RabbitMQ container already exists
if docker ps -a --format '{{.Names}}' | grep -q "^rabbitmq$"; then
    echo "[INFO] RabbitMQ container already exists"
    
    # Check if it's running
    if docker ps --format '{{.Names}}' | grep -q "^rabbitmq$"; then
        echo "[OK] RabbitMQ is already running"
        echo ""
        echo "======================================"
        echo "RabbitMQ Information:"
        echo "======================================"
        echo "AMQP Port:       5672"
        echo "Management UI:   http://localhost:15672"
        echo "Username:        guest"
        echo "Password:        guest"
        echo "======================================"
        echo ""
        echo "To stop:   docker stop rabbitmq"
        echo "To remove: docker rm rabbitmq"
        exit 0
    else
        echo "[INFO] Starting existing RabbitMQ container..."
        docker start rabbitmq
        echo "[OK] RabbitMQ started"
    fi
else
    echo "[INFO] Creating new RabbitMQ container..."
    docker run -d \
        --name rabbitmq \
        -p 5672:5672 \
        -p 15672:15672 \
        rabbitmq:3-management
    
    echo "[OK] RabbitMQ container created and started"
fi

echo ""
echo "======================================"
echo "Waiting for RabbitMQ to be ready..."
echo "======================================"

# Wait for RabbitMQ to be ready (max 30 seconds)
for i in {1..30}; do
    if docker exec rabbitmq rabbitmqctl status &> /dev/null; then
        echo "[OK] RabbitMQ is ready!"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 1
done

echo ""
echo "======================================"
echo "RabbitMQ Information:"
echo "======================================"
echo "AMQP Port:       5672"
echo "Management UI:   http://localhost:15672"
echo "Username:        guest"
echo "Password:        guest"
echo "======================================"
echo ""
echo "Container name:  rabbitmq"
echo ""
echo "Commands:"
echo "  View logs:     docker logs -f rabbitmq"
echo "  Stop:          docker stop rabbitmq"
echo "  Start:         docker start rabbitmq"
echo "  Remove:        docker rm -f rabbitmq"
echo ""
echo "[OK] RabbitMQ is ready to use!"