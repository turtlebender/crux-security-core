/*
 * Copyright 1999-2010 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.globus.security.filestore;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.security.Security;
import java.security.cert.CRL;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.security.auth.x500.X500Principal;

import org.globus.crux.security.util.DirSetupUtil;
import org.globus.security.SigningPolicy;
import org.globus.security.provider.GlobusProvider;
import org.globus.security.provider.SigningPolicyStore;
import org.globus.security.provider.SigningPolicyStoreParameters;
import org.globus.security.stores.ResourceCertStoreParameters;
import org.globus.security.stores.ResourceSigningPolicyStore;
import org.globus.security.stores.ResourceSigningPolicyStoreParameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * FILL ME
 * 
 * @author ranantha@mcs.anl.gov
 */
public class TestFileBasedTrustStore {

	static DirSetupUtil dir;
	static CertStoreParameters parameters;
	static CertStoreParameters directoryParameters;
	static CertStore certStore;
	static CertStoreParameters crlParameters;
	static SigningPolicyStoreParameters policyParameters;
	static Collection<? extends Certificate> trustAnchors;

	@BeforeClass
	public static void setUp() throws Exception {

		// FIXME: mock the actual reading of the files and test the idea that
		// modified is used and map is used to pull information.
		dir = new DirSetupUtil(new String[] { "testTrustStore/1c3f2ca8.0", "testTrustStore/b38b4d8c.0",
				"testTrustStore/d1b603c3.0", "testTrustStore/1c3f2ca8.r0", "testTrustStore/d1b603c3.r0",
				"testTrustStore/1c3f2ca8.signing_policy", "testTrustStore/b38b4d8c.signing_policy",
				"testTrustStore/d1b603c3.signing_policy" });
		dir.createTempDirectory();
		dir.copy();
		parameters = new ResourceCertStoreParameters("classpath:/testTrustStore/*.0,classpath:/testTrustStore/*.9",
				null);
		crlParameters = new ResourceCertStoreParameters(null, "classpath:/testTrustStore/*.r*");
		policyParameters = new ResourceSigningPolicyStoreParameters("classpath:/testTrustStore/*.signing_policy");
		directoryParameters = new ResourceCertStoreParameters("file:" + dir.getTempDirectory().getAbsolutePath()
				+ "/*.0", null);
		Security.addProvider(new GlobusProvider());
	}

	@Test
	public void testEngineGetCertificates() throws Exception {

//		File tempDir = dir.getTempDirectory();

		// number of CA files
		// String[] caFiles = tempDir.list(new TrustAnchorFilter());

		// Get comparison parameters
		certStore = CertStore.getInstance("PEMFilebasedCertStore", parameters);

		assert certStore != null;

		trustAnchors = certStore.getCertificates(new X509CertSelector());

		assert trustAnchors != null;

		assertTrue(trustAnchors.size() > 0);

		// assert caFiles != null;

		assertThat(trustAnchors.size(), is(3));

		for (Certificate trustAnchor : trustAnchors) {

			assert (trustAnchor instanceof X509Certificate);

		}

		// FIXME: figure out whether reload functions as expected

	}

	@Test
	public void testEngineGetCertificatesDirectory() throws Exception {
		File tempDir = this.dir.getTempDirectory();
		// number of CA files
		// String[] caFiles = tempDir.list(new TrustAnchorFilter());
		this.certStore = CertStore.getInstance("PEMFilebasedCertStore", directoryParameters);

		assert certStore != null;

		this.trustAnchors = certStore.getCertificates(new X509CertSelector());

		assert trustAnchors != null;

		assertTrue(trustAnchors.size() > 0);

		// assert caFiles != null;

		assertTrue(trustAnchors.size() == 3);

		for (Certificate trustAnchor : trustAnchors) {

			assertThat(trustAnchor, instanceOf(X509Certificate.class));

		}

	}

	public static class CrlFilter implements FilenameFilter {

		public boolean accept(File dir, String file) {

			if (file == null) {
				throw new IllegalArgumentException();
			}

			int length = file.length();
			return length > 3 && file.charAt(length - 3) == '.' && file.charAt(length - 2) == 'r'
					&& file.charAt(length - 1) >= '0' && file.charAt(length - 1) <= '9';

		}
	}

	@Test
	public void testEngineGetCRLs() throws Exception {

		File tempDir = dir.getTempDirectory();
		// number of CRL files
		String[] crlFiles = tempDir.list(new CrlFilter());

		// Get comparison parameters
		certStore = CertStore.getInstance("PEMFilebasedCertStore", crlParameters);

		assert certStore != null;

		Collection<? extends CRL> crls = certStore.getCRLs(null);

		assertThat(crls, not(nullValue()));

		assertTrue(crls.size() > 0);

		assert crlFiles != null;

		assertThat(crls.size(), is(crlFiles.length));

		for (CRL crl : crls) {

			assertThat(crl, instanceOf(X509CRL.class));

		}

		// FIXME: figure out whether reload functions as expected
	}

	@Test
	public void testGetSigningPolicies() throws Exception {

		SigningPolicyStore store = new ResourceSigningPolicyStore(policyParameters);

		SigningPolicy policy = store.getSigningPolicy(null);

		assert (policy == null);

		policy = store.getSigningPolicy(new X500Principal("C=US, CN=Foo"));

		assert (policy == null);

		for (Certificate trustAnchor : trustAnchors) {

			X509Certificate certificate = (X509Certificate) trustAnchor;

			X500Principal principal = certificate.getIssuerX500Principal();

			policy = store.getSigningPolicy(principal);

			assert (policy != null);

			assert (policy.getAllowedDNs() != null);
		}

		// FIXME: figure out whether reload functions as expected
	}

	@AfterClass
	public static void tearDown() throws Exception {
		dir.delete();
	}
}
