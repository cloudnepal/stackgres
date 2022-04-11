/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.stackgres.common.component.Component;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KubectlUtil {

  private static final Logger LOG = LoggerFactory.getLogger(KubectlUtil.class);

  private final int k8sMinorVersion;

  @Inject
  public KubectlUtil(KubernetesClient client) {
    int minor;
    try {
      VersionInfo kubernetesVersion = client.getKubernetesVersion();
      LOG.debug("Kubernetes version: {}", kubernetesVersion.getGitVersion());
      minor = Integer.parseInt(kubernetesVersion.getMinor());
    } catch (RuntimeException e) {
      // Fallback to latest image
      minor = -1;
    }
    this.k8sMinorVersion = minor;
  }

  public String getImageName(@NotNull StackGresVersion sgversion) {
    Component kubectl = StackGresComponent.KUBECTL.getOrThrow(sgversion);
    final String imageName = kubectl.getOrderedVersions()
        .filter(ver -> k8sMinorVersion != -1)
        .findFirst(ver -> {
          int minor = Integer.parseInt(ver.split("\\.")[1]);
          return (k8sMinorVersion >= minor - 1 && k8sMinorVersion <= minor + 1);
        })
        .map(kubectl::findImageName)
        .orElseGet(kubectl::findLatestImageName);
    LOG.debug("Using kubectl image: {}", imageName);
    return imageName;
  }

  public String getImageName(@NotNull StackGresCluster cluster) {
    return getImageName(StackGresVersion.getStackGresVersion(cluster));
  }

  public String getImageName(@NotNull StackGresDistributedLogs distributedLogs) {
    return getImageName(StackGresVersion.getStackGresVersion(distributedLogs));
  }

}