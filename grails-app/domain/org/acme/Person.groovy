package org.acme

import javax.persistence.*

/**
 * @author Graeme Rocher
 * @since 1.0
 * 
 * Created: May 13, 2009
 */

@Entity
class Person {

    @Id
    @GeneratedValue (strategy = GenerationType.AUTO)
    Long id

    String name

    @Transient
    String leaveMe

    @Version
    Long version

    static constraints = {
        name blank:false
    }
}