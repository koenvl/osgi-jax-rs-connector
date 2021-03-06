/*******************************************************************************
 * Copyright (c) 2012 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Holger Staudacher - initial API and implementation
 *    Dragos Dascalita  - disbaled autodiscovery
 ******************************************************************************/
package com.eclipsesource.jaxrs.publisher.internal;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Request;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;


public class JerseyContext {

  private final RootApplication application;
  private ServletContainer servletContainer;
  private final HttpService httpService;
  private final String rootPath;
  private boolean isApplicationRegistered;

  public JerseyContext( HttpService httpService, String rootPath ) {
    System.out.println("Adapted JerseyContext: calling constructor");
    this.httpService = httpService;
    this.rootPath = rootPath == null ? "/services" : rootPath;
    this.application = new RootApplication();
    disableAutoDiscovery();
    ResourceConfig myApplication = makeResourceConfig();
    this.servletContainer = new ServletContainer( myApplication );
    System.out.println("Adapted JerseyContext: called constructor");
  }

  private ResourceConfig makeResourceConfig() {
    System.out.println("Adapted JerseyContext: registering MultiPartFeature");
    ResourceConfig myApplication = ResourceConfig.forApplication( application ).packages("com.design4s.catalog4s.http.restful")
        .register(MultiPartFeature.class);
    System.out.println("Adapted JerseyContext: registered MultiPartFeature");
    return myApplication;
  }

  private void disableAutoDiscovery() {
    // don't look for implementations described by META-INF/services/*
    this.application.addProperty(ServerProperties.METAINF_SERVICES_LOOKUP_DISABLE, true);
    // disable auto discovery on server, as it's handled via OSGI
    this.application.addProperty(ServerProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
  }

  public void addResource( Object resource ) {
    getRootApplication().addResource( resource );
    registerServletWhenNotAlreadyRegistered();
    if( isApplicationRegistered ) {
      ClassLoader original = getContextClassloader();
      try {
        Thread.currentThread().setContextClassLoader( Request.class.getClassLoader() );
		ResourceConfig myApplication = makeResourceConfig();
        getServletContainer().reload( myApplication );
      } finally {
        resetContextClassloader( original );
      }
    }
  }

  void registerServletWhenNotAlreadyRegistered() {
    if( !isApplicationRegistered ) {
      isApplicationRegistered = true;
      registerApplication();
    }
  }

  private void registerApplication() {
    ClassLoader loader = getContextClassloader();
    setContextClassloader();
    try {
      registerServlet();
    } catch( ServletException shouldNotHappen ) {
      throw new IllegalStateException( shouldNotHappen );
    } catch( NamespaceException shouldNotHappen ) {
      throw new IllegalStateException( shouldNotHappen );
    } finally {
      resetContextClassloader( loader );
    }
  }

  private ClassLoader getContextClassloader() {
    return Thread.currentThread().getContextClassLoader();
  }

  private void setContextClassloader() {
    Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
  }

  private void registerServlet() throws ServletException, NamespaceException {
    ClassLoader original = getContextClassloader();
    try {
      Thread.currentThread().setContextClassLoader( Application.class.getClassLoader() );
      httpService.registerServlet( rootPath, 
                                   getServletContainer(), 
                                   null, 
                                   null );
    } finally {
      resetContextClassloader( original );
    }
  }

  private void resetContextClassloader( ClassLoader loader ) {
    Thread.currentThread().setContextClassLoader( loader );
  }
  
  public void removeResource( Object resource ) {
    getRootApplication().removeResource( resource );
    if( isApplicationRegistered ) {
		ResourceConfig myApplication = makeResourceConfig();
      getServletContainer().reload( myApplication );
    }
    unregisterServletWhenNoresourcePresents();
  }

  private void unregisterServletWhenNoresourcePresents() {
    if( !getRootApplication().hasResources() ) {
      httpService.unregister( rootPath );
		ResourceConfig myApplication = makeResourceConfig();
      servletContainer = new ServletContainer( myApplication );
      isApplicationRegistered = false;
    }
  }

  public List<Object> eliminate() {
    getServletContainer().destroy();
    try {
      httpService.unregister( rootPath );
    } catch( IllegalArgumentException iae ) {
      // do nothing
    }
    return new ArrayList<Object>( getRootApplication().getSingletons() );
  }

  // For testing purpose
  ServletContainer getServletContainer() {
    return servletContainer;
  }
  
  // For testing purpose
  RootApplication getRootApplication() {
    return application;
  }

}
