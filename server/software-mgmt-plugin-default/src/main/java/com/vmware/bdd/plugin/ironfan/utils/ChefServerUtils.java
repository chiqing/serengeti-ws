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
package com.vmware.bdd.plugin.ironfan.utils;

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

import com.vmware.bdd.plugin.ironfan.IronFanPluginException;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;

public class ChefServerUtils {

    private static final Logger logger = Logger.getLogger(ChefServerUtils.class);
    private static HashSet<String> allRoles = null;
    private static String GET_ROLES_CMD = "/usr/bin/knife role list -c /opt/serengeti/.chef/knife.rb";

    public ChefServerUtils() {
    }

    /**
     * Return true if the role exists in Chef Server.
     */
    public static boolean isValidRole(String role) {
        HashSet<String> roles = getAllRoles();
        return roles != null && roles.contains(role);
    }

    /**
     * Set all Chef Roles existing in Chef Server.
     */
    public static void setAllRoles(HashSet<String> allRoles) {
        ChefServerUtils.allRoles = allRoles;
    }

    /**
     * Get all Chef Roles in Chef Server.
     */
    public static HashSet<String> getAllRoles() {
        synchronized(ChefServerUtils.class) {
            if (allRoles == null) {
                HashSet<String> roles = new HashSet<String>();
                Process p = DefaultUtils.launchProcess(GET_ROLES_CMD);
                if (p == null) {
                   throw IronFanPluginException.GET_ROLES_EXCEPTION(null);
                }
                else if(p.exitValue() != 0) {
                    throw IronFanPluginException.GET_ROLES_ERR_EXIT_CODE(p.exitValue());
                }

                try {
                    BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String role;
                    while ((role = buf.readLine()) != null) {
                        if (!role.isEmpty()) {
                            logger.info("Found role " + role + " in Chef Server.");
                            roles.add(role);
                        }
                    }
                    allRoles = roles;
                } catch (IOException e) {
//                    logger.error("Failed to get all roles from Chef Server: " + e.getMessage());
                    throw IronFanPluginException.GET_ROLES_EXCEPTION(e);
                }
            }
            return allRoles;
        }
    }
}
