package org.jboss.seam.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.cxf.common.security.GroupPrincipal;

/**
 * Implementation of the Group interface, used for holding roles etc.
 * 
 * @author Shane Bryzak
 */
public class SimpleGroup implements GroupPrincipal, Serializable
{
   private static final long serialVersionUID = 5766373925836425908L;

   /**
    * The name of the group
    */
   private String name;

   /**
    * The members of this group
    */
   private Set<Principal> members = new HashSet<Principal>();

   public SimpleGroup(String name)
   {
      this.name = name;
   }

   public boolean addMember(Principal user)
   {
      return members.add(user);
   }

   public boolean isMember(Principal member)
   {
      if ( members.contains(member) )
      {
         return true;
      }
      else
      {
         for (Principal m : members)
         {
            if (m instanceof GroupPrincipal && ((GroupPrincipal) m).isMember(member))
            {
               return true;
            }
         }
      }
      return false;
   }

   public Enumeration<? extends Principal> members()
   {
      return Collections.enumeration(members);
   }

   public boolean removeMember(Principal user)
   {
      return members.remove(user);
   }

   public String getName()
   {
      return name;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (obj instanceof SimpleGroup)
      {
         SimpleGroup other = (SimpleGroup) obj;
         return other.name.equals(name);
      }
      else
      {
         return false;
      }
   }

   @Override
   public int hashCode()
   {
      return name.hashCode();
   }
}
