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
package com.vmware.bdd.manager.intf;

import java.util.List;
import java.util.Map;

import com.vmware.bdd.apitypes.ClusterRead;
import com.vmware.bdd.apitypes.ClusterStatus;
import com.vmware.bdd.apitypes.NodeRead;
import com.vmware.bdd.dal.IServerInfoDAO;
import com.vmware.bdd.entity.ClusterEntity;
import com.vmware.bdd.entity.DiskEntity;
import com.vmware.bdd.entity.NodeEntity;
import com.vmware.bdd.entity.NodeGroupEntity;
import com.vmware.bdd.software.mgmt.plugin.model.ClusterBlueprint;
import com.vmware.bdd.software.mgmt.plugin.model.NodeGroupInfo;
import com.vmware.bdd.software.mgmt.plugin.monitor.ClusterReport;
import com.vmware.bdd.software.mgmt.thrift.OperationStatusWithDetail;

public interface IClusterEntityManager {
   public ClusterEntity findClusterById(Long id);

   public NodeGroupEntity findNodeGroupById(Long id);

   public NodeEntity findNodeById(Long id);

   public ClusterEntity findByName(String clusterName);

   public NodeGroupEntity findByName(String clusterName, String groupName);

   public NodeGroupEntity findByName(ClusterEntity cluster, String groupName);

   public NodeEntity findByName(String clusterName, String groupName,
         String nodeName);

   public NodeEntity findByName(NodeGroupEntity nodeGroup, String nodeName);

   public NodeEntity findNodeByName(String nodeName);

   public List<String> findByAppManager(String appManagerName);

   public List<ClusterEntity> findAllClusters();

   public List<NodeGroupEntity> findAllGroups(String clusterName);

   public List<NodeEntity> findAllNodes(String clusterName);

   public List<NodeEntity> findAllNodes(String clusterName, String groupName);

   public void insert(ClusterEntity cluster);

   public void insert(NodeEntity node);

   public void delete(NodeEntity node);

   public void delete(ClusterEntity cluster);

   public void updateClusterStatus(String clusterName, ClusterStatus status);

   public void updateNodesActionForUpgrade(String clusterName, String action);

   public void updateNodeActionForUpgrade(NodeEntity node, String action);

   public void update(ClusterEntity clusterEntity);

   public void update(NodeGroupEntity group);

   public void update(NodeEntity node);

   public void updateDisks(String nodeName, List<DiskEntity> diskSets);

   public boolean handleOperationStatus(String clusterName,
         OperationStatusWithDetail status, boolean lastUpdate);

   public boolean handleOperationStatus(String clusterName,
         ClusterReport report, boolean lastUpdate);

   public void setClusterStatus(String clusterName, ClusterReport report);

   public void syncUp(String clusterName, boolean updateClusterStatus);

   public void removeVmReference(String vmId);

   public void syncUpNode(String clusterName, String nodeName);

   public List<String> getPortGroupNames(String clusterName);

   public ClusterBlueprint toClusterBluePrint(String clusterName);

   public NodeGroupInfo toNodeGroupInfo(String clusterName, String groupName);

   public ClusterRead toClusterRead(String clusterName);

   public ClusterRead toClusterRead(String clusterName,
         boolean ignoreObsoleteNode);

   public void refreshNodeByMobId(String vmId, boolean inSession);

   public void setNodeConnectionState(String vmName);

   public void refreshNodeByMobId(String vmId, String action, boolean inSession);

   public NodeEntity getNodeByMobId(String vmId);

   public NodeEntity getNodeWithNicsByMobId(String vmId);

   public NodeEntity getNodeByVmName(String vmName);

   public List<NodeEntity> getNodesByHost(String hostName);

   public void refreshNodeByVmName(String vmId, String vmName, boolean inSession);

   public void refreshNodeByVmName(String vmId, String vmName,
         String nodeAction, boolean inSession);

   public void updateClusterTaskId(String clusterName, Long taskId);

   public List<Long> getLatestTaskIds();

   public List<DiskEntity> getDisks(String nodeName);

   public void cleanupActionError(String clusterName);

   public IServerInfoDAO getServerInfoDao();

   public void storeClusterLastStatus(String clusterName);

   public String getServerVersion();

   public boolean needUpgrade(String clusterName);

   public void cleanupErrorForClusterUpgrade(String clusterName);

   public void updateNodeAction(NodeEntity node, String action);

   public Map<String, String> findUserMgmtCfg(String clusterName);

   public NodeEntity findNodeByIpAddress(String ipAddress);

   public NodeRead getNodeReadByVmName(String vmName);
}
