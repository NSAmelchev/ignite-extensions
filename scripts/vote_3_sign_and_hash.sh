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

#
# Sign artifacts
#
echo "# Starting GPG Agent #"
gpg-connect-agent /bye

cd svn/vote || exit

list=$(find -type f -printf "%f\n")

for file in $list
do
    echo "Signing ${file}"
    GPG_AGENT_INFO=~/.gnupg/S.gpg-agent:0:1 gpg -ab ${file}

    echo "Hashing ${file}"
    sha512sum ${file} > ${file}.sha512
done
