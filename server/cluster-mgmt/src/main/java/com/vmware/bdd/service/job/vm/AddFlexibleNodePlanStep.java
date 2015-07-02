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
package com.vmware.bdd.service.job.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.vmware.aurora.composition.NetworkSchema;
import com.vmware.aurora.composition.VmSchema;
import com.vmware.aurora.composition.NetworkSchema.Network;
import com.vmware.aurora.vc.VcDatastore;
import com.vmware.aurora.vc.VcHost;
import com.vmware.bdd.apitypes.ClusterCreate;
import com.vmware.bdd.apitypes.NetworkAdd;
import com.vmware.bdd.apitypes.NodeGroupCreate;
import com.vmware.bdd.entity.NodeEntity;
import com.vmware.bdd.exception.BddException;
import com.vmware.bdd.manager.ClusterConfigManager;
import com.vmware.bdd.placement.Container;
import com.vmware.bdd.placement.entity.BaseNode;
import com.vmware.bdd.service.IClusteringService;
import com.vmware.bdd.service.job.JobConstants;
import com.vmware.bdd.service.job.JobExecutionStatusHolder;
import com.vmware.bdd.service.job.TrackableTasklet;
import com.vmware.bdd.service.utils.VcResourceUtils;
import com.vmware.bdd.spectypes.DiskSpec;
import com.vmware.bdd.utils.Constants;
import com.vmware.bdd.utils.VcVmUtil;

public class AddFlexibleNodePlanStep extends TrackableTasklet {
   private IClusteringService clusteringService;
   private ClusterConfigManager configMgr;

   public ClusterConfigManager getConfigMgr() {
      return configMgr;
   }

   public void setConfigMgr(ClusterConfigManager configMgr) {
      this.configMgr = configMgr;
   }

   @Override
   public RepeatStatus executeStep(ChunkContext chunkContext,
         JobExecutionStatusHolder jobExecutionStatusHolder) throws Exception {
      String clusterName =
            getJobParameters(chunkContext).getString(
                  JobConstants.CLUSTER_NAME_JOB_PARAM);
      String host = 
            getJobParameters(chunkContext).getString(
                  JobConstants.SINGLE_VM_HOST_JOB_PARAM);
      String nodeGroupName = getJobParameters(chunkContext).getString(JobConstants.GROUP_NAME_JOB_PARAM);

      ClusterCreate clusterSpec = configMgr.getClusterConfig(clusterName);

      String vmName = getVmName(clusterName, nodeGroupName);
      BaseNode node = new BaseNode(vmName, clusterSpec.getNodeGroup(nodeGroupName), clusterSpec);
      VmSchema createSchema =
            VcVmUtil.getVmSchema(clusterSpec, nodeGroupName, new ArrayList<DiskSpec>(),
                  clusteringService.getTemplateVmId(),
                  Constants.ROOT_SNAPSTHOT_NAME);
      // target network, hard coded as the only one NIC
      NetworkSchema netSchema = new NetworkSchema();

      ArrayList<Network> networks = new ArrayList<Network>();
      netSchema.networks = networks;

      // TODO: enhance this logic to support nodegroup level networks
      for (NetworkAdd networkAdd : clusterSpec.getNetworkings()) {
         Network network = new Network();
         network.vcNetwork = networkAdd.getPortGroup();
         networks.add(network);
      }

      node.setVmSchema(createSchema);
      node.getVmSchema().networkSchema = netSchema;
      node.setTargetHost(host);
      VcHost vcHost = VcResourceUtils.findHost(host);
      List<VcDatastore> dss = vcHost.getDatastores();
      for(VcDatastore ds : dss) {
         if ((ds.getFreeSpace() / (1024 * 1024 * 1024)) > 20) {
            node.setTargetDs(ds.getName());
            break;
         }
      }
      node.setTargetVcCluster(VcResourceUtils.getHostVcCluster(vcHost).getName());
      node.setCluster(clusterSpec);
      node.setNodeGroup(clusterSpec.getNodeGroup(nodeGroupName));
      List<BaseNode> vNodes = new ArrayList<>();
      vNodes.add(node);
      putIntoJobExecutionContext(chunkContext, JobConstants.CLUSTER_SPEC_JOB_PARAM, clusterSpec);
      putIntoJobExecutionContext(chunkContext,
            JobConstants.CLUSTER_ADDED_NODES_JOB_PARAM, vNodes);
      return RepeatStatus.FINISHED;
   }

   private String getVmName(String clusterName, String nodeGroupName) throws Exception {
      for (int i = 0; i < 10; i++) {
         String vmName = clusterName + "-" + nodeGroupName + '-' + UUID.randomUUID().toString();
         if (vmName.length() > 60) {
            vmName = vmName.substring(0, 60);
         }
         NodeEntity nodeEntity = getClusterEntityMgr().findNodeByName(vmName);
         if (nodeEntity == null) {
            return vmName;
         }
      }
      throw BddException.INTERNAL(null, "Can not find random VM name, try again later.");
   }

   public IClusteringService getClusteringService() {
      return clusteringService;
   }

   public void setClusteringService(IClusteringService clusteringService) {
      this.clusteringService = clusteringService;
   }
}

