/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.cluster;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatchOperation;
import com.google.common.collect.ImmutableList;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterPod;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgprofile.StackGresProfile;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScheduler;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.initialization.DefaultCustomResourceFactory;
import io.stackgres.operatorframework.admissionwebhook.Operation;

@ApplicationScoped
public class DefaultProfileMutator extends AbstractDefaultResourceMutator<StackGresProfile> {

  @Inject
  public DefaultProfileMutator(
      DefaultCustomResourceFactory<StackGresProfile> resourceFactory,
      CustomResourceFinder<StackGresProfile> finder,
      CustomResourceScheduler<StackGresProfile> scheduler) {
    super(resourceFactory, finder, scheduler);
  }

  @PostConstruct
  @Override
  public void init() {
    super.init();
  }

  @Override
  public List<JsonPatchOperation> mutate(StackGresClusterReview review) {
    if (review.getRequest().getOperation() == Operation.CREATE) {
      ImmutableList.Builder<JsonPatchOperation> operations = ImmutableList.builder();
      final StackGresClusterSpec spec = review.getRequest().getObject().getSpec();
      StackGresClusterPod pod = spec.getPod();
      final JsonPointer clusterPodPointer = SPEC_POINTER
          .append("pod");
      if (pod == null) {
        pod = new StackGresClusterPod();
        spec.setPod(pod);
        operations.add(new AddOperation(clusterPodPointer, FACTORY.objectNode()));
      }
      if (pod.getPersistentVolume() == null) {
        operations.add(new AddOperation(clusterPodPointer
            .append("persistentVolume"), FACTORY.objectNode()));
      }
      operations.addAll(super.mutate(review));
      return operations.build();
    }
    return ImmutableList.of();
  }

  @Override
  protected String getTargetPropertyValue(StackGresCluster targetCluster) {
    return targetCluster.getSpec().getResourceProfile();
  }

  @Override
  protected JsonPointer getTargetPointer() {
    return getTargetPointer("resourceProfile");
  }
}
