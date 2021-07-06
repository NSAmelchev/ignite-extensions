#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

source ./vote_0_properties.sh

echo "RC ${module_name}-${module_version}-${rc_version}"

# Uncomment subsequent line in case you want to remove incorrectly prepared RC
#svn rm -m "Removing redundant Release" https://dist.apache.org/repos/dist/dev/ignite/ignite-extensions/${module_name}/${module_version}-${rc_version} || true
svn import svn/vote https://dist.apache.org/repos/dist/dev/ignite/ignite-extensions/${module_name}/${module_version}-${rc_version} -m "New RC ${module_name}-${module_version}-${rc_version}"

#
# Output result and notes
#
echo
echo "============================================================================="
echo "Artifacts should be moved to RC repository"
echo "Please check results at:"
echo " * binaries: https://dist.apache.org/repos/dist/dev/ignite/ignite-extensions/${module_name}/${module_version}-${rc_version}"
