/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.cluster;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import io.stackgres.operator.conciliation.ResourceDiscoverer;
import io.stackgres.operator.conciliation.cluster.StackGresClusterContext;
import io.stackgres.operator.conciliation.cluster.StackGresVersion;
import io.stackgres.operator.conciliation.factory.VolumeDiscoverer;
import io.stackgres.operator.conciliation.factory.VolumeFactory;
import io.stackgres.operator.conciliation.factory.VolumePair;

@ApplicationScoped
public class ClusterVolumeDiscoverer
    extends ResourceDiscoverer<VolumeFactory<StackGresClusterContext>>
    implements VolumeDiscoverer<StackGresClusterContext> {

  @Inject
  public ClusterVolumeDiscoverer(
      @Any Instance<VolumeFactory<StackGresClusterContext>> instance) {
    init(instance);
  }

  @Override
  public Map<String, VolumePair> discoverVolumes(
      StackGresClusterContext context) {

    StackGresVersion version = StackGresVersion.getClusterStackGresVersion(context.getSource());

    return resourceHub.get(version)
        .stream().flatMap(vf -> vf.buildVolumes(context))
        .collect(Collectors.toMap(vp -> vp.getVolume().getName(), Function.identity()));

  }
}
