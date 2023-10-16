package com.litongjava.tio.boot.context;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.tio.http.common.HttpConfig;
import org.tio.http.common.handler.HttpRequestHandler;
import org.tio.http.server.HttpServerStarter;
import org.tio.http.server.handler.DefaultHttpRequestHandler;
import org.tio.server.TioServer;
import org.tio.utils.jfinal.P;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.jfinal.aop.AopManager;
import com.litongjava.jfinal.aop.Autowired;
import com.litongjava.tio.boot.annotation.Bean;
import com.litongjava.tio.boot.annotation.Component;
import com.litongjava.tio.boot.annotation.Configuration;
import com.litongjava.tio.boot.annotation.Controller;
import com.litongjava.tio.boot.annotation.Repository;
import com.litongjava.tio.boot.annotation.Service;
import com.litongjava.tio.boot.constatns.ConfigKeyConstants;
import com.litongjava.tio.boot.scaner.ComponentScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TioApplicationContext implements Context {

  private HttpServerStarter httpServerStarter;
  // 创建一个队列来存储 process 方法的返回值
  private Queue<Object> beans = new LinkedList<>();

  @SuppressWarnings("unchecked")
  public void initAnnotation(List<Class<?>> scannedClasses) {
    if (scannedClasses == null) {
      return;
    }
    BeanProcess beanProcess = new BeanProcess();
    // 1. 显式地先初始化Bean
    for (Class<?> clazz : scannedClasses) {
      if (isComponent(clazz)) {
        Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces.length > 0) {
          AopManager.me().addMapping((Class<Object>) interfaces[0], (Class<? extends Object>) clazz);
        }
        Object object = Aop.get(clazz);
        beans.add(object);
        for (Method method : clazz.getDeclaredMethods()) {
          if (method.isAnnotationPresent(Bean.class)) {
            beans.add(beanProcess.process(clazz, method));
          }
        }
      }
    }

    // 处理autoWird注解
    processAutowired();

  }

  private void processAutowired() {
    for (Object bean : beans) {
      Class<?> clazz = bean.getClass();
      for (Field field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(Autowired.class)) {
          Object value = Aop.get(field.getType());
          try {
            field.setAccessible(true);
            field.set(bean, value);
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  private boolean isComponent(Class<?> clazz) {
    return clazz.isAnnotationPresent(Component.class) || clazz.isAnnotationPresent(Controller.class)
        || clazz.isAnnotationPresent(Service.class) || clazz.isAnnotationPresent(Repository.class)
        || clazz.isAnnotationPresent(Configuration.class);
  }

  @Override
  public Context run(Class<?>[] primarySources, String[] args) {

    List<Class<?>> scannedClasses = null;
    // 执行组件扫描
    try {
      scannedClasses = ComponentScanner.scan(primarySources);
    } catch (Exception e1) {
      e1.printStackTrace();
    }
    this.initAnnotation(scannedClasses);

    // 启动端口
    int port = P.getInt(ConfigKeyConstants.http_port);
    String contextPath = P.get(ConfigKeyConstants.http_contexPath);

    // html/css/js等的根目录，支持classpath:，也支持绝对路径
    String pageRoot = P.get(ConfigKeyConstants.http_page);
    // maxLiveTimeOfStaticRes
    String page404 = P.get(ConfigKeyConstants.http_404);
    String page500 = P.get(ConfigKeyConstants.http_500);
    Integer maxLiveTimeOfStaticRes = P.getInt(ConfigKeyConstants.http_maxLiveTimeOfStaticRes);

    // httpConfig
    HttpConfig httpConfig = new HttpConfig(port, null, contextPath, null);
    try {
      httpConfig.setPageRoot(pageRoot);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (maxLiveTimeOfStaticRes != null) {
      httpConfig.setMaxLiveTimeOfStaticRes(maxLiveTimeOfStaticRes);
    }
    Optional.ofNullable(page404).ifPresent((t) -> {
      httpConfig.setPage404(t);
    });
    Optional.ofNullable(page500).ifPresent((t) -> {
      httpConfig.setPage500(page500);
    });

    httpConfig.setUseSession(P.getBoolean(ConfigKeyConstants.http_useSession, false));
    httpConfig.setCheckHost(P.getBoolean(ConfigKeyConstants.http_checkHost, false));

    // 第二个参数也可以是数组,自动考试扫描handler的路径
    HttpRequestHandler requestHandler = null;
    try {
      requestHandler = new DefaultHttpRequestHandler(httpConfig, primarySources);
    } catch (Exception e) {
      e.printStackTrace();
    }

    // httpServerStarter
    httpServerStarter = new HttpServerStarter(httpConfig, requestHandler);
    AopManager.me().addSingletonObject(httpServerStarter);
    Aop.inject(httpServerStarter);
    httpServerStarter.getServerTioConfig();
    // 启动http服务器
    try {
      httpServerStarter.start();
    } catch (IOException e) {
      e.printStackTrace();
    }
    log.info("port:{}", port);
    String fullUrl = "http://localhost";
    if (port != 80) {
      fullUrl += (":" + port);
    }
    if (contextPath != null) {
      fullUrl += contextPath;
    }
    System.out.println(fullUrl);

    return this;
  }

  @Override
  public void close() {
    log.info("stop server");
    try {
      httpServerStarter.stop();
      Aop.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public boolean isRunning() {
    
    if (httpServerStarter != null) {
      TioServer tioServer = httpServerStarter.getTioServer();
      if (tioServer != null) {
        return true;
      } else {
        return false;
      }
    }
    return false;

  }

  public void restart(Class<?>[] primarySources, String[] args) {
    run(primarySources, args);
    close();
  }
  
  public HttpServerStarter getServer() {
    return httpServerStarter;
  }
}