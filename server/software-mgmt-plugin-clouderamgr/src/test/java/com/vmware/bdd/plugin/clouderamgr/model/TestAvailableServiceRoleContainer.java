/***************************************************************************
 * Copyright (c) 2014 VMware, Inc. All Rights Reserved.
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
package com.vmware.bdd.plugin.clouderamgr.model;

import com.google.gson.Gson;
import com.vmware.bdd.plugin.clouderamgr.model.support.AvailableServiceRole;
import com.vmware.bdd.plugin.clouderamgr.model.support.AvailableServiceRoleContainer;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Author: Xiaoding Bian
 * Date: 6/12/14
 * Time: 7:48 PM
 */
public class TestAvailableServiceRoleContainer {
   @BeforeClass(groups = { "TestAvailableServiceRoleContainer" }, dependsOnGroups = {"TestCmClusterValidator"})
   public static void setup() throws IOException {
   }

   @Test(groups = {"TestAvailableServiceRoleContainer"})
   public void testLoad() throws IOException {
      AvailableServiceRoleContainer.loadAll();
      System.out.println(AvailableServiceRoleContainer.dump());
      System.out.println(AvailableServiceRoleContainer.load("HDFS").getAvailableConfigurations().keySet());
      System.out.println((new Gson()).toJson(AvailableServiceRoleContainer.load("HUE").getDependencies()));

      Assert.assertTrue(AvailableServiceRoleContainer.load("HBASE").compareTo(AvailableServiceRoleContainer.load("ZOOKEEPER")) == 1);
      Assert.assertTrue(AvailableServiceRoleContainer.load("ZOOKEEPER").compareTo(AvailableServiceRoleContainer.load("HBASE")) == -1);
      Assert.assertTrue(AvailableServiceRoleContainer.load("HDFS").compareTo(AvailableServiceRoleContainer.load("YARN_RESOURCE_MANAGER")) == 0);
      Assert.assertTrue(AvailableServiceRoleContainer.load("HBASE").compareTo(AvailableServiceRoleContainer.load("YARN")) == 0);
      Assert.assertTrue(AvailableServiceRoleContainer.load("HDFS").compareTo(AvailableServiceRoleContainer.load("HIVE")) == -1);
      Assert.assertTrue(AvailableServiceRoleContainer.load("IMPALA").compareTo(AvailableServiceRoleContainer.load("ZOOKEEPER")) == 1);

      List<AvailableServiceRole> services = new ArrayList<AvailableServiceRole>();
      services.add(AvailableServiceRoleContainer.load("HIVE"));
      services.add(AvailableServiceRoleContainer.load("HBASE"));
      services.add(AvailableServiceRoleContainer.load("HDFS"));
      services.add(AvailableServiceRoleContainer.load("ZOOKEEPER"));
      services.add(AvailableServiceRoleContainer.load("YARN"));
      services.add(AvailableServiceRoleContainer.load("IMPALA"));
      Collections.sort(services);
      for (AvailableServiceRole service : services) {
         System.out.println(service.getDisplayName());
      }
   }

   @Test(groups = {"TestAvailableServiceRoleContainer"})
   public void testAllServicesRoles() throws IOException {
      Set<String> allServicesV4 = AvailableServiceRoleContainer.allServices("4");
      Assert.assertTrue(allServicesV4.contains("HDFS"));
      Assert.assertFalse(allServicesV4.contains("YARN")); // CDH4 does not has YARN
      Assert.assertTrue(allServicesV4.contains("ZOOKEEPER"));
      Assert.assertTrue(allServicesV4.contains("HIVE"));
      Set<String> allServicesV5 = AvailableServiceRoleContainer.allServices("5");
      Assert.assertTrue(allServicesV5.contains("YARN")); // CDH5 has YARN
      Set<String> allRoles = AvailableServiceRoleContainer.allRoles("-1");
      Assert.assertTrue(allRoles.contains("HDFS_DATANODE"));
      Assert.assertTrue(allRoles.contains("YARN_NODE_MANAGER"));
   }

   @Test(groups = {"TestAvailableServiceRoleContainer"})
   public void testConfigs() throws IOException {
      String configs = AvailableServiceRoleContainer.getSupportedConfigs("5");
      Map<String, Object> configMap = (new Gson()).fromJson(configs, Map.class);
      Assert.assertTrue(configMap.containsKey("HDFS"));
      Assert.assertTrue(configMap.containsKey("HDFS_DATANODE"));
      List<String> hdfsConfig = (List<String>) configMap.get("HDFS");
      Assert.assertTrue(hdfsConfig.contains("hdfs_missing_blocks_thresholds"));
      System.out.println(configs);
   }

   @Test(groups = {"TestAvailableServiceRoleContainer"})
   public void testServiceIsilon() throws IOException {
      Set<String> allServicesV51 = AvailableServiceRoleContainer.allServices("5.1.0");
      Assert.assertTrue(allServicesV51.contains("ISILON"));

      Set<String> allRolesV51 = AvailableServiceRoleContainer.allRoles("5.1.0");
      Assert.assertTrue(allRolesV51.contains("GATEWAY"));

      Set<String> allServicesV511 = AvailableServiceRoleContainer.allServices("5.1.1");
      Assert.assertTrue(allServicesV511.contains("ISILON"));

      Set<String> allRolesV511 = AvailableServiceRoleContainer.allRoles("5.1.1");
      Assert.assertTrue(allRolesV511.contains("GATEWAY"));

      Set<String> allServicesV52 = AvailableServiceRoleContainer.allServices("5.2");
      Assert.assertTrue(allServicesV52.contains("ISILON"));

      Set<String> allRolesV52 = AvailableServiceRoleContainer.allRoles("5.2");
      Assert.assertTrue(allRolesV52.contains("GATEWAY"));

      Set<String> allServicesV6 = AvailableServiceRoleContainer.allServices("6.0.0");
      Assert.assertTrue(allServicesV6.contains("ISILON"));

      Set<String> allRolesV6 = AvailableServiceRoleContainer.allRoles("6.0.0");
      Assert.assertTrue(allRolesV6.contains("GATEWAY"));

      Set<String> allServicesV1 = AvailableServiceRoleContainer.allServices("-1");
      Assert.assertTrue(allServicesV1.contains("ISILON"));

      Set<String> allRolesV1 = AvailableServiceRoleContainer.allRoles("-1");
      Assert.assertTrue(allRolesV1.contains("GATEWAY"));

      Set<String> allServicesV509 = AvailableServiceRoleContainer.allServices("5.0.9");
      Assert.assertFalse(allServicesV509.contains("ISILON"));

      Set<String> allRolesV510 = AvailableServiceRoleContainer.allRoles("5.0.9");
      Assert.assertFalse(allRolesV510.contains("GATEWAY"));

      Set<String> allServicesV49 = AvailableServiceRoleContainer.allServices("4.9");
      Assert.assertFalse(allServicesV49.contains("ISILON"));

      Set<String> allRolesV49 = AvailableServiceRoleContainer.allRoles("4.9");
      Assert.assertFalse(allRolesV49.contains("GATEWAY"));

      Set<String> allServicesV4 = AvailableServiceRoleContainer.allServices("4");
      Assert.assertFalse(allServicesV4.contains("ISILON"));

      Set<String> allRolesV4 = AvailableServiceRoleContainer.allRoles("4");
      Assert.assertFalse(allRolesV4.contains("GATEWAY"));
   }
}
