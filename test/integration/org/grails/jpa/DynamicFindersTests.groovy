package org.grails.jpa

import org.acme.Person

/**
 * @author Graeme Rocher
 * @since 1.1
 */

public class DynamicFindersTests extends GroovyTestCase{

  protected void setUp() {
    super.setUp();
    new Person(name:"Bob", age:11).save()
    new Person(name:"Fred", age:14).save()
    new Person(name:"Joe", age:9).save()
    new Person(name:"Ed", age:22).save()
    new Person(name:"Frank", age:3).save()
    new Person(name:"Ricky", age:11).save()

  }

  void testSimpleDynamicFinder() {
      def person = Person.findByName("Bob")

      assertNotNull "should have returned a person", person

      // call it again to exercise cached version
      person = Person.findByName("Bob", [max:1])

      assertNotNull "should have returned a person", person

      assertNull "should not have returned a result", Person.findByName("Bad")
  }

  void testFinderWithAnd() {
     def person = Person.findByNameAndAge("Bob", 11)

      assertNotNull "should have returned a person", person

      // call it again to exercise cached version
      person = Person.findByNameAndAge("Bob", 11)
      assertNotNull "should have returned a person", person


      assertNull "should not have returned a result", Person.findByNameAndAge("Bob", 12)
  }


}