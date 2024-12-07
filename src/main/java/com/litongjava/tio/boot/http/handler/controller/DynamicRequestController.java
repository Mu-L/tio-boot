package com.litongjava.tio.boot.http.handler.controller;

import java.lang.reflect.Method;

import com.esotericsoftware.reflectasm.MethodAccess;
import com.litongjava.annotation.EnableCORS;
import com.litongjava.annotation.GatewayCheck;
import com.litongjava.annotation.RequiresAuthentication;
import com.litongjava.annotation.RequiresPermissions;
import com.litongjava.tio.boot.aspect.IGateWayCheckAspect;
import com.litongjava.tio.boot.aspect.IRequiresAuthenticationAspect;
import com.litongjava.tio.boot.aspect.IRequiresPermissionsAspect;
import com.litongjava.tio.boot.http.utils.RequestActionUtils;
import com.litongjava.tio.boot.server.TioBootServer;
import com.litongjava.tio.http.common.HttpConfig;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.model.HttpCors;
import com.litongjava.tio.http.server.util.CORSUtils;

/**
 * Handles dynamic HTTP requests by performing pre-action checks, executing controller methods,
 * and processing post-action responses.
 * 
 * @author Tong Li
 */
public class DynamicRequestController {

  private IGateWayCheckAspect gatewayCheckAspect;
  private IRequiresAuthenticationAspect requiresAuthenticationAspect;
  private IRequiresPermissionsAspect requiresPermissionsAspect;

  /**
   * Processes the incoming HTTP request by performing pre-action checks, executing the controller
   * method, and processing the response.
   *
   * @param request                  The incoming HTTP request.
   * @param httpConfig               The HTTP configuration.
   * @param compatibilityAssignment Flag indicating compatibility assignment.
   * @param routes                   The HTTP controller router.
   * @param actionMethod             The controller method to execute.
   * @return The HTTP response after processing.
   */
  public HttpResponse process(HttpRequest request, HttpConfig httpConfig, boolean compatibilityAssignment, TioBootHttpControllerRouter routes, Method actionMethod) {

    // Execute pre-action checks
    HttpResponse response = performPreActionChecks(request, routes, actionMethod);
    if (response != null) {
      return response;
    }

    // Execute the controller action
    Object actionReturnValue = executeAction(request, httpConfig, compatibilityAssignment, routes, actionMethod);

    // Process post-action response
    response = processPostAction(routes.METHOD_BEAN_MAP.get(actionMethod), actionMethod, actionReturnValue);
    return response;
  }

  /**
   * Performs pre-action checks based on annotations present on the controller method.
   *
   * @param request      The incoming HTTP request.
   * @param routes       The HTTP controller router.
   * @param actionMethod The controller method to check.
   * @return An HTTP response if any pre-action check fails; otherwise, null.
   */
  private HttpResponse performPreActionChecks(HttpRequest request, TioBootHttpControllerRouter routes, Method actionMethod) {
    Object targetController = routes.METHOD_BEAN_MAP.get(actionMethod);

    // GatewayCheck Annotation
    if (actionMethod.isAnnotationPresent(GatewayCheck.class)) {
      GatewayCheck gatewayCheckAnnotation = actionMethod.getAnnotation(GatewayCheck.class);
      if (gatewayCheckAnnotation != null) {
        if (gatewayCheckAspect == null) {
          gatewayCheckAspect = TioBootServer.me().getGateWayCheckAspect();
        }
        if (gatewayCheckAspect != null) {
          return gatewayCheckAspect.check(request, targetController, actionMethod, gatewayCheckAnnotation);
        }
      }
    }

    // RequiresAuthentication Annotation
    if (actionMethod.isAnnotationPresent(RequiresAuthentication.class)) {
      if (requiresAuthenticationAspect == null) {
        requiresAuthenticationAspect = TioBootServer.me().getRequiresAuthenticationAspect();
      }
      if (requiresAuthenticationAspect != null) {
        return requiresAuthenticationAspect.check(request, targetController, actionMethod);
      }
    }

    // RequiresPermissions Annotation
    if (actionMethod.isAnnotationPresent(RequiresPermissions.class)) {
      RequiresPermissions requiresPermissionsAnnotation = actionMethod.getAnnotation(RequiresPermissions.class);
      if (requiresPermissionsAnnotation != null) {
        if (requiresPermissionsAspect == null) {
          requiresPermissionsAspect = TioBootServer.me().getRequiresPermissionsAspect();
        }

        if (requiresPermissionsAspect != null) {
          return requiresPermissionsAspect.check(request, targetController, actionMethod, requiresPermissionsAnnotation);
        }
      }
    }

    return null; // Continue action execution if all checks pass
  }

  /**
   * Executes the controller method corresponding to the incoming request.
   *
   * @param request                  The incoming HTTP request.
   * @param httpConfig               The HTTP configuration.
   * @param compatibilityAssignment Flag indicating compatibility assignment.
   * @param routes                   The HTTP controller router.
   * @param actionMethod             The controller method to execute.
   * @return The return value from the controller method.
   */
  private Object executeAction(HttpRequest request, HttpConfig httpConfig, boolean compatibilityAssignment, TioBootHttpControllerRouter routes, Method actionMethod) {

    String[] paramNames = routes.METHOD_PARAM_NAME_MAP.get(actionMethod);
    Class<?>[] parameterTypes = routes.METHOD_PARAM_TYPE_MAP.get(actionMethod);

    Object actionReturnValue = null;
    MethodAccess methodAccess = TioBootHttpControllerRouter.BEAN_METHODACCESS_MAP.get(routes.METHOD_BEAN_MAP.get(actionMethod));

    if (parameterTypes == null || parameterTypes.length == 0) {
      // No parameters in the controller method
      actionReturnValue = methodAccess.invoke(routes.METHOD_BEAN_MAP.get(actionMethod), actionMethod.getName());
    } else {
      // Build parameter values from the request
      Object[] paramValues = RequestActionUtils.buildFunctionParamValues(request, httpConfig, compatibilityAssignment, paramNames, parameterTypes, actionMethod.getGenericParameterTypes());
      actionReturnValue = methodAccess.invoke(routes.METHOD_BEAN_MAP.get(actionMethod), actionMethod.getName(), paramValues);
    }

    return actionReturnValue;
  }

  /**
   * Processes the return value from the controller method to generate an HTTP response.
   *
   * @param targetController The controller instance.
   * @param actionMethod     The controller method executed.
   * @param actionReturnValue The return value from the controller method.
   * @return The HTTP response after processing.
   */
  private HttpResponse processPostAction(Object targetController, Method actionMethod, Object actionReturnValue) {
    // Generate HTTP response from the action's return value
    HttpResponse response = RequestActionUtils.afterExecuteAction(actionReturnValue);

    // Enable CORS if @EnableCORS annotation is present
    EnableCORS enableCORS = actionMethod.getAnnotation(EnableCORS.class);
    if (enableCORS == null) {
      enableCORS = targetController.getClass().getAnnotation(EnableCORS.class);
    }
    if (enableCORS != null) {
      CORSUtils.enableCORS(response, new HttpCors(enableCORS));
    }
    return response;
  }
}