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
package com.vmware.bdd.service.job.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.google.gson.reflect.TypeToken;
import com.vmware.bdd.apitypes.ClusterCreate;
import com.vmware.bdd.placement.entity.BaseNode;
import com.vmware.bdd.service.IClusteringService;
import com.vmware.bdd.service.job.DefaultStatusUpdater;
import com.vmware.bdd.service.job.JobConstants;
import com.vmware.bdd.service.job.JobExecutionStatusHolder;
import com.vmware.bdd.service.job.NodeOperationStatus;
import com.vmware.bdd.service.job.StatusUpdater;
import com.vmware.bdd.service.job.TrackableTasklet;
import com.vmware.bdd.utils.Constants;

public class ForkSingleFlexibleVMStep extends TrackableTasklet {
   private final static Logger logger = Logger.getLogger(ForkSingleFlexibleVMStep.class);
   IClusteringService clusteringService;

   private ForkParentService forkParent = new ForkParentService();
   @Override
   public RepeatStatus executeStep(ChunkContext chunkContext,
         JobExecutionStatusHolder jobExecutionStatusHolder) throws Exception {

      StatusUpdater statusUpdator = new DefaultStatusUpdater(jobExecutionStatusHolder,
            getJobExecutionId(chunkContext));
      List<BaseNode> vNodes = getFromJobExecutionContext(chunkContext, JobConstants.CLUSTER_ADDED_NODES_JOB_PARAM,
            new TypeToken<List<BaseNode>>() {}.getType());
      ClusterCreate clusterSpec = getFromJobExecutionContext(chunkContext,JobConstants.CLUSTER_SPEC_JOB_PARAM, ClusterCreate.class);
      Map<String, Set<String>> usedIpSets = getFromJobExecutionContext(chunkContext,
            JobConstants.CLUSTER_USED_IP_JOB_PARAM,
            new TypeToken<Map<String, Set<String>>>() {}.getType());
      if (usedIpSets == null) {
         usedIpSets = new HashMap<String, Set<String>>();
      }
      String clusterCloneType = clusterSpec.getClusterCloneType();

      boolean success = true;
      if (clusterCloneType.equals(Constants.CLUSTER_CLONE_TYPE_INSTANT_CLONE)) {
         String referenceVmId = 
               getJobParameters(chunkContext).getString(
                     JobConstants.REFERENCE_VM_ID_JOB_PARAM);
         success = clusteringService.forkOneVM(clusterSpec.getNetworkings(), vNodes,
               usedIpSets, statusUpdator, forkParent, referenceVmId);
      } else {
         logger.error("Not instance clone, skip vm creation.");
         success = false;
      }
      putIntoJobExecutionContext(chunkContext, JobConstants.CLUSTER_CREATE_VM_OPERATION_SUCCESS, success);
      putIntoJobExecutionContext(chunkContext, JobConstants.CLUSTER_ADDED_NODES_JOB_PARAM, vNodes);
      putIntoJobExecutionContext(chunkContext, JobConstants.VERIFY_VM_NAME_JOB_PARAM, vNodes.get(0).getVmName());
      
      UUID reservationId = getFromJobExecutionContext(chunkContext, JobConstants.CLUSTER_RESOURCE_RESERVATION_ID_JOB_PARAM, UUID.class);
      if (reservationId != null) {
         // release the resource reservation since vm is created
         clusteringService.commitReservation(reservationId);
         putIntoJobExecutionContext(chunkContext, JobConstants.CLUSTER_RESOURCE_RESERVATION_ID_JOB_PARAM, null);
      }

      ExecutionContext context = getJobExecutionContext(chunkContext);
      if (success) {
         List<NodeOperationStatus> succeededNodes = new ArrayList<NodeOperationStatus>();
         NodeOperationStatus succeededSubJob = new NodeOperationStatus(vNodes.get(0).getVmName());
         succeededNodes.add(succeededSubJob);
         context.put(JobConstants.SUB_JOB_NODES_SUCCEED, succeededNodes);
      } else {
         List<NodeOperationStatus> failedNodes = new ArrayList<NodeOperationStatus>();
         NodeOperationStatus failedSubJob = new NodeOperationStatus(vNodes.get(0).getVmName());
         failedNodes.add(failedSubJob);
         context.put(JobConstants.SUB_JOB_NODES_FAIL, failedNodes);
      }
      return RepeatStatus.FINISHED;
   }

   public IClusteringService getClusteringService() {
      return clusteringService;
   }

   public void setClusteringService(IClusteringService clusteringService) {
      this.clusteringService = clusteringService;
   }



}
