/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.clustering.infinispan.subsystem;

import static org.jboss.as.clustering.infinispan.subsystem.InfinispanSubsystemResourceDefinition.*;

import java.util.ServiceLoader;

import org.jboss.as.clustering.controller.CapabilityServiceBuilder;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.infinispan.InfinispanLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.jgroups.spi.JGroupsRequirement;
import org.wildfly.clustering.service.ServiceNameProvider;
import org.wildfly.clustering.spi.GroupAliasBuilderProvider;
import org.wildfly.clustering.spi.GroupBuilderProvider;
import org.wildfly.clustering.spi.LocalGroupBuilderProvider;

/**
 * @author Paul Ferraro
 */
public class InfinispanSubsystemServiceHandler implements ResourceServiceHandler {

    @Override
    public void installServices(OperationContext context, ModelNode model) throws OperationFailedException {
        InfinispanLogger.ROOT_LOGGER.activatingSubsystem();

        PathAddress address = context.getCurrentAddress();
        ServiceTarget target = context.getServiceTarget();

        // Install local group services
        for (GroupBuilderProvider provider : ServiceLoader.load(LocalGroupBuilderProvider.class, LocalGroupBuilderProvider.class.getClassLoader())) {
            InfinispanLogger.ROOT_LOGGER.debugf("Installing %s for %s group", provider.getClass().getSimpleName(), LocalGroupBuilderProvider.LOCAL);
            for (CapabilityServiceBuilder<?> builder : provider.getBuilders(requirement -> LOCAL_CLUSTERING_CAPABILITIES.get(requirement).getServiceName(address), LocalGroupBuilderProvider.LOCAL)) {
                builder.configure(context).build(target).install();
            }
        }

        // If JGroups subsystem is not available, install default group aliases to local group.
        if (!context.hasOptionalCapability(JGroupsRequirement.CHANNEL.getDefaultRequirement().getName(), null, null)) {
            for (GroupAliasBuilderProvider provider : ServiceLoader.load(GroupAliasBuilderProvider.class, GroupAliasBuilderProvider.class.getClassLoader())) {
                for (CapabilityServiceBuilder<?> builder : provider.getBuilders(requirement -> CLUSTERING_CAPABILITIES.get(requirement).getServiceName(address), null, LocalGroupBuilderProvider.LOCAL)) {
                    builder.configure(context).build(target).install();
                }
            }
        }
    }

    @Override
    public void removeServices(OperationContext context, ModelNode model) throws OperationFailedException {
        PathAddress address = context.getCurrentAddress();

        for (GroupBuilderProvider provider : ServiceLoader.load(LocalGroupBuilderProvider.class, LocalGroupBuilderProvider.class.getClassLoader())) {
            for (ServiceNameProvider builder : provider.getBuilders(requirement -> LOCAL_CLUSTERING_CAPABILITIES.get(requirement).getServiceName(address), LocalGroupBuilderProvider.LOCAL)) {
                context.removeService(builder.getServiceName());
            }
        }

        if (!context.hasOptionalCapability(JGroupsRequirement.CHANNEL.getDefaultRequirement().getName(), null, null)) {
            for (GroupAliasBuilderProvider provider : ServiceLoader.load(GroupAliasBuilderProvider.class, GroupAliasBuilderProvider.class.getClassLoader())) {
                for (CapabilityServiceBuilder<?> builder : provider.getBuilders(requirement -> CLUSTERING_CAPABILITIES.get(requirement).getServiceName(address), null, LocalGroupBuilderProvider.LOCAL)) {
                    context.removeService(builder.getServiceName());
                }
            }
        }
    }
}
