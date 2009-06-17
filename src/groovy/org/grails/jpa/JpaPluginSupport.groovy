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
import org.springframework.orm.jpa.JpaCallback
import javax.persistence.EntityManager
import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.grails.jpa.domain.JpaDomainClassArtefactHandler
import org.grails.jpa.domain.JpaGrailsDomainClass
import org.springframework.beans.SimpleTypeConverter
import javax.persistence.Query
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

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

              GrailsApplication app = application
              for(entry in entityBeans) {
                 Class entityClass = entry.value.class
                 app.addArtefact(JpaDomainClassArtefactHandler.TYPE, new JpaGrailsDomainClass(entityClass))                 
              }

              for(domainClass in application.domainClasses) {
                  if(!(domainClass instanceof JpaGrailsDomainClass)) continue;

                  JpaGrailsDomainClass jpaGrailsDomainClass = domainClass
                  Class entityClass = jpaGrailsDomainClass.clazz
                  def logicalName = GrailsNameUtils.getLogicalPropertyName(entityClass.name,'') 

					org.codehaus.groovy.grails.documentation.DocumentationContext.instance.active=true

				  def plugin = delegate
                  def typeConverter = new SimpleTypeConverter()
                  entityClass.metaClass {
                      'static' {
                          def countLogic = {->
                            jpaTemplate.execute({ EntityManager em ->
                              def q = em.createQuery("select count(${logicalName}) from ${entityClass.name} as ${logicalName}")
                              q.singleResult
                            } as JpaCallback)
                          }
                          // Foo.count()
                          plugin.doc "Returns the count for the total number of entities"
                          count countLogic

                          // Foo.count
                          plugin.doc "Returns the count for the total number of entities"
                          getCount countLogic

                          // Foo.get(1)
						  plugin.doc "Retrieves an entity by its identifier"
                          get { Serializable id ->
                              Class idType = jpaGrailsDomainClass.identifier.type
                              if( !idType.isInstance(id) ) {
                                 id = typeConverter.convertIfNecessary(id,idType)
                              }
                              jpaTemplate.find(entityClass, id)
                          }

                          // Foo.exists(1)
                          exists { Serializable id ->
                            get(id)!=null 
                          }

                          // Foo.executeQuery("select..")
                          executeQuery { String q ->
                            jpaTemplate.find(q)
                          }
                          // Foo.executeQuery("select..", ['param1'])
                          executeQuery { String q, List params ->
                            jpaTemplate.find(q, params.toArray())
                          }

                          // Foo.executeQuery("select..", [param1:'param1'])
                          executeQuery { String q, Map params ->
                            jpaTemplate.findByNamedParams(q, params)
                          }

                          // Foo.find("select..")
                          findAll = { String q -> executeQuery(q) }
                          findAll = { String q, List params -> executeQuery(q, params) }
                          findAll = { String q, Map params -> executeQuery(q, params) }
                        
                          // Foo.list(max:10)
                          plugin.doc "Returns a List of all entities"
                          list { Map args = [:] ->
                              jpaTemplate.executeFind( { EntityManager em ->
                                def orderBy = ''
                                def order = ''
                                if(args?.sort) {
                                    if(args?.order) {
                                        order = args.order == 'desc' ? ' desc' : ' asc'
                                    }
                                    def sort = args.sort
                                    if(sort instanceof List) {
                                        orderBy = " order by ${sort.join(", ${logicalName}.")}${order}"
                                    }
                                    else {
                                        orderBy = " order by ${logicalName}.${sort}${order}"
                                    }
                                }

                                  def q = em.createQuery("select ${logicalName} from ${entityClass.name} as ${logicalName} ${orderBy}")
                                  if(args?.max) {
                                      q.setMaxResults(args.max.toInteger())
                                  }
                                  if(args?.offset) {
                                      q.setFirstResult(args.offset.toInteger())
                                  }
                                  q.resultList
                              } as JpaCallback)
                          }
                          // Foo.withEntityManager { em -> }
                          plugin.doc "Allows direct access to the JPA EntityManager"
                          withEntityManager { Closure callable ->
                              callable.call( jpaTemplate.getEntityManager() )
                          }
                      }

                      getConstraints {->
                        domainClass.constrainedProperties 
                      }
                      // foo.save(flush:true)
                      plugin.doc "Retrieves an entity by its identifier"
                      save { Map args = [:] ->
                        if(delegate.validate()) {
                          jpaTemplate.persist delegate
                          if(args?.flush) {
                             jpaTemplate.flush()
                          }
                          return delegate
                        }
                      }
                      // foo.delete(flush:true)
                      plugin.doc "Deletes an entity"
                      delete { Map args = [:]->
                        jpaTemplate.remove delegate
                        if(args?.flush) {
                           jpaTemplate.flush()
                        }
                      }
                      // foo.refresh()
                      plugin.doc "Refreshes an entities state"
                      refresh {-> jpaTemplate.refresh delegate }


                  }


              }

          }
          else {
              throw new JpaPluginException("No ${EntityManagerFactory.name} configured, you need to configure one or install a JPA provider plugin.")
          }
  }
}