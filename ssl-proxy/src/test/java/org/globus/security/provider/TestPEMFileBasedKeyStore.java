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
package org.globus.security.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

import org.globus.security.X509Credential;
import org.globus.security.filestore.DirSetupUtil;
import org.globus.security.filestore.FileBasedKeyStoreParameters;
import org.globus.security.filestore.FileSetupUtil;
import org.globus.security.util.CertificateLoadUtil;

import org.springframework.core.io.FileSystemResource;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * FILL ME
 *
 * @author ranantha@mcs.anl.gov
 */
public class TestPEMFileBasedKeyStore {

    DirSetupUtil trustedDirectory;
    DirSetupUtil defaultTrustedDirectory;
    Vector<X509Certificate> testTrustedCertificates = new Vector<X509Certificate>();
    FileSetupUtil proxyFile1;
    FileSetupUtil proxyFile2;

    Map<FileSetupUtil, X509Certificate> trustedCertificates = new HashMap<FileSetupUtil, X509Certificate>();
    Map<FileSetupUtil, X509Credential> proxyCertificates = new HashMap<FileSetupUtil, X509Credential>();

    @BeforeClass
    public void setUp() throws Exception {

        ClassLoader loader = TestPEMFileBasedKeyStore.class.getClassLoader();

        String[] trustedCertFilenames =
                new String[]{"testTrustStore/1c3f2ca8.0", "testTrustStore/b38b4d8c.0"};
        this.trustedDirectory = new DirSetupUtil(trustedCertFilenames);
        this.trustedDirectory.createTempDirectory();
        this.trustedDirectory.copy();
        for (String trustedCertFilename : trustedCertFilenames) {
            InputStream in = null;
            try {
                in = loader.getResourceAsStream(trustedCertFilename);

                if (in == null) {
                    throw new Exception("Unable to load: " + trustedCertFilename);
                }
                this.trustedCertificates.put(
                        this.trustedDirectory.getFileSetupUtil(trustedCertFilename),
                        CertificateLoadUtil.loadCertificate(in));
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }

        String[] defaultTrustedCert = new String[]{
                "testTrustStore/d1b603c3.0"};
        this.defaultTrustedDirectory = new DirSetupUtil(defaultTrustedCert);
        this.defaultTrustedDirectory.createTempDirectory();
        this.defaultTrustedDirectory.copy();
        for (String aDefaultTrustedCert : defaultTrustedCert) {
            InputStream in = null;
            try {
                in = loader.getResourceAsStream(aDefaultTrustedCert);
                if (in == null) {
                    throw new Exception("Unable to load: " + aDefaultTrustedCert);
                }
                this.trustedCertificates.put(
                        this.defaultTrustedDirectory.getFileSetupUtil(aDefaultTrustedCert),
                        CertificateLoadUtil.loadCertificate(in));
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }


        String proxyFilename1 = "validatorTest/gsi2fullproxy.pem";
        this.proxyFile1 = new FileSetupUtil(proxyFilename1);
        this.proxyFile1.copyFileToTemp();
        this.proxyCertificates.put(this.proxyFile1,
                new X509Credential(loader.getResourceAsStream(proxyFilename1)));

        String proxyFilename2 = "validatorTest/gsi2limitedproxy.pem";
        this.proxyFile2 = new FileSetupUtil(proxyFilename2);
        this.proxyFile2.copyFileToTemp();
        this.proxyCertificates.put(this.proxyFile2,
                new X509Credential(loader.getResourceAsStream(proxyFilename2)));

        Security.addProvider(new GlobusProvider());
    }

    @Test
    public void testCreationDate() throws Exception {
        KeyStore store = KeyStore.getInstance("PEMFilebasedKeyStore", "Globus");

        // Parameters in properties file
        Properties properties = new Properties();
        properties.setProperty(FileBasedKeyStore.DEFAULT_DIRECTORY_KEY,
                "file:" + this.defaultTrustedDirectory.getTempDirectoryName() + "/*.0");
        properties.setProperty(FileBasedKeyStore.DIRECTORY_LIST_KEY, "file:" + this.trustedDirectory.getTempDirectoryName() + "/*.0");

        InputStream ins = null;
        try {
            ins = getProperties(properties);
            store.load(ins, null);
        } finally {
            if (ins != null) {
                ins.close();
            }
        }
        Enumeration<String> aliases = store.aliases();
        if (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            assertNotNull(store.getCreationDate(alias));
        }
        assertNull(store.getCreationDate("FakeAlias"));

    }

    @Test(dependsOnMethods = {"testParameterLoad"})
    public void testTrustedCerts() throws Exception {

        FileBasedKeyStore store = new FileBasedKeyStore();

        // Parameters in properties file
        Properties properties = new Properties();
        properties.setProperty(FileBasedKeyStore.DEFAULT_DIRECTORY_KEY,
                "file:" + this.defaultTrustedDirectory.getTempDirectoryName() + "/*.0");
        properties.setProperty(FileBasedKeyStore.DIRECTORY_LIST_KEY, "file:" + this.trustedDirectory.getTempDirectoryName() + "/*.0");

        InputStream ins = null;
        try {
            ins = getProperties(properties);
            store.engineLoad(ins, null);
        } finally {
            if (ins != null) {
                ins.close();
            }
        }
        testLoadedStore(store);
        Iterator<FileSetupUtil> iterator = this.trustedCertificates.keySet().iterator();
        FileSetupUtil util = iterator.next();
        testDelete(store, util.getTempFilename(), util);
    }

    @Test
    public void testParameterLoad() throws Exception {
        FileBasedKeyStore keystore = loadFromParameters();
        testLoadedStore(keystore);
    }

    private FileBasedKeyStore loadFromParameters() throws Exception {
        FileBasedKeyStoreParameters params = new FileBasedKeyStoreParameters();
        params.setDefaultCertDir("file:" + this.defaultTrustedDirectory.getTempDirectoryName() + "/*.0");
        params.setCertDirs("file:" + this.trustedDirectory.getTempDirectoryName() + "/*.0");
        FileBasedKeyStore keystore = new FileBasedKeyStore();
        keystore.engineLoad(params);
        return keystore;
    }

//    @Test
    public void testOutputStore() throws Exception {
        FileBasedKeyStore existingKeyStore = loadFromParameters();
        //Create new KeyStore to test
        FileBasedKeyStoreParameters params = new FileBasedKeyStoreParameters();
        params.setDefaultCertDir("file:" + System.getProperty("java.io.tmpdir") + File.separator + "pemOutputStore");
        FileBasedKeyStore newKeyStore = new FileBasedKeyStore();
        newKeyStore.engineLoad(params);
        Enumeration<String> enumeration = existingKeyStore.engineAliases();
        Certificate cert = null;
        if(enumeration.hasMoreElements()){
            cert = existingKeyStore.engineGetCertificate(enumeration.nextElement());
        }
        String alias = "file:" + System.getProperty("java.io.tmpdir") + File.separator + "pemOutputStore" + File.separator + "blah.0";
        newKeyStore.engineSetCertificateEntry(alias,cert);
        newKeyStore.engineStore(null, null);

    }

    private void testLoadedStore(FileBasedKeyStore store) throws KeyStoreException {
        Enumeration aliases = store.engineAliases();
        assertTrue(aliases.hasMoreElements());

        // alias to certificate test to be added.
        Iterator<FileSetupUtil> iterator = this.trustedCertificates.keySet().iterator();
        String alias;
        FileSetupUtil util;
        while (iterator.hasNext()) {
            util = iterator.next();
            alias = util.getTempFilename();
            assertTrue(store.engineIsCertificateEntry(alias));
            Certificate certificate = store.engineGetCertificate(alias);
            assertNotNull(certificate);
            assertEquals(certificate, this.trustedCertificates.get(util));
            String storeAlias = store.engineGetCertificateAlias(certificate);
            assertEquals(alias, storeAlias);
        }
        assertFalse(store.engineIsCertificateEntry("FakeCert"));
    }

    private void testDelete(FileBasedKeyStore store, String alias, FileSetupUtil util) throws Exception {
        // test delete
        store.engineDeleteEntry(alias);

        assertNull(store.engineGetCertificate(alias));
        assertNotNull(util);
        File tempFile = util.getTempFile();
        assertNotNull(tempFile);
        assertFalse(util.getTempFile() == null);

        assertFalse(util.getTempFile().exists());
    }

    @Test
    public void testProxyCerts() throws Exception {

        FileBasedKeyStore store = new FileBasedKeyStore();
        // Parameters in properties file
        Properties properties = new Properties();
        properties.setProperty(FileBasedKeyStore.PROXY_FILENAME,
                "file:" + this.proxyFile1.getAbsoluteFilename());
        InputStream ins = null;
        try {
            ins = getProperties(properties);
            store.engineLoad(ins, null);
        } finally {
            if (ins != null)
                ins.close();
        }

        Enumeration aliases = store.engineAliases();
        assert (aliases.hasMoreElements());

        // proxy file 1
        String proxyId1 = new FileSystemResource(this.proxyFile1.getTempFile()).getURL().toExternalForm();
        assertTrue(store.engineIsKeyEntry(proxyId1));
        Key key = store.engineGetKey(proxyId1, null);
        assertNotNull(key != null);
        assertTrue(key instanceof PrivateKey);

        Certificate[] certificates = store.engineGetCertificateChain(this.proxyFile1.getURL().toExternalForm());
        assertNotNull(certificates != null);
        assertTrue(certificates instanceof X509Certificate[]);
        key = null;
        //     assert (this.proxyCertificates.get(this.proxyFile1.getAbsoluteFilename()).equals(certificates[0]));

        properties.setProperty(FileBasedKeyStore.PROXY_FILENAME,
                "file:" + this.proxyFile2.getAbsoluteFilename());
        ins = null;
        try {
            ins = getProperties(properties);
            store.engineLoad(ins, null);
        } finally {
            if (ins != null)
                ins.close();
        }
        // proxy file 2
        String proxyId2 = new FileSystemResource(this.proxyFile2.getTempFile()).getURL().toExternalForm();
        key = store.engineGetKey(proxyId2, null);
        assertTrue(store.engineIsKeyEntry(proxyId2));
        assertNotNull(key);
        assertTrue(key instanceof PrivateKey);

        certificates = store.engineGetCertificateChain(proxyId1);
        assertNotNull(certificates != null);
        assertTrue(certificates instanceof X509Certificate[]);

//        assert (this.proxyCertificates.get(this.proxyFile2.getTempFilename()).equals(certificates[0]));


        // test delete
        store.engineDeleteEntry(proxyId1);

        certificates = store.engineGetCertificateChain(proxyId1);
        assertNull(certificates);
        assertFalse(this.proxyFile1.getTempFile().exists());
        assertFalse(store.engineIsKeyEntry(proxyId1));

    }

    private InputStream getProperties(Properties properties) throws Exception {

        ByteArrayOutputStream stream = null;
        ByteArrayInputStream ins = null;

        try {
            stream = new ByteArrayOutputStream();
            properties.store(stream, "Test Properties");

            // load all the CA files
            ins = new ByteArrayInputStream(stream.toByteArray());

        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        return ins;
    }

    @AfterTest
    public void tearDown() throws Exception {
        this.defaultTrustedDirectory.delete();
        this.trustedDirectory.delete();
        this.proxyFile1.deleteFile();
        this.proxyFile2.deleteFile();
    }
}
