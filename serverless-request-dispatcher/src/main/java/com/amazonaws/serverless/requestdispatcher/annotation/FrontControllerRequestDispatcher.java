// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.requestdispatcher.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.Headers;
import com.amazonaws.serverless.proxy.model.MultiValuedTreeMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.val;

public abstract class FrontControllerRequestDispatcher {

	private MultiValuedTreeMap<String, String> valueMap;

	private static final Logger logger = Logger.getLogger(FrontControllerRequestDispatcher.class.getName());
	private static final String PATH_VALIDATION_ERROR_PREFIX = "Duplicate route found for path";

	private final String basePath;

	private final Set<Class<?>> parsedController = new HashSet<>();

	private final Map<String, Map<String, RouteMeta>> mappings = new HashMap<>();
	private final Map<String, RouteMeta> getMappings = new HashMap<>();
	private final Map<String, RouteMeta> postMappings = new HashMap<>();
	private final Map<String, RouteMeta> deleteMappings = new HashMap<>();
	private final Map<String, RouteMeta> putMappings = new HashMap<>();
	private final Map<String, RouteMeta> patchMappings = new HashMap<>();

	@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
			justification = "RouteException on duplicate routes is intentional fail-fast behaviour; "
					+ "object is only constructed once during Lambda cold start via Dagger DI")
	protected FrontControllerRequestDispatcher(List<Object> controllers, final String basePath) {
		this.basePath = basePath != null ? basePath : "";
		Map<String, RouteMeta> tempGet = new HashMap<>();
		Map<String, RouteMeta> tempPost = new HashMap<>();
		Map<String, RouteMeta> tempPut = new HashMap<>();
		Map<String, RouteMeta> tempDelete = new HashMap<>();
		Map<String, RouteMeta> tempPatch = new HashMap<>();
		buildMappings(controllers, tempGet, tempPost, tempPut, tempDelete, tempPatch);
		this.getMappings.putAll(tempGet);
		this.postMappings.putAll(tempPost);
		this.putMappings.putAll(tempPut);
		this.deleteMappings.putAll(tempDelete);
		this.patchMappings.putAll(tempPatch);
		registerMappings();
	}
	
	private void registerMappings() {
		mappings.put("GET", this.getMappings);
		mappings.put("POST", this.postMappings);
		mappings.put("PUT", this.putMappings);
		mappings.put("DELETE", this.deleteMappings);
		mappings.put("PATCH", this.patchMappings);
		
	}

	/**
	 * Immutable holder for a route's target method and controller instance.
	 * Fields are set once at construction and never mutated.
	 */
	public static final class RouteMeta {
		private final Method method;
		private final Object instance;

		RouteMeta(Object instance, Method method) {
			this.instance = instance;
			this.method = method;
		}

		@SuppressFBWarnings(value = "EI_EXPOSE_REP",
				justification = "Method reference is intentionally shared; RouteMeta is package-private and immutable after construction")
		public Method getMethod() {
			return method;
		}

		public Object getInstance() {
			return instance;
		}
	}
	
	public Object invoke(AwsProxyRequest request)
			throws RouteException, InvocationTargetException, IllegalAccessException {
		String httpMethod = request.getHttpMethod();
		String resourcePath = request.getResource();
		
		// Per-request diagnostics at FINE/FINEST so they stay out of CloudWatch by default.
		// Suppliers avoid building the strings unless the level is enabled.
		logger.fine(() -> "Dispatching " + httpMethod + " " + resourcePath);
		logger.finest(() -> "Registered routes: " + mappings);

		// Methods without a route map (OPTIONS, HEAD, or a missing method) are "not found", not a crash
		final RouteMeta routeMeta = mappings.getOrDefault(httpMethod, Map.of()).get(resourcePath);

		if (routeMeta != null) {

			final Method method = routeMeta.getMethod();

			return noParameters(method) 
					? method.invoke(routeMeta.getInstance())
					: method.invoke(routeMeta.getInstance(), handleMultiParameters(request, method)
							.toArray());

		} else {
			logger.warning("No matching route for " + httpMethod + " " + request.getResource());
			throw new RouteException("Not found");
		}

	}

	private void buildMappings(final List<Object> routeHandlerClasses,
			Map<String, RouteMeta> tempGet, Map<String, RouteMeta> tempPost,
			Map<String, RouteMeta> tempPut, Map<String, RouteMeta> tempDelete,
			Map<String, RouteMeta> tempPatch) {
		for (Object obj : routeHandlerClasses) {
			iterateOverAnnotatedMethods(obj, tempGet, tempPost, tempPut, tempDelete, tempPatch);
		}
	}

	private List<Object> handleMultiParameters(AwsProxyRequest request, Method method) {
		final List<Object> args = new ArrayList<>();

		for (Parameter parameter : method.getParameters()) {

			setAwsProxyRequest(parameter, args, request);
			setHttpHeaders(parameter, args, request);
			setPathParameter(parameter, args, request.getPathParameters());
			setRequestParameter(parameter, args, request.getMultiValueQueryStringParameters());
			setRequestBody(parameter, args, request.getBody());
		}
		return args;
	}

	private void setRequestBody(Parameter parameter, List<Object> args, String body) {
		Annotation[] annotations = parameter.getAnnotations();
		if (annotations.length == 0 && isNotRequestOrHeadersType(parameter)) {
			args.add(body);
		}
	}

	private boolean isNotRequestOrHeadersType(Parameter parameter) {
		return parameter.getType() != AwsProxyRequest.class && parameter.getType() != Headers.class;
	}

	private void setRequestParameter(Parameter parameter, List<Object> args,
			MultiValuedTreeMap<String, String> multiValueQueryStringParameters) {
		final RequestParam requestParaAnnotation = parameter.getDeclaredAnnotation(RequestParam.class);
		if (null != requestParaAnnotation) {
			val paramValue = Optional.ofNullable(multiValueQueryStringParameters)
					.orElseGet(this::getEmptyMultiValueTreeMap)
					.getOrDefault(requestParaAnnotation.value(), List.of())
					.stream()
					.filter(Objects::nonNull)
					.findFirst().orElse(null);
			args.add(paramValue);

		}
	}

	private MultiValuedTreeMap<String, String> getEmptyMultiValueTreeMap() {

		if (this.valueMap == null) {
			this.valueMap = new MultiValuedTreeMap<>();
		}
		return this.valueMap;
	}

	private void setPathParameter(Parameter parameter, List<Object> args, Map<String, String> pathParameters) {

		PathVariable pathParaAnnotation = parameter.getDeclaredAnnotation(PathVariable.class);
		if (null != pathParaAnnotation) {
			args.add(pathParameters.get(pathParaAnnotation.value()));
		}

	}

	private void setHttpHeaders(final Parameter parameter, final List<Object> args, final AwsProxyRequest request) {

		if (parameter.getType() == Headers.class) {
			args.add(request.getMultiValueHeaders());
		}

	}

	private void setAwsProxyRequest(Parameter parameter, List<Object> args, AwsProxyRequest request) {
		if (parameter.getType() == AwsProxyRequest.class) {
			args.add(request);
		}

	}

	private boolean noParameters(Method method) {
		return method.getParameterCount() == 0;
	}

	private void iterateOverAnnotatedMethods(final Object obj,
			Map<String, RouteMeta> tempGet, Map<String, RouteMeta> tempPost,
			Map<String, RouteMeta> tempPut, Map<String, RouteMeta> tempDelete,
			Map<String, RouteMeta> tempPatch) {
		if (!parsedController.contains(obj.getClass())) {
			Stream.of(obj.getClass().getMethods()).filter(this::isMappingAnnotatedMethod).forEach(method -> {
				addGetMappings(obj, method, tempGet);
				addPostMappings(obj, method, tempPost);
				addPutMappings(obj, method, tempPut);
				addDeleteMappings(obj, method, tempDelete);
				addPatchMappings(obj, method, tempPatch);
			});
		}
	}

	private String resolvePath(String annotationPath) {
		return basePath + annotationPath;
	}

	private void addGetMappings(Object obj, Method routeAnnotatedMethod, Map<String, RouteMeta> target) {
		GetMapping routeAnnotation = routeAnnotatedMethod.getDeclaredAnnotation(GetMapping.class);
		if (null != routeAnnotation) {
			String path = resolvePath(routeAnnotation.value());
			validateMapping(target.put(path, new RouteMeta(obj, routeAnnotatedMethod)), path);
		}
	}

	private void addPostMappings(Object obj, Method routeAnnotatedMethod, Map<String, RouteMeta> target) {
		PostMapping routeAnnotation = routeAnnotatedMethod.getDeclaredAnnotation(PostMapping.class);
		if (null != routeAnnotation) {
			String path = resolvePath(routeAnnotation.value());
			validateMapping(target.put(path, new RouteMeta(obj, routeAnnotatedMethod)), path);
		}
	}

	private void addDeleteMappings(Object obj, Method routeAnnotatedMethod, Map<String, RouteMeta> target) {
		DeleteMapping routeAnnotation = routeAnnotatedMethod.getDeclaredAnnotation(DeleteMapping.class);
		if (null != routeAnnotation) {
			String path = resolvePath(routeAnnotation.value());
			validateMapping(target.put(path, new RouteMeta(obj, routeAnnotatedMethod)), path);
		}
	}

	private void addPutMappings(Object obj, Method routeAnnotatedMethod, Map<String, RouteMeta> target) {
		PutMapping routeAnnotation = routeAnnotatedMethod.getDeclaredAnnotation(PutMapping.class);
		if (null != routeAnnotation) {
			String path = resolvePath(routeAnnotation.value());
			validateMapping(target.put(path, new RouteMeta(obj, routeAnnotatedMethod)), path);
		}
	}

	private void addPatchMappings(Object obj, Method routeAnnotatedMethod, Map<String, RouteMeta> target) {
		PatchMapping routeAnnotation = routeAnnotatedMethod.getDeclaredAnnotation(PatchMapping.class);
		if (null != routeAnnotation) {
			String path = resolvePath(routeAnnotation.value());
			validateMapping(target.put(path, new RouteMeta(obj, routeAnnotatedMethod)), path);
		}
	}

	private void validateMapping(RouteMeta routeMeta, String path) {
		if (routeMeta != null) {
			throw new RouteException(PATH_VALIDATION_ERROR_PREFIX + path);
		}

	}

	private boolean isMappingAnnotatedMethod(final Method method) {

		return (null != method.getDeclaredAnnotation(GetMapping.class))
				|| (null != method.getDeclaredAnnotation(PostMapping.class))
				|| (null != method.getDeclaredAnnotation(PutMapping.class))
				|| (null != method.getDeclaredAnnotation(PatchMapping.class))
				|| (null != method.getDeclaredAnnotation(DeleteMapping.class));
	}

}
