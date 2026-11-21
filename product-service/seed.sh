
#!/bin/bash

set -e

PRODUCTS=${1:-1000}

echo "Seeding catalog with target: $PRODUCTS products"

./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--seed.enabled=true --seed.products=$PRODUCTS"
