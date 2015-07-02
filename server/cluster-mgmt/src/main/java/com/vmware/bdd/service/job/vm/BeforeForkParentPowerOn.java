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
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmware.aurora.composition.IPrePostPowerOn;
import com.vmware.aurora.exception.VcException;
import com.vmware.aurora.vc.VcVirtualMachine;
import com.vmware.aurora.vc.VmConfigUtil;
import com.vmware.bdd.exception.BddException;
import com.vmware.vim.binding.vim.vm.device.VirtualDeviceSpec;

/**
 * Created By xiaoliangl on 1/30/15.
 */
public class BeforeForkParentPowerOn implements IPrePostPowerOn {
   private final static Map<String, String> bootupConfigs;
   private final static Logger logger = Logger.getLogger(BeforeForkParentPowerOn.class);

   static{
      bootupConfigs = new HashMap<>();
      bootupConfigs.put("vmfork", "yes");
   }

   private VcVirtualMachine parentVm;

   @Override
   public void setVm(VcVirtualMachine vm) {
      parentVm = vm;
   }

   @Override
   public VcVirtualMachine getVm() {
      return parentVm;
   }

   @Override
   public Void call() throws Exception {
      ArrayList<VirtualDeviceSpec> scsiCtlSpecList = new ArrayList<>();

      for(int i = 1; i < 4; i ++) {
         scsiCtlSpecList.add(VmConfigUtil.createControllerDevice(VmConfigUtil.ScsiControllerType.PVSCSI, i));
      }
      parentVm.reconfigure(VmConfigUtil.createConfigSpec(scsiCtlSpecList));

      //1. enable ForkParent
      try {
         parentVm.enableForkParent();
      } catch (Exception e) {
         logger.error(e.getMessage());
         throw BddException.INTERNAL(null, "ENABLE_FORK_PARENT_FAILED" + e.getMessage());
      }

      //2. set variable for CustomerVMFork.sh
      Map<String, String> bootupConfigs = new HashMap<>();
      bootupConfigs.put("vmfork", "yes");
      parentVm.setGuestConfigs(bootupConfigs);

      //3. power on.

      return null;
   }
}
