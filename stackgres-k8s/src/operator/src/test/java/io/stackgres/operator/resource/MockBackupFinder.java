/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.resource;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

import io.quarkus.test.Mock;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfig;
import io.stackgres.operator.utils.JsonUtil;


public class MockBackupFinder implements CustomResourceFinder<StackGresBackupConfig> {

  @Override
  public Optional<StackGresBackupConfig> findByNameAndNamespace(String name, String namespace) {
    return Optional.of(JsonUtil
        .readFromJson("backup_config/default.json", StackGresBackupConfig.class));
  }
}