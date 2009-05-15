/* Copyright 2004-2005 Graeme Rocher
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

import org.codehaus.groovy.grails.commons.AbstractGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.exceptions.GrailsDomainException;
import org.springframework.validation.Validator;
import org.springframework.beans.BeanUtils;

import javax.persistence.*;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import grails.util.GrailsNameUtils;

/**
 * Models a JPA domain class hooking the JPA annotations into the Grails meta model
 *
 * @author Graeme Rocher
 * @since 1.0
 *        <p/>
 *        Created: May 15, 2009
 */
public class JpaGrailsDomainClass extends AbstractGrailsClass implements GrailsDomainClass {
    private Map<String, GrailsDomainClassProperty> propertyMap = new HashMap<String, GrailsDomainClassProperty>();
    private Map<String, GrailsDomainClassProperty> persistentProperties = new HashMap<String, GrailsDomainClassProperty>();
    private GrailsDomainClassProperty[] propertiesArray;
    private JpaDomainClassProperty identifier;
    private JpaDomainClassProperty version;
    private Validator validator;
    private GrailsDomainClassProperty[] persistentPropertyArray;

    public JpaGrailsDomainClass(Class clazz) {
        super(clazz, "");
        Annotation entityAnnotation = clazz.getAnnotation(Entity.class);
        if(entityAnnotation == null) {
            throw new GrailsDomainException("Class ["+clazz.getName()+"] is not annotated with java.persistence.Entity!");
        }
        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(clazz);
        for (PropertyDescriptor descriptor : descriptors) {

            final JpaDomainClassProperty property = new JpaDomainClassProperty(this, descriptor);
            final boolean isIdentifier = descriptor.getReadMethod().getAnnotation(Id.class) != null;
            final boolean isVersion = descriptor.getReadMethod().getAnnotation(Version.class) != null;
            if(isIdentifier) {
                this.identifier = property;
            }
            else if(isVersion) {
                this.version = property;
            }
            else {
                propertyMap.put(descriptor.getName(), property);
                if(property.isPersistent()) {
                    persistentProperties.put(descriptor.getName(), property);
                }
            }
        }
        propertiesArray = propertyMap.values().toArray(new GrailsDomainClassProperty[propertyMap.size()]);
        persistentPropertyArray = persistentProperties.values().toArray(new GrailsDomainClassProperty[persistentProperties.size()]);
    }

    public boolean isOwningClass(Class domainClass) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public GrailsDomainClassProperty[] getProperties() {
        return propertiesArray;
    }

    public GrailsDomainClassProperty[] getPersistantProperties() {
        return getPersistentProperties();
    }

    public GrailsDomainClassProperty[] getPersistentProperties() {
        return persistentPropertyArray;
    }

    public GrailsDomainClassProperty getIdentifier() {
        return this.identifier;
    }

    public GrailsDomainClassProperty getVersion() {
        return this.version;
    }

    public Map getAssociationMap() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public GrailsDomainClassProperty getPropertyByName(String name) {
        return propertyMap.get(name);
    }

    public String getFieldName(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getFieldName() : null;
    }

    public boolean isOneToMany(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop!=null&&prop.isOneToMany();
    }

    public boolean isManyToOne(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop!=null&&prop.isManyToOne();
    }

    public boolean isBidirectional(String propertyName) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Class getRelatedClassType(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getType() : null;
    }

    public Map getConstrainedProperties() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Validator getValidator() {
        return this.validator;
    }

    public void setValidator(Validator validator) {
        this.validator=validator;
    }

    public String getMappingStrategy() {
        return "JPA";
    }

    public boolean isRoot() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set<GrailsDomainClass> getSubClasses() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void refreshConstraints() {
        // NOOP
    }

    public boolean hasSubClasses() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Map getMappedBy() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean hasPersistentProperty(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isPersistent();
    }

    public void setMappingStrategy(String strategy) {
        // do nothing
    }

    private class JpaDomainClassProperty implements GrailsDomainClassProperty {
        private Class ownerClass;
        private PropertyDescriptor descriptor;
        private String name;
        private Class type;
        private GrailsDomainClass domainClass;
        private Method getter;
        private Column columnAnnotation;
        private boolean persistent;

        public JpaDomainClassProperty(GrailsDomainClass domain, PropertyDescriptor descriptor) {
            this.ownerClass = domain.getClazz();
            this.domainClass = domain;
            this.descriptor = descriptor;
            this.name = descriptor.getName();
            this.type = descriptor.getPropertyType();
            this.getter = descriptor.getReadMethod();
            this.columnAnnotation = getter.getAnnotation(javax.persistence.Column.class);
            this.persistent = getter.getAnnotation(Transient.class) == null;                    
        }

        public int getFetchMode() {
            return FETCH_LAZY;
        }

        public String getName() {
            return this.name;
        }

        public Class getType() {
            return this.type;
        }

        public Class getReferencedPropertyType() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public GrailsDomainClassProperty getOtherSide() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getTypePropertyName() {
            return GrailsNameUtils.getPropertyName(getType());
        }

        public GrailsDomainClass getDomainClass() {
            return domainClass;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public boolean isOptional() {
            return columnAnnotation != null && columnAnnotation.nullable();
        }

        public boolean isIdentity() {
            return getter.getAnnotation(javax.persistence.Id.class)!=null;
        }

        public boolean isOneToMany() {
            return getter.getAnnotation(OneToMany.class)!=null;
        }

        public boolean isManyToOne() {
            return getter.getAnnotation(ManyToOne.class)!=null;
        }

        public boolean isManyToMany() {
            return getter.getAnnotation(ManyToMany.class)!=null;
        }

        public boolean isBidirectional() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getFieldName() {
            return getName().toUpperCase();
        }

        public boolean isOneToOne() {
            return getter.getAnnotation(OneToOne.class)!=null;
        }

        public GrailsDomainClass getReferencedDomainClass() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isAssociation() {
            return isOneToMany()||isOneToOne()||isManyToOne()||isManyToMany();
        }

        public boolean isEnum() {
            return getType().isEnum();
        }

        public String getNaturalName() {
            return GrailsNameUtils.getNaturalName(getShortName());
        }

        public void setReferencedDomainClass(GrailsDomainClass referencedGrailsDomainClass) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setOtherSide(GrailsDomainClassProperty referencedProperty) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isInherited() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isOwningSide() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isCircular() {
            return getType().equals(ownerClass);
        }

        public String getReferencedPropertyName() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isEmbedded() {
            return getter.getAnnotation(Embedded.class)!=null;
        }

        public GrailsDomainClass getComponent() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setOwningSide(boolean b) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isBasicCollectionType() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
