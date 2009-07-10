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
class Role {

  @Id
  @GeneratedValue (strategy = GenerationType.AUTO)
  Long id

  String name
  
}