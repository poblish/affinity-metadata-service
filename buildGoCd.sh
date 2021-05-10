#!/bin/bash
set -eo pipefail

# Obtain filter, strip all " and spaces
service_filter=${1:-}
service_filter=$(echo $service_filter | tr -d '" ')

if [[ -n $service_filter && $service_filter != '.' ]]; then
  echo -e "\n\e[96mUsing multi-module filter for: $service_filter\e[33m"
  echo
  replace_commas=$(echo $service_filter | tr , :)

  # Do filtered multimodule builds from the top (.) to include Sonar, *but* use a different projectKey (and projectName) to avoid overwriting existing entries
  multimodule_settings="--projects \".,${service_filter}\" -Dsonar.projectKey=${replace_commas}  -Dsonar.projectName=${replace_commas}"
fi

if [[ $(git rev-parse --abbrev-ref HEAD) == "master" ]] ; then
  runMaven jdk-15-latest ${multimodule_settings} --no-transfer-progress clean org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:report sonar:sonar -Dmaven.repo.local=/tmp -DGO_PIPELINE_COUNTER=r.${GO_PIPELINE_COUNTER} -DJVM_DEBUG_DISALLOWED=1
else
  runMaven jdk-15-latest ${multimodule_settings} --no-transfer-progress clean package -Dmaven.repo.local=/tmp -DGO_PIPELINE_COUNTER=r.${GO_PIPELINE_COUNTER}
fi

###################### Build individual services and monorepos

if [ -z ${service_filter} ]; then
  echo -e "\n\e[96mBuilding all charts...\e[33m"
else
  echo -e "\n\e[96mOnly building charts named \"$service_filter\" ...\e[33m"
fi

service_root_dir=$(pwd)  # service or monorepo dir

# Find any/all subprojects / individual service charts
for charts_dir in $(find $service_root_dir -name charts -type d); do
  pushd $charts_dir
  service_chart_name=$(ls | head -n 1)  # don't rely on convention, find out for sure. *Should* be just one.
  cd ..

  # If filter is set, and its comma-separated list doesn't include this service name
  if [[ -n ${service_filter} && ",$service_filter," != *",$service_chart_name,"* ]]; then
    echo -e "\n\e[96mFiltering out chart build for $service_chart_name\e[33m"
    popd
    continue
  fi

  echo -e "\n\e[96mBuilding chart for $service_chart_name ...\e[33m"

  make build push copy-helm-templates
  mv image.version.txt $service_root_dir 2>/dev/null || true  # Must be discoverable at the same level we run `runKubeTools`. Ensure nofail if we're in right dir already and file copies over itself

  service_path_relative_to_checkout=$(python -c "import os.path; print os.path.relpath('$(pwd)', '$service_root_dir')")
  popd

  # Build from the service root upwards, as the git repo must be mapped-in
  runKubeTools $service_path_relative_to_checkout/kube-scripts/helm-build-helmfile $service_path_relative_to_checkout/charts/$service_chart_name

  # Copy to top level to maintain compatibility with GoCD Build Artifacts
  mv $service_chart_name.yaml $service_root_dir/..
done
