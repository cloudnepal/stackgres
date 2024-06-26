/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgcluster;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.validation.FieldReference;
import io.stackgres.common.validation.FieldReference.ReferencedField;
import io.stackgres.common.validation.ValidEnum;
import io.sundr.builder.annotations.Buildable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresClusterReplication {

  @ValidEnum(enumClass = StackGresReplicationMode.class, allowNulls = false,
      message = "mode must be async, sync, sync-all, strict-sync or strict-sync-all")
  private String mode;

  private String role;

  @Min(value = 1)
  private Integer syncInstances;

  @Valid
  private List<StackGresClusterReplicationGroup> groups;

  @Valid
  private StackGresClusterReplicationInitialization initialization;

  @ReferencedField("role")
  interface Role extends FieldReference { }

  @ReferencedField("syncInstances")
  interface SyncInstances extends FieldReference { }

  @JsonIgnore
  @AssertTrue(message = "role must be ha or ha-read",
      payload = { Role.class })
  public boolean isRoleValid() {
    if (role == null) {
      return false;
    }
    try {
      StackGresMainReplicationRole.fromString(role);
    } catch (IllegalArgumentException ex) {
      return false;
    }
    return true;
  }

  @JsonIgnore
  @AssertTrue(message = "syncInstances must be set when mode is sync or strict-sync",
      payload = { SyncInstances.class })
  public boolean isSyncInstancesSetForSyncMode() {
    return !isSynchronousMode()
        || syncInstances != null;
  }

  @JsonIgnore
  public boolean isSynchronousMode() {
    return Objects.equals(StackGresReplicationMode.SYNC.toString(), mode)
        || Objects.equals(StackGresReplicationMode.STRICT_SYNC.toString(), mode);
  }

  @JsonIgnore
  public boolean isSynchronousModeAll() {
    return Objects.equals(StackGresReplicationMode.SYNC_ALL.toString(), mode)
        || Objects.equals(StackGresReplicationMode.STRICT_SYNC_ALL.toString(), mode);
  }

  @JsonIgnore
  public boolean isStrictSynchronousMode() {
    return Objects.equals(StackGresReplicationMode.STRICT_SYNC.toString(), mode);
  }

  @JsonIgnore
  public boolean isStrictSynchronousModeAll() {
    return Objects.equals(StackGresReplicationMode.STRICT_SYNC_ALL.toString(), mode);
  }

  @JsonIgnore
  public StackGresReplicationInitializationMode getInitializationModeOrDefault() {
    return Optional.ofNullable(initialization)
        .map(StackGresClusterReplicationInitialization::getModeOrDefault)
        .orElse(StackGresClusterReplicationInitialization.DEFAULT_MODE);
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Integer getSyncInstances() {
    return syncInstances;
  }

  public void setSyncInstances(Integer syncInstances) {
    this.syncInstances = syncInstances;
  }

  public List<StackGresClusterReplicationGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<StackGresClusterReplicationGroup> groups) {
    this.groups = groups;
  }

  public StackGresClusterReplicationInitialization getInitialization() {
    return initialization;
  }

  public void setInitialization(StackGresClusterReplicationInitialization initialization) {
    this.initialization = initialization;
  }

  @Override
  public int hashCode() {
    return Objects.hash(groups, initialization, mode, role, syncInstances);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresClusterReplication)) {
      return false;
    }
    StackGresClusterReplication other = (StackGresClusterReplication) obj;
    return Objects.equals(groups, other.groups) && Objects.equals(initialization, other.initialization)
        && Objects.equals(mode, other.mode) && Objects.equals(role, other.role)
        && Objects.equals(syncInstances, other.syncInstances);
  }

  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
