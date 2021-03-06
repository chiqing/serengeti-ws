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

package com.vmware.bdd.service.resmgmt;

import java.util.List;

import com.vmware.bdd.apitypes.AppManagerAdd;
import com.vmware.bdd.apitypes.AppManagerRead;
import com.vmware.bdd.entity.AppManagerEntity;

/**
 * Author: Xiaoding Bian
 * Date: 6/4/14
 * Time: 5:44 PM
 */
public interface IAppManagerService {

   void addAppManager(AppManagerAdd appManagerAdd);

   public List<AppManagerEntity> findAll();

   public AppManagerEntity findAppManagerByName(String name);

   public AppManagerRead getAppManagerRead(String name);

   public List<AppManagerRead> getAllAppManagerReads();

   public void deleteAppManager(String name);

   public void modifyAppManager(AppManagerAdd appManagerAdd);
}
