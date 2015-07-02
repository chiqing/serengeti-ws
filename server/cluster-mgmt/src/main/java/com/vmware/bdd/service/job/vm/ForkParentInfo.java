/******************************************************************************
 *   Copyright (c) 2014 VMware, Inc. All Rights Reserved.
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
package com.vmware.bdd.service.job.vm;

import java.util.ArrayList;

import org.apache.commons.collections.ListUtils;
import org.apache.log4j.Logger;

import com.vmware.aurora.composition.VmSchema;
import com.vmware.aurora.global.Configuration;
import com.vmware.bdd.utils.CommonUtil;

/**
 * Created By xiaoliangl on 1/30/15.
 */
public final class ForkParentInfo {
   private final static Logger LOGGER = Logger.getLogger(ForkParentService.class);
   public enum STATE {
      CREATED, TO_BE_CREATED, NO_ENOUGH_RESOURCE
   }

   private final String hostName;
   private final String clusterName;
   private final String dataStoreName;
   private final STATE state;
   private final VmSchema vmSchema;

   // we use bde name(the uuid) as part of the parent vm name so that multiple bde
   // can use its own parent vm on the same set of hosts
   private static String vappName = "";
   // the tag is used to differentiate parent vms when node-template is updated
   // the related property in serengeti.properties is: cluster.clone.instant.parent.tag
   private static String tag = "";

   public ForkParentInfo(String hostName, String clusterName, String dataStoreName, STATE state,
                         VmSchema vmSchema) {
      this.hostName = hostName;
      this.clusterName = clusterName;
      this.dataStoreName = dataStoreName;
      this.state = state;
      this.vmSchema = vmSchema;
   }

   public static String getForkRootName(String clusterName, String hostName) {
      String forkRootName = String.format("%s-%s-%s", getVappName(), clusterName, hostName);
      String parentTag = getTag();
      if ( !CommonUtil.isBlank(parentTag) ) {
         forkRootName += "-" + parentTag;
      }
      return forkRootName;
   }

   public String getHostName() {
      return hostName;
   }

   public String getClusterName() {
      return clusterName;
   }

   public String getDataStoreName() {
      return dataStoreName;
   }

   public STATE getState() {
      return state;
   }

   public VmSchema getVmSchema() {
      return vmSchema;
   }

   public static String getVappName() {
      if ( CommonUtil.isBlank(vappName) ) {
         String sgtid = Configuration.getString("serengeti.uuid");
         if ( !CommonUtil.isBlank(sgtid) ) {
            LOGGER.info("The vapp uuid is: " + sgtid);
            vappName = sgtid.replaceAll("[' ']+", "_");
         }
      }
      return vappName;
   }

   public static String getTag() {
      if ( CommonUtil.isBlank(tag) ) {
         String forkParentTag = Configuration.getString("cluster.clone.instant.parent.tag");
         if ( !CommonUtil.isBlank(forkParentTag) ) {
            LOGGER.info("The tag for the parent vm is: " + forkParentTag);
            tag = forkParentTag;
         }
      }
      return tag;
   }
}
