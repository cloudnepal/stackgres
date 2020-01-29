/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.customresource.sgcluster;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@RegisterForReflection
public class StackGresClusterRestore implements KubernetesResource {

  private static final long serialVersionUID = 1L;

  @JsonProperty("downloadDiskConcurrency")
  private int downloadDiskConcurrency;

  @JsonProperty("fromBackup")
  private String stackgresBackup;

  @JsonProperty("autoCopySecrets")
  private boolean autoCopySecretsEnabled;

  public int getDownloadDiskConcurrency() {
    return downloadDiskConcurrency;
  }

  public void setDownloadDiskConcurrency(int downloadDiskConcurrency) {
    this.downloadDiskConcurrency = downloadDiskConcurrency;
  }

  public String getStackgresBackup() {
    return stackgresBackup;
  }

  public void setStackgresBackup(String stackgresBackup) {
    this.stackgresBackup = stackgresBackup;
  }

  public boolean isAutoCopySecretsEnabled() {
    return autoCopySecretsEnabled;
  }

  public void setAutoCopySecretsEnabled(boolean autoCopySecretsEnabled) {
    this.autoCopySecretsEnabled = autoCopySecretsEnabled;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("stackgresBackup", stackgresBackup)
        .add("autoCopySecrets", autoCopySecretsEnabled)
        .add("downloadDiskConcurrency", downloadDiskConcurrency)
        .toString();
  }
}