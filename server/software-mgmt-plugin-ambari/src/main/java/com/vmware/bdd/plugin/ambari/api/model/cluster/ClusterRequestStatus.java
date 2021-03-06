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
package com.vmware.bdd.plugin.ambari.api.model.cluster;

public enum ClusterRequestStatus {

   PENDING(0), //Not queued for a host
   QUEUED(1), //Queued for a host
   IN_PROGRESS(2), //Host reported it is working
   COMPLETED(3), //Host reported success
   FAILED(4), //Failed
   TIMEDOUT(5), //Host did not respond in time
   ABORTED(6); //Operation was abandoned

   private final int status;

   private ClusterRequestStatus(int status) {
      this.status = status;
   }

   /**
    * Indicates whether or not it is a valid failure state.
    *
    * @return true if this is a valid failure state.
    */
   public boolean isFailedState() {
      switch (ClusterRequestStatus.values()[this.status]) {
      case FAILED:
      case TIMEDOUT:
      case ABORTED:
         return true;
      default:
         return false;
      }
   }

   /**
    * Indicates whether or not this is a completed state. Completed means that
    * the associated task has stopped running because it has finished
    * successfully or has failed.
    *
    * @return true if this is a completed state.
    */
   public boolean isCompletedState() {
      switch (ClusterRequestStatus.values()[this.status]) {
      case COMPLETED:
      case FAILED:
      case TIMEDOUT:
      case ABORTED:
         return true;
      default:
         return false;
      }
   }

   public boolean isRunningState() {
      switch (ClusterRequestStatus.values()[this.status]) {
      case IN_PROGRESS:
         return true;
      default:
         return false;
      }
   }
}
