package org.acme

import javax.persistence.Entity
import javax.persistence.Id
import javax.annotation.Generated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Version

/**
 * @author Graeme Rocher
 * @since 1.0
 * 
 * Created: May 13, 2009
 */

@Entity
public class Person {

    @Id
    @GeneratedValue (strategy = GenerationType.AUTO)
    Long id

    String name

    @Version
    Long version
}