/*
 * Copyright 2011 <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ocpsoft.rewrite.servlet.config;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.ocpsoft.common.pattern.WeightedComparator;
import org.ocpsoft.common.services.ServiceLoader;
import org.ocpsoft.common.util.Iterators;
import org.ocpsoft.logging.Logger;
import org.ocpsoft.rewrite.context.EvaluationContext;
import org.ocpsoft.rewrite.param.ParameterStore;
import org.ocpsoft.rewrite.param.Parameterized;
import org.ocpsoft.rewrite.param.RegexParameterizedPatternBuilder;
import org.ocpsoft.rewrite.servlet.ServletRegistration;
import org.ocpsoft.rewrite.servlet.http.event.HttpServletRewrite;
import org.ocpsoft.rewrite.servlet.spi.ServletRegistrationProvider;
import org.ocpsoft.rewrite.util.Transformations;

/**
 * A {@link org.ocpsoft.rewrite.config.Condition} responsible for comparing URLs to Servlet Mappings.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class ServletMapping extends HttpCondition implements Parameterized
{
   private static final Logger log = Logger.getLogger(Resource.class);

   private final RegexParameterizedPatternBuilder resource;

   private List<ServletRegistrationProvider> servletRegistrationProviders = null;

   private ServletMapping(final String resource)
   {
      this.resource = new RegexParameterizedPatternBuilder(resource);
   }

   @Override
   public boolean evaluateHttp(final HttpServletRewrite event, final EvaluationContext context)
   {
      if (resource != null)
      {
         String path = resource.build(event, context, Transformations.encodePath());
         try {

            for (ServletRegistration registration : getServletRegistration(event.getServletContext()))
            {
               Collection<String> mappings = registration.getMappings();

               for (String mapping : mappings) {
                  if (path.startsWith("/") && !mapping.startsWith("/"))
                  {
                     mapping = "/" + mapping;
                  }

                  if (mapping.contains("*"))
                  {
                     mapping = mapping.replaceAll("\\*", ".*");
                  }

                  if (path.matches(mapping))
                  {
                     return true;
                  }
               }
            }

            return event.getServletContext().getResource(path) != null;
         }
         catch (MalformedURLException e) {
            log.debug("Invalid file format [{}]", path);
         }
      }
      return false;
   }

   /**
    * Obtains the list of registered Servlets using the {@link ServletRegistrationProvider} SPI.
    */
   private List<ServletRegistration> getServletRegistration(ServletContext context)
   {
      for (ServletRegistrationProvider provider : getServletRegistrationProviders()) {
         List<ServletRegistration> registrations = provider.getServletRegistrations(context);
         if (registrations != null) {
            return registrations;
         }
      }
      throw new IllegalStateException("Unable to find the Servlet registrations of the application");
   }

   /**
    * Returns the list of {@link ServletRegistrationProvider} implementations.
    */
   private List<ServletRegistrationProvider> getServletRegistrationProviders()
   {
      if (servletRegistrationProviders == null) {
         servletRegistrationProviders = Iterators.asList(
                  ServiceLoader.loadTypesafe(ServletRegistrationProvider.class).iterator());
         Collections.sort(servletRegistrationProviders, new WeightedComparator());
      }
      return servletRegistrationProviders;
   }

   /**
    * Create a condition which returns true if the given resource is mapped by any {@link Servlet} instances registered
    * within the current application, and returns false if no {@link Servlet} will handle the resource.
    */
   public static ServletMapping includes(final String resource)
   {
      return new ServletMapping(resource);
   }

   public RegexParameterizedPatternBuilder getResourceExpression()
   {
      return resource;
   }

   @Override
   public Set<String> getRequiredParameterNames()
   {
      return resource.getRequiredParameterNames();
   }

   @Override
   public void setParameterStore(ParameterStore store)
   {
      resource.setParameterStore(store);
   }
}