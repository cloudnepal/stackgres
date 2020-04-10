/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.resource;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.stackgres.common.crd.sgpooling.StackGresPoolingConfig;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigDefinition;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigDoneable;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigList;
import io.stackgres.operator.app.KubernetesClientFactory;
import io.stackgres.operator.common.ArcUtil;

@ApplicationScoped
public class PgPoolingConfigScheduler
    extends AbstractCustomResourceScheduler<StackGresPoolingConfig,
    StackGresPoolingConfigList, StackGresPoolingConfigDoneable> {

  @Inject
  public PgPoolingConfigScheduler(KubernetesClientFactory clientFactory) {
    super(clientFactory,
        StackGresPoolingConfigDefinition.NAME,
        StackGresPoolingConfig.class,
        StackGresPoolingConfigList.class,
        StackGresPoolingConfigDoneable.class);
  }

  public PgPoolingConfigScheduler() {
    super(null, null, null, null, null);
    ArcUtil.checkPublicNoArgsConstructorIsCalledFromArc();
  }

}
