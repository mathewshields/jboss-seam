/**
 * GENERATED FILE - DO NOT EDIT
 *
 */

package org.jboss.seam.ui.component.html;

import org.jboss.seam.ui.component.UITaskId ;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

/**
 * Component-Type org.jboss.seam.ui.TaskId
 * Component-Family org.jboss.seam.ui.TaskId
  	 * Add the task id to an output link (or similar JSF control), when the task is available via #{task}.
 */
 public class HtmlTaskId extends org.jboss.seam.ui.component.UITaskId {

  public static final String COMPONENT_TYPE = "org.jboss.seam.ui.TaskId";

  /**
   *  Constructor to init default renderers 
   */ 
  public HtmlTaskId (){
  	  }

// Component properties fields
      
// Getters-setters
      
// Component family.
	public static final String COMPONENT_FAMILY = "org.jboss.seam.ui.TaskId";

	public String getFamily() {
		return COMPONENT_FAMILY;
	}

// Save state
// ----------------------------------------------------- StateHolder Methods


    public Object saveState(FacesContext context) {
        Object values[] = new Object[1];
        values[0] = super.saveState(context);
      	  return values;
   }
   

    public void restoreState(FacesContext context, Object state) {
        Object values[] = (Object[]) state;
        super.restoreState(context, values[0]);
      	
		
	}	
// Utilites

}