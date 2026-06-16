// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.requestdispatcher.annotation;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.Headers;

/**
 * A simple controller used by unit tests.
 */
public class TestController {

    @GetMapping("/items")
    public String listItems() {
        return "[\"item1\",\"item2\"]";
    }

    @GetMapping("/items/{id}")
    public String getItem(@PathVariable("id") String id) {
        return "{\"id\":\"" + id + "\"}";
    }

    @PostMapping("/items")
    public String createItem(String body) {
        return "{\"created\":" + body + "}";
    }

    @PutMapping("/items/{id}")
    public String updateItem(@PathVariable("id") String id, String body) {
        return "{\"id\":\"" + id + "\",\"updated\":" + body + "}";
    }

    @DeleteMapping("/items/{id}")
    public String deleteItem(@PathVariable("id") String id) {
        return "{\"deleted\":\"" + id + "\"}";
    }

    @PatchMapping("/items/{id}")
    public String patchItem(@PathVariable("id") String id, String body) {
        return "{\"id\":\"" + id + "\",\"patched\":" + body + "}";
    }

    @GetMapping("/search")
    public String search(@RequestParam("q") String query) {
        return "{\"query\":\"" + query + "\"}";
    }

    @GetMapping("/echo")
    public String echo(AwsProxyRequest request) {
        return request.getHttpMethod() + " " + request.getResource();
    }

    @GetMapping("/headers")
    public String readHeaders(Headers headers) {
        return headers != null ? headers.toString() : "null";
    }
}
