package org.jboss.seam.core;

import java.util.Map;

import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.log.LogProvider;
import org.jboss.seam.log.Logging;
import org.jboss.seam.navigation.Page;
import org.jboss.seam.navigation.Pages;

/**
 * Overrideable component for extracting the conversation id
 * from a request.
 * 
 * @author Gavin King
 * @author Pete Muir
 * @author Shane Bryzak
 *
 */
@Name("org.jboss.seam.core.conversationPropagation")
@Scope(ScopeType.EVENT)
@BypassInterceptors
@Install(precedence=Install.BUILT_IN)
public class ConversationPropagation
{
   private static final LogProvider log = Logging.getLogProvider(ConversationPropagation.class);

   public static final String CONVERSATION_NAME_PARAMETER = "conversationName";
   public static final String CONVERSATION_PROPAGATION_PARAMETER = "conversationPropagation";
   
   private String conversationName;
   private String conversationId;
   private String parentConversationId;
   private boolean validateLongRunningConversation;
   private PropagationType propagationType;   
   private String pageflow;

   /**
    * Initialize the request conversation id, taking
    * into account conversation propagation style, and
    * any conversation id passed as a request parameter
    * or in the PAGE context.
    * 
    * @param parameters the request parameters
    */
   public void restoreConversationId(Map parameters)
   {
      getConversationNameFromRequestParameters(parameters);
      restoreNaturalConversationId(parameters);
      restoreSyntheticConversationId(parameters);
      restorePageContextConversationId();
      getPropagationFromRequestParameter(parameters);
      handlePropagationType(parameters);
   }

   private void handlePropagationType(Map parameters)
   {
      if ( propagationType == PropagationType.NONE )
      {
         conversationId = null;
         parentConversationId = null;
         validateLongRunningConversation = false;
      }
      else if ( propagationType == PropagationType.END )
      {
         validateLongRunningConversation = false;
      }
   }

   private void restorePageContextConversationId()
   {
      if ( Contexts.isPageContextActive() && isMissing(conversationId) ) 
      {
         //checkPageContext is a workaround for a bug in MySQL server-side state saving
         
         //if it is not passed as a request parameter,
         //try to get it from the page context
         org.jboss.seam.faces.FacesPage page = org.jboss.seam.faces.FacesPage.instance();
         conversationId = page.getConversationId();
         parentConversationId = null;
         validateLongRunningConversation = page.isConversationLongRunning();
      }
   
      else
      {
         log.debug("Found conversation id in request parameter: " + conversationId);
      }
   }
   
   private void getConversationNameFromRequestParameters(Map parameters)
   {
      conversationName = getRequestParameterValue(parameters, CONVERSATION_NAME_PARAMETER);
   }

   private void restoreNaturalConversationId(Map parameters)
   {
      //First, try to get the conversation id from the request parameter defined for the page
      String viewId = Pages.getCurrentViewId();
      if ( viewId!=null )
      {
         Page page = Pages.instance().getPage(viewId);
         conversationId = page.getConversationIdParameter().getRequestConversationId(parameters);
         //TODO: how about the parent conversation id?
      }
      // TODO handle conversationName 
   }

   private void restoreSyntheticConversationId(Map parameters)
   {
      //Next, try to get the conversation id from the globally defined request parameters
      Manager manager = Manager.instance(); //TODO: move the conversationIdParameter to this class!
      if ( isMissing(conversationId) )
      {
         conversationId = getRequestParameterValue( parameters, manager.getConversationIdParameter() );
      }
      if ( isMissing(parentConversationId) )
      {
         parentConversationId = getRequestParameterValue( parameters, manager.getParentConversationIdParameter() );
      }
   }

   private void getPropagationFromRequestParameter(Map parameters)
   {
      String value = getRequestParameterValue(parameters, CONVERSATION_PROPAGATION_PARAMETER);
      
      if (value == null)
      {
         return;
      }
      
      if (value.startsWith("begin"))
      {
         propagationType = PropagationType.BEGIN;
         if ( value.length()>6 )
         {
            pageflow = value.substring(6);
         }         
      }
      else if (value.startsWith("join"))
      {
         propagationType = PropagationType.JOIN;
         if ( value.length()>5 )
         {
            pageflow = value.substring(5);
         }         
      }
      else if (value.startsWith("nest"))
      {
         propagationType = PropagationType.NEST;
         if ( value.length()>5 )
         {
            pageflow = value.substring(5);
         }         
      }
      else
      {
         propagationType = PropagationType.valueOf(value.toUpperCase());
      }
   }

   private static boolean isMissing(String storedConversationId) 
   {
      return storedConversationId==null || "".equals(storedConversationId);
   }

   /**
    * Retrieve a parameter value from the request
    * 
    * @param parameters the request parameters
    * @param parameterName the name of the parameter to retrieve
    * @return the parameter value or null if not in the map
    */
   public static String getRequestParameterValue(Map parameters, String parameterName) 
   {
      Object object = parameters.get(parameterName);
      if (object==null)
      {
         return null;
      }
      else
      {
         if ( object instanceof String )
         {
            //when it comes from JSF it is (usually?) a plain string
            return (String) object;
         }
         else
         {
            //in a servlet it is a string array
            String[] values = (String[]) object;
            if (values.length!=1)
            {
               throw new IllegalArgumentException("expected exactly one value for " + parameterName + " request parameter");
            }
            return values[0];
         }
      }
   }

   /**
    * @return the id of the current conversation
    */
   public String getConversationId()
   {
      return conversationId;
   }

   public void setConversationId(String conversationId)
   {
      this.conversationId = conversationId;
   }

   /**
    * @return the id of the parent of the current conversation
    */
   public String getParentConversationId()
   {
      return parentConversationId;
   }

   public void setParentConversationId(String parentConversationId)
   {
      this.parentConversationId = parentConversationId;
   }

   /**
    * Specifies that a redirect will occur if there is no
    * conversation found on the server.
    */
   public boolean isValidateLongRunningConversation()
   {
      return validateLongRunningConversation;
   }

   public void setValidateLongRunningConversation(boolean validateLongRunningConversation)
   {
      this.validateLongRunningConversation = validateLongRunningConversation;
   }

   public static ConversationPropagation instance()
   {
      if ( !Contexts.isEventContextActive() )
      {
         throw new IllegalStateException("No active event context");
      }
      return (ConversationPropagation) Component.getInstance(ConversationPropagation.class, ScopeType.EVENT);
   }

   /**
    * @return the conversation propagation type specified in the request
    */
   public PropagationType getPropagationType()
   {
      return propagationType;
   }

   public void setPropagationType(PropagationType propagationType)
   {
      this.propagationType = propagationType;
   }

   public String getPageflow()
   {
      return pageflow;
   }
   
   public String getConversationName()
   {
      return this.conversationName;
   }
}
