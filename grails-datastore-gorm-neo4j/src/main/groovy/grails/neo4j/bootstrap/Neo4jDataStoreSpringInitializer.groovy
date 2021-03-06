
/*
 * Copyright 2015 original authors
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
package grails.neo4j.bootstrap

import grails.neo4j.Neo4jEntity
import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.bootstrap.AbstractDatastoreInitializer
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jDatastoreTransactionManager
import org.grails.datastore.gorm.neo4j.bean.factory.DefaultMappingHolder
import org.grails.datastore.gorm.neo4j.bean.factory.Neo4jDatastoreFactoryBean
import org.grails.datastore.gorm.neo4j.bean.factory.Neo4jMappingContextFactoryBean
import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.springframework.beans.factory.config.CustomEditorConfigurer
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.propertyeditors.ClassEditor
/**
 * @author Graeme Rocher
 * @since 4.0
 */
@InheritConstructors
class Neo4jDataStoreSpringInitializer extends AbstractDatastoreInitializer {
    static String neo4jDefaultLocation = "data/neo4j"
    public static final String JDBC_NEO4J_PREFIX = "jdbc:neo4j:instance:"

    protected Closure defaultMapping

    @Override
    protected Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass() {
        DatastorePersistenceContextInterceptor
    }

    @Override
    protected boolean isMappedClass(String datastoreType, Class cls) {
        return Neo4jEntity.isAssignableFrom(cls) || super.isMappedClass(datastoreType, cls)
    }

    @Override
    Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
        {->
            def callable = getCommonConfiguration(beanDefinitionRegistry, "neo4j")
            callable.delegate = delegate
            callable.call()


            // reverting the change done for fixing GRAILS-11112
            // since we supply a GraphDatabaseService instance to dbProperties we do not want
            // it being converted to a String
            customEditors(CustomEditorConfigurer) {
                customEditors = [(Class.name): ClassEditor.name ]
            }

            Closure defaultMapping = configuration.getProperty(Neo4jDatastore.SETTING_DEFAULT_MAPPING,Closure, this.defaultMapping)

            neo4jMappingContext(Neo4jMappingContextFactoryBean) {
                grailsApplication = ref('grailsApplication')
                defaultExternal = secondaryDatastore
                if (defaultMapping) {
                    delegate.defaultMapping = new DefaultMappingHolder(defaultMapping)
                }
            }

            neo4jDatastore(Neo4jDatastoreFactoryBean) {
                mappingContext = neo4jMappingContext
                delegate.configuration = configuration
            }


            callable = getAdditionalBeansConfiguration(beanDefinitionRegistry, "neo4j")
            callable.delegate = delegate
            callable.call()

            "neo4jTransactionManager"(Neo4jDatastoreTransactionManager) {
                datastore = ref("neo4jDatastore")
            }
            graphDatabaseService(neo4jDatastore:"getGraphDatabaseService")

            "org.grails.gorm.neo4j.internal.GORM_ENHANCER_BEAN-neo4j"(GormEnhancer, ref("neo4jDatastore"), ref("neo4jTransactionManager")) { bean ->
                bean.initMethod = 'enhance'
                bean.destroyMethod = 'close'
                bean.lazyInit = false
                includeExternal = !secondaryDatastore
            }
        }
    }

    /**
     * Sets the default Neo4j GORM mapping configuration
     */
    void setDefaultMapping(Closure defaultMapping) {
        this.defaultMapping = defaultMapping
    }
}
