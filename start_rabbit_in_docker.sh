#!/bin/bash

set -e

echo "======================================"
echo "Starting RabbitMQ in Docker"
echo "======================================"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    echo "   Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo "❌ Docker daemon is not running"
    echo "   Please start Docker and try again"
    exit 1
fi

# Check if rabbitmq container already exists
if docker ps -a --format '{{.Names}}' | grep -q "^rabbitmq$"; then
    echo "⚠️  Container 'rabbitmq' already exists"
    
    # Check if it's running
    if docker ps --format '{{.Names}}' | grep -q "^rabbitmq$"; then
        echo "✅ RabbitMQ is already running"
        echo ""
        echo "Management UI: http://localhost:15672"
        echo "Username: guest"
        echo "Password: guest"
        echo ""
        echo "To stop:    docker stop rabbitmq"
        echo "To restart: docker restart rabbitmq"
        echo "To remove:  docker rm -f rabbitmq"
        exit 0
    else
        echo "🔄 Starting existing container..."
        docker start rabbitmq
        echo "✅ RabbitMQ started"
        echo ""
        echo "Management UI: http://localhost:15672"
        echo "Username: guest"
        echo "Password: guest"
        exit 0
    fi
fi

# Start new RabbitMQ container
echo "🐰 Starting RabbitMQ container..."
echo ""

sudo docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ RabbitMQ started successfully!"
    echo ""
    echo "======================================"
    echo "Connection Details:"
    echo "======================================"
    echo "AMQP Port:      5672 (for adapter)"
    echo "Management UI:  http://localhost:15672"
    echo "Username:       guest"
    echo "Password:       guest"
    echo "======================================"
    echo ""
    echo "⏳ Waiting for RabbitMQ to be ready..."
    sleep 5
    
    # Check if it's actually running
    if docker ps | grep -q rabbitmq; then
        echo "✅ RabbitMQ is running"
        echo ""
        echo "Useful commands:"
        echo "  View logs:   docker logs -f rabbitmq"
        echo "  Stop:        docker stop rabbitmq"
        echo "  Restart:     docker restart rabbitmq"
        echo "  Remove:      docker rm -f rabbitmq"
    else
        echo "⚠️  Container started but may not be running properly"
        echo "   Check logs: docker logs rabbitmq"
    fi
else
    echo "❌ Failed to start RabbitMQ"
    exit 1
fi
