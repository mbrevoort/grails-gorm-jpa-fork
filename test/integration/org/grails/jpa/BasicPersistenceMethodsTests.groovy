package org.grails.jpa

import org.acme.Person

/**
 * @author Graeme Rocher
 * @since 1.0
 * 
 * Created: May 14, 2009
 */

public class BasicPersistenceMethodsTests extends GroovyTestCase{

    void testSaveAndGet() {

          def p = new Person(name:"Fred")
          p.save()

          assertEquals 1, p.id

          p = Person.get(1L)

          assertNotNull "should have retrieved a person", p
    }

}