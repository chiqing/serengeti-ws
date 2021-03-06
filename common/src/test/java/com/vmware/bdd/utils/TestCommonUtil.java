/***************************************************************************
 * Copyright (c) 2012-2014 VMware, Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/
package com.vmware.bdd.utils;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCommonUtil {

   @Test
   public void testValidateVcResourceName() {
      assertFalse(CommonUtil.validateVcResourceName(""));
      assertTrue(CommonUtil
            .validateVcResourceName("the你好學習こんにちは안녕하세요schönenuntrèsbon_t- aux.3210"));
      assertFalse(CommonUtil
            .validateVcResourceName("the你好學習こんにちは안녕하세요schönenuntrèsbon_t- aux$"));
      assertFalse(CommonUtil.validateVcResourceName("%&*$#@!\\\\/:*?\"<>|;'"));
      assertFalse(CommonUtil.validateVcResourceName("()+,=[]^`{}~"));
      assertTrue(CommonUtil.validateVcResourceName("resource name"));
      assertTrue(CommonUtil.validateVcResourceName("192.168.0.1"));
      assertTrue(CommonUtil.validateVcResourceName("VM network192.168.0.1"));
      assertTrue(CommonUtil.validateVcResourceName("-------- ------"));
   }

   @Test
   public void testValidateResourceName() {
      assertEquals(CommonUtil.validateDistroName("name1"), true);
      assertEquals(CommonUtil.validateDistroName("Name2"), true);
      assertEquals(CommonUtil.validateDistroName("name3_"), true);
      assertEquals(CommonUtil.validateDistroName("name4 "), true);
      assertEquals(CommonUtil.validateDistroName("name-5"), true);
      assertEquals(CommonUtil.validateDistroName("name6-"), true);
      assertEquals(CommonUtil.validateDistroName("-name7-"), true);
      assertFalse(CommonUtil.validateDistroName("%&*$#@!\\\\/:*?\"<>|;'"));
      assertFalse(CommonUtil.validateDistroName("()+,=[]^`{}~"));
   }

   @Test
   public void testValidateDistroName() {
      assertEquals(CommonUtil.validateDistroName("name1"), true);
      assertEquals(CommonUtil.validateDistroName("Name2"), true);
      assertEquals(CommonUtil.validateDistroName("name3_"), true);
      assertEquals(CommonUtil.validateDistroName("name4 "), true);
      assertEquals(CommonUtil.validateDistroName("name-5"), true);
      assertEquals(CommonUtil.validateDistroName("name6-"), true);
      assertEquals(CommonUtil.validateDistroName("-name7-"), true);
      assertFalse(CommonUtil.validateDistroName("%&*$#@!\\\\/:*?\"<>|;'"));
      assertFalse(CommonUtil.validateDistroName("()+,=[]^`{}~"));
   }

   @Test
   public void testValidateClusterName() {
      assertEquals(CommonUtil.validateClusterName("clusterName1"), true);
      assertEquals(CommonUtil.validateClusterName("clusterName2"), true);
      assertEquals(CommonUtil.validateClusterName("clusterName3_"), true);
      assertEquals(CommonUtil.validateClusterName("clusterName4 "), false);
      assertEquals(CommonUtil.validateClusterName("clusterName-5"), false);
      assertEquals(CommonUtil.validateClusterName("clusterName6-"), false);
      assertEquals(CommonUtil.validateClusterName("-clusterName7"), false);
      assertEquals(CommonUtil.validateClusterName("cluster-Name8"), false);
      assertEquals(CommonUtil.validateClusterName("cluster Name9"), false);
   }

   @Test
   public void testValidateNodeGroupName() {
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroupName1"), true);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroupName2"), true);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroupName3_"), false);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroupName4 "), false);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroupName-5"), false);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroupName6-"), false);
      assertEquals(CommonUtil.validateNodeGroupName("-nodeGroupName7"), false);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroup Name8"), false);
      assertEquals(CommonUtil.validateNodeGroupName("nodeGroup_Name9"), false);
      assertEquals(CommonUtil.validateNodeGroupName("_nodeGroupName10"), false);
      assertEquals(
            CommonUtil
                  .validateNodeGroupName("nodeGroupName1234567890123456789012345678901234567890123456789012345678901234567890"),
            false);
   }

   @Test
   public void testValidatePathInfo() {
      assertEquals(CommonUtil.validataPathInfo("/clusters"), true); // delete|stop|start cluster
      assertEquals(CommonUtil.validataPathInfo("/cluster/test01/fix/disk"), true); // fixCluster
      assertEquals(CommonUtil.validataPathInfo("/cluster/test_01/fix/disk"), true);
      assertEquals(CommonUtil.validataPathInfo("/cluster//test_01/fix///disk//"), true);
      assertEquals(CommonUtil.validataPathInfo("/cluster/test01/param_wait_for_result"), true); // asyncSetParam
      assertEquals(CommonUtil.validataPathInfo("/cluster/test01/nodegroup/data/scale"), true); // scaleCluster
      assertEquals(CommonUtil.validataPathInfo("/cluster/test01/config"), true); // configCluster
      assertEquals(CommonUtil.validataPathInfo("/cluster/test01/nodegroup/data/instancenum"), true); // resizeCluster

      assertEquals(CommonUtil.validataPathInfo("/clusters/\r\nHTTP/1.1 200 OK\r\n location: index.html"), false);
   }

   @Test
   public void testValidateVcDataStoreNames() {
      List<String> vcDataStoreNames = new ArrayList<String>();
      vcDataStoreNames.add("vcDataStore_Name1");
      vcDataStoreNames.add("vcDataStore_Nam*");
      vcDataStoreNames.add("vcDataStore_Nam?");
      vcDataStoreNames.add("vcData Store_Name2");
      vcDataStoreNames.add("vcDataStoreName3-");
      vcDataStoreNames.add("vcDataStoreName192.168.0.1");
      vcDataStoreNames.add("vcDataStoreName(192.168.0.1)");
      vcDataStoreNames.add("vc本地存储(192.168.0.1)");
      assertEquals(CommonUtil.validateVcDataStoreNames(vcDataStoreNames), true);

      List<String> errorVcDataStoreNames1 = new ArrayList<String>();
      errorVcDataStoreNames1.add("vcDataStoreName!");
      assertEquals(CommonUtil.validateVcDataStoreNames(errorVcDataStoreNames1), false);
      List<String> errorVcDataStoreNames2 = new ArrayList<String>();
      errorVcDataStoreNames2.add("vcDataStoreName#");
      assertEquals(CommonUtil.validateVcDataStoreNames(errorVcDataStoreNames2), false);
   }

   @Test
   public void testGetDatastoreJavaPattern() {
      String datastore = "(192.168.0.1)datastore";
      String pattern = CommonUtil.getDatastoreJavaPattern("(192.168.0.1)datasto?e");
      assertTrue(datastore.matches(pattern));
      pattern = CommonUtil.getDatastoreJavaPattern("(192.168.0.1)data*");
      assertTrue(datastore.matches(pattern));
   }
/*
   @Test
   public void testIsComputeOnly() {
      List<String> roles = new ArrayList<String>();
      roles.add(HadoopRole.HADOOP_TASKTRACKER.toString());
      String distroVendor = "apache";
      assertTrue(CommonUtil.isComputeOnly(roles, distroVendor));
      roles.add(HadoopRole.TEMPFS_CLIENT_ROLE.toString());
      assertTrue(CommonUtil.isComputeOnly(roles, distroVendor));
      roles.add(HadoopRole.HADOOP_DATANODE.toString());
      assertTrue(!CommonUtil.isComputeOnly(roles, distroVendor));

      roles.clear();
      roles.add(HadoopRole.MAPR_TASKTRACKER_ROLE.toString());
      distroVendor = Constants.MAPR_VENDOR;
      assertTrue(CommonUtil.isComputeOnly(roles, distroVendor));
      roles.add(HadoopRole.MAPR_NFS_ROLE.toString());
      assertTrue(!CommonUtil.isComputeOnly(roles, distroVendor));
   }*/

   @Test
   public void testMakeVmMemoryDivisibleBy4() {
      long max = Long.MAX_VALUE;
      max = CommonUtil.makeVmMemoryDivisibleBy4(max);
      assertTrue(max > 0);
   }

   @Test
   public void testDecode() {
      assertEquals(CommonUtil.decode("datastore1"), "datastore1");
      assertEquals(CommonUtil.decode(CommonUtil.encode("데이터저장소1")), "데이터저장소1");
      assertEquals(CommonUtil.decode(CommonUtil.encode("数据存储1")), "数据存储1");
   }

   @Test
   public void testEncode() {
      assertEquals(CommonUtil.encode("datastore1"), "datastore1");
      assertEquals(CommonUtil.encode("데이터저장소1"),
            "%EB%8D%B0%EC%9D%B4%ED%84%B0%EC%A0%80%EC%9E%A5%EC%86%8C1");
      assertEquals(CommonUtil.encode("数据存储1"),
            "%E6%95%B0%E6%8D%AE%E5%AD%98%E5%82%A81");
   }

   @Test
   public void testGetTimestamp() throws ParseException {
      String dateStr = CommonUtil.getCurrentTimestamp();
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
      Date date = simpleDateFormat.parse(dateStr.substring(1, dateStr.length()-1));
      assertNotNull(date);
   }

   @Test
   public void testValidateClusterPassword() {
      //length less than 8
      assertFalse(CommonUtil.validateClusterPassword("P@ssw0r"));

      //length more than 20
      assertFalse(CommonUtil.validateClusterPassword("P@ssw0rdP@ssw0rdP@ssw0rd"));

      //no special character
      assertFalse(CommonUtil.validateClusterPassword("Passw0rd"));

      //no digit
      assertFalse(CommonUtil.validateClusterPassword("P@ssword"));

      //no upper case letter
      assertFalse(CommonUtil.validateClusterPassword("p@ssw0rd"));

      //no lower case letter
      assertFalse(CommonUtil.validateClusterPassword("P@SSW0RD"));

      //valid one
      assertTrue(CommonUtil.validateClusterPassword("P@ssw0rd"));
   }

   @Test
   public void testReadJsonFileSucc() throws ParseException {
      String fileName = "/whitelist.json";
      try {
         URL url = this.getClass().getResource(fileName);
         FileInputStream fis = new FileInputStream(url.getPath());
         BufferedReader br = new BufferedReader(new InputStreamReader(fis));
         StringBuilder sb = new StringBuilder();
         String line = "";
         while ( (line = br.readLine()) != null ) {
            sb.append(line);
         }
         br.close();
         String contentRead = CommonUtil.readJsonFile(url);
         String testString = sb.toString();
         contentRead = contentRead.replaceAll("\n", "");
         Assert.assertEquals(contentRead, testString);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   @Test
   public void testReadJsonFileNotExisted() throws ParseException {
      String contentRead = CommonUtil.readJsonFile("Not_Existed");
      Assert.assertEquals(contentRead, "");
   }

   @Test
   public void testInputsConvert() throws ParseException {
      String[] sa = { "ab", "123", "*&^" };
      List<String> ls = CommonUtil.inputsConvert("ab,123,*&^");
      for ( int i=0; i<sa.length; i++ ) {
         Assert.assertEquals(ls.get(i), sa[i]);
      }

      // add a case for a string without ","
      ls = CommonUtil.inputsConvert("abc123-xyz");
      Assert.assertEquals(ls.get(0), "abc123-xyz");

      Set<String> words = new TreeSet<String>();
      words.addAll(Arrays.asList("ab", "123", "*&^"));
      Assert.assertEquals("*&^,123,ab", CommonUtil.inputsConvert(words));

      Map<String, String> map = new TreeMap<String, String>();
      map.put("name", "name1");
      map.put("value", "value1");
      Assert.assertEquals("name:name1,value:value1", CommonUtil.inputsConvert(map));
   }

   @Test
   public void testExecCommand() throws ParseException {
      String tmpDir = System.getProperty("java.io.tmpdir");
      if ( tmpDir.endsWith(File.separator) ) {
         tmpDir = tmpDir.substring(0, tmpDir.length()-1);
      }
      String filePathName = tmpDir + File.separator + "abc";
      File f = new File(filePathName);
      Assert.assertFalse(f.exists());

      // call the api method
      String cmd = "touch " + filePathName;
      Process p = CommonUtil.execCommand(cmd);

      Assert.assertNotNull(p);
      Assert.assertTrue(f.exists());

      // delete the file after test
      f.delete();
   }

   @Test
   public void testValidateUrl() throws ParseException {
      List<String> errorMsgs = new ArrayList<String>();
      boolean valid = CommonUtil.validateUrl("http://10.141.73.8:8080", errorMsgs);
      Assert.assertTrue(valid);

      errorMsgs.clear();
      valid = CommonUtil.validateUrl("ftp://10.141.73.8:8080", errorMsgs);
      Assert.assertFalse(valid);
      Assert.assertEquals(errorMsgs.get(0), "URL should starts with http or https");
   }
}
