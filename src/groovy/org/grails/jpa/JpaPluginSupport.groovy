/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.jpa

import org.springframework.beans.factory.generic.GenericBeanFactoryAccessor
import org.springframework.context.ApplicationContext
import javax.persistence.Entity
import org.grails.spring.scope.PrototypeScopeMetadataResolver
import javax.persistence.EntityManagerFactory
import org.grails.jpa.exceptions.JpaPluginException
import org.springframework.orm.jpa.JpaTemplate

/**
 * @author Graeme Rocher
 * @since 0.1
 * 
 * Created: Apr 17, 2009
 */
public class JpaPluginSupport {

  static doWithSpring = {
          xmlns context:"http://www.springframework.org/schema/context"
          context.'component-scan'( type:"annotation", filter: Entity.name, 'scope-resolver':PrototypeScopeMetadataResolver.name )
  }

  static doWithApplicationContext = { ApplicationContext applicationContext ->

          def accessor = new GenericBeanFactoryAccessor(applicationContext)
          def entityBeans = accessor.getBeansWithAnnotation(Entity)

          def entityManagerFactoryBean = applicationContext.getBeansOfType(EntityManagerFactory)
          if(entityManagerFactoryBean) {
              EntityManagerFactory entityManagerFactory = entityManagerFactoryBean.values().iterator().next()
              JpaTemplate jpaTemplate = new JpaTemplate(entityManagerFactory)

              for(entry in entityBeans) {
                  Class entityClass = entry.value.class

                  entityClass.metaClass {
                      'static' {
                          get { Serializable id -> jpaTemplate.find(entityClass, id) }  
                      }
                      save {-> jpaTemplate.persist delegate }
                      delete {-> jpaTemplate.remove delegate }
                      refresh {-> jpaTemplate.refresh delegate }
                  }


              }

          }
          else {
              throw new JpaPluginException("No ${EntityManagerFactory.name} configured, you need to configure one or install a JPA provider plugin.")
          }
  }
}