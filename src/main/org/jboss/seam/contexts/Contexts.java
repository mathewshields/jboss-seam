/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.seam.contexts;

import java.lang.reflect.Method;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import org.jboss.logging.Logger;
import org.jboss.seam.Component;
import org.jboss.seam.Components;
import org.jboss.seam.Seam;
import org.jboss.seam.components.ConversationManager;
import org.jboss.seam.util.Reflections;

import java.util.Map;

/**
 * Provides access to the current contexts associated with the thread.
 * 
 * @author Gavin King
 * @author <a href="mailto:theute@jboss.org">Thomas Heute</a>
 * @version $Revision$
 */
public class Contexts {

	private static final Logger log = Logger.getLogger( Contexts.class );

   private static final ThreadLocal<Context> applicationContext = new ThreadLocal<Context>();
	private static final ThreadLocal<Context> eventContext = new ThreadLocal<Context>();
	private static final ThreadLocal<Context> sessionContext = new ThreadLocal<Context>();
	private static final ThreadLocal<Context> conversationContext = new ThreadLocal<Context>();
   private static final ThreadLocal<Context> businessProcessContext = new ThreadLocal<Context>();

   private static final ThreadLocal<Boolean> isLongRunningConversation = new ThreadLocal<Boolean>();
   private static final ThreadLocal<Boolean> isSessionInvalid = new ThreadLocal<Boolean>();
   private static final ThreadLocal<Boolean> isProcessing = new ThreadLocal<Boolean>();

	public static Context getEventContext() {
		return eventContext.get();
	}

	public static Context getSessionContext() {
		return sessionContext.get();
	}

	public static Context getApplicationContext() {
		return applicationContext.get();
	}

	public static Context getStatelessContext() {
		return new StatelessContext();
	}

	public static Context getConversationContext() {
		return conversationContext.get();
	}

    public static Context getBusinessProcessContext() {
	    return businessProcessContext.get();
    }

	public static void beginRequest(HttpSession session) {
		log.info( ">>> Begin web request" );
		//eventContext.set( new WebRequestContext( request ) );
      eventContext.set( new EventContext() );
		sessionContext.set( new WebSessionContext(session) );
      applicationContext.set( new WebApplicationContext( session.getServletContext() ) );
      isSessionInvalid.set(false);
	}

   public static void endApplication(ServletContext servletContext)
   {
      Context tempApplicationContext = new WebApplicationContext( servletContext );
      applicationContext.set( tempApplicationContext );
      log.info("destroying application context");
      destroy(tempApplicationContext);
      applicationContext.set(null);
   }
   
   private static ConversationManager getConversationManager()
   {
      return (ConversationManager) Components.getComponentInstance( Seam.getComponentName(ConversationManager.class), true );
   }

   public static void endSession(HttpSession session)
   {
      log.info("End of session, destroying contexts");
      
      Context tempAppContext = new WebApplicationContext(session.getServletContext() );
      applicationContext.set(tempAppContext);
      
      //this is used just as a place to stick the ConversationManager
      Context tempEventContext = new EventContext();
      eventContext.set(tempEventContext);
      
      //this is used (a) for destroying session-scoped components
      //and is also used (b) by the ConversationManager
      Context tempSessionContext = new WebSessionContext( session );
      sessionContext.set(tempSessionContext);
      
      Set<String> ids = getConversationManager().getConversationIds();
      log.info("destroying conversation contexts: " + ids);
      for (String conversationId: ids)
      {
         destroy( new ConversationContext(session, conversationId) );
      }

      log.info("destroying session context");
      destroy(tempSessionContext);
      sessionContext.set(null);
      
      destroy(tempEventContext);
      eventContext.set(null);
      
      destroy(tempAppContext);
      applicationContext.set(null);
   }

	public static void endRequest(HttpSession session) {
      
      log.info("After render response, destroying contexts");
      
      if ( Contexts.isEventContextActive() )
      {
         log.info("destroying event context");
         destroy( Contexts.getEventContext() );
      }
      if ( Contexts.isConversationContextActive() )
      {
         if ( Contexts.isLongRunningConversation() )
         {
            getConversationContext().flush();
         }
         else
         {
            log.info("destroying conversation context");
            destroy( Contexts.getConversationContext() );
         }
      }
      if ( Contexts.isBusinessProcessContextActive() )
      {
         getBusinessProcessContext().flush();
      }

      if ( isSessionInvalid.get() )
      {
         isSessionInvalid.set(false);
         session.invalidate();
         //actual session context will be destroyed from the listener
      }
      
		eventContext.set( null );
		sessionContext.set( null );
		conversationContext.set( null );
      
		if ( businessProcessContext.get() != null ) {
			( ( BusinessProcessContext ) businessProcessContext.get() ).release();
			businessProcessContext.set( null );
		}

      log.info( "<<< End web request" );
	}

	public static boolean isConversationContextActive() {
		return getConversationContext() != null;
	}

	public static boolean isEventContextActive() {
		return eventContext.get() != null;
	}

	public static boolean isSessionContextActive() {
		return sessionContext.get() != null;
	}

	public static boolean isApplicationContextActive() {
		return applicationContext != null;
	}

    public static boolean isBusinessProcessContextActive() {
        return businessProcessContext.get() != null;
    }
   
   public static void endConversation() 
   {
      log.info("Ending conversation");
      setLongRunningConversation(false);
   }

   public static void beginConversation() 
   {
      log.info("Beginning conversation");
      setLongRunningConversation(true);
   }
   
   public static boolean isLongRunningConversation()
   {
      return isLongRunningConversation.get();
   }
   
   public static void setLongRunningConversation(boolean value)
   {
      isLongRunningConversation.set(value);
   }
   
   public static void setConversationContext(Context context)
   {
      conversationContext.set(context);
   }
   
   public static void remove(String name)
   {
      if (isEventContextActive())
      {
         log.info("removing from event context");
         getEventContext().remove(name);
      }
      if (isConversationContextActive())
      {
         log.info("removing from conversation context");
         getConversationContext().remove(name);
      }
      if (isSessionContextActive())
      {
         log.info("removing from session context");
         getSessionContext().remove(name);
      }
      if (isBusinessProcessContextActive())
      {
         log.info("removing from process context");
         getBusinessProcessContext().remove(name);
      }
      if (isApplicationContextActive())
      {
         log.info("removing from application context");
         getApplicationContext().remove(name);
      }
   }

   public static Object lookupInStatefulContexts(String name)
   {
      if (isEventContextActive())
      {
         Object result = getEventContext().get(name);
         if (result!=null)
         {
            log.info("found in event context");
            return result;
         }
      }
      
      if (isConversationContextActive())
      {
         Object result = getConversationContext().get(name);
         if (result!=null)
         {
            log.info("found in conversation context");
            return result;
         }
      }
      
      if (isSessionContextActive())
      {
         Object result = getSessionContext().get(name);
         if (result!=null)
         {
            log.info("found in session context");
            return result;
         }
      }
      
      if (isBusinessProcessContextActive())
      {
         Object result = getBusinessProcessContext().get(name);
         if (result!=null)
         {
            log.info("found in business process context");
            return result;
         }
      }
      
      if (isApplicationContextActive())
      {
         Object result = getApplicationContext().get(name);
         if (result!=null)
         {
            log.info("found in application context");
            return result;
         }
      }
      
      return null;
      
   }
      
   public static Object lookup(String name)
   {
      Object result = lookupInStatefulContexts(name);
      if (result!=null) return result;
      
      result = getStatelessContext().get(name);
      if (result!=null)
      {
         log.info("found in stateless context");
         return result;
      }
      else {
         log.info("not found in any context");
         return null;
      }
   }
   
   public static void destroy(Context context)
   {
      for ( String name: context.getNames() ) {
         Component component = Components.getComponent(name);
         log.info("destroying: " + name);
         if ( component!=null )
         {
            callDestroyMethod( component, context.get(name) );
         }
      }
   }

   private static void callDestroyMethod(Component component, Object instance)
   {
      if ( component.hasDestroyMethod() )
      {
         String methodName = component.getDestroyMethod().getName();
         try {
            Method method = instance.getClass().getMethod(methodName);
            Reflections.invokeAndWrap( method, instance );
         }
         catch (NoSuchMethodException e)
         {
            log.warn("could not find destroy method", e);
         }
      }
   }

	public static void beginBusinessProcessContext() {
		if ( isBusinessProcessContextActive() ) {
			throw new IllegalStateException( "There is already a BusinessProcessContext active" );
		}
		businessProcessContext.set ( new BusinessProcessContext() );
	}

	public static void recoverBusinessProcessContext(Map state) {
		if ( isBusinessProcessContextActive() ) {
			throw new IllegalStateException( "There is already a BusinessProcessContext active" );
		}
		BusinessProcessContext ctx = new BusinessProcessContext();
		ctx.recover( state );
		businessProcessContext.set( ctx );
	}

	public static void endBusinessProcessContext() {
		businessProcessContext.set( null );
	}

   public static void setProcessing(boolean processing)
   {
      isProcessing.set(processing);
   }
   
   public static boolean isProcessing()
   {
      return isProcessing.get();
   }
   
   public static void invalidateSession()
   {
      isSessionInvalid.set(true);
   }
   
   public static boolean isSessionInvalid()
   {
      return isSessionInvalid.get();
   }
   
}