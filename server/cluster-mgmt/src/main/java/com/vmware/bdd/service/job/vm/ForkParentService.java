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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.vmware.aurora.composition.CreateVMFolderSP;
import com.vmware.aurora.composition.concurrent.ExecutionResult;
import com.vmware.aurora.composition.concurrent.Priority;
import com.vmware.aurora.composition.concurrent.Scheduler;
import com.vmware.aurora.vc.VcCluster;
import com.vmware.aurora.vc.VcDatacenter;
import com.vmware.aurora.vc.VcHost;
import com.vmware.aurora.vc.VcResourcePool;
import com.vmware.aurora.vc.VcVirtualMachine;
import com.vmware.aurora.vc.VcVmCloneType;
import com.vmware.aurora.vc.vcservice.VcContext;
import com.vmware.aurora.vc.vcservice.VcSession;
import com.vmware.bdd.clone.spec.VmCreateResult;
import com.vmware.bdd.clone.spec.VmCreateSpec;
import com.vmware.bdd.exception.BddException;
import com.vmware.bdd.service.sp.CreateResourcePoolSP;
import com.vmware.bdd.service.sp.NoProgressUpdateCallback;
import com.vmware.bdd.service.utils.VcResourceUtils;
import com.vmware.bdd.utils.AuAssert;
import com.vmware.bdd.utils.CommonUtil;
import com.vmware.bdd.utils.VcVmUtil;
import com.vmware.bdd.vmclone.service.intf.IClusterCloneService;
import com.vmware.vim.binding.vim.Folder;

/**
 * Created By xiaoliangl on 1/23/15.
 */
@Component
public class ForkParentService {
   private final static Logger LOGGER = Logger.getLogger(ForkParentService.class);

   private static String PARENT_COLLECTION_COMMON_STR = "BigDataExtension-InstantCloneParentVMs";

   private static List<String> PARENTS_FOLDER_PATH;
   private String parentResPoolName = null;

   @Autowired
   private Map<String, IClusterCloneService> cloneServiceMap;

   public VcResourcePool getParentsResPool(VcResourcePool vAppResPool) {
      List<VcResourcePool> childResPool = vAppResPool.getChildren();

      if (childResPool != null) {
         for (VcResourcePool resourcePool : childResPool) {
            if (StringUtils.equals(getParentResPoolName(), resourcePool.getName())) {
               return resourcePool;
            }
         }
      }

      return null;
   }

   private String getParentResPoolName() {
      if ( null != parentResPoolName ) {
         return parentResPoolName;
      }

      String prpName = PARENT_COLLECTION_COMMON_STR;
      String vappName = ForkParentInfo.getVappName();
      if ( !CommonUtil.isBlank(vappName) ) {
         prpName = vappName + "-" + prpName;
      }
      String forkParentTag = ForkParentInfo.getTag();
      if ( !CommonUtil.isBlank(forkParentTag) ) {
         prpName += "-" + forkParentTag.trim();
      }
      ArrayList<String> paths = new ArrayList<>();
      paths.add(prpName);
      PARENTS_FOLDER_PATH = ListUtils.unmodifiableList(paths);

      return prpName;
   }

   private VcResourcePool checkAndCreateParentResPool(VcCluster vcCluster, HashMap<String, VcResourcePool> vcResPoolByCluster) {
      VcResourcePool parentResPool = findParentsResPoolByCluster(vcCluster, vcResPoolByCluster);
      if (parentResPool == null) {
         executeStoreProcedure("CREATE_PARENT_VM_RP", new CreateResourcePoolSP(vcCluster.getRootRP(), getParentResPoolName()));
         parentResPool = findParentsResPoolByCluster(vcCluster, vcResPoolByCluster);
      }

      return parentResPool;
   }

   private VcResourcePool findParentsResPoolByCluster(VcCluster vcCluster, HashMap<String, VcResourcePool> vcResPoolByCluster) {
      VcResourcePool parentsResPool = null;
      if (!vcResPoolByCluster.containsKey(vcCluster.getName())) {
         parentsResPool = getParentsResPool(vcCluster.getRootRP());

         if (parentsResPool != null) {
            vcResPoolByCluster.put(vcCluster.getName(), parentsResPool);
         } else {
            LOGGER.info(String.format("fork parents resource pool for cluster: %1s not found.", vcCluster.getName()));
         }
      } else {
         parentsResPool = vcResPoolByCluster.get(vcCluster.getName());
      }

      return parentsResPool;
   }


   private Folder findParentsFolder(String dataCenterName, Folder datacenter, HashMap<String, Folder> vcFolderByDataCenter) {
      Folder parentsFolder = null;
      if (vcFolderByDataCenter.containsKey(dataCenterName)) {
         parentsFolder = vcFolderByDataCenter.get(dataCenterName);
      } else {
         parentsFolder = VcResourceUtils.findFolderByName(datacenter, getParentResPoolName());

         if (parentsFolder != null) {
            vcFolderByDataCenter.put(dataCenterName, parentsFolder);
         }
      }

      return parentsFolder;
   }

   public List<VmCreateResult<?>> cloneParents(VmCreateSpec templateVm, Map<String, ForkParentInfo> plan, int maxConcurrentCopy, Scheduler.ProgressCallback callback) {
      HashMap<String, VcResourcePool> vcResPoolByCluster = new HashMap<>();
      HashMap<String, Folder> vcFolderByDataCenter = new HashMap<>();

      List<VmCreateSpec> parentVmCreates = new ArrayList<>();
      for (ForkParentInfo forkParentInfo : plan.values()) {
         VmCreateSpec forkParentCreate = new VmCreateSpec();
         forkParentCreate.setCloneType(VcVmCloneType.FULL);
         forkParentCreate.setPersisted(true);

         String vcClusterName = forkParentInfo.getClusterName();
         VcCluster vcCluster = VcResourceUtils.findVcCluster(vcClusterName);

         VcHost vcHost = VcResourceUtils.findHost(forkParentInfo.getHostName());
         forkParentCreate.setTargetHost(vcHost);

         forkParentCreate.setTargetDs(VcResourceUtils.findDSInVcByName(forkParentInfo.getDataStoreName()));
         forkParentCreate.setVmName(
               ForkParentInfo.getForkRootName(forkParentInfo.getClusterName(), forkParentInfo.getHostName()));
         forkParentCreate.setSchema(forkParentInfo.getVmSchema());

         VcResourcePool parentResPool = checkAndCreateParentResPool(vcCluster, vcResPoolByCluster);
         forkParentCreate.setTargetRp(parentResPool);


         Folder parentsFolder = checkAndCreateParentFolder(vcCluster, vcFolderByDataCenter);
         forkParentCreate.setTargetFolder(parentsFolder);

         forkParentCreate.setPrePowerOn(new BeforeForkParentPowerOn());
         forkParentCreate.setPostPowerOn(new AfterForkParentPowerOn());

         parentVmCreates.add(forkParentCreate);
      }

      return cloneServiceMap.get("fastClusterCloneService").createCopies(templateVm, maxConcurrentCopy, parentVmCreates, callback);
   }

   public VcVirtualMachine findRootParentVm(VcResourcePool parentsResPool, String forkRootVmName) {
      return VcVmUtil.findVmInRp(parentsResPool, forkRootVmName);
   }

   public VcVirtualMachine findRootParentVm(String hostName) {
      final VcHost vchost = VcResourceUtils.findHost(hostName);

      AuAssert.check(vchost != null, String.format("can' find host: %1s in VC.", hostName));


      final VcCluster vcCluster = VcContext.inVcSessionDo(new VcSession<VcCluster>() {
         @Override
         protected VcCluster body() throws Exception {
            return vchost.getCluster();
         }
      });

      VcResourcePool vcClusterRootResPool = VcContext.inVcSessionDo(new VcSession<VcResourcePool>() {
         @Override
         protected VcResourcePool body() throws Exception {
            return vcCluster.getRootRP();
         }
      });

      VcResourcePool vcParentResPool = getParentsResPool(vcClusterRootResPool);

      if (vcParentResPool != null) {
         return findRootParentVm(vcParentResPool, ForkParentInfo.getForkRootName(vcCluster.getName(), vchost.getName()));
      }

      return null;
   }

   public Folder checkAndCreateParentFolder (VcCluster vcCluster, HashMap<String, Folder> vcFolderByDataCenter) {
      VcDatacenter vcDatacenter = getDataCenter(vcCluster);
      String dataCenterName = vcDatacenter.getName();
      Folder vcDataCenterFolder = getDataCenterFolder(vcDatacenter);

      Folder parentsFolder = findParentsFolder(dataCenterName, vcDataCenterFolder, vcFolderByDataCenter);
      if (parentsFolder == null) {
         executeStoreProcedure("CREATE_PARENT_VM_FOLDER",
               new CreateVMFolderSP(vcDatacenter, vcDataCenterFolder, PARENTS_FOLDER_PATH));
         parentsFolder = findParentsFolder(dataCenterName, vcDataCenterFolder, vcFolderByDataCenter);
      }

      return parentsFolder;
   }

   public static VcDatacenter getDataCenter(final VcCluster vcCluster) {
      return VcContext.inVcSessionDo(new VcSession<VcDatacenter>() {
         @Override
         protected VcDatacenter body() throws Exception {
            return vcCluster.getDatacenter();
         }
      });
   }

   public static Folder getDataCenterFolder(final VcDatacenter vcDatacenter) {
      return VcContext.inVcSessionDo(new VcSession<Folder>() {
         @Override
         protected Folder body() throws Exception {
            return vcDatacenter.getVmFolder();
         }
      });
   }

   public static <T extends Callable<Void>> void executeStoreProcedure(String actionId, T... tasks) {
      ExecutionResult[] result = null;
      try {
         result = Scheduler.executeStoredProcedures(Priority.BACKGROUND, tasks, new NoProgressUpdateCallback());

         if (ArrayUtils.isEmpty(result)) {
            String errMsg = "no sp is executed successfully.";
            LOGGER.error(errMsg);
            throw BddException.INTERNAL(null, errMsg);
         }

         if (result[0].throwable != null) {
            throw BddException.INTERNAL(result[0].throwable, "Fork VM failed.");
         }
      } catch (InterruptedException ie) {
         throw BddException.INTERNAL(ie, null);
      }
   }

}
