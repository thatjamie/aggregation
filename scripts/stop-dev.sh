#!/bin/bash

# Stop development environment

echo "Stopping Aggregation Service development environment..."
docker-compose down

echo "Development environment stopped."
echo "To remove volumes as well, run: docker-compose down -v"
