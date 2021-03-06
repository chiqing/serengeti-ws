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
package com.vmware.bdd.software.mgmt.plugin.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.Expose;
import com.vmware.bdd.apitypes.InstanceType;
import com.vmware.bdd.apitypes.PlacementPolicy;

/**
 * Node group creation specification
 * @author line
 *
 */
public class NodeGroupInfo implements Serializable{

   private static final long serialVersionUID = 4443680719513071084L;

   @Expose
   private String name;

   @Expose
   private List<String> roles;

   @Expose
   private int instanceNum;

   @Expose
   private Map<String, Object> configuration;

   @Expose
   private List<NodeInfo> nodes;

   private boolean haEnabled;
   private String storageType;
   private PlacementPolicy placement;
   private InstanceType instanceType;
   private int storageSize;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<String> getRoles() {
      return roles;
   }

   public void setRoles(List<String> roles) {
      this.roles = roles;
   }

   public int getInstanceNum() {
      return instanceNum;
   }

   public void setInstanceNum(int instanceNum) {
      this.instanceNum = instanceNum;
   }

   public Map<String, Object> getConfiguration() {
      return configuration;
   }

   public void setConfiguration(Map<String, Object> configuration) {
      this.configuration = configuration;
   }

   public List<NodeInfo> getNodes() {
      return nodes;
   }

   public void setNodes(List<NodeInfo> nodes) {
      this.nodes = nodes;
   }

   public boolean isHaEnabled() {
      return haEnabled;
   }

   public void setHaEnabled(boolean haEnabled) {
      this.haEnabled = haEnabled;
   }

   public PlacementPolicy getPlacement() {
      return placement;
   }

   public void setPlacement(PlacementPolicy placement) {
      this.placement = placement;
   }

   public String getStorageType() {
      return storageType;
   }

   public void setStorageType(String storageType) {
      this.storageType = storageType;
   }

   public InstanceType getInstanceType() {
      return instanceType;
   }

   public void setInstanceType(InstanceType instanceType) {
      this.instanceType = instanceType;
   }

   public int getStorageSize() {
      return storageSize;
   }

   public void setStorageSize(int storageSize) {
      this.storageSize = storageSize;
   }
}
