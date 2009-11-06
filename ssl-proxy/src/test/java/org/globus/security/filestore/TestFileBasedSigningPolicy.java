/*
 * Copyright 1999-2006 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.security.filestore;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;

import org.globus.security.SigningPolicy;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * FILL ME
 *
 * @author ranantha@mcs.anl.gov
 */
@Test(groups = {"AbstractFileBasedStore"})
public class TestFileBasedSigningPolicy {

    FileSetupUtil testPolicy1;

    @BeforeClass
    public void setUp() throws Exception {

        // FIXME paramters
        this.testPolicy1 =
                new FileSetupUtil(
                        "certificateUtilTest/validPolicy1.signing_policy");
    }


    @Test
    public void testSigningPolicy() throws Exception {

        this.testPolicy1.copyFileToTemp();

        String tempFileName = this.testPolicy1.getTempFilename();

        FileBasedSigningPolicy filePolicy =
                new FileBasedSigningPolicy(new File(tempFileName));

//        assert (filePolicy != null);

        Collection<SigningPolicy> policies = filePolicy.getSigningPolicies();

        assert (policies != null);

        assert (policies.size() == 2);

        // assert policy values here
        assertFalse(filePolicy.hasChanged());

        policies = filePolicy.getSigningPolicies();

        assert (policies != null);

        assertFalse(filePolicy.hasChanged());

        testPolicy1.modifyFile();

        policies = filePolicy.getSigningPolicies();

        assert (policies != null);

        assertTrue(filePolicy.hasChanged());
    }

    @Test
    public void testPolicyFilter() {

        FilenameFilter filter = FileBasedSigningPolicy.getSigningPolicyFilter();

        // Null checks
        boolean worked = false;
        try {
            filter.accept(null, null);
        } catch (IllegalArgumentException e) {
            worked = true;
        }
        assert worked;

        // null dir name
        assert (filter.accept(null, "foo.signing_policy"));

        // dir name ignored
        assert (filter.accept(new File("bar"), "foo.signing_policy"));

        assertFalse(filter.accept(null, "foo.r"));

        assertFalse(filter.accept(null, "foo.SIGNING_POLICY"));

        assertFalse(filter.accept(null, "foo.signing"));

    }

    @AfterTest
    public void tearDown() throws Exception {
        this.testPolicy1.deleteFile();
    }

}