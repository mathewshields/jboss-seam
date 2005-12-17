/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.seam.init;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.faces.context.ExternalContext;

import org.hibernate.HibernateException;
import org.jboss.logging.Logger;
import org.jboss.seam.Component;
import org.jboss.seam.Seam;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.contexts.Context;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.contexts.Lifecycle;
import org.jboss.seam.core.ApplicationContext;
import org.jboss.seam.core.BusinessProcessContext;
import org.jboss.seam.core.Conversation;
import org.jboss.seam.core.ConversationContext;
import org.jboss.seam.core.EventContext;
import org.jboss.seam.core.FacesContext;
import org.jboss.seam.core.Init;
import org.jboss.seam.core.ManagedHibernateSession;
import org.jboss.seam.core.ManagedJbpmSession;
import org.jboss.seam.core.ManagedPersistenceContext;
import org.jboss.seam.core.Manager;
import org.jboss.seam.core.ResourceBundle;
import org.jboss.seam.core.SessionContext;
import org.jboss.seam.core.StatelessContext;
import org.jboss.seam.deployment.Scanner;
import org.jboss.seam.util.Reflections;

/**
 * @author Gavin King
 * @author <a href="mailto:theute@jboss.org">Thomas Heute</a>
 * @version $Revision$
 */
public class Initialization
{
   private static final Logger log = Logger.getLogger(Initialization.class);
   
   private Map<String, String> properties = new HashMap<String, String>();
   private ExternalContext externalContext;
   private boolean isScannerEnabled = true;

   public Initialization(ExternalContext externalContext)
   {
      this.externalContext = externalContext;
      initPropertiesFromServletContext();
      initPropertiesFromResource();
   }

   public Initialization setProperty(String name, String value)
   {
      properties.put(name, value);
      return this;
   }

   public Initialization init()
   {
      log.info("initializing Seam");
      Lifecycle.beginInitialization(externalContext);
      Contexts.getApplicationContext().set(Component.PROPERTIES, properties);
      addComponents();
      Lifecycle.endInitialization();
      log.info("done initializing Seam");
      return this;
   }

   private void initPropertiesFromServletContext()
   {
      Iterator params = externalContext.getInitParameterMap().keySet().iterator();
      while (params.hasNext())
      {
         String name = (String) params.next();
         properties.put(name, externalContext.getInitParameter(name));
      }
   }

   private void initPropertiesFromResource()
   {
      loadFromResource( properties, "/seam.properties" );
      loadFromResource( properties, "/seam-jndi.properties" );
   }
   
   public static void loadFromResource(Map properties, String resource)
   {
      InputStream stream = getResourceAsStream(resource);
      if (stream!=null)
      {
         log.info("reading properties from: " + resource);
         Properties props = new Properties();
         try
         {
            props.load(stream);
         }
         catch (IOException ioe)
         {
            log.error("could not read " + resource, ioe);
         }
         properties.putAll(props);
      }
      else 
      {
         log.debug("not found: " + resource);
      }
   }

   private static InputStream getResourceAsStream(String resource) {
      String stripped = resource.startsWith("/") ? 
            resource.substring(1) : resource;
   
      InputStream stream = null; 
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader!=null) {
         stream = classLoader.getResourceAsStream( stripped );
      }
      if ( stream == null ) {
         Seam.class.getResourceAsStream( resource );
      }
      if ( stream == null ) {
         stream = Seam.class.getClassLoader().getResourceAsStream( stripped );
      }
      return stream;
   }
   
   protected void addComponents()
   {
      Context context = Contexts.getApplicationContext();

      addComponent( Init.class, context );
      addComponent( Manager.class, context );
      addComponent( Conversation.class, context );
      addComponent( FacesContext.class, context );
      addComponent( EventContext.class, context );
      addComponent( SessionContext.class, context );
      addComponent( StatelessContext.class, context );
      addComponent( ConversationContext.class, context );
      addComponent( ApplicationContext.class, context );
      addComponent( BusinessProcessContext.class, context );
      addComponent( ResourceBundle.class, context );

      Init init = (Init) Component.getInstance(Init.class, true);
      
      //TODO: move all this stuff into Init component?
      for ( String className : init.getComponentClasses() )
      {
         try
         {
            addComponent( Reflections.classForName(className), context );
         }
         catch (ClassNotFoundException cnfe)
         {
            throw new IllegalArgumentException("Component class not found: " + className, cnfe);
         }
      }

      for ( String unitName : init.getManagedPersistenceContexts() )
      {
         addComponent( unitName, ManagedPersistenceContext.class, context );
      }

      for ( String sfName : init.getManagedSessions() )
      {
         addComponent( sfName, ManagedHibernateSession.class, context );
      }

      if ( init.getJbpmSessionFactoryName() != null )
      {
         addComponent( ManagedJbpmSession.class, context );
      }
      
      if (isScannerEnabled)
      {
         for ( Class clazz: new Scanner().getClasses() )
         {
            if ( clazz.isAnnotationPresent(Name.class) )
            {
               addComponent(clazz, context);
            }
         }
      }
      
   }
   
   protected void addComponent(String name, Class clazz, Context context)
   {
      context.set( name + ".component", new Component(clazz, name) );
   }

   protected void addComponent(Class clazz, Context context)
   {
      context.set( Seam.getComponentName(clazz) + ".component", new Component(clazz) );
   }

   public boolean isScannerEnabled()
   {
      return isScannerEnabled;
   }

   public Initialization setScannerEnabled(boolean isScannerEnabled)
   {
      this.isScannerEnabled = isScannerEnabled;
      return this;
   }

}
