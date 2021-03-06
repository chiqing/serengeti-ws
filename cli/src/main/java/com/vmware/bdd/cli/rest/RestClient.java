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
package com.vmware.bdd.cli.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.vmware.bdd.cli.auth.LoginClientImpl;
import com.vmware.bdd.cli.auth.LoginResponse;
import jline.console.ConsoleReader;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.fusesource.jansi.AnsiConsole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import com.vmware.bdd.apitypes.Connect;
import com.vmware.bdd.apitypes.TaskRead;
import com.vmware.bdd.apitypes.TaskRead.Status;
import com.vmware.bdd.apitypes.TaskRead.Type;
import com.vmware.bdd.cli.commands.CommandsUtils;
import com.vmware.bdd.cli.commands.Constants;
import com.vmware.bdd.cli.commands.CookieCache;
import com.vmware.bdd.cli.config.RunWayConfig;
import com.vmware.bdd.cli.config.RunWayConfig.RunType;
import com.vmware.bdd.exception.BddException;
import com.vmware.bdd.utils.CommonUtil;

/**
 * RestClient provides common rest apis required by resource operations.
 * 
 */
@Component
public class RestClient {

   static final Logger logger = Logger.getLogger(RestClient.class);

   private String hostUri;

   @Autowired
   private RestTemplate client;

   @Autowired
   private LoginClientImpl loginClient;

   static {
      trustSSLCertificate();
   }

   private RestClient() {
   }

   /**
    * connect to a Serengeti server
    * 
    * @param host
    *           host url with optional port
    * @param username
    *           serengeti login user name
    * @param password
    *           serengeti password
    */
   public Connect.ConnectType connect(final String host, final String username,
         final String password) {
      String oldHostUri = hostUri;

      hostUri =
            Constants.HTTPS_CONNECTION_PREFIX + host
                  + Constants.HTTPS_CONNECTION_LOGIN_SUFFIX;

      Connect.ConnectType connectType = null;
      try {
         LoginResponse response = loginClient.login(hostUri, username, password);
         //200
         if (response.getResponseCode() == HttpStatus.OK.value()) {
            if(CommonUtil.isBlank(response.getSessionId())) {
               if (isConnected()) {
                  System.out.println(Constants.CONNECTION_ALREADY_ESTABLISHED);
                  connectType = Connect.ConnectType.SUCCESS;
               } else {
                  System.out.println(Constants.CONNECT_FAILURE_NO_SESSION_ID);
                  connectType = Connect.ConnectType.ERROR;
               }
            } else {
               //normal response
               writeCookieInfo(response.getSessionId());
               System.out.println(Constants.CONNECT_SUCCESS);
               connectType = Connect.ConnectType.SUCCESS;
            }
         }
         //401
         else if(response.getResponseCode() == HttpStatus.UNAUTHORIZED.value()) {
            System.out.println(Constants.CONNECT_UNAUTHORIZATION_CONNECT);
            //recover old hostUri
            hostUri = oldHostUri;
            connectType = Connect.ConnectType.UNAUTHORIZATION;
         }
         //500
         else if(response.getResponseCode() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            System.out.println(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
            connectType = Connect.ConnectType.ERROR;
         } else {
            //error
            System.out.println(
                  String.format(
                  Constants.UNSUPPORTED_HTTP_RESPONSE_CODE, response.getResponseCode()));
            //recover old hostUri
            hostUri = oldHostUri;
            connectType = Connect.ConnectType.ERROR;
         }
      } catch (Exception e) {
         System.out.println(Constants.CONNECT_FAILURE + ": "
               + (CommandsUtils.getExceptionMessage(e)));
         connectType = Connect.ConnectType.ERROR;
      }

      return connectType;
   }

   private boolean isConnected() {
      return (hostUri != null) && (!CommandsUtils.isBlank(readCookieInfo()));
   }

   /**
    * Disconnect the session
    */
   public void disconnect() {
      try {
         checkConnection();
         logout(Constants.REST_PATH_LOGOUT, String.class);
      } catch (CliRestException cliRestException) {
         if (cliRestException.getStatus() == HttpStatus.UNAUTHORIZED) {
            writeCookieInfo("");
         }
      } catch (Exception e) {
         System.out.println(Constants.DISCONNECT_FAILURE + ": "
               + CommandsUtils.getExceptionMessage(e));
      }
   }

   private void writeCookieInfo(String cookie) {
      CookieCache.put(CookieCache.COOKIE, cookie);
   }

   private String readCookieInfo() {
      String cookieValue = "";
      cookieValue = CookieCache.get(CookieCache.COOKIE);
      return cookieValue;
   }

   private <T> ResponseEntity<T> restGetById(final String path,
         final String id, final Class<T> respEntityType,
         final boolean hasDetailQueryString) {
      String targetUri =
            hostUri + Constants.HTTPS_CONNECTION_API + path + "/" + id;
      if (hasDetailQueryString) {
         targetUri += Constants.QUERY_DETAIL;
      }
      return restGetByUri(targetUri, respEntityType);
   }

   private <T> ResponseEntity<T> restGet(final String path,
         final Class<T> respEntityType, final boolean hasDetailQueryString) {
      String targetUri = hostUri + Constants.HTTPS_CONNECTION_API + path;
      if (hasDetailQueryString) {
         targetUri += Constants.QUERY_DETAIL;
      }
      return restGetByUri(targetUri, respEntityType);
   }

   private <T> ResponseEntity<T> logout(final String path,
         final Class<T> respEntityType) {
      StringBuilder uriBuff = new StringBuilder();
      uriBuff.append(hostUri).append(path);
      return restGetByUri(uriBuff.toString(), respEntityType);
   }

   private <T> ResponseEntity<T> restGetByUri(String uri,
         Class<T> respEntityType) {
      HttpHeaders headers = buildHeaders();
      HttpEntity<String> entity = new HttpEntity<String>(headers);

      return client.exchange(uri, HttpMethod.GET, entity, respEntityType);
   }

   private HttpHeaders buildHeaders(boolean withCookie) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      List<MediaType> acceptedTypes = new ArrayList<MediaType>();
      acceptedTypes.add(MediaType.APPLICATION_JSON);
      acceptedTypes.add(MediaType.TEXT_HTML);
      headers.setAccept(acceptedTypes);

      if (withCookie) {
         String cookieInfo = readCookieInfo();
         headers.add("Cookie", cookieInfo == null ? "" : cookieInfo);
      }
      return headers;
   }

   private HttpHeaders buildHeaders() {
      return buildHeaders(true);
   }

   /**
    * Create an object through rest apis
    * 
    * @param entity
    *           the creation content
    * @param path
    *           the rest url
    * @param verb
    *           the http method
    * @param prettyOutput
    *           output callback
    */
   public void createObject(Object entity, final String path,
         final HttpMethod verb, PrettyOutput... prettyOutput) {
      checkConnection();
      try {
         if (verb == HttpMethod.POST) {
            ResponseEntity<String> response = restPost(path, entity);
            if (!validateAuthorization(response)) {
               return;
            }
            processResponse(response, HttpMethod.POST, prettyOutput);
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }

      } catch (Exception e) {
         if(e instanceof CliRestException) {
            throw (CliRestException)e;
         }

         if(e instanceof BddException) {
            throw (BddException)e;
         }

         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   private ResponseEntity<String> restPost(String path, Object entity) {
      String targetUri = hostUri + Constants.HTTPS_CONNECTION_API + path;

      HttpHeaders headers = buildHeaders();
      HttpEntity<Object> postEntity = new HttpEntity<Object>(entity, headers);

      return client.exchange(targetUri, HttpMethod.POST, postEntity,
            String.class);
   }

   /*
    * Will process normal response with/without a task location header
    */
   private TaskRead processResponse(ResponseEntity<String> response,
         HttpMethod verb, PrettyOutput... prettyOutput) throws Exception {

      HttpStatus responseStatus = response.getStatusCode();
      if (responseStatus == HttpStatus.ACCEPTED) {//Accepted with task in the location header
         //get task uri from response to trace progress
         HttpHeaders headers = response.getHeaders();
         URI taskURI = headers.getLocation();
         String[] taskURIs = taskURI.toString().split("/");
         String taskId = taskURIs[taskURIs.length - 1];

         TaskRead taskRead;
         int oldProgress = 0;
         Status oldTaskStatus = null;
         Status taskStatus = null;
         int progress = 0;
         do {
            ResponseEntity<TaskRead> taskResponse =
                  restGetById(Constants.REST_PATH_TASK, taskId, TaskRead.class,
                        false);

            //task will not return exception as it has status
            taskRead = taskResponse.getBody();

            progress = (int) (taskRead.getProgress() * 100);
            taskStatus = taskRead.getStatus();

            //fix cluster deletion exception
            Type taskType = taskRead.getType();
            if ((taskType == Type.DELETE) && (taskStatus == TaskRead.Status.COMPLETED)) {
               clearScreen();
               System.out.println(taskStatus + " " + progress + "%\n");
               break;
            }

            if (taskType == Type.SHRINK && !taskRead.getFailNodes().isEmpty()) {
               throw new CliRestException(taskRead.getFailNodes().get(0).getErrorMessage());
            }

            if ((prettyOutput != null && prettyOutput.length > 0 && (taskRead.getType() == Type.VHM ? prettyOutput[0]
                  .isRefresh(true) : prettyOutput[0].isRefresh(false)))
                  || oldTaskStatus != taskStatus
                  || oldProgress != progress) {
               //clear screen and show progress every few seconds 
               clearScreen();
               //output completed task summary first in the case there are several related tasks
               if (prettyOutput != null && prettyOutput.length > 0
                     && prettyOutput[0].getCompletedTaskSummary() != null) {
                  for (String summary : prettyOutput[0]
                        .getCompletedTaskSummary()) {
                     System.out.println(summary + "\n");
                  }
               }
               System.out.println(taskStatus + " " + progress + "%\n");

               if (prettyOutput != null && prettyOutput.length > 0) {
                  // print call back customize the detailed output case by case
                  prettyOutput[0].prettyOutput();
               }

               if (oldTaskStatus != taskStatus || oldProgress != progress) {
                  oldTaskStatus = taskStatus;
                  oldProgress = progress;
                  if (taskRead.getProgressMessage() != null) {
                     System.out.println(taskRead.getProgressMessage());
                  }
               }
            }
            try {
               Thread.sleep(3 * 1000);
            } catch (InterruptedException ex) {
               //ignore
            }
         } while (taskStatus != TaskRead.Status.COMPLETED
               && taskStatus != TaskRead.Status.FAILED
               && taskStatus != TaskRead.Status.ABANDONED
               && taskStatus != TaskRead.Status.STOPPED);

         String errorMsg = taskRead.getErrorMessage();
         if (!taskRead.getStatus().equals(TaskRead.Status.COMPLETED)) {
            throw new CliRestException(errorMsg);
         } else { //completed
            if (taskRead.getType().equals(Type.VHM)) {
               logger.info("task type is vhm");
               Thread.sleep(5*1000);
               if (prettyOutput != null && prettyOutput.length > 0
                     && prettyOutput[0].isRefresh(true)) {
                  //clear screen and show progress every few seconds
                  clearScreen();
                  System.out.println(taskStatus + " " + progress + "%\n");

                  // print call back customize the detailed output case by case
                  if (prettyOutput != null && prettyOutput.length > 0) {
                     prettyOutput[0].prettyOutput();
                  }
               }
            } else {
               return taskRead;
            }
         }
      }
      return null;
   }

   private void clearScreen() {
      AnsiConsole.systemInstall();
      String separator = "[";
      char ESC = 27;
      String clearScreen = "2J";
      System.out.print(ESC + separator + clearScreen);
      AnsiConsole.systemUninstall();
   }

   /**
    * Generic method to get an object by id
    * 
    * @param id
    * @param entityType
    *           the object type
    * @param path
    *           the rest url
    * @param verb
    *           the http method
    * @param detail
    *           flag to retrieve detailed information or not
    * @return the object
    */
   public <T> T getObject(final String id, Class<T> entityType,
         final String path, final HttpMethod verb, final boolean detail) {
      checkConnection();
      try {
         if (verb == HttpMethod.GET) {
            ResponseEntity<T> response =
                  restGetById(path, id, entityType, detail);
            if (!validateAuthorization(response)) {
               return null;
            }
            T objectRead = response.getBody();

            return objectRead;
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }
      } catch (Exception e) {
         if(e instanceof CliRestException) {
            throw (CliRestException)e;
         }

         if(e instanceof BddException) {
            throw (BddException)e;
         }

         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   public <T> T getObject(final String path, Class<T> entityType, final HttpMethod verb, Object body) {
      checkConnection();
      try {
         if (verb == HttpMethod.POST) {
            ResponseEntity<T> response = this.restQueryWithBody(path, entityType, body);
            if (!validateAuthorization(response)) {
               return null;
            }
            T objectRead = response.getBody();

            return objectRead;
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }
      } catch (Exception e) {
         if(e instanceof CliRestException) {
            throw (CliRestException)e;
         }

         if(e instanceof BddException) {
            throw (BddException)e;
         }

         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   /**
    * Method to get by path
    * 
    * @param entityType
    * @param path
    * @param verb
    * @param detail
    * @return
    */
   public <T> T getObjectByPath(Class<T> entityType, final String path,
         final HttpMethod verb, final boolean detail) {
      checkConnection();

      try {
         if (verb == HttpMethod.GET) {
            ResponseEntity<T> response = restGet(path, entityType, detail);

            T objectRead = response.getBody();

            return objectRead;
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }
      } catch (Exception e) {
         if(e instanceof CliRestException) {
            throw (CliRestException)e;
         }

         if(e instanceof BddException) {
            throw (BddException)e;
         }

         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   /**
    * Generic method to get all objects of a type
    * 
    * @param entityType
    *           object type
    * @param path
    *           the rest url
    * @param verb
    *           the http method
    * @param detail
    *           flag to retrieve detailed information or not
    * @return the objects
    */
   public <T> T getAllObjects(final Class<T> entityType, final String path,
         final HttpMethod verb, final boolean detail) {
      checkConnection();
      try {
         if (verb == HttpMethod.GET) {
            ResponseEntity<T> response = restGet(path, entityType, detail);
            if (!validateAuthorization(response)) {
               return null;
            }
            T objectsRead = response.getBody();

            return objectsRead;
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }
      } catch (Exception e) {
         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   /**
    * Delete an object by id
    * 
    * @param id
    * @param path
    *           the rest url
    * @param verb
    *           the http method
    * @param prettyOutput
    *           utput callback
    */
   public void deleteObject(final String id, final String path,
         final HttpMethod verb, PrettyOutput... prettyOutput) {
      checkConnection();
      try {
         if (verb == HttpMethod.DELETE) {
            ResponseEntity<String> response = restDelete(path, id);
            if (!validateAuthorization(response)) {
               return;
            }
            processResponse(response, HttpMethod.DELETE, prettyOutput);
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }

      } catch (Exception e) {
         if(e instanceof CliRestException) {
            throw (CliRestException)e;
         }

         if(e instanceof BddException) {
            throw (BddException)e;
         }

         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   private ResponseEntity<String> restDelete(String path, String id) {
      String targetUri =
            hostUri + Constants.HTTPS_CONNECTION_API + path + "/" + id;

      HttpHeaders headers = buildHeaders();
      HttpEntity<String> entity = new HttpEntity<String>(headers);

      return client
            .exchange(targetUri, HttpMethod.DELETE, entity, String.class);
   }

   private void checkConnection() {
      if (hostUri == null) {
         throw new CliRestException(Constants.NEED_CONNECTION);
      } else if (CommandsUtils.isBlank(readCookieInfo())) {
         throw new CliRestException(Constants.CONNECT_CHECK_LOGIN);
      }
   }

   /**
    * process requests with query parameters
    * 
    * @param id
    * @param path
    *           the rest url
    * @param verb
    *           the http method
    * @param queryStrings
    *           required query strings
    * @param prettyOutput
    *           output callback
    */
   public void actionOps(final String id, final String path,
         final HttpMethod verb, final Map<String, String> queryStrings,
         PrettyOutput... prettyOutput) {
      checkConnection();
      try {
         if (verb == HttpMethod.PUT) {
            ResponseEntity<String> response =
                  restActionOps(path, id, queryStrings);
            if (!validateAuthorization(response)) {
               return;
            }
            processResponse(response, HttpMethod.PUT, prettyOutput);
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }

      } catch (Exception e) {
         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   private ResponseEntity<String> restActionOps(String path, String id,
         Map<String, String> queryStrings) {
      String targetUri =
            hostUri + Constants.HTTPS_CONNECTION_API + path + "/" + id;
      if (queryStrings != null) {
         targetUri = targetUri + buildQueryStrings(queryStrings);
      }
      HttpHeaders headers = buildHeaders();
      HttpEntity<String> entity = new HttpEntity<String>(headers);

      return client.exchange(targetUri, HttpMethod.PUT, entity, String.class);
   }

   private String buildQueryStrings(Map<String, String> queryStrings) {
      StringBuilder stringBuilder = new StringBuilder("?");

      Set<Entry<String, String>> entryset = queryStrings.entrySet();
      for (Entry<String, String> entry : entryset) {
         stringBuilder.append(entry.getKey() + "=" + entry.getValue() + "&");
      }
      int length = stringBuilder.length();
      if (stringBuilder.charAt(length - 1) == '&') {
         return stringBuilder.substring(0, length - 1);
      } else {
         return stringBuilder.toString();
      }
   }

   /**
    * Update an object
    * 
    * @param entity
    *           the updated content
    * @param path
    *           the rest url
    * @param verb
    *           the http method
    * @param prettyOutput
    *           output callback
    */
   public void update(Object entity, final String path, final HttpMethod verb,
         PrettyOutput... prettyOutput) {
      checkConnection();
      try {
         if (verb == HttpMethod.PUT) {
            ResponseEntity<String> response = restUpdate(path, entity);
            if (!validateAuthorization(response)) {
               return;
            }
            processResponse(response, HttpMethod.PUT, prettyOutput);
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }

      } catch (Exception e) {
         if(e instanceof CliRestException) {
            throw (CliRestException)e;
         }

         if(e instanceof BddException) {
            throw (BddException)e;
         }

         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   public TaskRead updateWithReturn(Object entity, final String path, final HttpMethod verb, PrettyOutput... prettyOutput) {
      checkConnection();
      try {
         if (verb == HttpMethod.PUT) {
            ResponseEntity<String> response = restUpdate(path, entity);
            if (!validateAuthorization(response)) {
               return null;
            }
            return processResponse(response, HttpMethod.PUT, prettyOutput);
         } else {
            throw new Exception(Constants.HTTP_VERB_ERROR);
         }
      } catch (Exception e) {
         throw new CliRestException(CommandsUtils.getExceptionMessage(e));
      }
   }

   private ResponseEntity<String> restUpdate(String path, Object entityName) {
      String targetUri = hostUri + Constants.HTTPS_CONNECTION_API + path;

      HttpHeaders headers = buildHeaders();
      HttpEntity<Object> entity = new HttpEntity<Object>(entityName, headers);

      return client.exchange(targetUri, HttpMethod.PUT, entity, String.class);
   }

   private <T> ResponseEntity<T> restQueryWithBody(String path, Class<T> entityType, Object body) {
      String targetUri = hostUri + Constants.HTTPS_CONNECTION_API + path;
      HttpHeaders headers = buildHeaders();
      HttpEntity<Object> entity = new HttpEntity<Object>(body, headers);
      return client.exchange(targetUri, HttpMethod.POST, entity, entityType);
   }

   @SuppressWarnings("rawtypes")
   private boolean validateAuthorization(ResponseEntity response) {
      if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
         System.out.println(Constants.CONNECT_UNAUTHORIZATION_OPT);
         return false;
      }
      return true;
   }

   /*
    * It will be trusted if users type 'yes' after CLI is aware of new SSL certificate. 
    */
   private static void trustSSLCertificate() {
      String errorMsg = "";
      try {
         KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
         TrustManagerFactory tmf =
               TrustManagerFactory.getInstance(TrustManagerFactory
                     .getDefaultAlgorithm());
         tmf.init(keyStore);
         SSLContext ctx = SSLContext.getInstance("SSL");
         ctx.init(new KeyManager[0],
               new TrustManager[] { new DefaultTrustManager(keyStore) },
               new SecureRandom());
         SSLContext.setDefault(ctx);
         HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String string, SSLSession ssls) {
               return true;
            }
         });
      } catch (KeyStoreException e) {
         errorMsg = "Key Store error: " + e.getMessage();
      } catch (KeyManagementException e) {
         errorMsg = "SSL Certificate error: " + e.getMessage();
      } catch (NoSuchAlgorithmException e) {
         errorMsg = "SSL Algorithm error: " + e.getMessage();
      } finally {
         if (!CommandsUtils.isBlank(errorMsg)) {
            System.out.println(errorMsg);
            logger.error(errorMsg);
         }
      }
   }

   private static class DefaultTrustManager implements X509TrustManager {

      private KeyStore keyStore;
      private static final char[] DEFAULT_PASSWORD = "changeit".toCharArray();
      private static final String KEY_STORE_FILE = "serengeti.keystore";
      private static final String KEY_STORE_PASSWORD_KEY = "keystore_pswd";
      private static final int KEY_STORE_PASSWORD_LENGTH = 8;

      public DefaultTrustManager (KeyStore keyStore) {
         this.keyStore = keyStore;
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
         String errorMsg = "";
         InputStream in = null;
         OutputStream out = null;

         // load key store file
         try {
            char[] pwd = readKeyStorePwd();
            File file = new File(KEY_STORE_FILE);

            if (!file.isFile()) {
               char SEP = File.separatorChar;
               file = new File(System.getProperty("java.home") + SEP + "lib"
                           + SEP + "security" + SEP + "cacerts");
               if (file.isFile()) {
                  // keystore password of cacerts file is DEFAULT_PASSWORD
                  keyStore.load(new FileInputStream(file), DEFAULT_PASSWORD);
               }
            } else {
               keyStore.load(new FileInputStream(file), pwd);
            }

            // show certificate informations
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String md5Fingerprint = "";
            String sha1Fingerprint = "";
            SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy/MM/dd");
            for (int i = 0; i < chain.length; i++) {
               X509Certificate cert = chain[i];
               sha1.update(cert.getEncoded());
               md5.update(cert.getEncoded());
               md5Fingerprint = toHexString(md5.digest());
               sha1Fingerprint = toHexString(sha1.digest());
               if (keyStore.getCertificate(md5Fingerprint) != null) {
                  if (i == chain.length - 1) {
                     return;
                  } else {
                     continue;
                  }
               }
               System.out.println();
               System.out.println("Server Certificate");
               System.out
                     .println("================================================================");
               System.out.println("Subject:  " + cert.getSubjectDN());
               System.out.println("Issuer:  " + cert.getIssuerDN());
               System.out.println("SHA Fingerprint:  " + sha1Fingerprint);
               System.out.println("MD5 Fingerprint:  " + md5Fingerprint);
               System.out.println("Issued on:  "
                     + dateFormate.format(cert.getNotBefore()));
               System.out.println("Expires on:  "
                     + dateFormate.format(cert.getNotAfter()));
               System.out.println("Signature:  " + cert.getSignature());
               System.out.println();
               if (checkExpired(cert.getNotBefore(), cert.getNotAfter())) {
                  throw new CertificateException(
                          "The security certificate has expired.");
               }
               ConsoleReader reader = new ConsoleReader();
               // Set prompt message
               reader.setPrompt(Constants.PARAM_PROMPT_ADD_CERTIFICATE_MESSAGE);
               // Read user input
               String readMsg = "";
               if (RunWayConfig.getRunType().equals(RunType.MANUAL)) {
                  readMsg = reader.readLine().trim();
               } else {
                  readMsg = "yes";
               }
               if ("yes".equalsIgnoreCase(readMsg) || "y".equalsIgnoreCase(readMsg)) {
                  {
                     // add new certificate into key store file.
                     keyStore.setCertificateEntry(md5Fingerprint, cert);
                     out = new FileOutputStream(KEY_STORE_FILE);
                     keyStore.store(out, pwd);
                     // save keystore password
                     saveKeyStorePwd(pwd);
                  }
               } else {
                  if (i == chain.length - 1) {
                     throw new CertificateException(
                           "Could not find a valid certificate in the keystore.");
                  } else {
                     continue;
                  }
               }
            }
         } catch (FileNotFoundException e) {
            errorMsg = "Cannot find the keystore file: " + e.getMessage();
         } catch (NoSuchAlgorithmException e) {
            errorMsg = "SSL Algorithm not supported: " + e.getMessage();
         } catch (IOException e) {
            e.printStackTrace();
            errorMsg = "IO error: " + e.getMessage();
         } catch (KeyStoreException e) {
            errorMsg = "Keystore error: " + e.getMessage();
         } catch (ConfigurationException e) {
            errorMsg = "cli.properties access error: " + e.getMessage();
         } finally {
            if (!CommandsUtils.isBlank(errorMsg)) {
               System.out.println(errorMsg);
               logger.error(errorMsg);
            }
            if (in != null) {
               try {
                  in.close();
               } catch (IOException e) {
                  logger.warn("Input stream of serengeti.keystore close failed.");
               }
            }
            if (out != null) {
               try {
                  out.close();
               } catch (IOException e) {
                  logger.warn("Output stream of serengeti.keystore close failed.");
               }
            }
         }
      }

      private boolean checkExpired(Date notBefore, Date notAfter) {
         Date now = new Date();
         if (now.before(notBefore) || now.after(notAfter)) {
            return true;
         }
         return false;
      }

      private PropertiesConfiguration loadCLIProperty() throws ConfigurationException {
         PropertiesConfiguration properties = new PropertiesConfiguration();
         File file = new File(Constants.PROPERTY_FILE);
         properties.setFile(file);
         if (file.isFile()) {
            properties.load();
         }
         return properties;
      }
      private char[] readKeyStorePwd() throws IOException, ConfigurationException {
         PropertiesConfiguration properties = loadCLIProperty();
         String password = properties.getString(KEY_STORE_PASSWORD_KEY);
         if (password == null) {
            // generate a random keystore password
            password = CommonUtil.randomString(KEY_STORE_PASSWORD_LENGTH);
         }
         return password.toCharArray();
      }

      private void saveKeyStorePwd(char[] password) throws IOException, ConfigurationException {
         PropertiesConfiguration properties = loadCLIProperty();
         properties.setProperty(KEY_STORE_PASSWORD_KEY, new String(password));
         properties.save();

         // set file permission to 600 to protect keystore password
         setOwnerOnlyReadWrite(Constants.PROPERTY_FILE);
         setOwnerOnlyReadWrite(Constants.CLI_HISTORY_FILE);
         setOwnerOnlyReadWrite(KEY_STORE_FILE);
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
         return null;
      }

      /*
       * Set file permission to 600
       */
      private void setOwnerOnlyReadWrite(String filename) throws IOException {
         Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
         perms.add(PosixFilePermission.OWNER_READ);
         perms.add(PosixFilePermission.OWNER_WRITE);
         Files.setPosixFilePermissions(Paths.get(filename), perms);
      }
   }

   private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

   /*
    * transfer a byte array to a hexadecimal string 
    */
   private static String toHexString(byte[] bytes) {
      StringBuilder sb = new StringBuilder(bytes.length * 3);
      for (int b : bytes) {
         b &= 0xff;
         sb.append(HEXDIGITS[b >> 4]);
         sb.append(HEXDIGITS[b & 15]);
         sb.append(':');
      }
      if (sb.length() > 0) {
         sb.delete(sb.length() - 1, sb.length());
      }
      return sb.toString().toUpperCase();
   }

   private class ResponseEntityResponseExtractor<T> implements
         ResponseExtractor<ResponseEntity<T>> {

      private final HttpMessageConverterExtractor<T> delegate;

      public ResponseEntityResponseExtractor(Class<T> responseType) {
         if (responseType != null && !Void.class.equals(responseType)) {
            this.delegate =
                  new HttpMessageConverterExtractor<T>(responseType,
                        client.getMessageConverters());
         } else {
            this.delegate = null;
         }
      }

      public ResponseEntity<T> extractData(ClientHttpResponse response)
            throws IOException {
         if (this.delegate != null) {
            T body = this.delegate.extractData(response);
            return new ResponseEntity<T>(body, response.getHeaders(),
                  response.getStatusCode());
         } else {
            return new ResponseEntity<T>(response.getHeaders(),
                  response.getStatusCode());
         }
      }
   }

}
