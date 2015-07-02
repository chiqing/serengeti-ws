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

import com.vmware.aurora.composition.IPrePostPowerOn;
import com.vmware.aurora.vc.VcVirtualMachine;
import com.vmware.bdd.exception.BddException;
import com.vmware.bdd.exception.MessageBundleCache;
import com.vmware.bdd.exception.Messages;

/**
* Created By xiaoliangl on 2/3/15.
*/
class AfterForkParentPowerOn implements IPrePostPowerOn {
   public static final String FORK_PARENT_SNAPSHOT = "ForkParentSnapshot";

   private static final String COM_VMWARE_BDD_VMCLONE_FORK_I18N_MESSAGES = "com.vmware.bdd.vmclone.fork.i18n.messages";

   private final static Messages MESSAGES =  MessageBundleCache.get(COM_VMWARE_BDD_VMCLONE_FORK_I18N_MESSAGES);

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
      parentVm.createSnapshot(FORK_PARENT_SNAPSHOT, MESSAGES.getString("FORK_PARENT_SNAPSHOT_NAME"));

      int i = 0;
      while(!parentVm.isQuiescedForkParent() && i++ < 60) {
         synchronized (this) {
            this.wait(2000);
         }
      }

      if(!parentVm.isQuiescedForkParent()) {
         throw BddException.INTERNAL(null, "FAILED_QUIESCE_PARENT_VM" + parentVm.getName());
      }

      return null;
   }
}
