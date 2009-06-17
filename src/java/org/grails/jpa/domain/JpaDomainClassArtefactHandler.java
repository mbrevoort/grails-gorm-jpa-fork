/* Copyright 2004-2005 the original author or authors.
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
package org.grails.jpa.domain;

import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;

import javax.persistence.Entity;

/**
 * An artefact handler that can detect JPA entities
 *
 * @author Graeme Rocher
 * @since 1.1
 */
public class JpaDomainClassArtefactHandler extends ArtefactHandlerAdapter{
    public static final String TYPE = "Domain";

    public JpaDomainClassArtefactHandler() {
        super(TYPE, GrailsDomainClass.class, JpaGrailsDomainClass.class, null);
    }

    public boolean isArtefactClass(Class clazz) {
        return isJPADomainClass(clazz);
    }

    public static boolean isJPADomainClass(Class clazz){
        return clazz != null && clazz.getAnnotation(Entity.class) != null;
    }

}
