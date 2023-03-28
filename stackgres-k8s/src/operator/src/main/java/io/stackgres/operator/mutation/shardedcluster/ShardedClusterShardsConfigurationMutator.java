/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.shardedcluster;

import java.util.List;

import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatchOperation;
import io.stackgres.common.crd.sgcluster.StackGresClusterConfiguration;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShards;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operatorframework.admissionwebhook.mutating.JsonPatchMutator;

public interface ShardedClusterShardsConfigurationMutator extends ShardedClusterMutator {

  default JsonPointer getConfigurationTargetPointer(String field) {
    String jsonField =
        getJsonMappingField(field, StackGresClusterConfiguration.class);
    return getConfigurationTargetPointer()
        .append(jsonField);
  }

  default JsonPointer getConfigurationTargetPointer() {
    return getShardsTargetPointer("configuration");
  }

  default List<JsonPatchOperation> ensureConfigurationNode(StackGresShardedClusterReview review) {
    final StackGresShardedClusterSpec spec = review.getRequest().getObject().getSpec();
    final StackGresShardedClusterShards shards = spec.getShards();
    StackGresClusterConfiguration configuration = shards.getConfiguration();

    if (configuration == null) {
      configuration = new StackGresClusterConfiguration();
      shards.setConfiguration(configuration);
      final JsonPointer confPointer = getConfigurationTargetPointer();
      final AddOperation configurationAdd = new AddOperation(confPointer,
          JsonPatchMutator.FACTORY.objectNode());
      return List.of(configurationAdd);
    }

    return List.of();
  }
}