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
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import com.vmware.aurora.composition.DiskSchema;
import com.vmware.aurora.composition.DiskSchemaUtil;
import com.vmware.aurora.composition.IPrePostPowerOn;
import com.vmware.aurora.composition.NetworkSchema;
import com.vmware.aurora.composition.NetworkSchema.Network;
import com.vmware.aurora.composition.NetworkSchemaUtil;
import com.vmware.aurora.composition.ResourceSchemaUtil;
import com.vmware.aurora.global.DiskSize;
import com.vmware.aurora.vc.DeviceId;
import com.vmware.aurora.vc.VcHost;
import com.vmware.aurora.vc.VcVirtualMachine;
import com.vmware.aurora.vc.VcVmCloneType;
import com.vmware.aurora.vc.VmConfigUtil;
import com.vmware.aurora.vc.vcservice.VcContext;
import com.vmware.aurora.vc.vcservice.VcSession;
import com.vmware.bdd.clone.spec.VmCreateSpec;
import com.vmware.vim.binding.impl.vim.vApp.ProductSpecImpl;
import com.vmware.vim.binding.impl.vim.vApp.VmConfigSpecImpl;
import com.vmware.vim.binding.impl.vim.vm.ConfigSpecImpl;
import com.vmware.vim.binding.impl.vim.vm.device.VirtualDiskImpl;
import com.vmware.vim.binding.vim.option.ArrayUpdateSpec;
import com.vmware.vim.binding.vim.vApp.ProductInfo;
import com.vmware.vim.binding.vim.vApp.ProductSpec;
import com.vmware.vim.binding.vim.vApp.VmConfigInfo;
import com.vmware.vim.binding.vim.vApp.VmConfigSpec;
import com.vmware.vim.binding.vim.vm.ConfigSpec;
import com.vmware.vim.binding.vim.vm.device.VirtualDeviceSpec;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;

/**
 * Created By xiaoliangl on 1/26/15.
 */
public class ForkChildVmSp implements Callable<Void> {
   private final static Logger logger = Logger.getLogger(ForkChildVmSp.class);
   private VcVirtualMachine vcParentVmOnHost;
   private VcVirtualMachine vcChildVm;
   private VmCreateSpec vmForkSpec;
   private Semaphore maxForkConcurrency;


   public ForkChildVmSp(VcVirtualMachine vcParentVm, VmCreateSpec vmFork, Semaphore maxForkConcurrent) {
      vcParentVmOnHost = vcParentVm;
      vmForkSpec = vmFork;
      maxForkConcurrency = maxForkConcurrent;
   }

   @Override
   public Void call() throws Exception {
      ConfigSpecImpl configSpec = new ConfigSpecImpl();
      configSpec.setName(vmForkSpec.getVmName());
      copyParentVmSettings(configSpec);

      // Resource schema
      ResourceSchemaUtil.setResourceSchema(configSpec, vmForkSpec.getSchema().resourceSchema);

      vmForkSpec.setCloneType(VcVmCloneType.VMFORK);
      vmForkSpec.setPersisted(true);

      final VcVirtualMachine.CreateSpec vmSpec = vmForkSpec.toCreateSpec(
            vmForkSpec.isPersisted() ? vcParentVmOnHost.getSnapshotByName(AfterForkParentPowerOn.FORK_PARENT_SNAPSHOT) : null,
            configSpec);

      VcContext.inVcSessionDo(new VcSession<Void>() {
         @Override
         protected boolean isTaskSession() {
            return true;
         }

         @Override
         protected Void body() throws Exception {
            try {
               maxForkConcurrency.acquire();
               vcChildVm = vcParentVmOnHost.cloneVm(vmSpec, null);
            } finally {
               maxForkConcurrency.release();
            }
            return null;
         }
      });

      if (vcChildVm != null) {
         VcContext.inVcSessionDo(new VcSession<Void>() {
            @Override
            protected boolean isTaskSession() {
               return true;
            }

            @Override
            protected Void body() throws Exception {
               reconfigureVm();
               powerOn();
               return null;
            }
         });

         vmForkSpec.setVmId(vcChildVm.getId());
      }
      return null;
   }

   private void powerOn() throws Exception {
      IPrePostPowerOn callback = vmForkSpec.getPrePowerOn();
      if (callback != null) {
         callback.setVm(vcChildVm);
         callback.call();
      }

      vcChildVm.powerOn(vmForkSpec.getTargetHost());

      callback = vmForkSpec.getPostPowerOn();
      if (callback != null) {
         callback.setVm(vcChildVm);
         callback.call();
      }

   }

   private void reconfigureVm()
         throws Exception {
      logger.info("start reconfiguring vm " + vmForkSpec.getVmName()
            + " after cloning");

      // Get list of disks to add
      List<VcHost> hostList = new ArrayList<VcHost>();

      HashMap<String, DiskSchema.Disk.Operation> diskMap =
            new HashMap<String, DiskSchema.Disk.Operation>();

      List<VcVirtualMachine.DiskCreateSpec> addDiskList =
            DiskSchemaUtil.getDisksToAdd(hostList, vmForkSpec.getTargetRp(),
                  vmForkSpec.getTargetDs(), vmForkSpec.getSchema().diskSchema, diskMap);

      VcVirtualMachine.DiskCreateSpec[] addDisks =
            addDiskList.toArray(new VcVirtualMachine.DiskCreateSpec[addDiskList.size()]);

      if (hostList.size() > 0 && !hostList.contains(vcChildVm.getHost())) {
         // Even if current host of VM is not in the list of hosts with access to the
         // datastore(s) for the new disk(s), can't migrate the fork Child VM.
         // vcChildVm.migrate(hostList.get(0));
         logger.warn("current host of VM is not in the list of hosts with local access to the new disk(s).");
      }

      if (logger.isDebugEnabled()) {
         logger.debug("add new disks:" + ArrayUtils.toString(addDisks));
      }

      // add the new disks
      vcChildVm.changeDisks(null, addDisks);

      // attach existed disks
      List<VirtualDeviceSpec> deviceChange = new ArrayList<VirtualDeviceSpec>();
      for (DiskSchema.Disk disk : vmForkSpec.getSchema().diskSchema.getDisks()) {
         if (disk.vmdkPath == null || disk.vmdkPath.isEmpty())
            continue;

         VirtualDisk.FlatVer2BackingInfo backing =
               new VirtualDiskImpl.FlatVer2BackingInfoImpl();
         backing.setFileName(disk.vmdkPath);
         backing.setDiskMode(disk.mode.toString());

         deviceChange.add(vcChildVm.attachVirtualDiskSpec(new DeviceId(
               disk.externalAddress), backing, false, DiskSize
               .sizeFromMB(disk.initialSizeMB)));
      }

      if (!deviceChange.isEmpty()) {
         vcChildVm.reconfigure(VmConfigUtil.createConfigSpec(deviceChange));
      }

      logger.info("Config network for child vm " + vmForkSpec.getVmName());
      ConfigSpecImpl newConfigSpec = new ConfigSpecImpl();
      // Network changes
      NetworkSchema netSch = vmForkSpec.getSchema().networkSchema;
      if ( null != netSch && null != netSch.networks ) {
         logger.info("The number of networks for child vm: " + netSch.networks.size());
         for ( Network net : netSch.networks ) {
            logger.info("Network for child vm: " + net.nicLabel + " -- " + net.vcNetwork);
         }
      }
      NetworkSchemaUtil.setNetworkSchema(newConfigSpec, vmForkSpec.getTargetRp()
            .getVcCluster(), vmForkSpec.getSchema().networkSchema, vcChildVm);
      vcChildVm.reconfigure(newConfigSpec);

      // set the bootup configs
      if (vmForkSpec.getBootupConfigs() != null) {
         vcChildVm.setGuestConfigs(vmForkSpec.getBootupConfigs());
      }

      // this reconfiguration is very slow if cluster size is large, as it requires
      // synchronization on vc cluster object
      if (vmForkSpec.getTargetHost() != null) {
         logger.info("Disable DRS for vm " + vmForkSpec.getVmName());
         vcChildVm.disableDrs();
      }
   }

   private void copyParentVmSettings(ConfigSpec configSpec) {
      // copy guest OS info
      configSpec.setGuestId(vcParentVmOnHost.getConfig().getGuestId());

      // copy hardware version
      configSpec.setVersion(vcParentVmOnHost.getConfig().getVersion());


      // copy vApp config info
      VmConfigInfo configInfo = vcParentVmOnHost.getConfig().getVAppConfig();

      // the parent vm might not have vApp option enabled. This is possible when user
      // used customized template.
      if (configInfo != null) {
         VmConfigSpec vAppSpec = new VmConfigSpecImpl();
         vAppSpec.setOvfEnvironmentTransport(configInfo
               .getOvfEnvironmentTransport());

         // product info
         List<ProductSpec> productSpecs = new ArrayList<ProductSpec>();
         for (ProductInfo info : configInfo.getProduct()) {
            ProductSpec spec = new ProductSpecImpl();
            spec.setInfo(info);
            spec.setOperation(ArrayUpdateSpec.Operation.add);
            productSpecs.add(spec);
         }
         vAppSpec.setProduct(productSpecs.toArray(new ProductSpec[productSpecs
               .size()]));

         configSpec.setVAppConfig(vAppSpec);
      }
   }

   public VmCreateSpec getForkSpec() {
      return vmForkSpec;
   }

   public VcVirtualMachine getParentVm() {
      return vcParentVmOnHost;
   }
}
