package org.acme

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType

/**
 * @author Graeme Rocher
 * @since 1.1
 */

@Entity
public class Address {
  @Id
  @GeneratedValue (strategy = GenerationType.AUTO)
  Long id


  String street

}