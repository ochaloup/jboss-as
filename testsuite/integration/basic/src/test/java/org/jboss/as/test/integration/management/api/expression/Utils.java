/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.management.api.expression;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.IOException;

import junit.framework.Assert;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 * Utility class used for manipulation with system properties via dmr api.
 * 
 * @author <a href="ochaloup@jboss.com">Ondrej Chaloupka</a>
 */
public class Utils {
    private static final Logger log = Logger.getLogger(Utils.class);
    
    public static void setProperty(String name, String value, ModelControllerClient client) {
        ModelNode modelNode = createOpNode("system-property=" + name, ADD);
        modelNode.get(VALUE).set(value);
        ModelNode result = executeOp(modelNode, client);
        log.debug("Added property " + name + ", result: " + result);
    }
    
    public static void removeProperty(String name, ModelControllerClient client) {
        ModelNode modelNode = createOpNode("system-property=" + name, REMOVE);
        ModelNode result = executeOp(modelNode, client);
        log.debug("Removing property " + name + ", result: " + result);
    }
    
    public static ModelNode executeOp(ModelNode op, ModelControllerClient client) {
        try {
            return client.execute(op);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
    
    public static String getProperty(String name, ModelControllerClient client) {
        ModelNode modelNode = createOpNode("system-property=" + name, READ_ATTRIBUTE_OPERATION);
        modelNode.get(NAME).set(VALUE);

        ModelNode result = executeOp(modelNode, client);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
        ModelNode resolvedResult = result.resolve();
        log.debug("Resolved property " + name+ ": " + resolvedResult);
        Assert.assertEquals(SUCCESS, resolvedResult.get(OUTCOME).asString());
        return resolvedResult.get("result").asString();
    }
}
