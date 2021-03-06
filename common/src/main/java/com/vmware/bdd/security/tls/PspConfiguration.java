/******************************************************************************
 *   Copyright (c) 2012-2014 VMware, Inc. All Rights Reserved.
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *****************************************************************************/
package com.vmware.bdd.security.tls;

/**
* Created By xiaoliangl on 11/28/14.
*/
public class PspConfiguration {
   /**
    * Application defined cipher suites and protocols
    */
   private final String[] CIPHER_SUITES = {
         "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
         "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
         "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
         "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
         "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
         "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
         "TLS_RSA_WITH_AES_256_CBC_SHA",
         "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
         "TLS_RSA_WITH_AES_128_CBC_SHA",
         "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
         "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
         "TLS_DHE_DSS_WITH_AES_128_CBC_SHA"};

   private final String[] SSL_PROTOCOLS = {"TLSv1", "TLSv1.1", "TLSv1.2" };

   private String[] supportedCipherSuites;
   private String[] supportedProtocols;
   private String SSLContextAlgorithm;

   public PspConfiguration() {
      /**
       * Add your custom configuration here. Alternatively, you could add it
       * outside too, but adding it here makes it cleaner.
       */
      this.setSupportedCipherSuites(CIPHER_SUITES);
      this.setSupportedProtocols(SSL_PROTOCOLS);
      this.setSSLContextAlgorithm("TLS");
   }


   public String getSSLContextAlgorithm() {
      return SSLContextAlgorithm;
   }

   public String[] getSupportedCipherSuites() {
      return supportedCipherSuites;
   }

   public String[] getSupportedProtocols() {
      return supportedProtocols;
   }

   public void setSupportedCipherSuites(String[] supportedCipherSuites) {
      this.supportedCipherSuites = supportedCipherSuites;
   }

   public void setSupportedProtocols(String[] supportedProtocols) {
      this.supportedProtocols = supportedProtocols;
   }

   public void setSSLContextAlgorithm(String SSLContextAlgorithm) {
      this.SSLContextAlgorithm = SSLContextAlgorithm;
   }
}
