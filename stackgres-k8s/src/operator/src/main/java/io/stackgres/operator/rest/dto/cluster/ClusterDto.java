/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.rest.dto.cluster;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.operator.rest.dto.Resource;

@RegisterForReflection
public class ClusterDto extends Resource {

  @JsonProperty("spec")
  @NotNull(message = "The specification of the cluster is required")
  @Valid
  private ClusterSpec spec;

  @JsonProperty("status")
  private ClusterResourceConsumtion status;

  public ClusterSpec getSpec() {
    return spec;
  }

  public void setSpec(ClusterSpec spec) {
    this.spec = spec;
  }

  public ClusterResourceConsumtion getStatus() {
    return status;
  }

  public void setStatus(ClusterResourceConsumtion status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("metadata", getMetadata())
        .add("spec", spec)
        .add("status", status)
        .toString();
  }

}
