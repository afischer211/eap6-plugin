package com.redhat.plugin.eap6.test;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import com.redhat.plugin.eap6.EAP6DeploymentStructureMojo;

public class EAP6DeploymentStructureMojoTest extends AbstractMojoTestCase {
    /** {@inheritDoc} */
    protected void setUp() throws Exception {
        // required
        super.setUp();
    }

    /** {@inheritDoc} */
    protected void tearDown() throws Exception {
        // required
        super.tearDown();
    }

    /**
     * @throws Exception
     */
    public void testMojoGoal() throws Exception {
        File pom = getTestFile("src/test/resources/unit/eap6-maven-plugin/pom.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());

        EAP6DeploymentStructureMojo mojo = (EAP6DeploymentStructureMojo) lookupMojo("build", pom);

        assertNotNull(mojo);
        mojo.execute();
    }
}
