// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.model;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Product model:
 * - DynamoDB Enhanced Client mapping works (annotations are correct)
 * - JSON serialization/deserialization works (Gson compatibility)
 * - Lombok-generated methods work
 */
public class ProductTest {

    private final Gson gson = new Gson();

    @Test
    public void dynamoDbTableSchemaCanBeCreated() {
        TableSchema<Product> schema = TableSchema.fromBean(Product.class);
        assertNotNull(schema);
        assertEquals("PK", schema.tableMetadata().primaryPartitionKey());
    }

    @Test
    public void dynamoDbSchemaHasExpectedAttributes() {
        TableSchema<Product> schema = TableSchema.fromBean(Product.class);
        assertTrue(schema.attributeNames().contains("PK"));
        assertTrue(schema.attributeNames().contains("Name"));
        assertTrue(schema.attributeNames().contains("Category"));
        assertTrue(schema.attributeNames().contains("Price"));
        assertTrue(schema.attributeNames().contains("Description"));
        assertTrue(schema.attributeNames().contains("ImageKey"));
    }

    @Test
    public void gsonSerializationRoundTrip() {
        Product original = new Product("id1", "Laptop", "Electronics", 999.99, "A nice laptop", "img/key");

        String json = gson.toJson(original);
        Product deserialized = gson.fromJson(json, Product.class);

        assertEquals(original.getId(), deserialized.getId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getCategory(), deserialized.getCategory());
        assertEquals(original.getPrice(), deserialized.getPrice());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getImageKey(), deserialized.getImageKey());
    }

    @Test
    public void gsonDeserializationFromPartialJson() {
        String json = "{\"name\":\"Widget\",\"price\":9.99}";
        Product product = gson.fromJson(json, Product.class);

        assertNull(product.getId());
        assertEquals("Widget", product.getName());
        assertNull(product.getCategory());
        assertEquals(9.99, product.getPrice());
        assertNull(product.getDescription());
        assertNull(product.getImageKey());
    }

    @Test
    public void lombokGettersAndSetters() {
        Product product = new Product();
        product.setId("test-id");
        product.setName("Test");
        product.setCategory("Cat");
        product.setPrice(42.0);
        product.setDescription("Desc");
        product.setImageKey("key");

        assertEquals("test-id", product.getId());
        assertEquals("Test", product.getName());
        assertEquals("Cat", product.getCategory());
        assertEquals(42.0, product.getPrice());
        assertEquals("Desc", product.getDescription());
        assertEquals("key", product.getImageKey());
    }

    @Test
    public void lombokAllArgsConstructor() {
        Product product = new Product("id", "Name", "Cat", 10.0, "Desc", "Key");

        assertEquals("id", product.getId());
        assertEquals("Name", product.getName());
        assertEquals("Cat", product.getCategory());
        assertEquals(10.0, product.getPrice());
        assertEquals("Desc", product.getDescription());
        assertEquals("Key", product.getImageKey());
    }

    @Test
    public void lombokEqualsAndHashCode() {
        Product p1 = new Product("id1", "Name", "Cat", 10.0, null, null);
        Product p2 = new Product("id1", "Name", "Cat", 10.0, null, null);
        Product p3 = new Product("id2", "Name", "Cat", 10.0, null, null);

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    public void nullPriceIsValid() {
        Product product = new Product("id", "Free", "Cat", null, null, null);
        assertNull(product.getPrice());

        String json = gson.toJson(product);
        assertFalse(json.contains("price"));
    }
}
