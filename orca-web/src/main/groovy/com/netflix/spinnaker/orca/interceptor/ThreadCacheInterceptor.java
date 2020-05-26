package com.netflix.spinnaker.orca.interceptor;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

@Component
public class ThreadCacheInterceptor extends HandlerInterceptorAdapter {

  @Autowired FiatPermissionEvaluator fiatPermissionEvaluator;

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    fiatPermissionEvaluator.clearUserPermission();
  }
}
