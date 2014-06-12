package com.vmware.bdd.model;

import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Author: Xiaoding Bian
 * Date: 5/23/14
 * Time: 4:45 PM
 */
public class CmClusterDef implements Serializable {

   private static final long serialVersionUID = -2922528263257124521L;

   @Expose
   private String name;

   @Expose
   private String displayName;

   @Expose
   private String version; // TODO: relate to ApiClusterVersion, support CDH3, CDH3u4X, CDH4, CDH5, and only CDH4/CDH5 are supported

   @Expose
   private String fullVersion;

   @Expose
   private Boolean isParcel;

   @Expose
   private CmNodeDef[] nodes;

   @Expose
   private CmServiceDef[] services;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDisplayName() {
      return displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
   }

   public String getVersion() {
      return version;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   public String getFullVersion() {
      return fullVersion;
   }

   public void setFullVersion(String fullVersion) {
      this.fullVersion = fullVersion;
   }

   public Boolean getIsParcel() {
      return isParcel;
   }

   public void setIsParcel(Boolean isParcel) {
      this.isParcel = isParcel;
   }

   public CmNodeDef[] getNodes() {
      return nodes;
   }

   public void setNodes(CmNodeDef[] nodes) {
      this.nodes = nodes;
   }

   public CmServiceDef[] getServices() {
      return services;
   }

   public void setServices(CmServiceDef[] services) {
      this.services = services;
   }

   public Set<String> allServiceNames() {
      Set<String> allServiceNames = new HashSet<String>();
      for (CmServiceDef serviceDef : this.services) {
         allServiceNames.add(serviceDef.getName());
      }
      return allServiceNames;
   }

   public Set<CmServiceRoleType> allServiceTypes() {
      Set<CmServiceRoleType> allServiceTypes = new HashSet<CmServiceRoleType>();
      for (CmServiceDef serviceDef : this.services) {
         allServiceTypes.add(CmServiceRoleType.valueOf(serviceDef.getType()));
      }
      return allServiceTypes;
   }

   public String serviceNameOfType(CmServiceRoleType type) {
      for (CmServiceDef serviceDef : this.services) {
         if (type.equals(CmServiceRoleType.valueOf(serviceDef.getType()))) {
            return serviceDef.getName();
         }
      }
      return null;
   }

   public boolean isEmpty() {
      return nodes == null || nodes.length == 0 || services == null || services.length == 0;
   }
}
