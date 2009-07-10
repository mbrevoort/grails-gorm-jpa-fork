package org.grails.jpa.domain

import org.codehaus.groovy.grails.exceptions.GrailsDomainException
import org.acme.Person
import org.acme.Role

/**
 * @author Graeme Rocher
 * @since 1.0
 * 
 * Created: May 15, 2009
 */

public class JpaGrailsDomainClassTests extends GroovyTestCase{

    void testGetReferencedType() {
      def personDomain = new JpaGrailsDomainClass(Person)

      assertEquals Role,personDomain.getPropertyByName("roles").referencedPropertyType
    }

    void testCreateDomainClass() {        
        shouldFail(GrailsDomainException) {
          new JpaGrailsDomainClass(GroovyTestCase)
        }

        def personDomain = new JpaGrailsDomainClass(Person)

        assertEquals "org.acme.Person",personDomain.fullName
    }

    void testIdentifier() {

        def personDomain = new JpaGrailsDomainClass(Person)

        assertNotNull personDomain.identifier
        assertEquals "id", personDomain.identifier.name
    }

    void testVersion() {
      def personDomain = new JpaGrailsDomainClass(Person)

      assertNotNull personDomain.version
      assertEquals "version", personDomain.version.name

    }

    void testConstraints() {
      def personDomain = new JpaGrailsDomainClass(Person)

      assertNotNull personDomain.constrainedProperties
      assertNotNull personDomain.constrainedProperties['name']  

    }

    void testPersistentProperties() {
      def personDomain = new JpaGrailsDomainClass(Person)

      assertTrue "name property should be persistent",personDomain.getPropertyByName("name").isPersistent()
      assertFalse "leaveMe property should be transient", personDomain.getPropertyByName("leaveMe").isPersistent()

    }
}