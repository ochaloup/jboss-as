package org.jboss.as.test.manualmode.transaction;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class TransactionRecoverOperationTestCase extends AbstractCliTestBase {
}
