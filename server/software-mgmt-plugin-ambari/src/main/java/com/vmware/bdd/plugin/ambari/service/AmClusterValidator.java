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
package com.vmware.bdd.plugin.ambari.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmware.bdd.plugin.ambari.api.manager.ApiManager;
import com.vmware.bdd.plugin.ambari.api.model.stack.ApiConfiguration;
import com.vmware.bdd.plugin.ambari.api.model.stack.ApiConfigurationInfo;
import com.vmware.bdd.plugin.ambari.api.model.stack.ApiStackService;
import com.vmware.bdd.plugin.ambari.api.model.stack.ApiStackServiceComponent;
import com.vmware.bdd.plugin.ambari.api.model.stack.ApiStackServiceList;
import com.vmware.bdd.plugin.ambari.utils.AmUtils;
import com.vmware.bdd.software.mgmt.plugin.exception.ValidationException;
import com.vmware.bdd.software.mgmt.plugin.model.ClusterBlueprint;
import com.vmware.bdd.software.mgmt.plugin.model.HadoopStack;
import com.vmware.bdd.software.mgmt.plugin.model.NodeGroupInfo;
import com.vmware.bdd.software.mgmt.plugin.model.NodeInfo;

public class AmClusterValidator {

   private static final Logger logger = Logger
         .getLogger(AmClusterValidator.class);
   private List<String> warningMsgList;
   private List<String> errorMsgList;
   private ApiManager apiManager;

   public ApiManager getApiManager() {
      return apiManager;
   }

   public void setApiManager(ApiManager apiManager) {
      this.apiManager = apiManager;
   }

   public AmClusterValidator() {
      this.warningMsgList = new ArrayList<String>();
      this.errorMsgList = new ArrayList<String>();
   }

   public boolean validateBlueprint(ClusterBlueprint blueprint) {
      logger.info("Start to validate bludprint for cluster "
            + blueprint.getName());
      HadoopStack hadoopStack = blueprint.getHadoopStack();
      String distro = hadoopStack.getDistro();
      String stackVendor = hadoopStack.getVendor();
      String stackVersion = hadoopStack.getFullVersion();

      List<String> unRecogConfigTypes = new ArrayList<String>();
      List<String> unRecogConfigKeys = new ArrayList<>();

      validateRoles(blueprint, unRecogConfigTypes, unRecogConfigKeys,
            stackVendor, stackVersion, distro);

      validateRacks(blueprint.getNodeGroups());

      validateConfigs(blueprint.getConfiguration(), unRecogConfigTypes,
            unRecogConfigKeys, stackVendor, stackVersion);

      if (!unRecogConfigTypes.isEmpty()) { // point 2: add to warning list as will be ignored by creating logic
         warningMsgList.add("Configurations for "
               + unRecogConfigTypes.toString()
               + " are not available by distro " + distro);
      }

      if (!unRecogConfigKeys.isEmpty()) { // point 3
         errorMsgList.add("Configuration items " + unRecogConfigKeys.toString()
               + " are invalid");
      }

      if (!warningMsgList.isEmpty() || !errorMsgList.isEmpty()) {
         ValidationException e = new ValidationException(null, null);
         e.getFailedMsgList().addAll(errorMsgList);
         e.getWarningMsgList().addAll(warningMsgList);
         throw e;
      }

      return true;
   }

   private void validateRoles(ClusterBlueprint blueprint,
         List<String> unRecogConfigTypes, List<String> unRecogConfigKeys,
         String stackVendor, String stackVersion, String distro) {
      Map<String, Integer> definedRoles = new HashMap<String, Integer>();

      List<String> unRecogRoles = null;

      List<NodeGroupInfo> nodeGroups = blueprint.getNodeGroups();
      if (nodeGroups == null || nodeGroups.isEmpty()) {
         return;
      }

      ApiStackServiceList servicesList =
            apiManager
                  .getStackServiceListWithComponents(stackVendor, stackVersion);

      List<ApiStackServiceComponent> apiStackComponents =
            new ArrayList<ApiStackServiceComponent>();
      for (ApiStackService apiStackService : servicesList.getApiStackServices()) {
         for (ApiStackServiceComponent apiStackComponent : apiStackService
               .getServiceComponents()) {
            apiStackComponents.add(apiStackComponent);
         }
      }

      for (NodeGroupInfo group : nodeGroups) {
         validateConfigs(group.getConfiguration(), unRecogConfigTypes,
               unRecogConfigKeys, stackVendor, stackVersion);

         for (String roleName : group.getRoles()) {
            boolean isSupported = false;
            for (ApiStackServiceComponent apiStackComponent : apiStackComponents) {
               if (roleName.equals(apiStackComponent.getApiServiceComponent()
                     .getComponentName())) {
                  isSupported = true;
                  if (isSupported) {
                     continue;
                  }
               }
            }
            if (!isSupported) {
               if (unRecogRoles == null) {
                  unRecogRoles = new ArrayList<String>();
               }
               unRecogRoles.add(roleName);
               continue;
            } else {
               if (!definedRoles.containsKey(roleName)) {
                  definedRoles.put(roleName, group.getInstanceNum());
               } else {
                  Integer instanceNum =
                        definedRoles.get(roleName) + group.getInstanceNum();
                  definedRoles.put(roleName, instanceNum);
               }
            }
         }
      }

      if (unRecogRoles != null && !unRecogRoles.isEmpty()) {
         errorMsgList.add("Roles " + unRecogRoles.toString()
               + " are not available by distro " + distro);
      }

      validateRoleDependencies(nodeGroups, apiStackComponents, definedRoles);

   }

   private void validateRoleDependencies(List<NodeGroupInfo> nodeGroups,
         List<ApiStackServiceComponent> apiStackComponents,
         Map<String, Integer> definedRoles) {
      // TODO
   }

   private void validateRacks(List<NodeGroupInfo> nodeGroups) {
      Set<String> invalidRacks = null;

      for (NodeGroupInfo group : nodeGroups) {
         if (group.getNodes() != null) {
            for (NodeInfo node : group.getNodes()) {
               if (node.getRack() != null
                     && !AmUtils.isValidRack(node.getRack())) {
                  if (invalidRacks == null) {
                     invalidRacks = new HashSet<String>();
                  }
                  invalidRacks.add(node.getRack());
               }
            }
         }
      }

      if (invalidRacks != null && !invalidRacks.isEmpty()) {
         errorMsgList
               .add("Racks "
                     + invalidRacks.toString()
                     + " are invalid,"
                     + " rack names must be slash-separated, like Unix paths. For example, \"/rack1\" and \"/cabinet3/rack4\"");
      }
   }

   private void validateConfigs(Map<String, Object> config,
         List<String> unRecogConfigTypes, List<String> unRecogConfigKeys,
         String stackVendor, String stackVersion) {
      if (config == null || config.isEmpty()) {
         return;
      }

      ApiStackServiceList servicesList =
            apiManager.getStackServiceListWithConfigurations(stackVendor,
                  stackVersion);
      Map<String, Object> supportedConfigs = new HashMap<String, Object>();
      for (ApiStackService apiStackService : servicesList.getApiStackServices()) {
         for (ApiConfiguration apiConfiguration : apiStackService
               .getApiConfigurations()) {
            ApiConfigurationInfo apiConfigurationInfo =
                  apiConfiguration.getApiConfigurationInfo();
            String configType = apiConfigurationInfo.getType();
            String propertyName = apiConfigurationInfo.getPropertyName();
            List<String> propertyNames = new ArrayList<String>();
            if (supportedConfigs.isEmpty()) {
               propertyNames.add(propertyName);
            } else {
               if (supportedConfigs.containsKey(configType)) {
                  propertyNames =
                        (List<String>) supportedConfigs.get(configType);
                  propertyNames.add(propertyName);
               } else {
                  propertyNames.add(propertyName);
               }
            }
            supportedConfigs.put(configType, propertyNames);
         }
      }

      Map<String, Object> notAvailableConfig = new HashMap<String, Object>();
      for (String key : config.keySet()) {
         boolean isSupportedType = false;
         for (String configType : supportedConfigs.keySet()) {
            if (configType.equals(key + ".xml")) {
               isSupportedType = true;
               if (isSupportedType) {
                  continue;
               }
            }
         }
         if (!isSupportedType) {
            unRecogConfigTypes.add(key);
         }

         try {
            Map<String, String> items = (Map<String, String>) config.get(key);
            for (String subKey : items.keySet()) {
               boolean isSupportedPropety = false;
               for (String propertyName : (List<String>) supportedConfigs
                     .get(key + ".xml")) {
                  if (propertyName.equals(subKey)) {
                     isSupportedPropety = true;
                     if (isSupportedPropety) {
                        continue;
                     }
                  }
               }
               if (!isSupportedPropety) {
                  unRecogConfigKeys.add(subKey);
               }
            }
         } catch (Exception e) {
            notAvailableConfig.put(key, config.get(key));
            errorMsgList.add("Configuration item " + notAvailableConfig.toString() + " is invalid");
         }
      }
   }

   @Override
   public String toString() {
      Map<String, List<String>> message = new HashMap<String, List<String>>();
      message.put("WarningMsgList", this.warningMsgList);
      message.put("ErrorMsgList", this.errorMsgList);
      return (new Gson()).toJson(message);
   }

}