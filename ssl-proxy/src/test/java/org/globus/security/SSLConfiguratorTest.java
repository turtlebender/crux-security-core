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

package org.globus.security;

import org.globus.security.filestore.FileBasedKeyStoreParameters;
import org.globus.security.provider.GlobusProvider;
import org.globus.security.resources.ResourceCertStoreParameters;
import org.globus.security.resources.ResourceSigningPolicyStore;
import org.globus.security.resources.ResourceSigningPolicyStoreParameters;
import org.globus.security.util.GlobusSSLConfigurationException;
import org.globus.security.util.SSLConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.testng.Assert.assertEquals;

@Test
public class SSLConfiguratorTest {

    private SSLSocket sslsocket;
    private SSLServerSocket serverSocket;
    private CountDownLatch latch = new CountDownLatch(1);
    private StringBuilder builder = new StringBuilder();

    @BeforeClass
    public void setup() throws Exception {
        Security.addProvider(new GlobusProvider());
    }

    @Test
    public void testConfig() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(GlobusProvider.KEYSTORE_TYPE, GlobusProvider.PROVIDER_NAME);

        FileBasedKeyStoreParameters keyParams = new FileBasedKeyStoreParameters(
                "classpath:/testTrustStore/*.0,classpath:/testTrustStore/*.9", "file:/tmp",
                "classpath:/testTrustStore/ca.9", "classpath:/testTrustStore/private.pem", null);

        keyStore.load(keyParams);

        ResourceCertStoreParameters certParams = new ResourceCertStoreParameters("classpath:/testTrustStore/*.r*");
        CertStore certStore = CertStore.getInstance(GlobusProvider.CERTSTORE_TYPE, certParams);
        ResourceSigningPolicyStoreParameters policyParams = new ResourceSigningPolicyStoreParameters(
                "classpath:/testTrustStore/*.signing_policy");
        ResourceSigningPolicyStore policyStore = new ResourceSigningPolicyStore(policyParams);
        SSLConfigurator config = new SSLConfigurator();
        config.setCertStore(certStore);
        config.setTrustStore(keyStore);
        config.setPolicyStore(policyStore);
        serverSocket = startServer(config);
        latch.await();
        sslsocket = runClient(config);
        OutputStream outputstream = sslsocket.getOutputStream();
        OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream);
        BufferedWriter bufferedwriter = new BufferedWriter(outputstreamwriter);
        bufferedwriter.write("hello");
        bufferedwriter.flush();
        assertEquals(builder.toString().trim(), "hello");
    }

    private SSLSocket runClient(SSLConfigurator config) throws IOException, GlobusSSLConfigurationException {
        SSLSocketFactory sslsocketfactory = config.createFactory();

        return (SSLSocket) sslsocketfactory.createSocket("localhost", 9999);
    }

    @AfterClass
    public void stop() throws Exception {
        serverSocket.close();
        sslsocket.close();
    }

    Logger logger = LoggerFactory.getLogger(SSLConfiguratorTest.class);

    private SSLServerSocket startServer(SSLConfigurator config) throws GlobusSSLConfigurationException, IOException {
        SSLServerSocketFactory sslserversocketfactory = config.createServerFactory();
        final SSLServerSocket sslserversocket = (SSLServerSocket) sslserversocketfactory.createServerSocket(9999);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(new Runnable() {
            /**
             * When an object implementing interface <code>Runnable</code> is used
             * to create a thread, starting the thread causes the object's
             * <code>run</code> method to be called in that separately executing
             * thread.
             * <p/>
             * The general contract of the method <code>run</code> is that it may
             * take any action whatsoever.
             *
             * @see Thread#run()
             */
            public void run() {
                latch.countDown();
                try {
                    SSLSocket sslsocket = (SSLSocket) sslserversocket.accept();
                    InputStream inputstream = sslsocket.getInputStream();
                    InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
                    BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
                    String line;
                    while ((line = bufferedreader.readLine()) != null) {
                        builder.append(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return sslserversocket;
    }
}