package org.grails.jpa

import org.acme.Person

/**
 * @author Graeme Rocher
 * @since 1.1
 */

public class QueryMethodsTests extends GroovyTestCase {


  void testExecuteQueryMethod() {
    def p = new Person(name:"Fred")
    p.save()


    def results = Person.executeQuery("select p.name from Person p")

    assertTrue "should have got results", results.size()>0
    assertEquals "first result should have been Fred","Fred", results[0]
  }

  void testExecuteQueryWithOrdinals() {
    def p = new Person(name:"Fred")
    p.save()


    def results = Person.executeQuery("select p.name from Person p where p.name = ?", ["Fred"])

    assertTrue "should have got results", results.size()>0
    assertEquals "first result should have been Fred","Fred", results[0]
  }

  void testExecuteQueryWithNamedParams() {
    def p = new Person(name:"Fred")
    p.save()


    def results = Person.executeQuery("select p.name from Person p where p.name = :myName", [myName:"Fred"])

    assertTrue "should have got results", results.size()>0
    assertEquals "first result should have been Fred","Fred", results[0]

  }
}