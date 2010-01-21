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

import grails.util.GrailsNameUtils
import java.beans.Introspector
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.grails.jpa.domain.JpaDomainClassArtefactHandler
import org.grails.jpa.domain.JpaGrailsDomainClass
import org.grails.jpa.exceptions.JpaPluginException
import org.grails.spring.scope.PrototypeScopeMetadataResolver
import org.springframework.beans.*
import org.springframework.context.ApplicationContext
import org.springframework.orm.jpa.JpaCallback
import org.springframework.orm.jpa.JpaTemplate
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.interceptor.TransactionAspectSupport
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import javax.persistence.*

/**
 * @author Graeme Rocher
 * @since 0.1
 * 
 * Created: Apr 17, 2009
 */
public class JpaPluginSupport {

  static final COMPARATORS = Collections.unmodifiableList([
          "IsNull",
          "IsNotNull",
          "LessThan",
          "LessThanEquals",
          "GreaterThan",
          "GreaterThanEquals",
          "NotEqual",
          "Like",
          "Ilike",
          "InList",
          "NotInList",
          "NotBetween",
          "Between" ])
  static final COMPARATORS_RE = COMPARATORS.join("|")
  static final DYNAMIC_FINDER_RE = /(\w+?)(${COMPARATORS_RE})?((And|Or)(\w+?)(${COMPARATORS_RE})?)?/
  

  static doWithSpring = {
    xmlns context:"http://www.springframework.org/schema/context"
    context.'component-scan'( type:"annotation", filter: Entity.name, 'scope-resolver':PrototypeScopeMetadataResolver.name )
  }

  static doWithApplicationContext = { ApplicationContext applicationContext ->
		  def grailsVersion = grails.util.GrailsUtil.grailsVersion
		  def entityBeans = [:]
		  // have to handle different Spring APIs differently
		  def accessorClass
		  if(grailsVersion?.startsWith("1.1")) {
			  accessorClass = Thread.currentThread().contextClassLoader.loadClass('org.springframework.beans.factory.generic.GenericBeanFactoryAccessor')
		  }
		  else {
			  accessorClass = Thread.currentThread().contextClassLoader.loadClass('org.codehaus.groovy.grails.beans.factory.GenericBeanFactoryAccessor')
		  }
          def accessor = accessorClass.newInstance(applicationContext)
          entityBeans = accessor.getBeansWithAnnotation(Entity)				
		

          def entityManagerFactoryBean = applicationContext.getBeansOfType(EntityManagerFactory)
          Map transactionManagerBeans = applicationContext.getBeansOfType(PlatformTransactionManager)
          PlatformTransactionManager transactionManagerBean
          transactionManagerBeans.each {key, value ->
            if(value instanceof JpaTransactionManager) {
                transactionManagerBean = value
            }
          }

          if(entityManagerFactoryBean) {
              EntityManagerFactory entityManagerFactory = entityManagerFactoryBean.values().iterator().next()
              JpaTemplate jpaTemplate = new JpaTemplate(entityManagerFactory)

              GrailsApplication app = application
              
              boolean appEngine = manager.hasGrailsPlugin("appEngine")
            
              for(entry in entityBeans) {
                 Class entityClass = entry.value.class
                 app.addArtefact(JpaDomainClassArtefactHandler.TYPE, new JpaGrailsDomainClass(entityClass))                 
              }

              for(dc in application.domainClasses) {
				  def domainClass = dc
                  if(!(domainClass instanceof JpaGrailsDomainClass)) continue;

                  JpaGrailsDomainClass jpaGrailsDomainClass = domainClass
                  Class entityClass = jpaGrailsDomainClass.clazz
                  def logicalName = GrailsNameUtils.getLogicalPropertyName(entityClass.name,'')

				  def plugin = delegate
                  def typeConverter = new SimpleTypeConverter()
				  applicationContext.getBeansOfType(PropertyEditorRegistrar).each { key, val ->
						val.registerCustomEditors typeConverter
				  }
                  // dynamic finder handling with methodMissing
                  entityClass.metaClass.static.methodMissing = { method, args ->
                    def m = method =~ /^find(All)?By${DYNAMIC_FINDER_RE}$/
                    if (m) {
                        def fields = []
                        def comparator = m[0][3]
                        // How many arguments do we need to pass for the given
                        // comparator?
                        def numArgs = getArgCountForComparator(comparator)

                        fields << [field:Introspector.decapitalize(m[0][2]),
                                   args:args[0..<numArgs],
                                   argCount:numArgs,
                                   comparator:comparator]

                        // Strip out that number of arguments from the ones
                        // we've been passed.
                        args = args[numArgs..<args.size()]

                        // If we have a second clause, evaluate it now.
                        def join = m[0][5]

                        if (join) {
                            comparator = m[0][7]
                            numArgs = getArgCountForComparator(comparator)
                            fields << [field: Introspector.decapitalize(m[0][6]),
                                       args:args[0..<numArgs],
                                       argCount:numArgs,
                                       comparator:comparator]
                        }

                        // synchronized the update to the MetaClass, note this is
                        // only necessary during method missing and next call to this
                        // method will not hit the synchronized block at all
                        synchronized(this) {
                            def queryString = new StringBuilder("select $logicalName from $entityClass.name as $logicalName where ")
                            boolean singleResult = !m[0][1]

                            def first = fields[0]
                            int state = 0
                            def expr
                            if(appEngine) {
                              (expr,state) = JpaPluginSupport.getJpaQLExpressionForAppEngine(first.comparator, state)
                            }
                            else {
                              expr = JpaPluginSupport.getJpaQLExpressionFor(first.comparator)
                            }
                            queryString.append( "(${logicalName}.${first.field} ${expr})" )
                            if(join) {
                                if(join == "Or") queryString.append( " or " )
                                else queryString.append(" and ")
                                def second = fields[1]
                                if(appEngine) {
                                  (expr,state) = JpaPluginSupport.getJpaQLExpressionForAppEngine(second.comparator, state)
                                }
                                else {
                                  expr = JpaPluginSupport.getJpaQLExpressionFor(first.comparator)
                                }
                                queryString.append("(${logicalName}.${second.field} ${expr})")
                            }

                            queryString = queryString.toString()
                            // cache new behavior
                            def newMethod = { Object[] varArgs ->
                                 def localArgs = varArgs ? varArgs[0] : []
                                 if(!localArgs) throw new MissingMethodException(method, delegate, localArgs)

                                 jpaTemplate.execute({ EntityManager em ->

                                   Query query = em.createQuery(queryString)

                                   def queryParams
                                   if(localArgs && (localArgs[-1] instanceof Map)) {
                                      queryParams = localArgs[-1]
									  if(localArgs.size()-1 != 0)
                                      	 localArgs = localArgs[0..-2]
									  else {
										 localArgs = null
									  }
                                   }

                                   localArgs?.eachWithIndex {val, int i ->
                                      if(val instanceof GString) val = val.toString()
                                      query.setParameter(i+1, val)
                                   }

                                   if(queryParams) {
                                      if(queryParams.max)
                                        query.setMaxResults(queryParams.max.toInteger())
                                      if(queryParams.offset)
                                        query.setFirstResult(queryParams.offset.toInteger())
                                   }
                                   if(singleResult) {
                                       query.setMaxResults(1)
                                       try {
                                         return query.singleResult
                                       } catch (javax.persistence.NoResultException e) {
                                         return null
                                       }
                                   }
                                   else {
                                       return query.resultList
                                   }

                                 } as JpaCallback)
                            }

                            // register new cached behavior on metaclass to speed up next invokation
                            entityClass.metaClass.static."$method" = newMethod

                            // Check whether we have any options, such as "sort".
                            def queryParams
                            if (args) {
                                if(args[0] instanceof Map) {
                                  queryParams = args[0]
                                }
                            }


                            def finalArgs = fields.collect { it.args }.flatten()
                            if(queryParams) {
                                finalArgs << queryParams
                            }
                            // invoke new behavior
                            return newMethod(finalArgs)
                        }

                    } else {
                        throw new MissingMethodException(method, delegate, args)
                    }
                }

                  entityClass.metaClass {
                      'static' {
                          def countLogic = {->
                            jpaTemplate.execute({ EntityManager em ->
                              def q = em.createQuery("select count(${logicalName}) from ${entityClass.name} as ${logicalName}")
                              q.singleResult
                            } as JpaCallback)
                          }
                          // Foo.count()
                          count countLogic

                          // Foo.count
                          getCount countLogic

                          // Foo.get(1)
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

                          // Foo.executeUpdate("delete from ...")
                          executeUpdate { String dml ->
                            jpaTemplate.execute({ EntityManager em ->
                              Query q = em.createQuery(dml)
                              q.executeUpdate()
                            } as JpaCallback)
                          }
                          // Foo.executeUpdate("delete from ...", ['param'])
                          executeUpdate { String dml, List params ->
                            jpaTemplate.execute({ EntityManager em ->
                              Query q = em.createQuery(dml)
                              params.eachWithIndex { val, int i ->
                                if(val instanceof GString) val = val.toString()
                                q.setParameter(i+1, val)
                              }
                              q.executeUpdate()
                            } as JpaCallback)
                          }
                          // Foo.executeUpdate("delete from ...", [name:'param'])
                          executeUpdate { String dml, Map params ->
                            jpaTemplate.execute({ EntityManager em ->
                              Query q = em.createQuery(dml)
                              params.each {String name, val ->
                                if(val instanceof GString) val = val.toString()
                                q.setParameter(name, val)
                              }
                              q.executeUpdate()
                            } as JpaCallback)
                          }



                          // Foo.findAll("select..")
                          findAll = { String q -> executeQuery(q) }
                          findAll = { String q, List params -> executeQuery(q, params) }
                          findAll = { String q, Map params -> executeQuery(q, params) }

                          // Foo.find("select..")
                          find = { String q ->
                              jpaTemplate.execute({ EntityManager em ->
                                Query query = em.createQuery(q)
                                query.setMaxResults(1)
                                try {
                                  return query.singleResult
                                } catch (javax.persistence.NoResultException e) {
                                  return null
                                }
                              } as JpaCallback)
                          }
                          // Foo.find("select..", ['param1'] )
                          find = { String q, List params ->
                            jpaTemplate.execute({ EntityManager em ->
                              Query query = em.createQuery(q)
                              query.setMaxResults(1)
                              params.eachWithIndex { val, int i ->
                                if(val instanceof GString) val = val.toString()
                                query.setParameter(i+1, val)
                              }
                              try {
                                return query.singleResult
                              } catch (javax.persistence.NoResultException e) {
                                return null
                              }
                            } as JpaCallback)

                          }
                          // Foo.find("select..", [param1:'param1'])
                          find = { String q, Map params ->
                              jpaTemplate.execute({ EntityManager em ->
                                Query query = em.createQuery(q)
                                params.each { String name, value ->
                                  if(value instanceof GString) value = value.toString()
                                  query.setParameter(name, value)
                                }
                                query.setMaxResults(1)
                                try {
                                  return query.singleResult
                                } catch (javax.persistence.NoResultException e) {
                                  return null
                                }
                              } as JpaCallback)
                          }

                          // Foo.findWhere(param1:"param1")
                          findWhere { Map params ->
                            jpaTemplate.execute({ EntityManager em ->
                              def whereClause = params.keySet().collect {
                                 "${logicalName}.$it = :$it"
                              }.join(" and ")
                              String q = "select ${logicalName} from ${entityClass.name} as ${logicalName} where ${whereClause}"
                              Query query = em.createQuery(q)

                              params.each { String name, value ->
                                if(value instanceof GString) value = val.toString()
                                query.setParameter(name, value)
                              }
                              query.setMaxResults(1)
                              try {
                                return query.singleResult
                              } catch (javax.persistence.NoResultException e) {
                                return null
                              }
                            } as JpaCallback)
                          }

                          // Foo.getAll(1,2,3)
                          getAll { List identifiers ->
                            jpaTemplate.execute({ EntityManager em ->
                                def converted = identifiers.collect { it.toInteger() }
                                String q = "select $logicalName from $entityClass.name as $logicalName where ${logicalName}.id in (${converted.join(',')})"
                                Query query = em.createQuery(q)
                                query.resultList
                            } as JpaCallback )
                          }
                        
                          // Foo.list(max:10)
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

                          // Foo.lock(1)
                          lock { Serializable id ->
                            def obj = get(id)
                            if(obj) {
                               jpaTemplate.execute({ EntityManager em ->
                                  em.lock obj, LockModeType.WRITE 
                               } as JpaCallback)
                            }
                            return obj
                          }
                          // Foo.withEntityManager { em -> }
                          withEntityManager { Closure callable ->
                              jpaTemplate.execute({ EntityManager em ->
                                   callable.call( em )
                              } as JpaCallback)                           
                          }

                          // Foo.withTransaction { status -> }
                          withTransaction { Closure callable ->
                            if(!transactionManagerBean) throw new IllegalStateException("No transactionManager bean is defined! Register a JpaTransactionManager in your Spring configuration.")

                            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManagerBean)
                            transactionTemplate.execute(callable as TransactionCallback)
                          }

                      }

                      getConstraints {->
                        domainClass.constrainedProperties 
                      }
                      // foo.save(flush:true)
                      save { Map args = [:] ->
                        if(delegate.validate()) {
                          jpaTemplate.persist delegate
                          if(args?.flush) {
                             jpaTemplate.flush()
                          }
                          return delegate
                        }
                        else {
                            JpaPluginSupport.rollbackCurrentTransaction(jpaTemplate)
                            return null
                        }
                      }

                      // foo.merge(flush:true)
                      merge { Map args = [:] ->
                        if(delegate.validate()) {
                          jpaTemplate.merge delegate
                          if(args?.flush) {
                             jpaTemplate.flush()
                          }
                          return delegate
                        }
                        else {
                            JpaPluginSupport.rollbackCurrentTransaction(jpaTemplate)
                            return null
                        }

                      }
                      // foo.delete(flush:true)
                      delete { Map args = [:]->
                        jpaTemplate.remove delegate
                        if(args?.flush) {
                           jpaTemplate.flush()
                        }
                      }
                      // foo.refresh()
                      refresh {-> jpaTemplate.refresh delegate }

                      // foo.lock()
                      lock {->
                        def object = delegate
                        jpaTemplate.execute({ EntityManager em ->
                          em.lock object, LockModeType.WRITE                         
                        } as JpaCallback)
                      }

                  }


              }

          }
          else {
              throw new JpaPluginException("No ${EntityManagerFactory.name} configured, you need to configure one or install a JPA provider plugin.")
          }
  }

  public static rollbackCurrentTransaction(JpaTemplate jpaTemplate) {
    try {
      // rollback the transaction on a validation error
      def status = TransactionAspectSupport.currentTransactionStatus()
      status.setRollbackOnly()
    } catch (NoTransactionException e) {
      // if there is no transaction set flush to manual
      jpaTemplate.execute({EntityManager em ->
        em.setFlushMode(FlushModeType.COMMIT)
      } as JpaCallback)
    }
  }

  public static getJpaQLExpressionForAppEngine(String comparator, int state) {
      // default to equals
      def result = " = ?${state++} "
      switch(comparator) {
          case "IsNull": result = " is null "; break
          case "IsNotNull": result = " is not null "; break
          case "Like": result = " like ?${state++} "; break
          case "Between": result = " between ?${state++} and ?${state++} "; break
          case "NotBetween": result = " not between ?${state++} and ?${state++} "; break
          case "InList": result = " in (?${state++}) "; break
          case "NotInList": result = " not in (?${state++}) "; break
          case "LessThan": result = " < ?${state++} "; break
          case "LessThanEquals": result = " <= ?${state++} "; break
          case "GreaterThan": result = " > ?${state++} "; break
          case "GreaterThanEquals": result = " >= ?${state++} "; break
          case "NotEqual": result = " != ?${state++} "; break
      }
      return [result, state]
  }

  public static getJpaQLExpressionFor(String comparator) {
      // default to equals
      def result = " = ? "
      switch(comparator) {
          case "IsNull": result = " is null "; break
          case "IsNotNull": result = " is not null "; break
          case "Like": result = " like ? "; break
          case "Between": result = " between ? and ? "; break
          case "NotBetween": result = " not between ? and ? "; break
          case "InList": result = " in (?) "; break
          case "NotInList": result = " not in (?) "; break
          case "LessThan": result = " < ? "; break
          case "LessThanEquals": result = " <= ? "; break
          case "GreaterThan": result = " > ? "; break
          case "GreaterThanEquals": result = " >= ? "; break
          case "NotEqual": result = " != ? "; break
      }
      return result
  }

  private static int getArgCountForComparator(String comparator) {
      if (comparator == "Between") {
          return 2
      }
      else if (["IsNull", "IsNotNull"].contains(comparator)) {
          return 0
      }
      else {
          return 1
      }
  }

}