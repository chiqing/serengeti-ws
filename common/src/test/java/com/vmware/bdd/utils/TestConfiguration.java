/***************************************************************************
 * Copyright (c) 2012-2013 VMware, Inc. All Rights Reserved.
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

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.vmware.aurora.global.Configuration;

public class TestConfiguration {

   @Test
   public void testGetStrings () {
      String proxy = "";
      proxy = Configuration.getStrings("serengeti.no_proxy", "127.0.0.1");
      assertEquals(proxy,"192.168.0.1,192.168.0.2");
//      proxy = Configuration.getStrings("serengeti.no_proxy_no_comma", "127.0.0.1");
//      assertEquals(proxy,"192.168.0.1 192.168.0.2");
      Configuration.setString("serengeti.http_proxy", "proxy.domain.com:3128");
      proxy = Configuration.getStrings("serengeti.http_proxy", "127.0.0.1");
      assertEquals(proxy,"proxy.domain.com:3128");
//      proxy = Configuration.getStrings("serengeti.svn_proxy", "127.0.0.1");
//      assertEquals(proxy,"127.0.0.1");
   }

   @Test
   public void testSetStrings () {
      int vcPort = Configuration.getInt("vim.port");
      assertEquals(vcPort, 443);

      String mobid = Configuration.getString("vim.cms_moref");
      assertEquals(mobid, "VirtualMachine:001");
      Configuration.setString("vim.cms_moref", "VirtualMachine:002");
      mobid = Configuration.getString("vim.cms_moref");

      assertEquals(mobid, "VirtualMachine:002");
      Configuration.setString("serengeti.http_proxy", "127.0.0.2");
      String proxy = Configuration.getString("serengeti.http_proxy", "127.0.0.1");
      assertEquals(proxy, "127.0.0.2");
      //manually check if vc.properties will not be saved and included into serengeti.properties
      Configuration.save();
   }

   @AfterClass
   public static void tearDown() {
      Configuration.setString("serengeti.http_proxy", "proxy.domain.com:3128");
      ConfigInfo.save();
   }
}