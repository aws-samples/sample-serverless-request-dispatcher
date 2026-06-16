#!/bin/bash

# Test script for the Product API
# Usage: ./test-api.sh [BASE_URL]
# Example: ./test-api.sh http://localhost:3000

BASE_URL=${1:-"http://localhost:3000"}
API_PATH="/api/products"

echo "Testing Product API at: ${BASE_URL}${API_PATH}"
echo "================================================"

# Test 1: List all products
echo -e "\n1. GET /api/products - List all products"
curl -s "${BASE_URL}${API_PATH}" | jq '.'

# Test 2: Get specific product
echo -e "\n2. GET /api/products/1 - Get product by ID"
curl -s "${BASE_URL}${API_PATH}/1" | jq '.'

# Test 3: Filter by category
echo -e "\n3. GET /api/products?category=Electronics - Filter by category"
curl -s "${BASE_URL}${API_PATH}?category=Electronics" | jq '.'

# Test 4: Create new product
echo -e "\n4. POST /api/products - Create new product"
NEW_PRODUCT=$(curl -s -X POST "${BASE_URL}${API_PATH}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Keyboard",
    "category": "Electronics",
    "price": 79.99,
    "description": "Bluetooth mechanical keyboard"
  }')
echo "$NEW_PRODUCT" | jq '.'
PRODUCT_ID=$(echo "$NEW_PRODUCT" | jq -r '.id')

# Test 5: Get the newly created product
echo -e "\n5. GET /api/products/${PRODUCT_ID} - Get newly created product"
curl -s "${BASE_URL}${API_PATH}/${PRODUCT_ID}" | jq '.'

# Test 6: Update product
echo -e "\n6. PUT /api/products/${PRODUCT_ID} - Update product"
curl -s -X PUT "${BASE_URL}${API_PATH}/${PRODUCT_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Premium Wireless Keyboard",
    "category": "Electronics",
    "price": 99.99,
    "description": "Premium Bluetooth mechanical keyboard with RGB"
  }' | jq '.'

# Test 7: Delete product
echo -e "\n7. DELETE /api/products/${PRODUCT_ID} - Delete product"
curl -s -X DELETE "${BASE_URL}${API_PATH}/${PRODUCT_ID}" | jq '.'

# Test 8: Verify deletion
echo -e "\n8. GET /api/products/${PRODUCT_ID} - Verify deletion (should fail)"
curl -s "${BASE_URL}${API_PATH}/${PRODUCT_ID}"

echo -e "\n\n================================================"
echo "Testing complete!"
