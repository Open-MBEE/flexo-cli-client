#!/bin/bash
# Demo script for multiple remote origins functionality
# This script demonstrates the new remote management commands

set -e

echo "=== Flexo CLI Multiple Remote Origins Demo ==="
echo ""

# Note: This assumes you have built the CLI with ./gradlew installDist
FLEXO_CMD="./build/install/flexo/bin/flexo"

if [ ! -f "$FLEXO_CMD" ]; then
    echo "Error: Flexo CLI not found at $FLEXO_CMD"
    echo "Please build first with: ./gradlew installDist"
    exit 1
fi

echo "1. Listing current remotes (should be empty initially)"
$FLEXO_CMD remote list || echo "No remotes configured yet"
echo ""

echo "2. Adding a local development remote"
$FLEXO_CMD remote add local http://localhost:8080 \
    --local-mode true \
    --local-user root \
    --local-jwt-secret dev-secret \
    --set-default
echo ""

echo "3. Adding a staging remote"
$FLEXO_CMD remote add staging http://staging.example.com:8080 \
    --local-mode false \
    --ssh-key ~/.ssh/id_rsa_staging
echo ""

echo "4. Adding a production remote with SSH authentication"
$FLEXO_CMD remote add production https://mms.example.com \
    --local-mode false \
    --ssh-key ~/.ssh/id_rsa_production
echo ""

echo "5. Listing all configured remotes"
$FLEXO_CMD remote list
echo ""

echo "6. Showing details of 'production' remote"
$FLEXO_CMD remote show production
echo ""

echo "7. Updating staging remote URL"
$FLEXO_CMD remote set-url staging http://new-staging.example.com:8080
echo ""

echo "8. Renaming 'staging' to 'stage'"
$FLEXO_CMD remote rename staging stage
echo ""

echo "9. Final remote list"
$FLEXO_CMD remote list
echo ""

echo "=== Example Usage with Remotes ==="
echo ""
echo "Pull from default (local) remote:"
echo "  $FLEXO_CMD pull master --output local-model.ttl"
echo ""
echo "Pull from production remote:"
echo "  $FLEXO_CMD --remote production pull master --output prod-model.ttl"
echo ""
echo "Push to staging remote:"
echo "  $FLEXO_CMD --remote stage push master --message 'Deploy to stage' --input model.ttl"
echo ""
echo "List branches on production:"
echo "  $FLEXO_CMD --remote production branch --list"
echo ""

echo "=== Cleanup (removing test remotes) ==="
echo "To remove remotes, use:"
echo "  $FLEXO_CMD remote remove stage"
echo "  $FLEXO_CMD remote remove production"
echo ""

echo "Demo complete! Check ~/.flexo/config to see the stored configuration."
