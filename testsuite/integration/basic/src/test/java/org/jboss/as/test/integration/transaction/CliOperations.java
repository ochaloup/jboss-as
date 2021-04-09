/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.as.test.integration.transaction;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.io.IOException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

final class CliOperations {
    private static final Logger log = Logger.getLogger(CliOperations.class);

    private CliOperations() {
        // utility class
    }

    static final PathAddress TXN_SUBSYSTEM_ADDRESS = PathAddress.pathAddress().append(SUBSYSTEM, "transactions");

    static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) {
        try {
            final ModelNode result = client.execute(operation);
            checkSuccessful(result, operation);
            return result.get(ClientConstants.RESULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void checkSuccessful(final ModelNode result, final ModelNode operation) {
        if (!ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
            log.error("Operation " + operation + " did not succeed. Result was " + result);
            throw new RuntimeException("operation has failed: " + result.get(ClientConstants.FAILURE_DESCRIPTION).toString());
        }
    }
}
